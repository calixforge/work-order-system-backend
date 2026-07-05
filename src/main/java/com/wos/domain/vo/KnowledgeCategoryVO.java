package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "知识库分类(一级标题)")
public class KnowledgeCategoryVO {

    @Schema(description = "分类名")
    private String category;

    @Schema(description = "该分类下的条目")
    private List<KnowledgeSectionVO> sections;
}
