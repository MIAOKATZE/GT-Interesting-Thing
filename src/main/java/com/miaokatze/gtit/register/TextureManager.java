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

    // [GT-compat] 单参 custom 兼容层（beta1/beta2/beta3/RC1）：GT5U 正式版发布时移除本兼容口径并切换至双参 custom(domain, name)
    // 目标兼容面 = GT5U 四代（源码不含仅高版本写法）：5.09.52.594 / 5.09.54.20 / 5.09.54.133 / 5.09.54.183（本轮基线 = RC-1，即最后一代）。
    // 为何不用仅高版本写法：Textures.BlockIcons.custom(String) 单参在 beta-3/RC-1 已 @Deprecated 但未删，而双参
    // custom(String,String) 是 beta-3 才引入的形态，beta1/beta2 的源级与 javap 两腿都不存在，采用即破那两代编译。
    // 本文件下方 10 处材质常量全部只用单参，并把域写进字符串前缀（"gtit:nekovm_*"），从未使用双参重载。
    // 实测口径：本轮只跑 RC-1 一格（compileJava + build + 六个 JavaExec 套件），beta-3 腿由换基线前的 v1.8.38（commit 2636205）承担，b1/b2 不实测也不对外许诺。
    // 判据出处：plan/gtit-rc1-baseline-20260925/evidence/r5-survival-legs.md（Q1 四代存续表）与同目录
    // evidence/r1-gtit-todo-table.md。
    // 跨代对照走参考库归档夹 2.9.0 beta-3\ 只读，或参考库新口径 GT5-Unofficial-5.09.54.183 里 git show <tag>:<path>，不切工作树。
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
