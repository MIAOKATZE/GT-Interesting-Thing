package com.miaokatze.gtit.common.items.infinitycell;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.util.PlayerLookup;

import appeng.api.util.DimensionalCoord;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 旧无限元件迁移提醒：每 10 分钟向「打开过装有旧元件的驱动器/箱子」的玩家播报一次，
 * 消息里列出这些元件所在坐标。播报范围按归属过滤，不做全服广播，避免泄露他人仓库位置。
 */
public class LegacyCellReminderScheduler {

    private static final Logger LOG = LogManager.getLogger("gtit");

    public static final LegacyCellReminderScheduler INSTANCE = new LegacyCellReminderScheduler();

    /** 巡检间隔（tick），与 NekoNotificationScheduler 同口径 10 秒 */
    private static final int CHECK_INTERVAL = 200;
    /** 播报间隔（tick），10 分钟 */
    private static final int REMIND_INTERVAL = 12000;
    /** 单条消息最多列举的坐标数，超出折叠为计数 */
    private static final int MAX_LINES = 6;

    private int tickCounter = 0;
    private int sinceLastNotice = 0;

    /** diskuuid → 最近一次被驱动器/箱子装载的位置 */
    private final Map<String, DimensionalCoord> locations = new ConcurrentHashMap<>();
    /** diskuuid → 打开过该容器 GUI 的玩家名 */
    private final Map<String, Set<String>> owners = new ConcurrentHashMap<>();

    private LegacyCellReminderScheduler() {}

    /**
     * 元件被驱动器/箱子取用时就地登记位置。
     * container 为 null（IO 端口、tooltip）或客户端路径一律跳过。
     */
    public void observe(ItemStack cell, Object container) {
        try {
            if (container == null || !(container instanceof TileEntity te)) return;
            if (FMLCommonHandler.instance()
                .getEffectiveSide()
                .isClient()) return;
            final String uuid = cellUuid(cell);
            if (uuid == null) return;
            final DimensionalCoord current = new DimensionalCoord(te);
            final DimensionalCoord previous = locations.put(uuid, current);
            if (previous != null && !samePlace(previous, current)) {
                // 元件被搬进新容器：旧归属者再收提示就会读到别人的仓库坐标，归属重新计时
                owners.remove(uuid);
            }
        } catch (Throwable t) {
            LOG.warn("旧无限元件位置登记失败", t);
        }
    }

    private static boolean samePlace(DimensionalCoord a, DimensionalCoord b) {
        return a.getDimension() == b.getDimension() && a.x == b.x && a.y == b.y && a.z == b.z;
    }

    /** 玩家打开容器 GUI → 该容器内所有旧元件都把这名玩家记为归属者。 */
    public void observeOwner(EntityPlayerMP player, TileEntity container) {
        try {
            if (player == null || !(container instanceof IInventory inventory)) return;
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                final String uuid = cellUuid(inventory.getStackInSlot(slot));
                if (uuid == null) continue;
                owners.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                    .add(player.getCommandSenderName());
            }
        } catch (Throwable t) {
            LOG.warn("旧无限元件归属登记失败", t);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // 只在 END 阶段累加，避免一个 tick 内 START/END 双触发
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;
        sinceLastNotice += CHECK_INTERVAL;
        if (sinceLastNotice >= REMIND_INTERVAL) {
            sinceLastNotice = 0;
            remindAll();
        }
    }

