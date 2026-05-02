package com.insurance.aml.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j（Swagger）API 文档配置
 * 配置 OpenAPI 基本信息和接口分组
 */
@Configuration
public class Knife4jConfig {

    /**
     * OpenAPI 基本信息配置
     * 设置 API 文档标题、描述、版本等
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("反洗钱管理系统 API")
                        .description("反洗钱（AML）管理系统后端接口文档，" +
                                "提供客户管理、交易监测、可疑交易报告、风险评估等核心功能接口。")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AML开发团队")
                                .email("aml-dev@insurance.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

    /**
     * 客户管理模块接口分组
     */
    @Bean
    public GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder()
                .group("客户管理")
                .pathsToMatch("/api/customer/**")
                .displayName("客户信息管理")
                .build();
    }

    /**
     * 交易监测模块接口分组
     */
    @Bean
    public GroupedOpenApi transactionApi() {
        return GroupedOpenApi.builder()
                .group("交易监测")
                .pathsToMatch("/api/transaction/**")
                .displayName("交易监测与预警")
                .build();
    }

    /**
     * 可疑交易报告模块接口分组
     */
    @Bean
    public GroupedOpenApi strApi() {
        return GroupedOpenApi.builder()
                .group("可疑交易报告")
                .pathsToMatch("/api/str/**")
                .displayName("可疑交易报告（STR）")
                .build();
    }

    /**
     * 风险评估模块接口分组
     */
    @Bean
    public GroupedOpenApi riskApi() {
        return GroupedOpenApi.builder()
                .group("风险评估")
                .pathsToMatch("/api/risk/**")
                .displayName("客户风险评估")
                .build();
    }

    /**
     * 系统管理模块接口分组
     */
    @Bean
    public GroupedOpenApi systemApi() {
        return GroupedOpenApi.builder()
                .group("系统管理")
                .pathsToMatch("/api/system/**", "/api/auth/**")
                .displayName("系统管理与认证")
                .build();
    }
}
