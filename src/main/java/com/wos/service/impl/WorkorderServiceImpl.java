package com.wos.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.common.enums.WorkOrderStatus;
import com.wos.domain.dto.WorkorderCreateDTO;
import com.wos.domain.dto.WorkorderQueryDTO;
import com.wos.domain.pojo.User;
import com.wos.domain.pojo.Workorder;
import com.wos.exception.BusinessException;
import com.wos.mapper.RoleMapper;
import com.wos.mapper.WorkorderMapper;
import com.wos.service.IRoleService;
import com.wos.service.IUserService;
import com.wos.service.IWorkorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkorderServiceImpl extends ServiceImpl<WorkorderMapper, Workorder> implements IWorkorderService {

    private final IUserService userService;

    private final IRoleService roleService;

    //校验当前用户是否拥有该角色权限
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

        //部门id为空不能创建工单
        if (user.getDepartmentId() != null) {
            workorder.setDepartmentId(user.getDepartmentId());
        }else {
            throw new BusinessException("未分配部门,不能提单");
        }

        //判断是直接提交还是保存为草稿
        if (createDTO.getSubmit()){
            workorder.setStatus(WorkOrderStatus.PENDING_REVIEW.name());
        }else {
            workorder.setStatus(WorkOrderStatus.DRAFT.name());
        }

        save(workorder);

        return Result.success(workorder.getId());

    }



    private Result<PageResult<Workorder>> pageQuery(WorkorderQueryDTO dto,
                                                    Consumer<LambdaQueryChainWrapper<Workorder>> extra) {
        Page<Workorder> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryChainWrapper<Workorder> query = lambdaQuery()
                .eq(dto.getStatus() != null, Workorder::getStatus, dto.getStatus())
                .eq(dto.getPriority() != null, Workorder::getPriority, dto.getPriority())
                .orderByDesc(Workorder::getCreateTime);

        // 各接口的特有 where 拼上去
        extra.accept(query);
        query.page(page);
        return Result.success(PageResult.of(page));
    }

    @Override
    public Result<PageResult<Workorder>> workorderQueryCreated(WorkorderQueryDTO queryDTO) {

        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getCreatorId, UserContext.getUserId()));

    }

    @Override
    public Result<PageResult<Workorder>> workorderQueryAssigned(WorkorderQueryDTO queryDTO) {

        checkRole("HANDLER");

        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getAssigneeId, UserContext.getUserId()));

    }

    @Override
    public Result<PageResult<Workorder>> workorderQueryReview(WorkorderQueryDTO queryDTO) {

        checkRole("REVIEWER");

        Long userId = UserContext.getUserId();
        User user = userService.getById(userId);
        queryDTO.setStatus(WorkOrderStatus.PENDING_REVIEW.name());

        return pageQuery(queryDTO, q ->
                q.eq(Workorder::getDepartmentId, user.getDepartmentId()));

    }

    @Override
    public Result<PageResult<Workorder>> workorderQueryDispatch(WorkorderQueryDTO queryDTO) {

        checkRole("DISPATCHER");

        queryDTO.setStatus(WorkOrderStatus.PENDING_ASSIGN.name());

        return  pageQuery(queryDTO, q ->{});
    }


}
