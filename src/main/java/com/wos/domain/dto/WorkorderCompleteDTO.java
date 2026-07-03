package com.wos.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkorderCompleteDTO {

    @Schema(description = "处理结果/解决说明")
    @NotBlank(message = "处理结果不能为空")
    @Size(max = 1000, message = "处理结果不能超过1000字")
    private String resolutionSummary;
}
