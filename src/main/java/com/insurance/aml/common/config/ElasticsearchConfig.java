package com.insurance.aml.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

/**
 * Elasticsearch 配置类
 *
 * 功能说明：
 * 1. 配置Elasticsearch客户端连接参数
 * 2. 基于Spring Data Elasticsearch自动配置，自定义客户端行为
 * 3. 用于存储和检索反洗钱交易记录、告警历史等大数据量文档
 *
 * 说明：Spring Boot 3.4 使用 ElasticsearchConfiguration 基类来配置客户端，
 *       底层使用新的 Elasticsearch Java Client（替代已废弃的RestHighLevelClient）
 *
 * @author AML Team
 */
@Slf4j
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    /**
     * 配置Elasticsearch客户端连接
     * 可通过 application.yml 中的配置覆盖以下默认值
     *
     * @return 客户端配置
     */
    @Override
    public ClientConfiguration clientConfiguration() {
        log.info("初始化 Elasticsearch 客户端配置...");
        ClientConfiguration config = ClientConfiguration.builder()
                // Elasticsearch 连接地址（可通过配置文件覆盖）
                .connectedTo("localhost:9200")
                // 启用HTTPS（生产环境启用）
                // .usingSsl()
                // 连接超时设置
                .withConnectTimeout(5000)
                .withSocketTimeout(60000)
                // 请求头部配置（如需认证）
                // .withDefaultHeaders(HttpHeaders.EMPTY)
                // 启用嗅探模式（自动发现集群节点）
                // .withClientConfigurer(ElasticsearchClientConfiguration.RestClientBuilderCallback.from(
                //         builder -> builder.setHttpClientConfigCallback(httpAsyncClientBuilder ->
                //                 httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider))))
                .build();
        log.info("Elasticsearch 客户端配置完成：连接地址=localhost:9200");
        return config;
    }
}
