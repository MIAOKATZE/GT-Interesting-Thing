package com.miaokatze.gtit.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端邮件数据缓存
 * <p>
 * 由 {@link MailSyncPacket} 的客户端处理器写入，{@code MailGui} 读取。
 * 服务端侧不会收到同步包，因此本类在服务端始终保持空数据（GUI 在服务端构建时
 * 读到的都是空列表，不影响服务端逻辑——邮件操作完全由 {@link MailManager}
 * 在服务端权威执行）。
 * <p>
 * 参照 {@code SignInClientData} 的静态缓存模式。
 */
public final class MailClientData {

    // ==================== 同步状态字段 ====================

    /** 邮件列表（时间倒序，最新在前；同步包全量覆盖） */
    private static volatile List<Mail> mails = new ArrayList<>();

    /** 未读邮件数量（由同步数据计算，供列表角标展示） */
    private static volatile int unreadCount;

    /** 当前选中的邮件 ID（GUI 详情面板展示目标；客户端本地状态，不随同步覆盖） */
    private static volatile String selectedMailId = "";

    /** 邮件列表当前页码（0 起；客户端本地状态，不随同步覆盖，GUI 读取时 clamp 防越界） */
    private static volatile int listPage;

    /** 缓存数据版本号（每次同步递增；GUI 可据此检测数据变化刷新列表） */
    private static volatile long dataVersion;

    private MailClientData() {}

    // ==================== 写入（网络包处理器调用） ====================

    /**
     * 用服务端同步的数据全量刷新客户端缓存
     * <p>
     * 邮件列表做不可变拷贝，避免 GUI 渲染线程读取时与网络线程写入竞争。
     *
     * @param data 服务端邮箱数据（同步包已反序列化）
     */
    public static synchronized void update(MailData data) {
        if (data != null) {
            mails = Collections.unmodifiableList(new ArrayList<>(data.getMails()));
            unreadCount = data.getUnreadCount();
        } else {
            mails = new ArrayList<>();
            unreadCount = 0;
        }
        dataVersion++;
        // 选中的邮件可能已被删除（服务端删除后同步），此处不主动清空，
        // 由 GUI 读取时按 findMail 判空回退到「未选中」状态
    }

    // ==================== 读取（GUI 调用） ====================

    /**
     * 邮件列表（时间倒序；不可变视图）
     */
    public static List<Mail> getMails() {
        return mails;
    }

    /**
     * 按 ID 查找邮件（不存在返回 null）
     */
    public static Mail findMail(String mailId) {
        if (mailId == null || mailId.isEmpty()) return null;
        for (Mail mail : mails) {
            if (mail != null && mailId.equals(mail.getId())) {
                return mail;
            }
        }
        return null;
    }

    /**
     * 未读邮件数量
     */
    public static int getUnreadCount() {
        return unreadCount;
    }

    /**
     * 缓存数据版本号（每次同步递增）
     */
    public static long getDataVersion() {
        return dataVersion;
    }

    // ==================== 选中状态（客户端本地） ====================

    /**
     * 当前选中的邮件 ID（空串表示未选中）
     */
    public static String getSelectedMailId() {
        return selectedMailId;
    }

    /**
     * 设置选中的邮件 ID（GUI 点击列表条目时调用）
     */
    public static void setSelectedMailId(String mailId) {
        selectedMailId = mailId == null ? "" : mailId;
    }

    // ==================== 列表页码（客户端本地） ====================

    /**
     * 邮件列表当前页码（0 起）
     */
    public static int getListPage() {
        return listPage;
    }

    /**
     * 设置邮件列表页码（GUI 翻页按钮调用；负值按 0 处理）
     */
    public static void setListPage(int page) {
        listPage = Math.max(0, page);
    }
}
