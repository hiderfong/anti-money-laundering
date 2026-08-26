package com.insurance.aml.module.integration.model.dto;

import com.insurance.aml.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IntegrationRunQuery extends PageQuery {
    private Long jobId;
    private Long connectorId;
    private String status;
    private String triggerType;
}
