package com.insurance.aml.module.alert.model.dto;

import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预警详情视图对象
 */
@Data
@Schema(description = "预警详情视图对象")
public class AlertVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "预警编号")
    private String alertNo;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户姓名")
    private String customerName;

    @Schema(description = "预警类型")
    private String alertType;

    @Schema(description = "风险评分")
    private Integer riskScore;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "命中规则编码")
    private String sourceRuleCodes;

    @Schema(description = "预警摘要")
    private String alertSummary;

    @Schema(description = "预警状态")
    private String status;

    @Schema(description = "分配处理人ID")
    private Long assignedTo;

    @Schema(description = "分配时间")
    private LocalDateTime assignedTime;

    @Schema(description = "处理结果")
    private String processResult;

    @Schema(description = "处理备注")
    private String processRemark;

    @Schema(description = "处理时间")
    private LocalDateTime processTime;

    @Schema(description = "去重键")
    private String deduplicateKey;

    @Schema(description = "关联交易ID")
    private String relatedTransactionIds;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "命中规则明细列表")
    private List<AlertRuleDetail> ruleDetails;
}
