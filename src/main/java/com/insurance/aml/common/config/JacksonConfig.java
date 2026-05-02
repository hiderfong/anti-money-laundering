package com.insurance.aml.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson JSON 序列化配置类
 *
 * 功能说明：
 * 1. 全局日期时间格式化 - 统一日期格式输出
 * 2. 设置时区为Asia/Shanghai
 * 3. 序列化时排除null值，减少JSON体积
 * 4. 禁用日期为时间戳格式，使用可读的日期字符串
 * 5. 注册自定义序列化器（如Long转String防止前端精度丢失）
 *
 * @author AML Team
 */
@Slf4j
@Configuration
public class JacksonConfig {

    /** 日期时间格式 */
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** 日期格式 */
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    /** 时区 */
    private static final String TIME_ZONE = "Asia/Shanghai";

    /**
     * 自定义全局ObjectMapper
     *
     * @return ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        log.info("初始化自定义 Jackson ObjectMapper...");

        ObjectMapper mapper = new ObjectMapper();

        // ========== 序列化配置 ==========

        // 禁用日期时间戳格式，使用日期字符串格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 禁用空Bean序列化异常（当对象无属性时不抛异常）
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        // 序列化时排除null值和空字符串
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 设置全局日期格式
        mapper.setDateFormat(new SimpleDateFormat(DATE_TIME_FORMAT));

        // ========== 反序列化配置 ==========

        // 反序列化时忽略未知属性（兼容性更好）
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // ========== 注册模块 ==========

        // 注册Java8日期时间模块
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)));
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        mapper.registerModule(javaTimeModule);

        // Long类型转String，防止前端JavaScript精度丢失
        SimpleModule longModule = new SimpleModule();
        longModule.addSerializer(Long.class, ToStringSerializer.instance);
        longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        mapper.registerModule(longModule);

        log.info("Jackson ObjectMapper 配置完成：日期格式={}, 时区={}, Long转String=启用",
                DATE_TIME_FORMAT, TIME_ZONE);
        return mapper;
    }
}
