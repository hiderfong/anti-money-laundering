package com.insurance.aml.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DateUtils 日期工具单元测试
 */
@ExtendWith(MockitoExtension.class)
class DateUtilsTest {

    @Test
    @DisplayName("工作日判断 - 周一是工作日")
    void testIsWorkingDayMonday() {
        // 找到一个最近的周一
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        assertTrue(DateUtils.isWorkingDay(monday), "周一应为工作日");
    }

    @Test
    @DisplayName("工作日判断 - 周六不是工作日")
    void testIsWorkingDaySaturday() {
        LocalDate saturday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        assertFalse(DateUtils.isWorkingDay(saturday), "周六不应为工作日");
    }

    @Test
    @DisplayName("工作日判断 - 周日不是工作日")
    void testIsWorkingDaySunday() {
        LocalDate sunday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        assertFalse(DateUtils.isWorkingDay(sunday), "周日不应为工作日");
    }

    @Test
    @DisplayName("增加工作日 - 周五加1个工作日到下周一")
    void testAddBusinessDaysFridayPlus1() {
        LocalDate friday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        LocalDate result = DateUtils.addBusinessDays(friday, 1);
        assertEquals(DayOfWeek.MONDAY, result.getDayOfWeek(), "周五加1个工作日应为下周一");
    }

    @Test
    @DisplayName("增加工作日 - 周三加3个工作日到下周一")
    void testAddBusinessDaysWednesdayPlus3() {
        LocalDate wednesday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
        LocalDate result = DateUtils.addBusinessDays(wednesday, 3);
        assertEquals(DayOfWeek.MONDAY, result.getDayOfWeek(), "周三加3个工作日应为下周一");
    }

    @Test
    @DisplayName("增加工作日 - 周一减1个工作日到上周五")
    void testAddBusinessDaysMondayMinus1() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate result = DateUtils.addBusinessDays(monday, -1);
        assertEquals(DayOfWeek.FRIDAY, result.getDayOfWeek(), "周一减1个工作日应为上周五");
    }

    @Test
    @DisplayName("距离未来日期的天数 - 返回正数")
    void testDaysUntilFutureDate() {
        LocalDate futureDate = LocalDate.now().plusDays(10);
        long days = DateUtils.daysUntil(futureDate);
        assertTrue(days > 0, "距离未来日期的天数应为正数");
    }

    @Test
    @DisplayName("距离过去日期的天数 - 返回负数")
    void testDaysUntilPastDate() {
        LocalDate pastDate = LocalDate.now().minusDays(10);
        long days = DateUtils.daysUntil(pastDate);
        assertTrue(days < 0, "距离过去日期的天数应为负数");
    }

    @Test
    @DisplayName("两个日期间的工作日数 - 周一到周五为4个工作日")
    void testBusinessDaysBetweenMondayToFriday() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate friday = monday.plusDays(4);
        long businessDays = DateUtils.businessDaysBetween(monday, friday);
        assertEquals(4, businessDays, "周一到周五应为4个工作日");
    }

    @Test
    @DisplayName("两个日期间的工作日数 - 正确包含周末")
    void testBusinessDaysBetweenSpanningWeekend() {
        LocalDate thursday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
        LocalDate nextWednesday = thursday.plusDays(6); // 跨越周末
        long businessDays = DateUtils.businessDaysBetween(thursday, nextWednesday);
        assertEquals(4, businessDays, "周四到下周三应为4个工作日（Fri,Mon,Tue,Wed）");
    }

    @Test
    @DisplayName("日期格式化 - yyyy-MM-dd格式")
    void testFormatDate() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        String formatted = DateUtils.formatDate(date);
        assertEquals("2025-03-15", formatted, "日期应格式化为yyyy-MM-dd");
    }

    @Test
    @DisplayName("日期时间格式化 - yyyy-MM-dd HH:mm:ss格式")
    void testFormatDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2025, 3, 15, 14, 30, 45);
        String formatted = DateUtils.formatDateTime(dateTime);
        assertEquals("2025-03-15 14:30:45", formatted, "日期时间应格式化为yyyy-MM-dd HH:mm:ss");
    }
}
