package com.insurance.aml.module.kyc.model.dto;

import com.insurance.aml.module.kyc.model.entity.CustomerRiskRatingLog;
import com.insurance.aml.module.kyc.model.entity.VerificationRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 客户360度视图对象
 * 整合客户基本信息、受益所有人、验证记录、风险评级历史、预警统计
 */
@Data
@Schema(description = "客户360度视图对象")
public class Customer360VO {

    @Schema(description = "客户基本信息")
    private CustomerVO customer;

    @Schema(description = "受益所有人列表")
    private List<CustomerBeneficialOwnerVO> beneficialOwners;

    @Schema(description = "验证历史记录")
    private List<VerificationRecord> verificationHistory;

    @Schema(description = "风险评级变更历史")
    private List<CustomerRiskRatingLog> riskRatingHistory;

    @Schema(description = "关联预警数量")
    private int alertCount;

    @Schema(description = "近期交易记录（预留）")
    private List<Object> recentTransactions;
}
