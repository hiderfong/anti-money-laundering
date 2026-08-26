package com.insurance.aml.module.integration.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部系统连接器档案，只保存凭据引用，不保存密钥明文。
 */
@Data
@TableName("t_integration_connector")
public class IntegrationConnector extends BaseEntity {
    private String connectorCode;
    private String connectorName;
    private String businessType;
    private String transportType;
    private String endpointUrl;
    private String authType;
    private String credentialRef;
    private String status;
    private String healthStatus;
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private Integer retryIntervalSeconds;
    private LocalDateTime lastHealthCheckTime;
    private LocalDateTime lastSuccessTime;
    private LocalDateTime lastFailureTime;
    private String lastErrorMessage;
    private String description;
}
