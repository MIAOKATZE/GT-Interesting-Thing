package com.miaokatze.gtit.common.items.pocket.magnet;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.common.items.pocket.PocketUpgrades;

/**
 * 磁力升级（R95 {@code PocketUpgradeType#MAGNET}）的 <b>tick 宿主本体</b>：把玩家周围掉落物
 * 自动收进口袋。挂载点是 {@code ItemNekoDimensionPocket.onUpdate} 的服务端分支（★R95 门控放宽后
 * 背包 36 格任意位都推进，本类自带位图早退与节拍闸）。
 * <p>
 * <b>落点顺序（R95 裁定）：拾取入袋优先 → 玩家背包 → 脚下</b>——先尽力塞口袋中栏，塞不下的余量
 * 交玩家背包，背包也满才 {@code dropPlayerItemWithRandomChoice} 掉在玩家脚下（ tossing 出的新
 * EntityItem 带 {@code delayBeforeCanPickup}，本类的吸收判据天然跳过它 ⇒ 不会形成"掉了就吸、
 * 吸了又掉"的同拍循环；中栏与背包全满时物品会在脚下滞留，这是磁力满载的可观测代价）。
 * <p>
 * <b>吸收判据沿用 Botania 口径</b>：{@code EntityItem} 且非死亡（{@code isEntityAlive()}）、
 * {@code delayBeforeCanPickup <= 0}（刚丢出的物品有拾取延迟，防止把玩家故意丢掉的东西瞬间吸回）。
 * <p>
 * <b>成本口径（R95 裁定：多枚口袋各自扫，成本线性）</b>：每枚带 MAGNET 位的口袋各自独立扫描，
 * N 枚口袋 = N 次扫描；无实体时一次 AABB 查询即返回（零 NBT 读写、零分配）。
 * <p>
 * <b>序列化上界 = 扫描率（R53c 口径）</b>：节拍 {@link #SCAN_PERIOD_TICKS} 每 10 tick 扫一次，
 * 无会话分支的 {@code readFrom → writeTo} 一次读改写<b>每扫至多一次</b>（禁每实体一次），
 * 且只有真的收进东西才 {@code writeTo}——与"开一次面板关一次屏"完全同构，不出现每 tick 写档。
 * <p>
 * <b>持久契约（F1 双分支，同 {@code PocketWorldFluidTap}）</b>——世界侧写入不得与驱动
 * {@code persistIdle}/关屏写入交错出双真相：
 * <ol>
 * <li><b>有活会话且承载同一枚栈</b>（{@code PocketSessions.peek(uuid).carrierStack() == 本栈}，
 * 按对象身份）：走会话模型 {@link PocketSession#depositItem}（中栏 → 背包兜底，内部即
 * {@code PocketInventory.depositIntoStorage} + {@code moveToPlayer} 同一语义并置 dirty），落盘交给
 * 既有通路。<b>绝不直改这枚栈的 NBT</b>（双写竞争）。</li>
 * <li><b>无活会话（或会话承载的是另一枚口袋）</b>：一次性 NBT 读改写——
 * {@code PocketInventory.readFrom} → 逐实体 {@code depositIntoStorage} → 收进过才
 * {@code writeTo}（本扫一次读改写，禁每实体一次）。</li>
 * </ol>
 */
public final class PocketMagnetDriver {

    /** 扫描节拍（tick）：每 10 tick 扫一圈。★R53c：序列化上界 = 扫描率，本常数就是那个上界的分母。 */
    private static final int SCAN_PERIOD_TICKS = 10;

    /** 吸收半径（格，R95 裁定 8 格）：AABB 以玩家包围盒向三轴各扩这么多。 */
    private static final double SCAN_RANGE = 8.0;

    private PocketMagnetDriver() {}

    /**
     * 磁力驱动的一拍（<b>仅服务端</b>由宿主调用；客户端路径在宿主已早退，这里再判一次是零成本双保险）。
     * <p>
     * 顺序：MAGNET 位早退（无位 ⇒ 每 tick 只花一次位图读）→ 节拍闸（玩家 tick 计数 % 10——这是
     * <b>无状态取模闸</b>不是倒计时：不持有任何跨 tick 计时状态，跨维重建导致的相位跳变至多把
     * 某一拍推迟一拍，R59e 要消灭的"写死的绝对到期"形态在这里结构性不存在）→ AABB 扫
     * {@code EntityItem} → 无实体零开销返回 → 批量入袋 → 溢出回退。
     */
    public static void onItemTick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || player == null || stack == null) {
            return;
        }
        if (!PocketUpgrades.hasUpgrade(stack, PocketUpgradeType.MAGNET)) {
            return;
        }
        if (player.ticksExisted % SCAN_PERIOD_TICKS != 0) {
            return;
        }
        @SuppressWarnings("unchecked")
        final List<EntityItem> nearby = world
            .getEntitiesWithinAABB(EntityItem.class, player.boundingBox.expand(SCAN_RANGE, SCAN_RANGE, SCAN_RANGE));
        if (nearby == null || nearby.isEmpty()) {
            // 无实体早退：零 NBT 读写、零分配（R95 磁力的常态开销就是一次空 AABB 查询/10 tick）
            return;
        }
        final NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            // hasUpgrade 为真时 root 必非空（位图写在根层）；这里只是防御，不在读路径建档（R53c）
            return;
        }
        // ---- F1 双分支：会话在场 ⇒ 会话模型；缺席 ⇒ 一次性 NBT 读改写（每扫一次，禁每实体一次） ----
        final UUID uuid = player.getGameProfile() == null ? null
            : player.getGameProfile()
                .getId();
        final PocketSession session = uuid == null ? null : PocketSessions.peek(uuid);
        final boolean viaSession = session != null && session.carrierStack() == stack;
        final PocketInventory oneshot = viaSession ? null : PocketInventory.readFrom(root);
        boolean storedAny = false;
        for (EntityItem drop : nearby) {
            if (drop == null || !drop.isEntityAlive() || drop.delayBeforeCanPickup > 0) {
                // Botania 口径：非死亡、拾取延迟已过，二者任一不满足都不碰
                continue;
            }
            final ItemStack content = drop.getEntityItem();
            if (content == null || content.stackSize <= 0) {
                continue;
            }
            final int want = content.stackSize;
            // 入袋优先：两个分支都不改写入参栈（depositIntoStorage 内部 copy；depositItem 用 sizedCopy），
            // 余量由返回值算出
            final int moved = viaSession ? session.depositItem(content) : oneshot.depositIntoStorage(content);
            storedAny |= moved > 0;
            int left = want - moved;
            if (left > 0) {
                // 溢出回退 1/2：玩家背包。★R84 教训（wiki addItemStackToInventory 条目）：
                // 返回 true ⇔ 入参 stackSize 被清零——按 stackSize 剩余判断，不依赖返回值
                final ItemStack rest = content.copy();
                rest.stackSize = left;
                player.inventory.addItemStackToInventory(rest);
                if (rest.stackSize > 0) {
                    // 溢出回退 2/2：掉玩家脚下（toss 带 pickup 延迟，见类注释）
                    player.dropPlayerItemWithRandomChoice(rest, false);
                }
            }
            // 吸收成功：账已全数落位（袋/背包/脚下），原实体必须摘除，否则就是复制
            drop.setDead();
        }
        if (!viaSession && storedAny) {
            // 无会话分支的一次性落盘：只有真的收进去了才写（readFrom → writeTo 与开关一次面板同构）
            oneshot.writeTo(root);
        }
    }
}
