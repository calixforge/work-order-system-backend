package com.wos.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员编辑用户基础信息请求参数。
 * 头像、密码、角色分别走独立接口。
 */
@Data
public class UserUpdateDTO {

    @Schema(description = "登录名")
    @NotBlank(message = "登录名不能为空")
    @Size(max = 50, message = "登录名最多 50 个字符")
    private String username;

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
