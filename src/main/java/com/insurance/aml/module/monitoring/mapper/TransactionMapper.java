package com.insurance.aml.module.monitoring.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易Mapper接口
 */
@Mapper
public interface TransactionMapper extends BaseMapperX<Transaction> {
}
