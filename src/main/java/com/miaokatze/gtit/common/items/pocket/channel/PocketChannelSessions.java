package com.miaokatze.gtit.common.items.pocket.channel;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.common.items.pocket.PocketCellBindings;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketRuntimeSession;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;

/** 免 GUI 持续通道的准入与复原入口。绑定及生效位检查通过后，使用与面板相同的 PocketRuntimeSession；不维护第二套持久化和补偿实现。 */
public final class PocketChannelSessions {

    private PocketChannelSessions() {}

    /**
     * 载体档 → 会话内存的<b>唯一解析入口</b>（面板装配与复原腿共用，别处不得再写第二份
     * "取 NBT 根 + readFrom"的式子）。{@code null} 栈 / 无根 ⇒ 空档解析（不建档，R53c）。
     */
    public static PocketInventory parseCarrierInventory(ItemStack carrier) {
        return PocketInventory.readFrom(carrier == null ? null : carrier.getTagCompound());
    }

    /**
     * 复原一只 headless 会话（★三条前置见类 javadoc；任一不满足 ⇒ {@code null} 且<b>零写入</b>）。
     * 成功 ⇒ 已登记进 {@link PocketSessions}（同键顶替旧会话时旧会话先落盘，语义与面板开屏一致）。
     * <p>
     * 成本口径（R53c）：调用方（driver 真空支）每 tick 都会路过这里 ⇒ 先用
     * {@link PocketCellBindings#readFrom} 那张<b>便宜</b>的绑定表当门（只解析 boundCells 一张
     * NBTTagList，≤64 条）；表空 ⇒ 直接判"无可复原"，<b>不做</b>整套 135+36+12 格的 readFrom。
     * 只有真要复原的那一拍才付全档解析的成本（复原后会话在场 ⇒ 不会逐拍重复付）。
     */
    public static PocketSession restoreIfBound(EntityPlayer player, ItemStack carrier) {
        if (player == null || carrier == null) {
            return null;
        }
        final UUID uuid = player.getGameProfile() == null ? null
            : player.getGameProfile()
                .getId();
        if (uuid == null) {
            return null;
        }
        // ★前置①：persist 位当前生效（组合谓词；关着开关这条路跟着断，S1 语义不被绕过）
        if (!PocketUpgradeSwitches.isActive(carrier, PocketUpgradeType.CHANNEL_PERSIST)) {
            return null;
        }
        final NBTTagCompound root = carrier.getTagCompound();
        if (root == null) {
            return null;
        }
        // ★前置③的便宜门 + 前置②的载体：绑定表非空才值得付全档解析（空表 = 没有可搬的对面）
        if (PocketCellBindings.readFrom(root)
            .isEmpty()) {
            return null;
        }
        final PocketInventory inventory = parseCarrierInventory(carrier);
        if (inventory.bindings()
            .isEmpty()) {
            // 全档解析后表仍空（档与门读数不一致的病态形状）⇒ 按前置③拒绝，零登记零写入
            return null;
        }
        final PocketRuntimeSession session = new PocketRuntimeSession(
            () -> player,
            () -> null,
            -1,
            carrier,
            false,
            false,
            inventory);
        PocketSessions.register(session);
        return session;
    }

}
