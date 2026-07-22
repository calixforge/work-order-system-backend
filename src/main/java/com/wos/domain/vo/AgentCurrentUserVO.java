package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Agent 当前登录用户")
public class AgentCurrentUserVO {

    @Schema(description = "用户 ID")
    private Long userId;
}
