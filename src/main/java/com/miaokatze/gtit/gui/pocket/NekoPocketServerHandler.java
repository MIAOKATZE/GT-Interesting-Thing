package com.miaokatze.gtit.gui.pocket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;
import com.miaokatze.gtit.common.items.pocket.PocketAeChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketBindFlow;
import com.miaokatze.gtit.common.items.pocket.PocketCellBindings;
import com.miaokatze.gtit.common.items.pocket.PocketCellProbe;
import com.miaokatze.gtit.common.items.pocket.PocketChannelManager;
import com.miaokatze.gtit.common.items.pocket.PocketChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketChannelRunner;
import com.miaokatze.gtit.common.items.pocket.PocketChannelState;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceIntake;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

/**
 * ★R90 T2：猫猫次元口袋面板的<b>服务端执行体</b>（从 {@link NekoPocketPanel} 剥离，对齐
 * {@code PocketEssenceIntake} 独立类先例——Panel 持本类实例并委托）。
 * <p>
 * <b>本类拥有</b>：全部 {@code perform*} 动作实现（取出/整理/通道/绑定/解绑/源质出入）、
 * ghost 请求的服务端执行体、R19 三层防伪的 L1 会话绑定判据，以及 {@code PocketSession}
 * 驱动面的实现体（Panel 上的 {@code @Override} 群逐条一行委托到此处，接口身份不变，
 * {@code PocketSessions.register(panel)} 语义不动）。
 * <p>
 * <b>刻意留在 Panel 的执行体</b>（★不是遗漏，是离线套件源码机检钉住的位置）：
 * {@code onServerAction} 的分发 switch（用例锚定 {@code case ACTION_ESSENCE_OUT_TO_PHIAL:}）、
 * {@code performEssenceOutToPhial} + {@code newPhialStack}（用例锚定方法体守卫与单源声明）、
 * {@code writeSessionToCarrier} + {@code rollbackEssenceToPersistedBaseline} + {@code isNestedInOwnStorage}
 * （用例锚定终态回滚接线）、{@code moveToPlayer}/{@code movedByVanillaContract}/{@code drainEssence}
 * （用例锚定方法体）。这些方法只经由本类同包调用，不因位置不变而耦合回退。
 * <p>
 * <b>依赖方向</b>：本类住在 {@code gui/pocket}（而非 common），因为它要读的
 * {@code PocketInventory}/{@code PocketSlots} 在 T3 之前仍在 gui 包——迁 common 会重建
 * T1 刚拆掉的反向 import；T3 迁包时本类随之迁。
 * <p>
 * <b>并发时序</b>：所有入口仍由 Panel 的 C2S 钩子经 {@code ServerTaskScheduler} 投递到
 * 服务器主线程后才到达这里（R71），本类自身不做任何投递；对 Panel 状态的访问全部走
 * 包内访问器（{@code inventory()/syncManager()/player()/pocketStack()} 等），不复制、不缓存。
 */
final class NekoPocketServerHandler {

    /** 宿主面板（装配壳 + 唯一状态源；本类只持引用，不持任何副本字段）。 */
    private final NekoPocketPanel panel;

    NekoPocketServerHandler(NekoPocketPanel panel) {
        this.panel = panel;
    }

    // ------------------------------------------------------------------ 动作实现

    /** 语义②「口袋 → 玩家背包」：逐格尝试入包，装不下就留在原地（不放地上，避免误丢）。 */
    void performTakeOut() {
        final EntityPlayer player = panel.player();
        if (player == null) {
            return;
        }
        boolean moved = false;
        for (int index = 0; index < panel.inventory()
            .storage()
            .getSlots(); index++) {
            final ItemStack stack = panel.inventory()
                .storage()
                .getStackInSlot(index);
            if (stack == null) {
                continue;
            }
            if (player.inventory.addItemStackToInventory(stack.copy())) {
                panel.inventory()
                    .storage()
                    .setStackInSlot(index, null);
                moved = true;
            }
        }
        if (moved) {
            panel.inventory()
                .markDirty();
        }
    }

