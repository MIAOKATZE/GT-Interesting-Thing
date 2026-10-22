package com.miaokatze.gtit.common.items.terminal;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.common.util.GTITUtils;
import com.miaokatze.gtit.gui.terminal.NekoRemoteVendingGui;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.register.CreativeTabManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.metatileentity.BaseMetaTileEntity;

public final class ItemNekoVendingTerminal extends Item implements IGuiHolder<PlayerInventoryGuiData> {

    public static final String ID = "neko_vending_terminal";
    private final Map<EntityPlayer, Long> lastBindingTick = new WeakHashMap<>();

    public ItemNekoVendingTerminal() {
        setUnlocalizedName(ID);
        setTextureName(GTInterestingThing.MODID + ":" + ID);
        setMaxStackSize(1);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        // Returning false on the client lets the authoritative C08 interaction reach the server.
        if (world.isRemote) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof BaseMetaTileEntity)) return false;
        BaseMetaTileEntity machine = (BaseMetaTileEntity) tile;
        if (!(machine.getMetaTileEntity() instanceof MTENekoVendingMachineV2)) return false;
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
        // Existing factory: no custom network factory registration or client-provided target packet.
        GuiFactories.playerInventory()
            .openFromMainHand(player);
        return stack;
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
