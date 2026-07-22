package com.wos.controller;

import com.wos.common.Result;
import com.wos.domain.vo.AgentCurrentUserVO;
import com.wos.service.IAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent 对接接口")
@RestController
@RequiredArgsConstructor
public class AgentController {

    private final IAgentService agentService;

    @Operation(summary = "获取当前登录用户 ID")
    @GetMapping("/auth/me")
    public Result<AgentCurrentUserVO> currentUser() {
        return agentService.currentUser();
    }
}
