package com.miaokatze.gtit.trade.v2;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.TeamDataProvider;
import com.miaokatze.gtit.util.PlayerLookup;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 冷却完毕通知调度器（v1.6.28）
 * <p>
 * 服务端定时（每 {@value #CHECK_INTERVAL} tick ≈ 10 秒）检查所有在线玩家的交易历史，
 * 若某交易组冷却已结束且 {@link NekoTradeHistory#isNotificationQueued()} 为 true，
 * 则向该玩家所在团队的全体在线成员广播聊天消息 + 音效（random.orb）。
 * <p>
 * 设计参考：
 * <ul>
 * <li>VM 模组的 {@code EventHandler.onServerTick} + {@code TradeManager.sendTradeNotifications}：
 * 定时检查 + 冷却结束判定逻辑</li>
 * <li>VM 模组的 {@code NotificationHandler}：聊天消息 + 音效播报</li>
 * <li>GTNHLib 的团队在线成员遍历（O2-04 起经 {@link TeamDataProvider#forEachOnlineMember} 门面）：向团队在线成员广播</li>
 * </ul>
 * <p>
 * 与 VM 的关键差异：VM 仅通知交易者本人（客户端本地播报），本调度器向团队全体在线成员广播，
 * 适用于团队成员共享冷却交易次数的场景。
 * <p>
 * 持久化：通知标记通过 {@link NekoTradeHistory#writeToNBT()} 持久化，服务器重启后未播报的通知不会丢失。
 */
public class NekoNotificationScheduler {

    /** 单例实例 */
    public static final NekoNotificationScheduler INSTANCE = new NekoNotificationScheduler();

    /** 检查间隔（tick），200 tick = 10 秒 */
    private static final int CHECK_INTERVAL = 200;

    /** tick 计数器，达到 CHECK_INTERVAL 时触发一次检查并重置 */
    private int tickCounter = 0;

    private NekoNotificationScheduler() {}

    /**
     * 服务端 tick 事件入口
     * <p>
     * 仅在 {@link TickEvent.Phase#END} 阶段累加计数器，避免一个 tick 内 START/END 双触发。
     * 计数器达到 {@link #CHECK_INTERVAL} 时调用 {@link #checkAllNotifications()} 并重置。
     *
     * @param event 服务端 tick 事件
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // 只在 END 阶段处理，避免一个 tick 内触发两次
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickCounter++;
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            checkAllNotifications();
        }
    }

    /**
     * 检查所有在线玩家的交易历史，对冷却已结束且有待通知的交易组进行团队播报
     * <p>
     * 遍历服务端所有在线玩家，对每个玩家检查其全部交易组历史，
     * 若 {@code notificationQueued == true} 且 {@code getCooldownRemaining == 0}，
     * 则调用 {@link #notifyTeam(EntityPlayerMP, NekoTradeGroup)} 播报并清除标记。
     * <p>
     * 异常防护：整个方法体包 try-catch 捕获 Throwable（含 NoClassDefFoundError），
     * 确保即使 GTNHLib 不可用或数据异常也不会导致服务端 tick 崩溃。
     */
    private void checkAllNotifications() {
        try {
            // 防御性检查：客户端逻辑跳过（服务器未启动由 PlayerLookup 内部静默跳过）
            if (FMLCommonHandler.instance()
                .getSide()
                .isClient()) {
                return;
            }

            // 遍历所有在线玩家（O2-12：PlayerLookup 统一遍历）
            PlayerLookup.forEachOnlinePlayer(player -> {
                UUID playerId = player.getUniqueID();

                // 遍历所有交易组，检查该玩家是否有待播报的冷却完毕通知
                for (Map.Entry<UUID, NekoTradeGroup> entry : NekoTradeDatabase.INSTANCE.getAllTradeGroups()
                    .entrySet()) {
                    NekoTradeGroup group = entry.getValue();
                    // 无冷却的交易组无需通知
                    if (group.getCooldown() <= 0) {
                        continue;
                    }
                    NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, entry.getKey());
                    synchronized (history) {
                        // 无待通知标记则跳过
                        if (!history.isNotificationQueued()) {
                            continue;
                        }
                        // 冷却尚未结束则跳过
                        if (history.getCooldownRemaining(group.getCooldown()) > 0) {
                            continue;
                        }
                        // 冷却已结束且有待通知 → 团队播报
                        notifyTeam(player, group);
                        // 清除通知标记并持久化
                        history.setNotificationQueued(false);
                        NekoHistoryManager.INSTANCE.markDirty(playerId);
                    }
                }
            });
        } catch (Throwable t) {
            // 捕获所有异常（含 NoClassDefFoundError），仅打印日志不崩溃
            GTInterestingThing.LOG.error("[NekoNotify] 冷却完毕通知检查异常", t);
        }
    }

    /**
     * 向玩家所在团队的全体在线成员广播冷却完毕消息 + 音效
     * <p>
     * 若玩家有团队：通过 {@link TeamDataProvider#forEachOnlineMember} 向所有在线团队成员发送消息和音效；
     * 若玩家无团队（team == null）：仅向该玩家本人发送消息和音效。
     * <p>
     * 音效使用 {@code random.orb}（经验球音效），参数 0.2F 音量 / 1.8F 音调，与 VM 模组同款。
     *
     * @param player 触发冷却结束的玩家（在线）
     * @param group  交易组（用于获取分组名）
     */
    private void notifyTeam(EntityPlayerMP player, NekoTradeGroup group) {
        // 构建消息内容：使用交易组分类的 key 作为分组名
        String groupName = "未知";
        try {
            if (group.getCategory() != null && group.getCategory()
                .getKey() != null) {
                groupName = group.getCategory()
                    .getKey();
            }
        } catch (Throwable ignored) {
            // 获取分组名失败时使用默认值
        }
        String message = EnumChatFormatting.YELLOW + "[猫猫售货机] "
            + EnumChatFormatting.GREEN
            + "交易组「"
            + groupName
            + "」冷却已结束，可以再次交易！"
            + EnumChatFormatting.RESET;

        try {
            // O2-04：Teams 探测/降级统一走 TeamDataProvider 门面——不可用时 getTeam 返回 null，
            // 自然落入下方"无团队"个人播报分支（与原 NCDFE 回退语义一致）
            Team team = TeamDataProvider.getTeam(player.getUniqueID());
            if (team != null) {
                // 有团队：向所有在线团队成员广播
                TeamDataProvider.forEachOnlineMember(team, new Consumer<EntityPlayerMP>() {

                    @Override
                    public void accept(EntityPlayerMP member) {
                        try {
                            member.addChatComponentMessage(new ChatComponentText(message));
                            // 在玩家所在世界播放音效（playSoundAtEntity 是 Entity 类方法，1.7.10 可用）
                            member.worldObj.playSoundAtEntity(member, "random.orb", 0.2F, 1.8F);
                        } catch (Throwable t) {
                            // 单个成员播报失败不影响其他成员
                            GTInterestingThing.LOG.error("[NekoNotify] 团队成员播报失败: " + member.getCommandSenderName(), t);
                        }
                    }
                });
            } else {
                // 无团队：仅通知玩家本人
                player.addChatComponentMessage(new ChatComponentText(message));
                player.worldObj.playSoundAtEntity(player, "random.orb", 0.2F, 1.8F);
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoNotify] notifyTeam 异常", t);
        }
    }
}
