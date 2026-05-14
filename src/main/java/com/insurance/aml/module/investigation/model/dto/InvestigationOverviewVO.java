package com.insurance.aml.module.investigation.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 调查协查概览。
 */
@Data
@Builder
@Schema(description = "调查协查概览")
public class InvestigationOverviewVO {

    @Schema(description = "待处理请求数")
    private long pendingRequests;

    @Schema(description = "处理中请求数")
    private long processingRequests;

    @Schema(description = "即将到期请求数")
    private long dueSoonRequests;

    @Schema(description = "已逾期请求数")
    private long overdueRequests;

    @Schema(description = "已闭环请求数")
    private long closedRequests;
}
