package com.insurance.aml.common.enums;

/**
 * 预警状态枚举
 */
public enum AlertStatusEnum {

    NEW("NEW", "新建", "预警刚生成，尚未分配处理人"),
    ASSIGNED("ASSIGNED", "已分配", "预警已分配给处理人"),
    PROCESSING("PROCESSING", "处理中", "预警正在被调查处理"),
    CONFIRMED("CONFIRMED", "已确认", "预警已确认为有效可疑交易"),
    EXCLUDED("EXCLUDED", "已排除", "预警经排查已排除");

    private final String code;
    private final String label;
    private final String description;

    AlertStatusEnum(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code查找枚举，未找到返回null
     */
    public static AlertStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AlertStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 根据code查找枚举，未找到抛异常
     */
    public static AlertStatusEnum of(String code) {
        AlertStatusEnum e = fromCode(code);
        if (e == null) {
            throw new IllegalArgumentException("Unknown AlertStatusEnum code: " + code);
        }
        return e;
    }
}
