package com.miaokatze.gtit.trade.preset.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtit.trade.preset.TradePresetManager;
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
public final class TradePresetAccessHandler {

    private static final TradePresetAccessHandler INSTANCE = new TradePresetAccessHandler();
    private static final Map<UUID, Login> LOGINS = new HashMap<>();
    private static volatile Consumer<Access> clientReceiver;
    private static SimpleNetworkWrapper channel;
    private static boolean installed;

    private TradePresetAccessHandler() {}

    public static synchronized void install() {
        if (installed) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("gtit_presets");
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

    public static boolean enqueue(EntityPlayerMP player, Runnable action) {
        return ServerTaskScheduler.trySchedulePlayerRequest(player, action);
    }

    public static boolean authorized(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        return player != null && !(player instanceof FakePlayer)
            && server != null
            && !player.isDead
            && server.getConfigurationManager()
                .func_152612_a(player.getCommandSenderName()) == player
            && TradePresetManager.canConfigure(player);
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
            boolean remind = false;
            if (allowed && login.age >= 160) {
                NoticeData data = NoticeData.get();
                if (data != null) {
                    if (data.legacyShown) {
                        Set<UUID> onlineManagers = new HashSet<>();
                        for (Login online : LOGINS.values())
                            if (authorized(online.player)) onlineManagers.add(online.player.getUniqueID());
                        data.migrateLegacy(onlineManagers);
                    }
                    if (data.shouldNotify(login.player.getUniqueID())) {
                        data.markShown(login.player.getUniqueID());
                        remind = true;
                    }
                }
            }
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

        Login(EntityPlayerMP player) {
            this.player = player;
        }
    }

    public static final class NoticeData extends WorldSavedData {

        private static final String KEY = "gtit_trade_preset_notice";
        private final Set<UUID> notifiedPlayers = new HashSet<>();
        private boolean legacyShown;

        public NoticeData() {
            super(KEY);
        }

        public NoticeData(String name) {
            super(name);
        }

        static NoticeData get() {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.worldServerForDimension(0) == null) return null;
            net.minecraft.world.storage.MapStorage storage = server.worldServerForDimension(0).mapStorage;
            NoticeData data = (NoticeData) storage.loadData(NoticeData.class, KEY);
            if (data == null) {
                data = new NoticeData();
                storage.setData(KEY, data);
            }
            return data;
        }

        boolean shouldNotify(UUID player) {
            return !notifiedPlayers.contains(player);
        }

        void markShown(UUID player) {
            if (notifiedPlayers.add(player)) markDirty();
        }

        /** Old global flags cannot identify their recipient. Seed current authorized managers once, then use UUIDs. */
        void migrateLegacy(Set<UUID> onlineManagers) {
            if (!legacyShown) return;
            notifiedPlayers.addAll(onlineManagers);
            legacyShown = false;
            markDirty();
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            notifiedPlayers.clear();
            legacyShown = nbt.getBoolean("legacyShown")
                || nbt.getInteger("schemaVersion") < 2 && nbt.getBoolean("shown");
            NBTTagList players = nbt.getTagList("notifiedPlayers", 10);
            for (int i = 0; i < players.tagCount(); i++) {
                String id = players.getCompoundTagAt(i)
                    .getString("uuid");
                try {
                    notifiedPlayers.add(UUID.fromString(id));
                } catch (IllegalArgumentException ignored) { /*
                                                              * Ignore one corrupt UUID without losing other recipients.
                                                              */ }
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt) {
            nbt.setInteger("schemaVersion", 2);
            // Preserve an unprocessed legacy migration across saves; schemaVersion controls how it is interpreted.
            nbt.setBoolean("shown", !notifiedPlayers.isEmpty());
            nbt.setBoolean("legacyShown", legacyShown);
            NBTTagList players = new NBTTagList();
            for (UUID id : notifiedPlayers) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("uuid", id.toString());
                players.appendTag(entry);
            }
            nbt.setTag("notifiedPlayers", players);
        }
    }

    public static final class Open implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {
            if (buf.isReadable()) throw new IllegalArgumentException("Unexpected preset open payload");
        }

        @Override
        public void toBytes(ByteBuf buf) {}
    }

    public static final class OpenHandler implements IMessageHandler<Open, IMessage> {

        @Override
        public IMessage onMessage(Open message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            enqueue(player, () -> {
                Login login = LOGINS.get(player.getUniqueID());
                if (login == null || !authorized(player) || login.age - login.lastOpen < 10) return;
                login.lastOpen = login.age;
                TradePresetUI.open(player);
            });
            return null;
        }
    }

    public static final class Access implements IMessage {

        /** Receiving handler identity, local only; never serialized or accepted as server authority. */
        public transient Object connection;

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
            message.connection = context.netHandler;
            Consumer<Access> receiver = clientReceiver;
            if (receiver != null) receiver.accept(message);
            return null;
        }
    }
}
