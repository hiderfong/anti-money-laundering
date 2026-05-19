package com.insurance.aml.module.ai.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.ai.model.entity.AiRiskScoreRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI风险评分记录 Mapper。
 */
@Mapper
public interface AiRiskScoreRecordMapper extends BaseMapperX<AiRiskScoreRecord> {
}
