package com.wos.controller;

import com.wos.common.PageResult;
import com.wos.common.Result;
import com.wos.domain.dto.AgentWorkorderQueryDTO;
import com.wos.domain.vo.AgentCurrentUserVO;
import com.wos.domain.vo.WorkorderDetailVO;
import com.wos.domain.vo.WorkorderVO;
import com.wos.service.IAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(summary = "Agent 查询当前用户相关工单")
    @PostMapping("/agent/workorders/query")
    public Result<PageResult<WorkorderVO>> queryWorkorders(
            @Valid @RequestBody AgentWorkorderQueryDTO queryDTO) {
        return agentService.queryWorkorders(queryDTO);
    }

    @Operation(summary = "Agent 按工单编号查询工单详情")
    @GetMapping("/agent/workorders/{code}")
    public Result<WorkorderDetailVO> getWorkorderDetail(@PathVariable String code) {
        return agentService.getWorkorderDetail(code);
    }
}
