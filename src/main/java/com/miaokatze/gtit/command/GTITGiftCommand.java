package com.miaokatze.gtit.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.lottery.LotteryConfig;
import com.miaokatze.gtit.lottery.LotteryManager;
import com.miaokatze.gtit.mail.Mail;
import com.miaokatze.gtit.mail.MailManager;
import com.miaokatze.gtit.signin.DailySignInConfig;
import com.miaokatze.gtit.signin.DailySignInData;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.signin.OnlineTimeConfig;
import com.miaokatze.gtit.signin.SignInClientData;
import com.miaokatze.gtit.signin.SignInNetworkManager;
import com.miaokatze.gtit.terminal.StarterGiftAudit;
import com.miaokatze.gtit.terminal.TerminalNetworkManager;
import com.miaokatze.gtit.terminal.TerminalText;
import com.miaokatze.gtit.trade.v2.NekoEditModeManager;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeNetworkManager;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;
import com.miaokatze.gtit.util.PlayerLookup;
import com.miaokatze.gtit.util.PlayerResolver;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * /gtit 指令
 * - /gtit gift certain [yesNBT|noNBT]: 将当前背包物品设为必中物品
 * - /gtit gift random <count> [yesNBT|noNBT]: 将当前背包物品设为随机物品，设置随机数
 * - /gtit gift reset: 恢复默认配置
 * - /gtit gift claimlist: 列出已领取新手礼包的玩家（在线+离线，支持控制台执行）
 * - /gtit gift claimreset [all|玩家名]: 重置新手礼包领取状态（支持在线/离线玩家，支持控制台执行）
 * - /gtit nekovm edit on|off: 开关可视化配置编辑模式
 * - /gtit nekovm reload: 热重载猫猫币交易配置
 * - /gtit nekovm timereset: 重置当前所有交易冷却
 * - /gtit nekovm help: 显示完整帮助
 * <p>
 * 配置类指令默认不记录 NBT；如需记录，请在指令末尾添加 {@code yesNBT}。
 */
