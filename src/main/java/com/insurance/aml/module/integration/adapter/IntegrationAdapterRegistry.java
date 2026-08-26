package com.insurance.aml.module.integration.adapter;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按传输类型选择适配器，后续REST、SFTP、MQ实现可直接注册进来。
 */
@Component
public class IntegrationAdapterRegistry {
    private final List<IntegrationAdapter> adapters;

    public IntegrationAdapterRegistry(List<IntegrationAdapter> adapters) {
        this.adapters = adapters;
    }

    public IntegrationAdapter resolve(String transportType) {
        return adapters.stream()
                .filter(adapter -> adapter.transportType().equalsIgnoreCase(transportType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ResultCode.INTEGRATION_ADAPTER_NOT_FOUND,
                        "尚未安装该传输类型的适配器：" + transportType));
    }
}
