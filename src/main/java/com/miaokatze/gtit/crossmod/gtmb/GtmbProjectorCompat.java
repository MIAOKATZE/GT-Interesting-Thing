package com.miaokatze.gtit.crossmod.gtmb;

import net.minecraft.item.Item;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/** 只提供旧物品的可选单向映射；GTIT 不注册或运行投影仪引擎。 */
public final class GtmbProjectorCompat {

    public static final String GROUP_ID = "gtit-neko-hologram-projector";
    public static final String ITEM_NAME = "neko_hologram_projector";
    public static final String OLD_ITEM_ID = "gtit:" + ITEM_NAME;
    public static final String GTMB_ITEM_ID = "gtmb:" + ITEM_NAME;

    private GtmbProjectorCompat() {}

    public static boolean isGtmbLoaded() {
        return Loader.isModLoaded("gtmb");
    }

    /** 世界加载阶段所有物品已注册；仅真实 GTMB 物品可作为 Forge 重映射目标。 */
    public static void remapMissingItems(FMLMissingMappingsEvent event) {
        if (!isGtmbLoaded()) return;
        Item target = GameRegistry.findItem("gtmb", ITEM_NAME);
        if (target == null) return;
        for (FMLMissingMappingsEvent.MissingMapping mapping : event.getAll()) {
            if (mapping.type == GameRegistry.Type.ITEM && OLD_ITEM_ID.equals(mapping.name)) {
                // Forge 负责保存的数值 ID 与注册名别名；数量、damage 和 NBT 原样读取。
                mapping.remap(target);
            }
        }
    }
}
