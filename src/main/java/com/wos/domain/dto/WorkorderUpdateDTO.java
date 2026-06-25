package com.wos.domain.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 编辑草稿工单请求参数。
 * 仅 DRAFT 状态、且由创建人本人编辑;status/creatorId/departmentId 等由后端控制,前端不能改。
 */
@Data
public class WorkorderUpdateDTO {

    @Schema(description = "标题")
    @NotBlank(message = "标题不能为空")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "优先级:1高 2中 3低")
    @NotNull(message = "优先级不能为空")
    @Min(1) @Max(3)
    private Integer priority;
}
