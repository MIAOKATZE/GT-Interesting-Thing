package com.miaokatze.gtit.common.items.pocket.magnet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketMagnetFilter;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;

/**
 * 磁力升级（R95 {@code PocketUpgradeType#MAGNET}）的 <b>tick 宿主本体</b>：把玩家周围掉落物
 * 自动收进口袋。挂载点是 {@code ItemNekoDimensionPocket.onUpdate} 的服务端分支（★R95 门控放宽后
 * 背包 36 格任意位都推进，本类自带开关早退与节拍闸）。
 * <p>
 * <b>★R96 S6 将落点模型从"原地搬空 + {@code setDead}"换成 DE 物品错位器的两段式（候选 B）</b>：
 * <ol>
 * <li><b>位移拍</b>（每 {@link #SCAN_PERIOD_TICKS} 一拍）：不删实体、不入包，只做 DE 那三件事
 * （{@code motionX/Y/Z} 归零 + {@code delayBeforeCanPickup} 上拾取保护 + {@code setPosition} 到玩家腰带处），
 * 并把实体 id 记进 {@link PocketMagnetClaims}。物品就此"跳到你身上"，客户端至少看到一帧它在你跟前。</li>
 * <li><b>收口拍</b>（位移后的第 {@link #HOVER_TICKS}+1 拍，每拍问一次认领表）：按实体 id
 * {@code getEntityByID} 取回来（<b>不再扫 AABB</b>），入账与 {@code setDead()} <b>同一拍</b>做完，
 * 部分成交的余量写回原实体留在原地。</li>
 * </ol>
 * 必须跨拍的依据（不是偏好）：服务端同一拍 {@code setPosition} + {@code setDead} 客户端什么都看不见
 * ——实体本来就在视野里，服务端只发一条移除包，位置更新不会单独成帧（{@code r96-eva2.md §2.3}）。
 * 纯 DE 位移（候选 A）会结构性砍掉"吸进口袋中栏"这一档，与需求正面冲突 ⇒ 才需要第二段的自家收口。
 * <p>
 * <b>落点顺序（★R96 P-11 改判，R95 的"三级兜底"作废）：只吸口袋（有活会话时中栏 → 玩家背包），
 * 塞不下就不吸</b>。R95 的"中栏 → 玩家背包 → 脚下"后两级已整体撤除：满载时 {@code depositItem} 返回 0 成交
 * ⇒ <b>不 {@code setDead}、不发 {@code ItemTossEvent}、不生成新实体</b>，只放手并进入
 * {@link #FULL_BACKOFF_SCANS} 次扫描的退避。旧形态是每 40 tick"吸进来 → 甩脚下"的死循环
 * （每轮一条 {@code ItemTossEvent} + {@code addStat(dropStat)} + 周围客户端看到一件物品被甩出来，
 * 见 {@code r96-eva2.md §2.5} 与 {@code EntityPlayer.java:845} 的 {@code delayBeforeCanPickup=40}）。
 * <p>
 * <b>吸收判据</b>：{@code EntityItem} 且非死亡、{@code delayBeforeCanPickup <= 0}（Botania 口径；
 * 本类自己位移过的实体天然被这一条挡住，不会同一轮重复认领）、未被 {@link #isProtectedEntity} 排除、
 * 未被任何口袋认领、<b>过得了 {@link PocketMagnetFilter} 的三态名单</b>（★R96 S7a：无限制恒放行 /
 * 白名单只放名单内 / 黑名单拦掉名单内，条目只到 {@code itemId + meta}，前提 P-11 不做 NBT 敏感匹配）、
 * 且<b>该物品的最近玩家就是本玩家</b>（多人公平，DE {@code Magnet.java:134,170-173} 同形）。
 * <p>
 * <b>★受保护实体（S6 验收 1，数据破坏级）</b>：{@code getEntitiesWithinAABB(EntityItem.class, …)}
 * <b>按类含子类</b>一起抓，而 AE2 的 {@code EntityGrowingCrystal}/{@code EntityChargedQuartz}/
 * {@code EntitySingularity}/{@code EntityFloatingItem} 全是 {@code EntityItem} 的子类，被抓到即灭实体 =
 * 直接吞掉正在成熟的水晶与单方块聚变现场。排除表 {@link #PROTECTED_ENTITY_CLASSES} <b>只做类名比较</b>
 * （照 DE {@code ModHelper.java:46-52} 的形状，但比它更稳：不引 AE2 类、零 {@code Class.forName} 失败分支、
 * 子类的子类也一并挡住）。阳性对照：{@code EntityItem} 直系的普通掉落物<b>必须</b>继续被处理，
 * 否则磁力直接失效（{@link #isProtectedClassName} 对 {@code net.minecraft.entity.item.EntityItem} 返 {@code false}）。
 * <p>
 * <b>成本口径（R95 裁定：多枚口袋各自扫，成本线性）</b>：每枚带 MAGNET 位且开关未关的口袋各自独立扫描，
 * N 枚口袋 = N 次 AABB；无实体时一次 AABB 查询即返回。★<b>R96 S7a 修正本段旧口径</b>：位移拍原先写的是
 * "<b>完全不碰 NBT</b>"，接了名单执法腿之后不再成立 —— 现在的准确读法是"<b>只在真的扫到掉落物的那一拍</b>
 * 读一次名单、建一个判定集合"（执法腿排在空 AABB 早退<b>之后</b> ⇒ 磁力的<b>常态</b>（周围没东西）仍是
 * 零 NBT 零分配）；★每扫一次一个集合，<b>禁</b>每实体构建（用例 {@code magnet_filter_one_scan_gate_per_scan}
 * 钉构建次数 = 扫描次数）。每拍的收口侧在无认领时是一次 {@code Map.get} + 判空，零分配零遍历。
 * 单扫位移上限 {@link #MAX_PULL_PER_SCAN}（EVA-2 §2.4 缺的第 1 条，Botania {@code ItemMagnetRing.java:111} 同形）。
 * <p>
 * <b>序列化上界 = 扫描率（R53c 口径）</b>：无会话分支的 {@code readFrom → writeTo} 一次读改写
 * <b>每扫至多一次</b>（同一批认领在同一拍到期，故收口拍也是每扫一次），且只有真的收进东西才 {@code writeTo}。
 * 认领状态<b>不落 NBT</b>（S6 明令禁止；丢掉一条认领的后果见 {@link PocketMagnetClaims} 类注释）。
 * <p>
 * <b>持久契约（F1 双分支，同 {@code PocketWorldFluidTap}）</b>——世界侧写入不得与驱动
 * {@code persistIdle}/关屏写入交错出双真相：
 * <ol>
 * <li><b>有活会话且承载同一枚栈</b>（{@code PocketSessions.peek(uuid).carrierStack() == 本栈}，
 * 按对象身份）：走会话模型 {@link PocketSession#depositItem}（中栏 → 背包，内部即
 * {@code PocketInventory.depositIntoStorage} + {@code moveToPlayer} 同一语义并置 dirty），落盘交给
 * 既有通路。<b>绝不直改这枚栈的 NBT</b>（双写竞争）。</li>
 * <li><b>无活会话（或会话承载的是另一枚口袋）</b>：一次性 NBT 读改写——
 * {@code PocketInventory.readFrom} → 逐条认领 {@code depositIntoStorage} → 收进过才 {@code writeTo}。</li>
 * </ol>
 * <p>
 * <b>本轮本片（S7a）已做 / 未做（各自的归属写清楚，免得被当成漏项或当成越项）</b>：
 * ★<b>名单进执法已接</b>（{@link PocketMagnetFilter} 三态 + 条目，判定腿在位移拍里，见 {@link #onItemTick}）；
 * <b>配置面</b>（72 格格件、NEI 与背包拖入"只记录不放置"、面板挂载、tooltip 与三态按钮）全归 <b>S7b</b>
 * （它还要等主干 S2 的配置面板挂载点）；"吸取目标两档"（P-11 的玩家主背包 36 格 / 口袋内 135 格栏）
 * 也不在本片；穿戴态的 {@code onWornTick} 宿主与 {@code runPassives} 抽取属 S11；
 * 潜行临时关（DE {@code MAGNET_SNEAK}）与自投三档（DE {@code SelfPickupMode}）不在需求内；
 * DE 的第二档半径 32 需要"按载体分档"的数值来源，本轮没有 ⇒ 半径仍单档 8。
 */
