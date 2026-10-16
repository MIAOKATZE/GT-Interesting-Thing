package com.miaokatze.gtit.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.miaokatze.gtit.gift.GiftRegistry;
import com.miaokatze.gtit.gift.GiftService;
import com.miaokatze.gtit.gift.ui.GiftGuiFactory;

/** Player-facing library; all editing permission is checked by the UI's server actions. */
public final class GiftLibraryCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gifts";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gifts [claim <id>]";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(new ChatComponentTranslation("gtit.gift.command.player_only"));
            return;
        }
        if (!GiftService.INSTANCE.isReady()) {
            player.addChatMessage(new ChatComponentTranslation("gtit.gift.failed"));
            return;
        }
        try {
            if (args.length == 0 || args.length == 1 && "ui".equals(args[0])) GiftGuiFactory.open(player);
            else if (args.length == 2 && "claim".equals(args[0])) {
                GiftService.INSTANCE.claim(player, args[1]);
                player.addChatMessage(new ChatComponentTranslation("gtit.gift.command.claimed"));
            } else player.addChatMessage(new ChatComponentTranslation("gtit.gift.command.usage"));
        } catch (RuntimeException failure) {
            player.addChatMessage(new ChatComponentTranslation("gtit.gift.failed"));
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "ui", "claim");
        if (args.length == 2 && "claim".equals(args[0]) && GiftRegistry.INSTANCE.isReady()) {
            return getListOfStringsMatchingLastWord(
                args,
                GiftRegistry.INSTANCE.snapshot().definitions.stream()
                    .map(gift -> gift.id)
                    .toArray(String[]::new));
        }
        return null;
    }
}
