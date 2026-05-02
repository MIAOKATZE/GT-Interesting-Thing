package com.miaokatze.gtit.common.items;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtit.register.CreativeTabManager;
import com.sinthoras.visualprospecting.VisualProspecting_API;

import gregtech.api.enums.Mods;

/**
 * 念力共振探矿核心
 * <p>
 * 简化版：直接调用 VisualProspecting_API 原生功能获取矿脉数据
 * <p>
 * 功能：
 * - 模式1：矿石探测
 * - 模式2：地下流体探测
 * - 右击空气/右击方块行为一致：消耗10饥饿值，扫描后数据上传至旅行地图
 * - Shift+右键：切换模式
 * - 聊天框只显示"结果已上传旅行地图"
 */
public class TelekinesisOreScannerCore extends Item {

    // ========== 探矿模式常量（简化版：只有2个模式）==========
    public static final int MODE_ORES = 0; // 矿石模式
    public static final int MODE_FLUIDS = 1; // 流体模式

    // 探矿参数（UHV级别）
    private static final int SCAN_RADIUS = 9; // 9x2+1 = 19区块
    private static final int HUNGER_COST = 6; // 饥饿值消耗（优先饱和度）

    // NBT键
    private static final String NBT_MODE = "GTIT_ScannerMode";

    /**
     * 构造函数
     */
    public TelekinesisOreScannerCore() {
        super();
        setUnlocalizedName("telekinesis_ore_scanner_core");
        setTextureName("gtit:telekinesis_ore_scanner_core");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    // ==================== 核心功能 ====================

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            // Shift+右键：切换模式
            if (player.isSneaking()) {
                switchMode(stack, player);
                return stack;
            }

            // 执行扫描（右击空气）
            performScan(stack, world, player);
        }
        return stack.copy();
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            // Shift+右键：切换模式
            if (player.isSneaking()) {
                switchMode(stack, player);
                return true;
            }

            // 执行扫描（右击方块，与右击空气一致）
            performScan(stack, world, player);
        }
        return true;
    }

    /**
     * 切换探矿模式
     */
    private void switchMode(ItemStack stack, EntityPlayer player) {
        int mode = getMode(stack);
        mode = (mode + 1) % 2; // 在0-1之间循环
        setMode(stack, mode);

        String modeName = getModeName(mode);
        player.addChatMessage(
            new ChatComponentText(
                StatCollector.translateToLocal("item.gtit.telekinesis_ore_scanner_core.switch") + " " + modeName));
    }

    /**
     * 执行扫描（核心逻辑）
     * <p>
     * 直接调用 VisualProspecting_API 原生方法获取矿脉数据
     */
    private void performScan(ItemStack stack, World world, EntityPlayer player) {
        // 检查 VisualProspecting 是否安装
        if (!Mods.VisualProspecting.isModLoaded()) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + StatCollector
                        .translateToLocal("item.gtit.telekinesis_ore_scanner_core.visualprospecting.required")));
            return;
        }

        // 检查饥饿值
        if (!player.capabilities.isCreativeMode) {
            int foodLevel = player.getFoodStats()
                .getFoodLevel();

            if (foodLevel < HUNGER_COST) {
                player.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("item.gtit.telekinesis_ore_scanner_core.hunger")));
                return;
            }

            // 消耗饥饿值（1.7.10没有saturation API，使用addExhaustion模拟饱和度消耗）
            player.getFoodStats()
                .addExhaustion(HUNGER_COST * 4.0f);
        }

        int mode = getMode(stack);

        // 调用 VisualProspecting_API 原生方法
        try {
            sendToVisualProspecting(world, player, mode);

            // 显示扫描成功信息
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN
                        + StatCollector.translateToLocal("item.gtit.telekinesis_ore_scanner_core.scan.success")));
        } catch (Exception e) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED
                        + StatCollector.translateToLocal("item.gtit.telekinesis_ore_scanner_core.scan.error")));
            e.printStackTrace();
        }
    }

    /**
     * 调用 VisualProspecting_API 发送探矿数据
     */
    private void sendToVisualProspecting(World world, EntityPlayer player, int mode) {
        int radius = SCAN_RADIUS * 16; // 区块*16 = 方块

        if (mode == MODE_ORES) {
            // 矿石模式：调用 prospectOreVeinsWithinRadius
            VisualProspecting_API.LogicalServer.sendProspectionResultsToClient(
                (EntityPlayerMP) player,
                VisualProspecting_API.LogicalServer.prospectOreVeinsWithinRadius(
                    world.provider.dimensionId,
                    (int) player.posX,
                    (int) player.posZ,
                    radius),
                new ArrayList<>());
        } else if (mode == MODE_FLUIDS) {
            // 流体模式：调用 prospectUndergroundFluidsWithingRadius
            VisualProspecting_API.LogicalServer.sendProspectionResultsToClient(
                (EntityPlayerMP) player,
                new ArrayList<>(),
                VisualProspecting_API.LogicalServer
                    .prospectUndergroundFluidsWithingRadius(world, (int) player.posX, (int) player.posZ, radius));
        }
    }

    // ==================== NBT数据管理 ====================

    public int getMode(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        if (!nbt.hasKey(NBT_MODE)) {
            nbt.setInteger(NBT_MODE, MODE_ORES); // 默认矿石模式
        }
        return nbt.getInteger(NBT_MODE);
    }

    public void setMode(ItemStack stack, int mode) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        nbt.setInteger(NBT_MODE, mode);
    }

    /**
     * 获取模式名称
     */
    private String getModeName(int mode) {
        return switch (mode) {
            case MODE_ORES -> StatCollector.translateToLocal("item.gtit.telekinesis_ore_scanner_core.mode.0");
            case MODE_FLUIDS -> StatCollector.translateToLocal("item.gtit.telekinesis_ore_scanner_core.mode.1");
            default -> "Unknown";
        };
    }

    // ==================== 物品提示信息 ====================

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, java.util.List list, boolean par4) {
        list.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.gtit.telekinesis_ore_scanner_core.desc.1"));
        list.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.gtit.telekinesis_ore_scanner_core.desc.2"));
        list.add(EnumChatFormatting.GRAY + "Mode: " + EnumChatFormatting.YELLOW + getModeName(getMode(stack)));
        list.add(EnumChatFormatting.GRAY + "Range: " + EnumChatFormatting.AQUA + "19x19 chunks (UHV)");

        if (Mods.VisualProspecting.isModLoaded()) {
            list.add(EnumChatFormatting.GREEN + "✓ VisualProspecting/JourneyMap");
        } else {
            list.add(EnumChatFormatting.DARK_GRAY + "○ VisualProspecting required");
        }

        list.add(EnumChatFormatting.ITALIC.toString() + EnumChatFormatting.DARK_GRAY + "Shift+Right to switch mode");
    }
}