    /** 语义①「整理中栏 135 格」：合并同类 + 前移紧凑。 */
    void performSort() {
        final int size = panel.inventory()
            .storage()
            .getSlots();
        final ItemStack[] snapshot = new ItemStack[size];
        for (int index = 0; index < size; index++) {
            // ★R85 A5：声明格<b>整格不参与整理</b>——它现在就是那条需求的落点（R84），里面的东西是"已经补到的
            // 产物"。旧写法把 135 格全扫进合并池、回写时又跳过 ghost 格 ⇒ 玩家点一次"整理"就把产物搬去别处、
            // 需求格重新变空，而且下面那条"非 ghost 槽数 ≥ 条目数"的不变量在声明格带货时根本不成立
            // （溢出走 giveToPlayer ⇒ 会把物品喷到脚下）。产物留在原格，其余照旧合并前移。
            if (panel.inventory()
                .isGhostItemSlot(index)) {
                continue;
            }
            final ItemStack stack = panel.inventory()
                .storage()
                .getStackInSlot(index);
            if (stack != null) {
                snapshot[index] = stack.copy();
                panel.inventory()
                    .storage()
                    .setStackInSlot(index, null);
            }
        }
        final Map<String, ItemStack> merged = new LinkedHashMap<>();
        for (ItemStack stack : snapshot) {
            if (stack == null) {
                continue;
            }
            final String key = mergeKey(stack);
            final ItemStack existing = merged.get(key);
            if (existing == null) {
                merged.put(key, stack);
                continue;
            }
            final int free = existing.getMaxStackSize() - existing.stackSize;
            final int take = Math.min(free, stack.stackSize);
            if (take > 0) {
                existing.stackSize += take;
                stack.stackSize -= take;
            }
            if (stack.stackSize > 0) {
                merged.put(key + "#" + merged.size(), stack);
            }
        }
        // ★ghost 格不参与整理，也<b>不是</b>落点池（R85 A5 起更进一步：连快照都不进，产物原地不动）：
        // 判据读服务端的 PocketFilterConfig，不读 widget 状态（widget 双端各一份，把显示层当真相即 bug）。
        // 因为声明格的货不再进合并池，"非 ghost 槽数 ≥ 池内条目数"这条不变量重新成立；
        // 仍保留兜底：万一存量档里 ghost 槽带着东西进来、或池内条目确实多于非 ghost 槽，走玩家背包而不是 break 掉（整理绝不吃件）。
        int cursor = 0;
        for (ItemStack stack : merged.values()) {
            cursor = nextRealSlot(cursor);
            if (cursor >= size) {
                panel.giveToPlayer(stack);
                continue;
            }
            panel.inventory()
                .storage()
                .setStackInSlot(cursor++, stack);
        }
        panel.inventory()
            .markDirty();
    }

    /** 从 {@code from} 起的第一个<b>非 ghost</b> 中栏槽号（越界返回 {@code slots} 本身）。 */
    private int nextRealSlot(int from) {
        return panel.inventory()
            .nextSortableStorageSlot(from);
    }

    private static String mergeKey(ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        return Item.getIdFromItem(stack.getItem()) + "@"
            + stack.getItemDamage()
            + "#"
            + (tag == null ? "" : tag.toString());
    }

