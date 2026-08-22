package com.miaokatze.gtit.trade;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.api.enums.GTITItemList;

/**
 * 猫猫币货币注册表
 * 定义猫猫币的 ID、显示名称、关联物品等
 * 完全独立于 VM 的 CurrencyType 枚举系统
 */
public class NekoCurrencyRegistrar {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    // 猫猫币 ID 常量
    public static final String NEKO_ID = "neko";
    public static final String SHIMMERING_NEKO_ID = "shimmeringNeko";

    // 猫猫币物品引用（在 init 时设置）
    public static Item nekoCoinItem = null;
    public static Item shimmeringNekoCoinItem = null;

    /**
     * 初始化猫猫币注册
     * 在 CommonProxy.postInit 中调用（确保物品已注册）
     */
    public static void init() {
        nekoCoinItem = GTITItemList.NekoCoin.getItem();
        shimmeringNekoCoinItem = GTITItemList.ShimmeringNekoCoin.getItem();
        LOG.info("猫猫币注册完成: neko={}, shimmeringNeko={}", nekoCoinItem, shimmeringNekoCoinItem);
    }

    /**
     * 获取所有猫猫币 ID
     */
    public static String[] getNekoCurrencyIds() {
        return new String[] { NEKO_ID, SHIMMERING_NEKO_ID };
    }

    /**
     * 获取猫猫币的显示名称
     */
    public static String getDisplayName(String currencyId) {
        if (NEKO_ID.equals(currencyId)) return "猫猫币";
        if (SHIMMERING_NEKO_ID.equals(currencyId)) return "闪烁猫猫币";
        return "未知货币";
    }

    /**
     * 判断物品是否是猫猫币
     */
    public static boolean isNekoCoinItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        Item item = stack.getItem();
        return item == nekoCoinItem || item == shimmeringNekoCoinItem;
    }

    /**
     * 从物品堆栈获取猫猫币 ID
     *
     * @return 猫猫币 ID，如果不是猫猫币返回 null
     */
    public static String getNekoCurrencyId(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        Item item = stack.getItem();
        if (item == nekoCoinItem) return NEKO_ID;
        if (item == shimmeringNekoCoinItem) return SHIMMERING_NEKO_ID;
        return null;
    }

    /**
     * 从猫猫币 ID 获取物品堆栈
     */
    public static ItemStack getItemStack(String currencyId, int amount) {
        if (NEKO_ID.equals(currencyId) && nekoCoinItem != null) {
            return new ItemStack(nekoCoinItem, amount);
        }
        if (SHIMMERING_NEKO_ID.equals(currencyId) && shimmeringNekoCoinItem != null) {
            return new ItemStack(shimmeringNekoCoinItem, amount);
        }
        return null;
    }
}
