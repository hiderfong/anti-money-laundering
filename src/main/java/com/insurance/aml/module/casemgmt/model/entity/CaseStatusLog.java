package com.insurance.aml.module.casemgmt.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 案件状态变更日志实体
 * 记录案件每次状态变更的详细信息
 */
@Data
@TableName("t_case_status_log")
public class CaseStatusLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联案件ID
     */
    private Long caseId;

    /**
     * 原状态（首次创建时为null）
     */
    private String fromStatus;

    /**
     * 目标状态
     */
    private String toStatus;

    /**
     * 状态变更备注
     */
    private String remark;

    /**
     * 变更人
     */
    private String changedBy;

    /**
     * 变更时间
     */
    private LocalDateTime changedTime;
}
