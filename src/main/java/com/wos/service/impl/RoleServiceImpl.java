package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.Result;
import com.wos.domain.pojo.Role;
import com.wos.domain.vo.RoleVO;
import com.wos.mapper.RoleMapper;
import com.wos.service.IRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.wos.common.RedisConstants.USER_ROLE_EXPIRE_MINUTES;
import static com.wos.common.RedisConstants.USER_ROLE_KEY;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements IRoleService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 查询用户的角色 code 列表,带 Redis 缓存。
     */
    @Override
    public List<String> selectCodesByUserId(Long userId) {

        String cached = stringRedisTemplate.opsForValue().get(USER_ROLE_KEY + userId);

        // cached != null 才算命中(含空串占位:该用户确实没有任何角色)。
        // 空串必须单独判,否则 "".split(",") 会得到 [""] 而不是空列表。
        if (cached != null) {
            return cached.isEmpty() ? List.of() : Arrays.asList(cached.split(","));
        }

        // 真未命中才查库;空结果也回填成空串占位,避免无角色用户每次穿透到库。
        List<String> codes = baseMapper.selectCodesByUserId(userId);
        stringRedisTemplate.opsForValue()
                .set(USER_ROLE_KEY + userId, String.join(",", codes), USER_ROLE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        return codes;
    }

    @Override
    public Result<List<RoleVO>> listRole() {
        List<Role> list = list();
        List<RoleVO> roleVOs = list.stream().map((role) ->
        {
            RoleVO vo = new RoleVO();
            BeanUtils.copyProperties(role, vo);
            return vo;
        }).toList();

        return Result.success(roleVOs);
    }

    /**
     * 查询用户拥有的角色(id + name + code)。详情页低频,直接查库不走缓存。
     */
    @Override
    public List<RoleVO> selectRoleVOByUserId(Long userId) {
        return baseMapper.selectRoleVOByUserId(userId);
    }

}
