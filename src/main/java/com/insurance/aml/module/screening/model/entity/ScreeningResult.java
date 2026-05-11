package com.insurance.aml.module.screening.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 筛查结果实体
 * 记制裁名单筛查的命中结果详情
 */
@Data
@TableName("t_screening_result")
public class ScreeningResult {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的筛查请求ID（关联t_screening_request）
     */
    private String requestId;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户姓名
     */
    private String customerName;

    /**
     * 客户证件号码
     */
    private String customerIdNumber;

    /**
     * 命中的制裁名单条目ID（关联t_watchlist）
     */
    private Long watchlistEntryId;

    /**
     * 制裁名单名称
     */
    private String watchlistName;

    /**
     * 匹配分数（0-100）
     */
    private BigDecimal matchScore;

    /**
     * 匹配类型：EXACT-精确匹配、FUZZY-模糊匹配、COMPOSITE-综合匹配
     */
    private String matchType;

    /**
     * 匹配字段（如name、id_number等）
     */
    private String matchField;

    /**
     * 匹配详情（JSON格式）
     */
    private String matchDetail;

    /**
     * 审核状态：PENDING_REVIEW-待审核、CONFIRMED-确认命中、EXCLUDED-排除、ESCALATED-上报
     */
    private String reviewStatus;

    /**
     * 审核结果
     */
    private String reviewResult;

    /**
     * 审核原因
     */
    private String reviewReason;

    /**
     * 审核人
     */
    private String reviewedBy;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedTime;

    /**
     * 是否已加入白名单
     */
    private Boolean whitelisted;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
