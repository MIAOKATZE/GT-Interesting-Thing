package com.miaokatze.gtit.gift.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** No client class references: packets only enqueue work; all authority runs on server ticks. */
public final class GiftAccessHandler {

    private static final GiftAccessHandler INSTANCE = new GiftAccessHandler();
    private static final Map<UUID, Login> LOGINS = new HashMap<>();
    private static volatile Consumer<Access> clientReceiver;
    private static SimpleNetworkWrapper channel;
    private static boolean installed;

    private GiftAccessHandler() {}

    public static synchronized void install() {
        if (installed) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("gtit_gifts");
        channel.registerMessage(OpenHandler.class, Open.class, 0, Side.SERVER);
        channel.registerMessage(AccessReceiver.class, Access.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        installed = true;
    }

    public static void setClientReceiver(Consumer<Access> receiver) {
        clientReceiver = receiver;
    }

    public static void requestOpen() {
        if (channel != null) channel.sendToServer(new Open());
    }

    public static void enqueue(Runnable action) {
        ServerTaskScheduler.scheduleServerTask(action);
    }

    public static boolean authorized(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        return player != null && !(player instanceof FakePlayer)
            && server != null
            && server.getConfigurationManager() != null
            && !player.isDead
            && server.getConfigurationManager()
                .func_152612_a(player.getCommandSenderName()) == player
            && player.worldObj != null
            && !player.worldObj.isRemote
            && (server.isSinglePlayer() ? player.getCommandSenderName()
                .equals(server.getServerOwner())
                : player.canCommandSenderUseCommand(DefaultContentConfig.managementOpLevel(), "gtit"));
    }

    public static void stop() {
        LOGINS.clear();
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP player && !(player instanceof FakePlayer))
            LOGINS.put(player.getUniqueID(), new Login(player));
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOGINS.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Login login : LOGINS.values()) {
            login.age++;
            if (login.age % 20 != 0) continue;
            boolean allowed = authorized(login.player);
            boolean remind = allowed && login.age >= 160 && !login.reminded;
            if (remind) login.reminded = true;
            if (login.lastAllowed == null || login.lastAllowed != allowed || remind) {
                channel.sendTo(new Access(allowed, remind), login.player);
                login.lastAllowed = allowed;
            }
        }
    }

    private static final class Login {

        final EntityPlayerMP player;
        int age;
        Boolean lastAllowed;
        long lastOpen;
        boolean reminded;

        Login(EntityPlayerMP player) {
            this.player = player;
        }
    }

    public static final class Open implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {
            if (buf.isReadable()) throw new IllegalArgumentException("Unexpected gift open payload");
        }

        @Override
        public void toBytes(ByteBuf buf) {}
    }

    public static final class OpenHandler implements IMessageHandler<Open, IMessage> {

        @Override
        public IMessage onMessage(Open message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            enqueue(() -> {
                Login login = LOGINS.get(player.getUniqueID());
                if (login == null || !authorized(player) || login.age - login.lastOpen < 10) return;
                login.lastOpen = login.age;
                GiftGuiFactory.openManagement(player);
            });
            return null;
        }
    }

    public static final class Access implements IMessage {

        public boolean allowed;
        public boolean remind;

        public Access() {}

        Access(boolean allowed, boolean remind) {
            this.allowed = allowed;
            this.remind = remind;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            allowed = buf.readBoolean();
            remind = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(allowed);
            buf.writeBoolean(remind);
        }
    }

    public static final class AccessReceiver implements IMessageHandler<Access, IMessage> {

        @Override
        public IMessage onMessage(Access message, MessageContext context) {
            Consumer<Access> receiver = clientReceiver;
            if (receiver != null) receiver.accept(message);
            return null;
        }
    }
}
