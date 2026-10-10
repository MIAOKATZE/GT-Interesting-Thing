package com.miaokatze.gtit.hologram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;

import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructableProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.ICustomBlockSetting;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.interfaces.IHeatingCoil;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.blocks.BlockCasings5;
import gregtech.common.blocks.ItemMachines;

/** Server-owned previews and bounded jobs. Client cells are never accepted as world operations. */
public final class HologramService {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final int BUDGET = 8;
    private static final long TIME_BUDGET = 2000000L;
    private static final String TOOL_ID = "gtitHologramToolId";
    private static final int PREPARE_TICKS = 8;
    private static final int CAPTURE_ATTEMPTS = 3;

    private HologramService() {}

    public static void open(EntityPlayerMP player, int x, int y, int z, int side) {
        Session session = new Session(player, x, y, z, true, side);
        SESSIONS.put(player.getUniqueID(), session);
        scan(session);
        send(session);
    }

    public static void openAir(EntityPlayerMP player) {
        Session previous = SESSIONS.get(player.getUniqueID());
        if (previous != null && previous.target != null && player.worldObj.getTotalWorldTime() - previous.openTick <= 2)
            return;
        Session session = new Session(player, 0, 0, 0, false, 2);
        session.status = "请右键多方块控制器选择目标";
        SESSIONS.put(player.getUniqueID(), session);
        send(session);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    public static void handle(EntityPlayerMP player, NBTTagCompound action) {
        Session s = SESSIONS.get(player.getUniqueID());
        if (s == null || !s.id.equals(action.getString("session"))) return;
        if (action.hasKey("uiSequence")) s.uiSequence = action.getLong("uiSequence");
        long now = player.worldObj.getTotalWorldTime();
        if (s.actionTick != now) {
            s.actionTick = now;
            s.actionCount = 0;
        }
        if (s.actionCount++ >= 8) return;
        if (!valid(s)) {
            s.job = 4;
            cancelCaptureRetry(s);
            clearPending(s);
            s.status = "目标、维度、距离或持有工具已变化";
            send(s);
            return;
        }
        String op = action.getString("op");
        if ("pause".equals(op) && s.job == 1) {
            s.job = 2;
            clearPending(s);
            s.status = "已暂停";
        } else if ("cancel".equals(op)) {
            s.job = 4;
            cancelCaptureRetry(s);
            clearPending(s);
            s.status = "已取消";
        } else if ("resume".equals(op) && s.job == 2) {
            s.job = 1;
            s.status = "继续施工";
        } else if ("scan".equals(op) && s.job != 1 && s.job != 2) scan(s);
        else if ("configure".equals(op) && s.job != 1 && s.job != 2) {
            int requestedFacing = action.getInteger("facing");
            if (!action.hasKey("main", 3) || action.getInteger("main") < 1
                || !validChannels(action.getCompoundTag("channels"))
                || (s.capabilities != null
                    && !s.capabilities.validConfiguration(action.getInteger("main"), action.getCompoundTag("channels")))
                || action.getInteger("mode") < 0
                || action.getInteger("mode") > 2
                || action.getInteger("scope") < 0
                || action.getInteger("scope") > 3
                || action.getInteger("layer") < -128
                || action.getInteger("layer") > 128
                || (s.machine != null && requestedFacing != s.machine.getExtendedFacing()
                    .ordinal())) {
                s.status = "参数无效，配置未修改";
                send(s);
                return;
            }
            boolean geometryChanged = s.main != action.getInteger("main")
                || !s.channels.equals(action.getCompoundTag("channels"))
                || s.noHatches != action.getBoolean("noHatches")
                || (s.machine != null && s.facing != s.machine.getExtendedFacing()
                    .ordinal());
            if (s.main != action.getInteger("main")) s.mainExplicit = true;
            s.main = action.getInteger("main");
            s.channels = boundedChannels(action.getCompoundTag("channels"));
            s.noHatches = action.getBoolean("noHatches");
            s.mode = clamp(action.getInteger("mode"), 0, 2);
            s.scope = clamp(action.getInteger("scope"), 0, 3);
            s.layer = clamp(action.getInteger("layer"), -128, 128);
            s.selected = action.getInteger("selected");
            if (geometryChanged || s.capturePending) scan(s);
            else {
                s.job = 0;
                refreshPlan(s);
            }
            persist(s);
        } else if ("start".equals(op) && s.job != 1 && s.job != 2) {
            if (s.capturePending) s.status = "正在采集完整结构，请等待自动重试回执";
            else if (s.capture == null || s.capture.incomplete) s.status = "该机器无法完整安全采集，请使用原版提示或搭建";
            else if ((s.scope == 2 || s.scope == 3) && (s.selected < 0 || s.selected >= s.capture.cells.size()))
                s.status = "请先选择施工范围的结构格";
            else if (s.scope == 3 && s.capture.cells.get(s.selected).family.isEmpty()) s.status = "选中格没有安全同族适配，请使用单格范围";
            else if (s.machine.getExtendedFacing()
                .ordinal() != s.facing) {
                    scan(s);
                    s.status = "控制器朝向已变化，请重新确认计划";
                } else if (action.getLong("planRevision") != s.planRevision) s.status = "确认计划已过期，请重新核对差分";
            else if (!planCurrent(s)) {
                refreshPlan(s);
                s.status = "现场已变化，请重新确认更新后的差分";
            } else {
                refreshResources(s);
                if (s.planMissing > 0 || !s.planRecoveryFits) {
                    s.status = s.planMissing > 0 ? "整份计划材料不足，请补齐后重新确认" : "背包无法容纳预计回收，请整理后重新确认";
                    send(s);
                    return;
                }
                s.cursor = 0;
                s.completed = 0;
                s.defer = false;
                s.stopTick = false;
                clearPending(s);
                s.job = 1;
                s.status = "正在逐格施工";
            }
        } else if ("native".equals(op) && s.job != 1 && s.job != 2) {
            if (player.capabilities.isCreativeMode && s.constructable != null && !s.noHatches) {
                s.constructable.construct(trigger(s), false);
                scan(s);
                s.status = "沿用原版机器创造构造行为；不属于高级安全差分施工";
            } else if (s.constructable instanceof ISurvivalConstructable) {
                IItemSource nativeSource = player.capabilities.isCreativeMode
                    ? new CreativeSource(Collections.emptyList())
                    : IItemSource.fromPlayer(player);
                if (s.noHatches) nativeSource = new ShellSource(nativeSource);
                int result = ((ISurvivalConstructable) s.constructable)
                    .survivalConstruct(trigger(s), BUDGET, ISurvivalBuildEnvironment.create(nativeSource, player));
                scan(s);
                s.status = result == -2 ? "原版生存搭建不支持此机器" : "沿用原版机器生存构造行为；不属于高级安全差分施工，请重新扫描";
            } else s.status = "该机器未提供原版生存搭建接口";
        } else if ("hints".equals(op)) {
            s.hints = true;
            s.status = "显示原版结构提示";
        } else if ("pin".equals(op) && s.job != 1 && s.job != 2) {
            if (s.capturePending) {
                s.status = "正在采集中，请等待完整结构后选择具体目标";
                send(s);
                return;
            }
            int index = action.getInteger("selected"),
                choice = action.hasKey("choiceindex") ? action.getInteger("choiceindex") : action.getInteger("choice");
            if (s.capture != null && index >= 0 && index < s.capture.cells.size()) {
                List<ItemStack> options = candidates(s, s.capture.cells.get(index));
                if (choice >= 0 && choice < options.size()) {
                    HologramCapture.Cell cell = s.capture.cells.get(index);
                    ItemStack chosen = options.get(choice);
                    if (s.mode == 1 && coil(cell.block, cell.meta)
                        && !same(chosen, new ItemStack(cell.block, 1, cell.meta))) s.status = "线圈替换目标由线圈信道统一指定";
                    else {
                        s.pins.put(index, chosen.copy());
                        refreshPlan(s);
                        s.status = "具体目标已指定，请确认更新后的差分计划";
                    }
                }
            }
        } else if (s.job == 2 && ("scan".equals(op) || "configure".equals(op) || "native".equals(op)))
            s.status = "暂停任务保留进度；请先取消再改变配置或重新扫描";
        send(s);
    }

    public static void tick() {
        Iterator<Session> sessions = SESSIONS.values()
            .iterator();
        while (sessions.hasNext()) {
            Session s = sessions.next();
            if (s.player.playerNetServerHandler == null || s.player.playerNetServerHandler.netManager == null
                || !s.player.playerNetServerHandler.netManager.isChannelOpen()) {
                clearPending(s);
                cancelCaptureRetry(s);
                sessions.remove();
                continue;
            }
            if (s.capturePending) {
                if (!valid(s) || s.machine == null
                    || s.machine.getExtendedFacing()
                        .ordinal() != s.retryFacing
                    || !captureConfigurationCurrent(s)) {
                    cancelCaptureRetry(s);
                    clearPending(s);
                    s.job = 4;
                    s.status = "工具、目标、控制器朝向或配置已变化，自动采集已停止";
                    send(s);
                } else if (s.player.worldObj.getTotalWorldTime() >= s.captureRetryDue) {
                    scan(s, false);
                    send(s);
                }
                continue;
            }
            if (s.job != 1) continue;
            if (!valid(s) || s.machine == null
                || s.machine.getExtendedFacing()
                    .ordinal() != s.facing) {
                clearPending(s);
                s.job = 4;
                s.status = "工具、目标或控制器朝向已变化，请重新采集";
                send(s);
                continue;
            }
            long now = s.player.worldObj.getTotalWorldTime();
            if (s.pending >= 0) {
                if (now < s.due) continue;
                int index = s.pending;
                HologramCapture.Cell cell = s.capture.cells.get(index);
                boolean unchanged = expectedCurrent(s, index);
                boolean success = unchanged && operate(s, cell, index);
                if (!success) {
                    if (!unchanged) s.status = "待施工格现场已变化，请取消后重新确认计划";
                    if (s.defer) s.defer = false;
                    else s.job = 2;
                } else {
                    s.cursor = index + 1;
                    if ("placed".equals(cell.status) || "replaced".equals(cell.status) || "removed".equals(cell.status))
                        s.completed++;
                }
                s.ackIndex = index;
                s.ackSuccess = success && ("placed".equals(cell.status) || "replaced".equals(cell.status)
                    || "removed".equals(cell.status));
                s.pending = -1;
                s.phase = "ACK";
                send(s);
                continue;
            }
            long deadline = System.nanoTime() + TIME_BUDGET;
            int visited = 0;
            while (s.cursor < s.capture.cells.size() && visited++ < 128 && System.nanoTime() < deadline) {
                int index = s.cursor;
                HologramCapture.Cell cell = s.capture.cells.get(index);
                if (!inScope(s, cell, index) || !"pending".equals(s.planStatus.get(index))) {
                    s.cursor++;
                    continue;
                }
                s.pending = index;
                s.due = now + PREPARE_TICKS;
                s.phase = "PREPARE";
                s.ackIndex = -1;
                s.ackSuccess = false;
                s.batchSeq++;
                send(s);
                break;
            }
            if (s.cursor >= s.capture.cells.size()) {
                int unresolved = 0;
                for (int i = 0; i < s.capture.cells.size(); i++) if (inScope(s, s.capture.cells.get(i), i)) {
                    String status = s.capture.cells.get(i).status;
                    if (!"satisfied".equals(status) && !"placed".equals(status)
                        && !"replaced".equals(status)
                        && !"removed".equals(status)) unresolved++;
                }
                clearPending(s);
                s.job = unresolved == 0 ? 3 : 5;
                s.status = unresolved == 0 ? "范围内施工完成（未执行机器成型检查）" : "部分完成：受保护或未知接口待手动处理，可调整配置";
                if (s.noHatches) s.status += "；无仓室模式不保证机器成型";
                send(s);
            }
        }
    }

    private static void clearPending(Session s) {
        s.pending = -1;
        s.due = 0;
        s.phase = "IDLE";
        s.ackIndex = -1;
        s.ackSuccess = false;
    }

    private static boolean valid(Session s) {
        ItemStack held = s.player.getHeldItem();
        return !s.player.isDead && s.player.dimension == s.dimension
            && held != null
            && held.hasTagCompound()
            && s.toolId.equals(
                held.getTagCompound()
                    .getString(TOOL_ID))
            && held.getItem() == s.tool
            && s.player.inventory.currentItem == s.slot
            && (s.target == null || (s.player.getDistanceSq(s.x + .5, s.y + .5, s.z + .5) <= 4096
                && s.player.worldObj.blockExists(s.x, s.y, s.z)
                && s.player.worldObj.getTileEntity(s.x, s.y, s.z) == s.target
                && (!(s.target instanceof IGregTechTileEntity)
                    || ((IGregTechTileEntity) s.target).getMetaTileEntity() == s.context)));
    }

    private static boolean inScope(Session s, HologramCapture.Cell c, int index) {
        return s.scope == 0 || (s.scope == 1 && c.y - s.y == s.layer)
            || (s.scope == 2 && index == s.selected)
            || (s.scope == 3 && s.selected >= 0
                && s.selected < s.capture.cells.size()
                && !c.family.isEmpty()
                && c.family.equals(s.capture.cells.get(s.selected).family));
    }

    private static boolean trivial(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return w.isAirBlock(x, y, z)
            || (b instanceof BlockBush && b.isReplaceable(w, x, y, z) && w.getTileEntity(x, y, z) == null);
    }

    private static boolean coil(Block b, int meta) {
        return b == GregTechAPI.sBlockCasings5 && meta >= 0
            && meta < 16
            && BlockCasings5.getCoilHeatFromDamage(meta) != HeatingCoilLevel.None;
    }

    private static boolean knownSatisfied(HologramCapture.Cell c, Block old, int meta) {
        if (old == c.block && meta == c.meta) return true;
        if (old instanceof IHeatingCoil && coil(c.block, c.meta)
            && factory(c.element, "ofCoil", new IdentityHashMap<>(), 0)) {
            try {
                return ((IHeatingCoil) old).getCoilHeat(meta) == BlockCasings5.getCoilHeatFromDamage(c.meta);
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
        }
        return false;
    }

    /** A capture position plus a real valid coil family is the deliberately narrow destructive adapter. */
    private static boolean destructive(HologramCapture.Cell c, Block old, int meta) {
        HologramReplacementFamily.Family family = HologramReplacementFamily.resolve(c.element, c.block, c.meta);
        if (family != null && family.contains(old, meta)) return true;
        if (coil(c.block, c.meta) && coil(old, meta)) return factory(c.element, "ofCoil", new IdentityHashMap<>(), 0);
        return old == c.block && meta == c.meta
            && old.getClass()
                .getName()
                .startsWith("gregtech.common.blocks.BlockCasings")
            && factory(c.element, "ofBlock", new IdentityHashMap<>(), 0);
    }

    /**
     * Identify actual factory provenance through element delegates, rather than treating a visual hint as authority.
     */
    private static boolean factory(Object value, String name, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (value == null || depth > 12 || seen.put(value, Boolean.TRUE) != null) return false;
        Class<?> type = value.getClass();
        java.lang.reflect.Method enclosing = type.getEnclosingMethod();
        if (enclosing != null && enclosing.getName()
            .equals(name)
            && enclosing.getDeclaringClass()
                .getName()
                .equals(
                    name.equals("ofCoil") ? "gregtech.api.util.GTStructureUtility"
                        : "com.gtnewhorizon.structurelib.structure.StructureUtility"))
            return true;
        for (java.lang.reflect.Field field : type.getDeclaredFields())
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                && IStructureElement.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        if (factory(field.get(value), name, seen, depth + 1)) return true;
                    } catch (IllegalAccessException | RuntimeException ignored) {}
                }
        if (value instanceof IStructureElementChain)
            for (Object child : ((IStructureElementChain<?>) value).fallbacks())
                if (factory(child, name, seen, depth + 1)) return true;
        return false;
    }

