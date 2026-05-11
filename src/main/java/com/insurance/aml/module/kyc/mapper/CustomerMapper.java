package com.insurance.aml.module.kyc.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.kyc.model.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户信息Mapper
 */
@Mapper
public interface CustomerMapper extends BaseMapperX<Customer> {
}
