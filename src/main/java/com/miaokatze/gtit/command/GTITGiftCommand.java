package com.miaokatze.gtit.command;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtit.mail.Mail;
import com.miaokatze.gtit.mail.MailManager;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.signin.DailySignInConfig;
import com.miaokatze.gtit.signin.DailySignInData;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.signin.SignInClientData;
import com.miaokatze.gtit.signin.SignInNetworkManager;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.v2.NekoEditModeManager;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * /gtit 指令
 * - /gtit gift certain [yesNBT|noNBT]: 将当前背包物品设为必中物品
 * - /gtit gift random <count> [yesNBT|noNBT]: 将当前背包物品设为随机物品，设置随机数
 * - /gtit gift reset: 恢复默认配置
 * - /gtit gift claimreset [all|玩家名]: 重置新手礼包领取状态（支持控制台执行）
 * - /gtit nekovm edit <标签页> [顺序ID] [冷却时间] [绑定ID] [yesNBT|noNBT]: 导入交易条目
 * - /gtit nekovm list [标签页]: 列出交易条目
 * - /gtit nekovm edithelp: 显示编辑帮助
 * - /gtit nekovm delete <标签页> <顺序ID>: 删除交易条目
 * - /gtit nekovm reload: 热重载猫猫币交易配置
 * - /gtit nekovm save: 手动保存当前交易数据到配置文件
 * - /gtit nekovm timereset: 重置当前所有交易冷却
 * <p>
 * 配置类指令默认不记录 NBT；如需记录，请在指令末尾添加 {@code yesNBT}。
 */
