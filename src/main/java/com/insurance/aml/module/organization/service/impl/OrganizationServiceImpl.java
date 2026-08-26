package com.insurance.aml.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.organization.mapper.AmlOrganizationMapper;
import com.insurance.aml.module.organization.mapper.OrganizationPersonMapper;
import com.insurance.aml.module.organization.mapper.OrganizationRegistrationMapper;
import com.insurance.aml.module.organization.mapper.OrganizationReviewLogMapper;
import com.insurance.aml.module.organization.mapper.OrganizationShareholderMapper;
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
import com.insurance.aml.module.organization.model.entity.OrganizationReviewLog;
import com.insurance.aml.module.organization.model.entity.OrganizationShareholder;
import com.insurance.aml.module.organization.service.OrganizationService;
import com.insurance.aml.module.system.mapper.SysUserMapper;
import com.insurance.aml.module.system.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 机构治理服务实现。登记申请采用快照和显式状态机，确保驳回、重提和批准均可追溯。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String REG_DRAFT = "DRAFT";
    private static final String REG_PENDING = "PENDING_REVIEW";
    private static final String REG_REJECTED = "REJECTED";
    private static final String REG_APPROVED = "APPROVED";

    private final AmlOrganizationMapper organizationMapper;
    private final OrganizationPersonMapper personMapper;
    private final OrganizationShareholderMapper shareholderMapper;
    private final OrganizationRegistrationMapper registrationMapper;
    private final OrganizationReviewLogMapper reviewLogMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public OrganizationOverviewVO overview() {
        List<AmlOrganization> organizations = accessibleOrganizations();
        Set<Long> ids = organizations.stream().map(AmlOrganization::getId).collect(Collectors.toSet());
        long amlOfficers = ids.isEmpty() ? 0 : personMapper.selectCount(new LambdaQueryWrapper<OrganizationPerson>()
                .in(OrganizationPerson::getOrganizationId, ids)
                .eq(OrganizationPerson::getPersonType, "AML_OFFICER")
                .eq(OrganizationPerson::getStatus, STATUS_ENABLED));
        return OrganizationOverviewVO.builder()
                .totalOrganizations(organizations.size())
                .headOffices(countOrgType(organizations, "HEAD_OFFICE"))
                .branches(countOrgType(organizations, "BRANCH"))
                .outlets(countOrgType(organizations, "OUTLET"))
                .pendingReviews(countRegistrationStatus(organizations, REG_PENDING))
                .rejectedRegistrations(countRegistrationStatus(organizations, REG_REJECTED))
                .amlOfficers(amlOfficers)
                .build();
    }

    @Override
    public PageResult<AmlOrganization> pageOrganizations(OrganizationQueryRequest request) {
        LambdaQueryWrapper<AmlOrganization> wrapper = new LambdaQueryWrapper<>();
        List<Long> scope = accessibleOrganizationIds();
        if (scope != null) {
            if (scope.isEmpty()) {
                wrapper.eq(AmlOrganization::getId, -1L);
            } else {
                wrapper.in(AmlOrganization::getId, scope);
            }
        }
        if (request.getTreeRootId() != null) {
            Set<Long> subtree = descendantIds(request.getTreeRootId());
            if (scope != null) {
                subtree.retainAll(scope);
            }
            if (subtree.isEmpty()) {
                wrapper.eq(AmlOrganization::getId, -1L);
            } else {
                wrapper.in(AmlOrganization::getId, subtree);
            }
        }
        wrapper.and(StringUtils.hasText(request.getKeyword()), query -> query
                        .like(AmlOrganization::getOrgCode, request.getKeyword())
                        .or().like(AmlOrganization::getOrgName, request.getKeyword())
                        .or().like(AmlOrganization::getUnifiedCreditCode, request.getKeyword())
                        .or().like(AmlOrganization::getLegalRepresentative, request.getKeyword()))
                .eq(StringUtils.hasText(request.getOrgType()), AmlOrganization::getOrgType, request.getOrgType())
                .eq(StringUtils.hasText(request.getStatus()), AmlOrganization::getStatus, request.getStatus())
                .eq(StringUtils.hasText(request.getRegistrationStatus()), AmlOrganization::getRegistrationStatus,
                        request.getRegistrationStatus())
                .eq(request.getParentId() != null, AmlOrganization::getParentId, request.getParentId())
                .orderByAsc(AmlOrganization::getOrgType)
                .orderByAsc(AmlOrganization::getOrgCode);
        IPage<AmlOrganization> page = organizationMapper.selectPage(request.toPage(), wrapper);
        return PageResult.from(page);
    }

    @Override
    public List<OrganizationTreeVO> organizationTree() {
        List<AmlOrganization> organizations = accessibleOrganizations();
        Set<Long> visibleIds = organizations.stream().map(AmlOrganization::getId).collect(Collectors.toSet());
        Map<Long, OrganizationTreeVO> nodes = new LinkedHashMap<>();
        organizations.stream().sorted(Comparator.comparing(AmlOrganization::getOrgCode)).forEach(org -> nodes.put(
                org.getId(), OrganizationTreeVO.builder()
                        .id(org.getId()).orgCode(org.getOrgCode()).orgName(org.getOrgName())
                        .orgType(org.getOrgType()).status(org.getStatus()).build()));

        List<OrganizationTreeVO> roots = new ArrayList<>();
        for (AmlOrganization organization : organizations) {
            OrganizationTreeVO node = nodes.get(organization.getId());
            if (organization.getParentId() == null || !visibleIds.contains(organization.getParentId())) {
                roots.add(node);
            } else {
                nodes.get(organization.getParentId()).getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public OrganizationDetailVO getDetail(Long id) {
        AmlOrganization organization = loadAccessibleOrganization(id);
        List<OrganizationPerson> persons = personMapper.selectList(new LambdaQueryWrapper<OrganizationPerson>()
                .eq(OrganizationPerson::getOrganizationId, id)
                .orderByAsc(OrganizationPerson::getPersonType)
                .orderByDesc(OrganizationPerson::getPrimaryFlag));
        List<OrganizationShareholder> shareholders = shareholderMapper.selectList(
                new LambdaQueryWrapper<OrganizationShareholder>()
                        .eq(OrganizationShareholder::getOrganizationId, id)
                        .orderByDesc(OrganizationShareholder::getOwnershipPercentage));
        List<OrganizationRegistration> registrations = registrationMapper.selectList(
                new LambdaQueryWrapper<OrganizationRegistration>()
                        .eq(OrganizationRegistration::getOrganizationId, id)
                        .orderByDesc(OrganizationRegistration::getVersion));
        List<Long> registrationIds = registrations.stream().map(OrganizationRegistration::getId).toList();
        List<OrganizationReviewLog> logs = registrationIds.isEmpty() ? Collections.emptyList()
                : reviewLogMapper.selectList(new LambdaQueryWrapper<OrganizationReviewLog>()
                        .in(OrganizationReviewLog::getRegistrationId, registrationIds)
                        .orderByDesc(OrganizationReviewLog::getOperatedAt));
        return OrganizationDetailVO.builder()
                .organization(organization).persons(persons).shareholders(shareholders)
                .registrations(registrations).reviewLogs(logs).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmlOrganization createOrganization(OrganizationRequest request) {
        ensureUnique(request, null);
        validateParent(null, request.getParentId(), request.getOrgType());
        if (request.getParentId() == null && SecurityUtils.getCurrentUser() != null && !SecurityUtils.hasRole("ADMIN")) {
            throw new BusinessException(ResultCode.ORGANIZATION_SCOPE_FORBIDDEN, "仅管理员可以创建总机构");
        }
        AmlOrganization organization = new AmlOrganization();
        applyOrganization(organization, request);
        organization.setStatus("DISABLED");
        organization.setRegistrationStatus(REG_DRAFT);
        organizationMapper.insert(organization);
        log.info("创建机构档案，organizationId={}, orgCode={}", organization.getId(), organization.getOrgCode());
        return organization;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmlOrganization updateOrganization(Long id, OrganizationRequest request) {
        AmlOrganization organization = loadAccessibleOrganization(id);
        ensureRegistrationEditable(organization);
        ensureUnique(request, id);
        validateParent(id, request.getParentId(), request.getOrgType());
        applyOrganization(organization, request);
        if (!REG_APPROVED.equals(organization.getRegistrationStatus())) {
            organization.setStatus("DISABLED");
        } else {
            organization.setRegistrationStatus(REG_DRAFT);
        }
        organizationMapper.updateById(organization);
        return organization;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationPerson createPerson(Long organizationId, OrganizationPersonRequest request) {
        AmlOrganization organization = loadAccessibleOrganization(organizationId);
        ensureRegistrationEditable(organization);
        clearExistingPrimary(organizationId, request.getPersonType(), request.getPrimaryFlag(), null);
        OrganizationPerson person = new OrganizationPerson();
        person.setOrganizationId(organizationId);
        applyPerson(person, request);
        personMapper.insert(person);
        markChangedAfterApproval(organization);
        return person;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationPerson updatePerson(Long personId, OrganizationPersonRequest request) {
        OrganizationPerson person = personMapper.selectById(personId);
        if (person == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构人员不存在");
        }
        AmlOrganization organization = loadAccessibleOrganization(person.getOrganizationId());
        ensureRegistrationEditable(organization);
        clearExistingPrimary(person.getOrganizationId(), request.getPersonType(), request.getPrimaryFlag(), personId);
        applyPerson(person, request);
        personMapper.updateById(person);
        markChangedAfterApproval(organization);
        return person;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationShareholder createShareholder(Long organizationId, OrganizationShareholderRequest request) {
        AmlOrganization organization = loadAccessibleOrganization(organizationId);
        ensureRegistrationEditable(organization);
        ensureOwnershipTotal(organizationId, request.getOwnershipPercentage(), request.getStatus(), null);
        OrganizationShareholder shareholder = new OrganizationShareholder();
        shareholder.setOrganizationId(organizationId);
        applyShareholder(shareholder, request);
        shareholderMapper.insert(shareholder);
        markChangedAfterApproval(organization);
        return shareholder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationShareholder updateShareholder(Long shareholderId, OrganizationShareholderRequest request) {
        OrganizationShareholder shareholder = shareholderMapper.selectById(shareholderId);
        if (shareholder == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "股东信息不存在");
        }
        AmlOrganization organization = loadAccessibleOrganization(shareholder.getOrganizationId());
        ensureRegistrationEditable(organization);
        ensureOwnershipTotal(shareholder.getOrganizationId(), request.getOwnershipPercentage(), request.getStatus(), shareholderId);
        applyShareholder(shareholder, request);
        shareholderMapper.updateById(shareholder);
        markChangedAfterApproval(organization);
        return shareholder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationRegistration createRegistration(Long organizationId, OrganizationRegistrationRequest request) {
        AmlOrganization organization = loadAccessibleOrganization(organizationId);
        long unfinished = registrationMapper.selectCount(new LambdaQueryWrapper<OrganizationRegistration>()
                .eq(OrganizationRegistration::getOrganizationId, organizationId)
                .in(OrganizationRegistration::getStatus, REG_DRAFT, REG_PENDING, REG_REJECTED));
        if (unfinished > 0) {
            throw new BusinessException(ResultCode.ORGANIZATION_REGISTRATION_STATUS_ERROR, "已有未完成的登记申请");
        }
        int version = Math.toIntExact(registrationMapper.selectCount(new LambdaQueryWrapper<OrganizationRegistration>()
                .eq(OrganizationRegistration::getOrganizationId, organizationId))) + 1;
        boolean hasApproved = registrationMapper.selectCount(new LambdaQueryWrapper<OrganizationRegistration>()
                .eq(OrganizationRegistration::getOrganizationId, organizationId)
                .eq(OrganizationRegistration::getStatus, REG_APPROVED)) > 0;

        OrganizationRegistration registration = new OrganizationRegistration();
        registration.setRegistrationNo(generateRegistrationNo());
        registration.setOrganizationId(organizationId);
        registration.setRegistrationType(hasApproved ? "CHANGE" : "INITIAL");
        registration.setVersion(version);
        registration.setStatus(REG_DRAFT);
        registration.setCommitmentAccepted(Boolean.TRUE.equals(request.getCommitmentAccepted()));
        registrationMapper.insert(registration);
        organization.setRegistrationStatus(REG_DRAFT);
        organizationMapper.updateById(organization);
        recordLog(registration, "CREATE", null, REG_DRAFT, "创建机构登记申请");
        return registration;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationRegistration submitRegistration(Long registrationId) {
        OrganizationRegistration registration = loadRegistration(registrationId);
        AmlOrganization organization = loadAccessibleOrganization(registration.getOrganizationId());
        if (!REG_DRAFT.equals(registration.getStatus()) && !REG_REJECTED.equals(registration.getStatus())) {
            throw new BusinessException(ResultCode.ORGANIZATION_REGISTRATION_STATUS_ERROR, "仅草稿或被驳回申请可提交");
        }
        if (!Boolean.TRUE.equals(registration.getCommitmentAccepted())) {
            throw new BusinessException(ResultCode.ORGANIZATION_REGISTRATION_INCOMPLETE, "尚未确认合规承诺");
        }
        validateRegistrationCompleteness(organization);
        String fromStatus = registration.getStatus();
        registration.setSnapshotJson(buildSnapshot(organization));
        registration.setStatus(REG_PENDING);
        registration.setSubmittedBy(currentOperator());
        registration.setSubmittedAt(LocalDateTime.now());
        registration.setReviewedBy(null);
        registration.setReviewedAt(null);
        registration.setReviewOpinion(null);
        registrationMapper.updateById(registration);
        organization.setRegistrationStatus(REG_PENDING);
        organizationMapper.updateById(organization);
        recordLog(registration, "SUBMIT", fromStatus, REG_PENDING, "提交机构登记审核");
        return registration;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationRegistration reviewRegistration(Long registrationId, OrganizationReviewRequest request) {
        OrganizationRegistration registration = loadRegistration(registrationId);
        AmlOrganization organization = loadAccessibleOrganization(registration.getOrganizationId());
        if (!REG_PENDING.equals(registration.getStatus())) {
            throw new BusinessException(ResultCode.ORGANIZATION_REGISTRATION_STATUS_ERROR, "仅待审核申请可处理");
        }
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        if (!approved && !StringUtils.hasText(request.getOpinion())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "驳回时必须填写审核意见");
        }
        String targetStatus = approved ? REG_APPROVED : REG_REJECTED;
        registration.setStatus(targetStatus);
        registration.setReviewedBy(currentOperator());
        registration.setReviewedAt(LocalDateTime.now());
        registration.setReviewOpinion(request.getOpinion());
        registrationMapper.updateById(registration);
        organization.setRegistrationStatus(targetStatus);
        if (approved) {
            organization.setStatus(STATUS_ENABLED);
        }
        organizationMapper.updateById(organization);
        recordLog(registration, approved ? "APPROVE" : "REJECT", REG_PENDING, targetStatus, request.getOpinion());
        return registration;
    }

    private void applyOrganization(AmlOrganization organization, OrganizationRequest request) {
        BeanUtils.copyProperties(request, organization);
        organization.setOrgCode(request.getOrgCode().trim().toUpperCase());
        organization.setUnifiedCreditCode(request.getUnifiedCreditCode().trim().toUpperCase());
        if (!StringUtils.hasText(organization.getStatus())) {
            organization.setStatus("DISABLED");
        }
    }

    private void applyPerson(OrganizationPerson person, OrganizationPersonRequest request) {
        BeanUtils.copyProperties(request, person);
        person.setPrimaryFlag(Boolean.TRUE.equals(request.getPrimaryFlag()));
        person.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_ENABLED);
    }

    private void applyShareholder(OrganizationShareholder shareholder, OrganizationShareholderRequest request) {
        BeanUtils.copyProperties(request, shareholder);
        shareholder.setControllingFlag(Boolean.TRUE.equals(request.getControllingFlag()));
        shareholder.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_ENABLED);
    }

    private void ensureUnique(OrganizationRequest request, Long excludedId) {
        long codeCount = organizationMapper.selectCount(new LambdaQueryWrapper<AmlOrganization>()
                .eq(AmlOrganization::getOrgCode, request.getOrgCode().trim().toUpperCase())
                .ne(excludedId != null, AmlOrganization::getId, excludedId));
        if (codeCount > 0) {
            throw new BusinessException(ResultCode.ORGANIZATION_CODE_EXISTS);
        }
        long creditCount = organizationMapper.selectCount(new LambdaQueryWrapper<AmlOrganization>()
                .eq(AmlOrganization::getUnifiedCreditCode, request.getUnifiedCreditCode().trim().toUpperCase())
                .ne(excludedId != null, AmlOrganization::getId, excludedId));
        if (creditCount > 0) {
            throw new BusinessException(ResultCode.ORGANIZATION_CREDIT_CODE_EXISTS);
        }
    }

    private void validateParent(Long organizationId, Long parentId, String orgType) {
        if ("HEAD_OFFICE".equals(orgType)) {
            if (parentId != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "总机构不能设置上级机构");
            }
            return;
        }
        if (parentId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分支机构或网点必须设置上级机构");
        }
        if (Objects.equals(organizationId, parentId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构不能将自身设为上级机构");
        }
        AmlOrganization parent = loadAccessibleOrganization(parentId);
        if ("OUTLET".equals(parent.getOrgType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "营业网点不能作为上级机构");
        }
        if (organizationId != null && descendantIds(organizationId).contains(parentId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上级机构不能选择当前机构的下级机构");
        }
    }

    private void validateRegistrationCompleteness(AmlOrganization organization) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(organization.getLegalRepresentative())) missing.add("法定代表人/负责人");
        if (!StringUtils.hasText(organization.getRegisteredAddress())) missing.add("注册地址");
        if (!StringUtils.hasText(organization.getBusinessAddress())) missing.add("经营地址");
        long officers = personMapper.selectCount(new LambdaQueryWrapper<OrganizationPerson>()
                .eq(OrganizationPerson::getOrganizationId, organization.getId())
                .eq(OrganizationPerson::getPersonType, "AML_OFFICER")
                .eq(OrganizationPerson::getStatus, STATUS_ENABLED));
        if (officers == 0) missing.add("反洗钱工作负责人或专员");
        long contacts = personMapper.selectCount(new LambdaQueryWrapper<OrganizationPerson>()
                .eq(OrganizationPerson::getOrganizationId, organization.getId())
                .eq(OrganizationPerson::getPersonType, "CONTACT")
                .eq(OrganizationPerson::getStatus, STATUS_ENABLED));
        if (contacts == 0) missing.add("机构联络人");
        if ("HEAD_OFFICE".equals(organization.getOrgType())) {
            long shareholders = shareholderMapper.selectCount(new LambdaQueryWrapper<OrganizationShareholder>()
                    .eq(OrganizationShareholder::getOrganizationId, organization.getId())
                    .eq(OrganizationShareholder::getStatus, STATUS_ENABLED));
            if (shareholders == 0) missing.add("股东信息");
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ResultCode.ORGANIZATION_REGISTRATION_INCOMPLETE,
                    "请补充：" + String.join("、", missing));
        }
    }

    private String buildSnapshot(AmlOrganization organization) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("organization", organization);
        snapshot.put("persons", personMapper.selectList(new LambdaQueryWrapper<OrganizationPerson>()
                .eq(OrganizationPerson::getOrganizationId, organization.getId())));
        snapshot.put("shareholders", shareholderMapper.selectList(new LambdaQueryWrapper<OrganizationShareholder>()
                .eq(OrganizationShareholder::getOrganizationId, organization.getId())));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "机构登记快照生成失败");
        }
    }

    private void ensureRegistrationEditable(AmlOrganization organization) {
        if (REG_PENDING.equals(organization.getRegistrationStatus())) {
            throw new BusinessException(ResultCode.ORGANIZATION_REGISTRATION_STATUS_ERROR, "待审核期间不可修改机构资料");
        }
    }

    private void markChangedAfterApproval(AmlOrganization organization) {
        if (REG_APPROVED.equals(organization.getRegistrationStatus())) {
            organization.setRegistrationStatus(REG_DRAFT);
            organizationMapper.updateById(organization);
        }
    }

    private void clearExistingPrimary(Long organizationId, String personType, Boolean primaryFlag, Long excludedId) {
        if (!Boolean.TRUE.equals(primaryFlag)) return;
        personMapper.update(null, new LambdaUpdateWrapper<OrganizationPerson>()
                .eq(OrganizationPerson::getOrganizationId, organizationId)
                .eq(OrganizationPerson::getPersonType, personType)
                .ne(excludedId != null, OrganizationPerson::getId, excludedId)
                .set(OrganizationPerson::getPrimaryFlag, false));
    }

    private void ensureOwnershipTotal(Long organizationId, BigDecimal newPercentage, String requestedStatus,
                                      Long excludedId) {
        if ("DISABLED".equals(requestedStatus)) {
            return;
        }
        BigDecimal current = shareholderMapper.selectList(new LambdaQueryWrapper<OrganizationShareholder>()
                        .eq(OrganizationShareholder::getOrganizationId, organizationId)
                        .eq(OrganizationShareholder::getStatus, STATUS_ENABLED)
                        .ne(excludedId != null, OrganizationShareholder::getId, excludedId))
                .stream().map(OrganizationShareholder::getOwnershipPercentage)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (current.add(newPercentage).compareTo(new BigDecimal("100.0000")) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "启用股东的持股比例合计不能超过100%");
        }
    }

    private void recordLog(OrganizationRegistration registration, String action, String fromStatus,
                           String toStatus, String opinion) {
        OrganizationReviewLog reviewLog = new OrganizationReviewLog();
        reviewLog.setRegistrationId(registration.getId());
        reviewLog.setActionType(action);
        reviewLog.setFromStatus(fromStatus);
        reviewLog.setToStatus(toStatus);
        reviewLog.setOpinion(opinion);
        reviewLog.setOperator(currentOperator());
        reviewLog.setOperatedAt(LocalDateTime.now());
        reviewLogMapper.insert(reviewLog);
    }

    private AmlOrganization loadAccessibleOrganization(Long id) {
        AmlOrganization organization = organizationMapper.selectById(id);
        if (organization == null) {
            throw new BusinessException(ResultCode.ORGANIZATION_NOT_FOUND);
        }
        List<Long> scope = accessibleOrganizationIds();
        if (scope != null && !scope.contains(id)) {
            throw new BusinessException(ResultCode.ORGANIZATION_SCOPE_FORBIDDEN);
        }
        return organization;
    }

    private OrganizationRegistration loadRegistration(Long id) {
        OrganizationRegistration registration = registrationMapper.selectById(id);
        if (registration == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构登记申请不存在");
        }
        return registration;
    }

    /**
     * 返回 null 表示管理员或内部任务可访问全部；空列表表示登录用户尚未绑定机构。
     */
    private List<Long> accessibleOrganizationIds() {
        if (SecurityUtils.getCurrentUser() == null || SecurityUtils.hasRole("ADMIN")) {
            return null;
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        SysUser user = currentUserId == null ? null : userMapper.selectById(currentUserId);
        if (user == null || user.getOrganizationId() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(descendantIds(user.getOrganizationId()));
    }

    private List<AmlOrganization> accessibleOrganizations() {
        List<Long> ids = accessibleOrganizationIds();
        if (ids != null && ids.isEmpty()) return Collections.emptyList();
        LambdaQueryWrapper<AmlOrganization> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ids != null, AmlOrganization::getId, ids == null ? List.of(-1L) : ids);
        return organizationMapper.selectList(wrapper);
    }

    private Set<Long> descendantIds(Long rootId) {
        List<AmlOrganization> all = organizationMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, List<Long>> children = new HashMap<>();
        for (AmlOrganization organization : all) {
            if (organization.getParentId() != null) {
                children.computeIfAbsent(organization.getParentId(), key -> new ArrayList<>()).add(organization.getId());
            }
        }
        Set<Long> result = new LinkedHashSet<>();
        collectDescendants(rootId, children, result);
        return result;
    }

    private void collectDescendants(Long current, Map<Long, List<Long>> children, Set<Long> result) {
        if (!result.add(current)) return;
        for (Long child : children.getOrDefault(current, Collections.emptyList())) {
            collectDescendants(child, children, result);
        }
    }

    private long countOrgType(List<AmlOrganization> organizations, String type) {
        return organizations.stream().filter(org -> type.equals(org.getOrgType())).count();
    }

    private long countRegistrationStatus(List<AmlOrganization> organizations, String status) {
        return organizations.stream().filter(org -> status.equals(org.getRegistrationStatus())).count();
    }

    private String generateRegistrationNo() {
        return "ORGREG-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String currentOperator() {
        return Objects.requireNonNullElse(SecurityUtils.getCurrentUsername(), "system");
    }
}
