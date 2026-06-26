package com.wos.controller;

import com.wos.common.Result;
import com.wos.domain.dto.DepartmentDTO;
import com.wos.domain.vo.DepartmentVO;
import com.wos.service.IDepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "部门管理")
@RestController
@RequestMapping("/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final IDepartmentService departmentService;

    @Operation(summary = "查询部门列表")
    @GetMapping("/list")
    public Result<List<DepartmentVO>> listDepartments() {
        return departmentService.listDepartments();
    }

    @Operation(summary = "创建部门")
    @PostMapping
    public Result<Long> createDepartment(@Valid @RequestBody DepartmentDTO dto) {
        return departmentService.createDepartment(dto);
    }

    @Operation(summary = "查询部门详情")
    @GetMapping("/{deptId}")
    public Result<DepartmentVO> getDepartment(@PathVariable Long deptId) {
        return departmentService.getDepartment(deptId);
    }

    @Operation(summary = "编辑部门")
    @PutMapping("/{deptId}")
    public Result<Void> updateDepartment(@PathVariable Long deptId,
                                         @Valid @RequestBody DepartmentDTO dto) {
        return departmentService.updateDepartment(deptId, dto);
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{deptId}")
    public Result<Void> deleteDepartment(@PathVariable Long deptId) {
        return departmentService.deleteDepartment(deptId);
    }
}
