package com.miaokatze.gtit.common.items.terminal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.gui.terminal.NekoRemoteVendingGui;
import com.miaokatze.gtit.lottery.LotteryGui;
import com.miaokatze.gtit.lottery.LotteryRequestPacket;
import com.miaokatze.gtit.mail.MailActionPacket;
import com.miaokatze.gtit.mail.MailGui;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.mojang.authlib.GameProfile;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Real vanilla NBT plus isolated player/container lifecycle checks; no running Minecraft server. */
public final class RemoteVendingTerminalTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(RemoteVendingTerminalTest.class, args)) return;
        Field namedMods = cpw.mods.fml.common.Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        namedMods.set(cpw.mods.fml.common.Loader.instance(), new java.util.HashMap<>());
        cpw.mods.fml.server.FMLServerHandler.instance();
        Thread.currentThread()
            .setName("Server thread");
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("binding_roundtrip_and_rebind_preserve_other_nbt", RemoteVendingTerminalTest::bindingRoundtrip);
        cases.put("missing_or_wrong_types_do_not_bind_to_origin", RemoteVendingTerminalTest::rejectMalformed);
        cases.put("session_binding_slot_and_container_lifecycle", RemoteVendingTerminalTest::sessionLifecycle);
        cases
            .put("client_model_keeps_real_address_without_world_registration", RemoteVendingTerminalTest::displayModel);
        cases.put("actual_lottery_and_mail_endpoints_bind_remote_session", RemoteVendingTerminalTest::packetEndpoints);
        cases.put(
            "remote_access_keeps_gt_machine_permissions_and_liveness",
            RemoteVendingTerminalTest::machinePermissions);
        cases.replaceAll((name, action) -> () -> {
            try {
                action.run();
            } catch (RuntimeException | Error failure) {
                failure.printStackTrace();
                throw failure;
            }
        });
        TestRunner.run(RemoteVendingTerminalTest.class, cases);
    }

    private static ItemStack terminal() {
        return new ItemStack(new ItemNekoVendingTerminal());
    }

    private static void bindingRoundtrip() {
        ItemStack stack = terminal();
        SimpleAssert.eq(null, NekoVendingTarget.read(stack), "unbound has no default target");
        SimpleAssert.that(!stack.hasTagCompound(), "read does not write NBT");
        NBTTagCompound root = new NBTTagCompound();
        root.setString("customName", "kept");
        stack.setTagCompound(root);
        NekoVendingTarget first = new NekoVendingTarget(-1, -123456, 255, 987654);
        first.write(stack);
        SimpleAssert.eq(first, NekoVendingTarget.read(stack), "negative dimension and coordinates roundtrip");
        SimpleAssert.eq(first, NekoVendingTarget.read(stack.copy()), "binding survives item copying");
        NekoVendingTarget second = new NekoVendingTarget(1048580, 10, 0, -10);
        second.write(stack);
        SimpleAssert.eq(second, NekoVendingTarget.read(stack), "rebind replaces old target");
        SimpleAssert.eq("kept", root.getString("customName"), "unrelated item data preserved");
    }

    private static void rejectMalformed() {
        ItemStack stack = terminal();
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound target = new NBTTagCompound();
        root.setTag(NekoVendingTarget.TAG, target);
        stack.setTagCompound(root);
        SimpleAssert.eq(null, NekoVendingTarget.read(stack), "empty compound is not overworld origin");
        target.setInteger("dimension", 0);
        target.setInteger("x", 0);
        target.setInteger("y", 64);
        target.setString("z", "0");
        SimpleAssert.eq(null, NekoVendingTarget.read(stack), "wrong numeric type rejected");
        target.setInteger("z", 0);
        SimpleAssert.that(NekoVendingTarget.read(stack) != null, "all four explicit integers accepted");
        target.setInteger("y", -1);
        SimpleAssert.eq(null, NekoVendingTarget.read(stack), "negative height rejected");
        target.setInteger("y", 256);
        SimpleAssert.eq(null, NekoVendingTarget.read(stack), "out of world height rejected");
        SimpleAssert.eq(
            null,
            RemoteVendingAccess.findLoaded(new NekoVendingTarget(Integer.MIN_VALUE, 0, 64, 0)),
            "missing dimension does not get initialized");
    }

    private static void sessionLifecycle() {
        PlayerShell player = playerShell();
        ItemStack stack = terminal();
        NekoVendingTarget target = new NekoVendingTarget(-1, 40, 64, -50);
        target.write(stack);
        player.inventory.mainInventory[2] = stack;
        player.inventory.currentItem = 2;
        PlayerInventoryGuiData source = new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, 2);
        BaseMetaTileEntity tile = (BaseMetaTileEntity) allocate(BaseMetaTileEntity.class);
        tile.setWorldObj(player.worldObj);
        RemoteVendingSession session = new RemoteVendingSession(source, target, tile);
        SimpleAssert.that(session.isValid(), "client lifecycle initially valid");
        SimpleAssert.that(
            session.getTileEntity() == tile && session.getWorld() == player.worldObj,
            "session uses its supplied model rather than looking up an unrelated local coordinate");
        SimpleAssert.eq(target, session.getTarget(), "actual target dimension retained");
        player.inventory.currentItem = 3;
        SimpleAssert.that(!session.isValid(), "changing held slot invalidates session");
        player.inventory.currentItem = 2;
        player.inventory.mainInventory[2] = stack.copy();
        SimpleAssert.that(!session.isValid(), "replaced item with same NBT cannot inherit session");
        player.inventory.mainInventory[2] = stack;
        new NekoVendingTarget(0, 1, 64, 1).write(stack);
        SimpleAssert.that(!session.isValid(), "rebind revokes old address");
        target.write(stack);
        player.dimension++;
        SimpleAssert.that(!session.isValid(), "player dimension change closes old session");
        player.dimension--;

        ModularSyncManager modularSync = new ModularSyncManager(false);
        PanelSyncManager sync = new PanelSyncManager(modularSync, true);
        ModularContainer container = (ModularContainer) allocate(ModularContainer.class);
        setField(ModularSyncManager.class, modularSync, "container", container);
        container.windowId = 7;
        player.openContainer = container;
        int[] actions = { 0 };
        Runnable guarded = RemoteVendingSession.guard(session, sync, () -> actions[0]++);
        guarded.run();
        SimpleAssert.eq(1, actions[0], "current exact container executes queued action");
        player.openContainer = null;
        guarded.run();
        SimpleAssert.eq(1, actions[0], "closed container cannot execute delayed action");
        player.openContainer = container;
        container.windowId++;
        guarded.run();
        SimpleAssert.eq(1, actions[0], "reused container window cannot inherit action");
        session.close();
        SimpleAssert.that(!session.isValid(), "close listener revokes session permanently");
    }

    private static void displayModel() {
        PlayerShell player = playerShell();
        ItemStack stack = terminal();
        NekoVendingTarget target = new NekoVendingTarget(-42, 1234567, 80, -7654321);
        target.write(stack);
        player.inventory.mainInventory[0] = stack;
        PlayerInventoryGuiData data = new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, 0);
        try {
            Method method = NekoRemoteVendingGui.class
                .getDeclaredMethod("createDisplayModel", PlayerInventoryGuiData.class, NekoVendingTarget.class);
            method.setAccessible(true);
            BaseMetaTileEntity model = (BaseMetaTileEntity) method.invoke(null, data, target);
            SimpleAssert.that(model.getWorld() == player.worldObj, "model uses only current client clock/world");
            SimpleAssert.eq(target.x, model.xCoord, "real remote x retained");
            SimpleAssert.eq(target.y, model.yCoord, "real remote y retained");
            SimpleAssert.eq(target.z, model.zCoord, "real remote z retained");
            SimpleAssert.eq(
                target.dimension,
                RemoteVendingDisplayTile.dimensionOf(model),
                "remote dimension retained independently of client world");
            SimpleAssert.that(
                model.getMetaTileEntity()
                    .getBaseMetaTileEntity() == model,
                "original GUI sees a paired client-only MTE/base model");
            SimpleAssert.eq(0L, model.getTimer(), "display model has never ticked");
            SimpleAssert.that(
                player.worldObj.loadedTileEntityList == null,
                "creating model did not register a tile into the constructor-free client world shell");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void packetEndpoints() {
        EntityPlayerMP player = (EntityPlayerMP) allocate(EntityPlayerMP.class);
        player.worldObj = playerShell().worldObj;
        player.inventory = new InventoryPlayer(player);
        ItemStack stack = terminal();
        NekoVendingTarget target = new NekoVendingTarget(-42, 1234567, 80, -7654321);
        target.write(stack);
        player.inventory.mainInventory[0] = stack;
        PlayerInventoryGuiData data = new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, 0);
        try {
            Method create = NekoRemoteVendingGui.class
                .getDeclaredMethod("createDisplayModel", PlayerInventoryGuiData.class, NekoVendingTarget.class);
            create.setAccessible(true);
            BaseMetaTileEntity model = (BaseMetaTileEntity) create.invoke(null, data, target);
            for (Class<?> gui : new Class<?>[] { LotteryGui.class, MailGui.class }) {
                Method dimension = gui.getDeclaredMethod("requestMachineDimension", IGregTechTileEntity.class);
                dimension.setAccessible(true);
                SimpleAssert.eq(
                    -42,
                    dimension.invoke(null, model),
                    gui.getSimpleName() + " actual request address uses target dimension");
            }
            RemoteVendingSession session = new RemoteVendingSession(data, target, model);
            player.openContainer = new RemoteVendingContainer(session);
            LotteryRequestPacket lottery = new LotteryRequestPacket(
                "pool",
                1,
                target.x,
                target.y,
                target.z,
                target.dimension);
            MailActionPacket mail = new MailActionPacket(
                "recipient",
                "title",
                "body",
                target.x,
                target.y,
                target.z,
                target.dimension);
            Object[] handlers = { new LotteryRequestPacket.Handler(), new MailActionPacket.Handler() };
            Object[] requests = { lottery, mail };
            Object[] wrong = { new LotteryRequestPacket("pool", 1, target.x, target.y, target.z, 0),
                new MailActionPacket(
                    "recipient",
                    "title",
                    "body",
                    target.x + 1,
                    target.y,
                    target.z,
                    target.dimension) };
            for (int i = 0; i < handlers.length; i++) {
                Method find = handlers[i].getClass()
                    .getDeclaredMethod("findMachine", EntityPlayerMP.class, requests[i].getClass());
                find.setAccessible(true);
                SimpleAssert.that(
                    find.invoke(handlers[i], player, requests[i]) == model.getMetaTileEntity(),
                    "actual " + handlers[i].getClass()
                        .getName() + " resolves only current session model in isolated client fixture");
                SimpleAssert.eq(
                    null,
                    find.invoke(handlers[i], player, wrong[i]),
                    "mismatched target cannot access remote inventory");
                player.inventory.currentItem = 1;
                SimpleAssert.eq(
                    null,
                    find.invoke(handlers[i], player, requests[i]),
                    "changed held slot revokes actual endpoint");
                player.inventory.currentItem = 0;
            }
            session.close();
            for (int i = 0; i < handlers.length; i++) {
                Method find = handlers[i].getClass()
                    .getDeclaredMethod("findMachine", EntityPlayerMP.class, requests[i].getClass());
                find.setAccessible(true);
                SimpleAssert
                    .eq(null, find.invoke(handlers[i], player, requests[i]), "closed session rejects actual endpoint");
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void machinePermissions() {
        BaseMetaTileEntity tile = new BaseMetaTileEntity();
        PermissionMachine machine = (PermissionMachine) allocate(PermissionMachine.class);
        machine.setBaseMetaTileEntity(tile);
        tile.setOwnerName("existing-owner");
        tile.mTickTimer = 2;
        machine.allowed = true;
        PlayerShell player = playerShell();
        player.posX = 100000;
        SimpleAssert
            .that(RemoteVendingAccess.canUse(tile, player), "remote access intentionally permits distance only");
        machine.allowed = false;
        SimpleAssert.that(!RemoteVendingAccess.canUse(tile, player), "MTE access denial is retained");
        machine.allowed = true;
        tile.mTickTimer = 1;
        SimpleAssert.that(!RemoteVendingAccess.canUse(tile, player), "freshly initialized tile remains unusable");
        tile.mTickTimer = 2;
        tile.isDead = true;
        SimpleAssert.that(!RemoteVendingAccess.canUse(tile, player), "dead tile remains unusable");
        tile.isDead = false;
        tile.setMetaTileEntity(null);
        SimpleAssert.that(!RemoteVendingAccess.canUse(tile, player), "detached MTE remains unusable");
    }

    private static PlayerShell playerShell() {
        World world = (World) allocate(WorldServer.class);
        setField(World.class, world, "isRemote", true);
        PlayerShell player = (PlayerShell) allocate(PlayerShell.class);
        player.worldObj = world;
        player.dimension = 0;
        player.inventory = new InventoryPlayer(player);
        return player;
    }

    private static Object allocate(Class<?> type) {
        try {
            Class<?> unsafe = Class.forName("sun.misc.Unsafe");
            Field field = unsafe.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return unsafe.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), type);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Class<?> owner, Object instance, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class PlayerShell extends EntityPlayer {

        private PlayerShell(World world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return null;
        }

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return false;
        }

        @Override
        public void addChatMessage(net.minecraft.util.IChatComponent component) {}
    }

    private static final class PermissionMachine extends MTENekoVendingMachineV2 {

        boolean allowed;

        private PermissionMachine() {
            super("permission_fixture");
        }

        @Override
        public boolean ownerControl() {
            return false;
        }

        @Override
        public boolean isAccessAllowed(EntityPlayer player) {
            return allowed;
        }
    }
}
