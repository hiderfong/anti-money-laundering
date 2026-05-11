package com.insurance.aml.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.insurance.aml.common.annotation.Sensitive;
import com.insurance.aml.common.util.MaskUtils;

import java.io.IOException;
import java.util.Objects;

/**
 * 敏感数据序列化器
 *
 * 根据 @Sensitive 注解自动对字段进行脱敏处理
 * 与 @MaskField + MaskAspect 方案互补：
 * - @MaskField：AOP 切面方案，适合 Controller 返回值
 * - @Sensitive：Jackson 序列化器方案，适合任意序列化场景
 */
public class SensitiveSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private Sensitive.Type type;
    private int prefixLen;
    private int suffixLen;

    public SensitiveSerializer() {
    }

    public SensitiveSerializer(Sensitive.Type type, int prefixLen, int suffixLen) {
        this.type = type;
        this.prefixLen = prefixLen;
        this.suffixLen = suffixLen;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        String masked = switch (type) {
            case NAME -> MaskUtils.maskName(value);
            case ID_CARD -> MaskUtils.maskIdNumber(value);
            case PHONE -> MaskUtils.maskPhone(value);
            case EMAIL -> MaskUtils.maskEmail(value);
            case BANK_ACCOUNT -> MaskUtils.maskBankAccount(value);
            case ADDRESS -> maskAddress(value);
            case CUSTOM -> maskCustom(value, prefixLen, suffixLen);
        };

        gen.writeString(masked);
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property != null) {
            Sensitive sensitive = property.getAnnotation(Sensitive.class);
            if (Objects.equals(sensitive, null)) {
                sensitive = property.getContextAnnotation(Sensitive.class);
            }
            if (sensitive != null) {
                return new SensitiveSerializer(sensitive.value(), sensitive.prefixLen(), sensitive.suffixLen());
            }
        }
        return this;
    }

    private String maskAddress(String address) {
        if (address == null || address.isEmpty()) return address;
        if (address.length() <= 6) return address;
        return address.substring(0, 6) + "****";
    }

    private String maskCustom(String value, int prefix, int suffix) {
        if (value == null || value.isEmpty()) return value;
        int totalKeep = prefix + suffix;
        if (value.length() <= totalKeep) return value;
        String prefixStr = value.substring(0, prefix);
        String suffixStr = value.substring(value.length() - suffix);
        return prefixStr + "*".repeat(value.length() - totalKeep) + suffixStr;
    }
}
