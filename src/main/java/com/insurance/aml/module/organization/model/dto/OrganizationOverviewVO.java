package com.insurance.aml.module.organization.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 机构治理概览指标。
 */
@Data
@Builder
public class OrganizationOverviewVO {
    private long totalOrganizations;
    private long headOffices;
    private long branches;
    private long outlets;
    private long pendingReviews;
    private long rejectedRegistrations;
    private long amlOfficers;
}
