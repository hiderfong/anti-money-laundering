package com.insurance.aml.module.screening.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.screening.model.entity.WatchlistSource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 制裁名单来源 Mapper
 */
@Mapper
public interface WatchlistSourceMapper extends BaseMapperX<WatchlistSource> {
}
