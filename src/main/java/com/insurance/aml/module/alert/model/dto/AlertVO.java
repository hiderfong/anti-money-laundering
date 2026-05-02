package com.insurance.aml.module.alert.model.dto;

import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预警详情视图对象
 */
@Data
public class AlertVO {

    /** 主键ID */
    private Long id;

    /** 预警编号 */
    private String alertNo;

    /** 客户ID */
    private Long customerId;

    /** 客户姓名 */
    private String customerName;

    /** 预警类型 */
    private String alertType;

    /** 风险评分 */
    private Integer riskScore;

    /** 风险等级 */
    private String riskLevel;

    /** 命中规则编码 */
    private String sourceRuleCodes;

    /** 预警摘要 */
    private String alertSummary;

    /** 预警状态 */
    private String status;

    /** 分配处理人ID */
    private Long assignedTo;

    /** 分配时间 */
    private LocalDateTime assignedTime;

    /** 处理结果 */
    private String processResult;

    /** 处理备注 */
    private String processRemark;

    /** 处理时间 */
    private LocalDateTime processTime;

    /** 去重键 */
    private String deduplicateKey;

    /** 关联交易ID */
    private String relatedTransactionIds;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    /** 命中规则明细列表 */
    private List<AlertRuleDetail> ruleDetails;
}
