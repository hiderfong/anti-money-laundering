package com.insurance.aml.module.integration.model.dto;

import com.insurance.aml.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IntegrationJobQuery extends PageQuery {
    private String keyword;
    private Long connectorId;
    private String businessObject;
    private Boolean enabled;
    private String executionStatus;
}
