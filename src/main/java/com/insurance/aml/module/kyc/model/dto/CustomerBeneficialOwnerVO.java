package com.insurance.aml.module.kyc.model.dto;

import com.insurance.aml.common.annotation.MaskField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户受益所有人视图对象
 */
@Data
@Schema(description = "客户受益所有人视图对象")
public class CustomerBeneficialOwnerVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "客户ID")
    private Long customerId;

    @MaskField(MaskField.MaskType.NAME)
    @Schema(description = "受益所有人姓名")
    private String ownerName;

    @Schema(description = "证件类型")
    private String ownerIdType;

    @MaskField(MaskField.MaskType.ID_NUMBER)
    @Schema(description = "证件号码")
    private String ownerIdNumber;

    @Schema(description = "国籍")
    private String nationality;

    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Schema(description = "持股/控制比例")
    private BigDecimal ownershipPercentage;

    @Schema(description = "控制类型")
    private String controlType;

    @Schema(description = "控制关系描述")
    private String controlDescription;

    @Schema(description = "与客户关系")
    private String relationship;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
