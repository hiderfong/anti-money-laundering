package com.insurance.aml.module.screening.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.screening.model.entity.Whitelist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 白名单 Mapper
 */
@Mapper
public interface WhitelistMapper extends BaseMapperX<Whitelist> {
}
