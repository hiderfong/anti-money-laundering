package com.insurance.aml.module.organization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.module.organization.mapper.AmlOrganizationMapper;
import com.insurance.aml.module.organization.mapper.OrganizationPersonMapper;
import com.insurance.aml.module.organization.mapper.OrganizationRegistrationMapper;
import com.insurance.aml.module.organization.mapper.OrganizationReviewLogMapper;
import com.insurance.aml.module.organization.mapper.OrganizationShareholderMapper;
import com.insurance.aml.module.organization.model.dto.OrganizationRegistrationRequest;
import com.insurance.aml.module.organization.model.dto.OrganizationRequest;
import com.insurance.aml.module.organization.model.dto.OrganizationReviewRequest;
import com.insurance.aml.module.organization.model.entity.AmlOrganization;
import com.insurance.aml.module.organization.model.entity.OrganizationRegistration;
import com.insurance.aml.module.organization.model.entity.OrganizationReviewLog;
import com.insurance.aml.module.organization.service.impl.OrganizationServiceImpl;
import com.insurance.aml.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 机构登记状态机测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("机构治理服务测试")
class OrganizationServiceImplTest {
    @Mock private AmlOrganizationMapper organizationMapper;
    @Mock private OrganizationPersonMapper personMapper;
    @Mock private OrganizationShareholderMapper shareholderMapper;
    @Mock private OrganizationRegistrationMapper registrationMapper;
    @Mock private OrganizationReviewLogMapper reviewLogMapper;
    @Mock private SysUserMapper userMapper;

    private OrganizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrganizationServiceImpl(organizationMapper, personMapper, shareholderMapper,
                registrationMapper, reviewLogMapper, userMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("创建总机构 -> 默认停用且登记状态为草稿")
    void createHeadOffice_defaultsToDraftAndDisabled() {
        when(organizationMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            AmlOrganization organization = invocation.getArgument(0);
            organization.setId(100L);
            return 1;
        }).when(organizationMapper).insert(any(AmlOrganization.class));

        OrganizationRequest request = completeOrganizationRequest();
        AmlOrganization created = service.createOrganization(request);

        assertEquals(100L, created.getId());
        assertEquals("HQ-001", created.getOrgCode());
        assertEquals("DRAFT", created.getRegistrationStatus());
        assertEquals("DISABLED", created.getStatus());
    }

    @Test
    @DisplayName("提交登记 -> 缺少反洗钱人员时拒绝提交")
    void submitRegistration_rejectsIncompleteGovernanceData() {
        AmlOrganization organization = completeOrganization();
        OrganizationRegistration registration = draftRegistration();
        when(registrationMapper.selectById(200L)).thenReturn(registration);
        when(organizationMapper.selectById(100L)).thenReturn(organization);
        when(personMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submitRegistration(200L));

        assertTrue(exception.getMessage().contains("请补充"));
    }

    @Test
    @DisplayName("提交登记 -> 完整资料进入待审批并固化快照")
    void submitRegistration_completeDataMovesToPendingReview() {
        AmlOrganization organization = completeOrganization();
        OrganizationRegistration registration = draftRegistration();
        when(registrationMapper.selectById(200L)).thenReturn(registration);
        when(organizationMapper.selectById(100L)).thenReturn(organization);
        when(personMapper.selectCount(any())).thenReturn(1L, 1L);
        when(shareholderMapper.selectCount(any())).thenReturn(1L);
        when(personMapper.selectList(any())).thenReturn(List.of());
        when(shareholderMapper.selectList(any())).thenReturn(List.of());

        OrganizationRegistration submitted = service.submitRegistration(200L);

        assertEquals("PENDING_REVIEW", submitted.getStatus());
        assertEquals("PENDING_REVIEW", organization.getRegistrationStatus());
        assertTrue(submitted.getSnapshotJson().contains("organization"));
        verify(registrationMapper).updateById(registration);
        verify(organizationMapper).updateById(organization);
        ArgumentCaptor<OrganizationReviewLog> logCaptor = ArgumentCaptor.forClass(OrganizationReviewLog.class);
        verify(reviewLogMapper).insert(logCaptor.capture());
        assertEquals("SUBMIT", logCaptor.getValue().getActionType());
    }

    @Test
    @DisplayName("审批登记 -> 通过后启用机构，驳回时必须填写意见")
    void reviewRegistration_enforcesOpinionAndEnablesApprovedOrganization() {
        AmlOrganization organization = completeOrganization();
        organization.setRegistrationStatus("PENDING_REVIEW");
        OrganizationRegistration registration = draftRegistration();
        registration.setStatus("PENDING_REVIEW");
        when(registrationMapper.selectById(200L)).thenReturn(registration);
        when(organizationMapper.selectById(100L)).thenReturn(organization);

        OrganizationReviewRequest rejectedWithoutOpinion = new OrganizationReviewRequest();
        rejectedWithoutOpinion.setApproved(false);
        assertThrows(BusinessException.class,
                () -> service.reviewRegistration(200L, rejectedWithoutOpinion));

        OrganizationReviewRequest approved = new OrganizationReviewRequest();
        approved.setApproved(true);
        approved.setOpinion("机构信息完整，同意登记");
        OrganizationRegistration reviewed = service.reviewRegistration(200L, approved);

        assertEquals("APPROVED", reviewed.getStatus());
        assertEquals("APPROVED", organization.getRegistrationStatus());
        assertEquals("ENABLED", organization.getStatus());
    }

    private OrganizationRequest completeOrganizationRequest() {
        OrganizationRequest request = new OrganizationRequest();
        request.setOrgCode("hq-001");
        request.setOrgName("华岳保险股份有限公司");
        request.setUnifiedCreditCode("91310000MA1AML001X");
        request.setOrgType("HEAD_OFFICE");
        request.setLegalRepresentative("周明远");
        request.setRegisteredAddress("上海市浦东新区世纪大道100号");
        request.setBusinessAddress("上海市浦东新区世纪大道100号");
        return request;
    }

    private AmlOrganization completeOrganization() {
        AmlOrganization organization = new AmlOrganization();
        organization.setId(100L);
        organization.setOrgCode("HQ-001");
        organization.setOrgName("华岳保险股份有限公司");
        organization.setUnifiedCreditCode("91310000MA1AML001X");
        organization.setOrgType("HEAD_OFFICE");
        organization.setLegalRepresentative("周明远");
        organization.setRegisteredAddress("上海市浦东新区世纪大道100号");
        organization.setBusinessAddress("上海市浦东新区世纪大道100号");
        organization.setRegistrationStatus("DRAFT");
        return organization;
    }

    private OrganizationRegistration draftRegistration() {
        OrganizationRegistrationRequest request = new OrganizationRegistrationRequest();
        request.setCommitmentAccepted(true);
        OrganizationRegistration registration = new OrganizationRegistration();
        registration.setId(200L);
        registration.setOrganizationId(100L);
        registration.setRegistrationNo("ORGREG-001");
        registration.setStatus("DRAFT");
        registration.setCommitmentAccepted(request.getCommitmentAccepted());
        return registration;
    }
}
