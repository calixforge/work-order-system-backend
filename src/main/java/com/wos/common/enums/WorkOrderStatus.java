package com.wos.common.enums;

import lombok.Getter;

/**
 * 工单状态。
 * 数据库存枚举 name(如 PENDING_REVIEW),接口返回时额外补充 desc 给前端显示。
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

    /**
     * 根据数据库中的状态 name 获取中文描述。
     */
    public static String descOf(String name) {
        if (name == null) {
            return null;
        }
        try {
            return WorkOrderStatus.valueOf(name).getDesc();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
