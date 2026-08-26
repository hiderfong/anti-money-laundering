package com.insurance.aml.module.reporting.security;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 报文签名器。凭据只按引用名从运行环境读取；MOCK 通道允许摘要签名用于业务演练。
 */
@Component
public class RegulatoryPayloadSigner {
    private final Environment environment;

    public RegulatoryPayloadSigner(Environment environment) {
        this.environment = environment;
    }

    public RegulatorySignature sign(String payload, IntegrationConnector connector) {
        try {
            byte[] content = payload.getBytes(StandardCharsets.UTF_8);
            String payloadHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            String secret = resolveCredential(connector.getCredentialRef());
            if (StringUtils.hasText(secret)) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return new RegulatorySignature(payloadHash, "HMAC-SHA256",
                        HexFormat.of().formatHex(mac.doFinal(content)));
            }
            if ("MOCK".equalsIgnoreCase(connector.getTransportType())) {
                return new RegulatorySignature(payloadHash, "SHA-256-MOCK", payloadHash);
            }
            throw new BusinessException(ResultCode.REPORT_SIGNATURE_FAIL,
                    "监管连接器未配置可解析的签名凭据引用");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.REPORT_SIGNATURE_FAIL, "监管报文签名失败");
        }
    }

    private String resolveCredential(String reference) {
        if (!StringUtils.hasText(reference)) {
            return null;
        }
        String value = environment.getProperty(reference);
        return StringUtils.hasText(value) ? value : System.getenv(reference);
    }
}
