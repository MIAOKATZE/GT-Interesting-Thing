package com.miaokatze.gtit.mail;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 邮件管理器单例
 * <p>
 * 管理所有玩家的邮箱数据（个人维度，按 UUID 存储），处理邮件投递、已读、
 * 附件领取、删除与持久化。玩家数据存储到 {@code <world>/gtit_mail/<player_uuid>.dat}
 * （NBT 格式，参照 {@code DailySignInManager} 的 CompressedStreamTools 模式）。
 * <p>
 * <b>全局奖励数据</b>（{@code <world>/gtit_mail/global.dat>}）：
 * <ul>
 * <li>首登奖励模板：新玩家首次登录时自动投递（按玩家 {@code firstRewardReceived} 标记防重）</li>
 * <li>一次性奖励表（rewardId → 邮件模板）：发布后向全服玩家投递，
 * 在线玩家立即送达，离线玩家登录时补投（按玩家 {@code receivedOnceIds} 集合防重）</li>
 * </ul>
 * <p>
 * <b>线程</b>：所有公开方法须在服务器主线程调用（网络包处理器经
 * {@link MailHandler#scheduleServerTask} 投递）。
 */
public class MailManager {

    public static final MailManager INSTANCE = new MailManager();

    /** 每玩家邮箱邮件上限（超出后普通投递拒绝，模板投递淘汰最旧） */
    public static final int MAX_MAILS = 50;
    /** 每封邮件附件数量上限（GUI 单行 5 槽） */
    public static final int MAX_ATTACHMENTS = 5;

    /** 已加载的玩家邮箱数据（仅在线玩家常驻内存） */
    private final ConcurrentHashMap<UUID, MailData> mailDataMap = new ConcurrentHashMap<>();
    /** 玩家数据存档目录（<world>/gtit_mail） */
    private File saveDir;
    /** 全局数据文件（<world>/gtit_mail/global.dat） */
    private File globalFile;

    /** 首登奖励模板（null = 未设置） */
    private Mail firstRewardTemplate;
    /** 一次性奖励表（rewardId → 邮件模板，LinkedHashMap 保持发布顺序） */
    private final Map<String, Mail> onceRewards = new LinkedHashMap<>();

    private MailManager() {}

    /**
     * 初始化存储目录并加载全局奖励数据（CommonProxy.serverStarted 调用，需要 World 对象）
     */
    public void init(World world) {
        saveDir = new File(
            world.getSaveHandler()
                .getWorldDirectory(),
            "gtit_mail");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        globalFile = new File(saveDir, "global.dat");
        loadGlobalData();
        GTInterestingThing.LOG.info(
            "邮件数据存储目录: {}（首登奖励模板{}，一次性奖励 {} 条）",
            saveDir.getAbsolutePath(),
            firstRewardTemplate != null ? "已设置" : "未设置",
            onceRewards.size());
    }

    // ==================== 数据存取 ====================

    /**
     * 获取玩家邮箱数据（优先内存缓存，未加载则从磁盘读取，均无则新建）
     */
    public MailData getMailData(UUID playerId) {
        if (playerId == null) return null;
        MailData data = mailDataMap.get(playerId);
        if (data == null) {
            data = loadFromDisk(playerId);
            if (data == null) {
                data = new MailData();
            }
            mailDataMap.put(playerId, data);
        }
        return data;
    }

    /**
     * 保存指定玩家数据到磁盘
     */
    public void saveMailData(UUID playerId) {
        if (playerId == null || saveDir == null) return;
        MailData data = mailDataMap.get(playerId);
        if (data == null) return;
        File file = new File(saveDir, playerId.toString() + ".dat");
        try {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setTag("mail", data.writeToNBT());
            CompressedStreamTools.safeWrite(nbt, file);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存邮件数据失败: " + playerId, e);
        }
    }

    /**
     * 玩家下线：保存并从内存卸载
     */
    public void unloadMailData(UUID playerId) {
        saveMailData(playerId);
        mailDataMap.remove(playerId);
    }

    /**
     * 保存所有已加载玩家数据与全局数据（周期保存/服务器关闭）
     */
    public void saveAll() {
        for (UUID playerId : mailDataMap.keySet()) {
            saveMailData(playerId);
        }
        saveGlobalData();
    }

    // ==================== 邮件投递 ====================

    /**
     * 向指定玩家发送邮件（指令路径）
     * <p>
     * 离线玩家：加载其数据 → 追加 → 落盘 → 从内存卸载（避免离线数据常驻）。
     * 在线玩家：追加 → 落盘 → 推送同步包。
     *
     * @param playerId 目标玩家 UUID
     * @param mail     邮件（由指令构建，含附件深拷贝）
     * @return true 表示投递成功；false 表示邮箱已满
     */
    public boolean sendMail(UUID playerId, Mail mail) {
        if (playerId == null || mail == null) return false;
        MailData data = getMailData(playerId);
        if (data == null) return false;
        if (!data.addMail(mail)) {
            return false;
        }
        saveMailData(playerId);
        EntityPlayerMP player = getPlayerByUUID(playerId);
        if (player != null) {
            MailNetworkManager.sendSyncToClient(player, data);
        } else {
            // 离线玩家：落盘后卸载，避免内存常驻
            mailDataMap.remove(playerId);
        }
        return true;
    }

    /**
     * 登录时投递待发的首登/一次性奖励（MailHandler.onPlayerLoggedIn 调用）
     * <p>
     * 防重机制：首登奖励按 {@code firstRewardReceived} 标记、
     * 一次性奖励按 {@code receivedOnceIds} 集合判定；投递成功后立即落盘。
     *
     * @param playerId 登录玩家 UUID
     * @return true 表示有新邮件投递（调用方可据此补发聊天提示）
     */
    public boolean deliverPendingRewards(UUID playerId) {
        MailData data = getMailData(playerId);
        if (data == null) return false;
        boolean dirty = false;

        // 首登奖励（仅模板已设置且玩家未收过时投递）
        if (firstRewardTemplate != null && !data.isFirstRewardReceived()) {
            data.addMailForced(firstRewardTemplate.copyAsDelivered());
            data.setFirstRewardReceived(true);
            dirty = true;
        }

        // 一次性奖励（逐条检查玩家未收的奖励 ID）
        for (Map.Entry<String, Mail> entry : onceRewards.entrySet()) {
            if (!data.hasReceivedOnce(entry.getKey())) {
                data.addMailForced(
                    entry.getValue()
                        .copyAsDelivered());
                data.markReceivedOnce(entry.getKey());
                dirty = true;
            }
        }

        if (dirty) {
            saveMailData(playerId);
        }
        return dirty;
    }

    // ==================== 首登奖励模板管理 ====================

    /**
     * 设置/覆盖首登奖励模板（/gtit mail first）
     */
    public void setFirstRewardTemplate(Mail template) {
        this.firstRewardTemplate = template;
        saveGlobalData();
    }

    /**
     * 清除首登奖励模板（/gtit mail firstclear）
     *
     * @return true 表示之前有模板且已清除
     */
    public boolean clearFirstRewardTemplate() {
        boolean had = this.firstRewardTemplate != null;
        this.firstRewardTemplate = null;
        if (had) {
            saveGlobalData();
        }
        return had;
    }

    public Mail getFirstRewardTemplate() {
        return firstRewardTemplate;
    }

    // ==================== 一次性奖励管理 ====================

    /**
     * 发布一次性奖励（/gtit mail once）
     * <p>
     * 奖励 ID 唯一：已存在的 ID 拒绝发布（保证「一次性」语义不被覆盖重发破坏）。
     * 发布成功后立即向全体在线玩家投递并落盘。
     *
     * @param rewardId 奖励 ID（防重键）
     * @param template 邮件模板
     * @return true 表示发布成功；false 表示该 ID 已发布过
     */
    public boolean publishOnceReward(String rewardId, Mail template) {
        if (rewardId == null || rewardId.isEmpty() || template == null) return false;
        if (onceRewards.containsKey(rewardId)) {
            return false;
        }
        onceRewards.put(rewardId, template);
        saveGlobalData();

        // 立即向在线玩家投递（deliverPendingRewards 内部按 receivedOnceIds 防重）
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            for (Object obj : server.getConfigurationManager().playerEntityList) {
                EntityPlayerMP player = (EntityPlayerMP) obj;
                UUID playerId = player.getUniqueID();
                if (deliverPendingRewards(playerId)) {
                    MailNetworkManager.sendSyncToClient(player, getMailData(playerId));
                }
            }
        }
        return true;
    }

    /**
     * 已发布的一次性奖励 ID 集合（指令查询/调试展示用）
     */
    public Map<String, Mail> getOnceRewards() {
        return onceRewards;
    }

    // ==================== 邮件操作（网络包路径） ====================

    /**
     * 标记邮件已读
     *
     * @return true 表示状态发生变化（从未读变为已读）
     */
    public boolean markRead(UUID playerId, String mailId) {
        MailData data = getMailData(playerId);
        if (data == null) return false;
        Mail mail = data.findMail(mailId);
        if (mail == null || mail.isRead()) return false;
        mail.setRead(true);
        saveMailData(playerId);
        return true;
    }

    /**
     * 领取邮件附件（物品入玩家背包，背包满则掉落在脚下）
     * <p>
     * 领取成功后邮件标记为已读+附件已领取并落盘。
     *
     * @param playerId 玩家 UUID
     * @param mailId   邮件 ID
     * @param player   在线玩家实体（附件直接入其背包）
     * @return 领取的附件组数；-1 表示邮件不存在或无待领取附件
     */
    public int claimAttachments(UUID playerId, String mailId, EntityPlayerMP player) {
        MailData data = getMailData(playerId);
        if (data == null || player == null) return -1;
        Mail mail = data.findMail(mailId);
        if (mail == null || !mail.hasUnclaimedAttachments()) return -1;

        int granted = 0;
        for (ItemStack stack : mail.getAttachments()) {
            if (stack == null || stack.getItem() == null) continue;
            ItemStack copy = stack.copy();
            if (!player.inventory.addItemStackToInventory(copy)) {
                // 背包已满：掉落在玩家位置，避免附件丢失
                EntityItem drop = new EntityItem(player.worldObj, player.posX, player.posY, player.posZ, copy);
                player.worldObj.spawnEntityInWorld(drop);
            }
            granted++;
        }
        mail.setAttachmentClaimed(true);
        mail.setRead(true);
        saveMailData(playerId);
        return granted;
    }

    /**
     * 删除邮件（带未领取附件的邮件拒绝删除，防止误丢奖励）
     *
     * @return 0=删除成功；1=邮件不存在；2=有未领取附件，拒绝删除
     */
    public int deleteMail(UUID playerId, String mailId) {
        MailData data = getMailData(playerId);
        if (data == null) return 1;
        Mail mail = data.findMail(mailId);
        if (mail == null) return 1;
        if (mail.hasUnclaimedAttachments()) return 2;
        data.removeMail(mailId);
        saveMailData(playerId);
        return 0;
    }

    // ==================== 全局数据持久化 ====================

    /**
     * 从 global.dat 加载首登奖励模板与一次性奖励表
     */
    private void loadGlobalData() {
        firstRewardTemplate = null;
        onceRewards.clear();
        if (globalFile == null || !globalFile.exists()) return;
        try {
            NBTTagCompound nbt = CompressedStreamTools.read(globalFile);
            if (nbt == null) return;
            if (nbt.hasKey("firstReward")) {
                firstRewardTemplate = Mail.fromNBT(nbt.getCompoundTag("firstReward"));
            }
            NBTTagList onceList = nbt.getTagList("onceRewards", 10);
            for (int i = 0; i < onceList.tagCount(); i++) {
                NBTTagCompound entry = onceList.getCompoundTagAt(i);
                String rewardId = entry.getString("id");
                Mail template = Mail.fromNBT(entry.getCompoundTag("mail"));
                if (rewardId != null && !rewardId.isEmpty() && template != null) {
                    onceRewards.put(rewardId, template);
                }
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("加载邮件全局奖励数据失败", e);
        }
    }

    /**
     * 将首登奖励模板与一次性奖励表写入 global.dat
     */
    private void saveGlobalData() {
        if (globalFile == null) return;
        try {
            NBTTagCompound nbt = new NBTTagCompound();
            if (firstRewardTemplate != null) {
                nbt.setTag("firstReward", firstRewardTemplate.writeToNBT());
            }
            NBTTagList onceList = new NBTTagList();
            for (Map.Entry<String, Mail> entry : onceRewards.entrySet()) {
                NBTTagCompound entryTag = new NBTTagCompound();
                entryTag.setString("id", entry.getKey());
                entryTag.setTag(
                    "mail",
                    entry.getValue()
                        .writeToNBT());
                onceList.appendTag(entryTag);
            }
            nbt.setTag("onceRewards", onceList);
            CompressedStreamTools.safeWrite(nbt, globalFile);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存邮件全局奖励数据失败", e);
        }
    }

    // ==================== 内部辅助 ====================

    /**
     * 从磁盘读取玩家邮箱数据（文件不存在或损坏时返回 null）
     */
    private MailData loadFromDisk(UUID playerId) {
        if (saveDir == null) return null;
        File file = new File(saveDir, playerId.toString() + ".dat");
        if (!file.exists()) return null;
        try {
            NBTTagCompound nbt = CompressedStreamTools.read(file);
            if (nbt != null && nbt.hasKey("mail")) {
                MailData data = new MailData();
                data.readFromNBT(nbt.getCompoundTag("mail"));
                return data;
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("加载邮件数据失败: " + playerId, e);
        }
        return null;
    }

    /**
     * 按 UUID 查找在线玩家（未找到返回 null）
     */
    public static EntityPlayerMP getPlayerByUUID(UUID playerId) {
        if (playerId == null) return null;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) obj;
            if (playerId.equals(player.getUniqueID())) {
                return player;
            }
        }
        return null;
    }
}
