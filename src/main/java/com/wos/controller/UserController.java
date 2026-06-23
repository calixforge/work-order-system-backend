package com.wos.controller;


import com.wos.common.Result;
import com.wos.domain.dto.LoginDTO;
import com.wos.domain.vo.UserDetailVO;
import com.wos.domain.vo.UserVO;
import com.wos.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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

    @Operation(summary = "查询用户列表")
    @GetMapping("/list")
    public Result<List<UserVO>> listUser() {
        return userService.listUser();
    }

    @Operation(summary = "查询用户详情")
    @GetMapping("/{userId}")
    public Result<UserDetailVO> getUserDetail(@PathVariable Long userId) {
        return userService.getUserDetail(userId);
    }


}