public class GTITGiftCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtit";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtit gift certain [yesNBT|noNBT]|random <count> [yesNBT|noNBT]|reset|claimreset [all|玩家名] | /gtit nekovm edit|list|edithelp|delete|reload|save|timereset | /gtit signin [info|reload|admin|help]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP 权限
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // claimreset 等管理命令允许控制台执行；需要玩家身份的子命令在各自 handler 内检查
        return super.canCommandSenderUseCommand(sender);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return;
        }

        switch (args[0]) {
            case "gift" -> handleGift(sender, args);
            case "nekovm" -> {
                // nekovm 子命令需要玩家身份（操作背包）
                if (!(sender instanceof EntityPlayerMP player)) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "猫猫售货机命令只有玩家可以执行"));
                    return;
                }
                handleNekoVM(sender, player, args);
            }
            case "signin" -> handleSignIn(sender, args);
            case "mail" -> handleMail(sender, args);
            default -> sendHelp(sender);
        }
    }

    // ==================== Tab 补全 ====================

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "gift", "nekovm", "signin", "mail");
        }

        if (args.length == 2) {
            if ("gift".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, "certain", "random", "reset", "claimreset");
            }
            if ("nekovm".equals(args[0])) {
                return getListOfStringsMatchingLastWord(
                    args,
                    "edit",
                    "list",
                    "edithelp",
                    "delete",
                    "reload",
                    "save",
                    "timereset",
                    "page",
                    "pagehelp",
                    "help");
            }
            if ("signin".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, "info", "reload", "admin", "help");
            }
            if ("mail".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, "send", "first", "firstclear", "once", "help");
            }
        }

        if (args.length == 3 && "mail".equals(args[0]) && "send".equals(args[1])) {
            // /gtit mail send <玩家名>：补全在线玩家名
            List<String> names = new ArrayList<>();
            for (EntityPlayerMP player : MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                names.add(player.getCommandSenderName());
            }
            return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
        }

        if (args.length == 3 && "signin".equals(args[0])) {
            if ("admin".equals(args[1])) {
                return getListOfStringsMatchingLastWord(args, "set", "reset");
            }
            if ("info".equals(args[1])) {
                // 补全在线玩家名
                List<String> names = new ArrayList<>();
                for (EntityPlayerMP player : MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList) {
                    names.add(player.getCommandSenderName());
                }
                return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
            }
        }

        if (args.length == 4 && "signin".equals(args[0]) && "admin".equals(args[1])) {
            // /gtit signin admin set|reset <玩家名>
            List<String> names = new ArrayList<>();
            for (EntityPlayerMP player : MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                names.add(player.getCommandSenderName());
            }
            return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
        }

        if (args.length == 3 && "gift".equals(args[0])) {
            if ("certain".equals(args[1]) || "random".equals(args[1])) {
                // 补全 NBT 标记：/gtit gift certain [yesNBT|noNBT] 或 /gtit gift random <count> [yesNBT|noNBT]
                return getListOfStringsMatchingLastWord(args, "yesNBT", "noNBT");
            }
            if ("claimreset".equals(args[1])) {
                // 补全 "all" + 在线玩家名
                List<String> options = new ArrayList<>();
                options.add("all");
                for (EntityPlayerMP player : MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList) {
                    options.add(player.getCommandSenderName());
                }
                return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
            }
        }

        if (args.length == 3 && "nekovm".equals(args[0])) {
            if ("edit".equals(args[1]) || "list".equals(args[1]) || "delete".equals(args[1])) {
                // 补全标签页ID
                List<String> pageIds = new ArrayList<>();
                try {
                    for (int id : NekoPageRegistry.getPageIds()) {
                        pageIds.add(String.valueOf(id));
                    }
                } catch (Exception e) {
                    // NekoPageRegistry 尚未初始化
                }
                return getListOfStringsMatchingLastWord(args, pageIds.toArray(new String[0]));
            }
            if ("page".equals(args[1])) {
                return getListOfStringsMatchingLastWord(args, "add", "delet");
            }
        }

        if (args.length == 4 && "gift".equals(args[0]) && "random".equals(args[1])) {
            // /gtit gift random <count> [yesNBT|noNBT]
            return getListOfStringsMatchingLastWord(args, "yesNBT", "noNBT");
        }

        if (args.length == 4 && "nekovm".equals(args[0])) {
            if ("edit".equals(args[1]) || "delete".equals(args[1])) {
                // 补全顺序ID（基于已有交易）
                try {
                    int tabId = Integer.parseInt(args[2]);
                    if (NekoPageRegistry.hasPage(tabId)) {
                        NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
                        List<String> orderIds = new ArrayList<>();
                        for (NekoTradeEntry entry : data.getTrades()) {
                            if (entry.getTabId() == tabId) {
                                orderIds.add(String.valueOf(entry.getOrderId()));
                            }
                        }
                        return getListOfStringsMatchingLastWord(args, orderIds.toArray(new String[0]));
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
            if ("page".equals(args[1]) && "delet".equals(args[2])) {
                // 补全可删除的自定义标签页ID
                List<String> pageIds = new ArrayList<>();
                try {
                    for (com.miaokatze.gtit.trade.NekoPageEntry page : NekoPageRegistry.getAllPages()) {
                        if (!page.isDefault()) {
                            pageIds.add(String.valueOf(page.getId()));
                        }
                    }
                } catch (Exception e) {
                    // NekoPageRegistry 尚未初始化
                }
                return getListOfStringsMatchingLastWord(args, pageIds.toArray(new String[0]));
            }
        }

        if (args.length == 7 && "nekovm".equals(args[0]) && "edit".equals(args[1])) {
            // /gtit nekovm edit <tabId> <顺序ID> <冷却> <绑定ID> [yesNBT|noNBT]
            return getListOfStringsMatchingLastWord(args, "yesNBT", "noNBT");
        }

        return null;
    }

    // ==================== NBT 标记解析 ====================

    /**
     * 从指令参数末尾解析 yesNBT/noNBT 标记。
     * <p>
     * 若最后一个参数为 {@code yesNBT} 或 {@code noNBT}，则返回对应布尔值；否则默认返回 {@code false}（不记录 NBT），
     * 并向玩家发送提示与完整指令结构。
     *
     * @param args           当前指令参数数组
     * @param player         执行指令的玩家
     * @param commandExample 完整指令结构示例，用于提示
     * @return true 表示记录 NBT，false 表示不记录
     */
    private boolean parseNbtFlagAtEnd(String[] args, EntityPlayerMP player, String commandExample) {
        if (args.length > 0) {
            String last = args[args.length - 1].toLowerCase();
            if ("yesnbt".equals(last)) {
                return true;
            }
            if ("nonbt".equals(last)) {
                return false;
            }
        }
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "提示：当前默认不记录 NBT。"));
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "如需记录 NBT，请在指令末尾添加 yesNBT"));
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "完整指令结构：" + commandExample));
        return false;
    }

    // ==================== Gift 子命令 ====================

    private void handleGift(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        switch (args[1]) {
            case "certain" -> {
                if (!(sender instanceof EntityPlayerMP player)) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "此命令只有玩家可以执行"));
                    return;
                }
                handleCertain(player, args);
            }
            case "random" -> {
                if (!(sender instanceof EntityPlayerMP player)) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "此命令只有玩家可以执行"));
                    return;
                }
                handleRandom(player, args);
            }
            case "reset" -> {
                if (!(sender instanceof EntityPlayerMP player)) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "此命令只有玩家可以执行"));
                    return;
                }
                handleReset(player);
            }
            case "claimreset" -> handleClaimReset(sender, args);
            default -> sendHelp(sender);
        }
    }

    private void handleCertain(EntityPlayerMP player, String[] args) {
        boolean recordNbt = parseNbtFlagAtEnd(args, player, "/gtit gift certain [yesNBT|noNBT]");

        List<com.miaokatze.gtit.config.GiftConfig.ItemEntry> entries = new ArrayList<>();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null) {
                String itemId = getItemId(stack);
                if (itemId != null) {
                    NBTTagCompound nbt = recordNbt ? stack.getTagCompound() : null;
                    entries.add(
                        new com.miaokatze.gtit.config.GiftConfig.ItemEntry(
                            itemId,
                            stack.stackSize,
                            stack.getItemDamage(),
                            nbt));
                }
            }
        }
        com.miaokatze.gtit.config.GiftConfig.setGuaranteedItems(entries);
        com.miaokatze.gtit.config.GiftConfig.saveConfig();
        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "必中物品已更新为当前背包内容（"
                    + entries.size()
                    + "项，NBT："
                    + (recordNbt ? "开启" : "关闭")
                    + "）"));
    }

    private void handleRandom(EntityPlayerMP player, String[] args) {
        int count = 2;
        boolean recordNbt = false;

        if (args.length >= 3) {
            String arg2Lower = args[2].toLowerCase();
            if ("yesnbt".equals(arg2Lower) || "nonbt".equals(arg2Lower)) {
                // 省略了随机数，使用默认值 2，并在末尾解析 NBT 标记
                recordNbt = "yesnbt".equals(arg2Lower);
                if (!recordNbt) {
                    player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "提示：当前默认不记录 NBT。"));
                    player
                        .addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "如需记录 NBT，请在指令末尾添加 yesNBT"));
                    player.addChatMessage(
                        new ChatComponentText(
                            EnumChatFormatting.WHITE + "完整指令结构：/gtit gift random <count> [yesNBT|noNBT]"));
                }
            } else {
                try {
                    count = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "随机数必须是整数"));
                    return;
                }
                recordNbt = parseNbtFlagAtEnd(args, player, "/gtit gift random <count> [yesNBT|noNBT]");
            }
        } else {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "提示：当前默认不记录 NBT。"));
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "如需记录 NBT，请在指令末尾添加 yesNBT"));
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.WHITE + "完整指令结构：/gtit gift random <count> [yesNBT|noNBT]"));
        }

        List<com.miaokatze.gtit.config.GiftConfig.ItemEntry> entries = new ArrayList<>();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null) {
                String itemId = getItemId(stack);
                if (itemId != null) {
                    NBTTagCompound nbt = recordNbt ? stack.getTagCompound() : null;
                    entries.add(
                        new com.miaokatze.gtit.config.GiftConfig.ItemEntry(
                            itemId,
                            stack.stackSize,
                            stack.getItemDamage(),
                            nbt));
                }
            }
        }
        com.miaokatze.gtit.config.GiftConfig.setRandomItems(entries);
        com.miaokatze.gtit.config.GiftConfig.setRandomCount(count);
        com.miaokatze.gtit.config.GiftConfig.saveConfig();
        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "随机物品已更新（"
                    + entries.size()
                    + "项，随机数："
                    + count
                    + "，NBT："
                    + (recordNbt ? "开启" : "关闭")
                    + "）"));
    }

    private void handleReset(EntityPlayerMP player) {
        com.miaokatze.gtit.config.GiftConfig.resetToDefault();
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "新手宝箱配置已重置为默认"));
    }

    // ==================== Gift ClaimReset 子命令 ====================

    /** NBT 键名：玩家已领取新手礼包的标记 */
    private static final String GIFT_CLAIMED_KEY = "gtit_received_starter_gift";

    /**
     * /gtit gift claimreset [all|玩家名]
     * 重置新手礼包的领取状态，支持控制台执行。
     * 重置后玩家下次登录时自动收到新手宝箱。
     */
    private void handleClaimReset(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            sender
                .addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit gift claimreset <all|玩家名>"));
            return;
        }

        String target = args[2];
        if ("all".equalsIgnoreCase(target)) {
            handleClaimResetAll(sender);
        } else {
            handleClaimResetPlayer(sender, target);
        }
    }

    /**
     * 重置指定玩家的领取状态
     * 优先在线处理（直接修改内存NBT），离线则操作 .dat 文件
     */
    private void handleClaimResetPlayer(ICommandSender sender, String playerName) {
        EntityPlayerMP onlinePlayer = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(playerName);

        if (onlinePlayer != null) {
            // 在线玩家：直接修改内存 NBT
            boolean hadFlag = resetOnlinePlayerGiftFlag(onlinePlayer);
            if (hadFlag) {
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.GREEN + "已重置玩家 " + playerName + " 的新手礼包领取状态"));
            } else {
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.YELLOW + "玩家 " + playerName + " 尚未领取新手礼包，无需重置"));
            }
        } else {
            // 离线玩家：操作 .dat 文件
            boolean success = resetOfflinePlayerGiftFlagByName(playerName);
            if (success) {
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.GREEN + "已重置离线玩家 " + playerName + " 的新手礼包领取状态"));
            } else {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "找不到玩家 " + playerName + " 的数据"));
                return;
            }
        }
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "更新完毕，该玩家下次登录时自动发放新手宝箱"));
    }

    /**
     * 重置所有玩家的领取状态
     * 先处理在线玩家（内存NBT），再处理离线玩家（.dat文件）
     */
    private void handleClaimResetAll(ICommandSender sender) {
        int onlineReset = 0;

        // 处理所有在线玩家
        for (EntityPlayerMP player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (resetOnlinePlayerGiftFlag(player)) {
                onlineReset++;
            }
        }

        // 处理所有离线玩家（扫描 playerdata 目录）
        int offlineReset = resetAllOfflinePlayerGiftFlags();

        int total = onlineReset + offlineReset;
        if (total > 0) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "已重置所有玩家的新手礼包领取状态"));
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.WHITE + "  在线: " + onlineReset + "  离线: " + offlineReset));
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "更新完毕，相关玩家下次登录时自动发放新手宝箱"));
        } else {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "没有需要重置的玩家"));
        }
    }

    /**
     * 重置在线玩家的礼包领取标记
     * 
     * @return true 如果标记存在且被移除；false 如果标记不存在
     */
    private boolean resetOnlinePlayerGiftFlag(EntityPlayerMP player) {
        NBTTagCompound playerData = player.getEntityData();
        NBTTagCompound persisted = playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.hasKey(GIFT_CLAIMED_KEY)) {
            persisted.removeTag(GIFT_CLAIMED_KEY);
            playerData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
            return true;
        }
        return false;
    }

    /**
     * 重置单个 .dat 文件中的礼包领取标记
     * 
     * @return true 成功重置；false 标记不存在或操作失败
     */
    private boolean resetOfflinePlayerGiftFlag(File datFile) {
        try {
            // 安全检查：操作 .dat 前再次确认玩家不在线，避免内存/文件数据冲突。
            // 外层批量方法虽收集过 onlineUuids，但收集与写入之间存在时间窗口；
            // 按名查找路径的二次确认也在更早时刻，无法覆盖真正落盘前的那一刻。
            UUID fileUuid;
            try {
                fileUuid = UUID.fromString(
                    datFile.getName()
                        .replace(".dat", ""));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            if (isPlayerOnline(fileUuid)) {
                GTInterestingThing.LOG.warn("跳过在线玩家的 .dat 文件操作，避免内存/文件数据冲突: {}", datFile.getName());
                return false;
            }

            NBTTagCompound rootNbt = CompressedStreamTools.read(datFile);
            if (rootNbt == null) return false;

            if (rootNbt.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
                NBTTagCompound persisted = rootNbt.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
                if (persisted.hasKey(GIFT_CLAIMED_KEY)) {
                    persisted.removeTag(GIFT_CLAIMED_KEY);
                    rootNbt.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
                    CompressedStreamTools.safeWrite(rootNbt, datFile);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("重置离线玩家礼包标记失败: " + datFile.getName(), e);
            return false;
        }
    }

    /**
     * 检查指定 UUID 的玩家当前是否在线
     */
    private boolean isPlayerOnline(UUID uuid) {
        if (uuid == null) return false;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return false;
        for (EntityPlayerMP onlinePlayer : server.getConfigurationManager().playerEntityList) {
            if (uuid.equals(onlinePlayer.getUniqueID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通过玩家名重置离线玩家的礼包领取标记
     * 使用 usercache.json 查找 UUID，回退到扫描 .dat 文件
     * 
     * @return true 成功重置；false 找不到玩家或操作失败
     */
    private boolean resetOfflinePlayerGiftFlagByName(String playerName) {
        File worldDir = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getWorldDirectory();
        File playerdataDir = new File(worldDir, "playerdata");
        if (!playerdataDir.exists() || !playerdataDir.isDirectory()) {
            return false;
        }

        // 二次确认该玩家不在线
        EntityPlayerMP onlinePlayer = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(playerName);
        if (onlinePlayer != null) {
            return resetOnlinePlayerGiftFlag(onlinePlayer);
        }

        // 尝试通过 usercache.json 查找 UUID
        File userCache = new File(worldDir.getParentFile(), "usercache.json");
        UUID targetUuid = findUuidFromUserCache(playerName, userCache);
        if (targetUuid != null) {
            File datFile = new File(playerdataDir, targetUuid.toString() + ".dat");
            if (datFile.exists()) {
                return resetOfflinePlayerGiftFlag(datFile);
            }
        }

        // usercache 中找不到，扫描所有 .dat 文件（通过 usercache 反查 UUID 对应的玩家名）
        File[] datFiles = playerdataDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (datFiles == null) return false;

        for (File datFile : datFiles) {
            try {
                String uuidStr = datFile.getName()
                    .replace(".dat", "");
                UUID fileUuid = UUID.fromString(uuidStr);
                String cachedName = findNameFromUserCache(fileUuid, userCache);
                if (playerName.equalsIgnoreCase(cachedName)) {
                    return resetOfflinePlayerGiftFlag(datFile);
                }
            } catch (IllegalArgumentException ignored) {
                // 文件名不是合法 UUID，跳过
            }
        }

        return false;
    }

    /**
     * 重置所有离线玩家的礼包领取标记
     * 扫描 playerdata 目录，跳过当前在线的玩家，避免内存/文件数据冲突
     * 
     * @return 成功重置的离线玩家数量
     */
    private int resetAllOfflinePlayerGiftFlags() {
        File worldDir = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getWorldDirectory();
        File playerdataDir = new File(worldDir, "playerdata");
        if (!playerdataDir.exists() || !playerdataDir.isDirectory()) {
            return 0;
        }

        // 收集在线玩家的 UUID，跳过在线玩家的 .dat 文件
        Set<UUID> onlineUuids = new HashSet<>();
        for (EntityPlayerMP onlinePlayer : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            onlineUuids.add(onlinePlayer.getUniqueID());
        }

        int count = 0;
        File[] datFiles = playerdataDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (datFiles == null) return 0;

        for (File datFile : datFiles) {
            try {
                String uuidStr = datFile.getName()
                    .replace(".dat", "");
                UUID fileUuid = UUID.fromString(uuidStr);
                // 跳过在线玩家
                if (onlineUuids.contains(fileUuid)) continue;
                if (resetOfflinePlayerGiftFlag(datFile)) {
                    count++;
                }
            } catch (IllegalArgumentException ignored) {
                // 文件名不是合法 UUID，跳过
            }
        }
        return count;
    }

    /**
     * 从 usercache.json 中查找玩家名对应的 UUID
     * usercache.json 格式: [{"name":"xxx","uuid":"xxx","expiresOn":"xxx"}, ...]
     */
    private UUID findUuidFromUserCache(String playerName, File userCacheFile) {
        if (!userCacheFile.exists()) return null;
        try {
            String content = new String(Files.readAllBytes(userCacheFile.toPath()), "UTF-8");
            return parseUuidFromUserCacheJson(content, playerName);
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("读取 usercache.json 失败", e);
            return null;
        }
    }

    /**
     * 从 usercache.json 中查找 UUID 对应的玩家名
     */
    private String findNameFromUserCache(UUID uuid, File userCacheFile) {
        if (!userCacheFile.exists()) return null;
        try {
            String content = new String(Files.readAllBytes(userCacheFile.toPath()), "UTF-8");
            return parseNameFromUserCacheJson(content, uuid.toString());
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("读取 usercache.json 失败", e);
            return null;
        }
    }

    /**
     * 从 usercache.json 内容中解析玩家名对应的 UUID
     * 简易 JSON 解析，不依赖 Gson
     */
    private UUID parseUuidFromUserCacheJson(String json, String playerName) {
        String lowerName = playerName.toLowerCase();
        // 查找 "name":"<playerName>" 条目
        String namePattern = "\"name\"";
        int idx = 0;
        while ((idx = json.indexOf(namePattern, idx)) >= 0) {
            // 找到 name 键，提取其值
            int colonPos = json.indexOf(':', idx + namePattern.length());
            if (colonPos < 0) break;
            int valueStart = json.indexOf('"', colonPos + 1);
            if (valueStart < 0) break;
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) break;
            String name = json.substring(valueStart + 1, valueEnd);

            if (name.equalsIgnoreCase(lowerName)) {
                // 找到匹配的玩家名，在同一对象中查找 uuid
                String uuidStr = extractUuidFromObject(json, idx);
                if (uuidStr != null) {
                    try {
                        return UUID.fromString(uuidStr);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            idx = valueEnd + 1;
        }
        return null;
    }

    /**
     * 从 usercache.json 内容中解析 UUID 对应的玩家名
     */
    private String parseNameFromUserCacheJson(String json, String uuidStr) {
        // 查找 "uuid":"<uuidStr>" 条目
        String uuidPattern = "\"uuid\"";
        int idx = 0;
        while ((idx = json.indexOf(uuidPattern, idx)) >= 0) {
            int colonPos = json.indexOf(':', idx + uuidPattern.length());
            if (colonPos < 0) break;
            int valueStart = json.indexOf('"', colonPos + 1);
            if (valueStart < 0) break;
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) break;
            String foundUuid = json.substring(valueStart + 1, valueEnd);

            if (foundUuid.equalsIgnoreCase(uuidStr)) {
                // 在同一对象中查找 name
                String name = extractNameFromObject(json, idx);
                if (name != null) return name;
            }
            idx = valueEnd + 1;
        }
        return null;
    }

    /**
     * 从 JSON 对象中提取 uuid 值（向前和向后搜索同一 {} 块内的键）
     */
    private String extractUuidFromObject(String json, int startIdx) {
        // 找到包含 startIdx 的 {} 块
        int objStart = json.lastIndexOf('{', startIdx);
        int objEnd = json.indexOf('}', startIdx);
        if (objStart < 0 || objEnd < 0) return null;

        String obj = json.substring(objStart, objEnd + 1);
        return extractJsonValue(obj, "uuid");
    }

    /**
     * 从 JSON 对象中提取 name 值
     */
    private String extractNameFromObject(String json, int startIdx) {
        int objStart = json.lastIndexOf('{', startIdx);
        int objEnd = json.indexOf('}', startIdx);
        if (objStart < 0 || objEnd < 0) return null;

        String obj = json.substring(objStart, objEnd + 1);
        return extractJsonValue(obj, "name");
    }

    /**
     * 从 JSON 字符串中提取指定键的值
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonPos = json.indexOf(':', idx + pattern.length());
        if (colonPos < 0) return null;
        int valueStart = json.indexOf('"', colonPos + 1);
        if (valueStart < 0) return null;
        int valueEnd = json.indexOf('"', valueStart + 1);
        if (valueEnd < 0) return null;
        return json.substring(valueStart + 1, valueEnd);
    }

    // ==================== NekoVM 子命令 ====================

    private void handleNekoVM(ICommandSender sender, EntityPlayerMP player, String[] args) {
        if (args.length < 2) {
            sendNekoVMHelp(sender);
            return;
        }

        switch (args[1]) {
            case "edit" -> handleNekoVMEdit(player, args);
            case "list" -> handleNekoVMList(player, args);
            case "edithelp" -> handleNekoVMEditHelp(sender);
            case "delete" -> handleNekoVMDelete(player, args);
            case "reload" -> handleNekoVMReload(player);
            case "save" -> handleNekoVMSave(player);
            case "timereset" -> handleNekoVMTimeReset(player);
            case "page" -> handleNekoVMPage(player, args);
            case "pagehelp" -> handleNekoVMPageHelp(sender);
            case "help" -> handleNekoVMFullHelp(sender);
            default -> sendNekoVMHelp(sender);
        }
    }

    /**
     * /gtit nekovm edit <标签页> [顺序ID] [冷却时间] [绑定ID] [yesNBT|noNBT]
     * /gtit nekovm edit on|off
     * <p>
     * 读取玩家背包前两行(需求)和快捷栏前 10 格(产物)，导入交易条目。
     * 默认不记录 NBT；如需记录，请在指令末尾添加 yesNBT。
     * <p>
     * on|off 子命令：开启/关闭可视化配置编辑模式（OP 权限）。
     */
    private void handleNekoVMEdit(EntityPlayerMP player, String[] args) {
        if (args.length < 3) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "用法: /gtit nekovm edit <标签页ID> [顺序ID] [冷却时间] [绑定ID] [yesNBT|noNBT]"));
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "或: /gtit nekovm edit on|off （开关可视化编辑模式）"));
            return;
        }

        // --- 编辑模式开关：/gtit nekovm edit on|off ---
        if (args.length == 3 && ("on".equalsIgnoreCase(args[2]) || "off".equalsIgnoreCase(args[2]))) {
            handleNekoVMEditModeToggle(player, "on".equalsIgnoreCase(args[2]));
            return;
        }

        // 从末尾解析 yesNBT/noNBT 标记
        int effectiveLen = args.length;
        boolean recordNbt = false;
        String lastArg = args[args.length - 1].toLowerCase();
        if ("yesnbt".equals(lastArg) || "nonbt".equals(lastArg)) {
            recordNbt = "yesnbt".equals(lastArg);
            effectiveLen--;
        } else {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "提示：当前默认不记录 NBT。"));
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "如需记录 NBT，请在指令末尾添加 yesNBT"));
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.WHITE + "完整指令结构：/gtit nekovm edit <标签页ID> [顺序ID] [冷却时间] [绑定ID] [yesNBT|noNBT]"));
        }

        // 解析标签页ID
        int tabId;
        try {
            tabId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是正整数"));
            return;
        }
        if (!NekoPageRegistry.hasPage(tabId)) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "标签页 #" + tabId + " 不存在！使用 /gtit nekovm page add 添加"));
            return;
        }

        // 解析顺序ID（可选，默认自动）
        int orderId = -1;
        if (effectiveLen >= 4) {
            try {
                orderId = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "顺序ID必须是整数"));
                return;
            }
        }

        // 解析冷却时间（可选，默认0）
        int cooldown = 0;
        if (effectiveLen >= 5) {
            try {
                cooldown = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "冷却时间必须是整数（秒）"));
                return;
            }
        }

        // 解析绑定ID（可选，默认空）
        String bqQuestId = "";
        if (effectiveLen >= 6) {
            bqQuestId = args[5];
        }

        // 读取需求物品：背包前两行（slot 9-26，即 mainInventory[9] 到 mainInventory[26]）
        // MC 的 mainInventory 布局：0-8=快捷栏，9-35=背包主体
        // 自动识别猫猫币/闪烁猫猫币，转化为 currency 参数而非 fromItems
        List<NekoTradeEntry.ItemEntry> fromItems = new ArrayList<>();
        NekoTradeEntry.NekoCurrencyCost detectedCurrency = null;

        for (int i = 9; i <= 26; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() != null) {
                // 检查是否为猫猫币/闪烁猫猫币
                String nekoCurrencyId = NekoCurrencyRegistrar.getNekoCurrencyId(stack);
                if (nekoCurrencyId != null) {
                    // 猫猫币：转化为 currency 参数
                    if (detectedCurrency == null) {
                        detectedCurrency = new NekoTradeEntry.NekoCurrencyCost(nekoCurrencyId, stack.stackSize);
                    } else if (detectedCurrency.getType()
                        .equals(nekoCurrencyId)) {
                            // 同种猫猫币，累加数量
                            detectedCurrency.setAmount(detectedCurrency.getAmount() + stack.stackSize);
                        } else {
                            // 不同种猫猫币（猫猫币+闪烁猫猫币），不允许
                            player.addChatMessage(
                                new ChatComponentText(EnumChatFormatting.RED + "不支持同时使用猫猫币和闪烁猫猫币！请只放一种"));
                            return;
                        }
                } else {
                    // 普通物品：放入 fromItems（根据 recordNbt 决定是否记录 NBT）
                    fromItems.add(NekoTradeEntry.ItemEntry.fromItemStack(stack, recordNbt));
                }
            }
        }

        // 读取产物物品：快捷栏前 10 格（slot 0-9）
        List<NekoTradeEntry.ItemEntry> toItems = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() != null) {
                toItems.add(NekoTradeEntry.ItemEntry.fromItemStack(stack, recordNbt));
            }
        }

        // 合并同类物品（NBT 不同则不合并，防止 NBT 丢失）
        fromItems = mergeItems(fromItems);
        toItems = mergeItems(toItems);

        if (toItems.isEmpty()) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "产物物品为空！请将产物放在快捷栏前 10 格（slot 0-9）"));
            return;
        }

        // 加载当前配置
        NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
        List<NekoTradeEntry> trades = data.getTrades();

        // 如果没有指定顺序ID，自动分配
        if (orderId < 0) {
            orderId = getNextOrderId(trades, tabId);
        }

        // 根据自动检测设置猫猫币信息（不再根据标签页自动添加猫猫币）
        NekoTradeEntry.NekoCurrencyCost currency = detectedCurrency;
        String tabName = getTabName(tabId);

        // 验证：猫猫币交易的标签页提示（完全解耦，不再强制修正）
        if (currency != null) {
            boolean isNeko = "neko".equals(currency.getType());
            boolean isShimmering = "shimmeringNeko".equals(currency.getType());
            if (tabId == 3 && (isNeko || isShimmering)) {
                // "GTIT"标签页放猫猫币交易，仅提示
                player.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.YELLOW + "提示：猫猫币交易通常放在标签页1，闪烁猫猫币交易通常放在标签页2"));
            }
            if (tabId == 1 && isShimmering) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "提示：闪烁猫猫币交易通常放在标签页2"));
            } else if (tabId == 2 && isNeko) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "提示：猫猫币交易通常放在标签页1"));
            }
        }

        // 查找是否已存在相同 tabId+orderId 的条目
        NekoTradeEntry existing = findEntry(trades, tabId, orderId);
        if (existing != null) {
            // 覆盖已有条目
            existing.setCurrency(currency);
            existing.setFromItems(fromItems);
            existing.setToItems(toItems);
            existing.setCooldown(cooldown);
            existing.setBqQuestId(bqQuestId);
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.YELLOW + "已覆盖 "
                        + tabName
                        + " 标签页的条目 #"
                        + orderId
                        + "（NBT："
                        + (recordNbt ? "开启" : "关闭")
                        + "）"));
        } else {
            // 新建条目
            NekoTradeEntry entry = new NekoTradeEntry();
            entry.setTabId(tabId);
            entry.setOrderId(orderId);
            entry.setCurrency(currency);
            entry.setFromItems(fromItems);
            entry.setToItems(toItems);
            entry.setCooldown(cooldown);
            entry.setBqQuestId(bqQuestId);
            trades.add(entry);
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "已添加 "
                        + tabName
                        + " 标签页的条目 #"
                        + orderId
                        + "（NBT："
                        + (recordNbt ? "开启" : "关闭")
                        + "）"));
        }

        // 保存并重载
        // V1 的 NekoTradeRegistry.reload() 已移除，改为调用 V2 的 reload
        // V2 从 NekoTradeConfig 加载配置并注册到 NekoTradeDatabase（V2 独立数据库）
        NekoTradeConfig.save(data);
        NekoTradeRegistryV2.reload();

        // 显示条目摘要
        String fromDesc = describeItems(fromItems);
        if (currency != null) {
            String coinName = tabId == 1 ? "猫猫币" : "闪烁猫猫币";
            String coinDesc = coinName + "x" + currency.getAmount();
            fromDesc = fromDesc.equals("无") ? coinDesc : coinDesc + "+" + fromDesc;
        }
        String toDesc = describeItems(toItems);
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  " + fromDesc + " → " + toDesc));
        if (cooldown > 0) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  冷却: " + cooldown + "s"));
        }
        if (!bqQuestId.isEmpty()) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  绑定: " + bqQuestId));
        }

        // 显示当前标签页最后5条交易
        showRecentTrades(player, trades, tabId);
    }

    /**
     * 显示当前标签页最后5条交易
     */
    private void showRecentTrades(EntityPlayerMP player, List<NekoTradeEntry> trades, int tabId) {
        String tabName = getTabName(tabId);
        List<NekoTradeEntry> tabTrades = trades.stream()
            .filter(t -> t.getTabId() == tabId)
            .sorted((a, b) -> Integer.compare(a.getOrderId(), b.getOrderId()))
            .collect(Collectors.toList());

        if (tabTrades.isEmpty()) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + tabName + " 标签页暂无交易"));
            return;
        }

        int start = Math.max(0, tabTrades.size() - 5);
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "--- " + tabName + " 最近交易 ---"));
        for (int i = start; i < tabTrades.size(); i++) {
            NekoTradeEntry entry = tabTrades.get(i);
            String fromDesc = describeItems(entry.getFromItems());
            if (entry.getCurrency() != null) {
                String coinName = entry.getCurrency()
                    .getType()
                    .equals("neko") ? "猫猫币" : "闪烁猫猫币";
                String coinDesc = coinName + "x"
                    + entry.getCurrency()
                        .getAmount();
                fromDesc = fromDesc.equals("无") ? coinDesc : coinDesc + "+" + fromDesc;
            }
            String toDesc = describeItems(entry.getToItems());
            String line = EnumChatFormatting.WHITE + "(#" + entry.getOrderId() + ") " + fromDesc + " → " + toDesc;
            player.addChatMessage(new ChatComponentText(line));
        }
    }

    /**
     * /gtit nekovm edit on|off
     * <p>
     * 开启/关闭可视化配置编辑模式。
     * 编辑模式下打开猫猫售货机 GUI 可编辑交易条目、签到奖励、抽奖配置。
     * 仅 OP 可执行（指令本身已要求 OP 权限）。
     *
     * @param player 目标玩家
     * @param enable true=开启编辑模式，false=关闭
     */
    private void handleNekoVMEditModeToggle(EntityPlayerMP player, boolean enable) {
        UUID playerId = player.getUniqueID();
        if (enable) {
            NekoEditModeManager.INSTANCE.enterEditMode(playerId);
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[编辑模式] 已开启可视化配置编辑模式"));
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GRAY + "打开猫猫售货机 GUI，左键点击交易条目进行编辑"));
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GRAY + "使用 /gtit nekovm edit off 退出编辑模式"));
        } else {
            NekoEditModeManager.INSTANCE.exitEditMode(playerId);
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.YELLOW + "[编辑模式] 已关闭可视化配置编辑模式"));
        }
        GTInterestingThing.LOG.info(
            "[NekoEdit] 玩家 {} {}编辑模式",
            player.getCommandSenderName(),
            enable ? "进入" : "退出");
    }

    /**
     * /gtit nekovm list [标签页]
     */
    private void handleNekoVMList(EntityPlayerMP player, String[] args) {
        NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
        List<NekoTradeEntry> trades = data.getTrades();

        // 过滤标签页
        int filterTabId = -1;
        if (args.length >= 3) {
            try {
                filterTabId = Integer.parseInt(args[2]);
                if (!NekoPageRegistry.hasPage(filterTabId)) {
                    player
                        .addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页 #" + filterTabId + " 不存在"));
                    return;
                }
            } catch (NumberFormatException e) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是整数"));
                return;
            }
        }

        // 按标签页分组显示
        for (int tabId : NekoPageRegistry.getPageIds()) {
            if (filterTabId > 0 && filterTabId != tabId) continue;

            String tabName = NekoPageRegistry.getPageName(tabId);
            List<NekoTradeEntry> tabTrades = trades.stream()
                .filter(t -> t.getTabId() == tabId)
                .sorted((a, b) -> Integer.compare(a.getOrderId(), b.getOrderId()))
                .collect(Collectors.toList());

            if (tabTrades.isEmpty() && filterTabId < 1) continue;

            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GOLD + "=== #" + tabId + " " + tabName + " ==="));
            if (tabTrades.isEmpty()) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  (空)"));
                continue;
            }

            for (NekoTradeEntry entry : tabTrades) {
                String fromDesc = describeItems(entry.getFromItems());
                if (entry.getCurrency() != null) {
                    String coinName = entry.getCurrency()
                        .getType()
                        .equals("neko") ? "猫猫币" : "闪烁猫猫币";
                    String coinDesc = coinName + "x"
                        + entry.getCurrency()
                            .getAmount();
                    fromDesc = fromDesc.equals("无") ? coinDesc : coinDesc + "+" + fromDesc;
                }
                String toDesc = describeItems(entry.getToItems());
                String line = EnumChatFormatting.WHITE + "(#" + entry.getOrderId() + ") " + fromDesc + " → " + toDesc;
                if (entry.getCooldown() > 0) {
                    line += EnumChatFormatting.GRAY + " 冷却:" + entry.getCooldown() + "s";
                }
                if (entry.getBqQuestId() != null && !entry.getBqQuestId()
                    .isEmpty()) {
                    line += EnumChatFormatting.AQUA + " 绑定:" + entry.getBqQuestId();
                }
                player.addChatMessage(new ChatComponentText(line));
            }
        }
    }

    /**
     * /gtit nekovm edithelp
     */
    private void handleNekoVMEditHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "=== 猫猫售货机交易编辑帮助 ==="));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + "/gtit nekovm edit <标签页ID> [顺序ID] [冷却] [绑定ID] [yesNBT|noNBT]"));
        sender
            .addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  标签页ID: 使用 /gtit nekovm list 查看已有标签页"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  顺序ID: 排序用，不写则自动分配"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  冷却: 交易冷却秒数，不写则0"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  绑定ID: BQ任务ID，不写则不绑定"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  yesNBT/noNBT: 是否记录物品 NBT，不写则默认不记录"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "物品读取规则:"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  需求物品 = 背包前两行（共18格）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  产物物品 = 快捷栏前 10 格（slot 0-9）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  同种物品自动合并数量（NBT 不同不合并）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "  猫猫币/闪烁猫猫币自动识别为货币参数！"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "  放入背包的猫猫币数量即为花费数量"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "覆盖规则:"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  标签页ID+顺序ID = 条目唯一身份"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  输入已存在的组合会覆盖原条目"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm list [标签页] - 列出交易"));
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm delete <标签页> <顺序ID> - 删除交易"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm reload - 热重载配置"));
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.YELLOW + "示例: /gtit nekovm edit 1 5 60 quest_001 yesNBT"));
    }

    /**
     * /gtit nekovm delete <标签页> <顺序ID>
     */
    private void handleNekoVMDelete(EntityPlayerMP player, String[] args) {
        if (args.length < 4) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit nekovm delete <标签页ID> <顺序ID>"));
            return;
        }

        int tabId;
        try {
            tabId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是正整数"));
            return;
        }

        int orderId;
        try {
            orderId = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "顺序ID必须是整数"));
            return;
        }

        NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
        List<NekoTradeEntry> trades = data.getTrades();
        NekoTradeEntry existing = findEntry(trades, tabId, orderId);

        if (existing == null) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "找不到 " + NekoPageRegistry.getPageName(tabId) + " 标签页的条目 #" + orderId));
            return;
        }

        trades.remove(existing);
        NekoTradeConfig.save(data);
        // V1 的 NekoTradeRegistry.reload() 已移除，改为调用 V2 的 reload
        NekoTradeRegistryV2.reload();

        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "已删除 " + NekoPageRegistry.getPageName(tabId) + " 标签页的条目 #" + orderId));
    }

    private void handleNekoVMReload(EntityPlayerMP player) {
        // V1 的 NekoTradeRegistry.reload() 已移除（V1 反射注入 VM TradeDatabase 的逻辑不再需要）
        // 仅保留 V2 的热重载：重载标签页配置、清空 BQ 触发器、重新加载交易数据
        boolean success = NekoTradeRegistryV2.reload();
        if (success) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "猫猫币交易配置已热重载"));
        } else {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "猫猫币交易配置热重载失败，请查看服务器日志"));
        }
    }

    private void handleNekoVMSave(EntityPlayerMP player) {
        NekoTradeConfig.save(NekoTradeConfig.load());
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "猫猫币交易数据已保存到配置文件"));
    }

    /**
     * /gtit nekovm timereset
     * <p>
     * 重置当前玩家（团队）的所有交易冷却，使所有交易立即可用。
     * V1 版本通过操作 VM 的 TradeManager.setTradeState 实现，已移除。
     * V2 版本通过 NekoHistoryManager.resetAllHistory 重置玩家在所有交易组的历史记录实现。
     */
    private void handleNekoVMTimeReset(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        // V2: 重置该玩家在所有交易组的交易历史（冷却）
        // resetAllHistory 内部会遍历玩家名下所有交易组历史并逐一 reset，同时标记脏数据持久化
        NekoHistoryManager.INSTANCE.resetAllHistory(playerId);
        // 统计当前 V2 数据库中注册的交易组数量作为重置计数
        int resetCount = NekoTradeDatabase.INSTANCE.getAllTradeGroupIds()
            .size();

        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "已重置 " + resetCount + " 个交易的冷却"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 合并同类物品（相同 item+meta+NBT 的合并数量）
     * <p>
     * 合并键包含 NBT 的 Base64 表示，避免相同 ID/meta 但不同 NBT 的物品被错误合并成一条并丢失 NBT。
     * 无 NBT 时使用空字符串占位。
     */
    private List<NekoTradeEntry.ItemEntry> mergeItems(List<NekoTradeEntry.ItemEntry> items) {
        Map<String, NekoTradeEntry.ItemEntry> merged = new HashMap<>();
        for (NekoTradeEntry.ItemEntry entry : items) {
            String nbtKey = entry.getNbtBase64() != null ? entry.getNbtBase64() : "";
            String key = entry.getItem() + ":" + entry.getMeta() + ":" + nbtKey;
            if (merged.containsKey(key)) {
                NekoTradeEntry.ItemEntry existing = merged.get(key);
                existing.setAmount(existing.getAmount() + entry.getAmount());
            } else {
                merged.put(key, entry);
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 获取下一个可用的顺序ID
     */
    private int getNextOrderId(List<NekoTradeEntry> trades, int tabId) {
        int maxOrderId = 0;
        for (NekoTradeEntry entry : trades) {
            if (entry.getTabId() == tabId && entry.getOrderId() > maxOrderId) {
                maxOrderId = entry.getOrderId();
            }
        }
        return maxOrderId + 1;
    }

    /**
     * 查找指定 tabId+orderId 的条目
     */
    private NekoTradeEntry findEntry(List<NekoTradeEntry> trades, int tabId, int orderId) {
        for (NekoTradeEntry entry : trades) {
            if (entry.getTabId() == tabId && entry.getOrderId() == orderId) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 获取标签页名称
     */
    private String getTabName(int tabId) {
        return NekoPageRegistry.getPageName(tabId);
    }

    /**
     * 描述物品列表（简短）
     * <p>
     * 不再限制显示数量，以完整展示最多 10 个产物或需求物品。
     */
    private String describeItems(List<NekoTradeEntry.ItemEntry> items) {
        if (items == null || items.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            NekoTradeEntry.ItemEntry entry = items.get(i);
            if (i > 0) sb.append("+");
            String name = getItemShortName(entry.getItem());
            sb.append(name)
                .append("x")
                .append(entry.getAmount());
        }
        return sb.toString();
    }

    /**
     * 获取物品短名称
     */
    private String getItemShortName(String itemId) {
        if (itemId == null) return "?";
        int colonIdx = itemId.indexOf(':');
        if (colonIdx >= 0) {
            String name = itemId.substring(colonIdx + 1);
            // 去掉 minecraft: 前缀
            return name.length() > 10 ? name.substring(0, 9) + "…" : name;
        }
        return itemId.length() > 10 ? itemId.substring(0, 9) + "…" : itemId;
    }

    private String getItemId(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        String gameId = GameRegistry.findUniqueIdentifierFor(stack.getItem())
            .toString();
        if (gameId.contains("@")) {
            gameId = gameId.substring(0, gameId.indexOf('@'));
        }
        return gameId;
    }

    // ==================== 帮助信息 ====================

    private void sendHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "用法:"));
        sender.addChatMessage(new ChatComponentText("/gtit gift certain [yesNBT|noNBT] - 设置必中物品为当前背包"));
        sender.addChatMessage(new ChatComponentText("/gtit gift random <count> [yesNBT|noNBT] - 设置随机物品为当前背包"));
        sender.addChatMessage(new ChatComponentText("/gtit gift reset - 重置为默认配置"));
        sender.addChatMessage(new ChatComponentText("/gtit gift claimreset [all|玩家名] - 重置新手礼包领取状态"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm help - 猫猫售货机完整帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm edithelp - 交易编辑帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm pagehelp - 标签页管理帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm list [标签页] - 列出交易条目"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm reload - 热重载猫猫币交易配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm save - 保存当前交易数据到配置文件"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm timereset - 重置所有交易冷却"));
        sender.addChatMessage(new ChatComponentText("/gtit signin - 每日签到"));
        sender.addChatMessage(new ChatComponentText("/gtit signin help - 签到命令帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit mail help - 邮件命令帮助（发送/首登/一次性奖励）"));
    }

    private void sendNekoVMHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "猫猫售货机命令:"));
        sender.addChatMessage(
            new ChatComponentText("/gtit nekovm edit <标签页ID> [顺序ID] [冷却] [绑定ID] [yesNBT|noNBT] - 导入交易"));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA + "/gtit nekovm edit on|off - 开关可视化编辑模式"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm list [标签页] - 列出交易条目"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm delete <标签页ID> <顺序ID> - 删除交易条目"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm page add <ID> <名字> - 添加/覆盖标签页（手持物品作图标）"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm page delet <ID> - 删除自定义标签页"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm edithelp - 详细编辑帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm pagehelp - 标签页管理帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm help - 完整帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm reload - 热重载配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm save - 保存配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm timereset - 重置所有交易冷却"));
    }

    // ==================== v1.7.0 签到子命令 ====================

    /**
     * /gtit signin —— 签到相关指令入口
     * <p>
     * 子命令：
     * <ul>
     * <li>/gtit signin —— 玩家自己签到（与 GUI 按钮等效）</li>
     * <li>/gtit signin info [玩家名] —— 查看签到状态（默认自己）</li>
     * <li>/gtit signin reload —— 热重载签到奖励配置</li>
     * <li>/gtit signin admin set &lt;玩家名&gt; &lt;天数&gt; —— 设置连续签到天数</li>
     * <li>/gtit signin admin reset &lt;玩家名&gt; —— 重置玩家签到数据</li>
     * </ul>
     */
    private void handleSignIn(ICommandSender sender, String[] args) {
        // /gtit signin —— 玩家自己签到
        if (args.length == 1) {
            if (!(sender instanceof EntityPlayerMP player)) {
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.RED + "控制台请使用 /gtit signin info|admin 子命令"));
                return;
            }
            doSignIn(player);
            return;
        }

        switch (args[1]) {
            case "info" -> handleSignInInfo(sender, args);
            case "reload" -> {
                DailySignInConfig.reload();
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "签到奖励配置已热重载"));
            }
            case "admin" -> handleSignInAdmin(sender, args);
            case "help" -> sendSignInHelp(sender);
            default -> sendSignInHelp(sender);
        }
    }

    /**
     * 执行签到并向玩家反馈结果（指令路径，与网络包路径逻辑一致）
     */
    private void doSignIn(EntityPlayerMP player) {
        DailySignInManager manager = DailySignInManager.INSTANCE;
        DailySignInManager.SignInResult result = manager.signIn(player.getUniqueID());

        int resultCode;
        switch (result.getStatus()) {
            case SUCCESS -> {
                resultCode = SignInClientData.RESULT_SUCCESS;
                StringBuilder sb = new StringBuilder();
                sb.append(EnumChatFormatting.GREEN)
                    .append("签到成功！")
                    .append(EnumChatFormatting.YELLOW)
                    .append(" +")
                    .append(result.getBaseReward())
                    .append(" 猫猫币")
                    .append(EnumChatFormatting.GRAY)
                    .append("（连续 ")
                    .append(result.getConsecutiveDays())
                    .append(" 天）");
                if (result.getTierReward() != null) {
                    sb.append(EnumChatFormatting.GOLD)
                        .append(" [达成")
                        .append(
                            result.getTierReward()
                                .getRequiredDays())
                        .append("天阶梯奖励]");
                }
                player.addChatMessage(new ChatComponentText(sb.toString()));
            }
            case ALREADY_SIGNED -> {
                resultCode = SignInClientData.RESULT_ALREADY_SIGNED;
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "今天已经签过到了"));
            }
            default -> {
                resultCode = SignInClientData.RESULT_ERROR;
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "签到失败，请稍后再试"));
            }
        }

        // 推送最新数据，使该玩家打开的签到日历同步刷新
        DailySignInData data = manager.getSignInData(player.getUniqueID());
        int tierDays = result.getTierReward() != null ? result.getTierReward()
            .getRequiredDays() : 0;
        SignInNetworkManager.sendSyncToClient(player, data, resultCode, result.getBaseReward(), tierDays);
    }

    /**
     * /gtit signin info [玩家名] —— 查看签到状态
     */
    private void handleSignInInfo(ICommandSender sender, String[] args) {
        EntityPlayerMP target;
        if (args.length >= 3) {
            target = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152612_a(args[2]);
            if (target == null) {
                sender
                    .addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "玩家不在线: " + args[2] + "（仅支持在线玩家）"));
                return;
            }
        } else if (sender instanceof EntityPlayerMP) {
            target = (EntityPlayerMP) sender;
        } else {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "控制台请指定玩家: /gtit signin info <玩家名>"));
            return;
        }

        DailySignInData data = DailySignInManager.INSTANCE.getSignInData(target.getUniqueID());
        String name = target.getCommandSenderName();
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "===== " + name + " 的签到状态 ====="));
        sender.addChatMessage(
            new ChatComponentText(
                "累计签到: " + EnumChatFormatting.GOLD + data.getTotalSignInDays() + EnumChatFormatting.RESET + " 天"));
        sender.addChatMessage(
            new ChatComponentText(
                "连续签到: " + EnumChatFormatting.GOLD + data.getConsecutiveDays() + EnumChatFormatting.RESET + " 天"));
        sender.addChatMessage(
            new ChatComponentText(
                "当月已签: " + EnumChatFormatting.GOLD
                    + data.getMonthlySignInDates()
                        .size()
                    + EnumChatFormatting.RESET
                    + " 天"));
        String last = data.getLastSignInDate();
        sender.addChatMessage(new ChatComponentText("上次签到: " + (last == null || last.isEmpty() ? "从未" : last)));
        sender.addChatMessage(
            new ChatComponentText(
                "今日状态: " + (data.hasSignedToday(DailySignInManager.getToday()) ? EnumChatFormatting.GREEN + "已签到"
                    : EnumChatFormatting.GRAY + "未签到")));
    }

    /**
     * /gtit signin admin set|reset —— 管理员操作
     */
    private void handleSignInAdmin(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "用法: /gtit signin admin set <玩家名> <天数> 或 /gtit signin admin reset <玩家名>"));
            return;
        }

        EntityPlayerMP target = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(args[3]);
        if (target == null) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "玩家不在线: " + args[3] + "（仅支持在线玩家）"));
            return;
        }
        UUID targetId = target.getUniqueID();

        switch (args[2]) {
            case "set" -> {
                if (args.length < 5) {
                    sender.addChatMessage(
                        new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit signin admin set <玩家名> <天数>"));
                    return;
                }
                int days;
                try {
                    days = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "天数必须是整数: " + args[4]));
                    return;
                }
                DailySignInManager.INSTANCE.adminSetConsecutiveDays(targetId, days);
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GREEN + "已将 "
                            + target.getCommandSenderName()
                            + " 的连续签到天数设为 "
                            + Math.max(0, days)));
            }
            case "reset" -> {
                DailySignInManager.INSTANCE.adminResetData(targetId);
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GREEN + "已重置 " + target.getCommandSenderName() + " 的签到数据"));
            }
            default -> sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "未知管理操作: " + args[2] + "，使用 set 或 reset"));
        }
    }

    private void sendSignInHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "签到命令:"));
        sender.addChatMessage(new ChatComponentText("/gtit signin - 玩家自己签到"));
        sender.addChatMessage(new ChatComponentText("/gtit signin info [玩家名] - 查看签到状态"));
        sender.addChatMessage(new ChatComponentText("/gtit signin reload - 热重载签到奖励配置"));
        sender.addChatMessage(new ChatComponentText("/gtit signin admin set <玩家名> <天数> - 设置连续签到天数"));
        sender.addChatMessage(new ChatComponentText("/gtit signin admin reset <玩家名> - 重置玩家签到数据"));
    }

    // ==================== v1.7.2 邮件子命令 ====================

    /**
     * /gtit mail —— 邮件系统指令入口
     * <p>
     * 子命令：
     * <ul>
     * <li>/gtit mail send &lt;玩家名&gt; &lt;标题&gt; [正文...] —— 发送邮件（支持离线玩家；
     * 附件 = 执行者手持物品，可空手；正文中字面 {@code \n} 转换为换行）</li>
     * <li>/gtit mail first &lt;标题&gt; [正文...] —— 设置首登奖励模板（附件 = 手持物品，
     * 覆盖旧模板；新玩家首次登录自动投递，按玩家标记防重）</li>
     * <li>/gtit mail firstclear —— 清除首登奖励模板</li>
     * <li>/gtit mail once &lt;奖励ID&gt; &lt;标题&gt; [正文...] —— 发布一次性奖励
     * （附件 = 手持物品；奖励 ID 唯一不可重复发布，全体玩家各收一次）</li>
     * </ul>
     */
    private void handleMail(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMailHelp(sender);
            return;
        }

        switch (args[1]) {
            case "send" -> handleMailSend(sender, args);
            case "first" -> handleMailFirst(sender, args);
            case "firstclear" -> {
                boolean had = MailManager.INSTANCE.clearFirstRewardTemplate();
                if (had) {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "已清除首登奖励模板"));
                } else {
                    sender.addChatMessage(
                        new ChatComponentText(EnumChatFormatting.YELLOW + "当前没有设置首登奖励模板"));
                }
            }
            case "once" -> handleMailOnce(sender, args);
            case "help" -> sendMailHelp(sender);
            default -> sendMailHelp(sender);
        }
    }

    /**
     * /gtit mail send &lt;玩家名&gt; &lt;标题&gt; [正文...]
     * <p>
     * 控制台可执行（发件人显示为「系统」，无附件）；
     * 玩家执行时发件人为玩家名，手持物品作为附件（深拷贝，不消耗手中物品）。
     */
    private void handleMailSend(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit mail send <玩家名> <标题> [正文...]"));
            return;
        }
        String targetName = args[2];
        String title = args[3];
        String content = joinMailContent(args, 4);

        // 解析目标玩家 UUID（在线优先，离线走 usercache.json）
        UUID targetId = resolvePlayerUuid(targetName);
        if (targetId == null) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "找不到玩家: " + targetName + "（从未登录过本服务器）"));
            return;
        }

        // 发件人显示名与附件（执行者为玩家时取手持物品）
        String senderName = "系统";
        List<ItemStack> attachments = new ArrayList<>();
        if (sender instanceof EntityPlayerMP player) {
            senderName = player.getCommandSenderName();
            attachments = heldAttachment(player);
        }

        Mail mail = new Mail(title, content, senderName, attachments);
        boolean ok = MailManager.INSTANCE.sendMail(targetId, mail);
        if (ok) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "邮件已发送给 " + targetName + "（附件 " + attachments.size() + " 组）"));
        } else {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "发送失败：" + targetName + " 的邮箱已满（" + MailManager.MAX_MAILS + " 封）"));
        }
    }

    /**
     * /gtit mail first &lt;标题&gt; [正文...]
     * <p>
     * 设置首登奖励模板（仅玩家可执行，手持物品作为附件）。
     * 已收过首登奖励的老玩家不会补发（按玩家 firstRewardReceived 标记防重）。
     */
    private void handleMailFirst(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "此命令只有玩家可以执行（需要手持物品作为附件）"));
            return;
        }
        if (args.length < 3) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit mail first <标题> [正文...]"));
            return;
        }
        String title = args[2];
        String content = joinMailContent(args, 3);
        List<ItemStack> attachments = heldAttachment(player);

        Mail template = new Mail(title, content, "系统", attachments);
        boolean replacing = MailManager.INSTANCE.getFirstRewardTemplate() != null;
        MailManager.INSTANCE.setFirstRewardTemplate(template);
        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "首登奖励模板已"
                    + (replacing ? "覆盖更新" : "设置")
                    + "（附件 "
                    + attachments.size()
                    + " 组），新玩家首次登录时将自动收到"));
        player.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GRAY + "注：已领取过首登奖励的老玩家不会补发"));
    }

    /**
     * /gtit mail once &lt;奖励ID&gt; &lt;标题&gt; [正文...]
     * <p>
     * 发布一次性奖励（仅玩家可执行，手持物品作为附件）。
     * 奖励 ID 唯一：重复 ID 拒绝发布，保证「一次性」语义不被覆盖重发破坏；
     * 在线玩家立即投递，离线玩家登录时补投（按玩家 receivedOnceIds 集合防重）。
     */
    private void handleMailOnce(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "此命令只有玩家可以执行（需要手持物品作为附件）"));
            return;
        }
        if (args.length < 4) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit mail once <奖励ID> <标题> [正文...]"));
            return;
        }
        String rewardId = args[2];
        String title = args[3];
        String content = joinMailContent(args, 4);
        List<ItemStack> attachments = heldAttachment(player);

        Mail template = new Mail(title, content, "系统", attachments);
        boolean ok = MailManager.INSTANCE.publishOnceReward(rewardId, template);
        if (ok) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "一次性奖励 ["
                        + rewardId
                        + "] 已发布（附件 "
                        + attachments.size()
                        + " 组），全体玩家将各收到一次"));
        } else {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "发布失败：奖励 ID [" + rewardId + "] 已发布过（一次性奖励不可重复发布）"));
        }
    }

    /**
     * 拼接邮件正文参数（从 start 起到末尾以空格连接；字面 "\n" 转换为换行符）
     */
    private String joinMailContent(String[] args, int start) {
        if (args.length <= start) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString()
            .replace("\\n", "\n");
    }

    /**
     * 取玩家手持物品作为附件列表（空手返回空列表；深拷贝，不消耗手中物品）
     */
    private List<ItemStack> heldAttachment(EntityPlayerMP player) {
        List<ItemStack> attachments = new ArrayList<>();
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() != null) {
            attachments.add(held.copy());
        }
        return attachments;
    }

    /**
     * 按玩家名解析 UUID（在线玩家直接取；离线玩家查 usercache.json，查不到返回 null）
     */
    private UUID resolvePlayerUuid(String playerName) {
        EntityPlayerMP online = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(playerName);
        if (online != null) {
            return online.getUniqueID();
        }
        File worldDir = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getWorldDirectory();
        File userCache = new File(worldDir.getParentFile(), "usercache.json");
        return findUuidFromUserCache(playerName, userCache);
    }

    private void sendMailHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "邮件命令:"));
        sender.addChatMessage(new ChatComponentText("/gtit mail send <玩家名> <标题> [正文...] - 发送邮件（附件=手持物品）"));
        sender.addChatMessage(new ChatComponentText("/gtit mail first <标题> [正文...] - 设置首登奖励模板（覆盖旧的）"));
        sender.addChatMessage(new ChatComponentText("/gtit mail firstclear - 清除首登奖励模板"));
        sender.addChatMessage(
            new ChatComponentText("/gtit mail once <奖励ID> <标题> [正文...] - 发布一次性奖励（全服各收一次）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "正文中输入 \\n 表示换行；附件取手持物品（可空手）"));
    }

    // ==================== Page 子命令 ====================

    /**
     * /gtit nekovm page add <ID> <名字> 或 /gtit nekovm page delet <ID>
     */
    private void handleNekoVMPage(EntityPlayerMP player, String[] args) {
        if (args.length < 3) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "用法: /gtit nekovm page add <ID> <名字> 或 /gtit nekovm page delet <ID>"));
            return;
        }

        switch (args[2]) {
            case "add" -> handlePageAdd(player, args);
            case "delet" -> handlePageDelet(player, args);
            default -> player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "未知子命令: " + args[2] + "，使用 add 或 delet"));
        }
    }

    /**
     * /gtit nekovm page add <ID> <名字>
     * 使用手持物品作为标签页图标
     */
    private void handlePageAdd(EntityPlayerMP player, String[] args) {
        if (args.length < 5) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit nekovm page add <标签页ID> <标签页名字>"));
            return;
        }

        int pageId;
        try {
            pageId = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是正整数"));
            return;
        }

        if (pageId < 1) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须为正整数"));
            return;
        }

        // 标签页名字（支持空格，从args[4]开始拼接）
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 4; i < args.length; i++) {
            if (nameBuilder.length() > 0) nameBuilder.append(" ");
            nameBuilder.append(args[i]);
        }
        String pageName = nameBuilder.toString();

        if (pageName.isEmpty()) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页名字不能为空"));
            return;
        }

        // 手持物品作为图标
        ItemStack heldItem = player.getHeldItem();
        if (heldItem == null) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "请手持一个物品作为标签页图标！"));
            return;
        }

        String result = NekoPageRegistry.addPage(pageId, pageName, heldItem);
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + result));

        // 提示需要重载
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "提示：使用 /gtit nekovm reload 使标签页生效"));
    }

    /**
     * /gtit nekovm page delet <ID>
     */
    private void handlePageDelet(EntityPlayerMP player, String[] args) {
        if (args.length < 4) {
            player
                .addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit nekovm page delet <标签页ID>"));
            return;
        }

        int pageId;
        try {
            pageId = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是正整数"));
            return;
        }

        String result = NekoPageRegistry.deletePage(pageId);
        if (result.startsWith("已删除")) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + result));
        } else {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + result));
        }
    }

    /**
     * /gtit nekovm pagehelp
     */
    private void handleNekoVMPageHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "=== 猫猫售货机标签页管理帮助 ==="));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm page add <ID> <名字>"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  添加或覆盖标签页，手持物品作为图标"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  ID 1-3 为默认标签页（可覆盖名称和图标，不可删除）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  ID 4+ 为自定义标签页"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  已存在的ID会覆盖（名称+图标）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm page delet <ID>"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  删除自定义标签页（ID 1-3 不可删除）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  该标签页的交易会移至\"其他\"标签页"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "当前标签页:"));
        for (com.miaokatze.gtit.trade.NekoPageEntry page : NekoPageRegistry.getAllPages()) {
            String tag = page.isDefault() ? " [默认]" : " [自定义]";
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.WHITE + "  #" + page.getId() + " " + page.getName() + tag));
        }
    }

    /**
     * /gtit nekovm help
     */
    private void handleNekoVMFullHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "=== 猫猫售货机完整帮助 ==="));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "--- 交易管理 ---"));
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm edit <标签页ID> [顺序ID] [冷却] [绑定ID]"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  导入交易条目，读取背包和工具栏物品"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm list [标签页ID]"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  列出交易条目，不写标签页则列出全部"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm delete <标签页ID> <顺序ID>"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  删除指定交易条目"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "--- 标签页管理 ---"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm page add <ID> <名字>"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  添加/覆盖标签页，手持物品作图标"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm page delet <ID>"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  删除自定义标签页（1-3不可删除）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "--- 系统命令 ---"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm reload"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  热重载交易和标签页配置"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm save"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  保存当前交易数据到配置文件"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm timereset"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  重置当前玩家（团队）的所有交易冷却"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "--- 更多帮助 ---"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm edithelp - 交易编辑详细帮助"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm pagehelp - 标签页管理详细帮助"));
    }
}
