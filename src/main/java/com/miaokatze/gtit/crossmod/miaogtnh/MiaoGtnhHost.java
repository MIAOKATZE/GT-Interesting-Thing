package com.miaokatze.gtit.crossmod.miaogtnh;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.config.Config;
import com.miaokatze.gtit.crossmod.bq.BqCompat;
import com.miaokatze.gtit.crossmod.bq.BqQuestInjector;
import com.miaokatze.gtit.lottery.LotteryHandler;
import com.miaokatze.gtit.util.PlayerLookup;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * MIAO-GTNH 综合任务包宿主（E4a）。
 * <p>
 * 职责：
 * <ul>
 * <li>{@link #shouldLoadMiaoPack()}：综合包加载判定——BetterQuesting + GTSR + GTSWN
 * 三 mod 齐备、配置 {@code miaogtnhQuestsEnabled} 开启、且 BQ 探测通过，
 * 任一不满足即零注入零提示（探测短路矩阵）</li>
 * <li>{@link #onServerStarting()}：先注入 GTIT 自身任务包（BQ 存在时），
 * 再按判定注入 MIAO-GTNH 综合包并置会话标志</li>
 * <li>登录提示：综合包注入成功后，对登录玩家延时 10 秒播放升级音效 + 双语两行聊天提示
 * （玩家已离线时安全跳过）</li>
 * </ul>
 * 登录事件注册范式对齐 {@code NekoTradeSyncHandler}（FML bus + PlayerLoggedInEvent）。
 */
public class MiaoGtnhHost {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** GTIT 自身任务包资产根（jar 内，必须以 / 结尾） */
    private static final String GTIT_PACK_ROOT = "assets/gtit/bqquests/";
    /** MIAO-GTNH 综合任务包资产根（jar 内，必须以 / 结尾） */
    private static final String MIAO_PACK_ROOT = "assets/gtit/bqquests/miao/";

    /** 本会话（本次服务器生命周期）是否已注入 MIAO-GTNH 综合包 */
    private static volatile boolean miaoInjectedThisSession = false;

    /**
     * MIAO-GTNH 综合包加载判定。
     * <p>
     * 五重短路：BetterQuesting / GTSR / GTSWN 任一缺席、配置关闭、BQ 探测未通过
     * 均返回 false——此时零注入、零登录提示、零 MIAO 贸易组。
     */
    public static boolean shouldLoadMiaoPack() {
        return Loader.isModLoaded("betterquesting") && Loader.isModLoaded("gtsr")
            && Loader.isModLoaded("gtswn")
            && Config.miaogtnhQuestsEnabled
            && BqCompat.isBqLoaded();
    }

    /**
     * serverStarting 挂载点：BQ 任务包注入。
     * <p>
     * 
     * @Mod 已声明 after:betterquesting（纯排序约束，不要求 BQ 存在），
     *      保证 BQ 的 default load 先于本方法完成，注入为幂等追加不会被清库。
     *      资产未就位时注入器 info 日志 + 静默返回。
     */
    public static void onServerStarting() {
        // 先注入 GTIT 自身任务包（仅要求 BQ 存在）
        if (BqCompat.isBqLoaded()) {
            BqQuestInjector.inject(GTIT_PACK_ROOT);
        }
        // 再按判定注入 MIAO-GTNH 综合包
        if (shouldLoadMiaoPack()) {
            BqQuestInjector.inject(MIAO_PACK_ROOT);
            miaoInjectedThisSession = true;
            LOG.info("[MIAO-GTNH] 综合任务包宿主已激活（betterquesting+gtsr+gtswn 齐备，配置开启）");
        } else {
            miaoInjectedThisSession = false;
        }
    }

    /**
     * @return 本会话是否已注入 MIAO-GTNH 综合包（登录提示的开关）
     */
    public static boolean isMiaoInjectedThisSession() {
        return miaoInjectedThisSession;
    }

    /**
     * 玩家登录：综合包已注入时，延时 10 秒发送提示。
     * <p>
     * 延时理由：让位给登录瞬间的模组同步/系统消息洪峰，10 秒后提示更易被看到。
     * 延时任务到点时玩家可能已离线——按 UUID 回查在线表，离线即安全跳过。
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!miaoInjectedThisSession) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        final UUID playerId = event.player.getUniqueID();

        LotteryHandler.scheduleDelayedTask(10_000L, () -> {
            try {
                EntityPlayerMP player = PlayerLookup.getOnlinePlayerByUuid(playerId);
                if (player == null || player.isDead) {
                    // 玩家已离线（或正在死亡流程），安全跳过本次提示
                    return;
                }
                // 升级音效 + 双语两行提示（范式对齐 NekoNotificationScheduler 的 playSoundAtEntity + ChatComponentText）
                player.worldObj.playSoundAtEntity(player, "random.levelup", 1.0F, 1.0F);
                player.addChatComponentMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GOLD + "[MIAO-GTNH]"
                            + EnumChatFormatting.WHITE
                            + " Quest pack loaded! Open your quest book."));
                player.addChatComponentMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GOLD + "[MIAO-GTNH]" + EnumChatFormatting.WHITE + " 综合任务包已加载！打开任务书查看。"));
            } catch (Throwable t) {
                LOG.warn("[MIAO-GTNH] 登录提示发送失败（已跳过）", t);
            }
        });
    }
}
