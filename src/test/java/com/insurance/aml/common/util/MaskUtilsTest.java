package com.insurance.aml.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MaskUtils 脱敏工具单元测试
 */
@ExtendWith(MockitoExtension.class)
class MaskUtilsTest {

    @Test
    @DisplayName("姓名脱敏 - 两个字的姓名")
    void testMaskNameTwoChars() {
        assertEquals("张*", MaskUtils.maskName("张三"));
    }

    @Test
    @DisplayName("姓名脱敏 - 四个字的姓名")
    void testMaskNameFourChars() {
        assertEquals("欧***", MaskUtils.maskName("欧阳明月"));
    }

    @Test
    @DisplayName("姓名脱敏 - null输入返回null")
    void testMaskNameNull() {
        assertNull(MaskUtils.maskName(null));
    }

    @Test
    @DisplayName("姓名脱敏 - 空字符串返回空字符串")
    void testMaskNameEmpty() {
        assertEquals("", MaskUtils.maskName(""));
    }

    @Test
    @DisplayName("身份证号脱敏 - 保留前4位和后4位")
    void testMaskIdNumber() {
        assertEquals("1101**********1234", MaskUtils.maskIdNumber("110101199001011234"));
    }

    @Test
    @DisplayName("身份证号脱敏 - 位数不足不做脱敏")
    void testMaskIdNumberTooShort() {
        assertEquals("12345", MaskUtils.maskIdNumber("12345"));
    }

    @Test
    @DisplayName("手机号脱敏 - 保留前3位和后4位")
    void testMaskPhone() {
        assertEquals("138****5678", MaskUtils.maskPhone("13812345678"));
    }

    @Test
    @DisplayName("邮箱脱敏 - 保留前2位用户名和域名")
    void testMaskEmail() {
        assertEquals("zh******@example.com", MaskUtils.maskEmail("zhangsan@example.com"));
    }

    @Test
    @DisplayName("银行账号脱敏 - 保留后4位")
    void testMaskBankAccount() {
        assertEquals("***************0123", MaskUtils.maskBankAccount("6222021234567890123"));
    }
}
