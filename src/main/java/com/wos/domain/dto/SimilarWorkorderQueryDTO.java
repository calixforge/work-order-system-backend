package com.wos.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "相似历史工单查询参数")
public class SimilarWorkorderQueryDTO {

    @Schema(description = "查询内容")
    @NotBlank(message = "查询内容不能为空")
    @Size(max = 100, message = "查询内容不能超过100字")
    private String query;
}