    private static ItemStack targetFor(Session s, HologramCapture.Cell c, int index) {
        if (index >= 0 && index < s.planTargets.size()) {
            ItemStack target = s.planTargets.get(index);
            return target == null ? null : target.copy();
        }
        return draftTarget(s, c, index);
    }

    private static ItemStack draftTarget(Session s, HologramCapture.Cell c, int index) {
        ItemStack pin = s.pins.get(index);
        if (pin == null && s.scope == 3
            && s.selected >= 0
            && s.selected < s.capture.cells.size()
            && !c.family.isEmpty()
            && c.family.equals(s.capture.cells.get(s.selected).family)) pin = s.pins.get(s.selected);
        if (pin != null && !(s.mode == 1 && coil(c.block, c.meta))) return pin.copy();
        if (!s.noHatches && s.channels.hasKey("gt_hatch", 3) && "hatch".equals(c.role)) {
            ItemStack first = null, available = null;
            int firstTier = Integer.MAX_VALUE, availableTier = Integer.MAX_VALUE;
            for (ItemStack option : candidates(s, c)) {
                if (!(option.getItem() instanceof ItemMachines)) continue;
                int id = option.getItemDamage();
                if (id < 0 || id >= GregTechAPI.METATILEENTITIES.length
                    || !(GregTechAPI.METATILEENTITIES[id] instanceof MTEHatch)) continue;
                int tier = ((MTEHatch) GregTechAPI.METATILEENTITIES[id]).mTier;
                if (tier < firstTier) {
                    first = option;
                    firstTier = tier;
                }
                if (tier < availableTier && (s.player.capabilities.isCreativeMode
                    || inventorySource(s.player).takeOne(a -> same(a, option), true) != null)) {
                    available = option;
                    availableTier = tier;
                }
            }
            if (available != null || first != null) return (available == null ? first : available).copy();
        }
        if (c.block != null && c.block != Blocks.air) {
            ItemStack block = new ItemStack(c.block, 1, c.meta);
            if (block.getItem() != null) return block;
        }
        List<ItemStack> options = candidates(s, c);
        return options.isEmpty() ? null
            : options.get(0)
                .copy();
    }

