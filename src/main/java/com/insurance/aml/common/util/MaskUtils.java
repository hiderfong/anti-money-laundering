package com.insurance.aml.common.util;

/**
 * 数据脱敏工具类
 * 提供姓名、身份证号、手机号、邮箱、银行卡号等敏感信息的脱敏处理
 */
public final class MaskUtils {

    private MaskUtils() {
        // 工具类不允许实例化
    }

    /**
     * 姓名脱敏：保留第一个字，其余用*替代
     * 例如：张三 -> 张*，欧阳修 -> 欧**
     *
     * @param name 姓名
     * @return 脱敏后的姓名
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /**
     * 身份证号脱敏：保留前4位和后4位，中间用*替代
     * 例如：110101199001011234 -> 1101**********1234
     *
     * @param idNumber 身份证号
     * @return 脱敏后的身份证号
     */
    public static String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.length() < 8) {
            return idNumber;
        }
        String prefix = idNumber.substring(0, 4);
        String suffix = idNumber.substring(idNumber.length() - 4);
        int maskLength = idNumber.length() - 8;
        return prefix + "*".repeat(maskLength) + suffix;
    }

    /**
     * 手机号脱敏：保留前3位和后4位，中间用*替代
     * 例如：13812345678 -> 138****5678
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        String prefix = phone.substring(0, 3);
        String suffix = phone.substring(phone.length() - 4);
        int maskLength = phone.length() - 7;
        return prefix + "*".repeat(maskLength) + suffix;
    }

    /**
     * 邮箱脱敏：@前的部分只保留前2个字符，其余用*替代
     * 例如：zhangsan@example.com -> zh******@example.com
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱地址
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "*" + domain;
        }
        String prefix = localPart.substring(0, 2);
        return prefix + "*".repeat(localPart.length() - 2) + domain;
    }

    /**
     * 银行卡号脱敏：只保留后4位，其余用*替代
     * 例如：6222021234567890123 -> *************0123
     *
     * @param account 银行卡号
     * @return 脱敏后的银行卡号
     */
    public static String maskBankAccount(String account) {
        if (account == null || account.length() < 4) {
            return account;
        }
        String suffix = account.substring(account.length() - 4);
        int maskLength = account.length() - 4;
        return "*".repeat(maskLength) + suffix;
    }
}
