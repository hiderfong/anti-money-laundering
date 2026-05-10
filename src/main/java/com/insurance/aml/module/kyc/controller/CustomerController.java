package com.insurance.aml.module.kyc.controller;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.kyc.model.dto.*;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.kyc.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 客户管理控制器
 * 提供客户CRUD、360度视图、风险评估等接口
 */
@Slf4j
@RestController
@RequestMapping("/kyc/customers")
@RequiredArgsConstructor
@Tag(name = "客户管理", description = "客户信息管理相关接口")
public class CustomerController {
    private final CustomerService customerService;

    /**
     * 创建客户
     */
    @PostMapping
    @Operation(summary = "创建客户", description = "新建客户信息，校验证件号唯一性")
    @AuditLog(module = "KYC", operationType = "CREATE", description = "创建客户")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('customer:create')")
    public Result<Customer> createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
        log.info("接收到创建客户请求，客户名称：{}", request.getName());
        Customer customer = customerService.createCustomer(request);
        return Result.success(customer);
    }

    /**
     * 更新客户信息
     */
    @PutMapping
    @Operation(summary = "更新客户", description = "更新客户信息，仅更新非空字段")
    @AuditLog(module = "KYC", operationType = "UPDATE", description = "更新客户信息")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('customer:update')")
    public Result<Customer> updateCustomer(@Valid @RequestBody CustomerUpdateRequest request) {
        log.info("接收到更新客户请求，客户ID：{}", request.getId());
        Customer customer = customerService.updateCustomer(request);
        return Result.success(customer);
    }

    /**
     * 获取客户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取客户详情", description = "根据客户ID获取客户详细信息及受益所有人")
    public Result<CustomerVO> getCustomerDetail(
            @Parameter(description = "客户ID", required = true) @PathVariable Long id) {
        log.debug("接收到获取客户详情请求，客户ID：{}", id);
        CustomerVO customerVO = customerService.getCustomerDetail(id);
        return Result.success(customerVO);
    }

    /**
     * 分页查询客户列表
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询客户", description = "根据条件分页查询客户列表")
    public Result<PageResult<CustomerVO>> pageQueryCustomers(CustomerQueryRequest request) {
        log.debug("接收到分页查询客户请求");
        PageResult<CustomerVO> result = customerService.pageQueryCustomers(request);
        return Result.success(result);
    }

    /**
     * 获取客户360度视图
     */
    @GetMapping("/{id}/360")
    @Operation(summary = "客户360度视图", description = "获取客户全方位信息，包括基本信息、受益所有人、验证记录、风险历史")
    public Result<Customer360VO> getCustomer360View(
            @Parameter(description = "客户ID", required = true) @PathVariable Long id) {
        log.debug("接收到客户360度视图请求，客户ID：{}", id);
        Customer360VO result = customerService.getCustomer360View(id);
        return Result.success(result);
    }

    /**
     * 触发客户风险评估
     */
    @PostMapping("/{id}/risk-assessment")
    @Operation(summary = "触发风险评估", description = "手动触发客户风险评估，计算风险评分并更新风险等级")
    @AuditLog(module = "KYC", operationType = "UPDATE", description = "触发客户风险评估")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('customer:update')")
    public Result<Void> triggerRiskAssessment(
            @Parameter(description = "客户ID", required = true) @PathVariable Long id) {
        log.info("接收到触发客户风险评估请求，客户ID：{}", id);
        customerService.assessRiskLevel(id);
        return Result.success();
    }
}
