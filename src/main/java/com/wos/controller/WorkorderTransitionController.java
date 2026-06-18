package com.wos.controller;

import com.wos.common.Result;
import com.wos.domain.dto.RemarkDTO;
import com.wos.domain.dto.TransitionDTO;
import com.wos.service.IWorkorderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@Tag(name = "工单状态流转管理")
@RequestMapping("/workorder")
@RestController
@RequiredArgsConstructor
public class WorkorderTransitionController {

    private final IWorkorderService workorderService;

    /**
     * 提单人提交草稿工单。
     * 工单从 DRAFT 流转到 PENDING_REVIEW。
     */
    @Operation(summary = "提交工单")
    @PutMapping("/{woId}/submit")
    public Result<Void> workorderSubmit(@PathVariable Long woId) {

        return workorderService.workorderSubmit(woId);
    }

    /**
     * 提单人撤回工单。
     * 接单前可撤回到草稿,修改后需要重新提交审核。
     */
    @Operation(summary = "撤回工单")
    @PutMapping("/{woId}/withdraw")
    public Result<Void> workorderWithdraw(@PathVariable Long woId) {

        return workorderService.workorderWithdraw(woId);
    }

    /**
     * 提单人取消工单。
     * 接单前可取消工单,取消后进入终态 CANCELED。
     */
    @Operation(summary = "取消工单")
    @PutMapping("/{woId}/cancel")
    public Result<Void> workorderCancel(@PathVariable Long woId,
                                        @Valid @RequestBody RemarkDTO dto) {

        return workorderService.workorderCancel(woId, dto);
    }

    /**
     * 审核工单。
     * 审核通过进入待派单;审核驳回退回草稿。
     */
    @Operation(summary = "审核工单")
    @PutMapping("/{woId}/review")
    public Result<Void> workorderReview(@PathVariable Long woId,
                                        @Valid @RequestBody TransitionDTO dto) {

        return workorderService.workorderReview(woId, dto);
    }


    /**
     * 验收工单。
     * 验收通过关闭工单;验收不通过退回接单人返工。
     */
    @Operation(summary = "验收工单")
    @PutMapping("/{woId}/acceptance")
    public Result<Void> workorderAcceptance(@PathVariable Long woId,
                                        @Valid @RequestBody TransitionDTO dto) {

        return workorderService.workorderAcceptance(woId, dto);
    }
}
