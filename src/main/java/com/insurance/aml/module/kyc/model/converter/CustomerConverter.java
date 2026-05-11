package com.insurance.aml.module.kyc.model.converter;

import com.insurance.aml.module.kyc.model.dto.CustomerBeneficialOwnerVO;
import com.insurance.aml.module.kyc.model.dto.CustomerCreateRequest;
import com.insurance.aml.module.kyc.model.dto.CustomerVO;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.kyc.model.entity.CustomerBeneficialOwner;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Customer 对象转换器
 * 使用 MapStruct 替代 BeanUtils.copyProperties，编译期生成代码，类型安全且性能更高
 */
@Mapper(componentModel = "spring")
public interface CustomerConverter {

    /**
     * Customer 实体 -> CustomerVO
     * 所有同名同类型字段自动映射，包括 BaseEntity 中的 id/createdBy/createdTime/updatedBy/updatedTime
     */
    CustomerVO toVO(Customer customer);

    /**
     * CustomerCreateRequest -> Customer 实体
     * 忽略请求中不存在的字段（id、审计字段、风险字段等由框架自动填充）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerNo", ignore = true)
    @Mapping(target = "riskLevel", ignore = true)
    @Mapping(target = "riskScore", ignore = true)
    @Mapping(target = "riskUpdateTime", ignore = true)
    @Mapping(target = "isPep", ignore = true)
    @Mapping(target = "pepType", ignore = true)
    @Mapping(target = "isSanctioned", ignore = true)
    @Mapping(target = "kycStatus", ignore = true)
    @Mapping(target = "kycLastReviewTime", ignore = true)
    @Mapping(target = "kycNextReviewTime", ignore = true)
    @Mapping(target = "remark", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdTime", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedTime", ignore = true)
    Customer toEntity(CustomerCreateRequest request);

    /**
     * Customer 实体列表 -> CustomerVO 列表
     */
    List<CustomerVO> toVOList(List<Customer> customers);

    /**
     * CustomerBeneficialOwner 实体 -> CustomerBeneficialOwnerVO
     */
    @Mapping(target = "createdTime", source = "createdTime")
    CustomerBeneficialOwnerVO toBeneficialOwnerVO(CustomerBeneficialOwner owner);

    /**
     * CustomerBeneficialOwner 实体列表 -> CustomerBeneficialOwnerVO 列表
     */
    List<CustomerBeneficialOwnerVO> toBeneficialOwnerVOList(List<CustomerBeneficialOwner> owners);
}
