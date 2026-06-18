package com.wos.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工单流转日志,对应表 workorder_log。日志只增,不改不删。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("workorder_log")
public class WorkorderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workorderId;

    private Long operatorId;

    private String fromStatus;

    private String toStatus;

    private String event;

    private String remark;

    private LocalDateTime createTime;
}
