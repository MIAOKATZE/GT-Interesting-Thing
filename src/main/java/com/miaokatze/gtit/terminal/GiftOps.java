package com.miaokatze.gtit.terminal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import com.miaokatze.gtit.util.PlayerLookup;
import com.miaokatze.gtit.util.PlayerResolver;

/**
 * 管理终端-礼包页服务端 processor（T5 实装）
 * <p>
 * 承接 {@link TerminalActionHandler} 分发的礼包域动作：
 * <ul>
 * <li>{@code ACTION_GIFT_CLAIM_LIST}：只读查询已领取新手礼包的玩家（在线+离线），
 * 审计行经 {@link TerminalNetworkManager#sendData}（{@code DATA_TYPE_GIFT_LIST}）推送，
 * 语义对齐 {@code /gtit gift claimlist}（{@code command/GTITGiftCommand}）；
 * 在线/离线查询与名字解析全部复用 {@link StarterGiftAudit}，不重写扫描逻辑</li>
 * <li>{@code ACTION_GIFT_CLAIM_RESET}：重置目标玩家领取状态，语义对齐
 * {@code /gtit gift claimreset <玩家名>}；在线走内存 NBT、离线走
 * {@link StarterGiftAudit#resetOfflinePlayerGiftFlagByName}（内部含写前在线复查与
 * {@code CompressedStreamTools.safeWrite} 竞态保护）</li>
 * </ul>
 * 载荷控制：列表只回审计字段（lines 字符串 + total 整数），≤{@value #MAX_LIST_LINES} 行，
 * 严禁回传原始 NBT；真实总数存 total，行内截断。
 * <p>
 * 约定：仅服务器主线程执行（投递方保证）；所有分支必发 {@link TerminalNetworkManager#sendResult}，
 * 失败绝不判成功（fail-closed）。
 */
public final class GiftOps {

    /** 列表载荷行数上限（含首行汇总行与截断提示行；防大服名单撑爆包体） */
    private static final int MAX_LIST_LINES = 100;

    private GiftOps() {
        // 静态工具类，禁止实例化
    }

