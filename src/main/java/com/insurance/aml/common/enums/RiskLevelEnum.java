package com.insurance.aml.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风险等级枚举
 */
@Getter
@AllArgsConstructor
public enum RiskLevelEnum {

    /** 低风险 */
    LOW("低风险", 1),

    /** 中风险 */
    MEDIUM("中风险", 2),

    /** 高风险 */
    HIGH("高风险", 3),

    /** 极高风险 */
    CRITICAL("极高风险", 4);

    /**
     * 风险等级标签
     */
    private final String label;

    /**
     * 风险等级数值
     */
    private final int level;
}
