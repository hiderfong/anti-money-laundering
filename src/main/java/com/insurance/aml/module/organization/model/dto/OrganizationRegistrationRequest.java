package com.insurance.aml.module.organization.model.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

/**
 * 新建登记申请请求。提交前必须明确接受真实性和合规承诺。
 */
@Data
public class OrganizationRegistrationRequest {
    @AssertTrue(message = "请确认机构信息真实、完整并接受合规承诺")
    private Boolean commitmentAccepted;
}
