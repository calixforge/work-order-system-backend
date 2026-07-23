package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.*;
import com.wos.common.enums.CreatedWorkorderView;
import com.wos.common.enums.Priority;
import com.wos.common.enums.RoleEnum;
import com.wos.common.enums.WorkOrderEvent;
import com.wos.common.enums.WorkOrderStatus;
import com.wos.domain.dto.*;
import com.wos.domain.pojo.Department;
import com.wos.domain.pojo.User;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.pojo.WorkorderLog;
import com.wos.domain.vo.WorkorderCreateVO;
import com.wos.domain.vo.WorkorderDetailVO;
import com.wos.domain.vo.WorkorderLogVO;
import com.wos.domain.vo.WorkorderStatsVO;
import com.wos.domain.vo.WorkorderVO;
import com.wos.exception.BusinessException;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.wos.common.RedisConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkorderServiceImpl extends ServiceImpl<WorkorderMapper, Workorder> implements IWorkorderService {

    private final IUserService userService;

    private final IRoleService roleService;

    private final IDepartmentService departmentService;

    private final IWorkorderLogService workorderLogService;

    private final StringRedisTemplate stringRedisTemplate;

    private final PermissionChecker permissionChecker;

    private static final Map<String, String> TRANSITIONS = new HashMap<>();

    private static final DateTimeFormatter WORKORDER_CODE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

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

    @Override
    @Transactional
    public Result<WorkorderCreateVO> workorderCreate(WorkorderCreateDTO createDTO) {

        permissionChecker.checkRole(RoleEnum.SUBMITTER);

        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或登录已失效");
        }

        Workorder workorder = new Workorder();
        BeanUtils.copyProperties(createDTO, workorder);
        workorder.setCreatorId(userId);
        LocalDateTime createdAt = LocalDateTime.now();
        workorder.setCreateTime(createdAt);

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

        String code = buildWorkorderCode(workorder.getId(), createdAt);
        boolean codeUpdated = lambdaUpdate()
                .set(Workorder::getCode, code)
                .eq(Workorder::getId, workorder.getId())
                .update();
        if (!codeUpdated) {
            throw new BusinessException("生成工单编号失败");
        }
        workorder.setCode(code);

        log.info("创建工单: workorderId={}, code={}, creatorId={}, departmentId={}, status={}, submit={}",
                workorder.getId(), code, userId, workorder.getDepartmentId(), workorder.getStatus(), createDTO.getSubmit());
        return Result.success(new WorkorderCreateVO(code));
    }

    private String buildWorkorderCode(Long id, LocalDateTime createdAt) {
        return String.format(
                Locale.ROOT,
                "WO-%s-%06d",
                createdAt.format(WORKORDER_CODE_DATE_FORMAT),
                id
        );
    }

    /**
     * 公共分页查询。
     *
     * 负责处理所有工单列表的通用筛选条件:
     * - keyword: 工单标题模糊搜索
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
        return pageQuery(dto, extra, false);
    }

    private Result<PageResult<WorkorderVO>> pageQuery(WorkorderQueryDTO dto,
                                                      Consumer<LambdaQueryChainWrapper<Workorder>> extra,
                                                      boolean orderByUpdateTime) {
        if (dto.getStartDate() != null
                && dto.getEndDate() != null
                && dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }

        String keyword = dto.getKeyword() == null ? null : dto.getKeyword().trim();
        LocalDateTime startTime = dto.getStartDate() == null
                ? null
                : dto.getStartDate().atStartOfDay();
        LocalDateTime endTimeExclusive = dto.getEndDate() == null
                ? null
                : dto.getEndDate().plusDays(1).atStartOfDay();
        Page<Workorder> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryChainWrapper<Workorder> query = lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), Workorder::getTitle, keyword)
                .eq(dto.getStatus() != null, Workorder::getStatus, dto.getStatus())
                .eq(dto.getPriority() != null, Workorder::getPriority, dto.getPriority())
                .ge(startTime != null, Workorder::getCreateTime, startTime)
                .lt(endTimeExclusive != null, Workorder::getCreateTime, endTimeExclusive);

        if (orderByUpdateTime) {
            query.orderByDesc(Workorder::getUpdateTime)
                    .orderByDesc(Workorder::getCreateTime);
        } else {
            query.orderByDesc(Workorder::getCreateTime);
        }

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

        Map<Long, String> userNameMap = loadUserNameMap(userIds);
        Map<Long, String> deptNameMap = loadDepartmentNameMap(deptIds);

        // 组装 VO:code 作为对外工单标识,desc/name 给前端直接展示。
        List<WorkorderVO> voList = records.stream().map(w -> {
            WorkorderVO vo = new WorkorderVO();
            fillWorkorderVO(w, vo, userNameMap, deptNameMap);
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(page.getTotal(), page.getPages(), voList);
    }

    private Map<Long, String> loadUserNameMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));
    }

    private Map<Long, String> loadDepartmentNameMap(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Map.of();
        }

        List<Long> deptIdList = new ArrayList<>(deptIds);
        Map<Long, String> deptNameMap = new HashMap<>();
        List<Long> missIds = new ArrayList<>();

        // 先从 Redis 批量读取部门名称缓存,deptList 的顺序与 deptIdList 保持一致。
        // multiGet 中未命中的 key 会返回 null,后续统一收集到 missIds 再查数据库。
        List<String> deptList = stringRedisTemplate.opsForValue()
                .multiGet(deptIdList.stream().map(id -> DEPT_NAME_KEY_PREFIX + id).toList());

        if (deptList == null) {
            deptList = Collections.nCopies(deptIdList.size(), null);
        }

        for (int i = 0; i < deptList.size(); i++) {
            String name = deptList.get(i);
            if (name != null) {
                deptNameMap.put(deptIdList.get(i), name);
            } else {
                missIds.add(deptIdList.get(i));
            }
        }

        // Redis 未命中的部门再批量查库,并回填部门名称缓存。
        if (!missIds.isEmpty()) {
            for (Department dept : departmentService.listByIds(missIds)) {
                deptNameMap.put(dept.getId(), dept.getName());
                stringRedisTemplate.opsForValue()
                        .set(DEPT_NAME_KEY_PREFIX + dept.getId(), dept.getName(), DEPT_NAME_EXPIRE_HOURS, TimeUnit.HOURS);
            }
        }

        return deptNameMap;
    }

    private void fillWorkorderVO(Workorder workorder, WorkorderVO vo,
                                 Map<Long, String> userNameMap,
                                 Map<Long, String> deptNameMap) {
        BeanUtils.copyProperties(workorder, vo);
        vo.setStatusDesc(WorkOrderStatus.descOf(workorder.getStatus()));
        vo.setCreatorName(userNameMap.get(workorder.getCreatorId()));
        vo.setAssigneeName(workorder.getAssigneeId() == null ? null : userNameMap.get(workorder.getAssigneeId()));
        vo.setDepartmentName(deptNameMap.get(workorder.getDepartmentId()));
        vo.setPriorityDesc(Priority.descOf(workorder.getPriority()));
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryCreated(WorkorderCreatedQueryDTO queryDTO) {
        permissionChecker.checkRole(RoleEnum.SUBMITTER);
        if (queryDTO.getView() != null && queryDTO.getStatus() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分组查询和状态筛选不能同时使用");
        }

        return pageQuery(queryDTO, q -> {
            q.eq(Workorder::getCreatorId, UserContext.getUserId());
            if (queryDTO.getView() == null || queryDTO.getView() == CreatedWorkorderView.ALL) {
                return;
            }
            switch (queryDTO.getView()) {
                case TODO -> q.in(Workorder::getStatus, List.of(
                        WorkOrderStatus.DRAFT.name(),
                        WorkOrderStatus.COMPLETED.name()));
                case PROCESSING -> q.in(Workorder::getStatus, List.of(
                        WorkOrderStatus.PENDING_REVIEW.name(),
                        WorkOrderStatus.PENDING_ASSIGN.name(),
                        WorkOrderStatus.ACCEPTED.name()));
                case FINISHED -> q.in(Workorder::getStatus, List.of(
                        WorkOrderStatus.CLOSED.name(),
                        WorkOrderStatus.CANCELED.name()));
                default -> {
                }
            }
        }, true);
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryAssigned(WorkorderQueryDTO queryDTO) {
        permissionChecker.checkRole(RoleEnum.HANDLER);
        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getAssigneeId, UserContext.getUserId()));
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderQueryReview(WorkorderQueryDTO queryDTO) {
        permissionChecker.checkRole(RoleEnum.REVIEWER);
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
        permissionChecker.checkRole(RoleEnum.DISPATCHER);

        // 待派单列表的业务语义固定为“查询待派单工单”。
        queryDTO.setStatus(WorkOrderStatus.PENDING_ASSIGN.name());

        return pageQuery(queryDTO, q -> {});
    }

    @Override
    public Result<WorkorderStatsVO> workorderStats() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }

        List<String> codes = roleService.selectCodesByUserId(userId);
        boolean reviewer = codes.contains(RoleEnum.REVIEWER.name());
        boolean dispatcher = codes.contains(RoleEnum.DISPATCHER.name());
        boolean handler = codes.contains(RoleEnum.HANDLER.name());
        boolean submitter = codes.contains(RoleEnum.SUBMITTER.name());

        Long departmentId = null;
        if (reviewer) {
            User user = userService.getById(userId);
            if (user == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或登录已失效");
            }
            departmentId = user.getDepartmentId();
        }

        WorkorderStatsVO stats = baseMapper.selectStats(userId, departmentId, reviewer, dispatcher, handler, submitter);
        return Result.success(stats == null ? new WorkorderStatsVO() : stats);
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
        WorkorderLog workorderLog = new WorkorderLog();
        workorderLog.setWorkorderId(wo.getId());
        workorderLog.setOperatorId(UserContext.getUserId());
        workorderLog.setFromStatus(from);
        workorderLog.setToStatus(to);
        workorderLog.setEvent(event.name());
        workorderLog.setRemark(remark);

        //更新工单并保存日志
        updateById(wo);
        workorderLogService.save(workorderLog);
        log.info("工单状态流转: workorderId={}, operatorId={}, event={}, fromStatus={}, toStatus={}, remarkPresent={}",
                wo.getId(), UserContext.getUserId(), event.name(), from, to, remark != null && !remark.isBlank());
    }

    private void requireRemark(String remark) {
        if (remark == null || remark.isBlank())
            throw new BusinessException(ResultCode.BAD_REQUEST, "请填写原因");
    }

    private Workorder getWorkorderOrThrow(String code) {
        String normalizedCode = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode == null || normalizedCode.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工单编号不能为空");
        }
        Workorder wo = lambdaQuery()
                .eq(Workorder::getCode, normalizedCode)
                .one();
        if (wo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单不存在");
        }
        return wo;
    }

    @Override
    @Transactional
    public Result<Void> workorderSubmit(String code) {

        permissionChecker.checkRole(RoleEnum.SUBMITTER);

        Workorder wo = getWorkorderOrThrow(code);

        if (!wo.getCreatorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法提交");
        }

        transition(wo, WorkOrderEvent.SUBMIT, null);

        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderWithdraw(String code) {
        permissionChecker.checkRole(RoleEnum.SUBMITTER);
        Workorder wo = getWorkorderOrThrow(code);

        if (!wo.getCreatorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法撤回");
        }

        transition(wo, WorkOrderEvent.WITHDRAW, null);

        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderCancel(String code, RemarkDTO dto) {

        permissionChecker.checkRole(RoleEnum.SUBMITTER);
        Workorder wo = getWorkorderOrThrow(code);


        if (!wo.getCreatorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法取消");
        }

        transition(wo, WorkOrderEvent.CANCEL, dto.getRemark());

        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderReview(String code, TransitionDTO dto) {
        permissionChecker.checkRole(RoleEnum.REVIEWER);
        Workorder wo = getWorkorderOrThrow(code);

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
    public Result<Void> workorderAcceptance(String code, TransitionDTO dto) {
        permissionChecker.checkRole(RoleEnum.SUBMITTER);

        Workorder wo = getWorkorderOrThrow(code);
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
    public Result<Void> workorderAssign(String code, AssignDTO dto) {
        permissionChecker.checkRole(RoleEnum.DISPATCHER);
        if (dto.getAssigneeId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接单人不能为空");
        }

        User assignee = userService.getById(dto.getAssigneeId());
        if (assignee == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "接单人不存在");
        }
        if (!Objects.equals(assignee.getStatus(), 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接单人已停用,无法派单");
        }

        List<String> list = roleService.selectCodesByUserId(dto.getAssigneeId());
        if (!list.contains(RoleEnum.HANDLER.name())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该用户不是接单人,无法派单");
        }
        Workorder wo = getWorkorderOrThrow(code);

        if (dto.getPriority() != null){
            wo.setPriority(dto.getPriority());
        }
        wo.setAssigneeId(dto.getAssigneeId());

        transition(wo, WorkOrderEvent.ASSIGN, null);

        log.info("工单派单: workorderId={}, dispatcherId={}, assigneeId={}, priority={}",
                wo.getId(), UserContext.getUserId(), dto.getAssigneeId(), wo.getPriority());
        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderTransfer(String code, RemarkDTO dto) {
        permissionChecker.checkRole(RoleEnum.HANDLER);
        Workorder wo = getWorkorderOrThrow(code);
        if (!UserContext.getUserId().equals(wo.getAssigneeId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法转派");
        }
        Long previousAssigneeId = wo.getAssigneeId();
        transition(wo, WorkOrderEvent.TRANSFER, dto.getRemark());
        // 转派后清空负责人，回到待派单池等待重新派单。
        // updateById 默认跳过 null 字段，故用 lambdaUpdate().set 显式写入 null。
        lambdaUpdate().set(Workorder::getAssigneeId, null).eq(Workorder::getId, wo.getId()).update();

        log.info("工单转派后清空负责人: workorderId={}, operatorId={}, previousAssigneeId={}",
                wo.getId(), UserContext.getUserId(), previousAssigneeId);
        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> workorderComplete(String code, WorkorderCompleteDTO dto) {
        permissionChecker.checkRole(RoleEnum.HANDLER);
        Workorder wo = getWorkorderOrThrow(code);
        if (!UserContext.getUserId().equals(wo.getAssigneeId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法完成");
        }
        String resolutionSummary = dto.getResolutionSummary().trim();
        if (resolutionSummary.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "处理结果不能为空");
        }
        wo.setCompleteTime(LocalDateTime.now());
        wo.setResolutionSummary(resolutionSummary);
        transition(wo, WorkOrderEvent.COMPLETE, null);

        return Result.success();
    }

    @Override
    public Result<PageResult<WorkorderVO>> workorderList(WorkorderQueryDTO queryDTO) {
        // 管理员查看所有工单:不限定数据范围(看全部),状态/优先级按前端传参自由筛选。
        permissionChecker.checkRole(RoleEnum.ADMIN);
        return pageQuery(queryDTO, q -> {});
    }

    @Override
    public Result<WorkorderDetailVO> getWorkorderDetailByCode(String code) {
        String normalizedCode = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode == null || normalizedCode.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "工单编号不能为空");
        }

        Workorder workorder = lambdaQuery()
                .eq(Workorder::getCode, normalizedCode)
                .one();
        if (workorder == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单不存在");
        }

        return Result.success(buildWorkorderDetail(workorder));
    }

    private WorkorderDetailVO buildWorkorderDetail(Workorder workorder) {
        checkWorkorderDetailPermission(workorder);

        Set<Long> userIds = new HashSet<>();
        userIds.add(workorder.getCreatorId());
        if (workorder.getAssigneeId() != null) {
            userIds.add(workorder.getAssigneeId());
        }

        Set<Long> deptIds = workorder.getDepartmentId() == null
                ? Set.of()
                : Set.of(workorder.getDepartmentId());

        WorkorderDetailVO vo = new WorkorderDetailVO();
        fillWorkorderVO(workorder, vo, loadUserNameMap(userIds), loadDepartmentNameMap(deptIds));
        vo.setLogs(listWorkorderLogVO(workorder.getId()));
        return vo;
    }

    private void checkWorkorderDetailPermission(Workorder workorder) {
        Long userId = UserContext.getUserId();
        List<String> codes = roleService.selectCodesByUserId(userId);

        if (codes.contains(RoleEnum.ADMIN.name())) {
            return;
        }
        if (codes.contains(RoleEnum.DISPATCHER.name())
                && WorkOrderStatus.PENDING_ASSIGN.name().equals(workorder.getStatus())) {
            return;
        }
        if (codes.contains(RoleEnum.SUBMITTER.name()) && userId.equals(workorder.getCreatorId())) {
            return;
        }
        if (codes.contains(RoleEnum.HANDLER.name()) && userId.equals(workorder.getAssigneeId())) {
            return;
        }
        if (codes.contains(RoleEnum.REVIEWER.name())
                && WorkOrderStatus.PENDING_REVIEW.name().equals(workorder.getStatus())) {
            User user = userService.getById(userId);
            if (user != null
                    && user.getDepartmentId() != null
                    && user.getDepartmentId().equals(workorder.getDepartmentId())) {
                return;
            }
        }

        throw new BusinessException(ResultCode.FORBIDDEN, "无权限查看该工单");
    }

    private List<WorkorderLogVO> listWorkorderLogVO(Long woId) {
        List<WorkorderLog> logs = workorderLogService.lambdaQuery()
                .eq(WorkorderLog::getWorkorderId, woId)
                .orderByAsc(WorkorderLog::getCreateTime)
                .orderByAsc(WorkorderLog::getId)
                .list();

        Set<Long> operatorIds = logs.stream()
                .map(WorkorderLog::getOperatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> operatorNameMap = loadUserNameMap(operatorIds);

        return logs.stream().map(log -> {
            WorkorderLogVO vo = new WorkorderLogVO();
            BeanUtils.copyProperties(log, vo);
            vo.setOperatorName(operatorNameMap.get(log.getOperatorId()));
            vo.setFromStatusDesc(WorkOrderStatus.descOf(log.getFromStatus()));
            vo.setToStatusDesc(WorkOrderStatus.descOf(log.getToStatus()));
            vo.setEventDesc(WorkOrderEvent.descOf(log.getEvent()));
            return vo;
        }).toList();
    }

    @Override
    public Result<Void> workorderUpdateDraft(String code, WorkorderUpdateDTO dto) {
        permissionChecker.checkRole(RoleEnum.SUBMITTER);
        Workorder wo = getWorkorderOrThrow(code);

        // 仅本人可编辑自己创建的工单。
        if (!wo.getCreatorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法编辑");
        }
        // 仅草稿可编辑:已进入流程的工单内容不允许直接改,只能走状态流转。
        if (!WorkOrderStatus.DRAFT.name().equals(wo.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅草稿状态可编辑");
        }

        wo.setTitle(dto.getTitle());
        wo.setDescription(dto.getDescription());
        wo.setPriority(dto.getPriority());
        updateById(wo);

        log.info("编辑草稿工单: workorderId={}, code={}, operatorId={}",
                wo.getId(), wo.getCode(), UserContext.getUserId());
        return Result.success();
    }

    @Override
    public Result<Void> workorderDeleteDraft(String code) {
        permissionChecker.checkRole(RoleEnum.SUBMITTER);
        Workorder wo = getWorkorderOrThrow(code);

        // 仅本人可删除自己创建的工单。
        if (!wo.getCreatorId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前工单不属于您，无法删除");
        }
        // 仅草稿可删除:进入流程的工单要留痕,只能取消/撤回,不能直接删除。
        if (!WorkOrderStatus.DRAFT.name().equals(wo.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅草稿状态可删除");
        }

        // 执行逻辑删
        removeById(wo.getId());

        log.info("删除草稿工单: workorderId={}, code={}, operatorId={}",
                wo.getId(), wo.getCode(), UserContext.getUserId());
        return Result.success();
    }

}
