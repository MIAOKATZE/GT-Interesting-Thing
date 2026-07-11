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

    public static final IIconContainer TEX_TEST_EV = Textures.BlockIcons.custom("gtit:MTETEST_1");
    public static final IIconContainer TEX_TEST_IV = Textures.BlockIcons.custom("gtit:MTETEST_2");
    public static final IIconContainer TEX_TEST_LUV = Textures.BlockIcons.custom("gtit:MTETEST_3");

    // 猫猫售货机正面材质（V2 独立版，从 VM 复制到本 mod）
    public static final IIconContainer NEKOVM_FRONT_OFF = Textures.BlockIcons.custom("gtit:nekovm_front_off");
    public static final IIconContainer NEKOVM_FRONT_ON = Textures.BlockIcons.custom("gtit:nekovm_front_on");
    public static final IIconContainer NEKOVM_FRONT_ON_GLOW = Textures.BlockIcons.custom("gtit:nekovm_front_on_glow");
    public static final IIconContainer NEKOVM_CASING = Textures.BlockIcons.custom("gtit:nekovm_casing");

    // 猫猫售货机覆盖材质（非激活状态）
    public static final IIconContainer NEKOVM_OVERLAY_1 = Textures.BlockIcons.custom("gtit:nekovm_1"); // 右上
    public static final IIconContainer NEKOVM_OVERLAY_2 = Textures.BlockIcons.custom("gtit:nekovm_2"); // 左上
    public static final IIconContainer NEKOVM_OVERLAY_3 = Textures.BlockIcons.custom("gtit:nekovm_3"); // 左下

    // 猫猫售货机覆盖材质（激活状态）
    public static final IIconContainer NEKOVM_OVERLAY_ACTIVE_1 = Textures.BlockIcons.custom("gtit:nekovm_active_1"); // 右上
    public static final IIconContainer NEKOVM_OVERLAY_ACTIVE_2 = Textures.BlockIcons.custom("gtit:nekovm_active_2"); // 左上
    public static final IIconContainer NEKOVM_OVERLAY_ACTIVE_3 = Textures.BlockIcons.custom("gtit:nekovm_active_3"); // 左下

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
