package com.wos.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工单实体,对应表 workorder。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("workorder")
public class Workorder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    /** 状态,存枚举 name(见 WorkOrderStatus) */
    private String status;

    /** 优先级:1 高 2 中 3 低 */
    private Integer priority;

    private Long creatorId;

    private Long assigneeId;

    private Long departmentId;

    private LocalDateTime completeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDel;
}
