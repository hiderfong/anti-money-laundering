package com.insurance.aml.module.alert.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预警分配日志实体
 */
@Data
@TableName("t_alert_assignment_log")
public class AlertAssignmentLog {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 预警ID */
    private Long alertId;

    /** 原处理人ID */
    private Long fromUserId;

    /** 新处理人ID */
    private Long toUserId;

    /**
     * 分配类型
     * AUTO - 自动分配
     * MANUAL - 手动分配
     * ESCALATION - 升级分配
     */
    private String assignType;

    /** 分配原因 */
    private String assignReason;

    /** 操作人 */
    private String assignedBy;

    /** 分配时间 */
    private LocalDateTime assignedTime;
}
