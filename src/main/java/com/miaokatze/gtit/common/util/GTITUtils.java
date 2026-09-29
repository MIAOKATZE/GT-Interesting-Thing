package com.miaokatze.gtit.common.util;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/** 通用工具（R100 片 A）：品牌尾缀行统一入口，禁止逐类硬编码。 */
public final class GTITUtils {

    private GTITUtils() {}

    /**
     * 品牌尾缀行（全 mod 统一调用，禁止逐类硬编码）：
     * WHITE「添加模组：」+ AQUA GT + GREEN - + GOLD Interesting + RED - + BLUE Thing
     */
    public static String getAddedByLine() {
        return EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtit.tooltip.added_by")
            + " "
            + EnumChatFormatting.AQUA
            + "GT"
            + EnumChatFormatting.GREEN
            + "-"
            + EnumChatFormatting.GOLD
            + "Interesting"
            + EnumChatFormatting.RED
            + "-"
            + EnumChatFormatting.BLUE
            + "Thing";
    }
}
