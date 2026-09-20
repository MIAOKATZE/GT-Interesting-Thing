package com.miaokatze.gtit.common.items.infinitycell;

import java.util.Collection;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.main.GTInterestingThing;

import appeng.api.exceptions.AppEngException;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEStackType;

/**
 * 猫猫无限存储单元：GTIT 自有的多通道无限元件，取代从 AE2Things 移植来的两枚旧元件。
 * 同一枚元件在同一个驱动器槽位里同时向物品、流体以及整合包已注册的第三方通道（源质等）贡献无限容量，
 * 各通道内容按 (uuid, 通道) 分桶，互不影响。
 */
public class ItemNekoInfinityStorageUnit extends ItemInfinityStorageCell {

    public ItemNekoInfinityStorageUnit() {
        super();
        this.setUnlocalizedName("gtit.neko_infinity_unit");
        this.setTextureName(GTInterestingThing.MODID + ":neko_infinity_unit");
    }

    @Override
    public Collection<IAEStackType<?>> getSupportedStackTypes() {
        return InfinityStackTypes.allSupportedTypes();
    }

    @Override
    public IMEInventoryHandler<?> getInventoryHandler(ItemStack o, ISaveProvider container, EntityPlayer player,
        IAEStackType<?> type) throws AppEngException {
        if (type == InfinityStackTypes.ITEM_STACK_TYPE) {
            // 物品通道沿用父类实现，分区卡/升级卡/模糊过滤行为与旧元件保持一致
            return this.getInventoryHandler(o, container, player);
        }
        return this.createTypedHandler(o, container, type);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private IMEInventoryHandler<?> createTypedHandler(ItemStack o, ISaveProvider container, IAEStackType type)
        throws AppEngException {
        final InfinityTypedCellInventory inventory = new InfinityTypedCellInventory(
            o,
            container,
            type,
            this.getBytesPerType(o),
            this.getIdleDrain());
        return new InfinityTypedCellHandler(inventory, type);
    }

    @Override
    protected void appendDeprecationNotices(final List<String> lines) {
        // 本单元是替代品，不是被废弃件：父类那段 [OLD] 提示对继承者不成立
    }

    @Override
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
        final boolean displayMoreInfo) {
        lines.add(StatCollector.translateToLocal("item.gtit.neko_infinity_unit.tooltip.channel"));
        super.addInformation(stack, player, lines, displayMoreInfo);
    }
}
