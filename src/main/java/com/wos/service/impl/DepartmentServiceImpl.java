package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.domain.pojo.Department;
import com.wos.mapper.DepartmentMapper;
import com.wos.service.IDepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements IDepartmentService {
}
