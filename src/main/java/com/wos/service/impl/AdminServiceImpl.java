package com.wos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wos.common.PermissionChecker;
import com.wos.common.Result;
import com.wos.common.enums.RoleEnum;
import com.wos.common.enums.WorkOrderStatus;
import com.wos.domain.pojo.User;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.vo.AdminStatsVO;
import com.wos.mapper.DepartmentMapper;
import com.wos.mapper.UserMapper;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.IAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements IAdminService {

    private final PermissionChecker permissionChecker;
    private final WorkorderMapper workorderMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    public Result<AdminStatsVO> adminStats() {
        permissionChecker.checkRole(RoleEnum.ADMIN);

        AdminStatsVO vo = new AdminStatsVO();
        // 工单总数 / 待审 / 待派(Workorder 带 @TableLogic,selectCount 自动过滤 is_del=0)
        vo.setWorkorderTotal(workorderMapper.selectCount(null));
        vo.setPendingReview(workorderMapper.selectCount(new LambdaQueryWrapper<Workorder>()
                .eq(Workorder::getStatus, WorkOrderStatus.PENDING_REVIEW.name())));
        vo.setPendingAssign(workorderMapper.selectCount(new LambdaQueryWrapper<Workorder>()
                .eq(Workorder::getStatus, WorkOrderStatus.PENDING_ASSIGN.name())));
        // 用户启用/停用(User 无逻辑删除,按 status 区分:1 启用 0 停用)
        vo.setEnabledUsers(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)));
        vo.setDisabledUsers(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 0)));
        // 部门数(Department 带 @TableLogic,自动过滤已删除)
        vo.setDepartmentCount(departmentMapper.selectCount(null));

        return Result.success(vo);
    }
}
