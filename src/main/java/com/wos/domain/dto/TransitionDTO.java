package com.wos.domain.dto;

import com.wos.common.enums.WorkOrderEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class TransitionDTO {

    @Schema(description = "流转事件")
    @NotNull(message = "流转事件不能为空")
    private WorkOrderEvent event;

    @Schema(description = "备注(驳回/转派等需填写原因)")
    private String remark;
}
