package com.insurance.aml.common.aspect;

import com.insurance.aml.common.annotation.MaskField;
import com.insurance.aml.common.util.MaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 字段脱敏切面
 * 对Controller返回值中标注了@MaskField注解的字段自动进行脱敏处理
 */
@Slf4j
@Aspect
@Component
public class MaskAspect {

    /**
     * 环绕通知：对Controller返回值进行脱敏处理
     */
    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (result != null) {
            try {
                applyMask(result);
            } catch (Exception e) {
                log.warn("脱敏处理失败，返回原始数据: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 对对象进行脱敏处理
     */
    private void applyMask(Object obj) throws IllegalAccessException {
        applyMask(obj, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * 递归处理 Result、PageResult、集合、数组、Map 等包装结构中的敏感字段。
     */
    private void applyMask(Object obj, Set<Object> visited) throws IllegalAccessException {
        if (obj == null || isSimpleValueType(obj.getClass()) || visited.contains(obj)) {
            return;
        }

        visited.add(obj);

        if (obj instanceof Collection<?> collection) {
            for (Object item : collection) {
                applyMask(item, visited);
            }
            return;
        }

        if (obj instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                applyMask(item, visited);
            }
            return;
        }

        Class<?> clazz = obj.getClass();

        if (clazz.isArray()) {
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                applyMask(Array.get(obj, i), visited);
            }
            return;
        }

        applyMaskToObject(obj);

        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                applyMask(field.get(obj), visited);
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 对单个对象的@MaskField注解字段进行脱敏
     */
    private void applyMaskToObject(Object obj) throws IllegalAccessException {
        if (obj == null) {
            return;
        }

        Class<?> clazz = obj.getClass();

        // 遍历类层次结构中的所有字段
        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                MaskField maskField = field.getAnnotation(MaskField.class);
                if (maskField == null) {
                    continue;
                }

                field.setAccessible(true);
                Object value = field.get(obj);
                if (!(value instanceof String strValue) || strValue.isEmpty()) {
                    continue;
                }

                // 根据脱敏类型应用对应的脱敏方法
                String maskedValue = switch (maskField.value()) {
                    case NAME -> MaskUtils.maskName(strValue);
                    case ID_NUMBER -> MaskUtils.maskIdNumber(strValue);
                    case PHONE -> MaskUtils.maskPhone(strValue);
                    case EMAIL -> MaskUtils.maskEmail(strValue);
                    case BANK_ACCOUNT -> MaskUtils.maskBankAccount(strValue);
                };

                field.set(obj, maskedValue);
            }
            clazz = clazz.getSuperclass();
        }
    }

    private boolean isSimpleValueType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz.isEnum()
                || CharSequence.class.isAssignableFrom(clazz)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class == clazz
                || Character.class == clazz
                || BigDecimal.class == clazz
                || BigInteger.class == clazz
                || Temporal.class.isAssignableFrom(clazz)
                || clazz.getPackageName().startsWith("java.time");
    }
}
