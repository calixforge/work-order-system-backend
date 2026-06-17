package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.domain.dto.WorkorderCreateDTO;
import com.wos.domain.dto.WorkorderQueryDTO;
import com.wos.domain.pojo.Workorder;
import jakarta.validation.Valid;

public interface IWorkorderService extends IService<Workorder> {

    Result<Long> workorderCreate(WorkorderCreateDTO workorderCreateDTO);

    Result<PageResult<Workorder>> workorderQueryCreated(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<Workorder>> workorderQueryAssigned(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<Workorder>> workorderQueryReview(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<Workorder>> workorderQueryDispatch(@Valid WorkorderQueryDTO queryDTO);
}
