package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.Result;
import com.wos.domain.pojo.User;
import com.wos.domain.vo.UserDetailVO;
import com.wos.domain.vo.UserVO;

import java.util.List;

public interface IUserService extends IService<User> {

    Result<String> login(String username, String password);

    Result<Void> logout(String token);

    Result<List<UserVO>> listUser();

    Result<UserDetailVO> getUserDetail(Long userId);
}
