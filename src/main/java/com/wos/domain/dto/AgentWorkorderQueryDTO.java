package com.wos.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wos.common.enums.AgentSortOrder;
import com.wos.common.enums.AgentWorkorderRelation;
import com.wos.common.enums.AgentWorkorderSortBy;
import com.wos.common.enums.WorkOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Agent 工单查询参数")
public class AgentWorkorderQueryDTO {

    @NotNull(message = "工单关系不能为空")
    @Schema(description = "当前用户与工单的关系")
    private AgentWorkorderRelation relation;

    @Size(max = 100, message = "标题关键词不能超过100字")
    @Schema(description = "标题关键词")
    private String keyword;

    @Size(max = 7, message = "状态条件不能超过7个")
    @Schema(description = "工单状态列表")
    private List<@NotNull WorkOrderStatus> statuses;

    @Size(max = 3, message = "优先级条件不能超过3个")
    @Schema(description = "优先级列表:1高,2中,3低")
    private List<@NotNull @Min(1) @Max(3) Integer> priorities;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "创建日期起点,包含当天,格式 yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "创建日期终点,包含当天,格式 yyyy-MM-dd")
    private LocalDate endDate;

    @NotNull(message = "排序字段不能为空")
    @Schema(description = "排序字段")
    private AgentWorkorderSortBy sortBy = AgentWorkorderSortBy.CREATE_TIME;

    @NotNull(message = "排序方向不能为空")
    @Schema(description = "排序方向")
    private AgentSortOrder sortOrder = AgentSortOrder.DESC;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    @Schema(description = "页码,从1开始")
    private Integer pageNum = 1;

    @NotNull(message = "返回条数不能为空")
    @Min(value = 1, message = "返回条数不能小于1")
    @Max(value = 20, message = "返回条数不能超过20")
    @Schema(description = "本页返回条数,最大20")
    private Integer limit = 5;

    @JsonIgnore
    public LocalDateTime getStartTime() {
        return startDate == null ? null : startDate.atStartOfDay();
    }

    @JsonIgnore
    public LocalDateTime getEndTimeExclusive() {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }
}
