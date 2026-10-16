package com.miaokatze.gtit.testlab;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import com.miaokatze.gtit.common.api.enums.GTITItemList;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * Explicit test-client transport. Acknowledgements are observations, never visual acceptance or trusted gameplay input.
 */
public final class TestLabUiNetwork {

    private static SimpleNetworkWrapper channel;
    private static volatile Consumer<Request> clientRequest;
    private static volatile Session active;
    private static UUID requestedPlayer;

    private TestLabUiNetwork() {}

    static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("gtit_testlab");
        channel.registerMessage(RequestHandler.class, Request.class, 0, Side.CLIENT);
        channel.registerMessage(AckHandler.class, Ack.class, 1, Side.SERVER);
        TestLabRuntime.register(new ProbeScenario() {

            @Override
            public String id() {
                return "client-ui-pocket";
            }

            @Override
            public boolean defaultEnabled() {
                return false;
            }

            @Override
            public ProbeTask start(ProbeContext context) {
                EntityPlayerMP player = requestedPlayer == null ? null
                    : MinecraftServer.getServer()
                        .getConfigurationManager()
                        .func_152612_a(requestedPlayer.toString());
                // Lookup by UUID explicitly; username lookup does not accept UUID strings in all 1.7.10 builds.
                if (requestedPlayer != null) for (Object candidate : MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList)
                    if (candidate instanceof EntityPlayerMP real && requestedPlayer.equals(real.getUniqueID()))
                        player = real;
                requestedPlayer = null;
                if (!validPlayer(player)) {
                    context.skip("open-pocket", "Explicit online OP test client is required");
                    return () -> true;
                }
                requireEmptyInventory(player);
                Session session = new Session(player, context.runId(), player.inventory.currentItem);
                context.onCleanup(() -> {
                    try {
                        scrub(session);
                        session.player.closeScreen();
                    } finally {
                        try {
                            scrub(session);
                        } finally {
                            if (active == session) active = null;
                        }
                    }
                });
                active = session;
                ItemStack pocket = context.mark(GTITItemList.NekoDimensionPocket.get(1));
                player.inventory.setInventorySlotContents(session.slot, pocket);
                player.inventoryContainer.detectAndSendChanges();
                channel.sendTo(new Request(session.runId, session.nonce, session.dimension, session.slot), player);
                return new ProbeTask() {

                    int ticks;

                    @Override
                    public boolean tick() {
                        if (session.violation != null) {
                            context.check("open-pocket", false, session.violation);
                            return true;
                        }
                        if (!validPlayer(session.player) || session.player.worldObj != session.world
                            || session.player.dimension != session.dimension) {
                            context.check(
                                "open-pocket",
                                false,
                                "Test client disconnected, lost permission or changed worlds");
                            return true;
                        }
                        Ack ack = session.ack;
                        if (ack != null) {
                            if (ack.result == 2) context.skip("open-pocket", "Client UI opt-in disabled");
                            else context.check(
                                "open-pocket",
                                ack.result == 1 && "gtit.pocket.main".equals(ack.screen),
                                "Client acknowledgement: " + ack.screen + "; screen-open check only");
                            return true;
                        }
                        if (++ticks >= 160) {
                            context.skip(
                                "open-pocket",
                                "No dedicated client acknowledgement within 160 ticks; UI not verified");
                            return true;
                        }
                        return false;
                    }
                };
            }
        });
    }

    static String run(EntityPlayerMP player) {
        if (!validPlayer(player)) throw new IllegalArgumentException("An online OP test client is required");
        requireEmptyInventory(player);
        requestedPlayer = player.getUniqueID();
        try {
            return TestLabRuntime.INSTANCE.run("ui", "client-ui-pocket", 1);
        } catch (RuntimeException error) {
            requestedPlayer = null;
            throw error;
        }
    }

    private static void requireEmptyInventory(EntityPlayerMP player) {
        if (player.inventory.getItemStack() != null)
            throw new IllegalStateException("UI probes require an empty cursor");
        if (player.openContainer != player.inventoryContainer || player.isSneaking())
            throw new IllegalStateException("Test client must close its UI and stand upright");
        for (int i = 0; i < player.inventory.getSizeInventory(); i++)
            if (player.inventory.getStackInSlot(i) != null) throw new IllegalStateException(
                "UI probes require a dedicated test client with an entirely empty inventory");
    }

    private static void scrub(Session session) {
        if (marked(session.player.inventory.getItemStack(), session.runId)) session.player.inventory.setItemStack(null);
        for (int i = 0; i < session.player.inventory.getSizeInventory(); i++) {
            ItemStack stack = session.player.inventory.getStackInSlot(i);
            if (marked(stack, session.runId)) session.player.inventory.setInventorySlotContents(i, null);
        }
        session.player.inventoryContainer.detectAndSendChanges();
    }

    static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        Session session = active;
        if (session != null && event.world == session.world
            && event.entity instanceof net.minecraft.entity.item.EntityItem drop
            && marked(drop.getEntityItem(), session.runId)) {
            session.violation = "Test item was dropped; exact marked drop suppressed and fixture removed";
            event.setCanceled(true);
            event.entity.setDead();
        }
    }

    static void onLogout(net.minecraft.entity.player.EntityPlayer player) {
        Session session = active;
        if (session != null && session.player == player) {
            session.violation = "Test client disconnected; temporary test item removed";
            scrub(session);
            // Logout event ordering differs across forks. Persist the scrubbed inventory even if vanilla saved earlier.
            session.player.getServerForPlayer()
                .getSaveHandler()
                .getSaveHandler()
                .writePlayerData(session.player);
        }
    }

    static boolean marked(ItemStack stack, String runId) {
        return stack != null && stack.hasTagCompound()
            && runId.equals(
                stack.getTagCompound()
                    .getCompoundTag(ProbeContext.TEST_ITEM_TAG)
                    .getString("runId"));
    }

    private static boolean validPlayer(EntityPlayerMP player) {
        return player != null && !(player instanceof net.minecraftforge.common.util.FakePlayer)
            && !player.isDead
            && player.worldObj != null
            && !player.worldObj.isRemote
            && player.canCommandSenderUseCommand(2, "gtitlab")
            && MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152612_a(player.getCommandSenderName()) == player;
    }

    public static void setClientRequest(Consumer<Request> handler) {
        clientRequest = handler;
    }

    public static void acknowledge(Request request, int result, String screen) {
        if (channel != null)
            channel.sendToServer(new Ack(request.runId, request.nonce, request.dimension, result, screen));
    }

    static void tick() {
        Session session = active;
        if (session != null) session.age++;
    }

    static void stop() {
        active = null;
        requestedPlayer = null;
    }

    private static final class Session {

        final EntityPlayerMP player;
        final String runId;
        final String nonce = UUID.randomUUID()
            .toString();
        final int slot;
        final int dimension;
        final net.minecraft.world.World world;
        final AtomicBoolean accepted = new AtomicBoolean();
        volatile Ack ack;
        volatile int age;
        volatile String violation;

        Session(EntityPlayerMP player, String runId, int slot) {
            this.player = player;
            this.runId = runId;
            this.slot = slot;
            dimension = player.dimension;
            world = player.worldObj;
        }
    }

    public static class Request implements IMessage {

        public String runId;
        public String nonce;
        public int dimension;
        public int slot;

        public Request() {}

        Request(String runId, String nonce, int dimension, int slot) {
            this.runId = runId;
            this.nonce = nonce;
            this.dimension = dimension;
            this.slot = slot;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            if (buffer.readableBytes() > 256) throw new IllegalArgumentException("Oversized UI probe");
            runId = ByteBufUtils.readUTF8String(buffer);
            nonce = ByteBufUtils.readUTF8String(buffer);
            dimension = buffer.readInt();
            slot = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            ByteBufUtils.writeUTF8String(buffer, runId);
            ByteBufUtils.writeUTF8String(buffer, nonce);
            buffer.writeInt(dimension);
            buffer.writeInt(slot);
        }
    }

    public static final class Ack extends Request {

        int result;
        String screen;

        public Ack() {}

        Ack(String runId, String nonce, int dimension, int result, String screen) {
            super(runId, nonce, dimension, 0);
            this.result = result;
            this.screen = screen;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            if (buffer.readableBytes() > 512) throw new IllegalArgumentException("Oversized UI acknowledgement");
            super.fromBytes(buffer);
            result = buffer.readInt();
            screen = ByteBufUtils.readUTF8String(buffer);
            if (screen.length() > 128) throw new IllegalArgumentException("Oversized screen name");
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            super.toBytes(buffer);
            buffer.writeInt(result);
            ByteBufUtils.writeUTF8String(buffer, screen);
        }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {

        @Override
        public IMessage onMessage(Request request, MessageContext context) {
            Consumer<Request> handler = clientRequest;
            if (handler != null) handler.accept(request);
            else acknowledge(request, 2, "client UI opt-in disabled");
            return null;
        }
    }

    public static final class AckHandler implements IMessageHandler<Ack, IMessage> {

        @Override
        public IMessage onMessage(Ack ack, MessageContext context) {
            Session session = active;
            EntityPlayerMP sender = context.getServerHandler().playerEntity;
            if (session == null || session.player != sender
                || session.age >= 160
                || !session.runId.equals(ack.runId)
                || !session.nonce.equals(ack.nonce)
                || ack.dimension != session.dimension
                || ack.result < 0
                || ack.result > 2
                || !session.accepted.compareAndSet(false, true)) return null;
            // No world mutation on Netty. The task rechecks the real session, dimension and permission on the server
            // tick.
            session.ack = ack;
            return null;
        }
    }
}
