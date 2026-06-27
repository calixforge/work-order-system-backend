package com.wos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.PermissionChecker;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.common.enums.RoleEnum;
import com.wos.domain.dto.DepartmentDTO;
import com.wos.domain.pojo.Department;
import com.wos.domain.pojo.User;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.vo.DepartmentVO;
import com.wos.exception.BusinessException;
import com.wos.mapper.DepartmentMapper;
import com.wos.mapper.UserMapper;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.IDepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wos.common.RedisConstants.DEPT_NAME_KEY_PREFIX;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements IDepartmentService {

    private final PermissionChecker permissionChecker;

    private final UserMapper userMapper;

    private final WorkorderMapper workorderMapper;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<List<DepartmentVO>> listDepartments() {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        List<DepartmentVO> departments = lambdaQuery()
                .orderByDesc(Department::getCreateTime)
                .list()
                .stream()
                .map(this::toVO)
                .toList();

        return Result.success(departments);
    }

    @Override
    public Result<Long> createDepartment(DepartmentDTO dto) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        String name = normalizeName(dto.getName());
        checkNameAvailable(name, null);

        Department department = new Department();
        department.setName(name);
        save(department);

        log.info("管理员创建部门: adminId={}, deptId={}, name={}",
                UserContext.getUserId(), department.getId(), department.getName());
        return Result.success(department.getId());
    }

    @Override
    public Result<DepartmentVO> getDepartment(Long deptId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        Department department = getDepartmentOrThrow(deptId);
        return Result.success(toVO(department));
    }

    @Override
    public Result<Void> updateDepartment(Long deptId, DepartmentDTO dto) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        Department department = getDepartmentOrThrow(deptId);
        String name = normalizeName(dto.getName());
        checkNameAvailable(name, deptId);

        String oldName = department.getName();
        department.setName(name);
        updateById(department);
        stringRedisTemplate.delete(DEPT_NAME_KEY_PREFIX + deptId);

        log.info("管理员更新部门: adminId={}, deptId={}, oldName={}, newName={}",
                UserContext.getUserId(), deptId, oldName, name);
        return Result.success();
    }

    @Override
    public Result<Void> deleteDepartment(Long deptId) {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        Department department = getDepartmentOrThrow(deptId);
        checkNoReferences(deptId);

        removeById(deptId);
        stringRedisTemplate.delete(DEPT_NAME_KEY_PREFIX + deptId);

        log.info("管理员删除部门: adminId={}, deptId={}, name={}",
                UserContext.getUserId(), deptId, department.getName());
        return Result.success();
    }

    private Department getDepartmentOrThrow(Long deptId) {
        Department department = getById(deptId);
        if (department == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }
        return department;
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private void checkNameAvailable(String name, Long excludeDeptId) {
        Long count = lambdaQuery()
                .eq(Department::getName, name)
                .ne(excludeDeptId != null, Department::getId, excludeDeptId)
                .count();
        if (count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "部门名称已存在");
        }
    }

    private void checkNoReferences(Long deptId) {
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getDepartmentId, deptId));
        if (userCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "部门下仍有用户,无法删除");
        }

        Long workorderCount = workorderMapper.selectCount(new LambdaQueryWrapper<Workorder>()
                .eq(Workorder::getDepartmentId, deptId));
        if (workorderCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "部门已被工单引用,无法删除");
        }
    }

    private DepartmentVO toVO(Department department) {
        DepartmentVO vo = new DepartmentVO();
        BeanUtils.copyProperties(department, vo);
        return vo;
    }
}
