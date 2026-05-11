package com.insurance.aml.module.kyc.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.kyc.model.entity.VerificationRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 验证记录Mapper
 */
@Mapper
public interface VerificationRecordMapper extends BaseMapperX<VerificationRecord> {
}
