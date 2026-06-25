package com.wos.domain.dto;


import com.wos.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UserQueryDTO extends PageQuery {

    @Schema(description = "搜索关键字: 用户名 / 真实姓名")
    private String keyword;
}
