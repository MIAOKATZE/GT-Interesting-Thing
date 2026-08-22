package com.miaokatze.gtit.trade.v2;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import betterquesting.api.events.QuestEvent;
import betterquesting.api.questing.IQuest;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * BQ 桥接器，替代 VM 的 BqAdapter
 * <p>
 * 直接对接 BetterQuesting API，带 @Optional 防护，
 * 在 BQ 未加载时安全降级（返回 true / 不执行操作）。
 * <p>
 * 通过 {@link #init()} 检测 BQ 是否加载，
 * 通过 {@link #isQuestCompleted(UUID, UUID)} 查询任务完成状态。
 * <p>
 * 内部维护两个缓存：
 * <ul>
 * <li>{@code questTriggers}：questId → 关联交易组ID集合，用于任务完成时触发交易组刷新</li>
 * <li>{@code completedQuestsCache}：playerId → 已完成questId集合，避免重复查询 BQ API</li>
 * </ul>
 */
public class NekoBqBridge {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** BQ 是否已加载 */
    private static boolean bqLoaded = false;

    /** questId → 关联交易组ID集合（任务完成时需刷新的交易组） */
    private static final Map<UUID, Set<UUID>> questTriggers = new ConcurrentHashMap<>();

    /** playerId → 已完成questId集合（查询缓存，避免重复调用 BQ API） */
    private static final Map<UUID, Set<UUID>> completedQuestsCache = new ConcurrentHashMap<>();

    /** 单例实例，用于注册到事件总线接收事件（@SubscribeEvent 需要实例方法） */
    private static NekoBqBridge INSTANCE;

    /**
     * 检查 BQ 是否已加载
     *
     * @return 已加载返回 true
     */
    public static boolean isBqLoaded() {
        return bqLoaded;
    }

    /**
     * 注册事件监听器
     * <p>
     * 供 CommonProxy.postInit() 调用，解决两个问题：
     * 1. 调用 init() 检测 BQ 是否加载（否则 bqLoaded 永远为 false，所有 BQ 检查走安全回退路径返回 true 不锁定）
     * 2. 注册事件监听器接收 BQ 任务完成/重置事件和玩家登录事件（否则无跨会话状态同步）
     * <p>
     * 内部流程：
     * <ol>
     * <li>调用 {@link #init()} 检测 BQ 是否加载</li>
     * <li>BQ 未加载时跳过注册并记录日志（安全降级）</li>
     * <li>BQ 已加载时创建单例实例并注册到两个事件总线</li>
     * </ol>
     * <p>
     * 注册到两个事件总线的原因：
     * <ul>
     * <li>MinecraftForge.EVENT_BUS：接收 BQ 的 {@link QuestEvent}（任务完成/重置事件）</li>
     * <li>FMLCommonHandler.instance().bus()：接收 {@link PlayerEvent.PlayerLoggedInEvent}（玩家登录事件）</li>
     * </ul>
     * <p>
     * 注意：V2 独立于 VM 的 BqAdapter，直接对接 BQ API，不依赖 VM 的 VendingMachine.isBqLoaded。
     */
    public static void register() {
        // 先检测 BQ 是否加载（设置 bqLoaded 字段）
        init();
        if (!bqLoaded) {
            LOG.info("BetterQuesting 未加载，跳过 NekoBqBridge 事件监听器注册");
            return;
        }
        // 创建单例实例并注册到事件总线
        // @SubscribeEvent 需要实例方法接收事件，所以必须创建实例而非静态注册
        INSTANCE = new NekoBqBridge();
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        LOG.info("NekoBqBridge 事件监听器已注册，监听 BQ 任务事件和玩家登录");
    }

    /**
     * 检查指定玩家是否已完成指定任务
     * <p>
     * 带 @Optional 防护，BQ 未加载时方法体不会被调用。
     * 内部带 try-catch NoClassDefFoundError 防护，确保运行时安全。
     * <p>
     * 查询流程：
     * <ol>
     * <li>先检查 {@code completedQuestsCache}，命中则直接返回 true</li>
     * <li>BQ 未加载时返回 true（安全回退，不阻断交易）</li>
     * <li>调用 BQ API 查询任务状态</li>
     * <li>查询成功且任务已完成时更新缓存</li>
     * </ol>
     *
     * @param playerId 玩家 UUID
     * @param questId  任务 UUID
     * @return 已完成返回 true，未完成返回 false，BQ 未加载或异常返回 true（安全回退）
     */
    @Optional.Method(modid = "betterquesting")
    public static boolean isQuestCompleted(UUID playerId, UUID questId) {
        // 1. 先检查缓存，命中则直接返回
        Set<UUID> completed = completedQuestsCache.get(playerId);
        if (completed != null && completed.contains(questId)) {
            return true;
        }

        // 2. BQ 未加载时安全回退（不阻断交易）
        if (!bqLoaded) {
            return true;
        }

        // 3. 调用 BQ API 查询任务状态
        try {
            // 使用全限定名调用，避免类加载问题
            betterquesting.api.questing.IQuest quest = betterquesting.questing.QuestDatabase.INSTANCE.get(questId);
            if (quest == null) {
                // 任务不存在，安全回退
                return true;
            }
            boolean result = quest.isComplete(playerId);
            // 4. 查询成功且任务已完成时更新缓存
            if (result) {
                setQuestCompleted(playerId, questId);
            }
            return result;
        } catch (NoClassDefFoundError e) {
            // BQ 类缺失（版本不匹配等），安全回退
            LOG.warn("BQ 类加载失败，任务完成检查安全回退: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            // 其他异常，安全回退
            LOG.warn("BQ 任务完成检查异常，安全回退: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 注册任务触发器
     * <p>
     * 将交易组ID关联到任务ID，当任务完成时可触发对应交易组刷新。
     *
     * @param questId 任务 UUID
     * @param groupId 交易组 UUID
     */
    public static void registerQuestTrigger(UUID questId, UUID groupId) {
        questTriggers.computeIfAbsent(questId, k -> ConcurrentHashMap.newKeySet())
            .add(groupId);
    }

    /**
     * 清除所有任务触发器
     */
    public static void clearAllTriggers() {
        questTriggers.clear();
    }

    /**
     * 获取指定任务关联的交易组集合
     *
     * @param questId 任务 UUID
     * @return 关联的交易组ID集合，无关联时返回空集合
     */
    public static Set<UUID> getTriggeredGroups(UUID questId) {
        Set<UUID> groups = questTriggers.get(questId);
        if (groups == null) {
            return java.util.Collections.emptySet();
        }
        return groups;
    }

    /**
     * 标记任务为已完成（更新缓存）
     *
     * @param playerId 玩家 UUID
     * @param questId  任务 UUID
     */
    public static void setQuestCompleted(UUID playerId, UUID questId) {
        completedQuestsCache.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
            .add(questId);
    }

    /**
     * 标记任务为未完成（从缓存中移除）
     *
     * @param playerId 玩家 UUID
     * @param questId  任务 UUID
     */
    public static void setQuestUncompleted(UUID playerId, UUID questId) {
        Set<UUID> completed = completedQuestsCache.get(playerId);
        if (completed != null) {
            completed.remove(questId);
        }
    }

    /**
     * 初始化 BQ 桥接器
     * <p>
     * 检测 BQ 是否加载，通过反射检查 BQ API 类是否存在。
     */
    public static void init() {
        try {
            Class.forName("betterquesting.api.questing.IQuest");
            bqLoaded = true;
        } catch (ClassNotFoundException e) {
            bqLoaded = false;
        }
    }

    /**
     * 监听 BQ 任务事件
     * <p>
     * 处理两种事件类型：
     * <ul>
     * <li>{@link QuestEvent.Type#COMPLETED}：玩家完成任务 → 调用 {@link #setQuestCompleted} 更新缓存</li>
     * <li>{@link QuestEvent.Type#RESET}：任务被重置 → 调用 {@link #setQuestUncompleted} 更新缓存</li>
     * </ul>
     * <p>
     * V2 与 V1 的差异：
     * V1 调用 VM 的 NetSatisfiedQuestSync.sendSync() 同步到客户端；
     * V2 不需要主动同步，因为 V2 通过 GUI 的 bqLockStatusSync 同步值在 GUI 打开时刷新，
     * BQ 事件触发后缓存更新，下次 GUI 同步时会读取新状态。
     * 暂不实现立即刷新（依赖 GUI 周期性同步），以降低复杂度。
     * <p>
     * 必须是实例方法（非静态），因为 @SubscribeEvent 需要实例接收事件。
     * <p>
     * {@code @Optional.Method(modid = "betterquesting")} 防护的必要性：
     * QuestEvent 类属于 BQ 模组，BQ 未加载时直接引用会导致类加载崩溃。
     * 该注解在 BQ 未加载时剥离方法体，避免类加载触发 BQ 类引用。
     *
     * @param event BQ 任务事件，包含玩家ID、任务ID集合和事件类型
     */
    @SubscribeEvent
    @Optional.Method(modid = "betterquesting")
    public void onQuestEvent(QuestEvent event) {
        UUID playerId = event.getPlayerID();
        Set<UUID> questIds = event.getQuestIDs();

        // 防御性检查：玩家ID或任务ID集合为空时跳过
        if (playerId == null || questIds == null || questIds.isEmpty()) {
            return;
        }

        switch (event.getType()) {
            case COMPLETED:
                // 任务完成：更新缓存，后续 isQuestCompleted 查询可直接命中缓存
                for (UUID questId : questIds) {
                    setQuestCompleted(playerId, questId);
                    LOG.info("V2 BQ 任务完成: player={}, questId={}", playerId, questId);
                }
                break;
            case RESET:
                // 任务重置：从缓存中移除，后续查询会重新调用 BQ API
                for (UUID questId : questIds) {
                    setQuestUncompleted(playerId, questId);
                    LOG.info("V2 BQ 任务重置: player={}, questId={}", playerId, questId);
                }
                break;
            default:
                // 其他事件类型（如 UPDATED）不处理
                break;
        }
    }

    /**
     * 监听玩家登录事件
     * <p>
     * 在玩家登录时，遍历 V2 交易数据库中所有绑定了 BQ 任务的交易组，
     * 查询 BQ API 获取任务完成状态，更新本地缓存。
     * <p>
     * 这解决了跨会话的任务完成状态同步问题：
     * BQ 的 {@link QuestEvent} 只在任务状态变化时触发，
     * 玩家之前会话中完成的任务不会在登录时触发事件，
     * 因此需要主动查询 BQ API 同步状态到缓存。
     * <p>
     * 必须是实例方法（非静态），因为 @SubscribeEvent 需要实例接收事件。
     * <p>
     * {@code @Optional.Method(modid = "betterquesting")} 防护的必要性：
     * 方法体内引用了 IQuest 和 QuestDatabase 等 BQ 类，
     * BQ 未加载时该注解会剥离方法体，避免类加载崩溃。
     *
     * @param event 玩家登录事件，包含玩家实体
     */
    @SubscribeEvent
    @Optional.Method(modid = "betterquesting")
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player == null) return;

        UUID playerId = event.player.getUniqueID();
        // 记录已同步的任务ID，避免同一任务被多个交易组重复查询
        Set<UUID> syncedQuests = new HashSet<>();

        // 遍历所有 V2 交易组，检查 BQ 任务完成状态
        for (Map.Entry<UUID, NekoTradeGroup> entry : NekoTradeDatabase.INSTANCE.getAllTradeGroups()
            .entrySet()) {
            NekoTradeGroup group = entry.getValue();
            String bqQuestIdStr = group.getBqQuestId();
            // 无 BQ 绑定的交易组跳过
            if (bqQuestIdStr == null || bqQuestIdStr.isEmpty()) continue;

            // 解析任务 ID（支持多种格式：high:low、UUID、Base64）
            UUID questId = NekoBqQuestIdParser.parse(bqQuestIdStr);
            if (questId == null || syncedQuests.contains(questId)) continue;

            // 直接查询 BQ API 获取任务完成状态
            try {
                IQuest quest = QuestDatabase.INSTANCE.get(questId);
                if (quest != null && quest.isComplete(playerId)) {
                    // 任务已完成，更新缓存
                    setQuestCompleted(playerId, questId);
                    syncedQuests.add(questId);
                    LOG.info("V2 玩家登录同步 BQ 任务: player={}, questId={}", playerId, questId);
                }
            } catch (Exception e) {
                LOG.warn("V2 查询 BQ 任务完成状态失败: questId={}, player={}", questId, playerId, e);
            }
        }

        if (!syncedQuests.isEmpty()) {
            LOG.info("V2 玩家 {} 登录同步了 {} 个已完成的 BQ 任务", playerId, syncedQuests.size());
        }
    }
}
