package com.insurance.aml.module.organization.model.dto;

import com.insurance.aml.module.organization.model.entity.AmlOrganization;
import com.insurance.aml.module.organization.model.entity.OrganizationPerson;
import com.insurance.aml.module.organization.model.entity.OrganizationRegistration;
import com.insurance.aml.module.organization.model.entity.OrganizationReviewLog;
import com.insurance.aml.module.organization.model.entity.OrganizationShareholder;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 机构详情聚合视图，避免前端为一个详情抽屉重复发起多次请求。
 */
@Data
@Builder
public class OrganizationDetailVO {
    private AmlOrganization organization;
    private List<OrganizationPerson> persons;
    private List<OrganizationShareholder> shareholders;
    private List<OrganizationRegistration> registrations;
    private List<OrganizationReviewLog> reviewLogs;
}
