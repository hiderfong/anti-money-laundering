package com.insurance.aml.module.integration.adapter;

import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import com.insurance.aml.module.integration.model.entity.IntegrationJob;

/**
 * 外部系统协议适配器扩展点。
 */
public interface IntegrationAdapter {
    String transportType();

    IntegrationExecutionResult healthCheck(IntegrationConnector connector);

    IntegrationExecutionResult execute(IntegrationConnector connector, IntegrationJob job, int attempt);
}
