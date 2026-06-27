package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "工单详情视图")
public class WorkorderDetailVO extends WorkorderVO {

    @Schema(description = "创建人ID")
    private Long creatorId;

    @Schema(description = "接单人ID")
    private Long assigneeId;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "流转日志")
    private List<WorkorderLogVO> logs;
}
