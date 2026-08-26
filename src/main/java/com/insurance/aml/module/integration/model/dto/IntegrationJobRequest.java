package com.insurance.aml.module.integration.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 集成任务维护请求。
 */
@Data
public class IntegrationJobRequest {
    @NotBlank(message = "任务编码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_-]{2,63}", message = "任务编码须为3至64位大写字母、数字、下划线或短横线")
    private String jobCode;

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称不能超过128个字符")
    private String jobName;

    @NotNull(message = "连接器不能为空")
    private Long connectorId;

    @NotBlank(message = "业务对象不能为空")
    private String businessObject;

    @NotBlank(message = "数据方向不能为空")
    private String direction;

    @NotBlank(message = "Cron表达式不能为空")
    private String cronExpression;

    @Min(value = 1, message = "批量大小不能小于1")
    @Max(value = 100000, message = "批量大小不能超过100000")
    private Integer batchSize;

    @Min(value = 0, message = "最大重试次数不能小于0")
    @Max(value = 10, message = "最大重试次数不能超过10")
    private Integer maxRetries;

    private Boolean enabled;

    @Size(max = 512, message = "说明不能超过512个字符")
    private String description;
}
