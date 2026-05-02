package com.insurance.aml.module.alert.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预警处理请求
 */
@Data
public class AlertProcessRequest {

    /** 预警ID */
    @NotNull(message = "预警ID不能为空")
    private Long alertId;

    /**
     * 处理结果
     * CONFIRMED_SUSPICIOUS - 确认可疑
     * EXCLUDED - 排除
     * ESCALATED - 升级
     */
    @NotBlank(message = "处理结果不能为空")
    private String processResult;

    /** 处理备注 */
    private String processRemark;
}
