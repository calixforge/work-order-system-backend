package com.wos.common.enums;

import lombok.Getter;

/**
 * 工单流转事件,对应状态机的每条转换。
 * 后续所有状态变更都应通过“事件”触发,避免前端直接传目标状态改库。
 */
@Getter
public enum WorkOrderEvent {

    SUBMIT("提交"),
    REVIEW_PASS("审核通过"),
    REVIEW_REJECT("驳回"),
    ASSIGN("派单"),
    TRANSFER("转派"),
    COMPLETE("标记完成"),
    ACCEPT("验收通过"),
    REJECT_REWORK("验收不通过"),
    CANCEL("取消"),
    WITHDRAW("撤回");

    private final String desc;

    WorkOrderEvent(String desc) {
        this.desc = desc;
    }
}
