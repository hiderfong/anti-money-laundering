package com.insurance.aml.module.monitoring.model.converter;

import com.insurance.aml.module.monitoring.model.dto.TransactionVO;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Transaction 对象转换器
 * 使用 MapStruct 替代 BeanUtils.copyProperties
 *
 * Transaction 实体未继承 BaseEntity，字段与 TransactionVO 一一对应
 */
@Mapper(componentModel = "spring")
public interface TransactionConverter {

    /**
     * Transaction 实体 -> TransactionVO
     * 所有同名同类型字段自动映射
     */
    TransactionVO toVO(Transaction transaction);

    /**
     * Transaction 实体列表 -> TransactionVO 列表
     */
    List<TransactionVO> toVOList(List<Transaction> transactions);

    /**
     * TransactionVO -> Transaction 实体（反向转换）
     */
    Transaction toEntity(TransactionVO transactionVO);
}
