package com.insurance.aml.module.casemgmt.model.dto;

import com.insurance.aml.module.casemgmt.model.entity.CaseAttachment;
import com.insurance.aml.module.casemgmt.model.entity.CaseInvestigation;
import com.insurance.aml.module.casemgmt.model.entity.CaseStatusLog;
import com.insurance.aml.module.casemgmt.model.entity.StrReport;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 案件详情视图对象
 * 包含案件全部关联数据：调查记录、附件、可疑交易报告、状态日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "案件详情视图对象")
public class CaseDetailVO extends CaseVO {

    /**
     * 调查记录列表
     */
    @Schema(description = "调查记录列表")
    private List<CaseInvestigation> investigations;

    /**
     * 附件列表
     */
    @Schema(description = "附件列表")
    private List<CaseAttachment> attachments;

    /**
     * 可疑交易报告（可能为空）
     */
    @Schema(description = "可疑交易报告")
    private StrReport strReport;

    /**
     * 状态变更日志列表
     */
    @Schema(description = "状态变更日志列表")
    private List<CaseStatusLog> statusLogs;
}