public final class PocketMagnetDriver {

    // ==== ★S6 验收 3：节拍与悬停拍数是<b>单一常量</b>（实机定值见计划 §8 V-10，代码侧不留散落的魔数） ====

    /** 扫描节拍（tick）：★R96 S6 由 10 改为 5，对齐 DE {@code Magnet.java:117} 同一节流档。 */
    private static final int SCAN_PERIOD_TICKS = 5;

    /** 位移到收口之间的悬停拍数：太小看不出"飞过来"，太大像 bug ⇒ 实机定值（V-10）。 */
    private static final int HOVER_TICKS = 2;

    /** 吸收半径（格，R95 裁定 8 格）：AABB 以玩家包围盒向三轴各扩这么多。 */
    private static final double SCAN_RANGE = 8.0;

    /** 位移期间压给实体的拾取延迟（tick）：挡住原版抢在我们前面收口，也保证认领丢了东西会回到"可捡"而不是永悬。 */
    private static final int PICKUP_HOLD_TICKS = 20;

    /** 满载退避的扫描次数：★P-11 满载不吸——一次 0 成交后连续这么多拍不做位移（掏空口袋后下一次收口即恢复）。 */
    private static final int FULL_BACKOFF_SCANS = 8;

    /** 单扫位移上限（GTNH 掉落物洪峰的止血阀；超出的留到下一拍扫描）。 */
    private static final int MAX_PULL_PER_SCAN = 32;

