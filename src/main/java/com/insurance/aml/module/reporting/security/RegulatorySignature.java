package com.insurance.aml.module.reporting.security;

/** 报文摘要与签名结果。 */
public record RegulatorySignature(String payloadHash, String algorithm, String signatureValue) {
}