    /**
     * 通道按钮的服务端入口（S6 / R64c + R14 扣费顺序）。
     * <p>
     * <b>顺序是判据，不是风格</b>（R14）：点检（识别 → 冷却 → 余额）→ {@code tryDeduct} → 传输；
     * 任一点检不过就直接返回，<b>一分都不扣</b>。扣完之后传输全失败 ⇒ 同回执退款；
     * 部分失败不退（"搬走的东西已经搬走了"，退款等于白送一次）。
     * <p>
     * ⚠ 调用 {@code tryDeduct} <b>之前</b>必须断言 {@code cost > 0}：
     * {@code NekoWallet.tryDeduct} 的 {@code if (amount <= 0) return true;} 会让"零成本扣费"
     * 静默成功（R58b 点名的陷阱）。本方法里两个成本都来自 {@code PocketConstants}，
     * 一旦有人把它们改成 0，这条断言就是唯一的止损线。
     */
    void performChannelRequest(PocketChannelState.Mode mode) {
        if (!serverGuardOk()) {
            return;
        }
        final UUID uuid = panel.playerId();
        final EntityPlayer target = panel.player();
        if (uuid == null || target == null) {
            return;
        }
        final PocketCellBindings bindings = panel.inventory()
            .bindings();
        final PocketChannelOps ops = new PocketAeChannelOps(target, panel.pocketStack(), panel);
        // 点检 1：识别（R6/R64c）—— 至少一枚绑定元件当前"在带电驱动器/ME 箱里且有可用通道"。
        // getCellArray 全空 ⇒ 拒绝开道并回独立回执码，客户端那侧的置灰只是体验层。
        if (bindings.isEmpty() || !anyRecognised(bindings, ops)) {
            panel.putReceipt("gtit.pocket.receipt.unrecognised", 0);
            return;
        }
        final boolean burst = mode == PocketChannelState.Mode.BURST;
        final int cost = burst ? PocketConstants.BURST_COST_NEKO : PocketConstants.SHORT_COST_SHIMMERING_NEKO;
        final String currency = burst ? NekoCurrencyRegistrar.NEKO_ID : NekoCurrencyRegistrar.SHIMMERING_NEKO_ID;
        if (cost <= 0) {
            // ★tryDeduct 的 amount<=0 会直接 return true ⇒ 宁可不开道也不做"零成本扣费"
            GTInterestingThing.LOG.warn("[pocket] 通道成本非正（mode={}, cost={}），拒绝开道", mode, cost);
            panel.putReceipt("gtit.pocket.receipt.nothing_to_do", 0);
            return;
        }
        // 点检 2：冷却（R16 双维墙钟；只有瞬时通道有冷却）
        final ItemStack pocket = panel.pocketStack();
        final NBTTagCompound root = pocket == null ? null : pocket.getTagCompound();
        final long cooldown = burst
            ? PocketChannelManager.burstRemaining(PocketChannelManager.INSTANCE.peek(uuid), root, ops.nowMs())
            : 0L;
        if (cooldown > 0L) {
            // ★R85 小项 4：这里<b>不</b>再把秒数当实参传下去（旧写法传了，而两份 lang 的
            // {@code gtit.pocket.receipt.cooldown} 里根本没有占位 ⇒ 那个数字算出来、下发到客户端、
            // 永远不显示，是一条纯粹的假读数）。
            // 选"删实参"而不是"文案加 %d"的理由是口径唯一性：本行的回执是<b>粘性</b>的（一直显示到
            // 下一次动作覆盖），而冷却秒数已经有一条每拍刷新的权威显示面（{@code composeRemain()} →
            // {@code applyRemainState} → 状态行的 {@code channel.instant.remain}）。把秒数塞进粘性回执
            // 等于给同一个数造第二个读数，而且第二个会<b>冻在点击那一刻</b>（"还剩 9 秒"挂满 10 秒），
            // 与旁边那条倒计时互相打脸 ⇒ 冷却"有"这件事走本行文案，冷却"还剩几秒"只走状态行那一处。
            panel.putReceipt("gtit.pocket.receipt.cooldown", 0);
            return;
        }
        // 点检 3：余额（团队钱包由 NekoWalletManager 内部路由，这里只认"付钱主体是这个人"）
        final NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(uuid);
        if (wallet == null || wallet.getCount(currency) < cost) {
            panel.putReceipt("gtit.pocket.receipt.no_funds", cost);
            return;
        }
        if (!wallet.tryDeduct(currency, cost)) {
            panel.putReceipt("gtit.pocket.receipt.no_funds", cost);
            return;
        }
        // 传输
        final boolean accepted = PocketChannelManager.INSTANCE.openChannel(
            uuid,
            mode,
            bindings,
            panel.inventory()
                .filters(),
            root,
            ops,
            PocketChannelManager.pairsPerBatchFromConfig());
        if (!accepted) {
            refund(wallet, currency, cost);
            panel.putReceipt(burst ? "gtit.pocket.receipt.cooldown" : "gtit.pocket.receipt.nothing_to_do", 0);
            return;
        }
        if (burst) {
            final PocketChannelState state = PocketChannelManager.INSTANCE.peek(uuid);
            final PocketChannelRunner.Report report = state == null ? null : state.lastReport();
            if (report == null || (report.transferred <= 0 && report.failures() <= 0)) {
                // 全失败（一件都没动、也没有可解释的失败）⇒ 退款（R14"全失败同回执退"）
                refund(wallet, currency, cost);
                panel.putReceipt("gtit.pocket.receipt.nothing_to_do", 0);
            } else {
                panel.putReceipt(NekoPocketPanel.receiptOfReport(report), report.transferred, report.failures());
            }
            // burst 的 work 位与显示窗一起开（5 秒 = BURST_SHOW_TICKS tick，R37/R62：动画走倒计时）
            ItemNekoDimensionPocket
                .startWorkAnimation(pocket, PocketConstants.BURST_SHOW_TICKS, PocketConstants.BURST_SHOW_TICKS);
        }
        // 短效通道：剩余秒数由 composeRemain 走 NBT 倒计时回显，无需即时回执（模式行同时刷新）
    }

