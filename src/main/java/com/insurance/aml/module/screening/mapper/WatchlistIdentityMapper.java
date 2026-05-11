package com.insurance.aml.module.screening.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.screening.model.entity.WatchlistIdentity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 制裁名单证件信息 Mapper
 */
@Mapper
public interface WatchlistIdentityMapper extends BaseMapperX<WatchlistIdentity> {
}
