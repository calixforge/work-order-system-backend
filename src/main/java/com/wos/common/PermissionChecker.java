package com.wos.common;


import com.wos.common.enums.RoleEnum;
import com.wos.exception.BusinessException;
import com.wos.service.IRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionChecker {

    private final IRoleService roleService;

    public void checkRole(RoleEnum role) {

        Long userId = UserContext.getUserId();

        if (userId == null) {
            log.warn("权限校验失败: 未登录, requiredRole={}", role.name());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }

        List<String> codes = roleService.selectCodesByUserId(userId);

        if (!codes.contains(role.name())) {
            log.warn("权限校验失败: userId={}, requiredRole={}, actualRoles={}", userId, role.name(), codes);
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限");
        }
    }
}
