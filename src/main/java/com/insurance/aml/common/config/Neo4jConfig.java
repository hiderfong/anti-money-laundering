package com.insurance.aml.common.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4j 图数据库配置
 * 用于交易网络关联分析
 * 通过 aml.neo4j.enabled=true 启用
 * 默认禁用，需要显式开启
 */
@Configuration
@ConditionalOnProperty(name = "aml.neo4j.enabled", havingValue = "true", matchIfMissing = false)
@EnableNeo4jRepositories(basePackages = "com.insurance.aml.module.monitoring.repository.graph")
public class Neo4jConfig {

    @Value("${spring.neo4j.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${spring.neo4j.authentication.username:neo4j}")
    private String username;

    @Value("${spring.neo4j.authentication.password:CHANGE_ME_DEV_NEO4J_PASSWORD}")
    private String password;

    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }

    @Bean
    public Neo4jClient neo4jClient(Driver driver) {
        return Neo4jClient.create(driver);
    }
}
