package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.Result;
import com.wos.domain.pojo.Role;
import com.wos.domain.vo.RoleVO;

import java.util.List;

/**
 * 角色业务接口。
 */
public interface IRoleService extends IService<Role> {

    /**
     * 查询用户拥有的角色编码,用于 Service 层权限校验。
     */
    List<String> selectCodesByUserId(Long userId);

    Result<List<RoleVO>> listRole();

    /**
     * 查询用户拥有的角色(id + name + code),给详情/管理页展示用,不走缓存。
     */
    List<RoleVO> selectRoleVOByUserId(Long userId);
}
