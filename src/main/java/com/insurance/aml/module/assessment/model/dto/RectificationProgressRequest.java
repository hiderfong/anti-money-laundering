package com.insurance.aml.module.assessment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 整改进度更新请求。
 */
@Data
@Schema(description = "整改进度更新请求")
public class RectificationProgressRequest {

    @Schema(description = "进度百分比", minimum = "0", maximum = "100")
    @NotNull(message = "进度不能为空")
    @Min(value = 0, message = "进度不能小于0")
    @Max(value = 100, message = "进度不能大于100")
    private Integer progressPercent;

    @Schema(description = "完成证据或进度说明")
    private String completionEvidence;

    @Schema(description = "状态：OPEN/IN_PROGRESS/COMPLETED")
    private String status;
}