    /** 至少一枚绑定元件"被识别"（R6 的 getCellArray 非空口径，不缓存结论、不看 isPowered）。 */
    private static boolean anyRecognised(PocketCellBindings bindings, PocketChannelOps ops) {
        for (String id : bindings.cells()) {
            if (ops.isCellLost(id)) {
                continue;
            }
            final List<String> channels = ops.channelIdsOf(id);
            if (channels != null && !channels.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void refund(NekoWallet wallet, String currency, int cost) {
        if (wallet != null && cost > 0) {
            wallet.addCount(currency, cost);
        }
    }

    /**
     * 绑定键（R40a/R43a：只读 {@code diskuuid}，原栈<b>非消耗</b>地放回玩家处）。
     * <p>
     * 绑定时元件在手里 ⇒ 此刻必然尚未识别 ⇒ 位置五键一律写 {@link PocketConstants#UNLOCATED}
     * （R40b 的正常态，文案 {@code bind.unlocated} 不得写成失败语气）。位置快照此后只由
     * {@code PocketCellProbe} 经 {@link PocketCellBindings#recordLocation} 回填
     * （S1fix 风险⑥：误用 7 参 {@code bind(...)} 回填会把已有定位抹掉）。
     * <p>
     * ★★<b>R81①②：「绑定只能绑定一个」的修复点就在这一小段</b>。旧实现把
     * {@code PocketCellProbe.cellUuid()} 的 {@code null} 当成一件事读（"格子里没放元件"），
     * 而 {@code diskuuid} 是<b>服务端首次取用时惰性分配</b>的 ⇒ 一枚从未进过驱动器的新元件
     * 身份读出来也是 {@code null}，于是被发回一句「把元件放入此格」（格子里明明躺着元件）——
     * 反向误导，玩家换个位置再试还是失败。现在：
     * <ol>
     * <li>四态判定与 {@code bind()} 的<b>返回值</b>（旧代码丢掉的就是它）一起收在
     * {@link PocketBindFlow#bind} 里，每种结果各回一条自己的 lang 回执（含"同身份重绑"，
     * 旧口径完全静默）；</li>
     * <li>身份为空时由 {@link PocketCellProbe#bindIdentity} 在<b>服务端</b>调一次现成公开 API
     * {@code StorageManager.getStorage(ItemStack)} 就地物化身份后<b>重读</b>（★不改
     * {@code allocateOrReadUuid} 的可见性、不新增分配逻辑、客户端一个字节都不写）；</li>
     * <li>只有真的写进表（{@code ADDED} / {@code DUPLICATE} 刷新了位置快照）才 {@code markDirty}
     * 并把元件退回玩家；表满或无身份时<b>原栈留在格内</b>由玩家自己拿走（R40a 的"绝不吃元件"）。</li>
     * </ol>
     */
    void performBind() {
        if (!serverGuardOk()) {
            return;
        }
        // serverGuardOk() 已经挡掉客户端（第一句就是 isClient），这里再取一次显式值，
        // 好让"物化只在服务端"这一跳在代码上读得出来，而不是靠调用点的注释。
        final boolean server = !panel.syncManager()
            .isClient();
        final ItemStack candidate = panel.inventory()
            .bindSlot()
            .getStackInSlot(0);
        final PocketBindFlow.Result result = PocketBindFlow.bind(
            panel.inventory()
                .bindings(),
            PocketCellProbe.isCell(candidate),
            PocketCellProbe.bindIdentity(candidate, server),
            server);
        panel.putReceipt(
            PocketBindFlow.langKeyOf(result),
            panel.inventory()
                .bindings()
                .size());
        if (result != PocketBindFlow.Result.ADDED && result != PocketBindFlow.Result.DUPLICATE) {
            // 没写进表（非元件 / 无身份 / 表满）：什么都不动，元件留在绑定格里由玩家自己拿走
            return;
        }
        panel.inventory()
            .markDirty();
        returnToPlayerFromBindSlot(candidate);
        // ★R81①：成功也发回执（bind.added）。旧口径"读数变了即是反馈"在缺陷现场不成立——
        // 玩家就是因为看不出条数有没有在涨才报"只能绑一个"的，每一次新增都必须能读到一个词。
        // 同身份重绑走 bind.dup（旧代码丢掉 bind() 返回值 ⇒ 完全静默，R81 点名的次因）。
    }

    /** R40a：绑定格里的元件原样退回（背包满则掉脚下），绝不"吃掉"元件。 */
    private void returnToPlayerFromBindSlot(ItemStack stack) {
        panel.inventory()
            .bindSlot()
            .setStackInSlot(0, null);
        if (stack != null) {
            panel.giveToPlayer(stack);
        }
    }

    /**
     * 解绑<b>最后一条</b>（R74 裁定：绑定按钮右键）。与 ghost 格的右键解绑仍是两套实现
     * （前者摘绑定表的元件身份、后者撤一格 ghost 声明），<b>不得合并</b>。
     * <p>
     * 只从绑定表摘除：元件本身一直在 AE 网络里，解绑不动它的内容，也不动 {@code PocketCellProbe}
     * 的观测表。空表时发"尚未绑定元件"回执（玩家侧必须知道这一下点空了，R10）。
     */
    void performUnbindLast() {
        if (!serverGuardOk()) {
            return;
        }
        final PocketCellBindings bindings = panel.inventory()
            .bindings();
        if (!bindings.unbindLast()) {
            panel.putReceipt("gtit.pocket.bind.none", 0);
            return;
        }
        panel.inventory()
            .markDirty();
    }

    /**
     * 解绑<b>全部</b>（R74 裁定：绑定按钮 Shift + 右键）。
     * <p>
     * 与 {@link #performUnbindLast()} 共用同一条"只摘表、不动元件、不动观测表"的口径；
     * 空表同样给回执，免得玩家以为"没反应 = 清空失败"。
     */
    void performUnbindAll() {
        if (!serverGuardOk()) {
            return;
        }
        final PocketCellBindings bindings = panel.inventory()
            .bindings();
        if (bindings.isEmpty()) {
            panel.putReceipt("gtit.pocket.bind.none", 0);
            return;
        }
        final int cleared = bindings.size();
        bindings.clear();
        panel.inventory()
            .markDirty();
        panel.putReceipt("gtit.pocket.bind.cleared", cleared);
    }

    /**
     * 源质格取出（需求 2 末句「要素栏里的要素拿出来」这一条独立路径，R15；★R88 起搬运件是瓶）。
     * <p>
     * ★<b>R86 改口径</b>（实机 6 条之 1，向玩家对物品槽的信念对齐）：<b>左键＝把「该组」拿到鼠标游标上</b>
     * （一组至多 {@link PocketConstants#ESSENCE_OUT_MAX_POINTS_PER_ACTION} 点，游标上已有东西则整笔不动
     * 并给回执）；<b>Shift+左键＝该格整份一次进背包</b>（至多单格上限，背包优先、余量落中栏）。
     * 旧口径是"左键 1 点到背包、Shift 64 点到背包"，两边都往背包塞，玩家拿不到手上那一叠。
     * <p>
     * ★★<b>R88 载体改判 + 自立口径 C1（最小粒度＝一瓶）</b>：搬运件是 <b>TC 安瓿瓶</b>
     * （{@code TaumCompat#newFilledContainer}），单瓶容量 =
     * {@value TaumDistillRules#PHIAL_CAPACITY} 点（取出侧读 E1 落地的粒度单源
     * {@link PocketConstants#ESSENCE_OUT_UNIT_POINTS}），<b>不是</b>旧口径的「1 枚晶 = 1 点」。于是取出量
     * <b>向下取整到一瓶的整数倍</b>，凑不满一瓶的<b>余数原地留盘</b>并走面板回执（★不进聊天框，见
     * {@link #performEssenceIntake} 顶部那条同裁定）。换算后的动作上界由 E1 的两条派生常量给出：
     * 左键一次至多 {@link PocketConstants#ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 只瓶，一格存满
     * （{@code ESSENCE_CAP_PER_TAG}）Shift 到底 {@link PocketConstants#ESSENCE_MAX_PHIALS_PER_TAG} 只瓶。
     * ★<b>自立口径 C2（晶只读不产）</b>在取出侧的落点就是本方法——它已经<b>不再</b>调
     * {@code TaumCompat#newCrystalStack}（旧晶仍可被入档路识别并溶回盘，识别面在
     * {@code PocketEssenceIntake} 与 {@code NekoEssenceGhostCell#carriesTag}）。
     * <b>先扣点、后物化、放不下就退点</b>：三步任何一步失败都不会凭空造瓶，也不会把点数值吞掉
     * （TC 缺席 ⇒ 物化返回 null ⇒ 点数原样退回）。
     * <p>
     * ★与 ghost 声明的组上限步进（{@code FILTER_CAP_STEP_ESSENCE} = 1 点）之间存在<b>已被 E1 登记的
     * 张力</b>：上限停在非整瓶（例如 3）时那一档<b>一瓶也搬不动</b>，通道照跑、零搬运——这不是本片引入的
     * （旧载体 1 点/枚时两者天然重合），但载体改判后被放大。收口属裁决题，见交付报告"待审查点"。
     * <p>
     * ★R78③：arg 是<b>格号</b>，tag 由服务端的格位归属表（{@code PocketEssenceStore#tagAtCell}）
     * 反查——<b>不吃</b>客户端可能送来的 tag（R18/R19：客户端字符串一律不可信），也不再是
     * {@code aspectOrder()[格号]} 那个固定派生序。空格位（该格从未被占过）一律不动并给"无事发生"回执。
     */
    void performEssenceOut(int packedArg) {
        if (!serverGuardOk()) {
            return;
        }
        final boolean shift = packedArg >= PocketConstants.ESSENCE_OUT_SHIFT_FLAG;
        final int cell = shift ? packedArg - PocketConstants.ESSENCE_OUT_SHIFT_FLAG : packedArg;
        final PocketEssenceStore store = panel.inventory()
            .essence();
        final String tag = store.tagAtCell(cell);
        if (tag == null) {
            // 该格没有归属（从未入过账）⇒ 没东西可取；给一条可读回执而不是静默（R10）
            panel.putReceipt("gtit.pocket.still.idle", 0);
            return;
        }
        if (!shift && panel.syncManager()
            .getCursorItem() != null) {
            // ★R86：游标已被占用 ⇒ vanilla 也是"什么都不发生"，但这里必须说话，否则又回到"点了一下没动静"
            panel.putReceipt("gtit.pocket.essence.cursor_busy", 0);
            return;
        }
        final int stock = store.get(tag);
        final int wanted = shift ? stock : Math.min(stock, PocketConstants.ESSENCE_OUT_MAX_POINTS_PER_ACTION);
        if (wanted <= 0) {
            // ★R87-f 起"格有归属但点数为 0"是合法现役态（声明保格）⇒ 这一档必须先判掉，
            // 否则下面的取整会把"无事发生"报成"余 0 点不足一瓶"那种当场可笑的谎
            panel.putReceipt("gtit.pocket.still.idle", 0);
            return;
        }
        // ★R88 自立口径 C1：一瓶固定 ESSENCE_OUT_UNIT_POINTS 点 ⇒ 取出量向下取整到一瓶的整数倍，
        // 余数原地留盘。取整算术只有 TaumDistillRules#floorToPhialUnits 一处（E3 已把它钉成离线判据），
        // 本处不再复写 "wanted - wanted % unit" 这第二条同形算式。
        final int whole = TaumDistillRules.floorToPhialUnits(wanted);
        final int leftover = wanted - whole;
        if (whole <= 0) {
            // 一格连一瓶都凑不满 ⇒ 一枚都不产、一分都不扣；把"为什么没动"说成留盘点数而不是静默
            panel.putReceipt("gtit.pocket.essence.not_enough_phial", leftover);
            return;
        }
        final int points = store.extract(tag, whole);
        if (points <= 0) {
            panel.putReceipt("gtit.pocket.still.idle", 0);
            return;
        }
        panel.inventory()
            .recordEssenceDelta(tag, -points);
        final int moved = shift ? panel.depositPhialsToPlayer(tag, points) : panel.handPhialsToCursor(tag, points);
        if (moved < points) {
            // 物化失败（TC 缺席/该 tag 不可物化/落点装不下）：点数退回原格，绝不销毁价值
            final int returned = points - moved;
            store.add(tag, returned);
            panel.inventory()
                .recordEssenceDelta(tag, returned);
        }
        panel.inventory()
            .markDirty();
        // ★L4（[PocketR89]）：取出支量值读数（U3——R88 备选一"取出支自身失效"的裁决样本）。
        // 退点分支（moved < points）另有一条 INFO 首例：那是 U3 唯一关心的异常形态，常规 jar 里可直读。
        GTInterestingThing.LOG.debug(
            "[PocketR89] L4 取出支：tag={} shift={} stock={} wanted={} whole={} leftover={} moved={}",
            tag,
            shift,
            stock,
            wanted,
            whole,
            leftover,
            moved);
        if (moved < points) {
            NekoPocketPanel.logOnce(
                "L4-out-refund",
                "[PocketR89] L4 取出支退点（U3 裁决样本，本形态首例升 INFO）：tag={} 扣 {} 点、物化只落 {} 点、退回 {} 点",
                tag,
                points,
                moved,
                points - moved);
        }
        if (moved <= 0) {
            // ★R88 债②（R90 T2 拆键）：取出语义的"落点满"——两个 %d =（本次取出点数 0、
            // 仍留在源质盘的余数点数），玩家必须能从这条回执读到"余数留盘"（与 note.phial 同口径）。
            panel.putReceipt("gtit.pocket.receipt.target_full_take", 0, leftover);
        } else if (leftover > 0) {
            panel.putReceipt("gtit.pocket.essence.partial_leftover", moved, leftover);
        } else {
            panel.putReceipt("gtit.pocket.receipt.ok", moved);
        }
    }

    /**
     * ★R87-d 服务端执行体：四态判定在 {@link PocketEssenceIntake}（目标 tag = 容器自带 tag，手势格号不读）；
     * 失败三态粘性回执、分毫不动。
     * <p>
     * ★★<b>R88 两处修正</b>：
     * <ol>
     * <li><b>B1（刷晶根因）</b>：清游标改走 {@code syncManager.setCursorItem(null)}。旧写法裸写
     * {@code target.inventory.setItemStack(null)} 只改服务端、<b>不推客户端</b>（上游
     * {@code ModularSyncManager#setCursorItem} = {@code setItemStack} + {@code cursorSlotSyncHandler.sync()}，
     * 后者才是 MUI2 唯一一条 cursor→client 通道），于是客户端游标上那一叠仍然画着、仍可放置/丢出
     * ⇒ 同一批容器既换回了点数又在客户端活着 = 刷取。同仓 {@code handPhialsToCursor}（Panel）一直用的是正解，
     * 两处口径必须同形。★作用域内的 {@code target} 与 {@code syncManager.getPlayer()} 是<b>同一玩家</b>
     * （{@code player()} 即 {@code data.getPlayer()}，而 MUI2 的 {@code GuiManager#open} 把同一个
     * {@code EntityPlayerMP} 同时喂给 {@code PlayerInventoryGuiData} 与 {@code ModularContainer#construct}）
     * ⇒ 不需要另找 syncManager。</li>
     * <li><b>C3（聊天框零输出）</b>：成功回执改道 {@code putReceipt}（面板内粘性回执，经
     * {@code SYNC_RECEIPT} 下发），源质域不再往聊天框发任何东西。</li>
     * </ol>
     */
    void performEssenceIntake() {
        if (!serverGuardOk()) {
            return;
        }
        final EntityPlayer target = panel.player();
        final PocketEssenceIntake.Result result = PocketEssenceIntake
            .intake(target.inventory.getItemStack(), panel.inventory()
                .essence(), EssenceGate.TAUM);
        // ★L3（[PocketR89]）：四态 Outcome 读数（D1：空瓶误入槽的纵深防御是否真的在拒）
        GTInterestingThing.LOG
            .debug("[PocketR89] L3 入槽执行：outcome={} tag={} points={}", result.outcome, result.tag, result.points);
        if (!result.accepted()) {
            panel.putReceipt(result.langKey(), 0);
            return;
        }
        // ★R88 B1：唯一的游标写口 = syncManager（写服务端现值 + 推客户端），不得再裸写 inventory
        panel.syncManager()
            .setCursorItem(null);
        panel.inventory()
            .markDirty();
        // ★R88 C3：入账点数进面板回执，不进聊天框（面板回执只载两个整数 ⇒ tag 名不再上文案）
        panel.putReceipt(result.langKey(), result.points);
    }

    // ------------------------------------------------------------------ S5 · ghost 服务端执行体

    /**
     * ★ghost 的<b>唯一</b>执行体（服务端；由 Panel 的 {@code receiveGhostRequest} 经
     * {@code ServerTaskScheduler} 投递后在<b>服务器主线程</b>调用，R71）。
     * <p>
     * R18/R19 的口径：客户端的 {@code handleDragAndDrop} 与 {@code onMousePressed} 都不做任何
     * 清空/落档动作，只把"请求"发上来；判定、落档、槽属性、虚化广播全在这里。
     * 文法解析、分区域越界判定与"按载荷类型分派 kind"全在 {@link PocketGhostRequest#apply}
     * （纯函数 ⇒ 由 {@code runPocketTest} 端到端钉住），本方法只剩守卫 + 写档 + 刷虚化三步。
     * ★R90 E3（D3）起 {@code apply} 的三参形态多带 {@code inventory.essence()}：源质声明在写档前
     * 先与<b>格位归属表</b>对账（无归属格 + 载荷带 tag ⇒ 放行并 {@code assignCell} 建档；有归属但
     * tag 不匹配 ⇒ 拒），声明与格位从此不再各写各的。
     * 客户端传来的字符串一律不可信 —— 伪造包最多只能往自己口袋里写声明，改不到别人的口袋
     * （会话守卫 {@link #serverGuardOk()} 保证 {@code pocketStack()} 就是该玩家背包里那一枚）。
     */
    void onServerGhostRequest(String request) {
        if (panel.syncManager()
            .isClient() || !serverGuardOk()) {
            return;
        }
        // ★L8（[PocketR89]，服务端入口）：请求原文 + 判定结论——与客户端发送侧读数（L8 拖入栈 dump /
        // L9 拒收回读）拼成 U2（MUI2/NEI 交付栈是否保 NBT）的完整裁决链：客户端 dump 有 NBT 而此处
        // 无请求到达 ⇒ 发送前置问题；此处有请求且载荷带 tag ⇒ NBT 保住了。
        final PocketGhostRequest.Decision decision = PocketGhostRequest
            .apply(request, panel.inventory()
                .filters(), panel.inventory()
                    .essence());
        GTInterestingThing.LOG.debug(
            "[PocketR89] L8 ghost 请求到达服务端：{} ⇒ outcome={} kind={} slot={}",
            request,
            decision.outcome,
            decision.kind,
            decision.slotIndex);
        if (!decision.changed()) {
            // REJECTED（文法不合法 / 越出该区域白名单 / 载荷解不出 / ★E3 起源质格归属不匹配）与
            // UNCHANGED（重复拖同一载荷、解绑本来就没声明的格）都不写档、都不刷虚化 —— 脏标记一旦为真就要在关屏时序列化整份 NBT
            return;
        }
        panel.inventory()
            .markDirty();
        panel.applyGhosts();
    }

    // ------------------------------------------------------------------ 防伪守卫（R19 三层）

    /**
     * L1 会话绑定：包体到达服务端后，重新核"这个玩家此刻开的就是这个面板、面板认的仍是那一枚口袋"。
     * <p>
     * 三层各自的落点（照 {@code decision-ledger.md} R19 的现成件口径）：
     * <ul>
     * <li><b>L1 会话绑定</b> = 本方法（{@code openContainer} 是 MUI2 的 {@code ModularContainer}
     * + {@code getGuiData()} 是 {@code PlayerInventoryGuiData} + 承载格上的栈仍是<b>同一枚</b>口袋）；</li>
     * <li>{@code L2 主线程} = <b>靠投递换来</b>，不是天然的（R71 更正本条旧口径）：MUI2 的 GUI 包
     * 走 Forge {@code SimpleNetworkWrapper}，其 {@code channelRead0} 在 <b>Netty IO 线程</b>直调 setter
     * （逐字节码依据见 Panel 的 {@code receiveServerAction}）⇒ 本方法的调用方（Panel 的
     * {@code onServerAction} 分发）已由 {@code ServerTaskScheduler} 投递到服务器 tick END。
     * 这一跳只把动作推迟 ≤1 tick，扣费与传输仍在<b>同一次</b>执行体内完成，不构成两处真相；</li>
     * <li><b>L3 冷却闩 + cost&gt;0 断言</b> = {@link #performChannelRequest} 里的点检 2 与那段
     * {@code cost <= 0} 早退（规避 {@code NekoWallet.tryDeduct} 的 {@code amount<=0 return true}）。</li>
     * </ul>
     */
    boolean serverGuardOk() {
        if (panel.syncManager()
            .isClient()) {
            return false;
        }
        final EntityPlayer target = panel.player();
        final ItemStack pocket = panel.pocketStack();
        if (target == null || pocket == null) {
            return false;
        }
        if (!(target.openContainer instanceof ModularContainer container)) {
            return false;
        }
        if (!(container.getGuiData() instanceof PlayerInventoryGuiData guiData)) {
            return false;
        }
        // 承载格上的栈必须还是这一枚（身份比较，不是 isItemEqual：换一枚就得重新开界面）
        final ItemStack atCarrier = guiData.getUsedItemStack();
        // ★R88（收 D1.3-5 的"比引用"面）：堆叠合并 / 跨维重建 EntityPlayerMP 会让 NBT 往返出<b>另一个
        // 引用</b>，此时按身份比就把合法持有者判成伪造 ⇒ 该 tick 之后所有取瓶/入槽动作静默拒执行
        // （观感"点了没反应"）。回落判据不再自造：直接用每 tick 交互闸同源的那条
        // （{@code carrierStillPresent}——同格、同物品、按 uuid 认玩家，并顺手把 pocket 重新钉过去），
        // 于是"能不能操作"与"界面还该不该开着"是同一处真相，防伪强度不降。
        return atCarrier == pocket || panel.carrierStillPresent(target);
    }

    // ------------------------------------------------------------------ PocketSession 实现体（Panel 的 @Override 逐条委托到这）

    ItemStack carrierStack() {
        return panel.pocketStack();
    }

    NBTTagCompound carrierTag() {
        final ItemStack pocket = panel.pocketStack();
        return pocket == null ? null : pocket.getTagCompound();
    }

    boolean isHeldByOwner() {
        final EntityPlayer target = panel.player();
        final ItemStack pocket = panel.pocketStack();
        if (target == null || target.inventory == null || pocket == null) {
            return false;
        }
        for (ItemStack held : target.inventory.mainInventory) {
            if (held == pocket) {
                return true;
            }
        }
        return false;
    }

    void markDirty() {
        panel.inventory()
            .markDirty();
    }

    PocketCellBindings bindings() {
        return panel.inventory()
            .bindings();
    }

    PocketFilterConfig filters() {
        return panel.inventory()
            .filters();
    }

    PocketEssenceStore essence() {
        return panel.inventory()
            .essence();
    }

    ItemStack distillInputStack(int index) {
        return index < 0 || index >= distillInputSlots() ? null
            : panel.inventory()
                .distillInput()
                .getStackInSlot(index);
    }

    int distillInputSlots() {
        return panel.inventory()
            .distillInput()
            .getSlots();
    }

    /** 一轮蒸馏对<b>每个非空格</b>各做一次"减 1"（★R84：对象是格不是组，同物多格并行各扣 1 件；R28：5 秒是节拍不是产量）。 */
    void consumeOneDistillInput(int index) {
        final ItemStack at = distillInputStack(index);
        if (at == null) {
            return;
        }
        if (at.stackSize <= 1) {
            panel.inventory()
                .distillInput()
                .setStackInSlot(index, null);
        } else {
            final ItemStack rest = at.copy();
            rest.stackSize = at.stackSize - 1;
            panel.inventory()
                .distillInput()
                .setStackInSlot(index, rest);
        }
        panel.inventory()
            .markDirty();
    }

    int fluidBarRoom(int tank, FluidStack probe) {
        return panel.inventory()
            .fluidBarRoom(tank, probe);
    }

    int depositFluid(int tank, FluidStack fluid) {
        return panel.inventory()
            .depositFluidIntoBar(tank, fluid);
    }

    // ------------------------------------------- ★R86 缺陷 3：口袋 → 元件的推送向来源面（服务端会话实现）

    int fluidTankCount() {
        return PocketInventory.tankCount();
    }

    FluidStack fluidInTank(int tank) {
        return panel.inventory()
            .ownTankFluid(tank);
    }

    int drainOwnTank(int tank, int milliBuckets) {
        return panel.inventory()
            .drainOwnTank(tank, milliBuckets);
    }

    Map<String, Integer> essenceStock() {
        return panel.inventory()
            .essence()
            .snapshot();
    }

    // ------------------------------------------------------------ R84：中栏既是注入来源也是抽取落点

    int storageSlots() {
        return panel.inventory()
            .storage()
            .getSlots();
    }

    ItemStack storageStackAt(int slot) {
        final int size = storageSlots();
        return slot < 0 || slot >= size ? null
            : panel.inventory()
                .storage()
                .getStackInSlot(slot);
    }

    void setStorageStackAt(int slot, ItemStack stack) {
        if (slot < 0 || slot >= storageSlots()) {
            return;
        }
        panel.inventory()
            .storage()
            .setStackInSlot(slot, stack);
        panel.inventory()
            .markDirty();
    }

    boolean isStorageGhostDeclared(int slot) {
        return panel.inventory()
            .isGhostItemSlot(slot);
    }
}
