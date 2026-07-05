package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "知识库条目(二级标题)")
public class KnowledgeSectionVO {

    @Schema(description = "条目 id(标题派生的确定性 UUID,前端定位与引用跳转用)")
    private String id;

    @Schema(description = "条目标题")
    private String title;

    @Schema(description = "条目内容(Markdown 原文)")
    private String content;
}
