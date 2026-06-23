package com.wos.controller;

import com.wos.common.Result;
import com.wos.domain.dto.UserRoleDTO;
import com.wos.domain.vo.RoleVO;
import com.wos.service.IRoleService;
import com.wos.service.IUserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final IRoleService roleService;

    private final IUserRoleService userRoleService;

    @Operation(summary = "查询角色列表")
    @GetMapping("/list")
    public Result<List<RoleVO>> listRole() {
        return roleService.listRole();
    }

    @Operation(summary = "分配角色")
    @PostMapping("/assign")
    public Result<Void> assignRole(@Valid @RequestBody UserRoleDTO dto) {
        return userRoleService.assignRole(dto.getUserId(), dto.getRoleId());
    }

    @Operation(summary = "剥夺角色")
    @DeleteMapping("/revoke")
    public Result<Void> revokeRole(@Valid @ParameterObject UserRoleDTO dto) {
        return userRoleService.revokeRole(dto.getUserId(), dto.getRoleId());
    }
}
