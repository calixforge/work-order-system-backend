package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.Result;
import com.wos.domain.dto.DepartmentDTO;
import com.wos.domain.pojo.Department;
import com.wos.domain.vo.DepartmentVO;

import java.util.List;

public interface IDepartmentService extends IService<Department> {

    Result<List<DepartmentVO>> listDepartments();

    Result<Long> createDepartment(DepartmentDTO dto);

    Result<DepartmentVO> getDepartment(Long deptId);

    Result<Void> updateDepartment(Long deptId, DepartmentDTO dto);

    Result<Void> deleteDepartment(Long deptId);
}
