package com.wos.domain.dto;

import com.wos.common.enums.CreatedWorkorderView;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "我创建的工单查询参数")
public class WorkorderCreatedQueryDTO extends WorkorderQueryDTO {

    @Schema(description = "页面分组:TODO待我处理,PROCESSING流转中,FINISHED已结束,ALL全部")
    private CreatedWorkorderView view;
}
