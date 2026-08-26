package com.insurance.aml.module.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insurance.aml.module.integration.model.entity.IntegrationRun;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IntegrationRunMapper extends BaseMapper<IntegrationRun> {
}
