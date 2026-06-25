package com.wos.service.impl;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.PageResult;
import com.wos.common.PermissionChecker;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.enums.RoleEnum;
import com.wos.domain.dto.UserQueryDTO;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    public Result<PageResult<UserVO>> userList(UserQueryDTO queryDTO) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        String keyword = queryDTO.getKeyword() == null ? null : queryDTO.getKeyword().trim();
        Page<User> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        lambdaQuery()
                .and(keyword != null && !keyword.isBlank(), q -> q
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getRealName, keyword))
                .orderByDesc(User::getCreateTime)
                .page(page);

        List<User> users = page.getRecords();
        Map<Long, String> departmentNameMap = getDepartmentNameMap(users);

        List<UserVO> userVOS = users.stream().map((user) ->
        {
            UserVO vo = new UserVO();
            fillUserVO(user, vo, departmentNameMap);
            return vo;
        }).toList();

        return Result.success(new PageResult<>(page.getTotal(), page.getPages(), userVOS));
    }

    @Override
    public Result<UserDetailVO> getUserDetail(Long userId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        UserDetailVO vo = new UserDetailVO();
        fillUserVO(user, vo, getDepartmentNameMap(List.of(user)));
        vo.setRoles(roleService.selectRoleVOByUserId(userId));
        return Result.success(vo);
    }

    @Override
    public Result<PageResult<UserVO>> userHandlersList(UserQueryDTO queryDTO) {
        permissionChecker.checkRole(RoleEnum.DISPATCHER);

        if (queryDTO.getKeyword() != null) {
            queryDTO.setKeyword(queryDTO.getKeyword().trim());
        }

        Page<UserVO> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<UserVO> resultPage = baseMapper.userHandlersList(page, queryDTO);
        return Result.success(PageResult.of(resultPage));
    }

    private Map<Long, String> getDepartmentNameMap(List<User> users) {
        Set<Long> departmentIds = users.stream()
                .map(User::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (departmentIds.isEmpty()) {
            return Map.of();
        }

        return departmentService.listByIds(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
    }

    private void fillUserVO(User user, UserVO vo, Map<Long, String> departmentNameMap) {
        BeanUtils.copyProperties(user, vo);
        vo.setDepartmentName(user.getDepartmentId() == null ? null : departmentNameMap.get(user.getDepartmentId()));
    }
}