public class GTITGiftCommand extends CommandBase {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    @Override
    public String getCommandName() {
        return "gtit";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtit gift certain [yesNBT|noNBT]|random <count> [yesNBT|noNBT]|reset|claimlist|claimreset [all|玩家名] | /gtit nekovm edit on|off|reload|timereset|help | /gtit signin [info|reload|admin|help] | /gtit lottery reload | /gtit mail [send|first|firstclear|once|help] | /gtit terminal";
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
            case "lottery" -> handleLottery(sender, args);
            case "mail" -> handleMail(sender, args);
            case "terminal" -> handleTerminal(sender);
            default -> sendHelp(sender);
        }
    }

    // ==================== Tab 补全 ====================

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "gift", "nekovm", "signin", "lottery", "mail", "terminal");
        }

        if (args.length == 2) {
            if ("gift".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, "certain", "random", "reset", "claimlist", "claimreset");
            }
            if ("nekovm".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, "edit", "reload", "timereset", "help");
            }
            if ("signin".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, "info", "reload", "admin", "help");
            }
            if ("lottery".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, "reload", "help");
            }
            if ("mail".equals(args[0])) {
                return getListOfStringsMatchingLastWord(args, "send", "first", "firstclear", "once", "help");
            }
        }

        if (args.length == 3 && "mail".equals(args[0]) && "send".equals(args[1])) {
            // /gtit mail send <玩家名>：补全在线玩家名（O2-12：PlayerLookup 统一收集）
            return getListOfStringsMatchingLastWord(
                args,
                PlayerLookup.getOnlineNames()
                    .toArray(new String[0]));
        }

        if (args.length == 3 && "signin".equals(args[0])) {
            if ("admin".equals(args[1])) {
                return getListOfStringsMatchingLastWord(args, "set", "reset");
            }
            if ("info".equals(args[1])) {
                // 补全在线玩家名（O2-12）
                return getListOfStringsMatchingLastWord(
                    args,
                    PlayerLookup.getOnlineNames()
                        .toArray(new String[0]));
            }
        }

        if (args.length == 4 && "signin".equals(args[0]) && "admin".equals(args[1])) {
            // /gtit signin admin set|reset <玩家名>（O2-12）
            return getListOfStringsMatchingLastWord(
                args,
                PlayerLookup.getOnlineNames()
                    .toArray(new String[0]));
        }

        if (args.length == 3 && "gift".equals(args[0])) {
            if ("certain".equals(args[1]) || "random".equals(args[1])) {
                // 补全 NBT 标记：/gtit gift certain [yesNBT|noNBT] 或 /gtit gift random <count> [yesNBT|noNBT]
                return getListOfStringsMatchingLastWord(args, "yesNBT", "noNBT");
            }
            if ("claimreset".equals(args[1])) {
                // 补全 "all" + 在线玩家名（O2-12）
                List<String> options = new ArrayList<>();
                options.add("all");
                options.addAll(PlayerLookup.getOnlineNames());
                return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
            }
        }

        if (args.length == 3 && "nekovm".equals(args[0]) && "edit".equals(args[1])) {
            // /gtit nekovm edit on|off
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }

        if (args.length == 4 && "gift".equals(args[0]) && "random".equals(args[1])) {
            // /gtit gift random <count> [yesNBT|noNBT]
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
            case "claimlist" -> handleClaimList(sender);
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

    /** NBT 键名：玩家已领取新手礼包的标记（Terminal T1 迁移至 {@link StarterGiftAudit}，此处委托引用） */
    private static final String GIFT_CLAIMED_KEY = StarterGiftAudit.GIFT_CLAIMED_KEY;

    /**
     * /gtit gift claimlist
     * 列出所有已领取新手礼包的玩家（在线 + 离线），支持控制台执行。
     * 在线玩家读内存 NBT；离线玩家扫描 playerdata/*.dat（ForgeData→PlayerPersisted→gtit_received_starter_gift），
     * 通过 usercache.json 反查玩家名，查不到则显示 UUID。
     */
    private void handleClaimList(ICommandSender sender) {
        List<String> onlineClaimed = new ArrayList<>();
        List<String> offlineClaimed = new ArrayList<>();

        // 在线：遍历内存 NBT（O2-12：PlayerLookup 统一遍历）
        PlayerLookup.forEachOnlinePlayer(player -> {
            if (hasGiftClaimedFlag(player.getEntityData())) {
                onlineClaimed.add(player.getCommandSenderName());
            }
        });

        // 离线：扫描 .dat（跳过在线玩家，避免重复）
        Set<UUID> onlineUuids = PlayerLookup.buildUuidSet();
        collectOfflineClaimedPlayers(onlineUuids, offlineClaimed);

        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "===== 已领取新手礼包的玩家 ====="));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "在线 ("
                    + onlineClaimed.size()
                    + "): "
                    + EnumChatFormatting.WHITE
                    + (onlineClaimed.isEmpty() ? "无" : String.join(", ", onlineClaimed))));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.AQUA + "离线 ("
                    + offlineClaimed.size()
                    + "): "
                    + EnumChatFormatting.WHITE
                    + (offlineClaimed.isEmpty() ? "无" : String.join(", ", offlineClaimed))));
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GRAY + "合计: " + (onlineClaimed.size() + offlineClaimed.size()) + " 人"));
    }

    /**
     * 判断玩家数据中是否含新手礼包领取标记。
     * 接受两种输入：在线玩家的 getEntityData()（已处于 ForgeData 层级）或离线 .dat 的根 NBT。
     */
    private static boolean hasGiftClaimedFlag(NBTTagCompound dataTag) {
        // Terminal T1 迁移至 StarterGiftAudit（逻辑逐行保持），保留签名令既有调用点零改动
        return StarterGiftAudit.hasGiftClaimedFlag(dataTag);
    }

    /**
     * 扫描 playerdata/*.dat，收集已领取新手礼包的离线玩家，跳过当前在线玩家。
     * 玩家名优先从 usercache.json 反查，查不到则用 UUID。
     */
    private void collectOfflineClaimedPlayers(Set<UUID> onlineUuids, List<String> outNames) {
        // Terminal T1 迁移至 StarterGiftAudit（逻辑逐行保持），保留签名令既有调用点零改动
        StarterGiftAudit.collectOfflineClaimedPlayers(onlineUuids, outNames);
    }

    /**
     * 只读检查单个 .dat 文件中是否存在新手礼包领取标记（不修改文件）。
     * 路径：.dat 根 → ForgeData → PlayerPersisted → gtit_received_starter_gift
     */
    private boolean offlineDatHasGiftClaimed(File datFile) {
        // Terminal T1 迁移至 StarterGiftAudit（逻辑逐行保持），保留签名令既有调用点零改动
        return StarterGiftAudit.offlineDatHasGiftClaimed(datFile);
    }

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
        // lambda 内可变计数（O2-12：PlayerLookup 统一遍历）
        int[] onlineReset = { 0 };

        // 处理所有在线玩家
        PlayerLookup.forEachOnlinePlayer(player -> {
            if (resetOnlinePlayerGiftFlag(player)) {
                onlineReset[0]++;
            }
        });

        // 处理所有离线玩家（扫描 playerdata 目录）
        int offlineReset = resetAllOfflinePlayerGiftFlags();

        int total = onlineReset[0] + offlineReset;
        if (total > 0) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "已重置所有玩家的新手礼包领取状态"));
            // Terminal T1 修复：原误打印数组引用（[I@...），改为计数 onlineReset[0]
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.WHITE + "  在线: " + onlineReset[0] + "  离线: " + offlineReset));
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
        // Terminal T1 迁移至 StarterGiftAudit（逻辑逐行保持），保留签名令既有调用点零改动
        return StarterGiftAudit.resetOnlinePlayerGiftFlag(player);
    }

    /**
     * 重置单个 .dat 文件中的礼包领取标记
     * 
     * @return true 成功重置；false 标记不存在或操作失败
     */
    private boolean resetOfflinePlayerGiftFlag(File datFile) {
        // Terminal T1 迁移至 StarterGiftAudit（逻辑逐行保持，含写前在线复查与 safeWrite 竞态保护），
        // 保留签名令既有调用点零改动
        return StarterGiftAudit.resetOfflinePlayerGiftFlag(datFile);
    }

    /**
     * 通过玩家名重置离线玩家的礼包领取标记
     * 使用 usercache.json 查找 UUID，回退到扫描 .dat 文件
     * 
     * @return true 成功重置；false 找不到玩家或操作失败
     */
    private boolean resetOfflinePlayerGiftFlagByName(String playerName) {
        // Terminal T1 迁移至 StarterGiftAudit（逻辑逐行保持），保留签名令既有调用点零改动
        return StarterGiftAudit.resetOfflinePlayerGiftFlagByName(playerName);
    }

    /**
     * 重置所有离线玩家的礼包领取标记
     * 扫描 playerdata 目录，跳过当前在线的玩家，避免内存/文件数据冲突
     * 
     * @return 成功重置的离线玩家数量
     */
    private int resetAllOfflinePlayerGiftFlags() {
        // Terminal T1 迁移至 StarterGiftAudit（逻辑逐行保持），保留签名令既有调用点零改动
        return StarterGiftAudit.resetAllOfflinePlayerGiftFlags();
    }

    // ==================== NekoVM 子命令 ====================

    private void handleNekoVM(ICommandSender sender, EntityPlayerMP player, String[] args) {
        if (args.length < 2) {
            sendNekoVMHelp(sender);
            return;
        }

        switch (args[1]) {
            case "edit" -> {
                if (args.length >= 3 && ("on".equalsIgnoreCase(args[2]) || "off".equalsIgnoreCase(args[2]))) {
                    handleNekoVMEditModeToggle(player, "on".equalsIgnoreCase(args[2]));
                } else {
                    player
                        .addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit nekovm edit on|off"));
                }
            }
            case "reload" -> handleNekoVMReload(player);
            case "timereset" -> handleNekoVMTimeReset(player);
            case "help" -> handleNekoVMFullHelp(sender);
            default -> sendNekoVMHelp(sender);
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
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[编辑模式] 已开启可视化配置编辑模式"));
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "打开猫猫售货机 GUI，左键点击交易条目进行编辑"));
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "使用 /gtit nekovm edit off 退出编辑模式"));
        } else {
            NekoEditModeManager.INSTANCE.exitEditMode(playerId);
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "[编辑模式] 已关闭可视化配置编辑模式"));
        }
        LOG.info("[NekoEdit] 玩家 {} {}编辑模式", player.getCommandSenderName(), enable ? "进入" : "退出");
    }

    private void handleNekoVMReload(EntityPlayerMP player) {
        // V1 的 NekoTradeRegistry.reload() 已移除（V1 反射注入 VM TradeDatabase 的逻辑不再需要）
        // 仅保留 V2 的热重载：重载标签页配置、清空 BQ 触发器、重新加载交易数据
        boolean success = NekoTradeRegistryV2.reload();
        if (success) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "猫猫币交易配置已热重载"));
            // v1.7.0 目标 5：重载后广播服务端最新交易/标签页配置，刷新全服客户端缓存
            NekoTradeNetworkManager.sendSyncToAll();
        } else {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "猫猫币交易配置热重载失败，请查看服务器日志"));
        }
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
        sender.addChatMessage(new ChatComponentText("/gtit gift claimlist - 列出已领取新手礼包的玩家（在线+离线）"));
        sender.addChatMessage(new ChatComponentText("/gtit gift claimreset [all|玩家名] - 重置新手礼包领取状态（支持离线玩家）"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm help - 猫猫售货机完整帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm edit on|off - 开关可视化编辑模式"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm reload - 热重载猫猫币交易配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm timereset - 重置所有交易冷却"));
        sender.addChatMessage(new ChatComponentText("/gtit signin - 每日签到"));
        sender.addChatMessage(new ChatComponentText("/gtit signin help - 签到命令帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit lottery reload - 热重载抽奖配置"));
        sender.addChatMessage(new ChatComponentText("/gtit mail help - 邮件命令帮助（发送/首登/一次性奖励）"));
        sender.addChatMessage(new ChatComponentText("/gtit terminal - 打开服务器管理终端（仅游戏内 OP2 玩家）"));
    }

    private void sendNekoVMHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "猫猫售货机命令:"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm edit on|off - 开关可视化编辑模式"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm reload - 热重载配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm timereset - 重置所有交易冷却"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm help - 完整帮助"));
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
                OnlineTimeConfig.reload();
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "签到奖励配置（含每日在线奖励）已热重载"));
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
        sender.addChatMessage(new ChatComponentText("/gtit signin reload - 热重载签到奖励配置（含每日在线奖励）"));
        sender.addChatMessage(new ChatComponentText("/gtit signin admin set <玩家名> <天数> - 设置连续签到天数"));
        sender.addChatMessage(new ChatComponentText("/gtit signin admin reset <玩家名> - 重置玩家签到数据"));
    }

    // ==================== v1.7.7 G4 抽奖子命令 ====================

    /**
     * /gtit lottery —— 抽奖配置指令入口
     * <p>
     * 子命令：
     * <ul>
     * <li>/gtit lottery reload —— 热重载抽奖卡池配置</li>
     * <li>/gtit lottery help —— 显示帮助</li>
     * </ul>
     */
    private void handleLottery(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendLotteryHelp(sender);
            return;
        }

        switch (args[1]) {
            case "reload" -> {
                LotteryConfig.reload();
                LotteryManager.INSTANCE.loadConfig();
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "抽奖配置已热重载"));
            }
            case "help" -> sendLotteryHelp(sender);
            default -> sendLotteryHelp(sender);
        }
    }

    private void sendLotteryHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "抽奖命令:"));
        sender.addChatMessage(new ChatComponentText("/gtit lottery reload - 热重载抽奖卡池配置"));
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
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "当前没有设置首登奖励模板"));
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
        UUID targetId = PlayerResolver.resolvePlayerUuid(targetName);
        if (targetId == null) {
            sender
                .addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "找不到玩家: " + targetName + "（从未登录过本服务器）"));
            return;
        }

        // 发件人显示名与附件（执行者为玩家时取手持物品）
        String senderName = "系统";
        List<ItemStack> attachments = new ArrayList<>();
        if (sender instanceof EntityPlayerMP player) {
            senderName = player.getCommandSenderName();
            attachments = heldAttachment(player);
        }

        // v1.7.6 G2②：指令路径发出的邮件归类为管理员邮件（类型分页用）
        Mail mail = new Mail(title, content, senderName, attachments, Mail.TYPE_ADMIN);
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
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "此命令只有玩家可以执行（需要手持物品作为附件）"));
            return;
        }
        if (args.length < 3) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit mail first <标题> [正文...]"));
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
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "注：已领取过首登奖励的老玩家不会补发"));
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
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "此命令只有玩家可以执行（需要手持物品作为附件）"));
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
                new ChatComponentText(EnumChatFormatting.RED + "发布失败：奖励 ID [" + rewardId + "] 已发布过（一次性奖励不可重复发布）"));
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

    private void sendMailHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "邮件命令:"));
        sender.addChatMessage(new ChatComponentText("/gtit mail send <玩家名> <标题> [正文...] - 发送邮件（附件=手持物品）"));
        sender.addChatMessage(new ChatComponentText("/gtit mail first <标题> [正文...] - 设置首登奖励模板（覆盖旧的）"));
        sender.addChatMessage(new ChatComponentText("/gtit mail firstclear - 清除首登奖励模板"));
        sender.addChatMessage(new ChatComponentText("/gtit mail once <奖励ID> <标题> [正文...] - 发布一次性奖励（全服各收一次）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "正文中输入 \\n 表示换行；附件取手持物品（可空手）"));
    }

    // ==================== Terminal 管理终端子命令（T1 骨架） ====================

    /**
     * /gtit terminal —— 打开服务器管理终端（GUI 入口）
     * <p>
     * 仅游戏内玩家可执行：发送 TerminalOpenPacket（S2C，含在线玩家名快照），
     * 客户端打开 MUI2 纯客户端面板 {@code TerminalGui}；
     * 终端内的动作请求走 TerminalActionPacket 五步校验链（实时 OP2 复核），
     * 本命令入口的 OP2 门槛由 vanilla 指令权限（getRequiredPermissionLevel=2）保证。
     * 控制台执行时仅回中文提示，不发包。
     */
    private void handleTerminal(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP player) {
            TerminalNetworkManager.sendOpen(player);
            return;
        }
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + TerminalText.CONSOLE_UNAVAILABLE));
    }

    /**
     * /gtit nekovm help
     */
    private void handleNekoVMFullHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "=== 猫猫售货机完整帮助 ==="));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "--- 编辑模式 ---"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm edit on|off"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  开关可视化编辑模式，开启后 GUI 内直接编辑交易条目"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "--- 系统命令 ---"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm reload"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  热重载交易和标签页配置"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm timereset"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  重置当前玩家（团队）的所有交易冷却"));
    }
}
