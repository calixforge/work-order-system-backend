package com.wos.controller;


import com.wos.common.Result;
import com.wos.common.PageResult;
import com.wos.domain.dto.LoginDTO;
import com.wos.domain.dto.UserQueryDTO;
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

    @Operation(summary = "查询用户列表")
    @GetMapping("/list")
    public Result<PageResult<UserVO>> userList(@Valid @ParameterObject UserQueryDTO queryDTO) {
        return userService.userList(queryDTO);
    }   

    @Operation(summary = "查询用户详情")
    @GetMapping("/{userId}")
    public Result<UserDetailVO> getUserDetail(@PathVariable Long userId) {
        return userService.getUserDetail(userId);
    }

    @Operation(summary = "查询接单人列表")
    @GetMapping("/handlers")
    public Result<PageResult<UserVO>> userHandlersList(@Valid @ParameterObject UserQueryDTO queryDTO) {
        return userService.userHandlersList(queryDTO);
    }


}
