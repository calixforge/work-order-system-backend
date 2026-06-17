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

        // 登录名唯一,只按 username 查一条用户记录。
        User user = lambdaQuery().eq(User::getUsername, username).one();

        // 登录失败统一提示,避免泄露“账号存在但密码错误”等细节。
        if(user == null || !PasswordUtil.matches(password, user.getPassword())){
            return Result.fail(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }

        // token 只保存 userId,角色/数据权限在业务接口中实时查询和校验。
        return Result.success(jwtUtil.createToken(user.getId()));
    }
}
