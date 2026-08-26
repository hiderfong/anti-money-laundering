package com.insurance.aml.module.integration.model.dto;

import com.insurance.aml.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IntegrationConnectorQuery extends PageQuery {
    private String keyword;
    private String businessType;
    private String transportType;
    private String status;
    private String healthStatus;
}
