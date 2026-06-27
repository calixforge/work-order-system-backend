package com.wos.controller;

import com.wos.common.Result;
import com.wos.domain.vo.AdminStatsVO;
import com.wos.service.IAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员统计")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final IAdminService adminService;

    @Operation(summary = "管理员首页统计")
    @GetMapping("/stats")
    public Result<AdminStatsVO> adminStats() {
        return adminService.adminStats();
    }
}
