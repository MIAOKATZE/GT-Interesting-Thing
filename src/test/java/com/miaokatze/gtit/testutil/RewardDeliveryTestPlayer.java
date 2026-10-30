package com.miaokatze.gtit.testutil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;

import io.netty.channel.embedded.EmbeddedChannel;

/** Real vanilla inventory/actor identity with only entity-spawn acceptance controlled; no game is launched. */
public final class RewardDeliveryTestPlayer implements AutoCloseable {

    public final EntityPlayerMP player;
    public final DropWorld world;
    private final MinecraftServer previous;
    private final ServerConfigurationManager configuration;
    private final EmbeddedChannel channel;

    public RewardDeliveryTestPlayer(UUID id, boolean acceptsDrops) throws Exception {
        previous = MinecraftServer.getServer();
        MinecraftServer server = allocate(net.minecraft.server.dedicated.DedicatedServer.class);
        configuration = allocate(net.minecraft.server.dedicated.DedicatedPlayerList.class);
        set(ServerConfigurationManager.class, configuration, "playerEntityList", new ArrayList<EntityPlayerMP>());
        set(MinecraftServer.class, server, "serverConfigManager", configuration);
        set(MinecraftServer.class, null, "mcServer", server);
        world = allocate(DropWorld.class);
        set(World.class, world, "provider", new WorldProviderSurface());
        world.accept = acceptsDrops;
        player = allocate(EntityPlayerMP.class);
        set(Entity.class, player, "entityUniqueID", id);
        DataWatcher watcher = new DataWatcher(player);
        watcher.addObject(6, Float.valueOf(20.0F));
        set(Entity.class, player, "dataWatcher", watcher);
        NetHandlerPlayServer handler = allocate(NetHandlerPlayServer.class);
        handler.playerEntity = player;
        NetworkManager network = new NetworkManager(false);
        channel = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        set(NetworkManager.class, network, "channel", channel);
        set(NetHandlerPlayServer.class, handler, "netManager", network);
        player.playerNetServerHandler = handler;
        player.worldObj = world;
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        player.inventoryContainer = new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer ignored) {
                return true;
            }
        };
        connect();
    }

    @SuppressWarnings("unchecked")
    public void connect() {
        configuration.playerEntityList.clear();
        configuration.playerEntityList.add(player);
    }

    public void disconnect() {
        configuration.playerEntityList.clear();
    }

    public void disconnectChannel() {
        channel.close()
            .syncUninterruptibly();
    }

    public void setWorldDirectory(java.nio.file.Path directory) throws Exception {
        Object handler = java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { net.minecraft.world.storage.ISaveHandler.class },
            (proxy, method, arguments) -> method.getName()
                .equals("getWorldDirectory") ? directory.toFile() : null);
        set(World.class, world, "saveHandler", handler);
    }

    @Override
    public void close() throws Exception {
        disconnectChannel();
        set(MinecraftServer.class, null, "mcServer", previous);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    public static final class DropWorld extends WorldServer {

        public boolean accept;
        public int calls;
        public int delivered;

        private DropWorld() {
            super(null, null, "", 0, null, null);
        }

        @Override
        public boolean spawnEntityInWorld(Entity entity) {
            calls++;
            ItemStack stack = ((EntityItem) entity).getEntityItem();
            if (accept && (stack.stackSize <= 0 || stack.stackSize > Math.min(64, stack.getMaxStackSize())))
                throw new AssertionError("Illegal reward entity count: " + stack.stackSize);
            if (accept) delivered += stack.stackSize;
            return accept;
        }
    }
}
