package com.insurance.aml.module.integration.adapter;

import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import com.insurance.aml.module.integration.model.entity.IntegrationJob;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 框架验证适配器。通过 mock://success、mock://failure、mock://flaky 演练成功、失败和重试。
 */
@Component
public class MockIntegrationAdapter implements IntegrationAdapter {
    @Override
    public String transportType() {
        return "MOCK";
    }

    @Override
    public IntegrationExecutionResult healthCheck(IntegrationConnector connector) {
        String mode = mode(connector.getEndpointUrl());
        if ("failure".equals(mode)) {
            return failure("模拟上游服务不可用");
        }
        return IntegrationExecutionResult.builder()
                .success(true)
                .responseSummary("模拟连接检查通过")
                .build();
    }

    @Override
    public IntegrationExecutionResult execute(IntegrationConnector connector, IntegrationJob job, int attempt) {
        URI uri = parse(connector.getEndpointUrl());
        String mode = normalizeHost(uri);
        Map<String, String> params = queryParams(uri);
        int plannedFailures = integer(params, "failures", 1);
        if ("failure".equals(mode) || ("flaky".equals(mode) && attempt <= plannedFailures)) {
            return failure("模拟上游在第" + attempt + "次调用时返回失败");
        }

        int read = integer(params, "read", Math.min(job.getBatchSize() == null ? 100 : job.getBatchSize(), 1000));
        int skipped = Math.min(integer(params, "skipped", 0), read);
        int written = Math.min(integer(params, "written", read - skipped), read);
        return IntegrationExecutionResult.builder()
                .success(true)
                .recordsRead(read)
                .recordsWritten(written)
                .recordsSkipped(skipped)
                .errorCount(0)
                .responseSummary("模拟交换完成：读取" + read + "条，写入" + written + "条，跳过" + skipped + "条")
                .build();
    }

    private IntegrationExecutionResult failure(String message) {
        return IntegrationExecutionResult.builder()
                .success(false)
                .errorCount(1)
                .errorMessage(message)
                .build();
    }

    private String mode(String endpointUrl) {
        return normalizeHost(parse(endpointUrl));
    }

    private URI parse(String endpointUrl) {
        try {
            URI uri = URI.create(endpointUrl);
            if (!"mock".equalsIgnoreCase(uri.getScheme()) || normalizeHost(uri).isBlank()) {
                throw new IllegalArgumentException("模拟端点必须使用 mock:// 模式");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("模拟端点格式无效：" + endpointUrl, exception);
        }
    }

    private String normalizeHost(URI uri) {
        return uri.getHost() == null ? "" : uri.getHost().toLowerCase();
    }

    private Map<String, String> queryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        if (uri.getRawQuery() == null) {
            return params;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private int integer(Map<String, String> params, String key, int defaultValue) {
        try {
            return Math.max(0, Integer.parseInt(params.getOrDefault(key, String.valueOf(defaultValue))));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
