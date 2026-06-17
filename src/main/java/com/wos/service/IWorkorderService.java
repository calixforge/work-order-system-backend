package com.wos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.domain.dto.WorkorderCreateDTO;
import com.wos.domain.dto.WorkorderQueryDTO;
import com.wos.domain.pojo.Workorder;
import com.wos.domain.vo.WorkorderVO;
import jakarta.validation.Valid;

/**
 * 工单业务接口。
 * 第一版先覆盖创建和不同角色视角的列表查询,状态流转接口后续继续补。
 */
public interface IWorkorderService extends IService<Workorder> {

    Result<Long> workorderCreate(WorkorderCreateDTO workorderCreateDTO);

    Result<PageResult<WorkorderVO>> workorderQueryCreated(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<WorkorderVO>> workorderQueryAssigned(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<WorkorderVO>> workorderQueryReview(@Valid WorkorderQueryDTO queryDTO);

    Result<PageResult<WorkorderVO>> workorderQueryDispatch(@Valid WorkorderQueryDTO queryDTO);
}
