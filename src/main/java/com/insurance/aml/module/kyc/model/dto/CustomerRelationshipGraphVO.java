package com.insurance.aml.module.kyc.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户关系图谱视图对象。
 */
@Data
@Schema(description = "客户关系图谱视图对象")
public class CustomerRelationshipGraphVO {

    @Schema(description = "图谱节点")
    private List<Node> nodes = new ArrayList<>();

    @Schema(description = "图谱关系")
    private List<Link> links = new ArrayList<>();

    @Schema(description = "图谱摘要")
    private Summary summary = new Summary();

    @Schema(description = "图谱解读")
    private List<String> insights = new ArrayList<>();

    @Data
    @Schema(description = "关系图谱节点")
    public static class Node {
        private String id;
        private String label;
        private String type;
        private String category;
        private String status;
        private String riskLevel;
        private Integer riskScore;
        private BigDecimal amount;
        private Map<String, Object> detail = new LinkedHashMap<>();
    }

    @Data
    @Schema(description = "关系图谱边")
    public static class Link {
        private String source;
        private String target;
        private String label;
        private BigDecimal value;
        private String riskLevel;
        private Map<String, Object> detail = new LinkedHashMap<>();
    }

    @Data
    @Schema(description = "关系图谱摘要")
    public static class Summary {
        private int nodeCount;
        private int linkCount;
        private int beneficialOwnerCount;
        private int policyCount;
        private int productCount;
        private int transactionCount;
        private int alertCount;
        private int caseCount;
        private int strReportCount;
        private int watchlistHitCount;
        private int riskSignalCount;
        private BigDecimal totalTransactionAmount = BigDecimal.ZERO;
    }
}