    /**
     * 礼包域动作统一入口（服务器主线程）
     *
     * @param player  发起玩家（已过五步校验）
     * @param message 动作请求包
     */
    public static void process(EntityPlayerMP player, TerminalActionPacket message) {
        switch (message.getAction()) {
            case TerminalActionHandler.ACTION_GIFT_CLAIM_LIST -> handleClaimList(player, message);
            case TerminalActionHandler.ACTION_GIFT_CLAIM_RESET -> handleClaimReset(player, message);
            default -> TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_INVALID_REQUEST,
                TerminalText.MSG_INVALID_REQUEST);
        }
    }

    // ==================== ACTION_GIFT_CLAIM_LIST：只读查询（对齐 claimlist） ====================

    /**
     * 组审计行推送：
     * <ol>
     * <li>在线已领取：{@link PlayerLookup#forEachOnlinePlayer} + {@link StarterGiftAudit#hasGiftClaimedFlag}，
     * 行「名字 [在线] 已领取」</li>
     * <li>离线已领取：{@link StarterGiftAudit#collectOfflineClaimedPlayers}（跳过在线；
     * 名字经 usercache 反查，查不到显示 UUID），行「名字 [离线] 已领取」</li>
     * <li>lines 首行汇总行「共 N 人已领取（在线 x / 离线 y）」，total 存真实总数</li>
     * </ol>
     */
    private static void handleClaimList(EntityPlayerMP player, TerminalActionPacket message) {
        List<String> onlineClaimed = new ArrayList<>();
        List<String> offlineClaimed = new ArrayList<>();

        // 在线：遍历内存 NBT（ForgeData→PlayerPersisted→领取标记）
        PlayerLookup.forEachOnlinePlayer(p -> {
            if (StarterGiftAudit.hasGiftClaimedFlag(p.getEntityData())) {
                onlineClaimed.add(p.getCommandSenderName());
            }
        });

        // 离线：扫描 playerdata/*.dat（跳过在线 UUID，usercache 反查名字）
        Set<UUID> onlineUuids = PlayerLookup.buildUuidSet();
        StarterGiftAudit.collectOfflineClaimedPlayers(onlineUuids, offlineClaimed);

        int total = onlineClaimed.size() + offlineClaimed.size();

        // 载荷控制：玩家行配额 = MAX_LIST_LINES - 汇总行；截断时再让 1 行给提示行
        int playerQuota = total > MAX_LIST_LINES - 1 ? MAX_LIST_LINES - 2 : total;
        List<String> lines = new ArrayList<>();
        lines.add("共 " + total + " 人已领取（在线 " + onlineClaimed.size() + " / 离线 " + offlineClaimed.size() + "）");
        int appended = appendClaimLines(lines, onlineClaimed, " [在线] 已领取", playerQuota);
        appended += appendClaimLines(lines, offlineClaimed, " [离线] 已领取", playerQuota - appended);
        if (appended < total) {
            lines.add("……仅显示前 " + appended + " 人，共 " + total + " 人");
        }

        // 只回审计字段（lines + total），不回传原始 NBT
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("lines", String.join("\n", lines));
        payload.setInteger("total", total);
        TerminalNetworkManager.sendData(player, TerminalClientData.DATA_TYPE_GIFT_LIST, payload);
        TerminalNetworkManager.sendResult(player, message.getAction(), TerminalActionHandler.STATUS_SUCCESS, "领取名单已刷新");
    }

    /** 按配额追加玩家行（配额用尽或达到行数上限即停；返回实际追加数） */
    private static int appendClaimLines(List<String> lines, List<String> names, String suffix, int quota) {
        int appended = 0;
        for (String name : names) {
            if (appended >= quota || lines.size() >= MAX_LIST_LINES) break;
            lines.add(name + suffix);
            appended++;
        }
        return appended;
    }

    // ==================== ACTION_GIFT_CLAIM_RESET（对齐 claimreset <玩家名>） ====================

    /**
     * 重置目标玩家领取状态：
     * <ul>
     * <li>目标在线 → {@link StarterGiftAudit#resetOnlinePlayerGiftFlag}（内存 NBT）</li>
     * <li>目标离线 → {@link StarterGiftAudit#resetOfflinePlayerGiftFlagByName}
     * （usercache 查 UUID→.dat，失败全目录反查；含写前在线复查与 safeWrite 竞态保护）；
     * 返回 boolean（false=找不到玩家或操作失败），失败按情形细分，绝不判成功</li>
     * </ul>
     */
    private static void handleClaimReset(EntityPlayerMP player, TerminalActionPacket message) {
        String targetName = message.getTargetPlayer() == null ? ""
            : message.getTargetPlayer()
                .trim();
        if (targetName.isEmpty()) {
            TerminalNetworkManager
                .sendResult(player, message.getAction(), TerminalActionHandler.STATUS_INVALID_REQUEST, "请指定目标玩家");
            return;
        }

        // 路径一：目标在线 → 内存 NBT 重置（立即生效）
        EntityPlayerMP online = PlayerLookup.getOnlinePlayerByName(targetName);
        if (online != null) {
            boolean hadFlag = StarterGiftAudit.resetOnlinePlayerGiftFlag(online);
            String msg = hadFlag ? "已重置 " + online.getCommandSenderName() + " 的领取状态（在线）"
                : online.getCommandSenderName() + " 尚未领取新手礼包，无需重置（在线）";
            TerminalNetworkManager.sendResult(player, message.getAction(), TerminalActionHandler.STATUS_SUCCESS, msg);
            return;
        }

        // 路径二：目标离线 → .dat 写回（内部含写前在线复查与 safeWrite 竞态保护）
        boolean success = StarterGiftAudit.resetOfflinePlayerGiftFlagByName(targetName);
        if (success) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_SUCCESS,
                "已重置 " + targetName + " 的领取状态（离线存档已写回）");
            return;
        }

        // ---- 失败分支细分（只读复查复用 StarterGiftAudit/PlayerResolver，不重写扫描） ----

        // ① 竞态窗口内目标恰好上线（写前在线复查跳过的典型情形）→ 提示改走在线重置
        if (PlayerLookup.getOnlinePlayerByName(targetName) != null) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_BUSINESS_FAILURE,
                "目标刚刚上线，请改用在线重置");
            return;
        }

        // ② 存档在且仍含领取标记，但重置返回 false → IO/写回失败（StarterGiftAudit 已记日志）
        File datFile = findOfflineDatFile(targetName);
        if (datFile != null && StarterGiftAudit.offlineDatHasGiftClaimed(datFile)) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_BUSINESS_FAILURE,
                "离线存档写回失败，详见服务器日志");
            return;
        }

        // ③ 存档在但无领取标记 → 幂等结果，与在线路径同为成功口径（目标已处于未领取状态）
        if (datFile != null) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_SUCCESS,
                targetName + " 尚未领取新手礼包，无需重置");
            return;
        }

        // ④ usercache 与全目录反查都找不到该玩家的存档数据
        TerminalNetworkManager
            .sendResult(player, message.getAction(), TerminalActionHandler.STATUS_TARGET_NOT_FOUND, "未找到该玩家的存档数据");
    }

    /**
     * 按名定位离线 .dat（usercache 解析 UUID；只读定位，不修改文件；找不到返回 null）。
     * 仅用于重置失败分支的回执细分，不参与重置主路径。
     */
    private static File findOfflineDatFile(String playerName) {
        UUID uuid = PlayerResolver.resolvePlayerUuid(playerName);
        if (uuid == null) return null;
        File worldDir = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getWorldDirectory();
        File datFile = new File(new File(worldDir, "playerdata"), uuid.toString() + ".dat");
        return datFile.exists() ? datFile : null;
    }
}
