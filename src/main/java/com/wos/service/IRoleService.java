package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.domain.pojo.Role;

import java.util.List;

public interface IRoleService extends IService<Role> {
    List<String> selectCodesByUserId(Long userId);
}
