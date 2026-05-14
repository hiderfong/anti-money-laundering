package com.insurance.aml.module.assessment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 整改验证请求。
 */
@Data
@Schema(description = "整改验证请求")
public class RectificationVerifyRequest {

    @Schema(description = "验证结果：PASSED/RETURNED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "验证结果不能为空")
    private String verificationStatus;

    @Schema(description = "验证意见")
    private String verifyResult;
}
