package com.insurance.aml.module.reporting.gateway;

import com.insurance.aml.module.integration.model.entity.IntegrationConnector;

/** 监管报送专用适配器。真实专网、SFTP或REST实现可按传输类型注册。 */
public interface RegulatoryGatewayAdapter {
    String transportType();

    RegulatoryGatewayResult submit(IntegrationConnector connector, RegulatorySubmissionEnvelope envelope);

    RegulatoryGatewayResult queryReceipt(IntegrationConnector connector, String externalRequestId);
}
