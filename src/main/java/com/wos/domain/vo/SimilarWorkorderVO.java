package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "相似历史工单")
public class SimilarWorkorderVO {

    @Schema(description = "对外工单编号")
    private String workorderCode;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "问题描述")
    private String description;

    @Schema(description = "处理结果/解决说明")
    private String resolutionSummary;

    @Schema(description = "完成时间")
    private String completeTime;

    @Schema(description = "相似度")
    private Double score;
}
