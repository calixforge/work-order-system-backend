package com.wos.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "知识库问答参数")
public class KnowledgeAskDTO {

    @Schema(description = "问题")
    @NotBlank(message = "问题不能为空")
    @Size(max = 200, message = "问题不能超过200字")
    private String question;
}
