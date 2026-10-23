package com.miaokatze.gtit.common.items.terminal;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.common.util.GTITUtils;
import com.miaokatze.gtit.gui.terminal.NekoRemoteVendingGui;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.register.CreativeTabManager;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import appeng.api.features.IWirelessTermHandler;
import appeng.api.util.IConfigManager;
import appeng.util.ConfigManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.metatileentity.BaseMetaTileEntity;

public final class ItemNekoVendingTerminal extends Item
    implements IGuiHolder<PlayerInventoryGuiData>, IWirelessTermHandler {

    public static final String ID = "neko_vending_terminal";
    private final Map<EntityPlayer, Long> lastBindingTick = new WeakHashMap<>();
    private final Map<EntityPlayer, Object> pendingOpens = new WeakHashMap<>();

    public ItemNekoVendingTerminal() {
        setUnlocalizedName(ID);
        setTextureName(GTInterestingThing.MODID + ":" + ID);
        setMaxStackSize(1);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
    }

    /** Community type identification only; AE wireless inventory access remains unsupported. */
    @Override
    public boolean canHandle(ItemStack stack) {
        return false;
    }

    @Override
    public boolean hasPower(EntityPlayer player, double amount, ItemStack stack) {
        return false;
    }

    @Override
    public boolean usePower(EntityPlayer player, double amount, ItemStack stack) {
        return false;
    }

    /** Independent empty configuration; AE configuration never writes the machine binding. */
    @Override
    public IConfigManager getConfigManager(ItemStack stack) {
        return new ConfigManager((manager, settingName, newValue) -> {});
    }

    @Override
    public String getEncryptionKey(ItemStack stack) {
        return "";
    }

    @Override
    public void setEncryptionKey(ItemStack stack, String key, String name) {}

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        // Returning false on the client lets the authoritative C08 interaction reach the server.
        if (world.isRemote) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof BaseMetaTileEntity)) return false;
        BaseMetaTileEntity machine = (BaseMetaTileEntity) tile;
        if (!(machine.getMetaTileEntity() instanceof MTENekoVendingMachineV2)) return false;
        pendingOpens.remove(player);
        lastBindingTick.put(player, world.getTotalWorldTime());
        if (player.getHeldItem() != stack || !machine.isUseableByPlayer(player)) {
            player.addChatMessage(new ChatComponentTranslation("gtit.terminal.access_denied"));
            return true;
        }
        NekoVendingTarget target = new NekoVendingTarget(world.provider.dimensionId, x, y, z);
        target.write(stack);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).playerNetServerHandler
                .sendPacket(new S2FPacketSetSlot(0, 36 + player.inventory.currentItem, stack));
        }
        player.addChatMessage(new ChatComponentTranslation("gtit.terminal.bound", target.dimension, x, y, z));
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || !(player instanceof EntityPlayerMP) || player.getHeldItem() != stack) return stack;
        Long boundAt = lastBindingTick.get(player);
        // The client can append an air-use packet to the same block click; binding must not open a second GUI.
        if (boundAt != null && world.getTotalWorldTime() - boundAt >= 0 && world.getTotalWorldTime() - boundAt <= 1)
            return stack;
        NekoVendingTarget target = NekoVendingTarget.read(stack);
        String failure = RemoteVendingAccess.failure(target, player);
        if (failure != null) {
            player.addChatMessage(new ChatComponentTranslation(failure));
            return stack;
        }
        // C08 handling copies the held stack AFTER onItemRightClick returns. Opening here would bind the
        // session to the discarded object and close it on the first interaction check. Capture the final
        // inventory object at tick END instead, after vanilla has finished processing the use packet.
        PendingOpen pending = new PendingOpen(player, stack);
        Object request = new Object();
        pendingOpens.put(player, request);
        ServerTaskScheduler.scheduleServerTask(() -> {
            if (pendingOpens.get(player) != request) return;
            pendingOpens.remove(player);
            PlayerInventoryGuiData data = pending.resolve();
            if (data == null) return;
            String deferredFailure = RemoteVendingAccess.failure(target, player);
            if (deferredFailure != null) {
                player.addChatMessage(new ChatComponentTranslation(deferredFailure));
                return;
            }
            GuiFactories.playerInventory()
                .openFromPlayerInventory(player, data.getSlotIndex());
        });
        return stack;
    }

    /** Short-lived use request; only this pre-opening step accepts vanilla's identical stack copy. */
    static final class PendingOpen {

        private final EntityPlayer player;
        private final World world;
        private final int dimension;
        private final int slot;
        private final ItemStack snapshot;
        private final Container container;
        private final int window;

        PendingOpen(EntityPlayer player, ItemStack stack) {
            this.player = player;
            world = player.worldObj;
            dimension = player.dimension;
            slot = player.inventory.currentItem;
            snapshot = stack.copy();
            container = player.openContainer;
            window = container == null ? -1 : container.windowId;
        }

        PlayerInventoryGuiData resolve() {
            if (player.isDead || player.worldObj != world
                || player.dimension != dimension
                || player.inventory.currentItem != slot
                || player.openContainer != container
                || (container != null && container.windowId != window)
                || !ItemStack.areItemStacksEqual(snapshot, player.getHeldItem())) return null;
            return new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, slot);
        }
    }

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager sync, UISettings settings) {
        return NekoRemoteVendingGui.build(data, sync, settings);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel panel) {
        return NekoRemoteVendingGui.createScreen(data, panel);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtit.terminal.tooltip.bind"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtit.terminal.tooltip.open"));
        NekoVendingTarget target = NekoVendingTarget.read(stack);
        tooltip.add(
            target == null ? StatCollector.translateToLocal("gtit.terminal.unbound")
                : StatCollector
                    .translateToLocalFormatted("gtit.terminal.bound", target.dimension, target.x, target.y, target.z));
        tooltip.add(GTITUtils.getAddedByLine());
    }
}
