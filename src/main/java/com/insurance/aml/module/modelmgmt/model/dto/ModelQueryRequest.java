package com.insurance.aml.module.modelmgmt.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 反洗钱模型分页查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "反洗钱模型分页查询请求")
public class ModelQueryRequest extends PageQuery {

    private String keyword;
    private String modelType;
    private String scenario;
    private String lifecycleStatus;
    private String riskLevel;
}
