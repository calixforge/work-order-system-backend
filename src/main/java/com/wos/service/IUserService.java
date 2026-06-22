package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.Result;
import com.wos.domain.pojo.User;

public interface IUserService extends IService<User> {

    Result<String> login(String username, String password);

    Result<Void> logout(String token);
}
