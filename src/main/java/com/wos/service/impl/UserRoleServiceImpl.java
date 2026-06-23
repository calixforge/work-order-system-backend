package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.PermissionChecker;
import com.wos.common.Result;
import com.wos.common.enums.RoleEnum;
import com.wos.domain.pojo.UserRole;
import com.wos.exception.BusinessException;
import com.wos.mapper.UserRoleMapper;
import com.wos.service.IRoleService;
import com.wos.service.IUserRoleService;
import com.wos.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static com.wos.common.RedisConstants.USER_ROLE_KEY;

/**
 * 用户角色关联:管理员分配 / 剥夺角色,并在变更后失效该用户的角色缓存。
 */
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements IUserRoleService {

    private final PermissionChecker permissionChecker;

    private final StringRedisTemplate stringRedisTemplate;

    private final IUserService userService;

    private final IRoleService roleService;

    @Override
    public Result<Void> assignRole(Long userId, Long roleId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        if (userService.getById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
        if (roleService.getById(roleId) == null) {
            throw new BusinessException("角色不存在");
        }

        // 已有该角色则不重复授权(在撞 uk_user_role 唯一键之前先拦)。
        boolean exists = lambdaQuery()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, roleId)
                .count() > 0;
        if (exists) {
            throw new BusinessException("该用户已拥有此角色");
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        save(userRole);

        // 角色变更:删除该用户的角色缓存,下次读自动回源重建。
        stringRedisTemplate.delete(USER_ROLE_KEY + userId);
        return Result.success();
    }

    @Override
    public Result<Void> revokeRole(Long userId, Long roleId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        // 中间表无逻辑删除,按 (user_id, role_id) 物理删除;不存在则删 0 行,无害。
        boolean removed = lambdaUpdate()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, roleId)
                .remove();

        if (!removed) {
            throw new BusinessException("该用户未拥有此角色");
        }

        stringRedisTemplate.delete(USER_ROLE_KEY + userId);
        return Result.success();
    }
}
