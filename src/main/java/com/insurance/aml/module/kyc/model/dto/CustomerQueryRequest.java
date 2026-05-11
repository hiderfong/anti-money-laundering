package com.insurance.aml.module.kyc.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "客户分页查询请求")
public class CustomerQueryRequest extends PageQuery {

    @Schema(description = "客户名称")
    private String name;

    @Schema(description = "客户类型")
    private String customerType;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "KYC状态")
    private String kycStatus;

    @Schema(description = "证件号码")
    private String idNumber;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "是否PEP")
    private Boolean pepFlag;

    @Schema(description = "是否被制裁")
    private Boolean sanctionedFlag;
}
