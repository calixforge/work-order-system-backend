package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "Agent 当前登录用户")
public class AgentCurrentUserVO {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "用户拥有的角色")
    private List<RoleVO> roles;
}
