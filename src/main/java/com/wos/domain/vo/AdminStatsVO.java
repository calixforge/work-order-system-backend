package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理员首页统计视图。
 */
@Data
@Schema(description = "管理员首页统计")
public class AdminStatsVO {

    @Schema(description = "工单总数")
    private Long workorderTotal = 0L;

    @Schema(description = "待审核工单数")
    private Long pendingReview = 0L;

    @Schema(description = "待派单工单数")
    private Long pendingAssign = 0L;

    @Schema(description = "启用用户数")
    private Long enabledUsers = 0L;

    @Schema(description = "停用用户数")
    private Long disabledUsers = 0L;

    @Schema(description = "部门总数")
    private Long departmentCount = 0L;
}
