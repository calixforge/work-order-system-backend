package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "问答引用条目")
public class RagCitationVO {

    @Schema(description = "资料编号(对应回答中的 [资料n])")
    private Integer index;

    @Schema(description = "知识库条目标题")
    private String title;

    @Schema(description = "知识库条目 id,前端跳转定位用")
    private String sectionId;
}