    private static boolean explicitTarget(Session s, HologramCapture.Cell c, int index) {
        return s.pins.containsKey(index) || (s.scope == 3 && s.pins.containsKey(s.selected)
            && s.selected >= 0
            && s.selected < s.capture.cells.size()
            && !c.family.isEmpty()
            && c.family.equals(s.capture.cells.get(s.selected).family));
    }

    private static boolean explicitGrade(Session s, HologramCapture.Cell c) {
        if (s.mainExplicit) return true;
        if (c.family.equals("coil")) return s.channels.hasKey("coil", 3);
        if (c.family.startsWith("glass:")) return s.channels.hasKey("glass", 3);
        return c.family.startsWith("tiered:")
            && (s.channels.hasKey("item_pipe", 3) || s.channels.hasKey("coke_oven_casing", 3));
    }

    private static boolean targetSatisfied(Session s, HologramCapture.Cell c, int index, Block old, int meta) {
        if (explicitGrade(s, c) || explicitTarget(s, c, index)) return targetMatches(s, c, index, old, meta);
        HologramReplacementFamily.Family family = HologramReplacementFamily.resolve(c.element, c.block, c.meta);
        if (family != null && family.contains(old, meta)) return true;
        return knownSatisfied(c, old, meta);
    }

    private static boolean targetMatches(Session s, HologramCapture.Cell c, int index, Block old, int meta) {
        ItemStack wanted = targetFor(s, c, index);
        if (wanted == null) return false;
        if (wanted != null && wanted.getItem() instanceof ItemMachines) {
            TileEntity tile = s.player.worldObj.getTileEntity(c.x, c.y, c.z);
            return old == Block.getBlockFromItem(wanted.getItem()) && tile instanceof IGregTechTileEntity
                && ((IGregTechTileEntity) tile).getMetaTileID() == wanted.getItemDamage();
        }
        return old == Block.getBlockFromItem(wanted.getItem()) && meta == wanted.getItem()
            .getMetadata(wanted.getItemDamage());
    }

    private static ItemStack elementTrigger(Session s, HologramCapture.Cell c) {
        ItemStack result = trigger(s);
        if (c.elementTrigger != null) {
            // Preserve the current inventory tool's identity/unrelated NBT while retaining the real piece signal.
            result.stackSize = c.elementTrigger.stackSize;
            List<String> existing = new ArrayList<>();
            ChannelDataAccessor.iterateChannelData(result)
                .forEach(entry -> existing.add(entry.getKey()));
            for (String name : existing) ChannelDataAccessor.unsetChannelData(result, name);
            ChannelDataAccessor.iterateChannelData(c.elementTrigger)
                .forEach(entry -> ChannelDataAccessor.setChannelData(result, entry.getKey(), entry.getValue()));
        }
        // A concrete new hatch pin authorizes that item even when the automatic hatch toggle is off.
        if (!s.noHatches && "hatch".equals(c.role)) {
            int index = s.capture == null ? -1 : s.capture.cells.indexOf(c);
            if (index >= 0 && explicitTarget(s, c, index)) {
                ItemStack target = targetFor(s, c, index);
                if (target != null && target.getItem() instanceof ItemMachines)
                    ChannelDataAccessor.setChannelData(result, "gt_hatch", 1);
            }
        }
        return result;
    }

    /** Pure preflight: never posts Forge events or calls element.check/checkMachine. */
    private static String classify(Session s, HologramCapture.Cell c, int index) {
        World w = s.player.worldObj;
        if (!w.blockExists(c.x, c.y, c.z)) return "unsupported";
        if (c.x == s.x && c.y == s.y && c.z == s.z) return "satisfied";
        if (w.getTileEntity(c.x, c.y, c.z) != null) return "protected";
        if (!w.canMineBlock(s.player, c.x, c.y, c.z) || !s.player.canPlayerEdit(c.x, c.y, c.z, 1, trigger(s)))
            return "protected";
        boolean empty = trivial(w, c.x, c.y, c.z);
        if (c.element == null || (c.unknown && !(empty && (explicitTarget(s, c, index)
            || (!s.noHatches && s.channels.hasKey("gt_hatch", 3) && "hatch".equals(c.role)))))) return "unsupported";
        Block old = w.getBlock(c.x, c.y, c.z);
        int meta = w.getBlockMetadata(c.x, c.y, c.z);
        if (s.mode == 2) return empty ? "satisfied" : destructive(c, old, meta) ? "pending" : "protected";
        if (targetSatisfied(s, c, index, old, meta)) return "satisfied";
        ItemStack target = targetFor(s, c, index);
        if (target == null || Block.getBlockFromItem(target.getItem()) == Blocks.air) return "unsupported";
        if (explicitTarget(s, c, index)) {
            boolean candidate = false;
            for (ItemStack option : candidates(s, c)) if (same(option, target)) {
                candidate = true;
                break;
            }
            if (!candidate) return "unsupported";
        }
        if (empty) return "pending";
        if (s.mode == 0 || !destructive(c, old, meta)) return "protected";
        HologramReplacementFamily.Family family = HologramReplacementFamily.resolve(c.element, c.block, c.meta);
        return family != null && family.canTarget(target) ? "pending" : "protected";
    }

