package com.wos.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wos.common.PermissionChecker;
import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.common.enums.RoleEnum;
import com.wos.common.enums.WorkOrderStatus;
import com.wos.domain.dto.AssignDTO;
import com.wos.domain.dto.RemarkDTO;
import com.wos.domain.dto.WorkorderCompleteDTO;
import com.wos.domain.dto.WorkorderUpdateDTO;
import com.wos.domain.pojo.User;
import com.wos.domain.pojo.Workorder;
import com.wos.exception.BusinessException;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.IDepartmentService;
import com.wos.service.IRoleService;
import com.wos.service.IUserService;
import com.wos.service.IWorkorderLogService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkorderServiceImplConcurrencyTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long WORKORDER_ID = 10L;
    private static final String WORKORDER_CODE = "WO-20260807-000010";

    @Mock
    private WorkorderMapper workorderMapper;
    @Mock
    private IUserService userService;
    @Mock
    private IRoleService roleService;
    @Mock
    private IDepartmentService departmentService;
    @Mock
    private IWorkorderLogService workorderLogService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private PermissionChecker permissionChecker;

    private WorkorderServiceImpl service;

    @BeforeAll
    static void initializeTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "workorder-test"),
                Workorder.class
        );
    }

    @BeforeEach
    void setUp() {
        service = new WorkorderServiceImpl(
                userService,
                roleService,
                departmentService,
                workorderLogService,
                stringRedisTemplate,
                permissionChecker
        );
        ReflectionTestUtils.setField(service, "baseMapper", workorderMapper);
        UserContext.setUserId(CURRENT_USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void onlyFirstRequestSucceedsWhenTwoRequestsReadTheSameOldStatus() {
        when(workorderMapper.selectOne(any())).thenReturn(
                workorder(WorkOrderStatus.DRAFT, CURRENT_USER_ID),
                workorder(WorkOrderStatus.DRAFT, CURRENT_USER_ID)
        );
        when(workorderMapper.update(isNull(), any())).thenReturn(1, 0);
        when(workorderLogService.save(any())).thenReturn(true);

        service.workorderSubmit(WORKORDER_CODE);

        assertThatThrownBy(() -> service.workorderSubmit(WORKORDER_CODE))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ResultCode.CONFLICT.getCode());
                    assertThat(exception.getMessage()).isEqualTo("工单状态已变更，请刷新后重试");
                });

        verify(workorderLogService, times(1)).save(any());
        List<LambdaUpdateWrapper<Workorder>> wrappers = captureUpdateWrappers(2);
        wrappers.forEach(wrapper -> assertOptimisticLock(
                wrapper,
                WorkOrderStatus.DRAFT.name(),
                WorkOrderStatus.PENDING_REVIEW.name()
        ));
    }

    @Test
    void assignWritesAssigneePriorityAndStatusInOneConditionalUpdate() {
        Long assigneeId = 9L;
        User assignee = new User();
        assignee.setId(assigneeId);
        assignee.setStatus(1);
        when(userService.getById(assigneeId)).thenReturn(assignee);
        when(roleService.selectCodesByUserId(assigneeId)).thenReturn(List.of(RoleEnum.HANDLER.name()));
        when(workorderMapper.selectOne(any())).thenReturn(
                workorder(WorkOrderStatus.PENDING_ASSIGN, CURRENT_USER_ID)
        );
        when(workorderMapper.update(isNull(), any())).thenReturn(1);
        when(workorderLogService.save(any())).thenReturn(true);

        AssignDTO dto = new AssignDTO();
        dto.setAssigneeId(assigneeId);
        dto.setPriority(1);
        service.workorderAssign(WORKORDER_CODE, dto);

        LambdaUpdateWrapper<Workorder> wrapper = captureUpdateWrappers(1).get(0);
        assertOptimisticLock(
                wrapper,
                WorkOrderStatus.PENDING_ASSIGN.name(),
                WorkOrderStatus.ACCEPTED.name()
        );
        assertThat(wrapper.getSqlSet())
                .contains("assignee_id")
                .contains("priority");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(assigneeId, dto.getPriority());
    }

    @Test
    void transferClearsAssigneeInTheSameConditionalUpdate() {
        Workorder workorder = workorder(WorkOrderStatus.ACCEPTED, CURRENT_USER_ID);
        workorder.setAssigneeId(CURRENT_USER_ID);
        when(workorderMapper.selectOne(any())).thenReturn(workorder);
        when(workorderMapper.update(isNull(), any())).thenReturn(1);
        when(workorderLogService.save(any())).thenReturn(true);

        RemarkDTO dto = new RemarkDTO();
        dto.setRemark("重新分派");
        service.workorderTransfer(WORKORDER_CODE, dto);

        LambdaUpdateWrapper<Workorder> wrapper = captureUpdateWrappers(1).get(0);
        assertOptimisticLock(
                wrapper,
                WorkOrderStatus.ACCEPTED.name(),
                WorkOrderStatus.PENDING_ASSIGN.name()
        );
        assertThat(wrapper.getSqlSet()).contains("assignee_id");
        verify(workorderMapper, times(1)).update(isNull(), any());
    }

    @Test
    void completeWritesCompletionFieldsAndStatusInOneConditionalUpdate() {
        Workorder workorder = workorder(WorkOrderStatus.ACCEPTED, CURRENT_USER_ID);
        workorder.setAssigneeId(CURRENT_USER_ID);
        when(workorderMapper.selectOne(any())).thenReturn(workorder);
        when(workorderMapper.update(isNull(), any())).thenReturn(1);
        when(workorderLogService.save(any())).thenReturn(true);

        WorkorderCompleteDTO dto = new WorkorderCompleteDTO();
        dto.setResolutionSummary("问题已处理");
        service.workorderComplete(WORKORDER_CODE, dto);

        LambdaUpdateWrapper<Workorder> wrapper = captureUpdateWrappers(1).get(0);
        assertOptimisticLock(
                wrapper,
                WorkOrderStatus.ACCEPTED.name(),
                WorkOrderStatus.COMPLETED.name()
        );
        assertThat(wrapper.getSqlSet())
                .contains("complete_time")
                .contains("resolution_summary");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(dto.getResolutionSummary());
    }

    @Test
    void draftUpdateDoesNotOverwriteAConcurrentlySubmittedWorkorder() {
        when(workorderMapper.selectOne(any())).thenReturn(
                workorder(WorkOrderStatus.DRAFT, CURRENT_USER_ID)
        );
        when(workorderMapper.update(isNull(), any())).thenReturn(0);

        WorkorderUpdateDTO dto = new WorkorderUpdateDTO();
        dto.setTitle("更新后的标题");
        dto.setDescription("更新后的描述");
        dto.setPriority(2);

        assertThatThrownBy(() -> service.workorderUpdateDraft(WORKORDER_CODE, dto))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ResultCode.CONFLICT.getCode()));

        LambdaUpdateWrapper<Workorder> wrapper = captureUpdateWrappers(1).get(0);
        assertThat(wrapper.getSqlSet())
                .contains("title")
                .contains("description")
                .contains("priority")
                .doesNotContain("status=");
        assertThat(wrapper.getSqlSegment())
                .contains("creator_id")
                .contains("status");
    }

    @Test
    void draftDeleteDoesNotDeleteAConcurrentlySubmittedWorkorder() {
        when(workorderMapper.selectOne(any())).thenReturn(
                workorder(WorkOrderStatus.DRAFT, CURRENT_USER_ID)
        );
        when(workorderMapper.delete(ArgumentMatchers.<Wrapper<Workorder>>any())).thenReturn(0);

        assertThatThrownBy(() -> service.workorderDeleteDraft(WORKORDER_CODE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ResultCode.CONFLICT.getCode()));

        LambdaQueryWrapper<Workorder> wrapper = captureDeleteWrapper();
        assertThat(wrapper.getSqlSegment())
                .contains("creator_id")
                .contains("status");
        verify(workorderMapper, never()).update(isNull(), any());
    }

    @Test
    void logFailureRaisesAnErrorSoTheTransactionCanRollBack() {
        when(workorderMapper.selectOne(any())).thenReturn(
                workorder(WorkOrderStatus.DRAFT, CURRENT_USER_ID)
        );
        when(workorderMapper.update(isNull(), any())).thenReturn(1);
        when(workorderLogService.save(any())).thenReturn(false);

        assertThatThrownBy(() -> service.workorderSubmit(WORKORDER_CODE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ResultCode.INTERNAL_SERVER_ERROR.getCode()));

        verify(workorderLogService, times(1)).save(any());
    }

    private Workorder workorder(WorkOrderStatus status, Long creatorId) {
        Workorder workorder = new Workorder();
        workorder.setId(WORKORDER_ID);
        workorder.setCode(WORKORDER_CODE);
        workorder.setStatus(status.name());
        workorder.setCreatorId(creatorId);
        workorder.setDepartmentId(2L);
        workorder.setPriority(3);
        workorder.setIsDel(0);
        return workorder;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<LambdaUpdateWrapper<Workorder>> captureUpdateWrappers(int invocationCount) {
        ArgumentCaptor<Wrapper<Workorder>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(workorderMapper, times(invocationCount)).update(isNull(), captor.capture());
        return captor.getAllValues().stream()
                .map(wrapper -> (LambdaUpdateWrapper<Workorder>) wrapper)
                .toList();
    }

    private void assertOptimisticLock(LambdaUpdateWrapper<Workorder> wrapper,
                                      String expectedStatus,
                                      String targetStatus) {
        assertThat(wrapper.getSqlSet()).contains("status");
        assertThat(wrapper.getSqlSegment())
                .contains("id")
                .contains("status");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(WORKORDER_ID, expectedStatus, targetStatus);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LambdaQueryWrapper<Workorder> captureDeleteWrapper() {
        ArgumentCaptor<Wrapper<Workorder>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(workorderMapper).delete(captor.capture());
        return (LambdaQueryWrapper<Workorder>) captor.getValue();
    }
}
