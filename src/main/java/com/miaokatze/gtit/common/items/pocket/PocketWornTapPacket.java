package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Alt + 空手右击穿戴口袋的抽液意图；不携带口袋 NBT、流体或客户端装备判定。 */
public final class PocketWornTapPacket implements IMessage {

    int dimension;
    int x;
    int y;
    int z;
    int face;

    public PocketWornTapPacket() {}

    public PocketWornTapPacket(int dimension, int x, int y, int z, int face) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimension = buffer.readInt();
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        face = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimension);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(face);
    }

    public static final class Handler implements IMessageHandler<PocketWornTapPacket, IMessage> {

        @Override
        public IMessage onMessage(PocketWornTapPacket message, MessageContext context) {
            if (context.side != Side.SERVER) return null;
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            // Netty 线程只捕获请求和连接玩家，世界、装备、权限、抽液全部在服务器主线程现查。
            ServerTaskScheduler
                .trySchedulePlayerRequest(player, () -> PocketWornTapHandler.INSTANCE.handleRequest(player, message));
            return null;
        }
    }
}
