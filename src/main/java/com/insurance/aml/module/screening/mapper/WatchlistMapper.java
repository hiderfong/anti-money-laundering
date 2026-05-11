package com.insurance.aml.module.screening.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.screening.model.entity.Watchlist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 制裁名单 Mapper
 */
@Mapper
public interface WatchlistMapper extends BaseMapperX<Watchlist> {
}
