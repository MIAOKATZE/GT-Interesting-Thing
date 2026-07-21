package com.miaokatze.gtit.mail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * 玩家邮箱数据
 * <p>
 * 记录单个玩家的邮件列表与奖励防重标记：
 * <ul>
 * <li>邮件列表（时间倒序，最新在前，上限 {@link MailManager#MAX_MAILS} 封）</li>
 * <li>首登奖励领取标记（{@code firstRewardReceived}，防重复投递）</li>
 * <li>已收一次性奖励 ID 集合（{@code receivedOnceIds}，按奖励 ID 防重复投递）</li>
 * </ul>
 * 通过 NBT 序列化/反序列化持久化到 {@code <world>/gtit_mail/<player_uuid>.dat}。
 * <p>
 * 参照 {@link com.miaokatze.gtit.signin.DailySignInData} 的 NBT 模式。
 *
 * @see MailManager
 */
public class MailData {

    // ==================== 字段定义 ====================

    /** 邮件列表（时间倒序，最新在前） */
    private final List<Mail> mails = new ArrayList<>();

    /** 首登奖励是否已投递（true = 已投递过，不再重复投递） */
    private boolean firstRewardReceived;

    /** 已投递的一次性奖励 ID 集合（对应全局一次性奖励表的奖励 ID） */
    private final Set<String> receivedOnceIds = new HashSet<>();

    // ==================== NBT 序列化 ====================

    /**
     * 将邮箱数据写入 NBT 标签
     *
     * @return 包含全部数据的 NBTTagCompound
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean("firstRewardReceived", firstRewardReceived);

        // 邮件列表
        NBTTagList mailList = new NBTTagList();
        for (Mail mail : mails) {
            if (mail != null) {
                mailList.appendTag(mail.writeToNBT());
            }
        }
        nbt.setTag("mails", mailList);

        // 已收一次性奖励 ID 集合（8 = NBTTagString）
        NBTTagList onceList = new NBTTagList();
        for (String rewardId : receivedOnceIds) {
            if (rewardId != null && !rewardId.isEmpty()) {
                onceList.appendTag(new NBTTagString(rewardId));
            }
        }
        nbt.setTag("receivedOnceIds", onceList);

        return nbt;
    }

    /**
     * 从 NBT 标签读取邮箱数据
     *
     * @param nbt 邮箱 NBT 标签
     */
    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt == null) return;
        this.firstRewardReceived = nbt.getBoolean("firstRewardReceived");

        this.mails.clear();
        NBTTagList mailList = nbt.getTagList("mails", 10);
        for (int i = 0; i < mailList.tagCount(); i++) {
            Mail mail = Mail.fromNBT(mailList.getCompoundTagAt(i));
            if (mail != null) {
                this.mails.add(mail);
            }
        }

        this.receivedOnceIds.clear();
        NBTTagList onceList = nbt.getTagList("receivedOnceIds", 8);
        for (int i = 0; i < onceList.tagCount(); i++) {
            String rewardId = onceList.getStringTagAt(i);
            if (rewardId != null && !rewardId.isEmpty()) {
                this.receivedOnceIds.add(rewardId);
            }
        }
    }

    // ==================== 邮件列表操作 ====================

    /**
     * 添加邮件到列表头部（最新在前）
     * <p>
     * 超出 {@link MailManager#MAX_MAILS} 上限时拒绝添加（调用方据此反馈「邮箱已满」）。
     *
     * @param mail 待添加邮件
     * @return true 表示添加成功；false 表示邮箱已满
     */
    public boolean addMail(Mail mail) {
        if (mail == null) return false;
        if (mails.size() >= MailManager.MAX_MAILS) return false;
        mails.add(0, mail);
        return true;
    }

    /**
     * 强制添加邮件（模板投递路径：超出上限时淘汰最旧的一封，保证奖励邮件不丢）
     *
     * @param mail 待添加邮件
     */
    public void addMailForced(Mail mail) {
        if (mail == null) return;
        while (mails.size() >= MailManager.MAX_MAILS) {
            // 淘汰列表末尾（最旧）邮件
            mails.remove(mails.size() - 1);
        }
        mails.add(0, mail);
    }

    /**
     * 按 ID 查找邮件（不存在返回 null）
     */
    public Mail findMail(String mailId) {
        if (mailId == null || mailId.isEmpty()) return null;
        for (Mail mail : mails) {
            if (mail != null && mailId.equals(mail.getId())) {
                return mail;
            }
        }
        return null;
    }

    /**
     * 按 ID 删除邮件
     *
     * @return true 表示找到并删除
     */
    public boolean removeMail(String mailId) {
        Mail mail = findMail(mailId);
        if (mail == null) return false;
        return mails.remove(mail);
    }

    /**
     * 未读邮件数量（GUI 角标展示用）
     */
    public int getUnreadCount() {
        int count = 0;
        for (Mail mail : mails) {
            if (mail != null && !mail.isRead()) count++;
        }
        return count;
    }

    // ==================== 奖励防重标记 ====================

    public boolean isFirstRewardReceived() {
        return firstRewardReceived;
    }

    public void setFirstRewardReceived(boolean received) {
        this.firstRewardReceived = received;
    }

    public boolean hasReceivedOnce(String rewardId) {
        return rewardId != null && receivedOnceIds.contains(rewardId);
    }

    public void markReceivedOnce(String rewardId) {
        if (rewardId != null && !rewardId.isEmpty()) {
            receivedOnceIds.add(rewardId);
        }
    }

    // ==================== Getter ====================

    /**
     * 邮件列表（时间倒序；只读视图语义，修改请通过本类方法）
     */
    public List<Mail> getMails() {
        return mails;
    }

    /**
     * 已收一次性奖励 ID 集合（只读视图语义）
     */
    public Set<String> getReceivedOnceIds() {
        return receivedOnceIds;
    }
}
