package com.wos.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.PageResult;
import com.wos.common.PermissionChecker;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.common.enums.RoleEnum;
import com.wos.common.enums.WorkOrderStatus;
import com.wos.domain.dto.ChangePasswordDTO;
import com.wos.domain.dto.ResetPasswordDTO;
import com.wos.domain.dto.UserCreateDTO;
import com.wos.domain.dto.UserQueryDTO;
import com.wos.domain.dto.UserUpdateDTO;
import com.wos.domain.pojo.Department;
import com.wos.domain.pojo.User;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.vo.UserDetailVO;
import com.wos.domain.vo.UserVO;
import com.wos.exception.BusinessException;
import com.wos.mapper.UserMapper;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.IDepartmentService;
import com.wos.service.IRoleService;
import com.wos.service.IUserService;
import com.wos.util.JwtUtil;
import com.wos.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.wos.common.RedisConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final String DEFAULT_AVATAR_URL = "/avatar/default.svg";

    private final IDepartmentService departmentService;

    private final IRoleService roleService;

    private final JwtUtil jwtUtil;

    private final StringRedisTemplate stringRedisTemplate;

    private final PermissionChecker permissionChecker;

    private final WorkorderMapper workorderMapper;

    @Override
    public Result<String> login(String username, String password) {

        // 登录名唯一,只按 username 查一条用户记录。
        User user = lambdaQuery().eq(User::getUsername, username).one();

        // 登录失败统一提示,避免泄露“账号存在但密码错误”等细节。
        if(user == null || !PasswordUtil.matches(password, user.getPassword())){
            log.warn("用户登录失败: username={}", username);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }

        // 只允许启用账号登录;null 或非法状态都按不可登录处理。
        if (!Objects.equals(user.getStatus(), 1)) {
            log.warn("停用账号尝试登录: userId={}, username={}", user.getId(), user.getUsername());
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被停用,请联系管理员");
        }

        String token = jwtUtil.createToken(user.getId());

        // 一个用户只保留一个有效 token;重复登录会覆盖旧 token,旧 token 立即失效。
        stringRedisTemplate.opsForValue().set(
                LOGIN_USER_TOKEN_KEY + user.getId(),
                token,
                LOGIN_TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
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

        stringRedisTemplate.delete(LOGIN_USER_TOKEN_KEY + UserContext.getUserId());

        log.info("用户退出登录: userId={}", UserContext.getUserId());
        return Result.success();
    }

    @Override
    public Result<PageResult<UserVO>> userList(UserQueryDTO queryDTO) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        String keyword = queryDTO.getKeyword() == null ? null : queryDTO.getKeyword().trim();
        Page<User> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        lambdaQuery()
                .eq(queryDTO.getStatus() != null, User::getStatus, queryDTO.getStatus())
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
    public Result<Long> createUser(UserCreateDTO dto) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        String username = dto.getUsername().trim();
        String realName = dto.getRealName().trim();
        String phone = normalizeBlank(dto.getPhone());

        checkUsernameAvailable(username, null);
        checkPhoneAvailable(phone, null);
        checkDepartmentExists(dto.getDepartmentId());

        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setRealName(realName);
        user.setPhone(phone);
        user.setAvatarUrl(DEFAULT_AVATAR_URL);
        user.setDepartmentId(dto.getDepartmentId());
        user.setStatus(1);
        save(user);

        log.info("管理员创建用户: adminId={}, userId={}, username={}, departmentId={}",
                UserContext.getUserId(), user.getId(), user.getUsername(), user.getDepartmentId());
        return Result.success(user.getId());
    }

    @Override
    public Result<UserDetailVO> getUserDetail(Long userId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);
        return Result.success(buildUserDetailVO(userId));
    }

    @Override
    public Result<Void> updateUser(Long userId, UserUpdateDTO dto) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        String username = dto.getUsername().trim();
        String realName = dto.getRealName().trim();
        String phone = normalizeBlank(dto.getPhone());

        checkUsernameAvailable(username, userId);
        checkPhoneAvailable(phone, userId);
        checkDepartmentExists(dto.getDepartmentId());

        user.setUsername(username);
        user.setRealName(realName);
        user.setPhone(phone);
        user.setDepartmentId(dto.getDepartmentId());
        updateById(user);

        log.info("管理员更新用户: adminId={}, userId={}, username={}, departmentId={}",
                UserContext.getUserId(), userId, user.getUsername(), user.getDepartmentId());
        return Result.success();
    }

    @Override
    public Result<Void> disableUser(Long userId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        if (Objects.equals(userId, UserContext.getUserId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能停用当前登录用户");
        }

        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        checkNoActiveWorkorderObligation(userId);

        // 停用而非删除:用户记录保留,历史工单仍能查到 realName。
        // 停用后不能登录、不进派单候选,但角色关系保留(可重新启用)。
        user.setStatus(0);
        updateById(user);

        // 踢下线 + 清角色缓存。
        stringRedisTemplate.delete(LOGIN_USER_TOKEN_KEY + userId);
        stringRedisTemplate.delete(USER_ROLE_KEY + userId);

        log.info("管理员停用用户: adminId={}, userId={}", UserContext.getUserId(), userId);
        return Result.success();
    }

    private void checkNoActiveWorkorderObligation(Long userId) {
        Long submittedCreatedCount = workorderMapper.selectCount(new LambdaQueryWrapper<Workorder>()
                .eq(Workorder::getCreatorId, userId)
                .notIn(Workorder::getStatus,
                        WorkOrderStatus.DRAFT.name(),
                        WorkOrderStatus.CLOSED.name(),
                        WorkOrderStatus.CANCELED.name()));
        if (submittedCreatedCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该用户仍有未结束的提单工单,无法停用");
        }

        Long assignedCount = workorderMapper.selectCount(new LambdaQueryWrapper<Workorder>()
                .eq(Workorder::getAssigneeId, userId)
                .notIn(Workorder::getStatus,
                        WorkOrderStatus.CLOSED.name(),
                        WorkOrderStatus.CANCELED.name()));
        if (assignedCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该用户仍有未结束的负责工单,无法停用");
        }
    }

    @Override
    public Result<Void> enableUser(Long userId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        user.setStatus(1);
        updateById(user);
        stringRedisTemplate.delete(USER_ROLE_KEY + userId);

        log.info("管理员启用用户: adminId={}, userId={}", UserContext.getUserId(), userId);
        return Result.success();
    }

    /**
     * 根据 userId 组装用户详情 VO(基本信息 + 部门名 + 角色),不含权限校验。
     * 供管理员查看他人详情(getUserDetail)与当前登录人查看自己(userinfo)共用。
     */
    private UserDetailVO buildUserDetailVO(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        UserDetailVO vo = new UserDetailVO();
        // copyProperties 自动带上 phone/avatarUrl(同名字段);UserDetailVO 无 password 字段,不会泄露。
        fillUserVO(user, vo, getDepartmentNameMap(List.of(user)));
        vo.setRoles(roleService.selectRoleVOByUserId(userId));
        return vo;
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

    @Override
    public Result<UserDetailVO> userinfo() {
        // 当前登录人查看自己:从登录态拿 userId,无需管理员权限。
        return Result.success(buildUserDetailVO(UserContext.getUserId()));
    }

    @Override
    public Result<Void> changePassword(ChangePasswordDTO dto) {
        // 改自己的密码:目标用户取登录态。
        User user = getById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或登录已失效");
        }
        // 必须先验证原密码,防止借用已登录会话直接改密。
        if (!PasswordUtil.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "原密码错误");
        }
        // 新旧相同没有意义,直接拦掉。
        if (PasswordUtil.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能与原密码相同");
        }

        user.setPassword(PasswordUtil.encode(dto.getNewPassword()));
        updateById(user);
        stringRedisTemplate.delete(LOGIN_USER_TOKEN_KEY + UserContext.getUserId());
        log.info("用户修改密码: userId={}", UserContext.getUserId());
        return Result.success();
    }

    @Override
    public Result<Void> resetPassword(Long userId, ResetPasswordDTO dto) {
        // 管理员重置他人密码:不校验原密码,直接覆盖。
        permissionChecker.checkRole(RoleEnum.ADMIN);

        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        user.setPassword(PasswordUtil.encode(dto.getNewPassword()));
        updateById(user);
        stringRedisTemplate.delete(LOGIN_USER_TOKEN_KEY + userId);
        log.info("管理员重置用户密码: adminId={}, userId={}", UserContext.getUserId(), userId);
        return Result.success();
    }

    private void checkUsernameAvailable(String username, Long excludeUserId) {
        Long count = lambdaQuery()
                .eq(User::getUsername, username)
                .ne(excludeUserId != null, User::getId, excludeUserId)
                .count();
        if (count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "登录名已存在");
        }
    }

    private void checkPhoneAvailable(String phone, Long excludeUserId) {
        if (phone == null) {
            return;
        }

        Long count = lambdaQuery()
                .eq(User::getPhone, phone)
                .ne(excludeUserId != null, User::getId, excludeUserId)
                .count();
        if (count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "手机号已存在");
        }
    }

    private void checkDepartmentExists(Long departmentId) {
        if (departmentId != null && departmentService.getById(departmentId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
