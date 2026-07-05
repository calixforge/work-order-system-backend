package com.wos.controller;

import com.wos.common.Result;
import com.wos.domain.vo.KnowledgeCategoryVO;
import com.wos.service.IKnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "知识库")
@RequestMapping("/kb")
@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final IKnowledgeBaseService knowledgeBaseService;

    /**
     * 知识库目录树:一级标题分类 → 二级标题条目(含 Markdown 内容)。
     * 全量返回(约 11KB),前端一次拉取后本地导航;登录即可访问。
     */
    @Operation(summary = "知识库目录")
    @GetMapping("/catalog")
    public Result<List<KnowledgeCategoryVO>> catalog() {

        return Result.success(knowledgeBaseService.getCatalog());
    }
}
