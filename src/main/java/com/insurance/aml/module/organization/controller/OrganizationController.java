package com.insurance.aml.module.organization.controller;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
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
import com.insurance.aml.module.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 反洗钱机构档案及登记审批接口。
 */
@RestController
@RequestMapping("/organizations")
@Tag(name = "机构治理")
@RequiredArgsConstructor
public class OrganizationController {
    private final OrganizationService organizationService;

    @GetMapping("/overview")
    @Operation(summary = "机构治理概览")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:view')")
    public Result<OrganizationOverviewVO> overview() {
        return Result.success(organizationService.overview());
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询机构")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:view')")
    public Result<PageResult<AmlOrganization>> page(@Valid OrganizationQueryRequest request) {
        return Result.success(organizationService.pageOrganizations(request));
    }

    @GetMapping("/tree")
    @Operation(summary = "查询机构树")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:view') or hasAuthority('system:user')")
    public Result<List<OrganizationTreeVO>> tree() {
        return Result.success(organizationService.organizationTree());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询机构完整档案")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:view')")
    public Result<OrganizationDetailVO> detail(@PathVariable Long id) {
        return Result.success(organizationService.getDetail(id));
    }

    @PostMapping
    @Operation(summary = "创建机构档案")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:manage')")
    @AuditLog(module = "机构治理", operationType = "CREATE", description = "创建机构档案")
    public Result<AmlOrganization> create(@Valid @RequestBody OrganizationRequest request) {
        return Result.success(organizationService.createOrganization(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新机构档案")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:manage')")
    @AuditLog(module = "机构治理", operationType = "UPDATE", description = "更新机构档案")
    public Result<AmlOrganization> update(@PathVariable Long id, @Valid @RequestBody OrganizationRequest request) {
        return Result.success(organizationService.updateOrganization(id, request));
    }

    @PostMapping("/{id}/persons")
    @Operation(summary = "新增机构治理人员")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:manage')")
    @AuditLog(module = "机构治理", operationType = "CREATE", description = "新增机构治理人员")
    public Result<OrganizationPerson> createPerson(@PathVariable Long id,
                                                    @Valid @RequestBody OrganizationPersonRequest request) {
        return Result.success(organizationService.createPerson(id, request));
    }

    @PutMapping("/persons/{personId}")
    @Operation(summary = "更新机构治理人员")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:manage')")
    @AuditLog(module = "机构治理", operationType = "UPDATE", description = "更新机构治理人员")
    public Result<OrganizationPerson> updatePerson(@PathVariable Long personId,
                                                    @Valid @RequestBody OrganizationPersonRequest request) {
        return Result.success(organizationService.updatePerson(personId, request));
    }

    @PostMapping("/{id}/shareholders")
    @Operation(summary = "新增机构股东")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:manage')")
    @AuditLog(module = "机构治理", operationType = "CREATE", description = "新增机构股东")
    public Result<OrganizationShareholder> createShareholder(@PathVariable Long id,
            @Valid @RequestBody OrganizationShareholderRequest request) {
        return Result.success(organizationService.createShareholder(id, request));
    }

    @PutMapping("/shareholders/{shareholderId}")
    @Operation(summary = "更新机构股东")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:manage')")
    @AuditLog(module = "机构治理", operationType = "UPDATE", description = "更新机构股东")
    public Result<OrganizationShareholder> updateShareholder(@PathVariable Long shareholderId,
            @Valid @RequestBody OrganizationShareholderRequest request) {
        return Result.success(organizationService.updateShareholder(shareholderId, request));
    }

    @PostMapping("/{id}/registrations")
    @Operation(summary = "创建机构登记申请")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:manage')")
    @AuditLog(module = "机构治理", operationType = "CREATE", description = "创建机构登记申请")
    public Result<OrganizationRegistration> createRegistration(@PathVariable Long id,
            @Valid @RequestBody OrganizationRegistrationRequest request) {
        return Result.success(organizationService.createRegistration(id, request));
    }

    @PostMapping("/registrations/{registrationId}/submit")
    @Operation(summary = "提交机构登记申请")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:manage')")
    @AuditLog(module = "机构治理", operationType = "SUBMIT", description = "提交机构登记申请")
    public Result<OrganizationRegistration> submitRegistration(@PathVariable Long registrationId) {
        return Result.success(organizationService.submitRegistration(registrationId));
    }

    @PostMapping("/registrations/{registrationId}/review")
    @Operation(summary = "审核机构登记申请")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('organization:review')")
    @AuditLog(module = "机构治理", operationType = "REVIEW", description = "审核机构登记申请")
    public Result<OrganizationRegistration> reviewRegistration(@PathVariable Long registrationId,
            @Valid @RequestBody OrganizationReviewRequest request) {
        return Result.success(organizationService.reviewRegistration(registrationId, request));
    }
}
