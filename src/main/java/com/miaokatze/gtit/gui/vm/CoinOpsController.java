package com.miaokatze.gtit.gui.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 货币/物品操作控制器（A01 蓝图 G5 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 持有投币/弹出/导入动作族（doNekoXxx 七件 + 输入槽强刷 + 世界掉落 + 服务端音效三件）
 * 与其 C2S 通道注册回调（eject/import/fill 动作类按切分单归本域）。跨域触点经构造
 * 注入窄回调：机器/基础 TE 直引、isClient/playerId 供应商、交易结果消息写与通知、
 * 货币余额同步值共享 Map、可交易状态置脏通知。
 * <p>
 * <b>双端镜像构建</b>：注册与动作在服务端同样触达（v1.7.17），类内不得有客户端专属 API
 * 的静态引用；{@link #isClient()} 守卫语义与宿主原方法一致。
 */
public final class CoinOpsController {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 机器引用（槽位读写/出货/ME uplink） */
    private final MTENekoVendingMachineV2 multiblock;
    /** 基础 TE 引用（世界坐标/存活守卫/音效） */
    private final IGregTechTileEntity baseMetaTileEntity;
    /** 宿主 isClient() 委托 */
    private final Supplier<Boolean> isClientCheck;
    /** 宿主 getPlayerId() 委托 */
    private final Supplier<UUID> playerIdSupplier;
    /** 交易结果消息纯写（仅更新宿主消息字段，不触发通道） */
    private final Consumer<String> tradeMessageSetter;
    /** 交易结果消息写+通知（更新宿主消息字段并 tradeResultSync.setValue） */
    private final Consumer<String> tradeResultNotifier;
    /** 各货币余额同步值映射（宿主注册、本域消费：入账后立即刷新余额显示） */
    private final Map<String, IntSyncValue> coinAmountSyncs;
    /** ME 货币余额同步值映射（宿主注册、本域消费：导入后刷新按钮显示） */
    private final Map<String, IntSyncValue> meCoinAmountSyncs;
    /** StatusCodec 置脏+重同步委托（钱包余额变化影响可交易性） */
    private final Runnable markTradeableStatusDirtyAndNotify;
    /** 宿主开 GUI 玩家供应商（guiData.getPlayer() 委托，填充背包动作用） */
    private final Supplier<EntityPlayer> openerPlayerSupplier;

    /** 弹出所有猫猫币标志（服务端处理后重置为 false） */
    private boolean nekoEjectAllCoins = false;
    /** 导入猫猫币标志（服务端处理后重置为 false） */
    private boolean nekoImportCoins = false;
    /** 弹出物品（清空输出槽）标志（服务端处理后重置为 false） */
    private boolean nekoEjectItems = false;
    /** 填充玩家背包标志（服务端处理后重置为 false） */
    private boolean nekoFillPlayerInventory = false;
    /** 弹出单种猫猫币标志映射 */
    private final Map<String, Boolean> nekoEjectSingleCoin = new HashMap<>();
    /** 弹出一组（64个）单种猫猫币标志映射（v1.6.22：Ctrl+点击弹出 1 组） */
    private final Map<String, Boolean> nekoEjectCoinStack = new HashMap<>();
    /** ME 货币导入请求标志（currencyId → flag，阶段 6） */
    private final Map<String, Boolean> nekoImportMeCoin = new HashMap<>();
    /** ME 货币导入同步值映射（供按钮调用 setValue，阶段 6） */
    private final Map<String, BooleanSyncValue> nekoImportMeCoinSyncs = new HashMap<>();

    /** 弹出所有猫猫币（C2S） */
    private BooleanSyncValue ejectAllCoinsSync;
    /** 导入猫猫币（C2S） */
    private BooleanSyncValue importCoinsSync;
    /** 弹出物品（清空输出槽）开关（C2S） */
    private BooleanSyncValue ejectItemsSync;
    /** 填充玩家背包开关（C2S，Shift+左键出货槽触发） */
    private BooleanSyncValue fillPlayerInventorySync;

    /** 输入槽 Widget 引用（服务端在弹出/入账后强制同步槽位状态到客户端） */
    private final List<ItemSlot> inputSlotRefs = new ArrayList<>();

    public CoinOpsController(MTENekoVendingMachineV2 multiblock, IGregTechTileEntity baseMetaTileEntity,
        Supplier<Boolean> isClientCheck, Supplier<UUID> playerIdSupplier, Consumer<String> tradeMessageSetter,
        Consumer<String> tradeResultNotifier, Map<String, IntSyncValue> coinAmountSyncs,
        Map<String, IntSyncValue> meCoinAmountSyncs, Runnable markTradeableStatusDirtyAndNotify,
        Supplier<EntityPlayer> openerPlayerSupplier) {
        this.multiblock = multiblock;
        this.baseMetaTileEntity = baseMetaTileEntity;
        this.isClientCheck = isClientCheck;
        this.playerIdSupplier = playerIdSupplier;
        this.tradeMessageSetter = tradeMessageSetter;
        this.tradeResultNotifier = tradeResultNotifier;
        this.coinAmountSyncs = coinAmountSyncs;
        this.meCoinAmountSyncs = meCoinAmountSyncs;
        this.markTradeableStatusDirtyAndNotify = markTradeableStatusDirtyAndNotify;
        this.openerPlayerSupplier = openerPlayerSupplier;
        // 初始化各货币的弹出标志
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            nekoEjectSingleCoin.put(currencyId, false);
        }
    }

    /** 宿主 isClient() 语义等价委托（同步管理器视角） */
    private boolean isClient() {
        return isClientCheck.get()
            .booleanValue();
    }

    /** 宿主 getPlayerId() 语义等价委托 */
    private UUID getPlayerId() {
        return playerIdSupplier.get();
    }

    /**
     * 注册货币/物品操作 C2S 通道（A01 蓝图 G5 分域下沉，注册体逐字搬移）
     *
     * @param syncManager 面板同步管理器
     * @param playerId    玩家 UUID
     */
    public void registerSyncValues(PanelSyncManager syncManager, UUID playerId) {
        // --- ME 货币导入请求（C2S，阶段 6）---
        // 客户端点击导入按钮时 setValue(true)，服务端处理提取并加到钱包
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            nekoImportMeCoin.put(cid, false);
            BooleanSyncValue importSync = new BooleanSyncValue(() -> nekoImportMeCoin.getOrDefault(cid, false), val -> {
                if (syncManager != null && !syncManager.isClient()) {
                    // B2-02：动作主体（含 HashMap put）整体投递服务器主线程
                    NekoVMGuiV2.scheduleServerAction(() -> {
                        if (val) {
                            doNekoImportMeCoin(cid);
                        }
                        nekoImportMeCoin.put(cid, false);
                    });
                    return;
                }
                // 客户端：仅本地标志维护（无服务端动作）
                nekoImportMeCoin.put(cid, false);
            });
            importSync.allowC2S();
            syncManager.syncValue("nekoV2ImportMeCoin_" + currencyId, importSync);
            nekoImportMeCoinSyncs.put(currencyId, importSync);
        }

        // --- 弹出单种猫猫币（C2S）---
        // 注意：syncHandler 名称必须与 NekoCoinDisplayV2 期望的一致
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            BooleanSyncValue ejectCoinSyncer = new BooleanSyncValue(
                () -> nekoEjectSingleCoin.getOrDefault(cid, false),
                val -> {
                    if (syncManager != null && !syncManager.isClient()) {
                        // B2-02：动作主体（含 HashMap put）整体投递服务器主线程
                        NekoVMGuiV2.scheduleServerAction(() -> {
                            nekoEjectSingleCoin.put(cid, val);
                            if (val) {
                                doNekoEjectCoin(cid, playerId);
                            }
                        });
                        return;
                    }
                    // 客户端：仅本地标志维护（doNekoEjectCoin 内部 isClient() 守卫直接返回）
                    nekoEjectSingleCoin.put(cid, val);
                    if (val) {
                        doNekoEjectCoin(cid, playerId);
                    }
                });
            ejectCoinSyncer.allowC2S();
            syncManager.syncValue("nekoEjectCoin_" + currencyId, ejectCoinSyncer);
        }

        // --- 弹出一组单种猫猫币（C2S：v1.6.22 新增，Ctrl+点击触发）---
        // 与 nekoEjectCoin_ 区别：仅弹出 1 组（64 个，不足则全部），而非全部余额
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            BooleanSyncValue ejectCoinStackSyncer = new BooleanSyncValue(
                () -> nekoEjectCoinStack.getOrDefault(cid, false),
                val -> {
                    if (syncManager != null && !syncManager.isClient()) {
                        // B2-02：动作主体（含 HashMap put）整体投递服务器主线程
                        NekoVMGuiV2.scheduleServerAction(() -> {
                            nekoEjectCoinStack.put(cid, val);
                            if (val) {
                                doNekoEjectCoinStack(cid, playerId);
                            }
                        });
                        return;
                    }
                    // 客户端：仅本地标志维护（doNekoEjectCoinStack 内部 isClient() 守卫直接返回）
                    nekoEjectCoinStack.put(cid, val);
                    if (val) {
                        doNekoEjectCoinStack(cid, playerId);
                    }
                });
            ejectCoinStackSyncer.allowC2S();
            syncManager.syncValue("nekoEjectCoinStack_" + currencyId, ejectCoinStackSyncer);
        }

        // --- 弹出所有猫猫币（C2S）---
        ejectAllCoinsSync = new BooleanSyncValue(() -> nekoEjectAllCoins, val -> {
            if (syncManager != null && !syncManager.isClient()) {
                // B2-02：动作主体（含标志赋值）整体投递服务器主线程
                NekoVMGuiV2.scheduleServerAction(() -> {
                    nekoEjectAllCoins = val;
                    if (val) {
                        doNekoEjectAllCoins(playerId);
                    }
                });
                return;
            }
            // 客户端：仅本地标志维护（doNekoEjectAllCoins 内部 isClient() 守卫直接返回）
            nekoEjectAllCoins = val;
            if (val) {
                doNekoEjectAllCoins(playerId);
            }
        });
        ejectAllCoinsSync.allowC2S();
        syncManager.syncValue("nekoV2EjectAllCoins", ejectAllCoinsSync);

        // --- 导入猫猫币（C2S）---
        importCoinsSync = new BooleanSyncValue(() -> nekoImportCoins, val -> {
            if (syncManager != null && !syncManager.isClient()) {
                // B2-02：动作主体（含标志赋值）整体投递服务器主线程
                NekoVMGuiV2.scheduleServerAction(() -> {
                    nekoImportCoins = val;
                    if (val) {
                        doNekoImportCoins(playerId);
                    }
                });
                return;
            }
            // 客户端：仅本地标志维护（doNekoImportCoins 内部 isClient() 守卫直接返回）
            nekoImportCoins = val;
            if (val) {
                doNekoImportCoins(playerId);
            }
        });
        importCoinsSync.allowC2S();
        syncManager.syncValue("nekoV2ImportCoins", importCoinsSync);

        // --- 弹出物品（清空输出槽并掉落到机器旁，C2S）---
        ejectItemsSync = new BooleanSyncValue(() -> nekoEjectItems, val -> {
            if (syncManager != null && !syncManager.isClient()) {
                // B2-02：动作主体（含标志赋值）整体投递服务器主线程
                NekoVMGuiV2.scheduleServerAction(() -> {
                    nekoEjectItems = val;
                    if (val) {
                        doNekoEjectItems();
                    }
                });
                return;
            }
            // 客户端：仅本地标志维护（doNekoEjectItems 内部 isClient() 守卫直接返回）
            nekoEjectItems = val;
            if (val) {
                doNekoEjectItems();
            }
        });
        ejectItemsSync.allowC2S();
        syncManager.syncValue("nekoV2EjectItems", ejectItemsSync);

        // --- 填充玩家背包（C2S，Shift+左键出货槽时触发）---
        fillPlayerInventorySync = new BooleanSyncValue(() -> nekoFillPlayerInventory, val -> {
            if (syncManager != null && !syncManager.isClient()) {
                // B2-02：动作主体（含标志赋值）整体投递服务器主线程
                NekoVMGuiV2.scheduleServerAction(() -> {
                    nekoFillPlayerInventory = val;
                    if (val) {
                        doNekoFillPlayerInventory(playerId);
                    }
                });
                return;
            }
            // 客户端：仅本地标志维护（doNekoFillPlayerInventory 内部 isClient() 守卫直接返回）
            nekoFillPlayerInventory = val;
            if (val) {
                doNekoFillPlayerInventory(playerId);
            }
        });
        fillPlayerInventorySync.allowC2S();
        syncManager.syncValue("nekoV2FillPlayerInventory", fillPlayerInventorySync);
    }

    /** 输入槽引用表（IO 列构建时填充，供弹出/入账后强刷） */
    public List<ItemSlot> getInputSlotRefs() {
        return inputSlotRefs;
    }

    /** ME 货币导入同步值查询（货币显示行的导入按钮消费） */
    public BooleanSyncValue getImportMeCoinSync(String currencyId) {
        return nekoImportMeCoinSyncs.get(currencyId);
    }

    /** 弹出物品按钮通道（IO 列弹射按钮消费） */
    public BooleanSyncValue getEjectItemsSync() {
        return ejectItemsSync;
    }

    /** 弹出所有猫猫币按钮通道（IO 列弹射按钮消费） */
    public BooleanSyncValue getEjectAllCoinsSync() {
        return ejectAllCoinsSync;
    }

    /** 填充玩家背包按钮通道（出货槽覆盖按钮消费） */
    public BooleanSyncValue getFillPlayerInventorySync() {
        return fillPlayerInventorySync;
    }

    /**
     * 播放交易成功音效
     * <p>
     * 在服务端机器位置播放本项目内置的 {@code gtit:trade_success} 音效（3 变体随机），
     * 会自动广播给附近所有玩家。音效资源复制自 VM mod 的 coin_insert，内置到 gtit 命名空间以减小外部依赖。
     * （跨域公开：宿主 processTradeRequest 消费，A01 蓝图 G5 音效三件随域下沉）
     */
    public void playTradeSuccessSound() {
        if (baseMetaTileEntity == null) return;
        World world = baseMetaTileEntity.getWorld();
        if (world == null || world.isRemote) return;

        world.playSoundEffect(
            baseMetaTileEntity.getXCoord() + 0.5,
            baseMetaTileEntity.getYCoord() + 0.5,
            baseMetaTileEntity.getZCoord() + 0.5,
            "gtit:trade_success",
            1.0f,
            1.0f);
    }

    /**
     * 弹出单种猫猫币的全部余额
     *
     * @param currencyId 货币 ID
     * @param playerId   玩家 UUID
     */
    private void doNekoEjectCoin(String currencyId, UUID playerId) {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoEjectSingleCoin.put(currencyId, false);
            return;
        }
        try {
            if (playerId == null) {
                nekoEjectSingleCoin.put(currencyId, false);
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null || wallet.getCount(currencyId) <= 0) {
                nekoEjectSingleCoin.put(currencyId, false);
                return;
            }

            int count = wallet.getCount(currencyId);
            java.util.List<ItemStack> toDispense = new java.util.ArrayList<>();
            while (count > 0) {
                int stackSize = Math.min(count, 64);
                ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, stackSize);
                if (stack != null) {
                    toDispense.add(stack);
                }
                count -= stackSize;
            }

            if (!toDispense.isEmpty()) {
                // 投放前检查：输出槽是否还有空位（考虑 outputBuffer 已堆积的情况）
                // 可用空位数 = 当前空槽数 - 队列已占用的虚拟槽位数
                int emptySlots = multiblock.getOutputEmptySlotCount();
                int queuedItems = multiblock.getOutputBufferSize();
                if (emptySlots - queuedItems <= 0) {
                    // 输出槽已满（含队列堆积），不扣钱，提示玩家
                    LOG.warn("[NekoVMV2] doNekoEjectCoin 输出槽已满，取消弹出货币 {}", currencyId);
                    tradeResultNotifier.accept("出货槽已满，无法弹出猫猫币");
                    return;
                }
                multiblock.dispenseItemStacks(toDispense);
                wallet.resetCount(currencyId);
                playCoinDropSound();
                // 交易状态可能受影响，标记为脏
                markTradeableStatusDirtyAndNotify.run();
            }
        } catch (Throwable t) {
            LOG.error("[NekoVMV2] doNekoEjectCoin 异常!", t);
        } finally {
            nekoEjectSingleCoin.put(currencyId, false);
        }
    }

    /**
     * 弹出一组（64个）单种猫猫币
     * <p>
     * 与 {@link #doNekoEjectCoin} 类似，但仅弹出 1 组（64 个，不足则弹出全部）。
     * 由 Ctrl+点击弹出按钮触发（v1.6.22 新增）。
     *
     * @param currencyId 货币 ID
     * @param playerId   玩家 UUID
     */
    private void doNekoEjectCoinStack(String currencyId, UUID playerId) {
        // 客户端不执行服务端逻辑
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoEjectCoinStack.put(currencyId, false);
            return;
        }
        try {
            if (playerId == null) {
                nekoEjectCoinStack.put(currencyId, false);
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null || wallet.getCount(currencyId) <= 0) {
                nekoEjectCoinStack.put(currencyId, false);
                return;
            }

            // 仅弹出 1 组（64 个，不足则全部）
            int walletBalance = wallet.getCount(currencyId);
            int count = Math.min(walletBalance, 64);
            java.util.List<ItemStack> toDispense = new java.util.ArrayList<>();
            ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, count);
            if (stack != null) {
                toDispense.add(stack);
            }

            if (!toDispense.isEmpty()) {
                // 投放前检查输出槽容量
                int emptySlots = multiblock.getOutputEmptySlotCount();
                int queuedItems = multiblock.getOutputBufferSize();
                if (emptySlots - queuedItems <= 0) {
                    LOG.warn("[NekoVMV2] doNekoEjectCoinStack 输出槽已满，取消弹出货币 {}", currencyId);
                    tradeResultNotifier.accept("出货槽已满，无法弹出猫猫币");
                    return;
                }
                multiblock.dispenseItemStacks(toDispense);
                // 扣减钱包余额（而非 resetCount）
                wallet.addCount(currencyId, -count);
                playCoinDropSound();
                // 通知余额同步值刷新
                IntSyncValue coinSync = coinAmountSyncs.get(currencyId);
                if (coinSync != null) {
                    coinSync.setValue(wallet.getCount(currencyId));
                }
                // 交易状态可能受影响，标记为脏
                markTradeableStatusDirtyAndNotify.run();
            }
        } catch (Throwable t) {
            LOG.error("[NekoVMV2] doNekoEjectCoinStack 异常!", t);
        } finally {
            nekoEjectCoinStack.put(currencyId, false);
        }
    }

    /**
     * 弹出所有猫猫币
     * <p>
     * 前提：机器必须已成型且处于 active 状态；否则直接重置标志并返回。
     *
     * @param playerId 玩家 UUID
     */
    private void doNekoEjectAllCoins(UUID playerId) {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        // 重置标志位避免客户端 UI 卡在 true 状态
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoEjectAllCoins = false;
            return;
        }
        try {
            if (playerId == null) {
                nekoEjectAllCoins = false;
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) {
                nekoEjectAllCoins = false;
                return;
            }

            java.util.List<ItemStack> toDispense = new java.util.ArrayList<>();
            for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
                int count = wallet.getCount(currencyId);
                while (count > 0) {
                    int stackSize = Math.min(count, 64);
                    ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, stackSize);
                    if (stack != null) {
                        toDispense.add(stack);
                    }
                    count -= stackSize;
                }
                wallet.resetCount(currencyId);
            }

            if (!toDispense.isEmpty()) {
                multiblock.dispenseItemStacks(toDispense);
                playCoinDropSound();
                // 交易状态可能受影响，标记为脏
                markTradeableStatusDirtyAndNotify.run();
            }
        } catch (Throwable t) {
            LOG.error("[NekoVMV2] doNekoEjectAllCoins 异常!", t);
        } finally {
            nekoEjectAllCoins = false;
        }
    }

    /**
     * 弹出物品
     * <p>
     * 扫描内置输入槽（{@code inputItems}），将所有非空物品复制后优先写入出货槽
     * （{@code outputItems}）以触发 {@code NekoFallingItemSlotFactory} 掉落动画；
     * 出货槽空间不足时，剩余物品掉落到机器旁。最后清空输入槽并强制同步到客户端。
     * <p>
     * 与 V1 的 {@code ejectItems} 行为一致：弹出的是输入槽中的物品（猫猫币通常
     * 已被 changeListener 自动导入钱包，不会滞留在此处）。
     */
    private void doNekoEjectItems() {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoEjectItems = false;
            return;
        }
        try {
            java.util.List<ItemStack> toDispense = new java.util.ArrayList<>();
            for (int i = 0; i < MTENekoVendingMachineV2.INPUT_SLOTS; i++) {
                ItemStack stack = multiblock.inputItems.getStackInSlot(i);
                if (stack == null || stack.stackSize <= 0) continue;
                toDispense.add(stack.copy());
                multiblock.inputItems.setStackInSlot(i, null);
            }

            if (!toDispense.isEmpty()) {
                multiblock.dispenseItemStacks(toDispense);
                forceSyncInputSlots();
                playItemDropSound();
            }
        } catch (Throwable t) {
            LOG.error("[NekoVMV2] doNekoEjectItems 异常!", t);
        } finally {
            nekoEjectItems = false;
        }
    }

    /**
     * 填充玩家背包
     * <p>
     * 复刻 V1/VM 父类的 fillPlayerInventoryWithDispensedItems：
     * 遍历出货槽，将物品移到玩家背包；无法放入的物品保留在槽中。
     * 前提：机器必须已成型且处于 active 状态。
     *
     * @param playerId 玩家 UUID
     */
    private void doNekoFillPlayerInventory(UUID playerId) {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoFillPlayerInventory = false;
            return;
        }
        try {
            if (playerId == null) {
                nekoFillPlayerInventory = false;
                return;
            }
            EntityPlayer player = openerPlayerSupplier.get();
            if (player == null) {
                nekoFillPlayerInventory = false;
                return;
            }
            multiblock.fillPlayerInventoryWithDispensedItems(player);
            forceSyncInputSlots();
        } catch (Throwable t) {
            LOG.error("[NekoVMV2] doNekoFillPlayerInventory 异常!", t);
        } finally {
            nekoFillPlayerInventory = false;
        }
    }

    /**
     * 导入输入槽中的全部猫猫币到玩家钱包
     *
     * @param playerId 玩家 UUID
     */
    private void doNekoImportCoins(UUID playerId) {
        // [NekoImportCoins] 诊断日志：方法入口
        LOG.debug(
            "[NekoImportCoins] 方法入口: playerId=" + playerId
                + " thread="
                + Thread.currentThread()
                    .getName());
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient()) {
            LOG.debug("[NekoImportCoins] 客户端分支，提前返回");
            nekoImportCoins = false;
            return;
        }
        try {
            if (playerId == null) {
                LOG.debug("[NekoImportCoins] playerId 为 null, 提前返回");
                nekoImportCoins = false;
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) {
                LOG.debug("[NekoImportCoins] wallet 为 null, 提前返回");
                nekoImportCoins = false;
                return;
            }

            int totalImported = 0;
            for (int i = 0; i < MTENekoVendingMachineV2.INPUT_SLOTS; i++) {
                ItemStack stack = multiblock.inputItems.getStackInSlot(i);
                if (stack == null) {
                    LOG.debug("[NekoImportCoins] slotIdx=" + i + " stack=null, 跳过");
                    continue;
                }
                String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(stack);
                LOG.debug(
                    "[NekoImportCoins] slotIdx=" + i
                        + " stack="
                        + stack.getDisplayName()
                        + " stackSize="
                        + stack.stackSize
                        + " currencyId="
                        + currencyId);
                if (currencyId != null) {
                    wallet.addCount(currencyId, stack.stackSize);
                    int newCount = wallet.getCount(currencyId);
                    LOG.debug(
                        "[NekoImportCoins] addCount 完成: slotIdx=" + i
                            + " currencyId="
                            + currencyId
                            + " added="
                            + stack.stackSize
                            + " newCount="
                            + newCount);
                    totalImported += stack.stackSize;
                    multiblock.inputItems.setStackInSlot(i, null);
                    LOG.debug("[NekoImportCoins] setStackInSlot(null) 完成: slotIdx=" + i);
                }
            }

            if (totalImported > 0) {
                tradeMessageSetter.accept("成功导入 " + totalImported + " 个猫猫币");
                LOG.debug("[NekoImportCoins] 总计导入: totalImported=" + totalImported);
            } else {
                tradeMessageSetter.accept("输入槽中未找到猫猫币");
                LOG.debug("[NekoImportCoins] 总计导入: totalImported=0（未找到猫猫币）");
            }
        } catch (Throwable t) {
            LOG.error("[NekoVMV2] doNekoImportCoins 异常!", t);
            tradeMessageSetter.accept("导入猫猫币失败");
        } finally {
            nekoImportCoins = false;
        }
    }

    /**
     * 从 ME 网络导入指定货币到玩家钱包（阶段 6）
     * <p>
     * 由 GUI 导入按钮触发（通过 nekoV2ImportMeCoin_ 同步值 C2S）。
     * 查询 ME 余额，提取全部，加到玩家钱包，并刷新货币余额同步值。
     *
     * @param currencyId 货币 ID（如 "neko"、"shimmeringNeko"）
     */
    private void doNekoImportMeCoin(String currencyId) {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient()) {
            nekoImportMeCoin.put(currencyId, false);
            return;
        }
        try {
            UUID playerId = getPlayerId();
            if (playerId == null || multiblock == null || !multiblock.hasUplink()) {
                tradeResultNotifier.accept("未连接 Uplink，无法从 ME 导入");
                return;
            }
            // 查询 ME 余额
            int meAmount = multiblock.getUplinkCurrencyAmount(currencyId);
            if (meAmount <= 0) {
                tradeResultNotifier.accept("ME 网络中无" + NekoCurrencyRegistrar.getDisplayName(currencyId));
                return;
            }
            // 从 ME 提取（extractFromUplink 返回未满足的剩余数量）
            ItemStack coinStack = NekoCurrencyRegistrar.getItemStack(currencyId, meAmount);
            if (coinStack == null) {
                tradeResultNotifier.accept("货币物品栈创建失败");
                return;
            }
            int remain = multiblock.extractFromUplink(coinStack);
            int extracted = meAmount - remain;
            if (extracted > 0) {
                // 加到玩家钱包（使用 addCount，与 doNekoImportCoins 一致）
                NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                if (wallet != null) {
                    wallet.addCount(currencyId, extracted);
                    // 刷新货币余额同步值，使余额显示立即更新
                    IntSyncValue coinSync = coinAmountSyncs.get(currencyId);
                    if (coinSync != null) {
                        coinSync.setValue(wallet.getCount(currencyId));
                    }
                    // 刷新 ME 货币余额同步值，使导入按钮显示立即更新
                    IntSyncValue meSync = meCoinAmountSyncs.get(currencyId);
                    if (meSync != null) {
                        meSync.notifyUpdate();
                    }
                    // 标记可交易状态为脏（钱包余额变化影响可交易性）
                    markTradeableStatusDirtyAndNotify.run();
                    tradeResultNotifier
                        .accept("从 ME 导入 " + extracted + " 个" + NekoCurrencyRegistrar.getDisplayName(currencyId));
                }
            } else {
                tradeResultNotifier.accept("ME 提取失败");
            }
        } catch (Throwable t) {
            LOG.error("[NekoVMV2] doNekoImportMeCoin 异常!", t);
            tradeResultNotifier.accept("ME 货币导入失败");
        } finally {
            nekoImportMeCoin.put(currencyId, false);
        }
    }

    /**
     * 强制同步输入槽到客户端
     * <p>
     * 在输入槽内容发生变化后（如弹出物品、自动导入猫猫币）调用，
     * 确保客户端立即看到最新槽位状态。
     */
    private void forceSyncInputSlots() {
        // [NekoForceSync] 诊断日志：方法入口
        LOG.debug(
            "[NekoForceSync] 方法入口: inputSlotRefs.size()=" + inputSlotRefs.size()
                + " thread="
                + Thread.currentThread()
                    .getName());
        for (int i = 0; i < inputSlotRefs.size(); i++) {
            ItemSlot slot = inputSlotRefs.get(i);
            boolean hasSyncHandler = (slot != null && slot.getSyncHandler() != null);
            LOG.debug(
                "[NekoForceSync] slotIdx=" + i + " slotNull=" + (slot == null) + " hasSyncHandler=" + hasSyncHandler);
            if (hasSyncHandler) {
                slot.getSyncHandler()
                    .forceSyncItem();
                LOG.debug("[NekoForceSync] forceSyncItem 已调用: slotIdx=" + i);
            }
        }
    }

    /**
     * 在机器旁弹出物品
     *
     * @param stack 要弹出的物品栈
     */
    private void dropItemsNearMachine(ItemStack stack) {
        if (stack == null || baseMetaTileEntity == null) return;
        World world = baseMetaTileEntity.getWorld();
        if (world == null || world.isRemote) return;

        double x = baseMetaTileEntity.getXCoord() + 0.5;
        double y = baseMetaTileEntity.getYCoord() + 0.5;
        double z = baseMetaTileEntity.getZCoord() + 0.5;

        EntityItem entity = new EntityItem(world, x, y, z, stack);
        entity.motionX = world.rand.nextDouble() * 0.2 - 0.1;
        entity.motionY = 0.2;
        entity.motionZ = world.rand.nextDouble() * 0.2 - 0.1;
        world.spawnEntityInWorld(entity);
    }

    /**
     * 播放 coin_drop 音效
     * <p>
     * 在服务端机器位置播放 VM mod 的 {@code vendingmachine:coin_drop} 音效，
     * 会自动广播给附近所有玩家。
     */
    private void playCoinDropSound() {
        if (baseMetaTileEntity == null) return;
        World world = baseMetaTileEntity.getWorld();
        if (world == null || world.isRemote) return;

        world.playSoundEffect(
            baseMetaTileEntity.getXCoord() + 0.5,
            baseMetaTileEntity.getYCoord() + 0.5,
            baseMetaTileEntity.getZCoord() + 0.5,
            "vendingmachine:coin_drop",
            1.0f,
            1.0f);
    }

    /**
     * 播放 item_drop 音效
     * <p>
     * 在服务端机器位置播放 VM mod 的 {@code vendingmachine:item_drop} 音效，
     * 会自动广播给附近所有玩家。
     */
    private void playItemDropSound() {
        if (baseMetaTileEntity == null) return;
        World world = baseMetaTileEntity.getWorld();
        if (world == null || world.isRemote) return;

        world.playSoundEffect(
            baseMetaTileEntity.getXCoord() + 0.5,
            baseMetaTileEntity.getYCoord() + 0.5,
            baseMetaTileEntity.getZCoord() + 0.5,
            "vendingmachine:item_drop",
            1.0f,
            1.0f);
    }
}
