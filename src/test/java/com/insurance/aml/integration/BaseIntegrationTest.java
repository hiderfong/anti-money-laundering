package com.insurance.aml.integration;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类
 * 使用test profile（H2嵌入式数据库 + Mock Redis）
 */
@SpringBootTest(
        properties = {
                "aml.neo4j.enabled=false",
                "aml.kafka.enabled=false",
                "aml.es.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration,org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MockRedisConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class BaseIntegrationTest {
}
