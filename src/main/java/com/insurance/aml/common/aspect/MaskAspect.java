package com.insurance.aml.common.aspect;

import com.insurance.aml.common.annotation.MaskField;
import com.insurance.aml.common.util.MaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;

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
        if (obj == null) {
            return;
        }

        Class<?> clazz = obj.getClass();

        // 处理包装类（如ResponseEntity、Result等）中的data字段
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(obj);
            if (value != null) {
                // 如果是集合类型，遍历每个元素进行脱敏
                if (value instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        applyMaskToObject(item);
                    }
                } else {
                    applyMaskToObject(value);
                }
            }
        }

        // 如果返回值本身就是需要脱敏的对象
        applyMaskToObject(obj);
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
}
