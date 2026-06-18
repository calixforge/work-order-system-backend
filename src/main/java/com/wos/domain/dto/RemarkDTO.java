package com.wos.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class RemarkDTO {

    @Schema(description = "备注")
    @NotBlank(message = "备注不能为空")
    private String remark;
}
