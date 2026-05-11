package com.insurance.aml.module.screening.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 名称匹配服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("名称匹配服务测试")
class NameMatcherTest {

    @InjectMocks
    private NameMatcher nameMatcher;

    @Test
    @DisplayName("JaroWinkler相似度 - 相似字符串返回高分")
    void testJaroWinklerSimilar() {
        double score = nameMatcher.calculateJaroWinkler("Smith", "Smyth");
        assertTrue(score > 0.8, "Smith与Smyth的相似度应大于0.8，实际: " + score);
    }

    @Test
    @DisplayName("JaroWinkler相似度 - 相同字符串返回1.0")
    void testJaroWinklerIdentical() {
        double score = nameMatcher.calculateJaroWinkler("John", "John");
        assertEquals(1.0, score, 0.001, "相同字符串的相似度应为1.0");
    }

    @Test
    @DisplayName("JaroWinkler相似度 - 完全不同字符串返回低分")
    void testJaroWinklerDifferent() {
        double score = nameMatcher.calculateJaroWinkler("abc", "xyz");
        assertTrue(score < 0.5, "完全不同字符串的相似度应小于0.5，实际: " + score);
    }

    @Test
    @DisplayName("中文转拼音 - 正常中文")
    void testToPinyin() {
        String pinyin = nameMatcher.toPinyin("张三");
        assertNotNull(pinyin);
        assertTrue(pinyin.contains("zhang"), "应包含zhang，实际: " + pinyin);
        assertTrue(pinyin.contains("san"), "应包含san，实际: " + pinyin);
    }

    @Test
    @DisplayName("中文转拼音 - 北京")
    void testToPinyinBeijing() {
        String pinyin = nameMatcher.toPinyin("北京");
        assertNotNull(pinyin);
        assertTrue(pinyin.contains("bei"), "应包含bei，实际: " + pinyin);
        assertTrue(pinyin.contains("jing"), "应包含jing，实际: " + pinyin);
    }

    @Test
    @DisplayName("中文转拼音 - null输入返回空字符串")
    void testToPinyinNull() {
        String pinyin = nameMatcher.toPinyin(null);
        assertEquals("", pinyin, "null输入应返回空字符串");
    }

    @Test
    @DisplayName("中文转拼音 - 英文输入原样返回")
    void testToPinyinEnglish() {
        String pinyin = nameMatcher.toPinyin("abc");
        assertEquals("abc", pinyin, "英文输入应原样返回");
    }

    @Test
    @DisplayName("证件号精确匹配 - 相同证件号")
    void testIsExactIdMatchSame() {
        assertTrue(nameMatcher.isExactIdMatch("110101199001011234", "110101199001011234"));
    }

    @Test
    @DisplayName("证件号精确匹配 - 不同证件号")
    void testIsExactIdMatchDifferent() {
        assertFalse(nameMatcher.isExactIdMatch("110101199001011234", "110101199001011235"));
    }

    @Test
    @DisplayName("包含中文 - 中文字符串返回true")
    void testContainsChineseTrue() {
        assertTrue(nameMatcher.containsChinese("张三"));
    }

    @Test
    @DisplayName("包含中文 - 英文字符串返回false")
    void testContainsChineseFalse() {
        assertFalse(nameMatcher.containsChinese("abc"));
    }

    @Test
    @DisplayName("综合相似度计算 - 中文相同姓名返回高分")
    void testCalculateSimilarityChineseSameName() {
        double score = nameMatcher.calculateSimilarity("张三", "张三", true);
        assertTrue(score > 90, "相同中文姓名的相似度应大于90，实际: " + score);
    }

    @Test
    @DisplayName("综合相似度计算 - 相似英文名返回中等分数")
    void testCalculateSimilarityEnglishSimilarNames() {
        double score = nameMatcher.calculateSimilarity("Smith", "Smyth", false);
        assertTrue(score > 70 && score < 100,
                "Smith与Smyth的相似度应在70-100之间，实际: " + score);
    }
}