    /**
     * 腰带高度相对眼高的下移量（DE {@code Magnet.java:188-191} 的 {@code playerEyesPos - 0.62}，
     * 即脚上方约 1 格）——物品出现在玩家身体上才谈得上"飞到我身上"。
     */
    private static final double BELT_DROP_FROM_EYES = 0.62;

    /** 位移与收口各自的轻音效（DE {@code Magnet.java:195-201} 同音源同音量档；音量 0.1 很轻）。 */
    private static final String PULL_SOUND = "random.orb";

    private static final float PULL_SOUND_VOLUME = 0.1F;

    // ==== ★S6 验收 1：受保护实体排除表（按类名，零 AE2 依赖） ====

    /**
     * 不得被磁力触碰的实体类全名。四条都是 {@code EntityItem} 的子类，被抓到即 {@code setDead} 就是数据破坏：
     * {@code EntityGrowingCrystal} 靠 {@code age} 慢慢成熟、{@code EntityChargedQuartz}/
     * {@code EntitySingularity} 是充能与单方块聚变现场、{@code EntityFloatingItem} 是 AE2 自家的浮空展示件
     * （DE 也把 {@code ItemCrystalSeed} 写进出厂黑名单，理由同源）。
     */
    private static final String[] PROTECTED_ENTITY_CLASSES = { "appeng.entity.EntityGrowingCrystal",
        "appeng.entity.EntityChargedQuartz", "appeng.entity.EntitySingularity", "appeng.entity.EntityFloatingItem" };

    /** 收口侧复用的到期缓冲（★热路径零分配：只在服务器主线程、每次用完即 {@code clear()}）。 */
    private static final ArrayList<PocketMagnetClaims.Claim> RIPED = new ArrayList<>();

    private PocketMagnetDriver() {}

