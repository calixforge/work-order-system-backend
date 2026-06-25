package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "工单流转日志视图")
public class WorkorderLogVO {

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人姓名")
    private String operatorName;

    @Schema(description = "原状态编码")
    private String fromStatus;

    @Schema(description = "原状态中文")
    private String fromStatusDesc;

    @Schema(description = "新状态编码")
    private String toStatus;

    @Schema(description = "新状态中文")
    private String toStatusDesc;

    @Schema(description = "流转事件编码")
    private String event;

    @Schema(description = "流转事件中文")
    private String eventDesc;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}
