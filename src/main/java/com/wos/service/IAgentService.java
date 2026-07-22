package com.wos.service;

import com.wos.common.Result;
import com.wos.domain.vo.AgentCurrentUserVO;

public interface IAgentService {

    Result<AgentCurrentUserVO> currentUser();
}
