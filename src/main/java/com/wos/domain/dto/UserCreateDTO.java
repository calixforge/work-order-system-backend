package com.wos.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员创建用户请求参数。
 * 头像由后端设置默认值,角色通过分配角色接口单独授权。
 */
@Data
public class UserCreateDTO {

    @Schema(description = "登录名")
    @NotBlank(message = "登录名不能为空")
    @Size(max = 50, message = "登录名最多 50 个字符")
    private String username;

    @Schema(description = "初始密码")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在 6 到 50 位之间")
    private String password;

    @Schema(description = "真实姓名")
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名最多 50 个字符")
    private String realName;

    @Schema(description = "手机号")
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "所属部门id")
    private Long departmentId;
}
