package com.miaokatze.gtit.trade.v2;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 编辑模式操作请求包（客户端→服务端）
 * <p>
 * 携带操作类型与 JSON 序列化的编辑数据。服务端收到后经
 * {@code scheduleServerTask} 投递到服务器主线程执行
 * （1.7.10 的包处理器运行在 Netty 线程，不能直接操作文件/配置）。
 * <p>
 * 所有操作均经统一校验入口（{@link NekoEditActionHandler#validateEditRequest}）：
 * 编辑模式标志（{@link NekoEditModeManager}）+ 发送者实时权限复核（OP 等级 2）+
 * ACTION 白名单 + payload 业务层限长，防止非编辑模式或已降权玩家伪造/续用请求。
 * <p>
 * 参照 {@code MailActionPacket} 的模式。
 */
public class NekoEditPacket implements IMessage {

    // ==================== 操作类型常量 ====================

    /** 保存交易条目编辑 */
    public static final int ACTION_SAVE_TRADE = 0;
    /** 保存签到奖励编辑 */
    public static final int ACTION_SAVE_SIGNIN_REWARD = 1;
    /** 保存抽奖条目编辑 */
    public static final int ACTION_SAVE_LOTTERY_ENTRY = 2;
    /** 保存抽奖卡池编辑（图标/名字/消耗需求/保底，v1.7.6 G2①） */
    public static final int ACTION_SAVE_LOTTERY_POOL = 3;
    /** 新建抽奖卡池（v1.7.6 G2①） */
    public static final int ACTION_CREATE_LOTTERY_POOL = 4;
    /** 删除抽奖卡池（v1.7.6 G2①） */
    public static final int ACTION_DELETE_LOTTERY_POOL = 5;
    /** 新建交易条目（v1.7.6 G3④，targetId=目标 page 的 tabId 字符串） */
    public static final int ACTION_CREATE_TRADE = 6;
    /** 新建标签页 page（v1.7.6 G3④，分配 id≥4） */
    public static final int ACTION_CREATE_PAGE = 7;
    /** 保存标签页 page 编辑（v1.7.6 G3④，targetId=pageId 字符串，图标/名字） */
    public static final int ACTION_SAVE_PAGE = 8;
    /** 删除标签页 page（v1.7.6 G3④，targetId=pageId 字符串；默认页 1-3 不可删） */
    public static final int ACTION_DELETE_PAGE = 9;
    /** 打开交易编辑面板（请求服务端加载交易数据到编辑缓冲区） */
    public static final int ACTION_OPEN_TRADE_EDITOR = 10;
    /** 打开签到编辑面板 */
    public static final int ACTION_OPEN_SIGNIN_EDITOR = 11;
    /** 打开抽奖编辑面板 */
    public static final int ACTION_OPEN_LOTTERY_EDITOR = 12;
    /** 保存祝福预设编辑（v1.7.6 G5，targetId="festival:&lt;序号&gt;"/"birthday"/"sender"） */
    public static final int ACTION_SAVE_BLESSING = 13;
    /** 保存每日在线奖励档位编辑（v1.7.7 G5②，targetId=原秒数字符串，jsonPayload 含 operation/update/add/remove 与字段） */
    public static final int ACTION_SAVE_ONLINE_TIER = 14;
    /** 删除交易条目（targetId=交易组 UUID 字符串，删除后不可恢复） */
    public static final int ACTION_DELETE_TRADE = 15;

    // ==================== 字段 ====================

    /** 操作类型 */
    private int action;
    /** 目标标识（交易组 UUID 字符串 / 签到天数 / 抽奖条目索引） */
    private String targetId = "";
    /** 目标索引（交易在组内的索引，其他操作可忽略） */
    private int targetIndex;
    /** JSON 序列化的编辑参数（非物品数据，物品通过 PhantomItemSlot 自动同步） */
    private String jsonPayload = "";

    // ==================== 构造器 ====================

    public NekoEditPacket() {
        // 反序列化需要无参构造
    }

    public NekoEditPacket(int action, String targetId, int targetIndex, String jsonPayload) {
        this.action = action;
        this.targetId = targetId == null ? "" : targetId;
        this.targetIndex = targetIndex;
        this.jsonPayload = jsonPayload == null ? "" : jsonPayload;
    }

    // ==================== 序列化 ====================

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.targetId = ByteBufUtils.readUTF8String(buf);
        this.targetIndex = buf.readInt();
        this.jsonPayload = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        ByteBufUtils.writeUTF8String(buf, this.targetId == null ? "" : this.targetId);
        buf.writeInt(this.targetIndex);
        ByteBufUtils.writeUTF8String(buf, this.jsonPayload == null ? "" : this.jsonPayload);
    }

    // ==================== Getter ====================

    public int getAction() {
        return action;
    }

    public String getTargetId() {
        return targetId;
    }

    public int getTargetIndex() {
        return targetIndex;
    }

    public String getJsonPayload() {
        return jsonPayload;
    }

    // ==================== 服务端处理器 ====================

    public static class Handler implements IMessageHandler<NekoEditPacket, IMessage> {

        @Override
        public IMessage onMessage(NekoEditPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 切到服务器主线程执行（涉及配置文件写入）
            com.miaokatze.gtit.util.ServerTaskScheduler.scheduleServerTask(() -> processAction(player, message));
            return null;
        }

        /**
         * 服务器主线程：统一校验入口通过后按策略表分发编辑操作
         * <p>
         * BUG B2 修复：编辑模式标志不再是唯一闸门——校验收口在
         * {@link NekoEditActionHandler#validateEditRequest}（编辑模式 + 实时 OP 复核 +
         * ACTION 白名单 + payload 限长），任一失败即拒绝。
         * <p>
         * O2-05 策略表化：原 15 case switch 与 15 个 handleXxx 转发方法收编为
         * {@link EditActionRegistry} 注册表分发，新增 ACTION 不再改本类。
         */
        private void processAction(EntityPlayerMP player, NekoEditPacket message) {
            // 统一校验入口（编辑模式 / 实时权限复核 / ACTION 白名单 / payload 限长）
            String error = NekoEditActionHandler.validateEditRequest(player, message);
            if (error != null) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + error));
                return;
            }

            // 策略表分发（白名单闸按注册表存在性判定，未知 ACTION 正常流程不会到达）
            EditAction action = EditActionRegistry.get(message.getAction());
            if (action == null) {
                // 未知操作，忽略
                return;
            }
            action.execute(player, message.getTargetId(), message.getTargetIndex(), message.getJsonPayload());
        }
    }
}
