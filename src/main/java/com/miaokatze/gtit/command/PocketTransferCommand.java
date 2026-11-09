package com.miaokatze.gtit.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;
import com.miaokatze.gtit.common.items.pocket.PocketRuntimeSession;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;

/** Exact-receipt, level-four adjudication. Never refunds an unknown target outcome. */
public final class PocketTransferCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtitpockettransfer";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtitpockettransfer inspect <player> | settle-consumed <player> <exact-token>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2 || !("inspect".equals(args[0]) && args.length == 2
            || "settle-consumed".equals(args[0]) && args.length == 3))
            throw new WrongUsageException(getCommandUsage(sender));
        EntityPlayerMP player = getPlayer(sender, args[1]);
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemNekoDimensionPocket))
            throw new WrongUsageException("Target player must hold the affected pocket");
        PocketSession live = PocketSessions.peek(player.getUniqueID());
        if ("inspect".equals(args[0])) {
            NBTTagCompound root = held.getTagCompound();
            sender.addChatMessage(
                new ChatComponentText(
                    live instanceof PocketRuntimeSession runtime && runtime.carrierStack() == held
                        ? runtime.outgoingTransferDiagnostic()
                        : root == null || !root.hasKey("gtitPocketOutgoingTransfer") ? "none"
                            : root.getTag("gtitPocketOutgoingTransfer")
                                .toString()));
            sender.addChatMessage(
                new ChatComponentText(
                    "Unknown outcomes require destination reconciliation. settle-consumed records confirmed consumption and releases the upload lock; it creates no refund."));
            return;
        }
        if (player.openContainer != player.inventoryContainer)
            throw new WrongUsageException("Close the target player's container before adjudication");
        PocketRuntimeSession runtime;
        if (live instanceof PocketRuntimeSession active && active.carrierStack() == held) runtime = active;
        else {
            if (live != null)
                throw new WrongUsageException("Another pocket runtime is active; close and retire it first");
            runtime = new PocketRuntimeSession(
                () -> player,
                player::getHeldItem,
                player.inventory.currentItem,
                held,
                false,
                false,
                null);
        }
        try {
            runtime.acknowledgeOutgoingConsumed(args[2], sender.getCommandSenderName());
            player.inventory.markDirty();
            sender.addChatMessage(new ChatComponentText("Confirmed consumed receipt recorded; no resources refunded."));
        } catch (RuntimeException failure) {
            throw new WrongUsageException("Receipt retained: " + failure.getMessage());
        }
    }
}
