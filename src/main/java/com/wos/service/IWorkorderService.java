package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.domain.dto.*;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.vo.WorkorderDetailVO;
import com.wos.domain.vo.WorkorderStatsVO;
import com.wos.domain.vo.WorkorderVO;
import jakarta.validation.Valid;

/**
 * 工单业务接口。
 */
public interface IWorkorderService extends IService<Workorder> {

    Result<Long> workorderCreate(WorkorderCreateDTO workorderCreateDTO);

    Result<PageResult<WorkorderVO>> workorderQueryCreated(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<WorkorderVO>> workorderQueryAssigned(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<WorkorderVO>> workorderQueryReview(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<WorkorderVO>> workorderQueryDispatch(@Valid WorkorderQueryDTO queryDTO);

    Result<WorkorderStatsVO> workorderStats();

    Result<Void> workorderSubmit(Long woId);

    Result<Void> workorderWithdraw(Long woId);

    Result<Void> workorderCancel(Long woId, RemarkDTO dto);

    Result<Void> workorderReview(Long woId, TransitionDTO dto);

    Result<Void> workorderAcceptance(Long woId, @Valid TransitionDTO dto);

    Result<Void> workorderAssign(Long woId, @Valid AssignDTO dto);

    Result<Void> workorderTransfer(Long woId, @Valid RemarkDTO dto);

    Result<Void> workorderComplete(Long woId, @Valid WorkorderCompleteDTO dto);

    Result<PageResult<WorkorderVO>> workorderList(@Valid WorkorderQueryDTO queryDTO);

    Result<WorkorderDetailVO> getWorkorderDetail(Long woId);

    Result<Void> workorderUpdateDraft(Long woId, @Valid WorkorderUpdateDTO dto);

    Result<Void> workorderDeleteDraft(Long woId);
}
