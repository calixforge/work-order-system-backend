package com.wos.controller;

import com.wos.common.Result;
import com.wos.domain.dto.KnowledgeAskDTO;
import com.wos.domain.vo.KnowledgeCategoryVO;
import com.wos.service.IKnowledgeBaseService;
import com.wos.service.IRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "知识库")
@RequestMapping("/kb")
@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final IKnowledgeBaseService knowledgeBaseService;

    private final IRagService ragService;

    /**
     * 知识库目录树:一级标题分类 → 二级标题条目(含 Markdown 内容)。
     * 全量返回(约 11KB),前端一次拉取后本地导航;登录即可访问。
     */
    @Operation(summary = "知识库目录")
    @GetMapping("/catalog")
    public Result<List<KnowledgeCategoryVO>> catalog() {

        return Result.success(knowledgeBaseService.getCatalog());
    }

    /**
     * 智能问答流式输出:前端用 fetch reader 读取 SSE,问题仍通过 POST body 传递。
     */
    @Operation(summary = "智能问答流式输出")
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody KnowledgeAskDTO dto) {

        return ragService.askStream(dto.getQuestion());
    }
}
