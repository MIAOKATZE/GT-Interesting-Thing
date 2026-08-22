package com.miaokatze.gtit.gui.vm.edit;

import net.minecraft.item.ItemStack;

/**
 * 编辑面板 JSON 编解码工具（A01 蓝图 G2 抽取自 NekoVMGuiV2.itemStackToEditJson）
 * <p>
 * 交易/标签页/轮盘/卡池等编辑面板共用的 ItemStack 序列化器，
 * 编辑面板的 PhantomItemSlot 可能放入带 NBT 的物品，
 * NBT 以 Base64 编码随 JSON 发送到服务端保存。
 */
public final class EditJsonCodec {

    private EditJsonCodec() {}

    /**
     * 将 ItemStack 序列化为编辑用 JSON（含 NBT）
     *
     * @param stack 物品堆
     * @return JSON 对象 {item, meta, amount, nbtBase64?}
     */
    public static com.google.gson.JsonObject itemStackToEditJson(ItemStack stack) {
        com.google.gson.JsonObject item = new com.google.gson.JsonObject();
        item.addProperty("item", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
        item.addProperty("meta", stack.getItemDamage());
        item.addProperty("amount", stack.stackSize);
        if (stack.hasTagCompound() && stack.getTagCompound() != null) {
            item.addProperty("nbtBase64", com.miaokatze.gtit.util.NbtBase64Util.nbtToBase64(stack.getTagCompound()));
        }
        return item;
    }
}