    /**
     * 磁力驱动的一拍（<b>仅服务端</b>由宿主调用；客户端路径在宿主已早退，这里再判一次是零成本双保险）。
     * <p>
     * 顺序（★S6 起分两拍，宿主仍是同一个 {@code Item.onUpdate}，<b>不</b>挪 {@code ServerTickEvent}）：
     * 客户端与空参早退 → MAGNET 开关早退 → <b>收口拍</b>（每拍一次，无认领时零成本）→ 节拍闸 →
     * 满载退避 → AABB 扫 → 无实体零开销返回 → <b>每扫一次</b>读名单建一个判定上下文（★S7a，排在空早退之后）→
     * 逐条判据（含名单）→ 位移 + 登记认领 → 轻音效。
     * <p>
     * ★节拍闸是<b>无状态取模闸</b>不是倒计时：不持有任何跨 tick 的到期字段，跨维重建导致的相位跳变至多把
     * 某一拍推迟一拍（R59e 要消灭的"写死的绝对到期"形态在这里结构性不存在）。
     * <p>
     * ★R96 S1：早退判据是组合谓词 {@code PocketUpgradeSwitches.isActive}（位图 ∧ ¬off-mask）——磁五是
     * 五位之一，玩家关掉开关必须让这一拍真的早退。
     */
    public static void onItemTick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || player == null || stack == null) {
            return;
        }
        if (!PocketUpgradeSwitches.isActive(stack, PocketUpgradeType.MAGNET)) {
            return;
        }
        final UUID owner = ownerId(player);
        if (owner == null) {
            return;
        }
        // ---- 第一段：每拍收口（上一拍位移过的东西在这里落袋；无认领 ⇒ 一次判空，零分配零 NBT） ----
        settleClaimed(stack, world, player, owner);
        // ---- 第二段：位移拍 ----
        if (player.ticksExisted % SCAN_PERIOD_TICKS != 0) {
            return;
        }
        if (PocketMagnetClaims.isScanCooling(owner)) {
            // ★P-11 满载不吸：退避期间连 AABB 都不扫（连"跳一下"都不发生，才是真的不吸）
            return;
        }
        @SuppressWarnings("unchecked")
        final List<EntityItem> nearby = world
            .getEntitiesWithinAABB(EntityItem.class, player.boundingBox.expand(SCAN_RANGE, SCAN_RANGE, SCAN_RANGE));
        if (nearby == null || nearby.isEmpty()) {
            // 无实体早退：零 NBT 读写、零分配（磁力的常态开销就是一次空 AABB 查询 / SCAN_PERIOD_TICKS）
            return;
        }
        // ★位移拍的唯一一次 NBT 读数，且★排在空 AABB 早退之后（磁力的常态是周围没东西 ⇒ 那一拍仍然零读零分配）。
        // ★每扫一次建一个判定上下文（一次集合复制），禁每实体构建 —— PocketMagnetFilter#newScanGate 的类注释。
        final PocketMagnetFilter.ScanGate gate = PocketMagnetFilter.readFrom(stack.getTagCompound())
            .newScanGate();
        // ★位移拍不再"一次 NBT 都不读"（S6 的旧口径，S7a 起改为"每扫至多一次"）：入账整段仍在收口拍
        final boolean soloWorld = world.playerEntities.size() < 2;
        final double beltY = player.posY + player.getEyeHeight() - BELT_DROP_FROM_EYES;
        int pulled = 0;
        for (EntityItem drop : nearby) {
            if (pulled >= MAX_PULL_PER_SCAN) {
                break;
            }
            if (drop == null || !drop.isEntityAlive() || drop.delayBeforeCanPickup > 0) {
                // Botania 口径：非死亡、拾取延迟已过。★后者同时挡住"我们上一拍刚位移过的那件"
                continue;
            }
            if (isProtectedEntity(drop)) {
                // ★验收 1：AE2 一族按类名免疫（放在一切动作之前，挡住 setDead 那条灭实体路径）
                continue;
            }
            if (PocketMagnetClaims.isClaimed(drop.getEntityId())) {
                // 已被别的口袋／别的玩家认领：不重复位移，也不抢别人的收口
                continue;
            }
            final ItemStack content = drop.getEntityItem();
            if (content == null || content.stackSize <= 0) {
                continue;
            }
            if (!gate.allows(Item.getIdFromItem(content.getItem()), content.getItemDamage())) {
                // ★R96 S7a 验收 1（名单进执法）：白名单外 / 黑名单内 ⇒ 整件跳过 —— 不位移、不认领，
                // 因此收口拍根本见不到它（也就不会退回 S6 刚修掉的"吸进来再甩脚下"）。
                // ★排在多人仲裁之前：这里是一次 long 装箱查找，而 getClosestPlayerToEntity 要遍历玩家。
                // ★无限制档（NONE）恒真：名单在场但全部放行（见 PocketMagnetFilter 类注释三态读法）。
                continue;
            }
            if (!soloWorld && world.getClosestPlayerToEntity(drop, SCAN_RANGE) != player) {
                // ★验收 4 多人公平：只吸"该物品的最近玩家就是我"的那些（单人短路，DE Magnet.java:134 同形）
                continue;
            }
            // DE Magnet.java:182-192 的三件事：归零 motion + 上拾取保护 + 挪到腰带处。不删实体、不入包。
            drop.motionX = 0.0D;
            drop.motionY = 0.0D;
            drop.motionZ = 0.0D;
            drop.delayBeforeCanPickup = PICKUP_HOLD_TICKS;
            drop.setPosition(
                player.posX - 0.2D + world.rand.nextDouble() * 0.4D,
                beltY,
                player.posZ - 0.2D + world.rand.nextDouble() * 0.4D);
            PocketMagnetClaims.claim(owner, drop.getEntityId(), stack, HOVER_TICKS);
            pulled++;
        }
        if (pulled > 0) {
            // 轻音效：服务端调用即广播给周围客户端（DE 同形；音量 0.1，比原版拾取更不吵）
            world.playSoundAtEntity(player, PULL_SOUND, PULL_SOUND_VOLUME, pullPitch(world));
        }
    }

    /**
     * 收口拍：把到期的认领按实体 id 取回来，<b>入账与 {@code setDead()} 同一拍</b>做完。
     * <p>
     * ★满载那一支是整个缺陷修复的落点：{@code moved <= 0} ⇒ 不 {@code setDead}、不发 {@code ItemTossEvent}、
     * 不生成新实体、不改实体内容，只摘掉认领条目并登记退避。这样"同一实体被连续扫到 N 次而件数不变、
     * 也没有新实体生成"（R95 的 40 tick 死循环正是靠新实体 + toss 风暴可证伪的）。
     */
    private static void settleClaimed(ItemStack stack, World world, EntityPlayer player, UUID owner) {
        RIPED.clear();
        if (PocketMagnetClaims.ripeClaims(owner, stack, RIPED) == 0) {
            return;
        }
        final NBTTagCompound root = stack.getTagCompound();
        final PocketSession session = PocketSessions.peek(owner);
        final boolean viaSession = session != null && session.carrierStack() == stack;
        if (root == null && !viaSession) {
            // 无处可写也不硬写（R53c 禁在读路径建档）：条目直接放手，实体回到"可被原版捡"
            releaseAll(owner);
            return;
        }
        PocketInventory oneshot = null;
        int landed = 0;
        for (int i = 0, n = RIPED.size(); i < n; i++) {
            final PocketMagnetClaims.Claim claim = RIPED.get(i);
            final int entityId = claim.entityId();
            final Entity entity = world.getEntityByID(entityId);
            if (!(entity instanceof EntityItem) || !entity.isEntityAlive()) {
                // 实体已被别人收走／卸载／换维：条目自然淘汰，绝不凭空造件
                PocketMagnetClaims.release(owner, entityId);
                continue;
            }
            final EntityItem drop = (EntityItem) entity;
            final ItemStack content = drop.getEntityItem();
            if (content == null || content.stackSize <= 0) {
                PocketMagnetClaims.release(owner, entityId);
                continue;
            }
            if (!viaSession && oneshot == null) {
                // 每扫至多一次读改写（同一批认领同一拍到期），禁每实体一次
                oneshot = PocketInventory.readFrom(root);
            }
            final int want = content.stackSize;
            final int moved = viaSession ? session.depositItem(content) : oneshot.depositIntoStorage(content);
            PocketMagnetClaims.release(owner, entityId);
            if (moved <= 0) {
                // ★P-11 满载不吸：一件都没收下 ⇒ 实体留在原地（不 setDead / 不 toss / 不建新实体），并进退避
                PocketMagnetClaims.noteScanBackoff(owner, FULL_BACKOFF_SCANS);
                continue;
            }
            PocketMagnetClaims.noteProductive(owner);
            if (moved < want) {
                // 部分成交：余量写回实体留在原地（原版拾取也是这个语义）。★不 setDead ⇒ 绝不吃掉差额
                content.stackSize = want - moved;
                drop.delayBeforeCanPickup = PICKUP_HOLD_TICKS;
            } else {
                // 账已全数落袋：同一拍摘除原实体，否则就是复制
                player.onItemPickup(drop, moved);
                drop.setDead();
            }
            landed += moved;
        }
        if (landed <= 0) {
            return;
        }
        world.playSoundAtEntity(player, PULL_SOUND, PULL_SOUND_VOLUME, pullPitch(world));
        if (!viaSession) {
            // 无会话分支的一次性落盘：只有真的收进去了才写（readFrom → writeTo 与开关一次面板同构）
            oneshot.writeTo(root);
        }
    }

    /** 放手全部认领（防御支：无处可写时把在飞的东西还给世界，不留着当垃圾）。 */
    private static void releaseAll(UUID owner) {
        for (int i = 0, n = RIPED.size(); i < n; i++) {
            PocketMagnetClaims.release(
                owner,
                RIPED.get(i)
                    .entityId());
        }
    }

    /** 轻音效的随机音高（DE {@code Magnet.java:195-201} 的"抖一下"口径，本仓先例 {@code MailHandler.java:91}）。 */
    private static float pullPitch(World world) {
        return 1.0F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F;
    }

    /**
     * ★S6 验收 1 的判据本体：<b>只做类名字符串比较</b>，沿父类链走到 {@link Entity} 为止。
     * <p>
     * 比 DE 的 {@code Class.forName} + {@code isInstance} 更稳的三点：① 零 {@code Class.forName}
     * 失败分支（AE2 缺席时那一支会 NPE，DE 靠 {@code isAE2Installed} 兜，我们连分支都不需要）；
     * ② 排除类的<b>子类</b>也一并挡住（父类链命中）；③ 判据纯字符串 ⇒ 可在无 MC 运行时的 JVM 里被用例直接驱动。
     */
    public static boolean isProtectedEntity(Entity entity) {
        return entity != null && isProtectedClass(entity.getClass());
    }

    /**
     * 父类链判据本体（★用例可以直接拿 {@code Class} 驱动，不需要构造实体：
     * {@code isProtectedClass(EntityItem.class)} 必须 {@code false} = 阳性对照，
     * {@code isProtectedClass(appeng 那四类)} 必须 {@code true}）。
     */
    public static boolean isProtectedClass(Class<?> clazz) {
        for (Class<?> cursor = clazz; cursor != null && cursor != Entity.class; cursor = cursor.getSuperclass()) {
            if (isProtectedClassName(cursor.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 排除表本体（★阳性对照：{@code net.minecraft.entity.item.EntityItem} 必须返 {@code false}，否则磁力形同失效）。 */
    public static boolean isProtectedClassName(String className) {
        if (className == null) {
            return false;
        }
        for (String protectedClass : PROTECTED_ENTITY_CLASSES) {
            if (protectedClass.equals(className)) {
                return true;
            }
        }
        return false;
    }

    /** 排除表的只读副本（用例与实机核验用；<b>不</b>暴露可写的 {@code List}，防被当成配置面往里塞）。 */
    public static String[] protectedEntityClasses() {
        return PROTECTED_ENTITY_CLASSES.clone();
    }

    /** 会话表的键（与 {@code PocketSessions}／{@code PocketChannelManager} 同一玩家维键；无档案 ⇒ null 早退）。 */
    private static UUID ownerId(EntityPlayer player) {
        return player.getGameProfile() == null ? null
            : player.getGameProfile()
                .getId();
    }
}
