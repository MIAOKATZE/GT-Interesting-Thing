package com.miaokatze.gtit.reincarnation.network;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 轮回奖励发放演出包（服务端→客户端，discriminator=0）
 * <p>
 * 服务端完成周目奖励结算后下发，携带发放物品清单（{@link ItemRef}：注册 id + meta），
 * 触发客户端「头顶螺旋降下+发光」演出。<b>实际发放由服务端另行完成</b>，
 * 本包只承载演出数据，客户端不得据此修改任何权威状态。
 * <p>
 * 客户端 handler 将载荷落入 {@link ClientReincarnationFxState}（static volatile holder）；
 * 真实 FX 渲染由 S8 切片消费该 holder，本切片只落状态与日志。
 * 本类（含 Handler）不引用任何 net.minecraft.client 类型，仅经 ClientProxy 注册路径可达。
 */
public class GrantEffectPacket implements IMessage {

    /** 清单条目数防御上限（防恶意/损坏包在客户端分配超大列表） */
    private static final int MAX_ITEMS = 256;

    private List<ItemRef> items = new ArrayList<>();

    public GrantEffectPacket() {
        // 反序列化需要无参构造
    }

    /**
     * 构建发放演出包（服务端）
     *
     * @param items 发放物品清单（null 条目跳过；null 列表按空清单处理）
     */
    public GrantEffectPacket(List<ItemRef> items) {
        this.items = new ArrayList<>();
        if (items != null) {
            for (ItemRef ref : items) {
                if (ref != null && ref.id != null && !ref.id.isEmpty()) {
                    this.items.add(ref);
                }
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = ByteBufUtils.readVarInt(buf, 5);
        int limit = Math.min(count, MAX_ITEMS);
        List<ItemRef> read = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            String id = ByteBufUtils.readUTF8String(buf);
            int meta = buf.readInt();
            read.add(new ItemRef(id, meta));
        }
        // 超上限的剩余条目不再读取（本包清单为唯一载荷，尾部字节本就丢弃；防御性场景不该出现）
        this.items = read;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(items == null ? 0 : items.size(), MAX_ITEMS);
        ByteBufUtils.writeVarInt(buf, count, 5);
        for (int i = 0; i < count; i++) {
            ItemRef ref = items.get(i);
            ByteBufUtils.writeUTF8String(buf, ref.id == null ? "" : ref.id);
            buf.writeInt(ref.meta);
        }
    }

    public List<ItemRef> getItems() {
        return items;
    }

    /**
     * 物品引用（注册 id + meta 的轻量对，仅用于客户端演出展示，不承载 NBT）
     */
    public static final class ItemRef {

        /** 物品注册 id（如 {@code gregtech:gt.metaitem.01}） */
        public final String id;
        /** 物品 meta/damage 值 */
        public final int meta;

        public ItemRef(String id, int meta) {
            this.id = id == null ? "" : id;
            this.meta = meta;
        }

        @Override
        public String toString() {
            return id + "@" + meta;
        }
    }

    public static class Handler implements IMessageHandler<GrantEffectPacket, IMessage> {

        @Override
        public IMessage onMessage(GrantEffectPacket message, MessageContext ctx) {
            // 本包只发往客户端；handler 在 Netty 线程执行，仅写 volatile holder（渲染方主线程读取）
            if (ctx.side == Side.CLIENT) {
                List<ItemRef> items = message.items == null ? new ArrayList<ItemRef>() : message.items;
                ClientReincarnationFxState.beginGrantEffect(items);
                GTInterestingThing.LOG.info("[reincarnation] 收到发放演出包：物品 " + items.size() + " 项，FX 状态已置位");
            }
            return null;
        }
    }
}
