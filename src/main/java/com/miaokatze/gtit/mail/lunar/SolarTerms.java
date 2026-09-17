package com.miaokatze.gtit.mail.lunar;

import java.util.HashMap;
import java.util.Map;

/**
 * 二十四节气公历日期计算（v1.7.8 猫猫邮件节日增强）
 * <p>
 * 采用经典「通式寿星公式」{@code [Y*D+C]-L}：Y = 公历年后两位，D = 0.2422，
 * C = 依节气与世纪而定的常数（20/21 世纪各一组），L = 世纪内闰年数——
 * 小寒/大寒/立春/雨水四个 1-2 月节气取 {@code (Y-1)/4}，其余取 {@code Y/4}。
 * 公式存在少量已知偏差年，按流传的例外年表逐项修正（加减 1 日）。
 * <p>
 * 只输出公历日期（不做时刻/天文级计算），适用于 1901-2100 年；
 * 超出范围返回 {@code null}。24 节气全支持。
 * <p>
 * 线程安全：全部为无状态静态方法（例外表初始化后只读）。
 */
public final class SolarTerms {

    private SolarTerms() {}

    // ==================== 节气名（index 0-23 依次对应 1-12 月的两个节气） ====================

    private static final String[] TERM_NAMES = { "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至" };

    // ==================== 寿星公式 C 值（20/21 世纪） ====================

    /** 20 世纪（1901-2000）C 值，与 {@link #TERM_NAMES} 同序 */
    private static final double[] C_1900S = { 6.11, 20.84, 4.6295, 19.4599, 6.3826, 21.4155, 5.59, 20.888, 6.318, 21.86,
        6.5, 22.2, 7.928, 23.65, 8.35, 23.95, 8.44, 23.822, 9.098, 24.218, 8.218, 23.08, 7.9, 22.6 };

    /** 21 世纪（2001-2100）C 值，与 {@link #TERM_NAMES} 同序 */
    private static final double[] C_2000S = { 5.4055, 20.12, 3.87, 18.73, 5.63, 20.646, 4.81, 20.1, 5.52, 21.04, 5.678,
        21.37, 7.108, 22.83, 7.5, 23.13, 7.646, 23.042, 8.318, 23.438, 7.438, 22.36, 7.18, 21.94 };

    // ==================== 已知例外年表（公式偏差 ±1 日修正） ====================

    /**
     * 例外修正表，行 = {世纪(0=1900s,1=2000s), 节气 index, 年份, 日修正量}
     */
    private static final int[][] TERM_EXCEPTIONS = { { 0, 0, 1982, 1 }, { 1, 0, 2019, -1 }, // 小寒
        { 1, 1, 2082, 1 }, // 大寒
        { 0, 3, 1911, -1 }, { 1, 3, 2026, -1 }, // 雨水
        { 1, 5, 2084, 1 }, // 春分
        { 0, 8, 1911, 1 }, // 立夏
        { 1, 9, 2008, 1 }, // 小满
        { 0, 10, 1902, 1 }, // 芒种
        { 0, 11, 1928, 1 }, // 夏至
        { 0, 12, 1925, 1 }, { 1, 12, 2016, 1 }, // 小暑
        { 0, 13, 1922, 1 }, // 大暑
        { 1, 14, 2002, 1 }, // 立秋
        { 0, 16, 1927, 1 }, // 白露
        { 0, 17, 1942, 1 }, // 秋分
        { 1, 19, 2089, 1 }, // 霜降
        { 1, 20, 2089, 1 }, // 立冬
        { 0, 21, 1978, 1 }, // 小雪
        { 0, 22, 1954, 1 }, // 大雪
        { 0, 23, 1918, -1 }, { 1, 23, 2021, -1 } // 冬至
    };

    /** 例外修正索引：{@code "世纪_年份_节气index" -> 修正天数}（初始化后只读） */
    private static final Map<String, Integer> EXCEPTION_INDEX = buildExceptionIndex();

    // ==================== 查询 ====================

    /**
     * 查询公历日期当天是否恰为某节气交节日
     *
     * @param year  公历年（1901-2100）
     * @param month 公历月（1-12）
     * @param day   公历日
     * @return 节气名（如 "清明"）；当日非交节日或超出支持范围返回 null
     */
    public static String getTermName(int year, int month, int day) {
        if (year < 1901 || year > 2100 || month < 1 || month > 12) return null;
        // 当月两个节气 index
        for (int termIndex = (month - 1) * 2; termIndex <= (month - 1) * 2 + 1; termIndex++) {
            if (termDay(year, termIndex) == day) return TERM_NAMES[termIndex];
        }
        return null;
    }

    /**
     * 指定年份某节气的交节公历日
     *
     * @param termIndex 节气序号（0-23，见 {@link #TERM_NAMES}）
     * @return 当月第几日
     */
    public static int termDay(int year, int termIndex) {
        double[] cTable = year >= 2001 ? C_2000S : C_1900S;
        int y = year % 100;
        // 1-2 月节气（小寒/大寒/立春/雨水）闰年数取 (Y-1)/4，其余取 Y/4
        int leapCount = termIndex <= 3 ? (y - 1) / 4 : y / 4;
        int day = (int) Math.floor(y * 0.2422 + cTable[termIndex]) - leapCount;
        Integer delta = EXCEPTION_INDEX.get(exceptionKey(year, termIndex));
        return delta != null ? day + delta : day;
    }

    private static String exceptionKey(int year, int termIndex) {
        return (year >= 2001 ? "1_" : "0_") + year + "_" + termIndex;
    }

    private static Map<String, Integer> buildExceptionIndex() {
        Map<String, Integer> index = new HashMap<>();
        for (int[] row : TERM_EXCEPTIONS) {
            int century = row[0];
            int termIndex = row[1];
            int year = row[2];
            int delta = row[3];
            // key 与 exceptionKey 同构：世纪_年份_节气index
            index.put(century + "_" + year + "_" + termIndex, delta);
        }
        return index;
    }
}
