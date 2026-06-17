package com.wos.common.enums;

import lombok.Getter;

/**
 * 工单状态。数据库存枚举 name,desc 用于显示。
 */
@Getter
public enum WorkOrderStatus {

    DRAFT("草稿"),
    PENDING_REVIEW("待审核"),
    PENDING_ASSIGN("待派单"),
    ACCEPTED("已接单"),
    COMPLETED("已完成"),
    CLOSED("已关闭"),
    CANCELED("已取消");

    private final String desc;

    WorkOrderStatus(String desc) {
        this.desc = desc;
    }
}
