package com.miaokatze.gtit.gui.pocket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.miaokatze.gtit.common.items.pocket.EssenceNativeChannels;
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
import com.miaokatze.gtit.common.items.pocket.PocketMagnetFilter;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
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
 * ★R91-④ 之后取出侧只剩一条单点 {@code performEssenceOutToPhial}（用例锚定方法体守卫与"只灌玩家自己那只瓶"
 * 的单源声明；旧 {@code newPhialStack} 自造瓶口已随该裁定整体删除），
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

    /**
     * ★★<b>R93-①：本类原有的 {@code performTakeOut()}（语义②「口袋 → 玩家背包」）已整体删除。</b>
     * <p>
     * 删除的理由不是"没人调了"这么简单，而是它的<b>判据本身就是缺陷</b>：
     * <ol>
     * <li>目标集合读的是<b>整个槽组</b>（{@code for (index = 0; index < storage().getSlots(); index++)}），
     * 既不读被点格号（格号从未上网络）、也不按组合并 ⇒ 用户实机"一次把整栏全拿出来"；</li>
     * <li>体内<b>零次</b> {@code isGhostItemSlot} ⇒ 绕过 R85 A5「声明格整格不参与」（对照本类
     * {@code performSort} 在快照那一圈是明确早退的），且它的 {@code setStackInSlot} 走的还是
     * "程序化写入不经过 isItemValid"那条豁免。</li>
     * </ol>
     * ★正解是<b>不做这条</b>：Shift+左键交回原版 {@code ModularContainer} 的 QUICK_MOVE →
     * {@code transferStackInSlot(slotId)}，它天然只搬被点那一格（★取证 spike：MUI2 的
     * {@code onMousePressed(int)} 不给坐标，本仓无法从满覆盖件里算出被点格号，"穿 arg"那条路不成立）。
     * <p>
     * ★★<b>但"缺陷 2 已修"这句要说清边界，不许写成"整格不参与已经成立了"</b>（独立审查实测库链证伪过一次口径）：
     * 交回原版之后，<b>单格</b> Shift+左键点在一格挂着声明的真实槽上<b>仍然会把它取出</b> —— 原因在库链上：
     * 声明格是<b>真实</b>槽（{@code NekoFilterSlot#applyAccessibility} 给 BIND / NONE 档
     * {@code accessibility(false, true)} ⇒ {@code canTake} 为真），而 {@code ModularContainer} 的
     * QUICK_MOVE 只问 {@code canTakeStack} 与 {@code isPhantom}（{@code ModularSlot.java:78-80} /
     * {@code ModularContainer.java:262-265}），★一次都不问 {@code isGhostItemSlot}。
     * ⇒ 这条<b>不是</b>绕过 A5：A5 约束的是<b>本仓自研的整批搬运 / 整理</b>（那里声明格里的东西是"已经补到的
     * 货"，整批搬走等于把需求掏空，故本类 {@code performSort} 在快照那一圈明确早退）；单格取出走的正是
     * <b>R84 的既有口径「声明格禁放置、可取出」</b>（"补进来的产物不许被永久关在格子里"）。
     * ★所以本轮真正消掉的是<b>"整批掏空声明格"这个形状</b>（载体没了），不是"声明格永不被取出"；
     * 本类也不再需要为"取出"维护第二套搬运判定。
     */
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
            // ★★R96 S4（根因第 2 处，取证 r96-ret1 §1.3 缺陷 2）：合堆天花板原来是裸
            // {@code existing.getMaxStackSize()}（= 64）⇒ 磁力拾取 / ME 补满两条自动路<b>好不容易</b>造出的
            // 1024 堆，玩家点一次「整理」就被拆回若干 64 堆 —— README 承诺的「单格 64 → 1024」在这一条上是<b>反的</b>。
            // 现在与 handler、通道消费侧、ghost 读数侧共读同一条单源算式
            // （{@link PocketInventory#effectiveStorageLimit(boolean, ItemStack)}），★不在本文件抄第二份三元。
            // 未升级档该算式给 64 ⇒ 现状逐字不变（反「修过头」的判据，见用例 A2）。
            final int free = PocketInventory.effectiveStorageLimit(storageStackUpgraded(), existing)
                - existing.stackSize;
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
        // ★★R96 S4b（R-1 同一条纪律的另一半）：回写这一圈<b>挂起</b>中栏的按件收口
        // （{@link PocketInventory#beginStorageRawRewrite()}）。上面的快照已经把非声明格清空，本循环按
        // {@code cursor++} 递增落点逐格原样写回；若收口插手，它钳下来的差额会被摊到<b>后面那几次落点</b>上
        // ⇒ 下一笔 {@code setStackInSlot} 正好覆盖它 = <b>整理一次吃掉一截</b>（关着 STACK 开关、格里还存着
        // 大堆的玩家一点整理就命中）。整理必须不增不减，与读档同一裁定（P-4：关开关不许销毁已存进去的）。
        panel.inventory()
            .beginStorageRawRewrite();
        try {
            for (ItemStack stack : merged.values()) {
                cursor = nextRealSlot(cursor);
                if (cursor >= size) {
                    // ★R96 S4 验收 5（出包边界）：溢出的一堆<b>不整堆</b>交给玩家，见
                    // {@link #giveAwayInNaturalChunks}（那条 1024 直接进原版背包 = NEI 当无限物品改写成 111）。
                    giveAwayInNaturalChunks(stack);
                    continue;
                }
                panel.inventory()
                    .storage()
                    .setStackInSlot(cursor++, stack);
            }
        } finally {
            panel.inventory()
                .endStorageRawRewrite();
        }
        panel.inventory()
            .markDirty();
    }

    /**
     * ★★<b>R96 S4 验收 5：出包边界</b> —— 把一堆内容<b>按该件的天然 {@code maxStackSize} 拆成若干普通栈</b>
     * 再逐块交给玩家，使 {@code >100} 的堆<b>只存在于口袋自己的 handler 内</b>。
     * <p>
     * 为什么必须在离开中栏这一刻拆（两条独立的数据破坏面，取证 r96-ret7 §2.1 / §3.3 / §3.4）：
     * <ol>
     * <li><b>NEI 的裸数值判据</b>：{@code InfiniteStackSizeHandler.isItemInfinite} 写作
     * {@code stackSize == -1 || stackSize > 100}，{@code NEIController.updateUnlimitedItems} 由
     * {@code ClientHandler} <b>每客户端 tick</b> 扫 {@code InventoryPlayer} 每一格，命中即
     * {@code replenishInfiniteStack} ⇒ <b>把该格件数改写成 111</b>。1024 件进背包 = 一次读成 111 的
     * 静默改档（NEI 开了 item 动作时；生存下多半不触发，但这条腿的存在不由我们决定）。</li>
     * <li><b>vanilla 的 byte 宽度</b>：{@code ItemStack.writeToNBT} 用 {@code setByte("Count", ...)}，
     * {@code EntityItem} 的掉落物存档同病 ⇒ 1024 掉成实体后读回是 0（<b>件数蒸发</b>）。MUI2 / 本仓的
     * handler 存档早已补成 INT 口径，那条加固<b>不覆盖</b>原版实体与原版背包。</li>
     * </ol>
     * ★天然上限取 {@code stack.getMaxStackSize()}（不是 {@code effectiveStorageLimit}）：出包后的世界是
     * <b>原版背包</b>，那里的尺子就是天然满量；把口袋那把放大尺带出去正是上面两条的成因。
     * 每块 ≤ 64 ⇒ 同时满足「不触发 NEI 阈值」与「byte 宽度装得下」两项，无需再判开关位。
     * <p>
     * 非消耗语义不变：{@link NekoPocketPanel#giveToPlayer} 自己负责「装不下掉脚下」，本方法只负责
     * <b>不把整堆 1024 递给它</b>；拆块过程中 {@code stack.stackSize} 递减到 0，一件不多一件不少。
     */
    private void giveAwayInNaturalChunks(ItemStack stack) {
        if (stack == null) {
            return;
        }
        final int natural = Math.max(1, stack.getMaxStackSize());
        while (stack.stackSize > 0) {
            final ItemStack chunk = stack.copy();
            chunk.stackSize = Math.min(natural, stack.stackSize);
            stack.stackSize -= chunk.stackSize;
            panel.giveToPlayer(chunk);
        }
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
     * ★R95 在三重点检<b>之前</b>还有一条持续化早退（★R96 S5 起它<b>分 mode</b> 且<b>会装道</b>：
     * 短效那一支先幂等地保证有一条活 SHORT 通道、再回 {@code gtit.pocket.channel.always_on}，
     * 瞬时那一支则<b>不早退</b>、照常走三重点检 ⇒ 持续化不再顺带禁掉瞬时通道）。
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
        final boolean burst = mode == PocketChannelState.Mode.BURST;
        final PocketCellBindings bindings = panel.inventory()
            .bindings();
        // ★R95 通道持续化：载体上这一型<b>当前生效</b> ⇒ 通道批边界自动续批（见 PocketChannelDriver 的
        // 回满腿），短效这一支<b>早退</b>——放在识别/冷却/扣费三重点检<b>之前</b> ⇒ 一分不扣、一次冷却不占、
        // 一个识别查询不发；回执走面板粘性回执通道（putReceipt，R88 口袋域聊天零输出的同一裁定）
        // 告知"已在常开态"。客户端按钮禁用是 BottomBand 的职责，本处是服务端腿（伪造包 / 旧客户端同样被挡）。
        // ★★R96 S2 收口（六处旁路的最后一处）：判据从位图直读换成组合谓词 {@code isActive}。
        // <p>
        // ★★★<b>R96 S5 的三条改动，一条都不许并回旧口径讲</b>（取证 r96-ret1 §2.4/§2.5）：
        // ① <b>分 mode</b>（验收 B3，本轮裁定 = 瞬时通道恢复可用）：早退只吃短效那一支。R95 那一支
        //    <b>不分 mode</b> ⇒ 位一置起连"一次穿完"的瞬时通道也被顺带禁掉，而 README 代价 33 从没
        //    承诺过这件事 —— 那是玩家可感知的功能损失，不是设计意图的证据。瞬时那一支照旧走
        //    识别 → 冷却 → 扣费三重点检（★恢复可用 ≠ 免费，旧门一条不放宽）。
        // ② <b>这一支不再是"什么都不做"</b>（验收 B1 的服务端补腿）：幂等地保证有一条活 SHORT 通道。
        //    R95 的死锁正在这儿 —— 早退排在下面那条 {@code openChannel}（全仓唯一的激活口）之前，
        //    于是"位"把"道"关死，而续批腿又要求已有活通道。两条腿（这里 + driver 的真空支）
        //    共用 {@code ensurePersistentShortChannel} <b>同一个</b>激活口，★不长出第二台状态机。
        // ③ <b>回执跟着真值走</b>（验收 B4 同一条纪律）：装不出活通道（一枚元件都没绑 ⇒ 绑定了也不算
        //    "常驻"）时不许回"已在常开态"，落到下面的点检，让玩家读到"没认出元件"那条真话。
        if (!burst && PocketUpgradeSwitches.isActive(panel.pocketStack(), PocketUpgradeType.CHANNEL_PERSIST)) {
            if (PocketChannelManager.INSTANCE.ensurePersistentShortChannel(
                uuid,
                panel.pocketStack(),
                bindings,
                panel.inventory()
                    .filters())) {
                panel.putReceipt("gtit.pocket.channel.always_on", 0);
                return;
            }
        }
        final PocketChannelOps ops = new PocketAeChannelOps(target, panel.pocketStack(), panel);
        // 点检 1：识别（R6/R64c）—— 至少一枚绑定元件当前"在带电驱动器/ME 箱里且有可用通道"。
        // getCellArray 全空 ⇒ 拒绝开道并回独立回执码，客户端那侧的置灰只是体验层。
        if (bindings.isEmpty() || !anyRecognised(bindings, ops)) {
            panel.putReceipt("gtit.pocket.receipt.unrecognised", 0);
            return;
        }
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

    /**
     * ★★R96 S2：配置面板那枚开关的<b>服务端入口</b>（三判的第三判 + 委托数据面写腿）。
     * <p>
     * <b>三判的顺序</b>：① 持有者本人 / 载体身份（{@link #serverGuardOk}，与取出·通道<b>同一条</b> R19 L1
     * 防伪单源，★不自造第二份身份判据）→ ② 该型已在档上 → ③ 关闭守卫。后两判全在
     * {@link PocketConfigPanel#commitSwitch} 里（那两判只吃 NBT 与数据面 ⇒ 抽成静态纯函数后能在纯 JVM
     * 真跑；留在本方法里就只能"钉文本"，正是 R57/C3 那族形状）。
     * <p>
     * <b>三种拒绝的公共形状：零写入 + 一条粘性回执</b>。回执走 {@code putReceipt}（口袋域聊天零输出，
     * R88 的同一裁定）⇒ 连点不会刷屏；同值重复到达走 {@code NO_CHANGE} 支，连 NBT 都不碰。
     * <p>
     * <b>非法 arg 静默丢弃</b>（不写档、不回执）：伪造包不该买到一条回执，而"这一型存在吗"的判定
     * 本身也不该长成一次可见反馈（口径同 {@code performEssenceOutToPhial} 的格号越界支）。
     *
     * @param arg {@code PocketConfigPanel.encode} 的产物：{@code ordinal * 2 + offBit}
     */
    void performUpgradeSwitchToggle(int arg) {
        final PocketUpgradeType type = PocketConfigPanel.typeOfArg(arg);
        if (type == null) {
            return;
        }
        // 判①：持有者本人 + 载体身份（客户端伪造 / 载体被换出 / 界面已关都在这里挡住）。
        // ★这一判不通过时★不写档★，只回一条粘性回执 —— 静默是这里最坏的失败方式。
        if (!serverGuardOk()) {
            panel.putReceipt(PocketConfigPanel.identityReceiptKey(), 0);
            return;
        }
        // ★写的是<b>活查表</b>取到的那枚载体（与 R95 的固化写点同一对象，W3 那条身份门同源）：
        // panel.pocketStack() 是开屏瞬间的引用，会话期内载体被换出时它会陈旧 ⇒ 开关会写进错误的口袋。
        final ItemStack carrier = panel.carrierStackLive();
        if (carrier == null) {
            panel.putReceipt(PocketConfigPanel.identityReceiptKey(), 0);
            return;
        }
        final PocketConfigPanel.Outcome outcome = PocketConfigPanel.commitSwitch(
            carrier,
            type,
            PocketConfigPanel.offOfArg(arg),
            panel.inventory(),
            cursorStack());
        panel.putReceipt(PocketConfigPanel.receiptKey(outcome), 0);
    }

    /**
     * ★R96 S9b：配置面板里那一行<b>模式控件</b>的服务端执行体（三判中的两判在
     * {@link PocketConfigPanel#commitMode}，本处只跑第三判"持有者本人 + 载体身份"）。
     * <p>
     * ★与 {@link #performUpgradeSwitchToggle} <b>同构而不共用</b>：码空间不同（见
     * {@code NekoPocketPanel#ACTION_UPGRADE_MODE} 那条注释里"ordinal 与 row 同为 0..2"那个陷阱），
     * 且模式没有容量/堆叠那两类关闭守卫。两条腿各写一遍"解越即丢 + 身份判 + 粘性回执"这三步，
     * 换来的是<b>改一条不会静默改掉另一条</b>（并成一条就会：守卫那段只对开关有意义，
     * 将来有人给开关加守卫会连带把模式也挡掉，而玩家读到的是同一句"这一型没在档上"）。
     */
    void performUpgradeModeToggle(int arg) {
        final int row = PocketConfigPanel.modeRowOfArg(arg);
        if (row < 0) {
            // 解越（含客户端伪造的 arg）⇒ 丢弃这条包：不写档、也不回回执
            // （★与开关那条腿同一条纪律：越界的包不代表玩家做了什么动作）
            return;
        }
        if (!serverGuardOk()) {
            panel.putReceipt(PocketConfigPanel.identityReceiptKey(), 0);
            return;
        }
        // ★写的是<b>活查表</b>取到的那枚载体（与开关腿同一个理由：panel.pocketStack() 是开屏快照，
        // 会话期内载体被换出时它会陈旧 ⇒ 模式会写进错误的口袋）。
        final ItemStack carrier = panel.carrierStackLive();
        if (carrier == null) {
            panel.putReceipt(PocketConfigPanel.identityReceiptKey(), 0);
            return;
        }
        final PocketConfigPanel.Outcome outcome = PocketConfigPanel.commitMode(
            carrier,
            row,
            PocketConfigPanel.modeOnOfArg(arg));
        panel.putReceipt(PocketConfigPanel.receiptKey(outcome), 0);
    }

    /**
     * 玩家游标栈（★堆叠守卫的扫描面必须含它，理由见 {@code PocketUpgradeGuards} 类 javadoc 那段
     * "游标必须在扫描面里"）。取的是 {@code EntityPlayer#inventory#getItemStack()} —— 服务端权威那份，
     * 不是 {@code syncManager.getCursorItem()}（那是同步镜像，本方法已在服务端主线程，直读权威）。
     */
    private ItemStack cursorStack() {
        final EntityPlayer target = panel.player();
        return target == null || target.inventory == null ? null : target.inventory.getItemStack();
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
     * ★★<b>R91-④（瓶往返改判）：本条不再是"取出一条路"，而是<b>转发到取出单点</b>。</b>
     * <p>
     * 用户报的「点击源质槽的源质，会凭空生成安瓿瓶」的正身就在旧 {@code ACTION_ESSENCE_OUT} 这一支：
     * 空游标点一格 ⇒ 服务端 {@code newPhialStack} 无中生有造一叠满瓶放上游标 / 进背包。R91-④ 裁定
     * <b>取出必须手持空安瓿瓶</b>，于是那三条自造瓶口（{@code newPhialStack} / 空游标 {@code handPhialsToCursor}
     * / Shift {@code depositPhialsToPlayer}）在 Panel 侧整体删除，本方法只保留<b>动作码入口</b>的身份
     * （★码值 7 与 arg 的 shift 位都不变 ⇒ 老客户端与既有装配侧 {@code requestEssenceOut} 一字不改仍可达），
     * 把 packed arg <b>原样</b>交给 {@link NekoPocketPanel#performEssenceOutToPhial(int)} 那唯一一处出瓶口：
     * <ul>
     * <li>游标是空瓶 ⇒ 灌玩家自己那几只瓶（C1 向下取整、余数留盘、Shift = 该格整份，全部算术在
     * {@code PocketEssenceIntake#phialsToFill} 单源里）；</li>
     * <li>空手 / 持非容器 ⇒ <b>零产出、零扣点</b> + {@code gtit.pocket.essence.need_phial} 面板回执
     * （★R88 C3：不进聊天框）。旧文案里"游标已被占用 ⇒ {@code essence.cursor_busy}"这一态因此
     * <b>并进了 need_phial</b>（游标上有东西但不是空瓶 = 同一条"拿瓶子去装"的说话）。</li>
     * </ul>
     * ★本方法体内因此<b>没有</b>任何库存写入（不扣点、不动游标、不 markDirty）——"改库存入口必显式标脏"
     * 那条 SC5 穷举的落点随之搬到 {@code performEssenceOutToPhial}；这里若再留一份 {@code store.add}
     * 就是两处真相（也是"撤了没给落点"的那一类删信息）。
     * ★<b>L4（[PocketR89]）量值读数</b>同理搬迁：旧"取出支"那条 debug 与"取出支退点"那条首例 INFO
     * 已随自造瓶支一起消失，现落点在单点的三条打点上（{@code L4 格→瓶取出} 成功读数 /
     * {@code L4 格→瓶取出拒收} 零产出 / {@code L4 格→瓶取出退点} U3 样本）⇒ 裁决链不缺环。
     */
    void performEssenceOut(int packedArg) {
        if (!serverGuardOk()) {
            return;
        }
        panel.performEssenceOutToPhial(packedArg);
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
     * ⇒ 同一批容器既换回了点数又在客户端活着 = 刷取。同仓取出侧（{@code NekoPocketPanel
     * #performEssenceOutToPhial}）一直用的是正解，两处口径必须同形。★作用域内的 {@code target} 与
     * {@code syncManager.getPlayer()} 是<b>同一玩家</b>
     * （{@code player()} 即 {@code data.getPlayer()}，而 MUI2 的 {@code GuiManager#open} 把同一个
     * {@code EntityPlayerMP} 同时喂给 {@code PlayerInventoryGuiData} 与 {@code ModularContainer#construct}）
     * ⇒ 不需要另找 syncManager。</li>
     * <li><b>C3（聊天框零输出）</b>：成功回执改道 {@code putReceipt}（面板内粘性回执，经
     * {@code SYNC_RECEIPT} 下发），源质域不再往聊天框发任何东西。</li>
     * </ol>
     * ★★<b>R91-④（瓶往返改判）：入槽不再吞玻璃</b>——{@link PocketEssenceIntake.Result#refund}
     * 非空（<b>只有瓶档</b>会非空）⇒ 把等量<b>空瓶</b>原路放回游标；{@code null} ⇒ 才是旧行为"清游标"
     * （★晶档恒 {@code null}：R86「晶入槽 {@code CONSUMED}、不退空壳」<b>只对晶继续成立</b>，
     * 那张分派表住 {@code PocketIntakeOps#refundsEmptyCarrier}，★本方法不判档位、不数瓶子）。
     * 两条分支共用同一条游标正解 {@code setCursorItem(...)} ⇒ "退件"这条<b>新增的库存写入</b>
     * 天然落在 SC5 穷举的那一格里（写游标之后必须 {@code markDirty}，顺序不许多次交错）。
     */
    void performEssenceIntake(int clickedCell) {
        if (!serverGuardOk()) {
            return;
        }
        final EntityPlayer target = panel.player();
        final PocketEssenceIntake.Result result = PocketEssenceIntake.intake(
            target.inventory.getItemStack(),
            panel.inventory()
                .essence(),
            EssenceGate.TAUM,
            // ★★R91-⑤ 的 L 执法腿（源质支）：本格被 alt+左 记成别的东西 ⇒ 整笔拒收。
            // 这一格是<b>动态</b>归属（R86：扣到 0 当场腾格）⇒ 现读 cellOf(tag)；
            // ★cellOf < 0（有货无格）⇒ 属性无处可挂，按"不拦"处理（与 P 那一条同一个如实口径）。
            // ★判据本体在 PocketFilterConfig#memoryAllowsTag（★本 lambda 不含第二份位表读法）。
            tag -> {
                final int cell = panel.inventory()
                    .essence()
                    .cellOf(tag);
                return cell >= 0 && !panel.inventory()
                    .filters()
                    .memoryAllowsTag(PocketFilterConfig.Kind.ESSENCE, cell, tag);
            });
        // ★L3（[PocketR89]）：四态 Outcome 读数（D1：空瓶误入槽的纵深防御是否真的在拒）
        // ★R91-④ 追加 refund 读数：退了几只壳（0 = 晶档或失败态，>0 = 瓶档）
        GTInterestingThing.LOG.debug(
            "[PocketR89] L3 入槽执行（R91-④ 带退件读数）：outcome={} tag={} points={} refund={} 只空壳",
            result.outcome,
            result.tag,
            result.points,
            result.refund == null ? 0 : result.refund.stackSize);
        if (!result.accepted()) {
            panel.putReceipt(result.langKey(), 0);
            return;
        }
        // ★R88 B1 + ★R91-④：唯一的游标写口 = syncManager（写服务端现值 + 推客户端），不得再裸写 inventory。
        // 瓶档 ⇒ 游标换成那叠空瓶（原路退回）；晶档 / 无退件 ⇒ 清空游标（旧行为逐字保留）。
        panel.syncManager()
            .setCursorItem(result.refundsCarrier() ? result.refund : null);
        panel.inventory()
            .markDirty();
        // ★★<b>R92-④（D4）：源质支的"放置即配置"落档腿</b> —— 玩家往<b>记忆档 pending 格</b>上点一只
        // 满瓶，溶进盘之后本格就以那个 tag 建档。三条口径与物品/流体两支逐字同形（判据单源在彼处）：
        // ① 只开「MEMORY + 尚无声明」；② 已定档格<b>不覆盖</b>（换声明走 NEI 拖入或手势，P2 裁定）；
        // ③ 建档落在<b>玩家实点的那一格</b>，★不是 {@code cellOf(tag)} 那一格 —— 与 NEI 拖入完全同形
        // （{@code applySet} 也是"声明写在被拖的格、assignCell 去占最小空位"，两者可以不是同一格）。
        // ★越界 arg（伪造包）⇒ 只跳过定档，不影响入槽本体：入槽从来不需要 arg，★也就绝不拿它去截断格号
        // （R91-j 同一条纪律：越界要拒绝，不许 modulo）。
        if (clickedCell >= 0 && clickedCell < PocketConstants.ESSENCE_DISPLAY_GRID) {
            declareEssenceFromIntake(clickedCell, result.tag);
        }
        // ★R88 C3：入账点数进面板回执，不进聊天框（面板回执只载两个整数 ⇒ tag 名不再上文案）
        panel.putReceipt(result.langKey(), result.points);
    }

    /**
     * ★R92-④：源质支定档的落笔点。★本方法不含第二份 attr 位表读法（问
     * {@link PocketFilterConfig#memoryPendingForPlacement}）、不含第二份键式样（载荷用
     * {@link PocketFilterConfig.EssenceFilter} 对象交给 {@code declare} 贴槽位）、
     * 也不含第二个写入口（走 {@link PocketFilterConfig#declare} 那条唯一原语）。
     */
    private void declareEssenceFromIntake(int cell, String tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        final PocketInventory inventory = panel.inventory();
        if (inventory == null) {
            return;
        }
        final PocketFilterConfig filters = inventory.filters();
        if (filters == null || !filters.memoryPendingForPlacement(PocketFilterConfig.Kind.ESSENCE, cell)) {
            return;
        }
        final String typeId = EssenceNativeChannels.nativeChannelTypeId(tag);
        if (typeId == null || typeId.isEmpty()) {
            // 没有源质原生通道 ⇒ 这条键根本建不出来（★不猜一个通道 id 去填空档）
            return;
        }
        // ★载荷对象改由 essenceKey 单源反解（键式样只住 PocketFilterConfig 一处，★不在 GUI 层重拼）
        filters.declare(
            PocketFilterConfig.Kind.ESSENCE,
            cell,
            PocketFilterConfig.parseKey(PocketFilterConfig.essenceKey(typeId, tag)));
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
        final PocketGhostRequest.Decision decision = PocketGhostRequest.apply(
            request,
            panel.inventory()
                .filters(),
            panel.inventory()
                .essence(),
            // ★R91-⑤：FLG 支要"按本格现有内容记录"⇒ 给它一个<b>服务端</b>载荷读数口（★不让客户端抄一份
            // 载荷上来，那是 R18/R19 明令的方向）。三条读数见 {@link #ghostPayloadAt}。
            this::ghostPayloadAt);
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

    /**
     * ★★<b>R91-⑤「格内有物 ⇒ 按该物记录」的服务端读数口</b>（{@link PocketGhostRequest.PayloadSource}
     * 的生产实现，只被 {@code FLG} 支问一次）。
     * <p>
     * 三条区域各读自己那一格的<b>真实内容</b>，★都是既有单源件，本方法不造第二判据：
     * <ul>
     * <li>{@code ITEM} = 中栏那格的 {@link PocketAeChannelOps#contentKey}（与 NEI 拖入、ghost 搬空
     * 比对<b>同一个</b>键式样）；</li>
     * <li>{@code FLUID} = 该 tank 现装流体名（★读 {@code ownTankFluid} 那份真值，不读声明样本）；</li>
     * <li>{@code ESSENCE} = 本格<b>当前归属</b>的 tag（{@code tagAtCell}，R86 口径）配
     * {@link EssenceNativeChannels#nativeChannelTypeId} 的通道 id —— ★源质载荷键<b>必须</b>带 typeId，
     * 而 typeId 只有 AE2 注册表在场时解得出：解不出（TC/AE 缺席、无原生通道）⇒ 返空 ⇒
     * 本格停在"只挂 attr、无载荷"的 pending 态（R91-b 的合法状态，内容待 NEI 拖拽落成）。</li>
     * </ul>
     * ★空格的三条读数都自然落空（{@code null} / 空串）⇒ 调用方只挂状态，这正是用户要的
     * "没东西则进入这个状态"。
     */
    String ghostPayloadAt(PocketFilterConfig.Kind kind, int slotIndex) {
        if (kind == null || slotIndex < 0) {
            return "";
        }
        final PocketInventory held = panel.inventory();
        switch (kind) {
            case ITEM: {
                final ItemStack stack = held.storageStack(slotIndex);
                return stack == null ? "" : PocketAeChannelOps.contentKey(stack);
            }
            case FLUID: {
                final FluidStack fluid = held.ownTankFluid(slotIndex);
                if (fluid == null || fluid.amount <= 0 || fluid.getFluid() == null) {
                    return "";
                }
                return PocketFilterConfig.fluidKey(
                    fluid.getFluid()
                        .getName());
            }
            case ESSENCE: {
                final String tag = held.essence()
                    .tagAtCell(slotIndex);
                if (tag == null || tag.isEmpty()) {
                    return "";
                }
                final String typeId = EssenceNativeChannels.nativeChannelTypeId(tag);
                if (typeId == null || typeId.isEmpty()) {
                    // ★不在这里回落物品通道（R90 AUQ-①=B / R91-① 的政策四项），也不写死 id
                    return "";
                }
                return PocketFilterConfig.essenceKey(typeId, tag);
            }
            default:
                return "";
        }
    }

    // ------------------------------------------- ★R96 S7a · 磁力三态名单的服务端写口

    /**
     * ★磁力名单（{@link PocketMagnetFilter}）的<b>唯一</b>服务端写口 —— 本切片只提供<b>执行体</b>：
     * 72 格格件、NEI/背包拖入、三态循环按钮与面板挂载全在 <b>S7b</b>（★刻意不留 TODO 占位，也不接任何
     * C2S 入口，S7b 的 widget 按 {@code perform*} 的既存形状一行委托过来即可）。
     * <p>
     * <b>三条与 ghost 写口同源的纪律</b>（{@link #onServerGhostRequest}）：
     * <ol>
     * <li>★<b>不接客户端抄上来的目标态</b>：三态只接受"点了一次循环"这一个意图，下一态由服务端读现态
     * {@link PocketMagnetFilter.Mode#next()} 推出 ⇒ 伪造包改不出第四态，也跳不到指定态；</li>
     * <li>条目入参是<b>键串</b>（{@code i:itemId:meta:} 形状，与 {@code PocketFilterConfig.itemKey} 同形）
     * 而不是整栈：★NBT 一个字都不上网络（前提 P-11 不做 NBT 敏感匹配），也就绕开了
     * {@code StringSyncValue} 那 32693 字节墙的一切风险；解不出的键 {@link #performMagnetEntryAdd}
     * 直接返 {@code false}（★不写档；拒收回执由 S7b 的格件负责 —— wiki 那条"每个拒收分支自带 tooltip"）；</li>
     * <li>★<b>只在真改变时写档</b>：四个写口都返回"本次是否真的改变"，{@code false} ⇒ 不落 NBT、
     * 不置脏（同 {@code decision.changed()} 那一支的理由：脏标记一为真，关屏就要序列化整份 NBT）。</li>
     * </ol>
     * <b>落在哪里</b>：直接写<b>载体栈的根 NBT</b>（键 {@link PocketConstants#MAGNET_FILTER}）。这个根键
     * {@code PocketInventory} <b>不拥有</b>，因此与会话落盘（{@code writeSessionToCarrier → inventory.writeTo(root)}）
     * <b>不构成双写竞争</b>；读点只有一个（{@code PocketMagnetDriver} 的位移拍），写点也只有这里 ⇒ 一份真相。
     * <p>
     * <b>防伪</b>：{@link #serverGuardOk()} 的 L1 会话绑定（持有者本人 + 承载格上仍是同一枚口袋）——
     * 与 ghost 同一强度：伪造包最多往<b>自己</b>口袋里写 ≤72 条身份键，改不到别人的口袋。
     * ★本片<b>不</b>额外问"该型是否已固化"：那要么加第 9 枚 {@code PocketUpgrades.hasUpgrade(} 直调点
     * （S1b 的门 E/F 把它钉成"恰 8 = 豁免表求和"，多一处即红），要么用 {@code isActive} 把"开关关掉时
     * 还能不能编辑名单"这种产品问题写进数据层。名单写进没装磁力的口袋 = 一坨永不被读的 ≤1.8KB 死数据，
     * 代价与 ghost 声明同类；S2 的面板挂载点本身就是"该型已固化"的入口闸。⇒ 记进交付报告的遗留项。
     */
    boolean performMagnetModeCycle() {
        final PocketMagnetFilter filter = magnetFilterToEdit();
        if (filter == null || !filter.cycleMode()) {
            return false;
        }
        commitMagnetFilter(filter);
        return true;
    }

    /** ★加一条名单条目（键串入参，见 {@link #performMagnetModeCycle} 的纪律 2）。@return 本次是否真的改变 */
    boolean performMagnetEntryAdd(String key) {
        final PocketMagnetFilter filter = magnetFilterToEdit();
        if (filter == null || !filter.addEntryKey(key)) {
            return false;
        }
        commitMagnetFilter(filter);
        return true;
    }

    /** ★按键摘一条（同键两条不可能存在：条目集合按身份去重）。@return 本次是否真的改变 */
    boolean performMagnetEntryRemove(String key) {
        final PocketMagnetFilter filter = magnetFilterToEdit();
        if (filter == null || !filter.removeEntryKey(key)) {
            return false;
        }
        commitMagnetFilter(filter);
        return true;
    }

    /**
     * ★清空名单（<b>不动三态</b>）。与"切到无限制"是两件事：后者按 P-11 的读法<b>保留</b>条目，
     * 只有这一条抹掉（{@code PocketMagnetFilter} 类注释三态读法第 1 条）。
     *
     * @return 本次是否真的改变
     */
    boolean performMagnetClearEntries() {
        final PocketMagnetFilter filter = magnetFilterToEdit();
        if (filter == null || !filter.clearEntries()) {
            return false;
        }
        commitMagnetFilter(filter);
        return true;
    }

    /** 名单写口的公共前段：防伪三判 + 载体在场，否则 {@code null}（★调用方据此什么都不写）。 */
    private PocketMagnetFilter magnetFilterToEdit() {
        if (panel.syncManager()
            .isClient() || !serverGuardOk()) {
            return null;
        }
        final ItemStack carrier = panel.pocketStack();
        return carrier == null ? null : PocketMagnetFilter.readFrom(carrier.getTagCompound());
    }

    /** 名单写口的公共后段：落回载体栈根层 + 置脏（★只在真改变时被调）。 */
    private void commitMagnetFilter(PocketMagnetFilter filter) {
        final ItemStack carrier = panel.pocketStack();
        if (carrier == null) {
            return;
        }
        NBTTagCompound root = carrier.getTagCompound();
        if (root == null) {
            // ★写路径可以建档（R53c 禁的是读路径顺手建）；与 writeSessionToCarrier 同一形状
            root = new NBTTagCompound();
            carrier.setTagCompound(root);
        }
        filter.writeTo(root);
        markDirty();
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

    // ------------------ ★R95 S5：16G 双轨的 long 面 + STACK 位查询（转发到 PocketInventory 的真值原语）

    long fluidBarRoomL(int tank, FluidStack probe) {
        return panel.inventory()
            .fluidBarRoomL(tank, probe);
    }

    long depositFluidL(int tank, Fluid fluid, long amount) {
        return panel.inventory()
            .fillOwnTankL(tank, fluid, amount);
    }

    long drainOwnTankL(int tank, long amount) {
        return panel.inventory()
            .drainOwnTankL(tank, amount);
    }

    boolean storageStackUpgraded() {
        return panel.storageStackUpgraded();
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
