package com.wos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.PermissionChecker;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.common.enums.RoleEnum;
import com.wos.common.enums.WorkOrderStatus;
import com.wos.domain.pojo.Role;
import com.wos.domain.pojo.User;
import com.wos.domain.pojo.UserRole;
import com.wos.domain.pojo.Workorder;
import com.wos.exception.BusinessException;
import com.wos.mapper.UserRoleMapper;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.IRoleService;
import com.wos.service.IUserRoleService;
import com.wos.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.wos.common.RedisConstants.USER_ROLE_KEY;

/**
 * 用户角色关联:管理员分配 / 剥夺角色,并在变更后失效该用户的角色缓存。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements IUserRoleService {

    private final PermissionChecker permissionChecker;

    private final StringRedisTemplate stringRedisTemplate;

    private final IUserService userService;

    private final IRoleService roleService;

    private final WorkorderMapper workorderMapper;

    @Override
    public Result<Void> assignRole(Long userId, Long roleId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!Objects.equals(user.getStatus(), 1)) {
            throw new BusinessException("用户已停用,无法分配角色");
        }
        Role role = roleService.getById(roleId);
        if (role == null) {
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
        log.info("管理员分配角色: adminId={}, userId={}, roleId={}, roleCode={}",
                UserContext.getUserId(), userId, roleId, role.getCode());
        return Result.success();
    }

    @Override
    public Result<Void> revokeRole(Long userId, Long roleId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        Role role = roleService.getById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }

        boolean exists = lambdaQuery()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, roleId)
                .count() > 0;
        if (!exists) {
            throw new BusinessException("该用户未拥有此角色");
        }

        checkNoActiveWorkorderObligation(userId, role);

        // 中间表无逻辑删除,按 (user_id, role_id) 物理删除。
        lambdaUpdate()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, roleId)
                .remove();

        stringRedisTemplate.delete(USER_ROLE_KEY + userId);
        log.info("管理员剥夺角色: adminId={}, userId={}, roleId={}, roleCode={}",
                UserContext.getUserId(), userId, roleId, role.getCode());
        return Result.success();
    }

    private void checkNoActiveWorkorderObligation(Long userId, Role role) {
        if (RoleEnum.SUBMITTER.name().equals(role.getCode())) {
            Long count = workorderMapper.selectCount(new LambdaQueryWrapper<Workorder>()
                    .eq(Workorder::getCreatorId, userId)
                    .notIn(Workorder::getStatus,
                            WorkOrderStatus.DRAFT.name(),
                            WorkOrderStatus.CLOSED.name(),
                            WorkOrderStatus.CANCELED.name()));
            if (count > 0) {
                throw new BusinessException(ResultCode.CONFLICT, "该用户仍有未结束的提单工单,无法剥夺提单人角色");
            }
        }

        if (RoleEnum.HANDLER.name().equals(role.getCode())) {
            Long count = workorderMapper.selectCount(new LambdaQueryWrapper<Workorder>()
                    .eq(Workorder::getAssigneeId, userId)
                    .notIn(Workorder::getStatus,
                            WorkOrderStatus.CLOSED.name(),
                            WorkOrderStatus.CANCELED.name()));
            if (count > 0) {
                throw new BusinessException(ResultCode.CONFLICT, "该用户仍有未结束的负责工单,无法剥夺接单人角色");
            }
        }
    }
}