    private static String reason(Session s, HologramCapture.Cell c, int index) {
        World w = s.player.worldObj;
        if (!w.blockExists(c.x, c.y, c.z)) return "区块未加载";
        if (c.x == s.x && c.y == s.y && c.z == s.z) return "控制器保留";
        if (w.getTileEntity(c.x, c.y, c.z) != null) return "已有设备无安全迁移适配器，保留内容与配置";
        if ("unsupported".equals(c.status) || c.element == null) return "此元素未提供安全施工目标";
        if ("protected".equals(c.status)) return "权限限制或旧块不属于受支持结构族";
        if ("satisfied".equals(c.status)) return "现有方块满足目标，保留";
        return "逐格重验；Forge 事件仅在实际操作时处理";
    }

    private static void refreshPlan(Session s) {
        s.job = 0;
        s.cursor = 0;
        s.completed = 0;
        s.defer = false;
        s.stopTick = false;
        clearPending(s);
        s.status = "差分计划已更新，请核对材料、回收与保护原因后确认";
        s.planRevision++;
        s.expectedBlocks.clear();
        s.expectedMeta.clear();
        s.expectedTiles.clear();
        s.planStatus.clear();
        s.planTargets.clear();
        s.planMissing = 0;
        s.planRecoveryFits = true;
        if (s.capture == null) return;
        World w = s.player.worldObj;
        for (int i = 0; i < s.capture.cells.size(); i++) s.planTargets.add(draftTarget(s, s.capture.cells.get(i), i));
        for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell c = s.capture.cells.get(i);
            boolean loaded = w.blockExists(c.x, c.y, c.z);
            s.expectedBlocks.add(loaded ? w.getBlock(c.x, c.y, c.z) : Blocks.air);
            s.expectedMeta.add(loaded ? w.getBlockMetadata(c.x, c.y, c.z) : 0);
            s.expectedTiles.add(loaded ? w.getTileEntity(c.x, c.y, c.z) : null);
            c.status = classify(s, c, i);
            s.planStatus.add(c.status);
        }
        refreshResources(s);
    }

    private static void refreshResources(Session s) {
        s.planMissing = 0;
        s.planRecoveryFits = true;
        if (s.capture == null) return;
        World w = s.player.worldObj;
        Map<String, ItemStack> stacks = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<ItemStack> recovery = new ArrayList<>();
        for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell c = s.capture.cells.get(i);
            if (!inScope(s, c, i) || !"pending".equals(c.status) || s.player.capabilities.isCreativeMode) continue;
            if (s.mode != 2) {
                ItemStack target = targetFor(s, c, i);
                String key = stackKey(target);
                stacks.put(key, target);
                counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
            }
            if (!trivial(w, c.x, c.y, c.z)) try {
                recovery.addAll(
                    w.getBlock(c.x, c.y, c.z)
                        .getDrops(w, c.x, c.y, c.z, w.getBlockMetadata(c.x, c.y, c.z), 0));
            } catch (RuntimeException | LinkageError failure) {
                s.planRecoveryFits = false;
            }
        }
        for (Map.Entry<String, ItemStack> entry : stacks.entrySet()) {
            ItemStack stack = entry.getValue();
            int available = inventorySource(s.player).take(a -> same(a, stack), true, 4096)
                .values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();
            s.planMissing += Math.max(0, counts.get(entry.getKey()) - available);
        }
        s.planRecoveryFits &= canFit(s.player, recovery);
    }

    private static String stackKey(ItemStack stack) {
        return stack.getItem() + ":" + stack.getItemDamage() + ":" + stack.getTagCompound();
    }

    private static boolean expectedCurrent(Session s, int index) {
        HologramCapture.Cell c = s.capture.cells.get(index);
        World w = s.player.worldObj;
        return w.blockExists(c.x, c.y, c.z) && w.getBlock(c.x, c.y, c.z) == s.expectedBlocks.get(index)
            && w.getBlockMetadata(c.x, c.y, c.z) == s.expectedMeta.get(index)
            && w.getTileEntity(c.x, c.y, c.z) == s.expectedTiles.get(index);
    }

    private static boolean planCurrent(Session s) {
        if (s.capture == null || s.expectedBlocks.size() != s.capture.cells.size()) return false;
        for (int i = 0; i < s.capture.cells.size(); i++)
            if (inScope(s, s.capture.cells.get(i), i) && !expectedCurrent(s, i)) return false;
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean operate(Session s, HologramCapture.Cell c, int index) {
        World w = s.player.worldObj;
        if (!w.blockExists(c.x, c.y, c.z)) {
            c.status = "unsupported";
            s.status = "区块未加载";
            return false;
        }
        Block old = w.getBlock(c.x, c.y, c.z);
        int oldMeta = w.getBlockMetadata(c.x, c.y, c.z);
        if (w.getTileEntity(c.x, c.y, c.z) != null || (c.x == s.x && c.y == s.y && c.z == s.z)) {
            c.status = "protected";
            return true;
        }
        if (!w.canMineBlock(s.player, c.x, c.y, c.z) || !s.player.canPlayerEdit(c.x, c.y, c.z, 1, trigger(s))) {
            c.status = "protected";
            return true;
        }
        boolean empty = trivial(w, c.x, c.y, c.z);
        if (s.mode == 2 && empty) {
            c.status = "satisfied";
            return true;
        }
        if (s.mode != 2 && targetSatisfied(s, c, index, old, oldMeta)) {
            c.status = "satisfied";
            return true;
        }
        if (!empty && (s.mode == 0 || !destructive(c, old, oldMeta))) {
            c.status = "protected";
            return true;
        }
        if ((c.unknown && !(empty && (explicitTarget(s, c, index)
            || (!s.noHatches && s.channels.hasKey("gt_hatch", 3) && "hatch".equals(c.role))))) || c.element == null
            || targetFor(s, c, index) == null) {
            c.status = "unsupported";
            return true;
        }
        List<ItemStack> drops = empty || s.player.capabilities.isCreativeMode ? Collections.emptyList()
            : old.getDrops(w, c.x, c.y, c.z, oldMeta, 0);
        ItemStack[] inventory = copyInventory(s.player);
        if (!canFit(s.player, drops)) {
            s.status = "背包无法安全回收掉落物";
            return false;
        }
        ItemStack reserved = null;
        if (s.mode == 1 && !empty && !s.player.capabilities.isCreativeMode) {
            ItemStack required = targetFor(s, c, index);
            reserved = inventorySource(s.player).takeOne(stack -> same(stack, required), false);
            if (reserved == null) {
                c.status = "missing";
                s.status = "替换材料不足，旧方块已保留";
                return false;
            }
        }
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(w, c.x, c.y, c.z);
        try {
            if (!empty) {
                BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(c.x, c.y, c.z, w, old, oldMeta, s.player);
                if (MinecraftForge.EVENT_BUS.post(event)) {
                    restoreInventory(s.player, inventory);
                    c.status = "protected";
                    return true;
                }
                old.onBlockHarvested(w, c.x, c.y, c.z, oldMeta, s.player);
                if (!old.removedByPlayer(w, s.player, c.x, c.y, c.z, true)) {
                    snapshot.restore(true, false);
                    restoreInventory(s.player, inventory);
                    c.status = "protected";
                    return true;
                }
                old.onBlockDestroyedByPlayer(w, c.x, c.y, c.z, oldMeta);
            }
            if (s.mode != 2) {
                final ItemStack material = reserved;
                ReservedSource reservation = material == null ? null : new ReservedSource(material);
                IItemSource source = reservation == null ? inventorySource(s.player) : reservation;
                // Lock the pure plan's chosen material. Native auto-selection must not consume a different item.
                ItemStack pin = targetFor(s, c, index);
                if (s.player.capabilities.isCreativeMode) source = new CreativeSource(Collections.singletonList(pin));
                if (pin != null && material == null) source = new FilteredSource(source, pin);
                if (s.noHatches) source = new ShellSource(source);
                boolean directCreative = s.player.capabilities.isCreativeMode && !s.noHatches && pin == null;
                PlaceResult result;
                if (directCreative) result = c.element.placeBlock(s.machine, w, c.x, c.y, c.z, elementTrigger(s, c))
                    ? PlaceResult.ACCEPT
                    : PlaceResult.REJECT;
                else {
                    result = c.element.survivalPlaceBlock(
                        s.machine,
                        w,
                        c.x,
                        c.y,
                        c.z,
                        elementTrigger(s, c),
                        AutoPlaceEnvironment.fromLegacy(source, s.player, s.player::addChatMessage));
                }
                if (result == PlaceResult.STOP) {
                    snapshot.restore(true, false);
                    restoreInventory(s.player, inventory);
                    s.defer = true;
                    c.status = "pending";
                    s.status = "原版元素要求等待下一 tick";
                    return false;
                }
                if (result == PlaceResult.ACCEPT_STOP) s.stopTick = true;
                if ((result != PlaceResult.ACCEPT && result != PlaceResult.ACCEPT_STOP && result != PlaceResult.SKIP)
                    || w.isAirBlock(c.x, c.y, c.z)
                    || (reservation != null && !reservation.used)
                    || (pin != null
                        && !targetMatches(s, c, index, w.getBlock(c.x, c.y, c.z), w.getBlockMetadata(c.x, c.y, c.z)))) {
                    snapshot.restore(true, false);
                    restoreInventory(s.player, inventory);
                    c.status = result == PlaceResult.REJECT_CONTINUE ? "unsupported" : "missing";
                    s.status = "原版元素拒绝施工；材料和旧方块已还原";
                    return false;
                }
                Block placed = w.getBlock(c.x, c.y, c.z);
                if (placed instanceof ICustomBlockSetting || directCreative) {
                    ItemStack placedStack = new ItemStack(placed, 1, w.getBlockMetadata(c.x, c.y, c.z));
                    placed.onBlockPlacedBy(w, c.x, c.y, c.z, s.player, placedStack);
                    placed.onPostBlockPlaced(w, c.x, c.y, c.z, w.getBlockMetadata(c.x, c.y, c.z));
                }
                if (MinecraftForge.EVENT_BUS.post(new BlockEvent.PlaceEvent(snapshot, old, s.player))) {
                    snapshot.restore(true, false);
                    restoreInventory(s.player, inventory);
                    c.status = "protected";
                    return true;
                }
            }
            for (ItemStack drop : drops) if (!s.player.inventory.addItemStackToInventory(drop.copy()))
                throw new IllegalStateException("drop capacity changed");
            s.player.inventory.markDirty();
            c.status = s.mode == 2 ? "removed" : empty ? "placed" : "replaced";
            return true;
        } catch (RuntimeException | LinkageError e) {
            snapshot.restore(true, false);
            restoreInventory(s.player, inventory);
            s.status = "施工异常，已还原该格并暂停";
            return false;
        }
    }

    private static ItemStack[] copyInventory(EntityPlayerMP p) {
        ItemStack[] result = new ItemStack[p.inventory.mainInventory.length];
        for (int i = 0; i < result.length; i++)
            result[i] = p.inventory.mainInventory[i] == null ? null : p.inventory.mainInventory[i].copy();
        return result;
    }

    private static void restoreInventory(EntityPlayerMP p, ItemStack[] saved) {
        for (int i = 0; i < saved.length; i++) {
            ItemStack current = p.inventory.mainInventory[i];
            if (i == p.inventory.currentItem && current != null
                && saved[i] != null
                && current.getItem() == saved[i].getItem()
                && current.hasTagCompound()
                && saved[i].hasTagCompound()
                && current.getTagCompound()
                    .getString(TOOL_ID)
                    .equals(
                        saved[i].getTagCompound()
                            .getString(TOOL_ID))) {
                current.stackSize = saved[i].stackSize;
                current.setItemDamage(saved[i].getItemDamage());
                current.setTagCompound(
                    saved[i].hasTagCompound() ? (NBTTagCompound) saved[i].getTagCompound()
                        .copy() : null);
            } else if (i != p.inventory.currentItem)
                p.inventory.mainInventory[i] = saved[i] == null ? null : saved[i].copy();
        }
        p.inventory.markDirty();
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.getItem() == b.getItem()
            && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static boolean canFit(EntityPlayerMP p, List<ItemStack> drops) {
        ItemStack[] simulation = copyInventory(p);
        for (ItemStack drop : drops) {
            int left = drop.stackSize;
            for (ItemStack slot : simulation) if (same(slot, drop)) {
                int n = Math
                    .min(left, Math.min(slot.getMaxStackSize(), p.inventory.getInventoryStackLimit()) - slot.stackSize);
                slot.stackSize += n;
                left -= n;
            }
            for (int i = 0; i < simulation.length && left > 0; i++) if (simulation[i] == null) {
                simulation[i] = drop.copy();
                simulation[i].stackSize = Math.min(left, drop.getMaxStackSize());
                left -= simulation[i].stackSize;
            }
            if (left > 0) return false;
        }
        return true;
    }

    private static ItemStack trigger(Session s) {
        ItemStack current = currentTool(s);
        ItemStack result = current == null ? new ItemStack(s.tool, 1) : current.copy();
        result.stackSize = s.main;
        List<String> existing = new ArrayList<>();
        ChannelDataAccessor.iterateChannelData(result)
            .forEach(entry -> existing.add(entry.getKey()));
        for (String channel : existing) ChannelDataAccessor.unsetChannelData(result, channel);
        NBTTagCompound active = s.capabilities == null ? s.channels : s.capabilities.sanitizeChannels(s.channels);
        for (Object key : active.func_150296_c())
            ChannelDataAccessor.setChannelData(result, (String) key, active.getInteger((String) key));
        return result;
    }

    private static ItemStack currentTool(Session s) {
        ItemStack held = s.player.getHeldItem();
        return held != null && held.getItem() == s.tool
            && s.player.inventory.currentItem == s.slot
            && held.hasTagCompound()
            && s.toolId.equals(
                held.getTagCompound()
                    .getString(TOOL_ID)) ? held : null;
    }

    private static void persist(Session s) {
        ItemStack held = currentTool(s);
        if (held == null) return;
        held.getTagCompound()
            .setInteger("gtitHologramMain", s.main);
        held.getTagCompound()
            .setBoolean("gtitHologramMainExplicit", s.mainExplicit);
        List<String> existing = new ArrayList<>();
        ChannelDataAccessor.iterateChannelData(held)
            .forEach(entry -> existing.add(entry.getKey()));
        for (String name : existing) ChannelDataAccessor.unsetChannelData(held, name);
        for (Object key : s.channels.func_150296_c())
            ChannelDataAccessor.setChannelData(held, (String) key, s.channels.getInteger((String) key));
        s.player.inventory.markDirty();
    }

    private static NBTTagCompound boundedChannels(NBTTagCompound input) {
        NBTTagCompound result = new NBTTagCompound();
        int count = 0;
        for (Object key : input.func_150296_c()) {
            String name = (String) key;
            if (count++ >= 32) break;
            if (name.matches("[a-z0-9_.-]{1,48}") && input.getInteger(name) > 0)
                result.setInteger(name, input.getInteger(name));
        }
        return result;
    }

    private static boolean validChannels(NBTTagCompound input) {
        if (input.func_150296_c()
            .size() > 32) return false;
        for (Object key : input.func_150296_c()) {
            String name = (String) key;
            if (!name.matches("[a-z0-9_.-]{1,48}") || !input.hasKey(name, 3) || input.getInteger(name) <= 0)
                return false;
        }
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void cancelCaptureRetry(Session s) {
        s.capturePending = false;
        s.captureRetryDue = 0;
        s.retryChannels = null;
    }

    private static boolean captureConfigurationCurrent(Session s) {
        return s.retryChannels != null && s.retryChannels.equals(s.channels)
            && s.retryMain == s.main
            && s.retryNoHatches == s.noHatches
            && s.retryMode == s.mode
            && s.retryScope == s.scope
            && s.retryLayer == s.layer
            && s.retrySelected == s.selected;
    }

    private static void scheduleCaptureRetry(Session s) {
        // A timeout mixed with another failure is not treated as a transient cold-load timeout.
        boolean timeoutOnly = s.capture != null && s.capture.incomplete
            && (s.capture.failure.equals("TIME_LIMIT") || s.capture.failure.startsWith("TIME_LIMIT "));
        if (!timeoutOnly) return;
        if (s.captureAttempts >= CAPTURE_ATTEMPTS) {
            s.status = "结构采集连续 " + CAPTURE_ATTEMPTS + " 次超时，高级施工已阻止；可重新扫描或使用原版提示";
            return;
        }
        s.capturePending = true;
        s.captureRetryDue = s.player.worldObj.getTotalWorldTime() + 2;
        s.retryFacing = s.facing;
        s.retryMain = s.main;
        s.retryChannels = (NBTTagCompound) s.channels.copy();
        s.retryNoHatches = s.noHatches;
        s.retryMode = s.mode;
        s.retryScope = s.scope;
        s.retryLayer = s.layer;
        s.retrySelected = s.selected;
        s.status = "正在采集结构：首次加载超出单次预算，稍后自动重试（" + s.captureAttempts + "/" + CAPTURE_ATTEMPTS + "）";
    }

    private static void scan(Session s) {
        scan(s, true);
    }

    private static void scan(Session s, boolean resetAttempts) {
        cancelCaptureRetry(s);
        if (resetAttempts) s.captureAttempts = 0;
        s.captureAttempts++;
        s.job = 0;
        s.cursor = 0;
        s.completed = 0;
        s.defer = false;
        s.stopTick = false;
        clearPending(s);
        s.pins.clear();
        s.capabilities = HologramCapabilities.describe(s.context, trigger(s), null)
            .withSavedChannels(s.channels);
        if (s.target == null) {
            s.capture = null;
            refreshPlan(s);
            s.status = "信道配置已保存；请右键控制器选择施工目标";
            return;
        }
        if (s.machine == null || !(s.machine instanceof IConstructable)) {
            s.capture = null;
            refreshPlan(s);
            s.status = "未支持高级采集；仍可使用机器原版构造器接口";
            return;
        }
        s.facing = s.machine.getExtendedFacing()
            .ordinal();
        ItemStack initialTrigger = trigger(s);
        s.capture = HologramCapture.collect(s.machine, initialTrigger, s.machine.getExtendedFacing());
        s.capabilities = HologramCapabilities.describe(s.context, initialTrigger, s.capture.channelReport)
            .withSavedChannels(s.channels);
        ItemStack observedTrigger = trigger(s);
        if (!s.capture.incomplete && !ItemStack.areItemStackTagsEqual(initialTrigger, observedTrigger)) {
            s.capture = HologramCapture.collect(s.machine, observedTrigger, s.machine.getExtendedFacing());
            s.capabilities = HologramCapabilities.describe(s.context, observedTrigger, s.capture.channelReport)
                .withSavedChannels(s.channels);
            if (!ItemStack.areItemStackTagsEqual(observedTrigger, trigger(s))) {
                s.capture.incomplete = true;
                s.capture.failure = "CHANNEL_BRANCH_UNSTABLE";
            }
        }
        for (HologramCapture.Cell cell : s.capture.cells) cell.candidates = candidates(s, cell);
        refreshPlan(s);
        s.status = s.capture.incomplete ? "结构采集不完整，高级施工已阻止" : "预览已采集；未知元素及机器部件保持保护";
        scheduleCaptureRetry(s);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> candidates(Session s, HologramCapture.Cell cell) {
        if (cell.element == null) return Collections.emptyList();
        if (cell.candidates != null) return cell.candidates;
        List<ItemStack> result = new ArrayList<>();
        try {
            IItemSource previewSource = (predicate, simulate, count) -> inventorySource(s.player)
                .take(predicate, true, count);
            IStructureElement.BlocksToPlace blocks = cell.element.getBlocksToPlace(
                s.machine,
                s.player.worldObj,
                cell.x,
                cell.y,
                cell.z,
                elementTrigger(s, cell),
                AutoPlaceEnvironment.fromLegacy(previewSource, s.player, message -> {}));
            int examined = 0;
            if (blocks != null && blocks.getStacks() != null) for (ItemStack stack : blocks.getStacks()) {
                if (examined++ >= 128 || result.size() >= 128) break;
                if (stack == null || stack.getItem() == null) continue;
                Block block = Block.getBlockFromItem(stack.getItem());
                if (!s.noHatches || (block != Blocks.air && !block.hasTileEntity(stack.getItemDamage())))
                    result.add(stack.copy());
            }
            List<ItemStack> hatches = s.noHatches ? Collections.emptyList()
                : HologramElementCatalog.predicateCandidates(cell.element, blocks);
            hatches.sort((a, b) -> Integer.compare(hatchTier(a), hatchTier(b)));
            for (ItemStack stack : hatches) {
                if (result.size() >= 128) break;
                boolean duplicate = false;
                for (ItemStack existing : result) if (same(existing, stack)) {
                    duplicate = true;
                    break;
                }
                if (!duplicate) result.add(stack.copy());
            }
        } catch (RuntimeException | LinkageError ignored) {}
        return result;
    }

    private static int hatchTier(ItemStack stack) {
        int id = stack.getItemDamage();
        return stack.getItem() instanceof ItemMachines && id >= 0
            && id < GregTechAPI.METATILEENTITIES.length
            && GregTechAPI.METATILEENTITIES[id] instanceof MTEHatch
                ? ((MTEHatch) GregTechAPI.METATILEENTITIES[id]).mTier
                : Integer.MAX_VALUE;
    }

    private static void send(Session s) {
        NBTTagCompound state = new NBTTagCompound();
        state.setString("session", s.id);
        state.setString("generation", s.id);
        state.setBoolean("capturePending", s.capturePending);
        state.setInteger("captureAttempts", s.captureAttempts);
        state.setLong("captureRetryDue", s.captureRetryDue);
        state.setLong("planRevision", s.planRevision);
        state.setLong("uiSequence", s.uiSequence);
        state.setString("phase", s.phase);
        state.setIntArray("pending", s.pending < 0 ? new int[0] : new int[] { s.pending });
        state.setLong("due", s.due);
        state.setLong("serverTick", s.player.worldObj.getTotalWorldTime());
        state.setLong("batchSeq", s.batchSeq);
        state.setIntArray("ack", s.ackIndex < 0 ? new int[0] : new int[] { s.ackIndex });
        state.setBoolean("ackSuccess", s.ackSuccess);
        state.setBoolean("recoveryFits", s.planRecoveryFits);
        state.setBoolean("formationChecked", false);
        state.setTag("capabilities", s.capabilities == null ? new NBTTagList() : s.capabilities.toNBT());
        state.setBoolean("target", s.target != null);
        state.setInteger("x", s.x);
        state.setInteger("y", s.y);
        state.setInteger("z", s.z);
        state.setInteger("dimension", s.dimension);
        state.setInteger("side", s.side);
        state.setString("title", s.machine == null ? "全息构造器" : s.machine.getLocalName());
        state.setInteger("main", s.main);
        state.setTag("channels", s.channels.copy());
        state.setBoolean("noHatches", s.noHatches);
        state.setInteger("facing", s.facing);
        state.setInteger("mode", s.mode);
        state.setInteger("scope", s.scope);
        state.setInteger("layer", s.layer);
        state.setInteger("selected", s.selected);
        state.setString("status", s.status);
        state.setInteger("job", s.job);
        state.setInteger("completed", s.completed);
        state.setBoolean("hints", s.hints);
        s.hints = false;
        state.setIntArray("facings", s.machine == null ? new int[0] : new int[] { s.facing });
        state.setBoolean("supported", s.capture != null && !s.capture.incomplete);
        state.setString(
            "unsupportedReason",
            s.capture == null || s.capture.incomplete
                ? s.status + (s.capture == null || s.capture.failure.isEmpty() ? "" : " (" + s.capture.failure + ")")
                : "");
        NBTTagList cells = new NBTTagList(), materials = new NBTTagList(), recovery = new NBTTagList();
        int protectedCount = 0, missing = 0, total = 0, workTotal = 0;
        Map<String, NBTTagCompound> requirements = new LinkedHashMap<>();
        if (s.capture != null) for (int index = 0; index < s.capture.cells.size(); index++) {
            HologramCapture.Cell c = s.capture.cells.get(index);
            World world = s.player.worldObj;
            NBTTagCompound row = new NBTTagCompound();
            row.setInteger("x", c.x);
            row.setInteger("y", c.y);
            row.setInteger("z", c.z);
            row.setInteger("dx", c.x - s.x);
            row.setInteger("dy", c.y - s.y);
            row.setInteger("dz", c.z - s.z);
            row.setBoolean("anchor", c.anchor);
            row.setBoolean("iconOnly", c.iconOnly);
            row.setString("role", c.role);
            row.setString("family", c.family);
            row.setString("renderKind", c.renderKind);
            if (c.actualStack != null) row.setTag("actualStack", c.actualStack.writeToNBT(new NBTTagCompound()));
            NBTTagList icons = new NBTTagList();
            if (c.hintIcons != null)
                for (String icon : c.hintIcons) icons.appendTag(new NBTTagString(icon == null ? "" : icon));
            row.setTag("hintIcons", icons);
            if (c.hintTint != null) {
                int[] tint = new int[c.hintTint.length];
                for (int i = 0; i < tint.length; i++) tint[i] = c.hintTint[i];
                row.setIntArray("hintTint", tint);
            }
            row.setInteger("hintIndex", c.hintIndex);
            row.setBoolean("inScope", inScope(s, c, index));
            row.setBoolean("explicitTarget", explicitTarget(s, c, index) || explicitGrade(s, c));
            row.setBoolean(
                "autoTarget",
                !s.noHatches && s.channels.hasKey("gt_hatch", 3)
                    && "hatch".equals(c.role)
                    && !explicitTarget(s, c, index));
            boolean loaded = world.blockExists(c.x, c.y, c.z);
            Block current = loaded ? world.getBlock(c.x, c.y, c.z) : Blocks.air;
            int meta = loaded ? world.getBlockMetadata(c.x, c.y, c.z) : 0;
            row.setString("id", String.valueOf(Block.blockRegistry.getNameForObject(current)));
            row.setInteger("meta", meta);
            row.setString(
                "wantId",
                c.block == null ? "" : String.valueOf(Block.blockRegistry.getNameForObject(c.block)));
            row.setInteger("wantMeta", c.meta);
            ItemStack pinned = targetFor(s, c, index);
            if (pinned != null) row.setTag("targetStack", pinned.writeToNBT(new NBTTagCompound()));
            if (pinned != null && pinned.getItem() instanceof ItemMachines && loaded && trivial(world, c.x, c.y, c.z))
                row.setString("renderKind", "item");
            if (pinned != null && !(s.mode == 1 && coil(c.block, c.meta))) {
                Block pinnedBlock = Block.getBlockFromItem(pinned.getItem());
                if (pinnedBlock != Blocks.air) {
                    row.setString("wantId", String.valueOf(Block.blockRegistry.getNameForObject(pinnedBlock)));
                    row.setInteger(
                        "wantMeta",
                        pinned.getItem()
                            .getMetadata(pinned.getItemDamage()));
                }
            }
            if (s.mode == 2 && loaded && destructive(c, current, meta)) {
                row.setString("wantId", "minecraft:air");
                row.setInteger("wantMeta", 0);
            }
            row.setString("status", c.status);
            row.setString("reason", reason(s, c, index));
            row.setString(
                "operation",
                !inScope(s, c, index) ? "OUTSIDE"
                    : "satisfied".equals(c.status) ? "KEEP"
                        : "protected".equals(c.status) || "unsupported".equals(c.status) ? "PROTECT"
                            : s.mode == 2 ? "REMOVE" : loaded && trivial(world, c.x, c.y, c.z) ? "PLACE" : "REPLACE");
            if (inScope(s, c, index) && ("protected".equals(c.status) || "unsupported".equals(c.status)))
                protectedCount++;
            if (inScope(s, c, index)) total++;
            if (inScope(s, c, index) && index < s.planStatus.size() && "pending".equals(s.planStatus.get(index)))
                workTotal++;
            NBTTagList choices = new NBTTagList();
            List<ItemStack> options = candidates(s, c);
            int chosenChoice = -1;
            for (int choice = 0; choice < options.size(); choice++) if (same(pinned, options.get(choice))) {
                chosenChoice = choice;
                break;
            }
            row.setInteger("chosenChoice", chosenChoice);
            row.setString("pinScope", s.mode == 1 && coil(c.block, c.meta) ? "channel" : "target");
            for (ItemStack option : options) {
                NBTTagCompound tag = new NBTTagCompound();
                option.writeToNBT(tag);
                choices.appendTag(tag);
            }
            row.setTag("candidates", choices);
            cells.appendTag(row);
            if (!"satisfied".equals(c.status) && !"protected".equals(c.status)
                && !"unsupported".equals(c.status)
                && !"placed".equals(c.status)
                && !"replaced".equals(c.status)
                && pinned != null
                && inScope(s, c, index)
                && s.mode != 2
                && !s.player.capabilities.isCreativeMode) {
                ItemStack stack = pinned.copy();
                stack.stackSize = 1;
                String key = stackKey(stack);
                NBTTagCompound req = requirements.get(key);
                if (req == null) {
                    req = new NBTTagCompound();
                    stack.writeToNBT(req);
                    req.setInteger(
                        "available",
                        inventorySource(s.player).take(a -> same(a, stack), true, 4096)
                            .values()
                            .stream()
                            .mapToInt(Integer::intValue)
                            .sum());
                    requirements.put(key, req);
                }
                req.setInteger("required", req.getInteger("required") + 1);
            }
            if (inScope(s, c, index) && "pending".equals(c.status)
                && loaded
                && !trivial(world, c.x, c.y, c.z)
                && world.getTileEntity(c.x, c.y, c.z) == null
                && !s.player.capabilities.isCreativeMode) try {
                    for (ItemStack drop : current.getDrops(world, c.x, c.y, c.z, meta, 0)) {
                        NBTTagCompound recovered = drop.writeToNBT(new NBTTagCompound());
                        recovered.setInteger("expected", drop.stackSize);
                        recovered.setBoolean("estimated", true);
                        recovery.appendTag(recovered);
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    row.setString("reason", "预计掉落无法确定，施工将重验并可暂停");
                }
        }
        for (NBTTagCompound req : requirements.values()) {
            materials.appendTag(req);
            missing += Math.max(0, req.getInteger("required") - req.getInteger("available"));
        }
        state.setTag("cells", cells);
        state.setTag("materials", materials);
        state.setTag("recovery", recovery);
        state.setInteger("protected", protectedCount);
        state.setInteger("missing", missing);
        state.setInteger("total", total);
        state.setInteger("workTotal", workTotal);
        state.setString("description", "按控制器实际朝向采集；具体目标仅支持已适配结构族。控制器及已有设备保留；固定接口待补齐。确认差分后，服务器预发施工再逐格重验；原版事件仅在真实操作时触发。");
        HologramNetwork.sendState(s.player, state);
    }

    private static final class Session {

        final EntityPlayerMP player;
        final String id = UUID.randomUUID()
            .toString();
        final int x, y, z, dimension, slot, side;
        final Item tool;
        final String toolId;
        final long openTick;
        TileEntity target;
        MTEEnhancedMultiBlockBase<?> machine;
        IConstructable constructable;
        Object context;
        int main = 1, facing, mode, scope, layer, selected = -1, job, cursor, completed;
        boolean noHatches, hints, defer, stopTick, mainExplicit;
        String status = "";
        NBTTagCompound channels = new NBTTagCompound();
        HologramCapture capture;
        HologramCapabilities.Result capabilities;
        final Map<Integer, ItemStack> pins = new HashMap<>();
        long planRevision, uiSequence, batchSeq, due;
        int pending = -1, ackIndex = -1, planMissing;
        String phase = "IDLE";
        boolean planRecoveryFits = true, ackSuccess;
        boolean capturePending, retryNoHatches;
        int captureAttempts, retryFacing, retryMain, retryMode, retryScope, retryLayer, retrySelected;
        long captureRetryDue;
        NBTTagCompound retryChannels;
        final List<Block> expectedBlocks = new ArrayList<>();
        final List<Integer> expectedMeta = new ArrayList<>();
        final List<TileEntity> expectedTiles = new ArrayList<>();
        final List<String> planStatus = new ArrayList<>();
        final List<ItemStack> planTargets = new ArrayList<>();
        long actionTick = -1;
        int actionCount;

        Session(EntityPlayerMP p, int x, int y, int z) {
            this(p, x, y, z, true, 2);
        }

        Session(EntityPlayerMP p, int x, int y, int z, boolean targetMode, int side) {
            this.side = side;
            player = p;
            this.x = x;
            this.y = y;
            this.z = z;
            dimension = p.dimension;
            slot = p.inventory.currentItem;
            tool = p.getHeldItem() == null ? null
                : p.getHeldItem()
                    .getItem();
            target = targetMode ? p.worldObj.getTileEntity(x, y, z) : null;
            ItemStack heldStack = p.getHeldItem();
            // Copies retain identity. Exact creative NBT clones represent the same logical tool;
            // opening any target rotates the session nonce and invalidates old client actions.
            if (heldStack != null) {
                if (!heldStack.hasTagCompound()) heldStack.setTagCompound(new NBTTagCompound());
                if (heldStack.getTagCompound()
                    .getString(TOOL_ID)
                    .isEmpty())
                    heldStack.getTagCompound()
                        .setString(
                            TOOL_ID,
                            UUID.randomUUID()
                                .toString());
                p.inventory.markDirty();
            }
            toolId = heldStack == null ? ""
                : heldStack.getTagCompound()
                    .getString(TOOL_ID);
            openTick = p.worldObj.getTotalWorldTime();
            context = target instanceof IGregTechTileEntity ? ((IGregTechTileEntity) target).getMetaTileEntity()
                : target;
            if (heldStack != null) {
                if (heldStack.hasTagCompound()) main = Math.max(
                    1,
                    heldStack.getTagCompound()
                        .getInteger("gtitHologramMain"));
                mainExplicit = heldStack.getTagCompound()
                    .getBoolean("gtitHologramMainExplicit") || main != 1;
                ChannelDataAccessor.iterateChannelData(heldStack)
                    .forEach(
                        entry -> { if (entry.getValue() > 0) channels.setInteger(entry.getKey(), entry.getValue()); });
            }
            if (context instanceof MTEEnhancedMultiBlockBase) {
                machine = (MTEEnhancedMultiBlockBase<?>) context;
                facing = machine.getExtendedFacing()
                    .ordinal();
            }
            if (target instanceof IConstructableProvider)
                constructable = ((IConstructableProvider) target).getConstructable();
            else if (target instanceof IConstructable) constructable = (IConstructable) target;
            else if (target != null && IMultiblockInfoContainer.contains(target.getClass())) {
                ExtendedFacing nativeFacing = target instanceof IAlignment ? ((IAlignment) target).getExtendedFacing()
                    : ExtendedFacing.of(ForgeDirection.getOrientation(side));
                constructable = IMultiblockInfoContainer.<TileEntity>get(target.getClass())
                    .toConstructable(target, nativeFacing);
            } else if (context instanceof IConstructableProvider)
                constructable = ((IConstructableProvider) context).getConstructable();
            else if (context instanceof IConstructable) constructable = (IConstructable) context;
        }
    }

    /** Transactional jobs intentionally use only the main inventory covered by their refund snapshot. */
    private static IItemSource inventorySource(EntityPlayerMP player) {
        return (predicate, simulate, count) -> {
            Map<ItemStack, Integer> result = new LinkedHashMap<>();
            if (count < 1 || count > 4096) return result;
            int remaining = count;
            for (int i = 0; i < player.inventory.mainInventory.length && remaining > 0; i++) {
                if (i == player.inventory.currentItem) continue;
                ItemStack stack = player.inventory.mainInventory[i];
                if (stack == null || !predicate.test(stack)) continue;
                int taken = Math.min(remaining, stack.stackSize);
                ItemStack key = stack.copy();
                key.stackSize = taken;
                result.put(key, taken);
                remaining -= taken;
                if (!simulate) {
                    stack.stackSize -= taken;
                    if (stack.stackSize <= 0) player.inventory.mainInventory[i] = null;
                }
            }
            if (!simulate) player.inventory.markDirty();
            return result;
        };
    }

    private static final class FilteredSource implements IItemSource {

        final IItemSource source;
        final ItemStack pin;

        FilteredSource(IItemSource source, ItemStack pin) {
            this.source = source;
            this.pin = pin;
        }

        public Map<ItemStack, Integer> take(java.util.function.Predicate<ItemStack> predicate, boolean simulate,
            int count) {
            return source.take(stack -> same(stack, pin) && predicate.test(stack), simulate, count);
        }
    }

    private static final class ShellSource implements IItemSource {

        final IItemSource source;

        ShellSource(IItemSource source) {
            this.source = source;
        }

        private boolean shell(ItemStack stack) {
            Block block = Block.getBlockFromItem(stack.getItem());
            return block != Blocks.air && !block.hasTileEntity(stack.getItemDamage());
        }

        public boolean takeOne(ItemStack stack, boolean simulate) {
            return shell(stack) && source.takeOne(stack, simulate);
        }

        public boolean takeAll(ItemStack stack, boolean simulate) {
            return shell(stack) && source.takeAll(stack, simulate);
        }

        public Map<ItemStack, Integer> take(java.util.function.Predicate<ItemStack> predicate, boolean simulate,
            int count) {
            return source.take(stack -> shell(stack) && predicate.test(stack), simulate, count);
        }
    }

    private static final class ReservedSource implements IItemSource {

        final ItemStack reserved;
        boolean used;

        ReservedSource(ItemStack stack) {
            reserved = stack.copy();
            reserved.stackSize = 1;
        }

        public Map<ItemStack, Integer> take(java.util.function.Predicate<ItemStack> predicate, boolean simulate,
            int count) {
            if (used || count != 1 || !predicate.test(reserved)) return Collections.emptyMap();
            if (!simulate) used = true;
            return Collections.singletonMap(reserved.copy(), 1);
        }
    }

    private static final class CreativeSource implements IItemSource {

        final List<ItemStack> options;

        CreativeSource(List<ItemStack> options) {
            this.options = options;
        }

        public boolean takeOne(ItemStack stack, boolean simulate) {
            return stack != null && stack.getItem() != null && stack.stackSize == 1;
        }

        public boolean takeAll(ItemStack stack, boolean simulate) {
            return stack != null && stack.getItem() != null && stack.stackSize > 0 && stack.stackSize <= 4096;
        }

        public Map<ItemStack, Integer> take(java.util.function.Predicate<ItemStack> predicate, boolean simulate,
            int count) {
            if (count < 1 || count > 4096) return Collections.emptyMap();
            for (ItemStack option : options) if (predicate.test(option)) {
                ItemStack stack = option.copy();
                stack.stackSize = count;
                return Collections.singletonMap(stack, count);
            }
            return Collections.emptyMap();
        }
    }
}
