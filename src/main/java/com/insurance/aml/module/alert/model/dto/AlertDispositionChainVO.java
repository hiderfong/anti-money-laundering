package com.insurance.aml.module.alert.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 预警处置链路视图对象。
 */
@Data
@Schema(description = "预警处置链路视图对象")
public class AlertDispositionChainVO {

    @Schema(description = "预警ID")
    private Long alertId;

    @Schema(description = "预警编号")
    private String alertNo;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "预警状态")
    private String alertStatus;

    @Schema(description = "处理结果")
    private String processResult;

    @Schema(description = "链路汇总")
    private Summary summary = new Summary();

    @Schema(description = "关联交易")
    private List<TransactionNode> transactions = new ArrayList<>();

    @Schema(description = "关联案件")
    private List<CaseNode> cases = new ArrayList<>();

    @Schema(description = "可疑交易报告")
    private List<StrReportNode> strReports = new ArrayList<>();

    @Schema(description = "链路步骤")
    private List<Step> steps = new ArrayList<>();

    @Data
    @Schema(description = "链路汇总")
    public static class Summary {
        @Schema(description = "交易数量")
        private int transactionCount;

        @Schema(description = "案件数量")
        private int caseCount;

        @Schema(description = "STR报告数量")
        private int strReportCount;

        @Schema(description = "是否已形成案件")
        private boolean hasCase;

        @Schema(description = "是否已形成STR")
        private boolean hasStrReport;

        @Schema(description = "是否已报送监管")
        private boolean submittedToRegulator;
    }

    @Data
    @Schema(description = "交易节点")
    public static class TransactionNode {
        private Long id;
        private String transactionNo;
        private Long policyId;
        private String transactionType;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String status;
        private String counterpartyName;
        private LocalDateTime transactionTime;
    }

    @Data
    @Schema(description = "案件节点")
    public static class CaseNode {
        private Long id;
        private String caseNo;
        private String caseStatus;
        private String caseType;
        private Integer priority;
        private String summary;
        private LocalDateTime createdTime;
        private LocalDateTime submitTime;
        private LocalDateTime closeTime;
    }

    @Data
    @Schema(description = "STR报告节点")
    public static class StrReportNode {
        private Long id;
        private Long caseId;
        private String reportNo;
        private String reportType;
        private String reportStatus;
        private String submitResult;
        private LocalDateTime createdTime;
        private LocalDateTime submitTime;
    }

    @Data
    @Schema(description = "链路步骤")
    public static class Step {
        private String key;
        private String title;
        private String subtitle;
        private String meta;
        private LocalDateTime time;
        private String state;
    }
}
