package com.wos.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class AssignDTO {

    @NotNull(message = "接单人不能为空")
    @Schema(description = "接单人id")
    private Long assigneeId;

    @Schema(description = "工单优先级")
    @Min(1)
    @Max(3)
    private Integer priority;
}
