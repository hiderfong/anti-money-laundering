package com.insurance.aml.module.reporting.gateway;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RegulatoryGatewayAdapterRegistry {
    private final List<RegulatoryGatewayAdapter> adapters;

    public RegulatoryGatewayAdapterRegistry(List<RegulatoryGatewayAdapter> adapters) {
        this.adapters = adapters;
    }

    public RegulatoryGatewayAdapter resolve(String transportType) {
        return adapters.stream()
                .filter(adapter -> adapter.transportType().equalsIgnoreCase(transportType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.REPORT_GATEWAY_NOT_FOUND,
                        "尚未安装监管报送适配器：" + transportType));
    }
}
