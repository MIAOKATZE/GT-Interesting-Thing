package com.miaokatze.gtit.register;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/**
 * 材质注册管理器
 * 统一管理模组内的所有材质资源，提供材质缓存、自定义图标定义以及资源路径创建功能。
 */
public class TextureManager {

    // RC2 native two-argument texture registration.
    // 猫猫售货机正面材质（V2 独立版，从 VM 复制到本 mod）
    public static final IIconContainer NEKOVM_FRONT_OFF = Textures.BlockIcons.custom("gtit", "nekovm_front_off");
    public static final IIconContainer NEKOVM_FRONT_ON = Textures.BlockIcons.custom("gtit", "nekovm_front_on");
    public static final IIconContainer NEKOVM_FRONT_ON_GLOW = Textures.BlockIcons
        .custom("gtit", "nekovm_front_on_glow");
    public static final IIconContainer NEKOVM_CASING = Textures.BlockIcons.custom("gtit", "nekovm_casing");

    public static final IIconContainer NEKO_VENDING_CASING_SIDE = Textures.BlockIcons
        .custom("gtit", "neko_vending_casing_side");
    public static final IIconContainer NEKO_VENDING_CASING_TOP = Textures.BlockIcons
        .custom("gtit", "neko_vending_casing_top");
    public static final IIconContainer NEKO_VENDING_CASING_BOTTOM = Textures.BlockIcons
        .custom("gtit", "neko_vending_casing_bottom");
    public static final IIconContainer NEKO_VENDING_CONTROLLER_OFF = Textures.BlockIcons
        .custom("gtit", "neko_vending_controller_off");
    public static final IIconContainer NEKO_VENDING_CONTROLLER_ON = Textures.BlockIcons
        .custom("gtit", "neko_vending_controller_on");
    public static final IIconContainer NEKO_VENDING_CONTROLLER_GLOW = Textures.BlockIcons
        .custom("gtit", "neko_vending_controller_glow");

    // 猫猫售货机覆盖材质（非激活状态）
    public static final IIconContainer NEKOVM_OVERLAY_1 = Textures.BlockIcons.custom("gtit", "nekovm_1"); // 右上
    public static final IIconContainer NEKOVM_OVERLAY_2 = Textures.BlockIcons.custom("gtit", "nekovm_2"); // 左上
    public static final IIconContainer NEKOVM_OVERLAY_3 = Textures.BlockIcons.custom("gtit", "nekovm_3"); // 左下

    // 猫猫售货机覆盖材质（激活状态）
    public static final IIconContainer NEKOVM_OVERLAY_ACTIVE_1 = Textures.BlockIcons.custom("gtit", "nekovm_active_1"); // 右上
    public static final IIconContainer NEKOVM_OVERLAY_ACTIVE_2 = Textures.BlockIcons.custom("gtit", "nekovm_active_2"); // 左上
    public static final IIconContainer NEKOVM_OVERLAY_ACTIVE_3 = Textures.BlockIcons.custom("gtit", "nekovm_active_3"); // 左下

    // 猫猫售货机覆盖材质数组（与覆盖层偏移顺序对应，控制器在右下角）
    public static final IIconContainer[] NEKOVM_OVERLAY = new IIconContainer[] { NEKOVM_OVERLAY_1, // 索引0: 右上 (0, +1)
        NEKOVM_OVERLAY_2, // 索引1: 左上 (-1, +1)
        NEKOVM_OVERLAY_3, // 索引2: 左下 (-1, 0)
    };
    public static final IIconContainer[] NEKOVM_OVERLAY_ACTIVE = new IIconContainer[] { NEKOVM_OVERLAY_ACTIVE_1, // 索引0:
                                                                                                                 // 右上
                                                                                                                 // (0,
                                                                                                                 // +1)
        NEKOVM_OVERLAY_ACTIVE_2, // 索引1: 左上 (-1, +1)
        NEKOVM_OVERLAY_ACTIVE_3, // 索引2: 左下 (-1, 0)
    };

    // 猫猫电视机 3×2 屏幕：从上到下，每行从左到右；六片共享固定动画时间轴。
    public static final IIconContainer[] NEKOTV_OVERLAY = new IIconContainer[] {
        Textures.BlockIcons.custom("gtit", "nekotv_1"), Textures.BlockIcons.custom("gtit", "nekotv_2"),
        Textures.BlockIcons.custom("gtit", "nekotv_3"), Textures.BlockIcons.custom("gtit", "nekotv_4"),
        Textures.BlockIcons.custom("gtit", "nekotv_5"), Textures.BlockIcons.custom("gtit", "nekotv_6"), };
    public static final IIconContainer[] NEKOTV_OVERLAY_ACTIVE = new IIconContainer[] {
        Textures.BlockIcons.custom("gtit", "nekotv_active_1"), Textures.BlockIcons.custom("gtit", "nekotv_active_2"),
        Textures.BlockIcons.custom("gtit", "nekotv_active_3"), Textures.BlockIcons.custom("gtit", "nekotv_active_4"),
        Textures.BlockIcons.custom("gtit", "nekotv_active_5"), Textures.BlockIcons.custom("gtit", "nekotv_active_6"), };

    // 5×4 全正面：逐行从左到右，共19片，跳过底行中央控制器。旧六片字段保留兼容。
    public static final IIconContainer[] NEKOTV_FULL_OVERLAY = new IIconContainer[] {
        Textures.BlockIcons.custom("gtit", "nekotv_full_1"), Textures.BlockIcons.custom("gtit", "nekotv_full_2"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_3"), Textures.BlockIcons.custom("gtit", "nekotv_full_4"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_5"), Textures.BlockIcons.custom("gtit", "nekotv_full_6"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_7"), Textures.BlockIcons.custom("gtit", "nekotv_full_8"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_9"), Textures.BlockIcons.custom("gtit", "nekotv_full_10"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_11"), Textures.BlockIcons.custom("gtit", "nekotv_full_12"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_13"), Textures.BlockIcons.custom("gtit", "nekotv_full_14"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_15"), Textures.BlockIcons.custom("gtit", "nekotv_full_16"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_17"), Textures.BlockIcons.custom("gtit", "nekotv_full_18"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_19"), };
    public static final IIconContainer[] NEKOTV_FULL_OVERLAY_ACTIVE = new IIconContainer[] {
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_1"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_2"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_3"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_4"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_5"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_6"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_7"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_8"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_9"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_10"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_11"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_12"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_13"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_14"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_15"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_16"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_17"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_18"),
        Textures.BlockIcons.custom("gtit", "nekotv_full_active_19"), };

    private static final Map<String, ITexture> textureCache = new HashMap<>();

    public static ITexture getOrCreateTexture(String name, IIconContainer icon) {
        return textureCache.computeIfAbsent(name, k -> TextureFactory.of(icon));
    }

    public static ITexture getTexture(String name) {
        return textureCache.get(name);
    }

    public static void registerTexture(String name, ITexture texture) {
        textureCache.put(name, texture);
    }

    public static ResourceLocation createResourceLocation(String path) {
        return new ResourceLocation("gtit", path);
    }

    public static void clearCache() {
        textureCache.clear();
    }
}
