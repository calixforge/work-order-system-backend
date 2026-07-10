#!/usr/bin/env python3
"""工单知识库 RAG 批量评估工具。"""

from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import random
import re
import subprocess
import sys
import tempfile
import time
import unicodedata
import uuid
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

import requests


SCRIPT_VERSION = "1.0.0"
BASE_URL = "http://localhost:8080"
USERNAME = "admin"
PASSWORD = "admin123"
DEFAULT_DATASET = "evaluation-set.json"
DEFAULT_READ_TIMEOUT = 75
REJECT_MARKERS = ("暂无相关资料", "没有找到相关资料")
VALID_OUTCOMES = {"pass", "known_limit"}
VALID_CASE_TYPES = {
    "direct",
    "multi_source",
    "edge_uncovered",
    "reject",
    "chat",
    "grounding_boundary",
    "known_limit",
}
BASELINE_VERSION_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")


class EvaluationError(RuntimeError):
    """评估无法继续时抛出的错误。"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="批量调用工单知识库 SSE 接口并生成评估报告")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--debug", action="store_true", help="允许 Git 工作区不干净，报告写入 .tmp")
    mode.add_argument("--baseline", metavar="VERSION", help="生成正式 baseline，如 v1；要求运行前后工作区干净")
    parser.add_argument("--dataset", default=DEFAULT_DATASET, help="评估集路径，默认 tools/eval/evaluation-set.json")
    parser.add_argument("--case-id", action="append", help="仅执行指定用例，可重复；只建议 debug 使用")
    parser.add_argument("--read-timeout", type=int, default=DEFAULT_READ_TIMEOUT, help="单题 SSE 读取超时秒数")
    parser.add_argument("--delay", type=float, default=0.2, help="题目之间的等待秒数，避免模型端限流")
    parser.add_argument("--sample-size", type=int, default=5, help="Markdown 中抽查自动通过用例的数量")
    args = parser.parse_args()
    if args.read_timeout <= 0:
        parser.error("--read-timeout 必须大于 0")
    if args.delay < 0:
        parser.error("--delay 不能小于 0")
    if args.sample_size < 0:
        parser.error("--sample-size 不能小于 0")
    if args.baseline:
        if not BASELINE_VERSION_PATTERN.fullmatch(args.baseline) or ".." in args.baseline:
            parser.error("baseline 版本名只能包含字母、数字、点、下划线和连字符，且不能包含 '..'")
        if args.case_id:
            parser.error("正式 baseline 必须执行完整评估集，不能使用 --case-id")
        if not 3 <= args.sample_size <= 5:
            parser.error("正式 baseline 的 --sample-size 必须在 3 到 5 之间")
    return args


def run_git(repo: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
        check=False,
    )


def discover_repo(script_dir: Path) -> Path:
    result = run_git(script_dir, "rev-parse", "--show-toplevel")
    if result.returncode != 0:
        raise EvaluationError(f"无法定位 Git 仓库: {result.stderr.strip()}")
    return Path(result.stdout.strip()).resolve()


def git_snapshot(repo: Path) -> Dict[str, Any]:
    commit_result = run_git(repo, "rev-parse", "HEAD")
    status_result = run_git(repo, "status", "--porcelain=v1", "--untracked-files=all")
    if commit_result.returncode != 0 or status_result.returncode != 0:
        raise EvaluationError("读取 Git 状态失败，正式 baseline 按 fail-closed 处理")
    status_lines = [line for line in status_result.stdout.splitlines() if line.strip()]
    return {
        "commit": commit_result.stdout.strip(),
        "dirty": bool(status_lines),
        "status": status_lines,
    }


def require_tracked_dataset(repo: Path, dataset_path: Path) -> None:
    try:
        relative_path = dataset_path.relative_to(repo)
    except ValueError as exc:
        raise EvaluationError("正式 baseline 的评估集必须位于当前 Git 仓库内") from exc
    result = run_git(repo, "ls-files", "--error-unmatch", "--", relative_path.as_posix())
    if result.returncode != 0:
        raise EvaluationError("正式 baseline 的评估集必须已经纳入 Git 跟踪")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_java_constant(path: Path, constant_name: str, value_pattern: str, cast: Any) -> Any:
    text = path.read_text(encoding="utf-8")
    pattern = rf"\b{re.escape(constant_name)}\s*=\s*({value_pattern})\s*;"
    matches = re.findall(pattern, text)
    if len(matches) != 1:
        raise EvaluationError(f"无法从 {path.name} 唯一读取 {constant_name}")
    return cast(matches[0])


def read_yaml_scalar(path: Path, key_path: Sequence[str]) -> str:
    """读取项目 YAML 中指定层级的简单标量，不引入额外 YAML 依赖。"""
    stack: List[Tuple[int, str]] = []
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        match = re.match(r"^(\s*)([A-Za-z0-9_-]+):(?:\s*(.*))?$", raw_line)
        if not match:
            continue
        indent = len(match.group(1).replace("\t", "    "))
        key = match.group(2)
        value = (match.group(3) or "").strip()
        while stack and indent <= stack[-1][0]:
            stack.pop()
        current_path = tuple(item[1] for item in stack) + (key,)
        if value:
            if current_path == tuple(key_path):
                value = value.split(" #", 1)[0].strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
                    value = value[1:-1]
                if value.startswith("${"):
                    raise EvaluationError(f"{path.name} 中 {'.'.join(key_path)} 仍是环境变量占位符")
                return value
        else:
            stack.append((indent, key))
    raise EvaluationError(f"无法从 {path.name} 读取 {'.'.join(key_path)}")


def read_rag_snapshot(repo: Path) -> Dict[str, Any]:
    resources = repo / "src/main/resources"
    active_profile = read_yaml_scalar(resources / "application.yml", ("spring", "profiles", "active"))
    profile_config = resources / f"application-{active_profile}.yml"
    if not profile_config.is_file():
        raise EvaluationError(f"当前 Spring Profile 配置文件不存在: {profile_config.name}")

    rag_service = repo / "src/main/java/com/wos/service/impl/RagServiceImpl.java"
    kb_service = repo / "src/main/java/com/wos/service/impl/KnowledgeBaseServiceImpl.java"
    return {
        "springProfile": active_profile,
        "chatModel": read_yaml_scalar(
            profile_config,
            ("spring", "ai", "openai", "chat", "options", "model"),
        ),
        "temperature": float(
            read_yaml_scalar(
                profile_config,
                ("spring", "ai", "openai", "chat", "options", "temperature"),
            )
        ),
        "embeddingModel": read_yaml_scalar(
            profile_config,
            ("spring", "ai", "openai", "embedding", "options", "model"),
        ),
        "topK": read_java_constant(rag_service, "TOP_K", r"\d+", int),
        "similarityThreshold": read_java_constant(
            rag_service,
            "SIMILARITY_THRESHOLD",
            r"\d+(?:\.\d+)?",
            float,
        ),
        "chunkSize": read_java_constant(kb_service, "CHUNK_SIZE", r"\d+", int),
    }


def java_name_uuid(value: str) -> str:
    raw = bytearray(hashlib.md5(value.encode("utf-8")).digest())
    raw[6] = (raw[6] & 0x0F) | 0x30
    raw[8] = (raw[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(raw)))


def parse_kb_sections(kb_path: Path) -> Dict[str, Dict[str, str]]:
    sections: Dict[str, Dict[str, str]] = {}
    category: Optional[str] = None
    for raw_line in kb_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line.startswith("# "):
            category = line[2:].strip()
        elif line.startswith("## "):
            title = line[3:].strip()
            if not category:
                raise EvaluationError(f"知识库条目 {title} 缺少一级分类")
            if title in sections:
                raise EvaluationError(f"知识库存在重复二级标题，评估集无法仅按标题标注: {title}")
            section_id = java_name_uuid(f"kb:{category}/{title}")
            sections[title] = {"title": title, "category": category, "sectionId": section_id}
    if not sections:
        raise EvaluationError("知识库未解析到任何二级标题")
    return sections


def normalize_text(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "").lower()
    return re.sub(r"[\s\W_]+", "", value, flags=re.UNICODE)


def normalize_key_point(raw: Any) -> Dict[str, Any]:
    if isinstance(raw, str):
        return {"label": raw, "anyOf": [raw]}
    if not isinstance(raw, dict):
        raise EvaluationError(f"expectedKeyPoints 元素格式错误: {raw!r}")
    aliases = raw.get("anyOf")
    if not isinstance(aliases, list) or not aliases or not all(isinstance(item, str) and item for item in aliases):
        raise EvaluationError(f"expectedKeyPoints.anyOf 必须是非空字符串数组: {raw!r}")
    return {"label": raw.get("label") or aliases[0], "anyOf": aliases}


def load_dataset(
    dataset_path: Path,
    kb_sections: Dict[str, Dict[str, str]],
    selected_ids: Optional[Sequence[str]],
) -> Dict[str, Any]:
    try:
        dataset = json.loads(dataset_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise EvaluationError(f"读取评估集失败: {exc}") from exc

    if dataset.get("schemaVersion") != 1:
        raise EvaluationError("仅支持 schemaVersion=1")
    if not isinstance(dataset.get("version"), str) or not dataset["version"].strip():
        raise EvaluationError("评估集缺少 version")
    cases = dataset.get("cases")
    if not isinstance(cases, list) or not cases:
        raise EvaluationError("评估集 cases 必须是非空数组")

    selected = set(selected_ids or [])
    seen_ids = set()
    normalized_cases = []
    for raw_case in cases:
        if not isinstance(raw_case, dict):
            raise EvaluationError("每个评估用例必须是 JSON 对象")
        case = dict(raw_case)
        case_id = case.get("id")
        question = case.get("question")
        if not isinstance(case_id, str) or not case_id:
            raise EvaluationError("评估用例缺少 id")
        if case_id in seen_ids:
            raise EvaluationError(f"评估用例 id 重复: {case_id}")
        seen_ids.add(case_id)
        if not isinstance(question, str) or not question.strip() or len(question) > 200:
            raise EvaluationError(f"用例 {case_id} 的 question 必须为 1-200 字")
        if case.get("type") not in VALID_CASE_TYPES:
            raise EvaluationError(f"用例 {case_id} 的 type 非法: {case.get('type')!r}")
        if case.get("expectedOutcome") not in VALID_OUTCOMES:
            raise EvaluationError(f"用例 {case_id} 的 expectedOutcome 非法")
        if not isinstance(case.get("shouldReject"), bool):
            raise EvaluationError(f"用例 {case_id} 缺少 shouldReject 布尔值")
        expected_titles = case.get("expectedSources", [])
        if not isinstance(expected_titles, list) or not all(isinstance(item, str) for item in expected_titles):
            raise EvaluationError(f"用例 {case_id} 的 expectedSources 必须是字符串数组")
        missing_titles = [title for title in expected_titles if title not in kb_sections]
        if missing_titles:
            raise EvaluationError(f"用例 {case_id} 标注了知识库不存在的标题: {missing_titles}")
        case["expectedSourceIds"] = [kb_sections[title]["sectionId"] for title in expected_titles]
        expected_key_points = case.get("expectedKeyPoints")
        if not isinstance(expected_key_points, list):
            raise EvaluationError(f"用例 {case_id} 的 expectedKeyPoints 必须是数组")
        case["expectedKeyPoints"] = [normalize_key_point(item) for item in expected_key_points]
        forbidden = case.get("forbiddenAnyOf", [])
        if not isinstance(forbidden, list) or not all(isinstance(item, str) and item for item in forbidden):
            raise EvaluationError(f"用例 {case_id} 的 forbiddenAnyOf 必须是字符串数组")
        case["forbiddenAnyOf"] = forbidden
        case.setdefault("requireEmptySources", False)
        if not isinstance(case["requireEmptySources"], bool):
            raise EvaluationError(f"用例 {case_id} 的 requireEmptySources 必须是布尔值")
        if not selected or case_id in selected:
            normalized_cases.append(case)

    unknown_ids = selected - seen_ids
    if unknown_ids:
        raise EvaluationError(f"--case-id 不存在: {sorted(unknown_ids)}")
    if not normalized_cases:
        raise EvaluationError("没有选中任何评估用例")
    dataset["cases"] = normalized_cases
    return dataset


def login(session: requests.Session) -> str:
    url = f"{BASE_URL.rstrip('/')}/user/login"
    response = session.post(url, json={"username": USERNAME, "password": PASSWORD}, timeout=(5, 20))
    try:
        payload = response.json()
    except requests.JSONDecodeError as exc:
        raise EvaluationError(f"登录接口未返回 JSON，HTTP {response.status_code}") from exc
    if not isinstance(payload, dict):
        raise EvaluationError(f"登录接口 JSON 根节点必须是对象，实际为 {type(payload).__name__}")
    if response.status_code >= 400 or payload.get("code") != 200 or not isinstance(payload.get("data"), str):
        raise EvaluationError(
            f"登录失败，HTTP {response.status_code}, code={payload.get('code')}, message={payload.get('message')}"
        )
    return payload["data"]


def decode_json_event(event_name: str, payload: str) -> List[Dict[str, Any]]:
    try:
        value = json.loads(payload) if payload else []
    except json.JSONDecodeError as exc:
        raise EvaluationError(f"SSE {event_name} 事件不是合法 JSON: {payload[:200]}") from exc
    if not isinstance(value, list) or not all(isinstance(item, dict) for item in value):
        raise EvaluationError(f"SSE {event_name} 事件必须是 JSON 数组")
    return value


def parse_sse(response: requests.Response) -> Dict[str, Any]:
    content_type = response.headers.get("Content-Type", "")
    if "text/event-stream" not in content_type.lower():
        try:
            payload = response.json()
            detail = f"code={payload.get('code')}, message={payload.get('message')}"
        except (ValueError, AttributeError):
            detail = response.text[:300]
        raise EvaluationError(f"问答接口未返回 SSE，HTTP {response.status_code}, Content-Type={content_type}: {detail}")

    response.encoding = "utf-8"
    answer_chunks: List[str] = []
    sources: List[Dict[str, Any]] = []
    citations: List[Dict[str, Any]] = []
    event_counts: Counter[str] = Counter()
    protocol_errors: List[str] = []
    unknown_events: List[str] = []
    done_seen = False
    current_event = "message"
    data_lines: List[str] = []

    def dispatch() -> None:
        nonlocal current_event, data_lines, sources, citations, done_seen
        if current_event == "message" and not data_lines:
            current_event = "message"
            return
        payload = "\n".join(data_lines)
        event_name = current_event or "message"
        event_counts[event_name] += 1
        if event_name == "answer":
            answer_chunks.append(payload)
        elif event_name == "sources":
            if event_counts[event_name] > 1:
                protocol_errors.append("sources 事件重复")
            try:
                sources = decode_json_event(event_name, payload)
            except EvaluationError as exc:
                protocol_errors.append(str(exc))
        elif event_name == "citations":
            if event_counts[event_name] > 1:
                protocol_errors.append("citations 事件重复")
            try:
                citations = decode_json_event(event_name, payload)
            except EvaluationError as exc:
                protocol_errors.append(str(exc))
        elif event_name == "done":
            if event_counts[event_name] > 1:
                protocol_errors.append("done 事件重复")
            done_seen = True
        else:
            unknown_events.append(event_name)
        current_event = "message"
        data_lines = []

    try:
        for raw_line in response.iter_lines(decode_unicode=True):
            line = (raw_line or "").rstrip("\r")
            if line == "":
                dispatch()
                if done_seen:
                    break
                continue
            if line.startswith(":"):
                continue
            field, separator, value = line.partition(":")
            if separator and value.startswith(" "):
                value = value[1:]
            if field == "event":
                current_event = value.strip() or "message"
            elif field == "data":
                data_lines.append(value)
        if current_event != "message" or data_lines:
            dispatch()
    except requests.RequestException as exc:
        protocol_errors.append(f"读取 SSE 失败: {exc}")

    if event_counts["answer"] < 1:
        protocol_errors.append("answer 事件至少应出现 1 次")
    for required_event in ("sources", "citations", "done"):
        actual_count = event_counts[required_event]
        if actual_count != 1:
            protocol_errors.append(f"{required_event} 事件应恰好出现 1 次，实际 {actual_count} 次")

    source_indexes = [item.get("index") for item in sources]
    if len(source_indexes) != len(set(source_indexes)):
        protocol_errors.append("sources 中存在重复 index")
    source_by_index = {item.get("index"): item for item in sources}
    source_ids = {item.get("sectionId") for item in sources if item.get("sectionId")}
    for citation in citations:
        if citation.get("sectionId") not in source_ids:
            protocol_errors.append(f"引用不在候选来源中: {citation.get('title')}")
        source = source_by_index.get(citation.get("index"))
        if source is None:
            protocol_errors.append(f"引用编号在候选来源中不存在: index={citation.get('index')}")
        elif (
            source.get("title") != citation.get("title")
            or source.get("sectionId") != citation.get("sectionId")
        ):
            protocol_errors.append(f"引用编号与候选来源不一致: index={citation.get('index')}")

    return {
        "answer": "".join(answer_chunks),
        "sources": sources,
        "citations": citations,
        "done": done_seen,
        "eventCounts": dict(event_counts),
        "unknownEvents": unknown_events,
        "protocolErrors": protocol_errors,
    }


def ask_stream(
    session: requests.Session,
    token: str,
    question: str,
    read_timeout: int,
) -> Dict[str, Any]:
    url = f"{BASE_URL.rstrip('/')}/kb/ask/stream"
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "text/event-stream",
        "Content-Type": "application/json",
    }
    started = time.perf_counter()
    try:
        with session.post(
            url,
            json={"question": question},
            headers=headers,
            stream=True,
            timeout=(5, read_timeout),
        ) as response:
            actual = parse_sse(response)
    except requests.Timeout as exc:
        raise EvaluationError(f"问答请求超时: {exc}") from exc
    except requests.RequestException as exc:
        raise EvaluationError(f"问答请求失败: {exc}") from exc
    actual["durationMs"] = round((time.perf_counter() - started) * 1000)
    return actual


def contains_any(text: str, values: Iterable[str]) -> bool:
    normalized = normalize_text(text)
    return any(normalize_text(value) in normalized for value in values if value)


def check_key_points(answer: str, points: Sequence[Dict[str, Any]]) -> Tuple[bool, List[Dict[str, Any]]]:
    normalized_answer = normalize_text(answer)
    details = []
    for point in points:
        matched_alias = next(
            (alias for alias in point["anyOf"] if normalize_text(alias) in normalized_answer),
            None,
        )
        details.append({"label": point["label"], "matched": bool(matched_alias), "matchedAlias": matched_alias})
    return all(item["matched"] for item in details), details


def evaluate_case(case: Dict[str, Any], actual: Dict[str, Any]) -> Dict[str, Any]:
    expected_ids = set(case["expectedSourceIds"])
    source_ids = {item.get("sectionId") for item in actual["sources"] if item.get("sectionId")}
    citation_ids = {item.get("sectionId") for item in actual["citations"] if item.get("sectionId")}
    retrieval_match = expected_ids.issubset(source_ids)
    citation_match = expected_ids.issubset(citation_ids)
    key_points_match, key_point_details = check_key_points(actual["answer"], case["expectedKeyPoints"])
    forbidden_hits = [value for value in case["forbiddenAnyOf"] if contains_any(actual["answer"], [value])]
    answer_is_reject = contains_any(actual["answer"], REJECT_MARKERS)
    citations_empty = not actual["citations"]
    sources_empty = not actual["sources"]
    protocol_ok = not actual["protocolErrors"] and actual["done"]

    if case["type"] == "chat":
        behavior_match = citations_empty and key_points_match
    elif case["shouldReject"]:
        behavior_match = answer_is_reject and citations_empty
        if case["requireEmptySources"]:
            behavior_match = behavior_match and sources_empty
    else:
        behavior_match = (
            retrieval_match
            and citation_match
            and key_points_match
            and not answer_is_reject
            and not forbidden_hits
        )

    regular_pass = protocol_ok and behavior_match
    if not protocol_ok:
        attribution = "protocol"
    elif case["shouldReject"] and case["requireEmptySources"] and not sources_empty:
        attribution = "retrieval"
    elif expected_ids and not retrieval_match:
        attribution = "retrieval"
    elif not behavior_match:
        attribution = "generation"
    else:
        attribution = None

    if not protocol_ok:
        auto_status = "error"
        needs_manual_review = True
    elif case["expectedOutcome"] == "known_limit":
        auto_status = "known_limit_not_reproduced" if regular_pass else "known_limit_reproduced"
        needs_manual_review = True
    else:
        auto_status = "auto_pass" if regular_pass else "auto_fail"
        needs_manual_review = not regular_pass

    checks = {
        "protocol": protocol_ok,
        "retrieval": retrieval_match,
        "citations": citation_match,
        "rejection": answer_is_reject if case["shouldReject"] else not answer_is_reject,
        "emptySources": sources_empty,
        "keyPoints": key_points_match,
        "forbiddenClear": not forbidden_hits,
    }
    warnings = []
    if case["shouldReject"] and not sources_empty and not case["requireEmptySources"]:
        warnings.append("最终拒答正确，但检索层仍召回了候选资料")
    if actual["unknownEvents"]:
        warnings.append(f"收到未知 SSE 事件: {actual['unknownEvents']}")

    return {
        "checks": checks,
        "keyPointDetails": key_point_details,
        "forbiddenHits": forbidden_hits,
        "autoStatus": auto_status,
        "suggestedAttribution": attribution,
        "needsManualReview": needs_manual_review,
        "warnings": warnings,
    }


def build_summary(case_results: Sequence[Dict[str, Any]]) -> Dict[str, Any]:
    regular = [item for item in case_results if item["expectedOutcome"] == "pass"]
    known = [item for item in case_results if item["expectedOutcome"] == "known_limit"]
    by_type: Dict[str, Dict[str, int]] = {}
    for item in regular:
        bucket = by_type.setdefault(item["type"], {"total": 0, "autoPassed": 0, "failed": 0, "errors": 0})
        bucket["total"] += 1
        if item["autoStatus"] == "auto_pass":
            bucket["autoPassed"] += 1
        elif item["autoStatus"] == "auto_fail":
            bucket["failed"] += 1
        elif item["autoStatus"] == "error":
            bucket["errors"] += 1

    attribution_counts = Counter(
        item.get("suggestedAttribution")
        for item in case_results
        if item.get("suggestedAttribution")
    )
    return {
        "regular": {
            "total": len(regular),
            "autoPassed": sum(item["autoStatus"] == "auto_pass" for item in regular),
            "failed": sum(item["autoStatus"] == "auto_fail" for item in regular),
        },
        "knownLimit": {
            "total": len(known),
            "reproduced": sum(item["autoStatus"] == "known_limit_reproduced" for item in known),
            "notReproduced": sum(item["autoStatus"] == "known_limit_not_reproduced" for item in known),
        },
        "errors": sum(item["autoStatus"] == "error" for item in case_results),
        "needsManualReview": sum(bool(item.get("needsManualReview")) for item in case_results),
        "attributionCounts": dict(attribution_counts),
        "byType": by_type,
    }


def markdown_escape(value: Any) -> str:
    return str(value if value is not None else "").replace("|", "\\|").replace("\n", "<br>")


def truncate(value: str, limit: int = 800) -> str:
    value = value.strip()
    return value if len(value) <= limit else value[:limit] + "..."


def render_markdown(result: Dict[str, Any]) -> str:
    metadata = result["metadata"]
    summary = result["summary"]
    lines = [
        f"# RAG 评估报告 {metadata['runId']}",
        "",
        "## 快照",
        "",
        "| 字段 | 值 |",
        "|---|---|",
    ]
    snapshot_fields = [
        ("模式", metadata["mode"]),
        ("开始时间", metadata["startedAt"]),
        ("Git Commit", metadata["gitCommit"]),
        ("运行前工作区", "dirty" if metadata["gitDirtyBeforeRun"] else "clean"),
        ("评估集版本", metadata["datasetVersion"]),
        ("评估集 SHA-256", metadata["datasetSha256"]),
        ("知识库 SHA-256", metadata["kbSha256"]),
        ("Spring Profile", metadata["springProfile"]),
        ("Chat Model", metadata["chatModel"]),
        ("Temperature", metadata["temperature"]),
        ("Embedding Model", metadata["embeddingModel"]),
        ("TopK", metadata["topK"]),
        ("Similarity Threshold", metadata["similarityThreshold"]),
        ("Chunk Size", metadata["chunkSize"]),
        ("用例数", metadata["caseCount"]),
        ("总耗时", f"{metadata['durationSeconds']:.2f}s"),
    ]
    lines.extend(f"| {name} | {markdown_escape(value)} |" for name, value in snapshot_fields)
    lines.extend(
        [
            "",
            "## 汇总",
            "",
            "| 类别 | 总数 | 自动通过/复现 | 失败/未复现 |",
            "|---|---:|---:|---:|",
            f"| 普通用例 | {summary['regular']['total']} | {summary['regular']['autoPassed']} | {summary['regular']['failed']} |",
            f"| 已知局限 | {summary['knownLimit']['total']} | {summary['knownLimit']['reproduced']} | {summary['knownLimit']['notReproduced']} |",
            f"| 协议/请求错误 | {summary['errors']} | - | - |",
            "",
            "> 自动通过只代表结构化规则通过，仍需抽查通过项以防假阳性。",
            "",
            "## 失败与待复核",
            "",
            "| ID | 类型 | 归因建议 | 预期来源 | 实际候选来源 | 实际引用 |",
            "|---|---|---|---|---|---|",
        ]
    )
    failures = [item for item in result["cases"] if item["autoStatus"] in {"auto_fail", "error"}]
    if failures:
        for item in failures:
            lines.append(
                "| {id} | {type} | {attr} | {expected} | {sources} | {citations} |".format(
                    id=markdown_escape(item["id"]),
                    type=markdown_escape(item["type"]),
                    attr=markdown_escape(item.get("suggestedAttribution") or "待人工判断"),
                    expected=markdown_escape(", ".join(item["expectedSources"])),
                    sources=markdown_escape(", ".join(source.get("title", "") for source in item["actual"]["sources"])),
                    citations=markdown_escape(", ".join(source.get("title", "") for source in item["actual"]["citations"])),
                )
            )
    else:
        lines.append("| - | - | - | - | - | - |")

    lines.extend(["", "### 失败详情", ""])
    for item in failures:
        lines.extend(
            [
                f"#### {item['id']} {item['question']}",
                "",
                f"- 自动状态：`{item['autoStatus']}`",
                f"- 归因建议：`{item.get('suggestedAttribution') or '待人工判断'}`",
                f"- 协议错误：{item['actual']['protocolErrors'] or '无'}",
                f"- 缺失关键点：{[point['label'] for point in item['keyPointDetails'] if not point['matched']]}",
                f"- 禁止断言命中：{item['forbiddenHits'] or '无'}",
                f"- 回答：{truncate(item['actual']['answer'])}",
                "",
            ]
        )

    lines.extend(["## 已知局限", ""])
    known = [item for item in result["cases"] if item["expectedOutcome"] == "known_limit"]
    if known:
        lines.extend(["| ID | 状态 | 实际候选来源 | 实际引用 |", "|---|---|---|---|"])
        for item in known:
            lines.append(
                f"| {item['id']} | {item['autoStatus']} | "
                f"{markdown_escape(', '.join(source.get('title', '') for source in item['actual']['sources']))} | "
                f"{markdown_escape(', '.join(source.get('title', '') for source in item['actual']['citations']))} |"
            )
    else:
        lines.append("无。")

    lines.extend(["", "## 自动通过项抽查", ""])
    sampled_ids = set(result.get("manualSampleIds", []))
    sampled = [item for item in result["cases"] if item["id"] in sampled_ids]
    for item in sampled:
        lines.extend(
            [
                f"### {item['id']} {item['question']}",
                "",
                f"- 引用：{', '.join(source.get('title', '') for source in item['actual']['citations']) or '无'}",
                f"- 回答：{truncate(item['actual']['answer'])}",
                "",
            ]
        )
    lines.extend(
        [
            "## 说明",
            "",
            "- `retrieval` 表示预期条目未进入 sources；`generation` 表示已召回但回答、引用或关键点未通过。",
            "- `case_label` 需要人工确认；自动脚本只在知识库不存在标注标题时于运行前直接拒绝执行。",
            "- 报告假设被测服务由本报告记录的干净工作区当前提交构建并启动；本次不增加生产 build-info 接口。",
            "",
        ]
    )
    return "\n".join(lines)


def atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False, dir=path.parent, newline="\n") as file:
        file.write(content)
        temp_path = Path(file.name)
    os.replace(temp_path, path)


def main() -> int:
    args = parse_args()
    script_dir = Path(__file__).resolve().parent
    repo = discover_repo(script_dir)
    dataset_path = Path(args.dataset)
    if not dataset_path.is_absolute():
        dataset_path = script_dir / dataset_path
    dataset_path = dataset_path.resolve()
    kb_path = repo / "src/main/resources/kb/工单知识库.md"

    pre_git = git_snapshot(repo)
    if args.baseline and pre_git["dirty"]:
        raise EvaluationError("正式 baseline 要求运行前工作区干净，请先提交或处理当前改动")
    if args.baseline:
        require_tracked_dataset(repo, dataset_path)

    dataset_hash_before = sha256_file(dataset_path)
    current_rag_snapshot = read_rag_snapshot(repo)
    kb_sections = parse_kb_sections(kb_path)
    dataset = load_dataset(dataset_path, kb_sections, args.case_id)

    mode = "baseline" if args.baseline else "debug"
    if args.baseline:
        run_id = f"baseline-{args.baseline}"
        run_dir = script_dir / "results" / args.baseline
    else:
        run_timestamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
        run_id = f"debug-{run_timestamp}"
        run_dir = script_dir / ".tmp" / run_timestamp
    json_path = run_dir / "result.json"
    md_path = run_dir / "report.md"
    if args.baseline and run_dir.exists():
        raise EvaluationError(f"正式报告目录已存在，请更换版本名: {run_dir.name}")

    started_at = datetime.now(timezone.utc)
    started_clock = time.perf_counter()
    session = requests.Session()
    token = login(session)
    case_results: List[Dict[str, Any]] = []
    total = len(dataset["cases"])
    for index, case in enumerate(dataset["cases"], start=1):
        print(f"[{index}/{total}] {case['id']} {case['question']}", flush=True)
        item: Dict[str, Any] = {
            "id": case["id"],
            "type": case["type"],
            "question": case["question"],
            "expectedOutcome": case["expectedOutcome"],
            "shouldReject": case["shouldReject"],
            "expectedSources": case["expectedSources"],
            "expectedKeyPoints": case["expectedKeyPoints"],
            "note": case.get("note", ""),
        }
        try:
            actual = ask_stream(session, token, case["question"], args.read_timeout)
            evaluation = evaluate_case(case, actual)
            item.update(evaluation)
            item["actual"] = actual
            item["error"] = "; ".join(actual["protocolErrors"]) if item["autoStatus"] == "error" else None
        except EvaluationError as exc:
            item.update(
                {
                    "checks": {},
                    "keyPointDetails": [],
                    "forbiddenHits": [],
                    "autoStatus": "error",
                    "suggestedAttribution": "protocol",
                    "needsManualReview": True,
                    "warnings": [],
                    "actual": {
                        "answer": "",
                        "sources": [],
                        "citations": [],
                        "done": False,
                        "eventCounts": {},
                        "unknownEvents": [],
                        "protocolErrors": [str(exc)],
                        "durationMs": None,
                    },
                    "error": str(exc),
                }
            )
        case_results.append(item)
        if args.delay > 0 and index < total:
            time.sleep(args.delay)

    summary = build_summary(case_results)
    auto_passed = [item["id"] for item in case_results if item["autoStatus"] == "auto_pass"]
    sample_size = min(max(args.sample_size, 0), len(auto_passed))
    seed = f"{pre_git['commit']}:{dataset['version']}:{dataset_hash_before}"
    manual_sample_ids = random.Random(seed).sample(auto_passed, sample_size) if sample_size else []

    if args.baseline:
        if sha256_file(dataset_path) != dataset_hash_before:
            raise EvaluationError("评估运行期间评估集内容发生变化，拒绝生成正式 baseline")
        post_git = git_snapshot(repo)
        if post_git["commit"] != pre_git["commit"] or post_git["dirty"]:
            raise EvaluationError("评估运行期间 HEAD 或工作区发生变化，拒绝生成正式 baseline")

    duration = time.perf_counter() - started_clock
    result = {
        "metadata": {
            "scriptVersion": SCRIPT_VERSION,
            "runId": run_id,
            "mode": mode,
            "startedAt": started_at.isoformat(),
            "durationSeconds": round(duration, 3),
            "baseUrl": BASE_URL.rstrip("/"),
            "gitCommit": pre_git["commit"],
            "gitDirtyBeforeRun": pre_git["dirty"],
            "gitStatusBeforeRun": pre_git["status"] if args.debug else [],
            "datasetVersion": dataset["version"],
            "datasetSha256": dataset_hash_before,
            "kbSha256": sha256_file(kb_path),
            "springProfile": current_rag_snapshot["springProfile"],
            "chatModel": current_rag_snapshot["chatModel"],
            "temperature": current_rag_snapshot["temperature"],
            "embeddingModel": current_rag_snapshot["embeddingModel"],
            "topK": current_rag_snapshot["topK"],
            "similarityThreshold": current_rag_snapshot["similarityThreshold"],
            "chunkSize": current_rag_snapshot["chunkSize"],
            "caseCount": len(case_results),
            "manualSampleSeed": seed,
            "serviceRevisionAssumption": "被测服务由记录的干净工作区当前提交构建并启动",
        },
        "summary": summary,
        "manualSampleIds": manual_sample_ids,
        "cases": case_results,
    }
    atomic_write(json_path, json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    atomic_write(md_path, render_markdown(result))
    print(f"报告已生成: {md_path}")
    print(
        f"普通用例 {summary['regular']['autoPassed']}/{summary['regular']['total']} 自动通过；"
        f"已知局限复现 {summary['knownLimit']['reproduced']}/{summary['knownLimit']['total']}；"
        f"错误 {summary['errors']}。"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except EvaluationError as exc:
        print(f"评估失败: {exc}", file=sys.stderr)
        raise SystemExit(2)
