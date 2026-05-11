package com.insurance.aml.module.screening.service;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 名称匹配服务
 * 提供Jaro-Winkler、Levenshtein距离、拼音转换等名称相似度计算算法
 * 用于制裁名单与客户名称的比对
 */
@Service
@Slf4j
public class NameMatcher {

    /**
     * Jaro-Winkler 阈值前缀长度（标准为4）
     */
    private static final int JARO_WINKLER_PREFIX_LENGTH = 4;

    /**
     * Jaro-Winkler 缩放因子（标准为0.1）
     */
    private static final double JARO_WINKLER_SCALING_FACTOR = 0.1;

    /**
     * 计算 Jaro-Winkler 相似度
     * 算法说明：
     * 1. 计算两个字符串的匹配字符数和转位数
     * 2. 得到 Jaro 相似度
     * 3. 根据共同前缀长度加权提升
     *
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 相似度（0.0 - 1.0）
     */
    public double calculateJaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (Objects.equals(s1, s2)) {
            return 1.0;
        }

        // 去除空白并转小写后比较
        s1 = s1.replaceAll("\\s+", "").toLowerCase();
        s2 = s2.replaceAll("\\s+", "").toLowerCase();

        if (s1.equals(s2)) {
            return 1.0;
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0 || len2 == 0) {
            return 0.0;
        }

        // 匹配窗口：字符串长度的一半减1
        int matchWindow = Math.max(len1, len2) / 2 - 1;
        if (matchWindow < 0) {
            matchWindow = 0;
        }

        // 记录哪些字符已匹配
        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];

        int matches = 0;
        int transpositions = 0;

        // 第一轮：查找匹配字符
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(i + matchWindow + 1, len2);

            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) {
                    continue;
                }
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) {
            return 0.0;
        }

        // 第二轮：计算转位数
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) {
                continue;
            }
            while (!s2Matches[k]) {
                k++;
            }
            if (s1.charAt(i) != s2.charAt(k)) {
                transpositions++;
            }
            k++;
        }

        // Jaro 相似度
        double jaro = ((double) matches / len1
                + (double) matches / len2
                + (matches - transpositions / 2.0) / matches) / 3.0;

        // Winkler 修正：根据共同前缀加权
        int prefixLength = 0;
        int maxPrefix = Math.min(JARO_WINKLER_PREFIX_LENGTH, Math.min(len1, len2));
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefixLength++;
            } else {
                break;
            }
        }

        return jaro + prefixLength * JARO_WINKLER_SCALING_FACTOR * (1.0 - jaro);
    }

    /**
     * 计算两个名称的相似度分数（0-100）
     * 根据是否中文名称选择不同算法：
     * - 中文名称：先转拼音再用Jaro-Winkler对比，同时保留中文直接对比分数，取较高值
     * - 非中文名称：直接使用Jaro-Winkler算法
     *
     * @param name1     名称1
     * @param name2     名称2
     * @param isChinese 是否为中文名称（name1是否含中文）
     * @return 相似度分数（0-100）
     */
    public double calculateSimilarity(String name1, String name2, boolean isChinese) {
        if (name1 == null || name2 == null || name1.isBlank() || name2.isBlank()) {
            return 0.0;
        }

        if (isChinese) {
            // 中文名称：同时计算拼音相似度和直接相似度，取较高值
            String pinyin1 = toPinyin(name1);
            String pinyin2 = toPinyin(name2);
            double pinyinScore = calculateJaroWinkler(pinyin1, pinyin2) * 100.0;

            // 去除空格后直接对比
            double directScore = calculateJaroWinkler(
                    name1.replaceAll("\\s+", ""),
                    name2.replaceAll("\\s+", "")
            ) * 100.0;

            return Math.max(pinyinScore, directScore);
        } else {
            // 非中文名称：直接使用Jaro-Winkler
            return calculateJaroWinkler(name1, name2) * 100.0;
        }
    }

    /**
     * 计算 Levenshtein 编辑距离
     * 将一个字符串转换为另一个字符串所需的最少单字符编辑操作次数
     *
     * @param s1 字符串1
     * @param s2 字符串2
     * @return 编辑距离
     */
    public int levenshteinDistance(String s1, String s2) {
        if (s1 == null) {
            return s2 == null ? 0 : s2.length();
        }
        if (s2 == null) {
            return s1.length();
        }

        int len1 = s1.length();
        int len2 = s2.length();

        // 动态规划矩阵
        int[][] dp = new int[len1 + 1][len2 + 1];

        // 初始化边界
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // 填充矩阵
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),  // 删除/插入
                        dp[i - 1][j - 1] + cost                          // 替换
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * 将中文字符串转换为拼音
     * 使用 pinyin4j 库进行转换
     *
     * @param chinese 中文字符串
     * @return 拼音字符串（小写，去除空格）
     */
    public String toPinyin(String chinese) {
        if (chinese == null || chinese.isBlank()) {
            return "";
        }

        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chinese.length(); i++) {
            char c = chinese.charAt(i);
            if (Character.toString(c).matches("[\\u4E00-\\u9FA5]+")) {
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        sb.append(pinyinArray[0]);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    log.warn("拼音转换失败: {}", c, e);
                    sb.append(c);
                }
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().replaceAll("\\s+", "");
    }

    /**
     * 判断两个证件号码是否精确匹配
     * 去除空格和特殊字符后忽略大小写进行比较
     *
     * @param id1 证件号码1
     * @param id2 证件号码2
     * @return 是否精确匹配
     */
    public boolean isExactIdMatch(String id1, String id2) {
        if (id1 == null || id2 == null) {
            return false;
        }
        // 去除空格和特殊字符后忽略大小写比较
        String normalizedId1 = id1.replaceAll("[\\s\\-]", "").toUpperCase();
        String normalizedId2 = id2.replaceAll("[\\s\\-]", "").toUpperCase();
        return normalizedId1.equals(normalizedId2);
    }

    /**
     * 判断字符串是否包含中文字符
     *
     * @param str 待检查字符串
     * @return 是否包含中文
     */
    public boolean containsChinese(String str) {
        if (str == null) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (Character.toString(str.charAt(i)).matches("[\\u4E00-\\u9FA5]+")) {
                return true;
            }
        }
        return false;
    }
}
