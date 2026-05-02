package com.insurance.aml.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EncryptUtils 加密工具单元测试
 * 使用ReflectionTestUtils注入@Value字段，无需启动Spring上下文
 */
@ExtendWith(MockitoExtension.class)
class EncryptUtilsTest {

    private EncryptUtils encryptUtils;

    @BeforeEach
    void setUp() {
        String testKey = "12345678901234567890123456789012"; // 32字节AES密钥
        encryptUtils = new EncryptUtils(testKey);
    }

    @Test
    @DisplayName("加密后解密 - 返回原始明文")
    void testEncryptThenDecrypt() {
        String plaintext = "Hello, World!";
        String ciphertext = encryptUtils.encrypt(plaintext);
        assertNotNull(ciphertext, "密文不应为空");
        assertNotEquals(plaintext, ciphertext, "密文不应与明文相同");
        String decrypted = encryptUtils.decrypt(ciphertext);
        assertEquals(plaintext, decrypted, "解密后应返回原始明文");
    }

    @Test
    @DisplayName("加密结果不同 - 每次加密因随机IV产生不同密文")
    void testEncryptProducesDifferentCiphertext() {
        String plaintext = "Same plaintext";
        String ciphertext1 = encryptUtils.encrypt(plaintext);
        String ciphertext2 = encryptUtils.encrypt(plaintext);
        // 由于使用随机IV，两次加密结果应不同
        assertNotEquals(ciphertext1, ciphertext2, "相同明文两次加密应产生不同密文");
    }

    @Test
    @DisplayName("解密无效密文 - 应抛出异常")
    void testDecryptInvalidCiphertext() {
        String invalidCiphertext = "this_is_not_valid_base64!!";
        assertThrows(Exception.class, () -> encryptUtils.decrypt(invalidCiphertext),
                "解密无效密文应抛出异常");
    }

    @Test
    @DisplayName("加密解密空字符串")
    void testEncryptDecryptEmptyString() {
        String plaintext = "";
        String ciphertext = encryptUtils.encrypt(plaintext);
        String decrypted = encryptUtils.decrypt(ciphertext);
        assertEquals(plaintext, decrypted, "空字符串加密解密后应保持一致");
    }

    @Test
    @DisplayName("加密解密中文字符")
    void testEncryptDecryptChineseCharacters() {
        String plaintext = "你好，世界！反洗钱系统";
        String ciphertext = encryptUtils.encrypt(plaintext);
        String decrypted = encryptUtils.decrypt(ciphertext);
        assertEquals(plaintext, decrypted, "中文字符加密解密后应保持一致");
    }

    @Test
    @DisplayName("加密解密特殊字符")
    void testEncryptDecryptSpecialCharacters() {
        String plaintext = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
        String ciphertext = encryptUtils.encrypt(plaintext);
        String decrypted = encryptUtils.decrypt(ciphertext);
        assertEquals(plaintext, decrypted, "特殊字符加密解密后应保持一致");
    }
}
