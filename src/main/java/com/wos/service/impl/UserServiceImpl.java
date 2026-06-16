package com.wos.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.domain.pojo.User;
import com.wos.mapper.UserMapper;
import com.wos.service.IUserService;
import com.wos.util.JwtUtil;
import com.wos.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    private final JwtUtil jwtUtil;

    @Override
    public Result<String> login(String username, String password) {

        User user = lambdaQuery().eq(User::getUsername, username).one();

        if(user == null || !PasswordUtil.matches(password, user.getPassword())){
            return Result.fail(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }

        return Result.success(jwtUtil.createToken(user.getId()));
    }
}
