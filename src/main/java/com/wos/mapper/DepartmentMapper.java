package com.wos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wos.domain.pojo.Department;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}
