package com.insurance.aml.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;

/**
 * Elasticsearch 配置类
 *
 * 功能说明：
 * 1. 配置Elasticsearch客户端连接参数
 * 2. 基于Spring Data Elasticsearch自动配置，自定义客户端行为
 * 3. 用于存储和检索反洗钱交易记录、告警历史等大数据量文档
 * 4. 启用Elasticsearch Repository扫描
 *
 * 说明：Spring Boot 3.4 使用 ElasticsearchConfiguration 基类来配置客户端，
 *       底层使用新的 Elasticsearch Java Client（替代已废弃的RestHighLevelClient）
 *
 * @author AML Team
 */
@Slf4j
@Configuration
@Profile("!test & !no-es")
@EnableElasticsearchRepositories(basePackages = "com.insurance.aml.module.system.repository")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUris;

    @Value("${spring.elasticsearch.username:}")
    private String elasticsearchUsername;

    @Value("${spring.elasticsearch.password:}")
    private String elasticsearchPassword;

    /**
     * 配置Elasticsearch客户端连接
     * 可通过 application.yml 中的配置覆盖以下默认值
     *
     * @return 客户端配置
     */
    @Override
    public ClientConfiguration clientConfiguration() {
        String[] endpoints = Arrays.stream(elasticsearchUris.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::toHostPort)
                .toArray(String[]::new);

        boolean useSsl = Arrays.stream(elasticsearchUris.split(","))
                .map(String::trim)
                .anyMatch(uri -> uri.startsWith("https://"));

        log.info("初始化 Elasticsearch 客户端配置，连接地址={}", String.join(",", endpoints));
        ClientConfiguration.MaybeSecureClientConfigurationBuilder endpointBuilder = ClientConfiguration.builder()
                .connectedTo(endpoints);
        ClientConfiguration.TerminalClientConfigurationBuilder builder = useSsl
                ? endpointBuilder.usingSsl()
                : endpointBuilder;

        if (StringUtils.hasText(elasticsearchUsername)) {
            builder = builder.withBasicAuth(elasticsearchUsername, elasticsearchPassword);
        }

        ClientConfiguration config = builder
                .withConnectTimeout(5000)
                .withSocketTimeout(60000)
                .build();

        log.info("Elasticsearch 客户端配置完成");
        return config;
    }

    private String toHostPort(String uri) {
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            URI parsed = URI.create(uri);
            int port = parsed.getPort();
            if (port < 0) {
                port = "https".equals(parsed.getScheme()) ? 443 : 9200;
            }
            return parsed.getHost() + ":" + port;
        }
        return uri.replaceAll("/$", "");
    }
}
