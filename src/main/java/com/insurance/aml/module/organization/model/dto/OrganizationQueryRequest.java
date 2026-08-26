package com.insurance.aml.module.organization.model.dto;

import com.insurance.aml.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 机构分页筛选条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrganizationQueryRequest extends PageQuery {
    private String keyword;
    private String orgType;
    private String status;
    private String registrationStatus;
    private Long parentId;
    private Long treeRootId;
}
