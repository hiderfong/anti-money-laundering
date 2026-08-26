package com.insurance.aml.module.integration.adapter;

import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import com.insurance.aml.module.integration.model.entity.IntegrationJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("模拟集成适配器测试")
class MockIntegrationAdapterTest {
    private final MockIntegrationAdapter adapter = new MockIntegrationAdapter();

    @Test
    @DisplayName("成功端点返回配置的交换数量")
    void successEndpointReturnsConfiguredCounts() {
        IntegrationExecutionResult result = adapter.execute(
                connector("mock://success?read=500&written=498&skipped=2"), job(), 1);

        assertTrue(result.isSuccess());
        assertEquals(500, result.getRecordsRead());
        assertEquals(498, result.getRecordsWritten());
        assertEquals(2, result.getRecordsSkipped());
    }

    @Test
    @DisplayName("抖动端点按尝试次数恢复")
    void flakyEndpointRecoversAfterPlannedFailures() {
        IntegrationConnector connector = connector("mock://flaky?failures=1&read=30&written=30");

        assertFalse(adapter.execute(connector, job(), 1).isSuccess());
        assertTrue(adapter.execute(connector, job(), 2).isSuccess());
    }

    @Test
    @DisplayName("失败端点健康检查返回异常")
    void failureEndpointFailsHealthCheck() {
        IntegrationExecutionResult result = adapter.healthCheck(connector("mock://failure"));

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrorCount());
    }

    private IntegrationConnector connector(String endpoint) {
        IntegrationConnector connector = new IntegrationConnector();
        connector.setEndpointUrl(endpoint);
        connector.setTransportType("MOCK");
        return connector;
    }

    private IntegrationJob job() {
        IntegrationJob job = new IntegrationJob();
        job.setBatchSize(1000);
        return job;
    }
}
