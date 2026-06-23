package com.wos.common;


import com.wos.common.enums.RoleEnum;
import com.wos.exception.BusinessException;
import com.wos.service.IRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PermissionChecker {

    private final IRoleService roleService;

    public void checkRole(RoleEnum role) {

        Long userId = UserContext.getUserId();

        if (userId == null) {
           throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }

        List<String> codes = roleService.selectCodesByUserId(userId);

        if (!codes.contains(role.name())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限");
        }
    }
}
