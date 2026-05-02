package com.insurance.aml.module.screening.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 筛查结果展示VO
 * 包含筛查结果全部字段及关联展示信息
 */
@Data
@Schema(description = "筛查结果详情")
public class ScreeningResultVO {

    @Schema(description = "结果ID")
    private Long id;

    @Schema(description = "请求ID")
    private Long requestId;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户姓名")
    private String customerName;

    @Schema(description = "客户证件号码")
    private String customerIdNumber;

    @Schema(description = "制裁名单条目ID")
    private Long watchlistEntryId;

    @Schema(description = "制裁名单姓名")
    private String watchlistName;

    @Schema(description = "匹配分数（0-100）")
    private BigDecimal matchScore;

    @Schema(description = "匹配类型：EXACT/FUZZY/COMPOSITE")
    private String matchType;

    @Schema(description = "匹配字段")
    private String matchField;

    @Schema(description = "匹配详情")
    private String matchDetail;

    @Schema(description = "审核状态")
    private String reviewStatus;

    @Schema(description = "审核结果")
    private String reviewResult;

    @Schema(description = "审核原因")
    private String reviewReason;

    @Schema(description = "审核人")
    private String reviewedBy;

    @Schema(description = "审核时间")
    private LocalDateTime reviewedTime;

    @Schema(description = "是否已加入白名单")
    private Boolean whitelisted;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
