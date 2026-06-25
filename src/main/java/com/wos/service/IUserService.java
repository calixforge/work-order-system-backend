package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.domain.dto.ChangePasswordDTO;
import com.wos.domain.dto.ResetPasswordDTO;
import com.wos.domain.dto.UserCreateDTO;
import com.wos.domain.dto.UserQueryDTO;
import com.wos.domain.dto.UserUpdateDTO;
import com.wos.domain.pojo.User;
import com.wos.domain.vo.UserDetailVO;
import com.wos.domain.vo.UserVO;

public interface IUserService extends IService<User> {

    Result<String> login(String username, String password);

    Result<Void> logout(String token);

    Result<PageResult<UserVO>> userList(UserQueryDTO queryDTO);

    Result<Long> createUser(UserCreateDTO dto);

    Result<UserDetailVO> getUserDetail(Long userId);

    Result<Void> updateUser(Long userId, UserUpdateDTO dto);

    Result<Void> disableUser(Long userId);

    Result<Void> enableUser(Long userId);

    Result<PageResult<UserVO>> userHandlersList(UserQueryDTO queryDTO);

    Result<UserDetailVO> userinfo();

    Result<Void> changePassword(ChangePasswordDTO dto);

    Result<Void> resetPassword(Long userId, ResetPasswordDTO dto);
}