    private void remindAll() {
        try {
            // 物理端判定会让单机内置服务器整块早退（提醒在单人游戏里永不出现），必须用有效端
            if (FMLCommonHandler.instance()
                .getEffectiveSide()
                .isClient()) return;
            if (locations.isEmpty()) return;

            final Map<String, List<DimensionalCoord>> byOwner = new LinkedHashMap<>();
            final Iterator<Map.Entry<String, DimensionalCoord>> it = locations.entrySet()
                .iterator();
            while (it.hasNext()) {
                final Map.Entry<String, DimensionalCoord> entry = it.next();
                if (!stillPresent(entry.getKey(), entry.getValue())) {
                    it.remove();
                    owners.remove(entry.getKey());
                    continue;
                }
                final Set<String> known = owners.get(entry.getKey());
                if (known == null || known.isEmpty()) continue; // 归属未知就不播，避免暴露他人仓库坐标
                for (String owner : known) {
                    byOwner.computeIfAbsent(owner, k -> new ArrayList<DimensionalCoord>())
                        .add(
                            entry.getValue()
                                .copy());
                }
            }
            if (byOwner.isEmpty()) return;

            PlayerLookup.forEachOnlinePlayer(player -> {
                final List<DimensionalCoord> spots = byOwner.get(player.getCommandSenderName());
                if (spots == null || spots.isEmpty()) return;
                sendNotice(player, spots);
            });
        } catch (Throwable t) {
            LOG.error("[LegacyCellRemind] 旧元件迁移提醒检查异常", t);
        }
    }

    /** 二次校验：坐标处仍是容器、且槽内仍是同一 diskuuid 的旧元件（防区块卸载/元件已迁走后的陈旧记录）。 */
    private boolean stillPresent(String uuid, DimensionalCoord spot) {
        try {
            final World world = spot.getWorld();
            if (world == null) return false;
            // 1.7.10 的 getTileEntity 会 getChunkFromChunkCoords 强制载块；未加载就当"无法判定"留着，
            // 既不为验证而拖服务器加载远端区块，也不把仅因未加载而查不到的元件误判为已迁走
            if (!world.getChunkProvider()
                .chunkExists(spot.x >> 4, spot.z >> 4)) return true;
            final TileEntity te = world.getTileEntity(spot.x, spot.y, spot.z);
            if (!(te instanceof IInventory inventory)) return false;
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                if (uuid.equals(cellUuid(inventory.getStackInSlot(slot)))) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private void sendNotice(EntityPlayerMP player, List<DimensionalCoord> spots) {
        final List<IChatComponent> lines = new ArrayList<>();
        lines.add(
            new ChatComponentText(
                EnumChatFormatting.RED + "[GTIT] "
                    + EnumChatFormatting.GRAY
                    + "你的 ME 网络里还有 "
                    + EnumChatFormatting.YELLOW
                    + spots.size()
                    + EnumChatFormatting.GRAY
                    + " 处 [OLD] 旧无限元件，请改用「猫猫无限存储单元」"));
        final int shown = Math.min(spots.size(), MAX_LINES);
        for (int i = 0; i < shown; i++) {
            lines.add(
                new ChatComponentText(
                    EnumChatFormatting.DARK_GRAY + "  - "
                        + EnumChatFormatting.GRAY
                        + spots.get(i)
                            .getGuiText()));
        }
        if (spots.size() > shown) {
            lines.add(
                new ChatComponentText(EnumChatFormatting.DARK_GRAY + "  ... 其余 " + (spots.size() - shown) + " 处省略"));
        }
        lines.add(new ChatComponentText(EnumChatFormatting.GRAY + "把旧元件插入 §bME-IO 端口§7（方向：元件 → 网络）转空，两枚旧元件可在工作台合成新单元"));
        for (IChatComponent line : lines) {
            try {
                player.addChatComponentMessage(line);
            } catch (Throwable t) {
                // 单个玩家发送失败不影响其余玩家
                LOG.debug("旧元件提醒发送失败：{}", player.getCommandSenderName(), t);
            }
        }
    }

    /** 停服/换档复位，防止跨存档残留坐标。 */
    public static void reset() {
        INSTANCE.locations.clear();
        INSTANCE.owners.clear();
        INSTANCE.sinceLastNotice = 0;
    }

    /** 仅统计旧两枚；新单元不触发提醒。返回 null 表示不是旧元件或无 UUID。 */
    private static String cellUuid(ItemStack stack) {
        if (stack == null) return null;
        final Object item = stack.getItem();
        if (!(item instanceof IInfinityCellItem) || item instanceof ItemNekoInfinityStorageUnit) return null;
        final NBTTagCompound data = stack.stackTagCompound;
        if (data == null) return null;
        final String uuid = data.getString(InfinityCellConstants.DISKUUID);
        return uuid == null || uuid.isEmpty() ? null : uuid;
    }
}
