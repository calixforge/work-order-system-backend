# RAG 评估工具

用于对知识库问答的真实登录与 SSE 链路执行批量回归，评估集严格来自
`src/main/resources/kb/工单知识库.md`。

## 文件说明

```text
tools/eval/
├── evaluation-set.json   # 固定问题、预期资料、关键点与已知局限
├── run_eval.py           # 登录、调用 SSE、自动断言并生成报告
├── requirements.txt      # Python 依赖
└── results/              # 正式 baseline 报告，每个版本一个目录
```

每次运行的 Markdown 与 JSON 放在同一个目录中，文件名固定：

```text
tools/eval/.tmp/
└── 20260710-202357-378629/
    ├── report.md
    └── result.json

tools/eval/results/
└── v1/
    ├── report.md
    └── result.json
```

调试目录 `.tmp/` 由仓库根目录 `.gitignore` 统一忽略；正式 baseline 目录需要提交仓库。
`report.md` 供人工阅读，`result.json` 保存所有用例的结构化原始结果，用于后续脚本统计和不同
baseline 的自动对比。

## 评估范围

- `sources`：检索阶段召回的候选知识条目，用于判断检索层是否命中。
- `citations`：模型回答实际使用的合法引用，用于判断生成与引用层。
- `answer`：按到达顺序拼接 SSE 文本分片，用于关键点、拒答和 Grounding 检查。
- `done`：正常结束标志；连接断开但没有 `done` 视为协议错误。

脚本不会使用 EventSource 类库，而是通过 `requests` 的 `stream=True` 手工解析
`event:` / `data:` 行，以便携带 `Authorization` 请求头。

## 准备环境

使用 Python 3.9+，在评估工具目录创建项目独立的虚拟环境，避免把依赖安装到本机全局 Python：

```powershell
cd F:\my_project\work-order-system\tools\eval
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

不需要激活虚拟环境，直接使用 `.venv` 中的 `python.exe` 即可。这样也不会受到 PowerShell
执行策略禁止运行 `Activate.ps1` 的影响。激活本质上只是把 `.venv\Scripts` 放到当前终端的
`PATH` 最前面，并不是运行脚本的必要步骤。

脚本顶部只保存本地连接信息：

- 后端地址：`http://localhost:8080`
- 登录账号：`admin / admin123`

其余参数不需要手动填写，也不会在 Python 中保存副本。脚本运行时自动：

- 从 `application.yml` 读取当前 Spring Profile。
- 从对应的 `application-{profile}.yml` 读取 Chat 模型、`temperature` 和 Embedding 模型。
- 从 `RagServiceImpl.java` 读取 `TOP_K` 与 `SIMILARITY_THRESHOLD`。
- 从 `KnowledgeBaseServiceImpl.java` 读取 `CHUNK_SIZE`。

因此后端代码或模型配置变化后，评估报告会自动记录新值；读取不到或出现多个同名常量时直接报错，
不会继续使用旧数据。

系统采用“单用户单 Token”，脚本使用 `admin` 重新登录会覆盖 Redis 中该账号原来的 Token；
如果浏览器也在使用 `admin`，浏览器登录可能失效。这里的默认账号仅适用于当前本地开发环境，
不要直接照搬到生产环境。

## 调试运行

调试模式允许 Git 工作区不干净，报告写入 `.tmp/`。首次运行建议先执行一条用例，确认登录、
SSE 解析和报告生成链路正常：

```powershell
.\.venv\Scripts\python.exe run_eval.py --debug --case-id direct-001
```

确认单题链路正常后，再执行完整评估集：

```powershell
.\.venv\Scripts\python.exe run_eval.py --debug
```

需要定位特定问题时，可以重复传入 `--case-id`：

```powershell
.\.venv\Scripts\python.exe run_eval.py --debug --case-id direct-001 --case-id reject-001
```

## 正式 baseline

正式 baseline 的正确顺序：

1. 调试脚本与评估集，修正标注错误。
2. 提交脚本和评估集。
3. 确认 `git status --porcelain` 没有输出。
4. 确认当前运行的后端由这个干净提交构建并启动。
5. 运行正式 baseline。
6. 人工检查失败项，并抽查报告选出的 3-5 条自动通过项。
7. 单独提交生成的 baseline 报告。

```powershell
.\.venv\Scripts\python.exe run_eval.py --baseline v1
```

正式模式会：

- 运行前检查 Git Commit 与工作区状态，dirty 时直接拒绝执行。
- 拒绝 `--case-id` 子集运行，确保 baseline 一定覆盖完整评估集。
- 要求评估集位于当前仓库内且已经纳入 Git 跟踪。
- 从当前 Spring 配置和 Java 实现类自动读取模型名称、temperature 与 RAG 参数快照。
- 记录评估集与知识库的 SHA-256、模型名称、参数快照和运行耗时。
- 运行结束、写报告之前再次检查 HEAD、工作区和评估集内容均未变化。
- 生成 `tools/eval/results/v1/report.md` 与 `result.json`。
- 拒绝覆盖已经存在的正式版本目录，下一次请使用新的版本名。

报告生成后工作区出现新的版本目录属于正常现象；检查无误后再提交整个目录。

## 结果解释

- `retrieval`：预期条目没有进入 `sources`，优先检查 Embedding、TopK、阈值或查询表达。
- `generation`：资料已经进入 `sources`，但回答、关键点、拒答或 `citations` 不符合预期。
- `protocol`：鉴权、超时、Content-Type、SSE JSON，或 `sources`、`citations`、`done` 未恰好出现一次等链路错误。
- `case_label`：需要人工确认评估用例的 Ground Truth 是否标错；脚本无法仅靠模型输出自动判定。

`known_limit` 用例单独统计，不进入普通通过率：

- `known_limit_reproduced`：当前已知边界仍然存在。
- `known_limit_not_reproduced`：本次意外完整回答，可能代表改善，也可能是模型随机性，需要人工复核。

协议错误统一记为 `error`，不会被误算成普通失败或“已知局限复现”。

自动关键点匹配只是启发式判断。正式报告必须检查所有失败案例，并抽查 3-5 条自动通过案例，
防止“引用正确但答案答歪”的假阳性。

## 当前用例结构

评估集覆盖：

- 不照抄标题的直接语义命中。
- 双条资料组合回答。
- IT/OA 域内但知识库未覆盖的问题。
- 库外拒答与闲聊。
- 拼房、临界天数、未知城市分类等 Grounding 边界。
- 多个无关意图混合造成查询语义稀释的已知局限。

新增或修改用例时，`expectedSources` 必须使用知识库真实的二级标题。脚本会在运行前解析
知识库并校验标题，同时按照后端相同规则计算确定性 `sectionId`，避免评估集自身出现假标签。
