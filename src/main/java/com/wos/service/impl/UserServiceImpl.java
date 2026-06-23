package com.wos.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.PermissionChecker;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.enums.RoleEnum;
import com.wos.domain.pojo.Department;
import com.wos.domain.pojo.User;
import com.wos.domain.vo.UserDetailVO;
import com.wos.domain.vo.UserVO;
import com.wos.exception.BusinessException;
import com.wos.mapper.UserMapper;
import com.wos.service.IDepartmentService;
import com.wos.service.IRoleService;
import com.wos.service.IUserService;
import com.wos.util.JwtUtil;
import com.wos.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.wos.common.RedisConstants.LOGIN_TOKEN_EXPIRE_MINUTES;
import static com.wos.common.RedisConstants.LOGIN_TOKEN_KEY;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final IDepartmentService departmentService;

    private final IRoleService roleService;

    private final JwtUtil jwtUtil;

    private final StringRedisTemplate stringRedisTemplate;

    private final PermissionChecker permissionChecker;

    @Override
    public Result<String> login(String username, String password) {

        // 登录名唯一,只按 username 查一条用户记录。
        User user = lambdaQuery().eq(User::getUsername, username).one();

        // 登录失败统一提示,避免泄露“账号存在但密码错误”等细节。
        if(user == null || !PasswordUtil.matches(password, user.getPassword())){
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }

        String token = jwtUtil.createToken(user.getId());

        //将token存入redis
        stringRedisTemplate.opsForValue().set(
                LOGIN_TOKEN_KEY + token,
                String.valueOf(user.getId()),
                LOGIN_TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // token 只保存 userId,角色/数据权限在业务接口中实时查询和校验。
        return Result.success(token);
    }

    @Override
    public Result<Void> logout(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        stringRedisTemplate.delete(LOGIN_TOKEN_KEY + token);

        return Result.success();
    }

    @Override
    public Result<List<UserVO>> listUser() {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        List<User> list = list();

        List<UserVO> userVOS = list.stream().map((user) ->
        {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(user, vo);
            return vo;
        }).toList();

        return Result.success(userVOS);
    }

    @Override
    public Result<UserDetailVO> getUserDetail(Long userId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        Long departmentId = user.getDepartmentId();
        Department department = departmentId == null ? null : departmentService.getById(departmentId);
        UserDetailVO vo = new UserDetailVO();
        BeanUtils.copyProperties(user, vo);
        vo.setDepartmentName(department == null ? null : department.getName());
        vo.setRoles(roleService.selectRoleVOByUserId(userId));
        return Result.success(vo);
    }
}
