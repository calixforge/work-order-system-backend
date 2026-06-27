package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 首页工单统计视图。
 */
@Data
@Schema(description = "首页工单统计")
public class WorkorderStatsVO {

    @Schema(description = "我的待审核数量:审核员统计本部门待审核工单")
    private Long pendingReview = 0L;

    @Schema(description = "待派单数量:派单人统计全局待派单工单")
    private Long pendingAssign = 0L;

    @Schema(description = "我的待处理数量:接单人统计派给自己的已接单工单")
    private Long assigned = 0L;

    @Schema(description = "我的待验收数量:提单人统计自己创建且待验收的工单")
    private Long pendingAcceptance = 0L;

    @Schema(description = "我创建的工单总数")
    private Long created = 0L;
}
