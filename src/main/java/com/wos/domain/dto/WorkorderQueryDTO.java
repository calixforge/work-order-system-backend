package com.wos.domain.dto;

import com.wos.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;


@Data
@Schema(description = "工单查询参数")
public class WorkorderQueryDTO extends PageQuery {

    @Schema(description = "搜索关键字: 工单标题")
    private String keyword;

    /**
     * 通用列表允许按状态筛选。
     * 待审核/待派单这类固定语义接口会在 Service 层覆盖该值。
     */
    @Schema(description = "状态，待审核、待派单接口可不传")
    private String status;

    @Min(1) @Max(3)
    @Schema(description = "优先级:1高 2中 3低")
    private Integer priority;
}
