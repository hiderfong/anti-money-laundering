package com.insurance.aml.module.modelmgmt.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 反洗钱模型创建/更新请求。
 */
@Data
@Schema(description = "反洗钱模型创建/更新请求")
public class ModelCreateRequest {

    @NotBlank(message = "模型编码不能为空")
    private String modelCode;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    @NotBlank(message = "模型类型不能为空")
    private String modelType;

    @NotBlank(message = "业务场景不能为空")
    private String scenario;

    private String algorithmType;
    private String version;
    private String lifecycleStatus;
    private String owner;
    private String governanceLevel;
    private String riskLevel;
    private String trainingDataset;
    private String validationDataset;
    private String iterationPlan;
    private String description;
    private String configJson;
}
