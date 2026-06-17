package com.wos.domain.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkorderCreateDTO {

    @Schema(description = "标题")
    @NotBlank(message = "标题不能为空")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "优先级:1高 2中 3低")
    @NotNull(message = "优先级不能为空")
    @Min(1) @Max(3)
    private Integer priority;

    @Schema(description = "true=直接提交、false=存草稿")
    @NotNull(message = "请选择提交或草稿")
    private Boolean submit;


}
