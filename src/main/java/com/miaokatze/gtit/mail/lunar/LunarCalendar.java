package com.miaokatze.gtit.mail.lunar;

/**
 * 公历 → 农历换算（v1.7.8 猫猫邮件节日增强）
 * <p>
 * 内置 1900-2100 农历压缩位表（每农历年 1 个 int：高 17 位 = 十二个月大小月 +
 * 闰月大小，低 4 位 = 闰月月份，0 = 无闰月）+ 经典查表换算算法，
 * 零外部依赖。位表取自广泛使用的开源农历实现
 * （jjonline/calendar.js，其 1935/1978/2029/2057 等年值已按香港天文台历表修正）。
 * <p>
 * 支持范围：公历 1900-01-31（农历 1900 年正月初一）起的换算；
 * 超出 2100 年表尾或早于基准日返回 {@code null}，调用方需判空。
 * <p>
 * 实现注意：公历天数差用纯整数儒略日算法（Howard Hinnant {@code days_from_civil}）
 * 计算，不经过 {@link java.util.TimeZone}/{@link java.util.Calendar}——
 * 1986-1991 中国夏令时会让 {@code Calendar.getTimeInMillis()} 差值偏离 24h 整数倍，
 * 截断换算会差一天。
 * <p>
 * 线程安全：全部为无状态静态方法。
 */
public final class LunarCalendar {

    private LunarCalendar() {}

    /** 农历日期（含闰月标记） */
    public static final class LunarDate {

        /** 农历年（如 2025） */
        public final int year;
        /** 农历月（1-12；闰月时 {@link #leapMonth}=true 且 month=闰几月） */
        public final int month;
        /** 农历日（1-30） */
        public final int day;
        /** 是否闰月 */
        public final boolean leapMonth;

        LunarDate(int year, int month, int day, boolean leapMonth) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.leapMonth = leapMonth;
        }

        /** 展示形如 {@code 2025年六月初二(闰)} */
        @Override
        public String toString() {
            return year + "年" + (leapMonth ? "闰" : "") + month + "月" + day + "日";
        }
    }

    // ==================== 压缩位表（1900-2100，每年 1 个 int） ====================

    /**
     * 农历数据表：位含义（自低位起）
     * <ul>
     * <li>bit0-3（0xF）：闰月月份，0 = 该年无闰月</li>
     * <li>bit4-16（0x10..0x10000）：1-12 月大小（1 = 30 天，0 = 29 天），
     * 第 m 月对应 {@code 0x10000 >> m}</li>
     * <li>bit16（0x10000）：闰月大小（1 = 30 天，0 = 29 天）</li>
     * </ul>
     */
    private static final int[] LUNAR_INFO = { 0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0,
        0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x16a95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06aa0, 0x1a6c4, 0x0aae0, // 2050-2059
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520 // 2100
    };

    /** 位表起始农历年 */
    private static final int BASE_YEAR = 1900;
    /** 位表结束农历年（含） */
    private static final int MAX_YEAR = 2100;
    /** 基准日：公历 1900-01-31 = 农历 1900 年正月初一 */
    private static final int BASE_OFFSET = daysFromCivil(1900, 1, 31);

    // ==================== 公历 → 农历 ====================

    /**
     * 公历日期换算农历
     *
     * @param year  公历年（1900-2100）
     * @param month 公历月（1-12）
     * @param day   公历日（1-31）
     * @return 农历日期；超出支持范围返回 null
     */
    public static LunarDate solarToLunar(int year, int month, int day) {
        if (year < BASE_YEAR || year > MAX_YEAR || month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        // 距基准日的天数（0 = 1900-01-31）
        int offset = daysFromCivil(year, month, day) - BASE_OFFSET;
        if (offset < 0) return null;

        // 逐年扣除整年天数，定位农历年
        int lunarYear = BASE_YEAR;
        while (lunarYear < MAX_YEAR) {
            int days = lunarYearDays(lunarYear);
            if (offset < days) break;
            offset -= days;
            lunarYear++;
        }
        if (offset < 0) return null; // 防御：不应发生

        // 逐月扣除，定位农历月/日（闰月排在同号正常月之后）
        int leap = leapMonth(lunarYear);
        int lunarMonth = 1;
        boolean inLeap = false;
        while (true) {
            int len = monthDays(lunarYear, lunarMonth);
            if (offset < len) break;
            offset -= len;
            if (leap > 0 && lunarMonth == leap) {
                int leapLen = leapMonthDays(lunarYear);
                if (offset < leapLen) {
                    inLeap = true;
                    break;
                }
                offset -= leapLen;
            }
            if (lunarMonth >= 12) return null; // 防御：年份循环已保证不会越出腊月
            lunarMonth++;
        }
        return new LunarDate(lunarYear, lunarMonth, offset + 1, inLeap);
    }

    // ==================== 位表查询（除夕等岁末判定也直接用） ====================

    /**
     * 指定农历年的闰月月份
     *
     * @return 闰几月（1-12）；无闰月返回 0
     */
    public static int leapMonth(int lunarYear) {
        int info = info(lunarYear);
        return (info & 0xF) == 0 ? 0 : info & 0xF;
    }

    /**
     * 指定农历年闰月的天数（无闰月返回 0）
     */
    public static int leapMonthDays(int lunarYear) {
        if (leapMonth(lunarYear) == 0) return 0;
        return (info(lunarYear) & 0x10000) != 0 ? 30 : 29;
    }

    /**
     * 指定农历年某正常月的天数（29/30）
     *
     * @param lunarMonth 农历月（1-12）
     */
    public static int monthDays(int lunarYear, int lunarMonth) {
        return (info(lunarYear) & (0x10000 >> lunarMonth)) != 0 ? 30 : 29;
    }

    /**
     * 指定农历年全年总天数（12 个正常月 + 可选闰月）
     */
    public static int lunarYearDays(int lunarYear) {
        int sum = 348; // 12 x 29
        for (int bit = 0x8000; bit > 0x8; bit >>= 1) {
            sum += (info(lunarYear) & bit) != 0 ? 1 : 0;
        }
        return sum + leapMonthDays(lunarYear);
    }

    private static int info(int lunarYear) {
        return LUNAR_INFO[lunarYear - BASE_YEAR];
    }

    // ==================== 纯整数公历天数（免时区/夏令时干扰） ====================

    /**
     * 公历日期 → 自 1970-01-00 起的天数（Howard Hinnant days_from_civil，纯整数）
     */
    private static int daysFromCivil(int year, int month, int day) {
        year -= month <= 2 ? 1 : 0;
        int era = (year >= 0 ? year : year - 399) / 400;
        int yoe = year - era * 400; // [0, 399]
        int doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1; // [0, 365]
        int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy; // [0, 146096]
        return era * 146097 + doe - 719468;
    }
}
