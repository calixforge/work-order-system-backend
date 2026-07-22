package com.wos.service.impl;

import com.wos.common.Result;
import com.wos.common.ResultCode;
import com.wos.common.UserContext;
import com.wos.domain.vo.AgentCurrentUserVO;
import com.wos.exception.BusinessException;
import com.wos.service.IAgentService;
import org.springframework.stereotype.Service;

@Service
public class AgentServiceImpl implements IAgentService {

    @Override
    public Result<AgentCurrentUserVO> currentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(new AgentCurrentUserVO(userId));
    }
}
