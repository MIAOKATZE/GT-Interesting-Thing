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
import gregtech.common.blocks.BlockCasings5;

/** Server-owned previews and bounded jobs. Client cells are never accepted as world operations. */
public final class HologramService {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final int BUDGET = 8;
    private static final long TIME_BUDGET = 2000000L;

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
        long now = player.worldObj.getTotalWorldTime();
        if (s.actionTick != now) {
            s.actionTick = now;
            s.actionCount = 0;
        }
        if (s.actionCount++ >= 8) return;
        if (!valid(s)) {
            s.job = 4;
            s.status = "目标、维度、距离或持有工具已变化";
            send(s);
            return;
        }
        String op = action.getString("op");
        if ("pause".equals(op) && s.job == 1) {
            s.job = 2;
            s.status = "已暂停";
        } else if ("cancel".equals(op)) {
            s.job = 4;
            s.status = "已取消";
        } else if ("resume".equals(op) && s.job == 2) {
            s.job = 1;
            s.status = "继续施工";
        } else if ("scan".equals(op) && s.job != 1 && s.job != 2) scan(s);
        else if ("configure".equals(op) && s.job != 1 && s.job != 2) {
            int requestedFacing = action.getInteger("facing");
            if (!action.hasKey("main", 3) || action.getInteger("main") < 1
                || !validChannels(action.getCompoundTag("channels"))
                || action.getInteger("mode") < 0
                || action.getInteger("mode") > 2
                || action.getInteger("scope") < 0
                || action.getInteger("scope") > 2
                || action.getInteger("layer") < -128
                || action.getInteger("layer") > 128
                || (s.machine != null && (requestedFacing < 0 || requestedFacing >= ExtendedFacing.values().length
                    || !s.machine.getAlignmentLimits()
                        .isNewExtendedFacingValid(ExtendedFacing.values()[requestedFacing])))) {
                s.status = "参数无效，配置未修改";
                send(s);
                return;
            }
            s.main = action.getInteger("main");
            s.channels = boundedChannels(action.getCompoundTag("channels"));
            s.noHatches = action.getBoolean("noHatches");
            s.mode = clamp(action.getInteger("mode"), 0, 2);
            s.scope = clamp(action.getInteger("scope"), 0, 2);
            s.layer = clamp(action.getInteger("layer"), -128, 128);
            s.selected = action.getInteger("selected");
            int facing = action.getInteger("facing");
            if (s.machine != null && facing >= 0
                && facing < ExtendedFacing.values().length
                && s.machine.getAlignmentLimits()
                    .isNewExtendedFacingValid(ExtendedFacing.values()[facing]))
                s.facing = facing;
            scan(s);
            persist(s);
        } else if ("start".equals(op) && s.job != 1) {
            if (s.capture == null || s.capture.incomplete) s.status = "该机器无法完整安全采集，请使用原版提示或搭建";
            else if (s.scope == 2 && (s.selected < 0 || s.selected >= s.capture.cells.size())) s.status = "请先选择要施工的格";
            else if (s.facing != s.machine.getExtendedFacing()
                .ordinal() && !applyFacing(s)) { /* Reason set by orientation validation. */ } else {
                    s.cursor = 0;
                    s.completed = 0;
                    s.defer = false;
                    s.stopTick = false;
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
        } else if ("pin".equals(op) && s.job != 1) {
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
                        s.status = "候选已固定；用于空格新建取料，已有合法替代件保持保留";
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
                sessions.remove();
                continue;
            }
            if (s.job != 1) continue;
            if (!valid(s)) {
                s.job = 4;
                s.status = "目标、维度、距离或持有工具已变化";
                send(s);
                continue;
            }
            long deadline = System.nanoTime() + TIME_BUDGET;
            int worked = 0, visited = 0;
            while (s.cursor < s.capture.cells.size() && worked < BUDGET
                && visited < 128
                && System.nanoTime() < deadline) {
                int index = s.cursor++;
                HologramCapture.Cell cell = s.capture.cells.get(index);
                visited++;
                if (!inScope(s, cell, index)) continue;
                if (!operate(s, cell, index)) {
                    s.cursor--;
                    if (s.defer) s.defer = false;
                    else s.job = 2;
                    break;
                }
                s.completed++;
                worked++;
                if (s.stopTick) {
                    s.stopTick = false;
                    break;
                }
            }
            if (s.cursor >= s.capture.cells.size() && s.job == 1) {
                int unresolved = 0;
                for (int i = 0; i < s.capture.cells.size(); i++) if (inScope(s, s.capture.cells.get(i), i)) {
                    String status = s.capture.cells.get(i).status;
                    if (!"satisfied".equals(status) && !"placed".equals(status)
                        && !"replaced".equals(status)
                        && !"removed".equals(status)) unresolved++;
                }
                s.job = unresolved == 0 ? 3 : 2;
                s.status = unresolved == 0 ? "范围内任务完成（未执行机器结构检查）" : "仍有受保护或未知格，任务未完成";
                if (s.noHatches) s.status = "外壳已施工；固定接口或受保护格待手动补齐，不代表机器成型";
            }
            send(s);
        }
    }

    private static boolean valid(Session s) {
        ItemStack held = s.player.getHeldItem();
        return !s.player.isDead && s.player.dimension == s.dimension
            && held != null
            && held == s.heldStack
            && held.getItem() == s.tool
            && s.player.inventory.currentItem == s.slot
            && (s.target == null || (s.player.getDistanceSq(s.x + .5, s.y + .5, s.z + .5) <= 4096
                && s.player.worldObj.blockExists(s.x, s.y, s.z)
                && s.player.worldObj.getTileEntity(s.x, s.y, s.z) == s.target
                && (!(s.target instanceof IGregTechTileEntity)
                    || ((IGregTechTileEntity) s.target).getMetaTileEntity() == s.context)));
    }

    private static boolean inScope(Session s, HologramCapture.Cell c, int index) {
        return s.scope == 0 || (s.scope == 1 && c.y - s.y == s.layer) || (s.scope == 2 && index == s.selected);
    }

    private static boolean emptyStructure(Session s) {
        if (!emptyStructure(s, s.capture)) return false;
        if (s.machine != null && s.facing != s.machine.getExtendedFacing()
            .ordinal()) {
            HologramCapture current = HologramCapture.collect(s.machine, trigger(s), s.machine.getExtendedFacing());
            return emptyStructure(s, current);
        }
        return true;
    }

    private static boolean emptyStructure(Session s, HologramCapture footprint) {
        if (footprint == null || footprint.incomplete) return false;
        for (HologramCapture.Cell c : footprint.cells) {
            if (!s.player.worldObj.blockExists(c.x, c.y, c.z)) return false;
            if ((c.x != s.x || c.y != s.y || c.z != s.z) && !trivial(s.player.worldObj, c.x, c.y, c.z)) return false;
        }
        return true;
    }

    private static boolean applyFacing(Session s) {
        IGregTechTileEntity base = s.machine.getBaseMetaTileEntity();
        ExtendedFacing before = s.machine.getExtendedFacing();
        ExtendedFacing requested = ExtendedFacing.values()[s.facing];
        ForgeDirection beforeDirection = base.getFrontFacing();
        if (!s.machine.getAlignmentLimits()
            .isNewExtendedFacingValid(requested)) {
            s.status = "该控制器不允许此朝向或镜像";
            return false;
        }
        if (!s.player.worldObj.canMineBlock(s.player, s.x, s.y, s.z)
            || !s.player.canPlayerEdit(s.x, s.y, s.z, s.side, trigger(s))) {
            s.status = "无权修改控制器朝向";
            return false;
        }
        if (!emptyStructure(s)) {
            s.status = "方向变换仅允许旧朝向与目标朝向都为空的新结构";
            return false;
        }
        try {
            base.setFrontFacing(requested.getDirection());
            s.machine.setExtendedFacing(requested);
            if (base.getFrontFacing() != requested.getDirection() || s.machine.getExtendedFacing() != requested) {
                throw new IllegalStateException("orientation rejected");
            }
            base.markDirty();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            try {
                base.setFrontFacing(beforeDirection);
                s.machine.setExtendedFacing(before);
            } catch (RuntimeException | LinkageError rollbackFailure) {
                s.status = "朝向修改及还原失败，任务未启动，请检查控制器";
                return false;
            }
            s.status = "朝向修改失败，已还原并停止启动";
            return false;
        }
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
        if (s.mode != 2 && knownSatisfied(c, old, oldMeta)) {
            c.status = "satisfied";
            return true;
        }
        if (!empty && (s.mode == 0 || !destructive(c, old, oldMeta))) {
            c.status = "protected";
            return true;
        }
        if (c.unknown || c.block == null) {
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
            ItemStack required = new ItemStack(c.block, 1, c.meta);
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
                ItemStack pin = empty ? s.pins.get(index) : null;
                if (s.player.capabilities.isCreativeMode) source = new CreativeSource(candidates(s, c));
                if (pin != null && material == null) source = new FilteredSource(source, pin);
                if (s.noHatches) source = new ShellSource(source);
                boolean directCreative = s.player.capabilities.isCreativeMode && !s.noHatches && pin == null;
                PlaceResult result;
                if (directCreative)
                    result = c.element.placeBlock(s.machine, w, c.x, c.y, c.z, trigger(s)) ? PlaceResult.ACCEPT
                        : PlaceResult.REJECT;
                else {
                    result = c.element.survivalPlaceBlock(
                        s.machine,
                        w,
                        c.x,
                        c.y,
                        c.z,
                        trigger(s),
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
                    || (reservation != null && !reservation.used)) {
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
                && current.getItem() == saved[i].getItem()) {
                current.stackSize = saved[i].stackSize;
                current.setItemDamage(saved[i].getItemDamage());
                current.setTagCompound(
                    saved[i].hasTagCompound() ? (NBTTagCompound) saved[i].getTagCompound()
                        .copy() : null);
            } else p.inventory.mainInventory[i] = saved[i] == null ? null : saved[i].copy();
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
        ItemStack result = s.heldStack == null ? new ItemStack(s.tool, 1) : s.heldStack.copy();
        result.stackSize = s.main;
        List<String> existing = new ArrayList<>();
        ChannelDataAccessor.iterateChannelData(result)
            .forEach(entry -> existing.add(entry.getKey()));
        for (String channel : existing) ChannelDataAccessor.unsetChannelData(result, channel);
        for (Object key : s.channels.func_150296_c())
            ChannelDataAccessor.setChannelData(result, (String) key, s.channels.getInteger((String) key));
        return result;
    }

    private static void persist(Session s) {
        if (s.heldStack == null) return;
        if (!s.heldStack.hasTagCompound()) s.heldStack.setTagCompound(new NBTTagCompound());
        s.heldStack.getTagCompound()
            .setInteger("gtitHologramMain", s.main);
        List<String> existing = new ArrayList<>();
        ChannelDataAccessor.iterateChannelData(s.heldStack)
            .forEach(entry -> existing.add(entry.getKey()));
        for (String name : existing) ChannelDataAccessor.unsetChannelData(s.heldStack, name);
        for (Object key : s.channels.func_150296_c())
            ChannelDataAccessor.setChannelData(s.heldStack, (String) key, s.channels.getInteger((String) key));
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

    private static void scan(Session s) {
        s.job = 0;
        s.cursor = 0;
        s.completed = 0;
        s.defer = false;
        s.stopTick = false;
        s.pins.clear();
        if (s.target == null) {
            s.capture = null;
            s.status = "信道配置已保存；请右键控制器选择施工目标";
            return;
        }
        if (s.machine == null || !(s.machine instanceof IConstructable)) {
            s.capture = null;
            s.status = "未支持高级采集；仍可使用机器原版构造器接口";
            return;
        }
        s.capture = HologramCapture.collect(s.machine, trigger(s), ExtendedFacing.values()[s.facing]);
        for (HologramCapture.Cell cell : s.capture.cells) cell.candidates = candidates(s, cell);
        s.status = s.capture.incomplete ? "结构采集不完整，高级施工已阻止" : "预览已采集；未知元素及机器部件保持保护";
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> candidates(Session s, HologramCapture.Cell cell) {
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
                trigger(s),
                AutoPlaceEnvironment.fromLegacy(previewSource, s.player, message -> {}));
            int examined = 0;
            if (blocks != null && blocks.getStacks() != null) for (ItemStack stack : blocks.getStacks()) {
                if (examined++ >= 64 || result.size() >= 16) break;
                if (stack == null) continue;
                Block block = Block.getBlockFromItem(stack.getItem());
                if (!s.noHatches || (block != Blocks.air && !block.hasTileEntity(stack.getItemDamage())))
                    result.add(stack.copy());
            }
        } catch (RuntimeException | LinkageError ignored) {}
        return result;
    }

    private static void send(Session s) {
        NBTTagCompound state = new NBTTagCompound();
        state.setString("session", s.id);
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
        List<Integer> facings = new ArrayList<>();
        if (s.machine != null) for (ExtendedFacing f : ExtendedFacing.values()) if (s.machine.getAlignmentLimits()
            .isNewExtendedFacingValid(f)) facings.add(f.ordinal());
        int[] array = new int[facings.size()];
        for (int i = 0; i < array.length; i++) array[i] = facings.get(i);
        state.setIntArray("facings", array);
        state.setBoolean("supported", s.capture != null && !s.capture.incomplete);
        state.setString(
            "unsupportedReason",
            s.capture == null || s.capture.incomplete
                ? s.status + (s.capture == null || s.capture.failure.isEmpty() ? "" : " (" + s.capture.failure + ")")
                : "");
        NBTTagList cells = new NBTTagList(), materials = new NBTTagList();
        int protectedCount = 0, missing = 0, total = 0;
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
            boolean loaded = world.blockExists(c.x, c.y, c.z);
            Block current = loaded ? world.getBlock(c.x, c.y, c.z) : Blocks.air;
            int meta = loaded ? world.getBlockMetadata(c.x, c.y, c.z) : 0;
            row.setString("id", String.valueOf(Block.blockRegistry.getNameForObject(current)));
            row.setInteger("meta", meta);
            row.setString(
                "wantId",
                c.block == null ? "" : String.valueOf(Block.blockRegistry.getNameForObject(c.block)));
            row.setInteger("wantMeta", c.meta);
            ItemStack pinned = s.pins.get(index);
            if (pinned != null && loaded && trivial(world, c.x, c.y, c.z) && !(s.mode == 1 && coil(c.block, c.meta))) {
                Block pinnedBlock = Block.getBlockFromItem(pinned.getItem());
                if (pinnedBlock != Blocks.air) {
                    row.setString("wantId", String.valueOf(Block.blockRegistry.getNameForObject(pinnedBlock)));
                    row.setInteger(
                        "wantMeta",
                        pinned.getItem()
                            .getMetadata(pinned.getItemDamage()));
                }
            }
            if (s.job == 0) {
                boolean empty = loaded && trivial(world, c.x, c.y, c.z);
                c.status = !loaded || c.unknown || c.block == null ? "unsupported"
                    : world.getTileEntity(c.x, c.y, c.z) != null ? "protected"
                        : s.mode == 2 ? (empty ? "satisfied" : destructive(c, current, meta) ? "pending" : "protected")
                            : knownSatisfied(c, current, meta) ? "satisfied"
                                : empty ? "pending"
                                    : s.mode == 1 && destructive(c, current, meta) ? "pending" : "protected";
            }
            if (s.mode == 2 && loaded && destructive(c, current, meta)) {
                row.setString("wantId", "minecraft:air");
                row.setInteger("wantMeta", 0);
            }
            row.setString("status", c.status);
            if ("protected".equals(c.status) || "unsupported".equals(c.status)) protectedCount++;
            if (inScope(s, c, index)) total++;
            NBTTagList choices = new NBTTagList();
            List<ItemStack> options = candidates(s, c);
            int chosenChoice = -1;
            for (int choice = 0; choice < options.size(); choice++) if (same(pinned, options.get(choice))) {
                chosenChoice = choice;
                break;
            }
            row.setInteger("chosenChoice", chosenChoice);
            row.setString("pinScope", s.mode == 1 && coil(c.block, c.meta) ? "channel" : "newbuild");
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
                && !options.isEmpty()
                && inScope(s, c, index)
                && s.mode != 2
                && !s.player.capabilities.isCreativeMode) {
                ItemStack stack = s.pins.containsKey(index) ? s.pins.get(index)
                    .copy()
                    : options.get(0)
                        .copy();
                stack.stackSize = 1;
                String key = stack.getItem() + ":" + stack.getItemDamage() + ":" + stack.getTagCompound();
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
        }
        for (NBTTagCompound req : requirements.values()) {
            materials.appendTag(req);
            missing += Math.max(0, req.getInteger("required") - req.getInteger("available"));
        }
        state.setTag("cells", cells);
        state.setTag("materials", materials);
        state.setInteger("protected", protectedCount);
        state.setInteger("missing", missing);
        state.setInteger("total", total);
        state.setString(
            "description",
            "真实构造器动态形状；候选和提示用于预览，施工由原版元素决定。明确结构方块族允许替换和拆除；机器部件及未知方块受保护。无仓室模式使用壳体候选，未明确候选处保持空缺。方向变换仅空地新结构执行。");
        HologramNetwork.sendState(s.player, state);
    }

    private static final class Session {

        final EntityPlayerMP player;
        final String id = UUID.randomUUID()
            .toString();
        final int x, y, z, dimension, slot, side;
        final Item tool;
        final ItemStack heldStack;
        final long openTick;
        TileEntity target;
        MTEEnhancedMultiBlockBase<?> machine;
        IConstructable constructable;
        Object context;
        int main = 1, facing, mode, scope, layer, selected = -1, job, cursor, completed;
        boolean noHatches, hints, defer, stopTick;
        String status = "";
        NBTTagCompound channels = new NBTTagCompound();
        HologramCapture capture;
        final Map<Integer, ItemStack> pins = new HashMap<>();
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
            heldStack = p.getHeldItem();
            openTick = p.worldObj.getTotalWorldTime();
            context = target instanceof IGregTechTileEntity ? ((IGregTechTileEntity) target).getMetaTileEntity()
                : target;
            if (heldStack != null) {
                if (heldStack.hasTagCompound()) main = Math.max(
                    1,
                    heldStack.getTagCompound()
                        .getInteger("gtitHologramMain"));
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
