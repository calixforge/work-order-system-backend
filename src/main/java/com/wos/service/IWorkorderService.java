package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.domain.dto.*;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.vo.WorkorderCreateVO;
import com.wos.domain.vo.WorkorderDetailVO;
import com.wos.domain.vo.WorkorderStatsVO;
import com.wos.domain.vo.WorkorderVO;
import jakarta.validation.Valid;

/**
 * 工单业务接口。
 */
public interface IWorkorderService extends IService<Workorder> {

    Result<WorkorderCreateVO> workorderCreate(WorkorderCreateDTO workorderCreateDTO);

    Result<PageResult<WorkorderVO>> workorderQueryCreated(@Valid WorkorderCreatedQueryDTO queryDTO);

    Result<PageResult<WorkorderVO>> workorderQueryAssigned(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<WorkorderVO>> workorderQueryReview(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<WorkorderVO>> workorderQueryDispatch(@Valid WorkorderQueryDTO queryDTO);

    Result<WorkorderStatsVO> workorderStats();

    Result<Void> workorderSubmit(String code);

    Result<Void> workorderWithdraw(String code);

    Result<Void> workorderCancel(String code, RemarkDTO dto);

    Result<Void> workorderReview(String code, TransitionDTO dto);

    Result<Void> workorderAcceptance(String code, @Valid TransitionDTO dto);

    Result<Void> workorderAssign(String code, @Valid AssignDTO dto);

    Result<Void> workorderTransfer(String code, @Valid RemarkDTO dto);

    Result<Void> workorderComplete(String code, @Valid WorkorderCompleteDTO dto);

    Result<PageResult<WorkorderVO>> workorderList(@Valid WorkorderQueryDTO queryDTO);

    Result<WorkorderDetailVO> getWorkorderDetailByCode(String code);

    Result<Void> workorderUpdateDraft(String code, @Valid WorkorderUpdateDTO dto);

    Result<Void> workorderDeleteDraft(String code);
}
