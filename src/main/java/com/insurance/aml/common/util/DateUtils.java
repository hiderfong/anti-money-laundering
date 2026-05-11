package com.insurance.aml.common.util;

import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

/**
 * 日期工具类
 * 提供日期计算、工作日判断、格式化等常用方法
 */
@Slf4j
public class DateUtils {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter COMPACT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private DateUtils() {}

    /**
     * 判断是否为工作日（周一至周五）
     */
    public static boolean isWorkingDay(LocalDate date) {
        if (date == null) return false;
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /**
     * 增加N个工作日（跳过周末）
     */
    public static LocalDate addBusinessDays(LocalDate startDate, int businessDays) {
        if (startDate == null || businessDays == 0) return startDate;
        LocalDate result = startDate;
        int step = businessDays > 0 ? 1 : -1;
        int remaining = Math.abs(businessDays);
        while (remaining > 0) {
            result = result.plusDays(step);
            if (isWorkingDay(result)) {
                remaining--;
            }
        }
        return result;
    }

    /**
     * 计算距离截止日期的剩余天数
     */
    public static int daysUntil(LocalDate deadline) {
        if (deadline == null) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline);
    }

    /**
     * 计算两个日期之间的工作日天数
     */
    public static int businessDaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || !start.isBefore(end)) return 0;
        int count = 0;
        LocalDate current = start.plusDays(1);
        while (!current.isAfter(end)) {
            if (isWorkingDay(current)) count++;
            current = current.plusDays(1);
        }
        return count;
    }

    /**
     * 获取本月第一天
     */
    public static LocalDate firstDayOfMonth(LocalDate date) {
        return date.with(TemporalAdjusters.firstDayOfMonth());
    }

    /**
     * 获取本月最后一天
     */
    public static LocalDate lastDayOfMonth(LocalDate date) {
        return date.with(TemporalAdjusters.lastDayOfMonth());
    }

    /**
     * 格式化日期 yyyy-MM-dd
     */
    public static String formatDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FORMAT);
    }

    /**
     * 格式化日期时间 yyyy-MM-dd HH:mm:ss
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATETIME_FORMAT);
    }

    /**
     * 格式化为紧凑格式 yyyyMMddHHmmss
     */
    public static String formatCompact(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(COMPACT_FORMAT);
    }

    /**
     * 获取当前紧凑时间字符串
     */
    public static String nowCompact() {
        return LocalDateTime.now().format(COMPACT_FORMAT);
    }
}
