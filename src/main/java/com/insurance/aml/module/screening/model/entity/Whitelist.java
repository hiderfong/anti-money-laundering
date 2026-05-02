package com.insurance.aml.module.screening.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 白名单实体
 * 记录经审核后排除的误报客户，避免重复筛查告警
 */
@Data
@TableName("t_whitelist")
public class Whitelist extends BaseEntity {

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户姓名
     */
    private String customerName;

    /**
     * 制裁名单条目ID（关联t_watchlist）
     */
    private Long watchlistEntryId;

    /**
     * 制裁名单名称
     */
    private String watchlistName;

    /**
     * 排除原因
     */
    private String excludeReason;

    /**
     * 证据材料（如说明文件路径或描述）
     */
    private String evidence;

    /**
     * 生效日期
     */
    private LocalDate effectiveDate;

    /**
     * 失效日期
     */
    private LocalDate expiryDate;

    /**
     * 审批人
     */
    private String approvedBy;

    /**
     * 审批时间
     */
    private LocalDateTime approvedTime;

    /**
     * 状态：ACTIVE-生效、EXPIRED-已过期、REVOKED-已撤销
     */
    private String reviewStatus;
}
