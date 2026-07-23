package com.miaokatze.gtit.signin;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 自定义纪念日条目（v1.7.6 G2③）
 * <p>
 * 玩家自配的纪念日记录：名称 + 月日（"MM-dd"）+ 可选年份（0 表示不记年份，每年同日周年）。
 * 存储于 {@link DailySignInData#getAnniversaries()}（玩家 UUID 维度，随签到数据落盘），
 * 供「活跃」大页第 4 页（纪念日）展示与增删；后续 G5 祝福邮件按 MM-dd 匹配当日触发。
 * <p>
 * <b>数据兼容</b>：NBT 读写缺省兼容——旧档无对应键时读出空串/0，不影响加载。
 */
public class AnniversaryEntry {

    /** 纪念日名称长度上限（与服务端校验一致，防恶意包刷爆存储） */
    public static final int MAX_NAME_LENGTH = 20;
    /** 年份合法下限（0 表示不记年份） */
    public static final int MIN_YEAR = 1900;
    /** 年份合法上限 */
    public static final int MAX_YEAR = 2100;

    /** 纪念日名称（如「结婚纪念」「入坑纪念」） */
    private String name;
    /** 月日（"MM-dd"，如 "07-23"） */
    private String monthDay;
    /** 年份（0 = 不记年份，每年同日周年；>0 时可用于计算「第 N 周年」） */
    private int year;

    /**
     * 反序列化需要无参构造（配合 {@link #readFromNBT(NBTTagCompound)}）
     */
    public AnniversaryEntry() {
        this("", "", 0);
    }

    public AnniversaryEntry(String name, String monthDay, int year) {
        this.name = name == null ? "" : name;
        this.monthDay = monthDay == null ? "" : monthDay;
        this.year = Math.max(0, year);
    }

    /**
     * 拷贝构造（客户端缓存防御性拷贝用，避免 GUI 直接持有服务端同步对象引用）
     */
    public AnniversaryEntry(AnniversaryEntry other) {
        this(other == null ? "" : other.name, other == null ? "" : other.monthDay, other == null ? 0 : other.year);
    }

    // ==================== NBT 序列化 ====================

    /**
     * 写入 NBT 标签
     *
     * @return 包含本条目全部字段的 NBTTagCompound
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("name", name);
        nbt.setString("monthDay", monthDay);
        nbt.setInteger("year", year);
        return nbt;
    }

    /**
     * 从 NBT 标签读取（缺省兼容：无键=空串/0）
     *
     * @param nbt 包含条目数据的 NBTTagCompound
     */
    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt == null) return;
        this.name = nbt.getString("name");
        this.monthDay = nbt.getString("monthDay");
        this.year = Math.max(0, nbt.getInteger("year"));
    }

    // ==================== 校验工具 ====================

    /**
     * 校验 "MM-dd" 月日格式合法性
     * <p>
     * 规则：定长 5 字符、中划线分隔、月份 01-12、日期在该月天数范围内
     * （2 月按 29 天放行，不区分平闰年——纪念日按年触发，闰日四年一遇属预期）。
     *
     * @param monthDay 待校验的月日串
     * @return true 表示格式与取值均合法
     */
    public static boolean isValidMonthDay(String monthDay) {
        if (monthDay == null || monthDay.length() != 5 || monthDay.charAt(2) != '-') return false;
        int month;
        int day;
        try {
            month = Integer.parseInt(monthDay.substring(0, 2));
            day = Integer.parseInt(monthDay.substring(3, 5));
        } catch (NumberFormatException e) {
            return false;
        }
        if (month < 1 || month > 12) return false;
        return day >= 1 && day <= daysInMonth(month);
    }

    /**
     * 按月份返回天数（2 月恒按 29 天放行，见 {@link #isValidMonthDay}）
     */
    private static int daysInMonth(int month) {
        return switch (month) {
            case 2 -> 29;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
    }

    /**
     * 格式化月/日为 "MM-dd"（补零对齐）
     *
     * @param month 月（1-12）
     * @param day   日（1-31）
     * @return "MM-dd" 格式串
     */
    public static String formatMonthDay(int month, int day) {
        return String.format("%02d-%02d", month, day);
    }

    // ==================== Getter / Setter ====================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getMonthDay() {
        return monthDay;
    }

    public void setMonthDay(String monthDay) {
        this.monthDay = monthDay == null ? "" : monthDay;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = Math.max(0, year);
    }
}
