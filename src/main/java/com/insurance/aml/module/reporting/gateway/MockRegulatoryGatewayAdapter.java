package com.insurance.aml.module.reporting.gateway;

import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 监管闭环演练适配器。mock://success 默认受理，可通过 receipt/poll/code/message 参数模拟回执。
 */
@Component
public class MockRegulatoryGatewayAdapter implements RegulatoryGatewayAdapter {
    @Override
    public String transportType() {
        return "MOCK";
    }

    @Override
    public RegulatoryGatewayResult submit(IntegrationConnector connector, RegulatorySubmissionEnvelope envelope) {
        URI uri = parse(connector.getEndpointUrl());
        String mode = host(uri);
        Map<String, String> params = params(uri);
        if ("failure".equals(mode)) {
            return RegulatoryGatewayResult.builder()
                    .transmitted(false)
                    .errorMessage(params.getOrDefault("message", "模拟监管前置平台不可用"))
                    .build();
        }
        String requestId = "MOCK-REQ-" + UUID.randomUUID().toString().replace("-", "");
        String status = params.getOrDefault("receipt", "ACCEPTED").toUpperCase();
        return receiptResult(requestId, status, params, "即时模拟回执");
    }

    @Override
    public RegulatoryGatewayResult queryReceipt(IntegrationConnector connector, String externalRequestId) {
        URI uri = parse(connector.getEndpointUrl());
        Map<String, String> params = params(uri);
        String status = params.getOrDefault("poll", params.getOrDefault("receipt", "ACCEPTED")).toUpperCase();
        return receiptResult(externalRequestId, status, params, "轮询模拟回执");
    }

    private RegulatoryGatewayResult receiptResult(String requestId, String status, Map<String, String> params,
                                                   String source) {
        String code = params.getOrDefault("code", "REJECTED".equals(status) ? "DATA_QUALITY" : "0000");
        String message = params.getOrDefault("message", "REJECTED".equals(status)
                ? "模拟回执：客户身份字段需要修正" : "监管前置平台已受理");
        String receiptNo = "PENDING".equals(status) ? null : "MOCK-RCPT-" + System.currentTimeMillis();
        String payload = String.format(
                "{\"source\":\"%s\",\"status\":\"%s\",\"code\":\"%s\",\"message\":\"%s\"}",
                source, status, code, message.replace("\"", "'"));
        return RegulatoryGatewayResult.builder()
                .transmitted(true)
                .externalRequestId(requestId)
                .receiptStatus(status)
                .receiptNo(receiptNo)
                .receiptCode(code)
                .receiptMessage(message)
                .receiptPayload(payload)
                .build();
    }

    private URI parse(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if (!"mock".equalsIgnoreCase(uri.getScheme()) || host(uri).isBlank()) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("监管模拟端点格式无效：" + endpoint, exception);
        }
    }

    private String host(URI uri) {
        return uri.getHost() == null ? "" : uri.getHost().toLowerCase();
    }

    private Map<String, String> params(URI uri) {
        Map<String, String> result = new HashMap<>();
        if (uri.getRawQuery() == null) {
            return result;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 1 ? "" : URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
