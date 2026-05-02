package com.insurance.aml.common.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 业务ID生成器
 * 用于生成各类业务单号，格式：前缀 + 时间戳 + 随机数
 */
@Component
public class IdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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

    /**
     * 生成业务ID
     *
     * @param prefix 业务前缀
     * @return 前缀 + yyyyMMddHHmmss + 6位随机数
     */
    public String generate(String prefix) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return prefix + timestamp + random;
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
