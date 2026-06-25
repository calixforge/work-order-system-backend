package com.wos.domain.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserDetailVO extends UserVO {

    @Schema(description = "用户拥有的角色")
    private List<RoleVO> roles;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "用户头像地址")
    private String avatarUrl;

}
