package com.insurance.aml.common.util;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务ID生成器
 * 用于生成各类业务单号，格式：前缀 + 毫秒时间戳 + 单调序列
 */
@Component
public class IdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final long SEQUENCE_MODULO = 10_000L;
    private static final long MAX_SEQUENCE = SEQUENCE_MODULO - 1;

    /** 客户编号前缀 */
    public static final String PREFIX_CUSTOMER = "CUS";
    /** 告警编号前缀 */
    public static final String PREFIX_ALERT = "ALT";
    /** 案件编号前缀 */
    public static final String PREFIX_CASE = "CAS";
    /** 筛查编号前缀 */
    public static final String PREFIX_SCREENING = "SCR";
    /** 报告编号前缀 */
    public static final String PREFIX_REPORT = "RPT";

    private final AtomicLong lastSerial = new AtomicLong(0L);

    /**
     * 生成业务ID
     *
     * @param prefix 业务前缀
     * @return 前缀 + yyyyMMddHHmmssSSS + 4位单调序列
     */
    public String generate(String prefix) {
        long serial = nextSerial(System.currentTimeMillis());
        long timestampMillis = serial / SEQUENCE_MODULO;
        long sequence = serial % SEQUENCE_MODULO;
        String timestamp = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestampMillis),
                ZoneId.systemDefault()
        ).format(FORMATTER);
        return prefix + timestamp + String.format("%04d", sequence);
    }

    private long nextSerial(long currentMillis) {
        return lastSerial.updateAndGet(last -> {
            long lastMillis = last / SEQUENCE_MODULO;
            long lastSequence = last % SEQUENCE_MODULO;
            if (currentMillis > lastMillis) {
                return currentMillis * SEQUENCE_MODULO;
            }
            if (lastSequence < MAX_SEQUENCE) {
                return lastMillis * SEQUENCE_MODULO + lastSequence + 1;
            }
            return (lastMillis + 1) * SEQUENCE_MODULO;
        });
    }

    /**
     * 生成客户编号
     *
     * @return 客户编号，如 CUS20260101120000123456
     */
    public String generateCustomerNo() {
        return generate(PREFIX_CUSTOMER);
    }

    /**
     * 生成告警编号
     *
     * @return 告警编号
     */
    public String generateAlertNo() {
        return generate(PREFIX_ALERT);
    }

    /**
     * 生成案件编号
     *
     * @return 案件编号
     */
    public String generateCaseNo() {
        return generate(PREFIX_CASE);
    }

    /**
     * 生成筛查编号
     *
     * @return 筛查编号
     */
    public String generateScreeningNo() {
        return generate(PREFIX_SCREENING);
    }

    /**
     * 生成报告编号
     *
     * @return 报告编号
     */
    public String generateReportNo() {
        return generate(PREFIX_REPORT);
    }
}
