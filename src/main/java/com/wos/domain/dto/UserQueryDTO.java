package com.wos.domain.dto;


import com.wos.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserQueryDTO extends PageQuery {

    @Schema(description = "搜索关键字: 用户名 / 真实姓名")
    private String keyword;

    @Schema(description = "状态:1启用 0停用;管理员用户列表可传,接单人列表固定只查启用用户")
    @Min(0)
    @Max(1)
    private Integer status;
}
