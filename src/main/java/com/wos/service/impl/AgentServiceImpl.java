package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wos.common.PageResult;
import com.wos.common.PermissionChecker;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.common.enums.Priority;
import com.wos.common.enums.RoleEnum;
import com.wos.common.enums.WorkOrderStatus;
import com.wos.domain.dto.AgentWorkorderQueryDTO;
import com.wos.domain.pojo.User;
import com.wos.domain.vo.AgentCurrentUserVO;
import com.wos.domain.vo.WorkorderVO;
import com.wos.exception.BusinessException;
import com.wos.mapper.AgentMapper;
import com.wos.service.IAgentService;
import com.wos.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements IAgentService {

    private final AgentMapper agentMapper;

    private final PermissionChecker permissionChecker;

    private final IUserService userService;

    @Override
    public Result<AgentCurrentUserVO> currentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(new AgentCurrentUserVO(userId));
    }

    @Override
    public Result<PageResult<WorkorderVO>> queryWorkorders(AgentWorkorderQueryDTO queryDTO) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (queryDTO.getStartDate() != null
                && queryDTO.getEndDate() != null
                && queryDTO.getStartDate().isAfter(queryDTO.getEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }

        if (queryDTO.getKeyword() != null) {
            queryDTO.setKeyword(queryDTO.getKeyword().trim());
        }
        if (queryDTO.getStatuses() != null) {
            queryDTO.setStatuses(queryDTO.getStatuses().stream().distinct().toList());
        }
        if (queryDTO.getPriorities() != null) {
            queryDTO.setPriorities(queryDTO.getPriorities().stream().distinct().toList());
        }

        Long departmentId = null;
        switch (queryDTO.getRelation()) {
            case CREATED -> permissionChecker.checkRole(RoleEnum.SUBMITTER);
            case ASSIGNED -> permissionChecker.checkRole(RoleEnum.HANDLER);
            case REVIEW -> {
                permissionChecker.checkRole(RoleEnum.REVIEWER);
                User user = userService.getById(userId);
                if (user == null) {
                    throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或登录已失效");
                }
                if (user.getDepartmentId() == null) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "未分配部门,无法审核");
                }
                departmentId = user.getDepartmentId();
                applyFixedStatus(queryDTO, WorkOrderStatus.PENDING_REVIEW);
            }
            case DISPATCH -> {
                permissionChecker.checkRole(RoleEnum.DISPATCHER);
                applyFixedStatus(queryDTO, WorkOrderStatus.PENDING_ASSIGN);
            }
        }

        Page<WorkorderVO> page = new Page<>(queryDTO.getPageNum(), queryDTO.getLimit());
        Page<WorkorderVO> resultPage = agentMapper.queryWorkorders(
                page,
                queryDTO,
                userId,
                departmentId
        );
        resultPage.getRecords().forEach(this::fillWorkorderDescription);
        return Result.success(PageResult.of(resultPage));
    }

    private void applyFixedStatus(AgentWorkorderQueryDTO queryDTO, WorkOrderStatus fixedStatus) {
        List<WorkOrderStatus> statuses = queryDTO.getStatuses();
        if (statuses != null && !statuses.isEmpty()
                && (!statuses.contains(fixedStatus) || statuses.size() != 1)) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "当前工单关系只允许查询状态: " + fixedStatus.name()
            );
        }
        queryDTO.setStatuses(List.of(fixedStatus));
    }

    private void fillWorkorderDescription(WorkorderVO workorder) {
        workorder.setStatusDesc(WorkOrderStatus.descOf(workorder.getStatus()));
        workorder.setPriorityDesc(Priority.descOf(workorder.getPriority()));
    }
}
