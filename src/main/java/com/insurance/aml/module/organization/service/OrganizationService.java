package com.insurance.aml.module.organization.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.organization.model.dto.OrganizationDetailVO;
import com.insurance.aml.module.organization.model.dto.OrganizationOverviewVO;
import com.insurance.aml.module.organization.model.dto.OrganizationPersonRequest;
import com.insurance.aml.module.organization.model.dto.OrganizationQueryRequest;
import com.insurance.aml.module.organization.model.dto.OrganizationRegistrationRequest;
import com.insurance.aml.module.organization.model.dto.OrganizationRequest;
import com.insurance.aml.module.organization.model.dto.OrganizationReviewRequest;
import com.insurance.aml.module.organization.model.dto.OrganizationShareholderRequest;
import com.insurance.aml.module.organization.model.dto.OrganizationTreeVO;
import com.insurance.aml.module.organization.model.entity.AmlOrganization;
import com.insurance.aml.module.organization.model.entity.OrganizationPerson;
import com.insurance.aml.module.organization.model.entity.OrganizationRegistration;
import com.insurance.aml.module.organization.model.entity.OrganizationShareholder;

import java.util.List;

/**
 * 机构与组织治理服务。
 */
public interface OrganizationService {
    OrganizationOverviewVO overview();

    PageResult<AmlOrganization> pageOrganizations(OrganizationQueryRequest request);

    List<OrganizationTreeVO> organizationTree();

    OrganizationDetailVO getDetail(Long id);

    AmlOrganization createOrganization(OrganizationRequest request);

    AmlOrganization updateOrganization(Long id, OrganizationRequest request);

    OrganizationPerson createPerson(Long organizationId, OrganizationPersonRequest request);

    OrganizationPerson updatePerson(Long personId, OrganizationPersonRequest request);

    OrganizationShareholder createShareholder(Long organizationId, OrganizationShareholderRequest request);

    OrganizationShareholder updateShareholder(Long shareholderId, OrganizationShareholderRequest request);

    OrganizationRegistration createRegistration(Long organizationId, OrganizationRegistrationRequest request);

    OrganizationRegistration submitRegistration(Long registrationId);

    OrganizationRegistration reviewRegistration(Long registrationId, OrganizationReviewRequest request);
}
