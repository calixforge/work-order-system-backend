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

}
