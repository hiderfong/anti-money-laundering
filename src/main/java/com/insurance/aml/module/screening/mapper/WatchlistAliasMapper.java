package com.insurance.aml.module.screening.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.screening.model.entity.WatchlistAlias;
import org.apache.ibatis.annotations.Mapper;

/**
 * 制裁名单别名 Mapper
 */
@Mapper
public interface WatchlistAliasMapper extends BaseMapperX<WatchlistAlias> {
}
