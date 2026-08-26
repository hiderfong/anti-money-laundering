package com.insurance.aml.module.integration.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 连接器维护请求。credentialRef 仅允许填写密钥管理系统或环境变量引用名。
 */
@Data
public class IntegrationConnectorRequest {
    @NotBlank(message = "连接器编码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_-]{2,63}", message = "连接器编码须为3至64位大写字母、数字、下划线或短横线")
    private String connectorCode;

    @NotBlank(message = "连接器名称不能为空")
    @Size(max = 128, message = "连接器名称不能超过128个字符")
    private String connectorName;

    @NotBlank(message = "业务类型不能为空")
    private String businessType;

    @NotBlank(message = "传输类型不能为空")
    private String transportType;

    @NotBlank(message = "端点地址不能为空")
    @Size(max = 512, message = "端点地址不能超过512个字符")
    private String endpointUrl;

    @NotBlank(message = "认证类型不能为空")
    private String authType;

    @Pattern(regexp = "^$|[A-Z][A-Z0-9_]{2,127}", message = "凭据引用须为大写环境变量或密钥引用名")
    private String credentialRef;

    private String status;

    @Min(value = 1, message = "超时时间不能小于1秒")
    @Max(value = 300, message = "超时时间不能超过300秒")
    private Integer timeoutSeconds;

    @Min(value = 0, message = "最大重试次数不能小于0")
    @Max(value = 10, message = "最大重试次数不能超过10")
    private Integer maxRetries;

    @Min(value = 0, message = "重试间隔不能小于0秒")
    @Max(value = 3600, message = "重试间隔不能超过3600秒")
    private Integer retryIntervalSeconds;

    @Size(max = 512, message = "说明不能超过512个字符")
    private String description;
}
