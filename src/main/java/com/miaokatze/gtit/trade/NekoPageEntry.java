package com.miaokatze.gtit.trade;

/**
 * 标签页条目数据模型
 * 用于标签页配置文件的序列化/反序列化
 */
public class NekoPageEntry {

    private int id; // 标签页ID：1=猫猫币，2=闪烁猫猫币，3=其他，4+=自定义
    private String name; // 标签页显示名称
    private String iconItem; // 图标物品ID（modid:name 格式）
    private int iconMeta; // 图标物品元数据
    private String iconNbt; // 图标物品NBT（Base64编码，可选）
    private boolean isDefault; // 是否为默认标签页（不可删除）

    public NekoPageEntry() {
        this.name = "";
        this.iconItem = "";
        this.iconMeta = 0;
        this.iconNbt = "";
        this.isDefault = false;
    }

    public NekoPageEntry(int id, String name, String iconItem, int iconMeta, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.iconItem = iconItem;
        this.iconMeta = iconMeta;
        this.iconNbt = "";
        this.isDefault = isDefault;
    }

    // --- Getters & Setters ---

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconItem() {
        return iconItem;
    }

    public void setIconItem(String iconItem) {
        this.iconItem = iconItem;
    }

    public int getIconMeta() {
        return iconMeta;
    }

    public void setIconMeta(int iconMeta) {
        this.iconMeta = iconMeta;
    }

    public String getIconNbt() {
        return iconNbt;
    }

    public void setIconNbt(String iconNbt) {
        this.iconNbt = iconNbt;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    /**
     * 从 ItemStack 提取图标信息并设置到该条目
     */
    public void setIconFromItemStack(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            this.iconItem = "";
            this.iconMeta = 0;
            this.iconNbt = "";
            return;
        }
        this.iconItem = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem())
            .toString();
        this.iconMeta = stack.getItemDamage();
        if (stack.hasTagCompound() && stack.getTagCompound() != null) {
            this.iconNbt = NekoTradeEntry.nbtToBase64(stack.getTagCompound());
        } else {
            this.iconNbt = "";
        }
    }

    /**
     * 将图标信息转换回 ItemStack
     */
    public net.minecraft.item.ItemStack toIconItemStack() {
        if (iconItem == null || iconItem.isEmpty()) return null;

        String[] parts = iconItem.split(":", 2);
        if (parts.length < 2) return null;

        net.minecraft.item.Item item = cpw.mods.fml.common.registry.GameRegistry.findItem(parts[0], parts[1]);
        if (item == null) return null;

        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item, 1, iconMeta);

        if (iconNbt != null && !iconNbt.isEmpty()) {
            net.minecraft.nbt.NBTTagCompound nbt = NekoTradeEntry.nbtFromBase64(iconNbt);
            if (nbt != null) {
                stack.setTagCompound(nbt);
            }
        }

        return stack;
    }
}
