package com.miaokatze.gtit.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 邮件数据模型
 * <p>
 * 一封邮件包含：唯一 ID、标题、正文、发件人显示名、时间戳、已读标记、
 * 附件物品列表（最多 {@link MailManager#MAX_ATTACHMENTS} 个）与附件领取标记。
 * 通过 NBT 序列化/反序列化实现持久化（随 {@link MailData} 存入
 * {@code <world>/gtit_mail/<player_uuid>.dat}）与网络同步（随 {@link MailSyncPacket} 下发）。
 * <p>
 * 双端共用：服务端由 {@link MailManager} 权威管理，客户端仅作展示缓存
 * （{@link MailClientData}），不得自行修改状态。
 */
public class Mail {

    // ==================== 邮件类型常量（v1.7.6 G2② 类型分页） ====================

    /** 类型：系统邮件（首登/一次性奖励模板、G5 自动祝福；旧存档无 type 键的邮件默认归入此类） */
    public static final String TYPE_SYSTEM = "SYSTEM";
    /** 类型：玩家互寄邮件（写邮件页面 compose 路径） */
    public static final String TYPE_PLAYER = "PLAYER";
    /** 类型：管理员邮件（/gtit mail send 指令路径） */
    public static final String TYPE_ADMIN = "ADMIN";

    // ==================== 字段定义 ====================

    /** 邮件唯一 ID（随机 UUID 字符串；同一玩家的邮箱内唯一） */
    private String id = "";

    /**
     * 邮件类型（{@link #TYPE_SYSTEM}/{@link #TYPE_PLAYER}/{@link #TYPE_ADMIN} 之一）
     * <p>
     * 持久化与同步均经 NBT 携带；旧存档无该键时 {@link #fromNBT} 回退为
     * {@link #TYPE_SYSTEM}（用户确认：旧邮件全部归系统页）。
     */
    private String type = TYPE_SYSTEM;

    /** 标题（单行，指令参数，不含空格） */
    private String title = "";

    /** 正文（可含 \n 换行，GUI 按行渲染） */
    private String content = "";

    /** 发件人显示名（如「系统」或管理员玩家名） */
    private String sender = "";

    /** 发送时间戳（System.currentTimeMillis） */
    private long timestamp;

    /** 是否已读 */
    private boolean read;

    /** 附件是否已领取（无附件时恒为 true 的语义由 {@link #hasAttachments()} 配合判断） */
    private boolean attachmentClaimed;

    /** 附件物品列表（最多 {@link MailManager#MAX_ATTACHMENTS} 个，深拷贝存储） */
    private final List<ItemStack> attachments = new ArrayList<>();

    // ==================== 构造 ====================

    /** 反序列化用空构造 */
    public Mail() {}

    /**
     * 构建新邮件（自动生成随机 ID，时间戳取当前时间，类型默认 {@link #TYPE_SYSTEM}）
     *
     * @param title       标题
     * @param content     正文
     * @param sender      发件人显示名
     * @param attachments 附件物品（可为 null/空；内部深拷贝，调用方可安全复用原物品堆）
     */
    public Mail(String title, String content, String sender, List<ItemStack> attachments) {
        this(title, content, sender, attachments, TYPE_SYSTEM);
    }

    /**
     * 构建指定类型的新邮件（v1.7.6 G2② 类型分页）
     *
     * @param title       标题
     * @param content     正文
     * @param sender      发件人显示名
     * @param attachments 附件物品（可为 null/空；内部深拷贝，调用方可安全复用原物品堆）
     * @param type        邮件类型（非法值回退 {@link #TYPE_SYSTEM}）
     */
    public Mail(String title, String content, String sender, List<ItemStack> attachments, String type) {
        this.id = UUID.randomUUID()
            .toString();
        this.type = normalizeType(type);
        this.title = title == null ? "" : title;
        this.content = content == null ? "" : content;
        this.sender = sender == null ? "" : sender;
        this.timestamp = System.currentTimeMillis();
        this.read = false;
        this.attachmentClaimed = false;
        if (attachments != null) {
            for (ItemStack stack : attachments) {
                if (stack != null && stack.getItem() != null && this.attachments.size() < MailManager.MAX_ATTACHMENTS) {
                    this.attachments.add(stack.copy());
                }
            }
        }
        // 无附件的邮件视为「附件已领取」，简化领取状态判断
        if (this.attachments.isEmpty()) {
            this.attachmentClaimed = true;
        }
    }

    // ==================== NBT 序列化 ====================

    /**
     * 将邮件写入 NBT 标签
     *
     * @return 包含全部字段的 NBTTagCompound
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("id", id);
        nbt.setString("type", type);
        nbt.setString("title", title);
        nbt.setString("content", content);
        nbt.setString("sender", sender);
        nbt.setLong("timestamp", timestamp);
        nbt.setBoolean("read", read);
        nbt.setBoolean("claimed", attachmentClaimed);

        // 附件物品列表（10 = NBTTagCompound）
        NBTTagList attachList = new NBTTagList();
        for (ItemStack stack : attachments) {
            if (stack != null && stack.getItem() != null) {
                attachList.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        nbt.setTag("attachments", attachList);
        return nbt;
    }

    /**
     * 从 NBT 标签读取邮件
     *
     * @param nbt 邮件 NBT 标签
     * @return 反序列化的邮件实例
     */
    public static Mail fromNBT(NBTTagCompound nbt) {
        if (nbt == null) return null;
        Mail mail = new Mail();
        mail.id = nbt.getString("id");
        // 类型兼容：旧存档无 type 键（或值非法）时归系统邮件（v1.7.6 用户确认口径）
        mail.type = normalizeType(nbt.hasKey("type") ? nbt.getString("type") : TYPE_SYSTEM);
        mail.title = nbt.getString("title");
        mail.content = nbt.getString("content");
        mail.sender = nbt.getString("sender");
        mail.timestamp = nbt.getLong("timestamp");
        mail.read = nbt.getBoolean("read");
        mail.attachmentClaimed = nbt.getBoolean("claimed");

        NBTTagList attachList = nbt.getTagList("attachments", 10);
        for (int i = 0; i < attachList.tagCount(); i++) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(attachList.getCompoundTagAt(i));
            if (stack != null && mail.attachments.size() < MailManager.MAX_ATTACHMENTS) {
                mail.attachments.add(stack);
            }
        }
        return mail;
    }

    // ==================== 业务方法 ====================

    /**
     * 是否有附件物品
     */
    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    /**
     * 是否有待领取的附件（有附件且未领取）
     */
    public boolean hasUnclaimedAttachments() {
        return hasAttachments() && !attachmentClaimed;
    }

    /**
     * 复制为新邮件（模板投递用：新随机 ID、新时间戳、未读未领取，附件深拷贝）
     * <p>
     * 首登奖励/一次性奖励模板在投递给每个玩家时调用，
     * 保证各玩家邮箱中的邮件互相独立（附件物品堆不共享引用）。
     *
     * @return 内容相同但身份独立的新邮件
     */
    public Mail copyAsDelivered() {
        Mail copy = new Mail();
        copy.id = UUID.randomUUID()
            .toString();
        copy.type = this.type;
        copy.title = this.title;
        copy.content = this.content;
        copy.sender = this.sender;
        copy.timestamp = System.currentTimeMillis();
        copy.read = false;
        copy.attachmentClaimed = false;
        for (ItemStack stack : this.attachments) {
            if (stack != null && stack.getItem() != null) {
                copy.attachments.add(stack.copy());
            }
        }
        if (copy.attachments.isEmpty()) {
            copy.attachmentClaimed = true;
        }
        return copy;
    }

    // ==================== Getter / Setter ====================

    /**
     * 类型值归一化（仅接受三种已知类型，其余一律回退 {@link #TYPE_SYSTEM}）
     */
    public static String normalizeType(String type) {
        if (TYPE_PLAYER.equals(type) || TYPE_ADMIN.equals(type) || TYPE_SYSTEM.equals(type)) {
            return type;
        }
        return TYPE_SYSTEM;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = normalizeType(type);
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isAttachmentClaimed() {
        return attachmentClaimed;
    }

    public void setAttachmentClaimed(boolean claimed) {
        this.attachmentClaimed = claimed;
    }

    /**
     * 附件物品列表（只读视图语义；修改邮件状态请通过 MailManager）
     */
    public List<ItemStack> getAttachments() {
        return attachments;
    }
}
