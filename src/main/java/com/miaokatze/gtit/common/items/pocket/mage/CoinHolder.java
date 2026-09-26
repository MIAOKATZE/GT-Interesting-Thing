package com.miaokatze.gtit.common.items.pocket.mage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketSession;

/**
 * 「一处能拿出猫猫币的地方」——猫猫币充能侧的<b>来源</b>抽象（R96 S9a 验收 2 的口径载体）。
 * <p>
 * 存在的理由是把"先扣哪、后扣哪"这条<b>用户裁定（G-2）</b>做成一个可注入的<b>有序数组</b>，
 * 而不是埋在驱动体内的两段 {@code for}：有序性是唯一判据，测试要能直接看见
 * "口袋 135 格在前、玩家背包 36 格在后"，并能在两档各放一枚币时断言<b>只有前者</b>被扣。
 * <p>
 * ★三档生产实现都从这里出（避免第二份"怎么扣一格"的实现）：
 * <ul>
 * <li>{@link #ofSession}：面板活会话在场时的口袋中栏（走 {@code setStorageStackAt} 那条
 * <b>不经过</b> {@code isItemValid} 的写入面，ghost 声明格照样能当来源 —— 口径同
 * {@link PocketSession#setStorageStackAt}）；</li>
 * <li>{@link #ofStorage}：无会话时的一次性 {@link PocketInventory#storage()}（135 格，
 * 由调用方在动作后整表 {@code writeTo} 一次，序列化上界 = 扫描率，R53c）；</li>
 * <li>{@link #ofPlayer}：玩家主背包 36 格，扣减走 {@code InventoryPlayer#decrStackSize}
 * （先例：{@code reincarnation/handler/ReincarnationHandler.java:690-698} 的"按首个命中格扣 1 枚"）。</li>
 * </ul>
 */
public interface CoinHolder {

    /** 本档的格数（越界一律按"没东西"处理，不抛）。 */
    int slots();

    /** 第 {@code slot} 格当前内容（可能为 {@code null}）。 */
    ItemStack stackAt(int slot);

    /**
     * 从第 {@code slot} 格扣掉<b>一枚</b>。
     *
     * @return 实际扣掉的个数（0 = 那一格空了/越界；1 = 扣到了）。★调用方只有在返回 1 之后
     *         才允许给容量 —— 反序就是"币没扣、容量白给"（R96 S9a 验收 2 明文禁止）。
     */
    int consumeOne(int slot);

    /** 口袋中栏（会话形态）：读 {@link PocketSession#storageStackAt}、写 {@link PocketSession#setStorageStackAt}。 */
    static CoinHolder ofSession(PocketSession session) {
        return new CoinHolder() {

            @Override
            public int slots() {
                return session == null ? 0 : session.storageSlots();
            }

            @Override
            public ItemStack stackAt(int slot) {
                return session == null ? null : session.storageStackAt(slot);
            }

            @Override
            public int consumeOne(int slot) {
                final ItemStack current = stackAt(slot);
                if (session == null || current == null || current.stackSize <= 0) {
                    return 0;
                }
                if (current.stackSize == 1) {
                    session.setStorageStackAt(slot, null);
                } else {
                    final ItemStack rest = current.copy();
                    rest.stackSize = current.stackSize - 1;
                    session.setStorageStackAt(slot, rest);
                }
                session.markDirty();
                return 1;
            }
        };
    }

    /** 口袋中栏（一次性读档形态）：直接摸 {@link PocketInventory#storage()} 那一组 135 格。 */
    static CoinHolder ofStorage(PocketInventory inventory) {
        return new CoinHolder() {

            @Override
            public int slots() {
                return inventory == null ? 0 : inventory.storage().getSlots();
            }

            @Override
            public ItemStack stackAt(int slot) {
                return inventory == null ? null : inventory.storage().getStackInSlot(slot);
            }

            @Override
            public int consumeOne(int slot) {
                final ItemStack current = stackAt(slot);
                if (inventory == null || current == null || current.stackSize <= 0) {
                    return 0;
                }
                final int size = slots();
                if (slot < 0 || slot >= size) {
                    return 0;
                }
                if (current.stackSize == 1) {
                    inventory.storage().setStackInSlot(slot, null);
                } else {
                    final ItemStack rest = current.copy();
                    rest.stackSize = current.stackSize - 1;
                    inventory.storage().setStackInSlot(slot, rest);
                }
                // ItemStackHandler 的 onContentsChanged 已把 PocketInventory 置脏，落盘由调用方一次 writeTo
                return 1;
            }
        };
    }

    /** 玩家主背包 36 格（★扣减用 vanilla 的 {@code decrStackSize}，不自己写数组元素）。 */
    static CoinHolder ofPlayer(EntityPlayer player) {
        return new CoinHolder() {

            @Override
            public int slots() {
                final ItemStack[] main = player == null ? null : player.inventory.mainInventory;
                return main == null ? 0 : main.length;
            }

            @Override
            public ItemStack stackAt(int slot) {
                final ItemStack[] main = player == null ? null : player.inventory.mainInventory;
                return main == null || slot < 0 || slot >= main.length ? null : main[slot];
            }

            @Override
            public int consumeOne(int slot) {
                final ItemStack[] main = player == null ? null : player.inventory.mainInventory;
                if (main == null || slot < 0 || slot >= main.length) {
                    return 0;
                }
                final ItemStack current = main[slot];
                if (current == null || current.stackSize <= 0) {
                    return 0;
                }
                return player.inventory.decrStackSize(slot, 1) == null ? 0 : 1;
            }
        };
    }
}
