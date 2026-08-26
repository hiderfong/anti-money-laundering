package com.insurance.aml.module.organization.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 机构登记审核请求。
 */
@Data
public class OrganizationReviewRequest {
    @NotNull(message = "审核结论不能为空")
    private Boolean approved;

    @Size(max = 1024, message = "审核意见不能超过1024个字符")
    private String opinion;
}
