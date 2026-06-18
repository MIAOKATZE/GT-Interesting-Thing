package com.miaokatze.gtit.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtit.config.GiftConfig;
import com.miaokatze.gtit.config.GiftConfig.ItemEntry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeRegistry;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * /gtit 指令
 * - /gtit gift certain: 将当前背包物品设为必中物品
 * - /gtit gift random <count>: 将当前背包物品设为随机物品，设置随机数
 * - /gtit gift reset: 恢复默认配置
 * - /gtit nekovm reload: 热重载猫猫币交易配置
 * - /gtit nekovm list: 列出当前所有猫猫币交易
 * - /gtit nekovm save: 手动保存当前交易数据到配置文件
 */
public class GTITGiftCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtit";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtit gift certain|random <count>|reset | /gtit nekovm reload|list|save|gui";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP 权限
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender instanceof EntityPlayerMP && super.canCommandSenderUseCommand(sender);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "只有玩家可以执行此指令"));
            return;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return;
        }

        switch (args[0]) {
            case "gift" -> handleGift(sender, player, args);
            case "nekovm" -> handleNekoVM(sender, player, args);
            default -> sendHelp(sender);
        }
    }

    private void handleGift(ICommandSender sender, EntityPlayerMP player, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        switch (args[1]) {
            case "certain" -> handleCertain(player);
            case "random" -> handleRandom(player, args);
            case "reset" -> handleReset(player);
            default -> sendHelp(sender);
        }
    }

    private void handleNekoVM(ICommandSender sender, EntityPlayerMP player, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        switch (args[1]) {
            case "reload" -> handleNekoVMReload(player);
            case "list" -> handleNekoVMList(player);
            case "save" -> handleNekoVMSave(player);
            case "gui" -> handleNekoVMGui(player);
            default -> sendHelp(sender);
        }
    }

    private void handleNekoVMReload(EntityPlayerMP player) {
        boolean success = NekoTradeRegistry.reload();
        if (success) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "猫猫币交易配置已热重载"));
        } else {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "猫猫币交易配置热重载失败，请查看服务器日志"));
        }
    }

    private void handleNekoVMList(EntityPlayerMP player) {
        List<String> trades = NekoTradeRegistry.getTradeList();
        if (trades.isEmpty()) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "当前没有猫猫币交易"));
            return;
        }
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "当前猫猫币交易列表："));
        for (String trade : trades) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + trade));
        }
    }

    private void handleNekoVMSave(EntityPlayerMP player) {
        NekoTradeConfig.save(NekoTradeConfig.load());
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "猫猫币交易数据已保存到配置文件"));
    }

    private void handleNekoVMGui(EntityPlayerMP player) {
        // NekoTradeEditorGui 尚未实现，先显示提示信息
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "猫猫币交易编辑器 GUI 尚未实现，敬请期待！"));
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "目前可使用 /gtit nekovm list 查看交易列表"));
    }

    private void handleCertain(EntityPlayerMP player) {
        List<ItemEntry> entries = new ArrayList<>();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null) {
                String itemId = getItemId(stack);
                if (itemId != null) {
                    entries.add(new ItemEntry(itemId, stack.stackSize, stack.getItemDamage()));
                }
            }
        }
        GiftConfig.setGuaranteedItems(entries);
        GiftConfig.saveConfig();
        player.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GREEN + "必中物品已更新为当前背包内容（" + entries.size() + "项）"));
    }

    private void handleRandom(EntityPlayerMP player, String[] args) {
        int count = 2;
        if (args.length >= 3) {
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "随机数必须是整数"));
                return;
            }
        }

        List<ItemEntry> entries = new ArrayList<>();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null) {
                String itemId = getItemId(stack);
                if (itemId != null) {
                    entries.add(new ItemEntry(itemId, stack.stackSize, stack.getItemDamage()));
                }
            }
        }
        GiftConfig.setRandomItems(entries);
        GiftConfig.setRandomCount(count);
        GiftConfig.saveConfig();
        player.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GREEN + "随机物品已更新（" + entries.size() + "项，随机数：" + count + "）"));
    }

    private void handleReset(EntityPlayerMP player) {
        GiftConfig.resetToDefault();
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "新手宝箱配置已重置为默认"));
    }

    private void sendHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "用法:"));
        sender.addChatMessage(new ChatComponentText("/gtit gift certain - 设置必中物品为当前背包"));
        sender.addChatMessage(new ChatComponentText("/gtit gift random <count> - 设置随机物品为当前背包"));
        sender.addChatMessage(new ChatComponentText("/gtit gift reset - 重置为默认配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm reload - 热重载猫猫币交易配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm list - 列出当前所有猫猫币交易"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm save - 保存当前交易数据到配置文件"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm gui - 打开猫猫币交易编辑器 GUI（开发中）"));
    }

    private String getItemId(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        String gameId = GameRegistry.findUniqueIdentifierFor(stack.getItem())
            .toString();
        // 格式: "modid:name" 或 "modid:name@meta"
        if (gameId.contains("@")) {
            gameId = gameId.substring(0, gameId.indexOf('@'));
        }
        return gameId;
    }
}
