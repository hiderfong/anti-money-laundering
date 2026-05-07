package com.insurance.aml.integration;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类
 * 使用test profile（H2嵌入式数据库 + Mock Redis + Mock Neo4j）
 */
@SpringBootTest(
        classes = TestApplication.class,
        properties = {
                "aml.neo4j.enabled=false",
                "aml.kafka.enabled=false",
                "aml.es.enabled=false"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MockRedisConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class BaseIntegrationTest {
}
