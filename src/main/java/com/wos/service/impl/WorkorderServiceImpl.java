package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.common.enums.Priority;
import com.wos.common.enums.RoleEnum;
import com.wos.common.enums.WorkOrderEvent;
import com.wos.common.enums.WorkOrderStatus;
import com.wos.domain.dto.*;
import com.wos.domain.pojo.Department;
import com.wos.domain.pojo.User;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.pojo.WorkorderLog;
import com.wos.domain.vo.WorkorderVO;
import com.wos.exception.BusinessException;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkorderServiceImpl extends ServiceImpl<WorkorderMapper, Workorder> implements IWorkorderService {

    private final IUserService userService;

    private final IRoleService roleService;

    private final IDepartmentService departmentService;

    private final IWorkorderLogService workorderLogService;

    private static final Map<String, String> TRANSITIONS = new HashMap<>();

    static {
        // 提交
        TRANSITIONS.put("DRAFT:SUBMIT", "PENDING_REVIEW");
        // 审核
        TRANSITIONS.put("PENDING_REVIEW:REVIEW_PASS", "PENDING_ASSIGN");
        TRANSITIONS.put("PENDING_REVIEW:REVIEW_REJECT", "DRAFT");
        // 派单
        TRANSITIONS.put("PENDING_ASSIGN:ASSIGN", "ACCEPTED");
        // 处理
        TRANSITIONS.put("ACCEPTED:TRANSFER", "PENDING_ASSIGN");
        TRANSITIONS.put("ACCEPTED:COMPLETE", "COMPLETED");
        // 验收
        TRANSITIONS.put("COMPLETED:ACCEPT", "CLOSED");
        TRANSITIONS.put("COMPLETED:REJECT_REWORK", "ACCEPTED");
        // 撤回：提单人在接单前发现内容有问题，撤回草稿后重新提交审核
        TRANSITIONS.put("PENDING_REVIEW:WITHDRAW", "DRAFT");
        TRANSITIONS.put("PENDING_ASSIGN:WITHDRAW", "DRAFT");
        // 取消：接单前终止工单，进入终态
        TRANSITIONS.put("PENDING_REVIEW:CANCEL", "CANCELED");
        TRANSITIONS.put("PENDING_ASSIGN:CANCEL", "CANCELED");
    }

    /**
     * 校验当前登录用户是否拥有指定角色。
     *
     * 当前阶段先在 Service 层做显式校验,后续如果多个模块都需要相同的角色判断,
     * 可以再抽成 PermissionService 或注解+AOP。
     */
    private void checkRole(RoleEnum role) {
        List<String> codes = roleService.selectCodesByUserId(UserContext.getUserId());
        if (!codes.contains(role.name())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限");
        }
    }

    @Override
    public Result<Long> workorderCreate(WorkorderCreateDTO createDTO) {

        checkRole(RoleEnum.SUBMITTER);

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
        checkRole(RoleEnum.SUBMITTER);
        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getCreatorId, UserContext.getUserId()));
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryAssigned(WorkorderQueryDTO queryDTO) {
        checkRole(RoleEnum.HANDLER);
        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getAssigneeId, UserContext.getUserId()));
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryReview(WorkorderQueryDTO queryDTO) {
        checkRole(RoleEnum.REVIEWER);
        User user = userService.getById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或登录已失效");
        }
        if (user.getDepartmentId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "未分配部门,无法审核");
        }

        // 待审核列表的业务语义固定为“查询待审核工单”。
        queryDTO.setStatus(WorkOrderStatus.PENDING_REVIEW.name());

        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getDepartmentId, user.getDepartmentId()));
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryDispatch(WorkorderQueryDTO queryDTO) {
        checkRole(RoleEnum.DISPATCHER);

        // 待派单列表的业务语义固定为“查询待派单工单”。
        queryDTO.setStatus(WorkOrderStatus.PENDING_ASSIGN.name());

        return pageQuery(queryDTO, q -> {});
    }


    /**
     * 执行工单状态流转。
     *
     * 职责边界:
     * 1. 根据“当前状态 + 事件”从 TRANSITIONS 中查找目标状态;
     * 2. 查不到说明该状态下不能执行该事件,直接抛出业务异常;
     * 3. 更新工单状态;
     * 4. 记录一条工单流转日志。
     */
    private void transition(Workorder wo, WorkOrderEvent event, String remark){
        String from = wo.getStatus();
        String to = TRANSITIONS.get(from + ":" + event.name());
        if (to == null) throw new BusinessException(ResultCode.CONFLICT, "当前状态不能执行该操作");

        wo.setStatus(to);
        WorkorderLog log = new WorkorderLog();
        log.setWorkorderId(wo.getId());
        log.setOperatorId(UserContext.getUserId());
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setEvent(event.name());
        log.setRemark(remark);

        //更新工单并保存日志
        updateById(wo);
        workorderLogService.save(log);
    }

    private void requireRemark(String remark) {
        if (remark == null || remark.isBlank())
            throw new BusinessException(ResultCode.BAD_REQUEST, "请填写原因");
    }

    private Workorder getWorkorderOrThrow(Long woId) {
        Workorder wo = getById(woId);
        if (wo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单不存在");
        }
        return wo;
    }

    @Override
    @Transactional
    public Result<Void> workorderSubmit(Long woId) {

        checkRole(RoleEnum.SUBMITTER);

        Workorder wo = getWorkorderOrThrow(woId);

        if (!wo.getCreatorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法提交");
        }

        transition(wo, WorkOrderEvent.SUBMIT, null);

        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderWithdraw(Long woId) {
        checkRole(RoleEnum.SUBMITTER);
        Workorder wo = getWorkorderOrThrow(woId);

        if (!wo.getCreatorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法撤回");
        }

        transition(wo, WorkOrderEvent.WITHDRAW, null);

        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderCancel(Long woId, RemarkDTO dto) {

        checkRole(RoleEnum.SUBMITTER);
        Workorder wo = getWorkorderOrThrow(woId);


        if (!wo.getCreatorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法取消");
        }

        transition(wo, WorkOrderEvent.CANCEL, dto.getRemark());

        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderReview(Long woId, TransitionDTO dto) {
        checkRole(RoleEnum.REVIEWER);
        Workorder wo = getWorkorderOrThrow(woId);

        User user = userService.getById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或登录已失效");
        }
        if (user.getDepartmentId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "未分配部门,无法审核");
        }

        if (!wo.getDepartmentId().equals(user.getDepartmentId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于该部门，无法审核");
        }

        if (WorkOrderEvent.REVIEW_PASS.equals(dto.getEvent())){
            transition(wo, WorkOrderEvent.REVIEW_PASS, null);
        }
        else if (WorkOrderEvent.REVIEW_REJECT.equals(dto.getEvent())){
            requireRemark(dto.getRemark());
            transition(wo, WorkOrderEvent.REVIEW_REJECT, dto.getRemark());
        }else{
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法审核操作");
        }


        return Result.success();

    }

    @Override
    @Transactional
    public Result<Void> workorderAcceptance(Long woId, TransitionDTO dto) {
        checkRole(RoleEnum.SUBMITTER);

        Workorder wo = getWorkorderOrThrow(woId);
        if (!wo.getCreatorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法验收");
        }

        if (WorkOrderEvent.ACCEPT.equals(dto.getEvent())) {
            transition(wo, WorkOrderEvent.ACCEPT, null);
        }else if (WorkOrderEvent.REJECT_REWORK.equals(dto.getEvent())){
            requireRemark(dto.getRemark());
            transition(wo, WorkOrderEvent.REJECT_REWORK, dto.getRemark());
        }else{
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法验收操作");
        }

        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderAssign(Long woId, AssignDTO dto) {
        checkRole(RoleEnum.DISPATCHER);
        if (dto.getAssigneeId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接单人不能为空");
        }
        List<String> list = roleService.selectCodesByUserId(dto.getAssigneeId());
        if (!list.contains(RoleEnum.HANDLER.name())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该用户不是接单人,无法派单");
        }
        Workorder wo = getWorkorderOrThrow(woId);

        if (dto.getPriority() != null){
            wo.setPriority(dto.getPriority());
        }
        wo.setAssigneeId(dto.getAssigneeId());

        transition(wo, WorkOrderEvent.ASSIGN, null);

        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderTransfer(Long woId, RemarkDTO dto) {
        checkRole(RoleEnum.HANDLER);
        Workorder wo = getWorkorderOrThrow(woId);
        if (!UserContext.getUserId().equals(wo.getAssigneeId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法转派");
        }
        wo.setAssigneeId(null);
        transition(wo, WorkOrderEvent.TRANSFER, dto.getRemark());

        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderComplete(Long woId) {
        checkRole(RoleEnum.HANDLER);
        Workorder wo = getWorkorderOrThrow(woId);
        if (!UserContext.getUserId().equals(wo.getAssigneeId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法完成");
        }
        wo.setCompleteTime(LocalDateTime.now());
        transition(wo, WorkOrderEvent.COMPLETE, null);

        return Result.success();
    }

}
