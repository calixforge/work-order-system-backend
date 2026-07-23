package com.wos.service;

import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.domain.dto.AgentWorkorderQueryDTO;
import com.wos.domain.vo.AgentCurrentUserVO;
import com.wos.domain.vo.WorkorderDetailVO;
import com.wos.domain.vo.WorkorderVO;

public interface IAgentService {

    Result<AgentCurrentUserVO> currentUser();

    Result<PageResult<WorkorderVO>> queryWorkorders(AgentWorkorderQueryDTO queryDTO);

    Result<WorkorderDetailVO> getWorkorderDetail(String code);
}
