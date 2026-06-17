package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.domain.pojo.Role;
import com.wos.mapper.RoleMapper;
import com.wos.service.IRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements IRoleService {
    @Override
    public List<String> selectCodesByUserId(Long userId) {
        return baseMapper.selectCodesByUserId(userId);
    }
}
