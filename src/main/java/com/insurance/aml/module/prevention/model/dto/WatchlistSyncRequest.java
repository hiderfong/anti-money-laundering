package com.insurance.aml.module.prevention.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 名单同步请求。
 */
@Data
@Schema(description = "名单同步请求")
public class WatchlistSyncRequest {

    @Schema(description = "名单源ID")
    private Long sourceId;

    @Schema(description = "更新模式：MANUAL/SCHEDULED/API", example = "MANUAL")
    @NotBlank(message = "更新模式不能为空")
    private String updateMode;
}
