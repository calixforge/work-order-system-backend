package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.Result;
import com.wos.domain.pojo.UserRole;

/**
 * 用户角色关联业务接口:管理员分配 / 剥夺用户角色。
 */
public interface IUserRoleService extends IService<UserRole> {

    /**
     * 给用户分配角色(仅管理员)。
     */
    Result<Void> assignRole(Long userId, Long roleId);

    /**
     * 剥夺用户的某个角色(仅管理员)。
     */
    Result<Void> revokeRole(Long userId, Long roleId);
}
