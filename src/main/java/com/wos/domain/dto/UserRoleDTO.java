package com.wos.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRoleDTO {

    @NotNull(message = "用户id不能为空")
    @Schema(description = "用户id")
    private Long userId;

    @NotNull(message = "角色id不能为空")
    @Schema(description = "角色id")
    private Long roleId;
}
