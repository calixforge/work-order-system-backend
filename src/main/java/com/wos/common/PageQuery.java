package com.wos.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页查询参数基类,查询 DTO 继承它即可带上分页参数。
 */
@Data
public class PageQuery {

    @Schema(description = "页码,从 1 开始")
    private Integer pageNum = 1;

    @Schema(description = "每页条数")
    private Integer pageSize = 10;
}
