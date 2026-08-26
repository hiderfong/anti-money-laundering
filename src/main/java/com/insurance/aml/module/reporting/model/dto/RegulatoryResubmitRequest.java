package com.insurance.aml.module.reporting.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegulatoryResubmitRequest {
    private Long connectorId;

    @NotBlank(message = "请填写退回修正说明")
    private String correctionNote;

    /** 可选。未提供时从最新业务数据重新生成报文。 */
    private String correctedPayload;
}
