package com.wos.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "工单创建结果")
public class WorkorderCreateVO {

    @Schema(description = "对外工单编号")
    private String code;
}
