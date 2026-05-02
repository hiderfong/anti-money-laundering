package com.insurance.aml.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置类
 *
 * 功能说明：
 * 1. 配置跨域访问策略（CORS）- 开发环境允许所有来源，生产环境限制来源
 * 2. 配置HTTP消息转换器 - 使用自定义Jackson ObjectMapper
 * 3. 注册请求拦截器 - 预留日志、认证等拦截器接口
 *
 * @author AML Team
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 当前激活的环境（dev/prod） */
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 配置跨域资源共享（CORS）
     *
     * 开发环境：允许所有来源、所有方法、所有头部
     * 生产环境：仅允许指定域名，限制HTTP方法
     *
     * @param registry CORS注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("配置 CORS 策略，当前环境: {}", activeProfile);

        if ("prod".equals(activeProfile)) {
            // 生产环境：严格限制CORS来源
            log.info("生产环境 CORS：仅允许指定域名访问");
            registry.addMapping("/api/**")
                    .allowedOrigins(
                            "https://aml.insurance.com",
                            "https://admin.aml.insurance.com"
                    )
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        } else {
            // 开发环境：允许所有来源
            log.info("开发环境 CORS：允许所有来源访问");
            registry.addMapping("/api/**")
                    .allowedOrigins("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                    .allowedHeaders("*")
                    .maxAge(3600);
        }
    }

    /**
     * 配置HTTP消息转换器
     * 使用自定义的ObjectMapper替代默认配置
     *
     * @param converters 消息转换器列表
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("配置 HTTP 消息转换器...");
        // 使用JacksonConfig中自定义的ObjectMapper
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        converters.add(0, converter);
    }

    /**
     * 注册拦截器
     *
     * 可在此处添加：
     * - 请求日志拦截器（记录请求耗时）
     * - 接口签名验证拦截器
     * - 操作审计拦截器
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("注册 Web 拦截器...");
        // 请求日志拦截器（待实现）
        // registry.addInterceptor(new RequestLoggingInterceptor())
        //         .addPathPatterns("/api/**")
        //         .excludePathPatterns("/api/health", "/api/actuator/**");

        // API签名验证拦截器（待实现）
        // registry.addInterceptor(new ApiSignatureInterceptor())
        //         .addPathPatterns("/api/**")
        //         .excludePathPatterns("/api/public/**");
    }
}
