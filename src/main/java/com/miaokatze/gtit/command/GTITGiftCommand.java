package com.miaokatze.gtit.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.NekoTradeRegistry;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * /gtit 指令
 * - /gtit gift certain: 将当前背包物品设为必中物品
 * - /gtit gift random <count>: 将当前背包物品设为随机物品，设置随机数
 * - /gtit gift reset: 恢复默认配置
 * - /gtit nekovm edit <标签页> [顺序ID] [冷却时间] [绑定ID]: 导入交易条目
 * - /gtit nekovm list [标签页]: 列出交易条目
 * - /gtit nekovm edithelp: 显示编辑帮助
 * - /gtit nekovm delete <标签页> <顺序ID>: 删除交易条目
 * - /gtit nekovm reload: 热重载猫猫币交易配置
 * - /gtit nekovm save: 手动保存当前交易数据到配置文件
 */
public class GTITGiftCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtit";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtit gift certain|random <count>|reset | /gtit nekovm edit|list|edithelp|delete|reload|save";
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

    // ==================== Gift 子命令 ====================

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

    private void handleCertain(EntityPlayerMP player) {
        List<com.miaokatze.gtit.config.GiftConfig.ItemEntry> entries = new ArrayList<>();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null) {
                String itemId = getItemId(stack);
                if (itemId != null) {
                    entries.add(
                        new com.miaokatze.gtit.config.GiftConfig.ItemEntry(
                            itemId,
                            stack.stackSize,
                            stack.getItemDamage()));
                }
            }
        }
        com.miaokatze.gtit.config.GiftConfig.setGuaranteedItems(entries);
        com.miaokatze.gtit.config.GiftConfig.saveConfig();
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

        List<com.miaokatze.gtit.config.GiftConfig.ItemEntry> entries = new ArrayList<>();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null) {
                String itemId = getItemId(stack);
                if (itemId != null) {
                    entries.add(
                        new com.miaokatze.gtit.config.GiftConfig.ItemEntry(
                            itemId,
                            stack.stackSize,
                            stack.getItemDamage()));
                }
            }
        }
        com.miaokatze.gtit.config.GiftConfig.setRandomItems(entries);
        com.miaokatze.gtit.config.GiftConfig.setRandomCount(count);
        com.miaokatze.gtit.config.GiftConfig.saveConfig();
        player.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GREEN + "随机物品已更新（" + entries.size() + "项，随机数：" + count + "）"));
    }

    private void handleReset(EntityPlayerMP player) {
        com.miaokatze.gtit.config.GiftConfig.resetToDefault();
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "新手宝箱配置已重置为默认"));
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
            default -> sendNekoVMHelp(sender);
        }
    }

    /**
     * /gtit nekovm edit <标签页> [顺序ID] [冷却时间] [绑定ID]
     * <p>
     * 读取玩家背包前两行(需求)和工具栏前4格(产物)，导入交易条目
     */
    private void handleNekoVMEdit(EntityPlayerMP player, String[] args) {
        if (args.length < 3) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit nekovm edit <标签页1-3> [顺序ID] [冷却时间] [绑定ID]"));
            return;
        }

        // 解析标签页ID
        int tabId;
        try {
            tabId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是1-3的整数"));
            return;
        }
        if (tabId < 1 || tabId > 3) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是1-3（1=猫猫币，2=闪烁猫猫币，3=其他）"));
            return;
        }

        // 解析顺序ID（可选，默认自动）
        int orderId = -1;
        if (args.length >= 4) {
            try {
                orderId = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "顺序ID必须是整数"));
                return;
            }
        }

        // 解析冷却时间（可选，默认0）
        int cooldown = 0;
        if (args.length >= 5) {
            try {
                cooldown = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "冷却时间必须是整数（秒）"));
                return;
            }
        }

        // 解析绑定ID（可选，默认空）
        String bqQuestId = "";
        if (args.length >= 6) {
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
                    // 普通物品：放入 fromItems
                    fromItems.add(NekoTradeEntry.ItemEntry.fromItemStack(stack));
                }
            }
        }

        // 读取产物物品：工具栏前4格（slot 0-3）
        List<NekoTradeEntry.ItemEntry> toItems = new ArrayList<>();
        for (int i = 0; i <= 3; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() != null) {
                toItems.add(NekoTradeEntry.ItemEntry.fromItemStack(stack));
            }
        }

        // 合并同类物品
        fromItems = mergeItems(fromItems);
        toItems = mergeItems(toItems);

        if (toItems.isEmpty()) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "产物物品为空！请将产物放在工具栏前4格"));
            return;
        }

        // 加载当前配置
        NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
        List<NekoTradeEntry> trades = data.getTrades();

        // 如果没有指定顺序ID，自动分配
        if (orderId < 0) {
            orderId = getNextOrderId(trades, tabId);
        }

        // 根据标签页和自动检测设置猫猫币信息
        // 优先使用自动检测的猫猫币（从背包中识别），其次根据标签页设置默认值
        NekoTradeEntry.NekoCurrencyCost currency = detectedCurrency;
        String tabName = getTabName(tabId);

        if (currency == null && tabId == 1) {
            // 标签页1（猫猫币）但背包中没有猫猫币，默认1个猫猫币
            currency = new NekoTradeEntry.NekoCurrencyCost("neko", 1);
        } else if (currency == null && tabId == 2) {
            // 标签页2（闪烁猫猫币）但背包中没有闪烁猫猫币，默认1个闪烁猫猫币
            currency = new NekoTradeEntry.NekoCurrencyCost("shimmeringNeko", 1);
        }

        // 验证：猫猫币交易的标签页必须匹配
        if (currency != null) {
            boolean isNeko = "neko".equals(currency.getType());
            boolean isShimmering = "shimmeringNeko".equals(currency.getType());
            if (tabId == 3 && (isNeko || isShimmering)) {
                // "其他"标签页不应该有猫猫币
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页3（其他）不支持猫猫币！请使用标签页1或2"));
                return;
            }
            if (tabId == 1 && isShimmering) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "注意：闪烁猫猫币交易已添加到标签页2"));
                tabId = 2;
            } else if (tabId == 2 && isNeko) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "注意：猫猫币交易已添加到标签页1"));
                tabId = 1;
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
                new ChatComponentText(EnumChatFormatting.YELLOW + "已覆盖 " + tabName + " 标签页的条目 #" + orderId));
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
                new ChatComponentText(EnumChatFormatting.GREEN + "已添加 " + tabName + " 标签页的条目 #" + orderId));
        }

        // 保存并重载
        NekoTradeConfig.save(data);
        NekoTradeRegistry.reload();

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
                if (filterTabId < 1 || filterTabId > 3) {
                    player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是1-3"));
                    return;
                }
            } catch (NumberFormatException e) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是整数"));
                return;
            }
        }

        // 按标签页分组显示
        for (int tabIdx = 1; tabIdx <= 3; tabIdx++) {
            final int currentTab = tabIdx;
            if (filterTabId > 0 && filterTabId != currentTab) continue;

            String tabName = getTabName(currentTab);
            List<NekoTradeEntry> tabTrades = trades.stream()
                .filter(t -> t.getTabId() == currentTab)
                .sorted((a, b) -> Integer.compare(a.getOrderId(), b.getOrderId()))
                .collect(Collectors.toList());

            if (tabTrades.isEmpty() && filterTabId < 1) continue;

            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "=== " + tabName + " ==="));
            if (tabTrades.isEmpty()) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "  (空)"));
                continue;
            }

            for (NekoTradeEntry entry : tabTrades) {
                String fromDesc = describeItems(entry.getFromItems());
                // 如果有猫猫币花费，在前面加上猫猫币信息
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
            new ChatComponentText(EnumChatFormatting.YELLOW + "/gtit nekovm edit <标签页> [顺序ID] [冷却] [绑定ID]"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  标签页: 1=猫猫币, 2=闪烁猫猫币, 3=其他"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  顺序ID: 排序用，不写则自动分配"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  冷却: 交易冷却秒数，不写则0"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  绑定ID: BQ任务ID，不写则不绑定"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "物品读取规则:"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  需求物品 = 背包前两行（共18格）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  产物物品 = 工具栏前4格（快捷栏0-3）"));
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.WHITE + "  同种物品自动合并数量"));
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
            new ChatComponentText(EnumChatFormatting.YELLOW + "示例: /gtit nekovm edit 1 5 60 quest_001"));
    }

    /**
     * /gtit nekovm delete <标签页> <顺序ID>
     */
    private void handleNekoVMDelete(EntityPlayerMP player, String[] args) {
        if (args.length < 4) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "用法: /gtit nekovm delete <标签页1-3> <顺序ID>"));
            return;
        }

        int tabId;
        try {
            tabId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "标签页ID必须是1-3的整数"));
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
                new ChatComponentText(EnumChatFormatting.RED + "找不到 " + getTabName(tabId) + " 标签页的条目 #" + orderId));
            return;
        }

        trades.remove(existing);
        NekoTradeConfig.save(data);
        NekoTradeRegistry.reload();

        player.addChatMessage(
            new ChatComponentText(EnumChatFormatting.GREEN + "已删除 " + getTabName(tabId) + " 标签页的条目 #" + orderId));
    }

    private void handleNekoVMReload(EntityPlayerMP player) {
        boolean success = NekoTradeRegistry.reload();
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

    // ==================== 辅助方法 ====================

    /**
     * 合并同类物品（相同 item+meta 的合并数量）
     */
    private List<NekoTradeEntry.ItemEntry> mergeItems(List<NekoTradeEntry.ItemEntry> items) {
        Map<String, NekoTradeEntry.ItemEntry> merged = new HashMap<>();
        for (NekoTradeEntry.ItemEntry entry : items) {
            String key = entry.getItem() + ":" + entry.getMeta();
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
        return switch (tabId) {
            case 1 -> "猫猫币";
            case 2 -> "闪烁猫猫币";
            case 3 -> "其他";
            default -> "未知";
        };
    }

    /**
     * 描述物品列表（简短）
     */
    private String describeItems(List<NekoTradeEntry.ItemEntry> items) {
        if (items == null || items.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size() && i < 4; i++) {
            NekoTradeEntry.ItemEntry entry = items.get(i);
            if (i > 0) sb.append("+");
            String name = getItemShortName(entry.getItem());
            sb.append(name)
                .append("x")
                .append(entry.getAmount());
        }
        if (items.size() > 4) sb.append("...");
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
        sender.addChatMessage(new ChatComponentText("/gtit gift certain - 设置必中物品为当前背包"));
        sender.addChatMessage(new ChatComponentText("/gtit gift random <count> - 设置随机物品为当前背包"));
        sender.addChatMessage(new ChatComponentText("/gtit gift reset - 重置为默认配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm edithelp - 猫猫售货机编辑帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm list [标签页] - 列出交易条目"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm reload - 热重载猫猫币交易配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm save - 保存当前交易数据到配置文件"));
    }

    private void sendNekoVMHelp(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "猫猫售货机命令:"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm edit <标签页> [顺序ID] [冷却] [绑定ID] - 导入交易"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm list [标签页] - 列出交易条目"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm delete <标签页> <顺序ID> - 删除交易条目"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm edithelp - 详细编辑帮助"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm reload - 热重载配置"));
        sender.addChatMessage(new ChatComponentText("/gtit nekovm save - 保存配置"));
    }
}
