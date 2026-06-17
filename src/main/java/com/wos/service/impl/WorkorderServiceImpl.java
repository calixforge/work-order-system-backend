package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.common.enums.Priority;
import com.wos.common.enums.WorkOrderStatus;
import com.wos.domain.dto.WorkorderCreateDTO;
import com.wos.domain.dto.WorkorderQueryDTO;
import com.wos.domain.pojo.Department;
import com.wos.domain.pojo.User;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.vo.WorkorderVO;
import com.wos.exception.BusinessException;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.IDepartmentService;
import com.wos.service.IRoleService;
import com.wos.service.IUserService;
import com.wos.service.IWorkorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkorderServiceImpl extends ServiceImpl<WorkorderMapper, Workorder> implements IWorkorderService {

    private final IUserService userService;

    private final IRoleService roleService;

    private final IDepartmentService departmentService;

    /**
     * 校验当前登录用户是否拥有指定角色。
     *
     * 当前阶段先在 Service 层做显式校验,后续如果多个模块都需要相同的角色判断,
     * 可以再抽成 PermissionService 或注解+AOP。
     */
    private void checkRole(String roleCode) {
        List<String> codes = roleService.selectCodesByUserId(UserContext.getUserId());
        if (!codes.contains(roleCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限");
        }
    }

    @Override
    public Result<Long> workorderCreate(WorkorderCreateDTO createDTO) {

        checkRole("SUBMITTER");

        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或登录已失效");
        }

        Workorder workorder = new Workorder();
        BeanUtils.copyProperties(createDTO, workorder);
        workorder.setCreatorId(userId);

        // 工单归属部门固定取提单人的部门,用于后续“本部门审核”数据权限。
        if (user.getDepartmentId() != null) {
            workorder.setDepartmentId(user.getDepartmentId());
        } else {
            throw new BusinessException("未分配部门,不能提单");
        }

        // 创建时支持两种入口:保存草稿或直接提交审核。
        if (createDTO.getSubmit()) {
            workorder.setStatus(WorkOrderStatus.PENDING_REVIEW.name());
        } else {
            workorder.setStatus(WorkOrderStatus.DRAFT.name());
        }

        save(workorder);

        return Result.success(workorder.getId());
    }

    /**
     * 公共分页查询。
     *
     * 负责处理所有工单列表的通用筛选条件:
     * - status: 工单状态
     * - priority: 优先级
     * - createTime 倒序
     *
     * extra 用来追加不同列表接口的专属数据范围条件:
     * - 我创建的工单: creatorId = 当前用户
     * - 我负责的工单: assigneeId = 当前用户
     * - 本部门待审核: departmentId = 当前用户部门
     * - 全局待派单: 无额外数据范围限制
     */
    private Result<PageResult<WorkorderVO>> pageQuery(WorkorderQueryDTO dto,
                                                      Consumer<LambdaQueryChainWrapper<Workorder>> extra) {
        Page<Workorder> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryChainWrapper<Workorder> query = lambdaQuery()
                .eq(dto.getStatus() != null, Workorder::getStatus, dto.getStatus())
                .eq(dto.getPriority() != null, Workorder::getPriority, dto.getPriority())
                .orderByDesc(Workorder::getCreateTime);

        // 追加当前接口特有的数据范围条件。
        extra.accept(query);
        query.page(page);

        return Result.success(toVoPage(page));
    }

    /**
     * Page<Workorder> -> PageResult<WorkorderVO>。
     *
     * 数据库实体只保留 id/code 等存储字段,这里统一组装前端展示需要的名称和中文描述:
     * - creatorName / assigneeName
     * - departmentName
     * - statusDesc / priorityDesc
     */
    private PageResult<WorkorderVO> toVoPage(Page<Workorder> page) {
        List<Workorder> records = page.getRecords();

        // 收集本页涉及的用户 id(创建人 + 接单人)和部门 id,后面批量查询,避免 N+1 查询。
        Set<Long> userIds = new HashSet<>();
        Set<Long> deptIds = new HashSet<>();
        for (Workorder w : records) {
            userIds.add(w.getCreatorId());
            if (w.getAssigneeId() != null) userIds.add(w.getAssigneeId());
            if (w.getDepartmentId() != null) deptIds.add(w.getDepartmentId());
        }

        // 批量查名称并转成 id -> name 映射;空集合不查库。
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));
        Map<Long, String> deptNameMap = deptIds.isEmpty() ? Map.of()
                : departmentService.listByIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));

        // 组装 VO: code/id 给前端做判断,desc/name 给前端直接展示。
        List<WorkorderVO> voList = records.stream().map(w -> {
            WorkorderVO vo = new WorkorderVO();
            BeanUtils.copyProperties(w, vo);
            vo.setStatusDesc(WorkOrderStatus.descOf(w.getStatus()));
            vo.setCreatorName(userNameMap.get(w.getCreatorId()));
            vo.setAssigneeName(w.getAssigneeId() == null ? null : userNameMap.get(w.getAssigneeId()));
            vo.setDepartmentName(deptNameMap.get(w.getDepartmentId()));
            vo.setPriorityDesc(Priority.descOf(w.getPriority()));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(page.getTotal(), page.getPages(), voList);
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryCreated(WorkorderQueryDTO queryDTO) {
        checkRole("SUBMITTER");
        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getCreatorId, UserContext.getUserId()));
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryAssigned(WorkorderQueryDTO queryDTO) {
        checkRole("HANDLER");
        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getAssigneeId, UserContext.getUserId()));
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryReview(WorkorderQueryDTO queryDTO) {
        checkRole("REVIEWER");
        User user = userService.getById(UserContext.getUserId());

        // 待审核列表的业务语义固定为“查询待审核工单”。
        // 因此前端即使传入 status,也不能改变该接口的查询状态,这里由后端强制覆盖。
        queryDTO.setStatus(WorkOrderStatus.PENDING_REVIEW.name());

        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getDepartmentId, user.getDepartmentId()));
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryDispatch(WorkorderQueryDTO queryDTO) {
        checkRole("DISPATCHER");

        // 待派单列表的业务语义固定为“查询待派单工单”。
        // 前端传入的 status 在该接口中不生效,避免把“待派单列表”查成其他状态。
        queryDTO.setStatus(WorkOrderStatus.PENDING_ASSIGN.name());

        return pageQuery(queryDTO, q -> {});
    }
}
