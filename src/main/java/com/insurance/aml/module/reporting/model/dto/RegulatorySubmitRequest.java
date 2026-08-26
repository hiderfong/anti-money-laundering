package com.insurance.aml.module.reporting.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegulatorySubmitRequest {
    @NotBlank(message = "报告类型不能为空")
    private String reportType;

    @NotNull(message = "报告ID不能为空")
    private Long reportId;

    private Long connectorId;
}
