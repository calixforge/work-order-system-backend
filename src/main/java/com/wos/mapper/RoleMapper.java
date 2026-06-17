package com.wos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wos.domain.pojo.Role;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    List<String> selectCodesByUserId(Long userId);
}
