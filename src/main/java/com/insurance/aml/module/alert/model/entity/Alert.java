package com.insurance.aml.module.alert.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预警实体
 */
@Data
@TableName("t_alert")
public class Alert {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 预警编号 */
    private String alertNo;

    /** 客户ID */
    private Long customerId;

    /** 客户姓名 */
    private String customerName;

    /**
     * 预警类型
     * LARGE_TXN - 大额交易
     * SUSPICIOUS - 可疑交易
     * SANCTIONS_HIT - 制裁命中
     * PEP_HIT - PEP命中
     * MANUAL - 人工创建
     */
    private String alertType;

    /** 风险评分 */
    private Integer riskScore;

    /**
     * 风险等级
     * LOW - 低
     * MEDIUM - 中
     * HIGH - 高
     * CRITICAL - 严重
     */
    private String riskLevel;

    /** 命中规则编码（多个以逗号分隔） */
    private String sourceRuleCodes;

    /** 预警摘要 */
    private String alertSummary;

    /**
     * 预警状态
     * NEW - 新建
     * ASSIGNED - 已分配
     * PROCESSING - 处理中
     * CONFIRMED - 已确认
     * EXCLUDED - 已排除
     * ESCALATED - 已升级
     */
    private String status;

    /** 分配处理人ID */
    private Long assignedTo;

    /** 分配时间 */
    private LocalDateTime assignedTime;

    /**
     * 处理结果
     * CONFIRMED_SUSPICIOUS - 确认可疑
     * EXCLUDED - 排除
     * ESCALATED - 升级
     */
    private String processResult;

    /** 处理备注 */
    private String processRemark;

    /** 处理时间 */
    private LocalDateTime processTime;

    /** 去重键 */
    private String deduplicateKey;

    /** 关联交易ID（多个以逗号分隔） */
    private String relatedTransactionIds;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
