package com.insurance.aml.module.integration.adapter;

import lombok.Builder;
import lombok.Data;

/**
 * 适配器执行结果，不向业务层暴露具体协议实现。
 */
@Data
@Builder
public class IntegrationExecutionResult {
    private boolean success;
    private int recordsRead;
    private int recordsWritten;
    private int recordsSkipped;
    private int errorCount;
    private String responseSummary;
    private String errorMessage;
}
