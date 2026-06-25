package com.wos.controller;


import com.wos.common.Result;
import com.wos.common.PageResult;
import com.wos.domain.dto.ChangePasswordDTO;
import com.wos.domain.dto.LoginDTO;
import com.wos.domain.dto.ResetPasswordDTO;
import com.wos.domain.dto.UserCreateDTO;
import com.wos.domain.dto.UserQueryDTO;
import com.wos.domain.dto.UserUpdateDTO;
import com.wos.domain.vo.UserDetailVO;
import com.wos.domain.vo.UserVO;
import com.wos.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;


@Tag(name = "用户管理")
@RequestMapping("/user")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    /**
     * 登录成功后返回 token。
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody LoginDTO loginDTO) {
        return userService.login(loginDTO.getUsername(), loginDTO.getPassword());
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        return userService.logout(token);
    }

    @Operation(summary = "查询当前登录用户信息")
    @GetMapping("/info")
    public Result<UserDetailVO> userinfo() {
        return userService.userinfo();
    }

    @Operation(summary = "查询用户列表")
    @GetMapping("/list")
    public Result<PageResult<UserVO>> userList(@Valid @ParameterObject UserQueryDTO queryDTO) {
        return userService.userList(queryDTO);
    }

    @Operation(summary = "管理员创建用户")
    @PostMapping
    public Result<Long> createUser(@Valid @RequestBody UserCreateDTO dto) {
        return userService.createUser(dto);
    }

    @Operation(summary = "查询用户详情")
    @GetMapping("/{userId}")
    public Result<UserDetailVO> getUserDetail(@PathVariable Long userId) {
        return userService.getUserDetail(userId);
    }

    @Operation(summary = "管理员编辑用户基础信息")
    @PutMapping("/{userId}")
    public Result<Void> updateUser(@PathVariable Long userId,
                                   @Valid @RequestBody UserUpdateDTO dto) {
        return userService.updateUser(userId, dto);
    }

    @Operation(summary = "管理员停用用户")
    @PutMapping("/{userId}/disable")
    public Result<Void> disableUser(@PathVariable Long userId) {
        return userService.disableUser(userId);
    }

    @Operation(summary = "管理员启用用户")
    @PutMapping("/{userId}/enable")
    public Result<Void> enableUser(@PathVariable Long userId) {
        return userService.enableUser(userId);
    }

    @Operation(summary = "查询接单人列表")
    @GetMapping("/handlers")
    public Result<PageResult<UserVO>> userHandlersList(@Valid @ParameterObject UserQueryDTO queryDTO) {
        return userService.userHandlersList(queryDTO);
    }

    @Operation(summary = "修改自己的密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        return userService.changePassword(dto);
    }

    @Operation(summary = "管理员重置用户密码")
    @PutMapping("/{userId}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long userId,
                                      @Valid @RequestBody ResetPasswordDTO dto) {
        return userService.resetPassword(userId, dto);
    }

}
