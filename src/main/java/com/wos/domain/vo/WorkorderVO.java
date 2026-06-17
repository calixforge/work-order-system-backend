package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单视图对象,用于列表/详情返回。
 * PO 只对应数据库字段;VO 额外补充状态中文、优先级中文、人员/部门名称,方便前端展示。
 */
@Data
@Schema(description = "工单视图")
public class WorkorderVO {

    @Schema(description = "工单ID")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "状态编码")
    private String status;

    @Schema(description = "状态中文")
    private String statusDesc;

    @Schema(description = "优先级:1高 2中 3低")
    private Integer priority;

    @Schema(description = "优先级中文")
    private String priorityDesc;

    @Schema(description = "创建人姓名")
    private String creatorName;

    @Schema(description = "接单人姓名")
    private String assigneeName;

    @Schema(description = "所属部门")
    private String departmentName;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "完成时间")
    private LocalDateTime completeTime;
}
