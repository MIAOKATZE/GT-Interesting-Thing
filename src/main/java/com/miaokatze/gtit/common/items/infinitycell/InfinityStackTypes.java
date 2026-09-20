package com.miaokatze.gtit.common.items.infinitycell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import appeng.api.storage.ICellCacheRegistry.TYPE;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStackType;

/**
 * StorageChannel（仅 ITEMS/FLUIDS 的旧枚举）与 IAEStackType（本 fork 的通道真身）之间的换算口。
 */
public final class InfinityStackTypes {

    public static final IAEStackType<?> ITEM_STACK_TYPE = AEItemStackType.ITEM_STACK_TYPE;
    public static final IAEStackType<?> FLUID_STACK_TYPE = AEFluidStackType.FLUID_STACK_TYPE;

    /**
     * 新单元支持的通道 = 物品 + 流体 + 运行时已注册的第三方通道（源质等）。
     * 第三方通道只有在对应 mod 于 preInit 调过 AEStackTypeRegistry.register 时才存在，缺席时集合自然收缩。
     */
    public static Collection<IAEStackType<?>> allSupportedTypes() {
        final List<IAEStackType<?>> types = new ArrayList<>();
        types.add(ITEM_STACK_TYPE);
        types.add(FLUID_STACK_TYPE);
        for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (type != ITEM_STACK_TYPE && type != FLUID_STACK_TYPE) {
                types.add(type);
            }
        }
        return Collections.unmodifiableList(types);
    }

    public static IAEStackType<?> of(StorageChannel channel) {
        return channel == StorageChannel.FLUIDS ? FLUID_STACK_TYPE : ITEM_STACK_TYPE;
    }

    public static StorageChannel toChannel(IAEStackType<?> type) {
        return type == FLUID_STACK_TYPE ? StorageChannel.FLUIDS : StorageChannel.ITEMS;
    }

    /** 显示层封顶三档（ICellCacheRegistry.TYPE 只有 ITEM/FLUID/ESSENTIA），第 4 类通道回落 ESSENTIA 档。 */
    public static TYPE toCacheType(IAEStackType<?> type) {
        if (type == ITEM_STACK_TYPE) {
            return TYPE.ITEM;
        }
        if (type == FLUID_STACK_TYPE) {
            return TYPE.FLUID;
        }
        return TYPE.ESSENTIA;
    }

    public static IAEStackType<?> byId(String typeId) {
        return AEStackTypeRegistry.getType(typeId);
    }

    private InfinityStackTypes() {}
}
