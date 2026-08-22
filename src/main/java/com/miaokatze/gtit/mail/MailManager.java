package com.miaokatze.gtit.mail;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

import com.miaokatze.gtit.command.GTITGiftCommand;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
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
 * {@link com.miaokatze.gtit.util.ServerTaskScheduler#scheduleServerTask} 投递）。
 */
public class MailManager {

    public static final MailManager INSTANCE = new MailManager();

    /** 每玩家邮箱邮件上限（超出后普通投递拒绝，模板投递淘汰最旧） */
    public static final int MAX_MAILS = 50;
    /** 每封邮件附件数量上限（GUI 单行 5 槽） */
    public static final int MAX_ATTACHMENTS = 5;

    // ==================== 玩家互寄结果码（v1.7.6 G2② compose 动作） ====================

    /** 玩家互寄结果：投递成功 */
    public static final int COMPOSE_SUCCESS = 0;
    /** 玩家互寄结果：收件人不存在（从未登录过本服务器） */
    public static final int COMPOSE_RECIPIENT_NOT_FOUND = 1;
    /** 玩家互寄结果：收件人邮箱已满 */
    public static final int COMPOSE_MAILBOX_FULL = 2;
    /** 玩家互寄结果：无正文且无附件（空邮件拒绝发送） */
    public static final int COMPOSE_EMPTY = 3;
    /** 玩家互寄结果：收件人名为空 */
    public static final int COMPOSE_NO_RECIPIENT = 4;
    /** 玩家互寄结果：触发机器无效（坐标无法定位，未扣任何物品） */
    public static final int COMPOSE_MACHINE_INVALID = 5;

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

    // ==================== 玩家互寄（v1.7.6 G2② 写邮件页面） ====================

    /**
     * 玩家互寄邮件（写邮件页面 compose 动作，服务端权威）
     * <p>
     * 流程（任何一步失败都不扣取输入槽物品）：
     * <ol>
     * <li>校验收件人名非空 → {@link GTITGiftCommand#resolvePlayerUuid} 解析 UUID
     * （在线玩家表 → usercache.json 离线缓存）</li>
     * <li>从触发机器输入槽<b>复制</b>非空物品（≤ {@link #MAX_ATTACHMENTS} 格）作为附件候选
     * （此时不清槽）</li>
     * <li>正文与附件均空 → 拒绝（空邮件）</li>
     * <li>收件人邮箱容量预检（满 → 拒绝；离线收件人多加载的数据立即卸载）</li>
     * <li>走 {@link #sendMail} 现有路径投递（在线推送同步、离线落盘）</li>
     * <li><b>仅投递成功后</b>才清除已取用的输入槽并 markDirty
     * （槽位内容变更由打开中的 GUI 槽位同步器自动下发客户端）</li>
     * </ol>
     * 发件人显示名 = 玩家名，类型 = {@link Mail#TYPE_PLAYER}。
     * 不给发件人回执邮件（避免刷屏），发送结果由调用方聊天提示。
     * <p>
     * <b>machine 信任链（IT-BUG-03）</b>：本方法信任调用方传入的 machine，不重复做归属校验；
     * 网络包路径（compose）的 machine 由
     * {@code MailActionPacket.Handler#findMachine} 完成同维度 + 距离 ≤8 格 + GUI 会话绑定
     * 三重校验后才传入，恶意包无法借坐标指定他人机器取物（校验失败按无附件投递）。
     *
     * @param sender        发件玩家（在线）
     * @param machine       触发机器（附件来源；调用方须已完成归属/距离/会话校验；null 时按无附件处理）
     * @param recipientName 收件人名（已由调用方 trim/限长）
     * @param title         标题（已由调用方限长/兜底）
     * @param content       正文（已由调用方限长）
     * @return {@link #COMPOSE_SUCCESS} 等 COMPOSE_* 结果码
     */
    public int sendPlayerMail(EntityPlayerMP sender, MTENekoVendingMachineV2 machine, String recipientName,
        String title, String content) {
        if (sender == null) return COMPOSE_MACHINE_INVALID;
        if (recipientName == null || recipientName.isEmpty()) return COMPOSE_NO_RECIPIENT;

        // 1. 解析收件人 UUID（失败不扣物品）
        UUID targetId = GTITGiftCommand.resolvePlayerUuid(recipientName);
        if (targetId == null) return COMPOSE_RECIPIENT_NOT_FOUND;

        // 2. 复制附件候选（不清槽——投递成功前输入槽物品保持不动）
        List<ItemStack> attachments = new ArrayList<>();
        List<Integer> takenSlots = new ArrayList<>();
        if (machine != null) {
            for (int i = 0; i < MTENekoVendingMachineV2.INPUT_SLOTS && attachments.size() < MAX_ATTACHMENTS; i++) {
                ItemStack stack = machine.inputItems.getStackInSlot(i);
                if (stack != null && stack.getItem() != null && stack.stackSize > 0) {
                    attachments.add(stack.copy());
                    takenSlots.add(i);
                }
            }
        }

        // 3. 空邮件拒绝（无正文且无附件）
        if ((content == null || content.isEmpty()) && attachments.isEmpty()) {
            return COMPOSE_EMPTY;
        }

        // 4. 邮箱容量预检（离线收件人：预检加载的数据在拒绝时立即卸载，避免常驻）
        EntityPlayerMP recipientOnline = getPlayerByUUID(targetId);
        MailData recipientData = getMailData(targetId);
        if (recipientData == null) return COMPOSE_MACHINE_INVALID;
        if (recipientData.getMails()
            .size() >= MAX_MAILS) {
            if (recipientOnline == null) {
                mailDataMap.remove(targetId);
            }
            return COMPOSE_MAILBOX_FULL;
        }

        // 5. 构建并投递（sendMail 内部对离线收件人完成落盘后卸载；服务器主线程单线程无竞态，
        // 预检通过后 addMail 不会失败，false 仅作兜底）
        Mail mail = new Mail(title, content, sender.getCommandSenderName(), attachments, Mail.TYPE_PLAYER);
        if (!sendMail(targetId, mail)) {
            if (recipientOnline == null) {
                mailDataMap.remove(targetId);
            }
            return COMPOSE_MAILBOX_FULL;
        }

        // 6. 投递成功：清除已取用的输入槽并标脏持久化
        if (machine != null && !takenSlots.isEmpty()) {
            for (int slot : takenSlots) {
                machine.inputItems.setStackInSlot(slot, null);
            }
            if (machine.getBaseMetaTileEntity() != null) {
                machine.getBaseMetaTileEntity()
                    .markDirty();
            }
        }
        return COMPOSE_SUCCESS;
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
