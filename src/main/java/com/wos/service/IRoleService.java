package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.domain.pojo.Role;

import java.util.List;

/**
 * 角色业务接口。
 */
public interface IRoleService extends IService<Role> {

    /**
     * 查询用户拥有的角色编码,用于 Service 层权限校验。
     */
    List<String> selectCodesByUserId(Long userId);
}
