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
import net.minecraft.item.ItemBlock;
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
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.util.ItemStackPredicate;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.interfaces.IHeatingCoil;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.blocks.BlockCasings5;
import gregtech.common.blocks.BlockFrameBox;
import gregtech.common.blocks.ItemMachines;

/** Server-owned previews and bounded jobs. Client cells are never accepted as world operations. */
public final class HologramService {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final int BUDGET = HologramCapture.LIMIT;
    private static final String TOOL_ID = "gtitHologramToolId";
    private static final int CAPTURE_ATTEMPTS = 3;
    private static final String DRAFT = "gtitHologramDraft";
    private static final String SELECTED_PRESET = "gtitHologramPreset";

    private HologramService() {}

    public static void open(EntityPlayerMP player, int x, int y, int z, int side) {
        Session previous = SESSIONS.get(player.getUniqueID());
        if (previous != null && !previous.virtual
            && !previous.cancelled
            && previous.x == x
            && previous.y == y
            && previous.z == z
            && valid(previous)) {
            previous.open = true;
            previous.settingsOnly = false;
            send(previous);
            return;
        }
        if (previous != null && (previous.job == 1 || previous.job == 2)) {
            previous.job = 4;
            clearPending(previous);
        }
        Session session = new Session(player, x, y, z, true, side);
        SESSIONS.put(player.getUniqueID(), session);
        readDraft(session);
        scan(session);
        persist(session);
        send(session);
    }

    public static void openAir(EntityPlayerMP player) {
        Session previous = SESSIONS.get(player.getUniqueID());
        if (previous != null && !previous.cancelled
            && previous.job != 4
            && (previous.target != null || previous.virtual)
            && valid(previous)) {
            if (player.worldObj.getTotalWorldTime() - previous.openTick <= 2) return;
            previous.open = true;
            previous.settingsOnly = false;
            send(previous);
            return;
        }
        Session session = new Session(player, 0, 0, 0, false, 2);
        session.status = "请右键多方块控制器选择目标";
        SESSIONS.put(player.getUniqueID(), session);
        send(session);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    public static void openSettings(EntityPlayerMP player) {
        Session s = SESSIONS.get(player.getUniqueID());
        if (s == null || s.cancelled || !valid(s)) {
            s = new Session(player, 0, 0, 0, false, 2);
            SESSIONS.put(player.getUniqueID(), s);
        }
        s.settingsOnly = true;
        s.open = true;
        send(s);
    }

    public static void openVirtual(EntityPlayerMP player, int x, int y, int z, int side, String presetId) {
        Session previous = SESSIONS.get(player.getUniqueID());
        if (previous != null && (previous.job == 1 || previous.job == 2)) {
            previous.status = "请先取消当前施工任务";
            send(previous);
            return;
        }
        Session s = new Session(player, x, y, z, false, side);
        s.virtual = true;
        s.open = true;
        SESSIONS.put(player.getUniqueID(), s);
        if (!valid(s) || !player.worldObj.blockExists(x, y, z)) {
            s.cancelled = true;
            s.status = "虚拟锚点未加载、超出高度或距离限制";
            send(s);
            return;
        }
        if (presetId == null || presetId.isEmpty()) presetId = selectedPreset(s);
        if (!presetId.isEmpty()) loadPreset(s, presetId);
        else s.status = "请先选择服务器保存的自定义预设";
        send(s);
    }

    public static void lockVirtual(EntityPlayerMP player, int x, int y, int z, int side) {
        Session s = SESSIONS.get(player.getUniqueID());
        if (s == null || s.cancelled || !s.virtual || s.virtualPreset == null || !valid(s) || s.job == 1 || s.job == 2)
            return;
        if (y < 0 || y >= player.worldObj.getHeight()
            || player.getDistanceSq(x + .5, y + .5, z + .5) > 4096
            || !player.worldObj.blockExists(x, y, z)) return;
        if (s.x != x || s.y != y || s.z != z) {
            int previousX = s.x, previousY = s.y, previousZ = s.z;
            Map<String, ItemStack> targets = new HashMap<>();
            Map<String, Integer> facings = new HashMap<>();
            java.util.Set<String> edited = new java.util.HashSet<>();
            List<HologramCapture.Cell> cells = new ArrayList<>(s.capture.cells);
            String selectedRelative = null;
            for (int i = 0; i < cells.size(); i++) {
                HologramCapture.Cell cell = cells.get(i);
                String relative = (cell.x - previousX) + "," + (cell.y - previousY) + "," + (cell.z - previousZ);
                if (i == s.selected) selectedRelative = relative;
                if (s.pins.containsKey(i)) targets.put(
                    relative,
                    s.pins.get(i)
                        .copy());
                if (s.facingPins.containsKey(position(cell))) facings.put(relative, s.facingPins.get(position(cell)));
                if (s.virtualEdited.contains(position(cell))) edited.add(relative);
            }
            s.x = x;
            s.y = y;
            s.z = z;
            s.side = side;
            populateVirtual(s);
            Map<String, Integer> indices = new HashMap<>();
            for (int i = 0; i < s.capture.cells.size(); i++) indices.put(position(s.capture.cells.get(i)), i);
            for (HologramCapture.Cell old : cells) {
                int dx = old.x - previousX, dy = old.y - previousY, dz = old.z - previousZ;
                String relative = dx + "," + dy + "," + dz;
                String key = (x + dx) + "," + (y + dy) + "," + (z + dz);
                HologramCapture.Cell cell = s.capture.positions.get(key);
                if (cell == null && s.capture.cells.size() < HologramCapture.LIMIT) {
                    cell = new HologramCapture.Cell(null, x + dx, y + dy, z + dz);
                    cell.block = old.block;
                    cell.meta = old.meta;
                    cell.userPosition = true;
                    cell.role = old.role;
                    s.capture.cells.add(cell);
                    s.capture.positions.put(key, cell);
                    indices.put(key, s.capture.cells.size() - 1);
                }
                if (cell == null) continue;
                ItemStack target = targets.get(relative);
                if (relative.equals(selectedRelative)) s.selected = indices.get(key);
                if (acceptableSample(s, cell, target)) {
                    int index = indices.get(key);
                    s.pins.put(index, target.copy());
                    s.coordinatePins.put(key, target.copy());
                }
                if (edited.contains(relative)) s.virtualEdited.add(key);
                Integer direction = facings.get(relative);
                if (direction != null) for (int allowed : legalFacings(s, cell, target))
                    if (allowed == direction) s.facingPins.put(key, direction);
            }
        }
        s.virtualLocked = true;
        s.open = false;
        refreshPlan(s);
        s.status = "虚拟结构位置已锁定；再次操作后施工";
        send(s);
    }

    public static void immediateVirtual(EntityPlayerMP player, int x, int y, int z, int side, String presetId) {
        Session s = SESSIONS.get(player.getUniqueID());
        if (s != null && (s.job == 1 || s.job == 2)) {
            s.status = "请先取消或完成当前施工任务";
            send(s);
            return;
        }
        if (s == null || !s.virtual || s.cancelled || s.virtualPreset == null || !valid(s)) {
            openVirtual(player, x, y, z, side, presetId);
            s = SESSIONS.get(player.getUniqueID());
        }
        if (s == null || !s.virtual || s.virtualPreset == null) return;
        if (!s.virtualLocked) lockVirtual(player, x, y, z, side);
        if (!s.virtualLocked) return;
        directBuild(s);
        send(s);
    }

    public static void cancel(EntityPlayerMP player) {
        Session s = SESSIONS.get(player.getUniqueID());
        if (s == null || !valid(s)) return;
        s.job = 4;
        s.cancelled = true;
        s.open = false;
        if (s.virtual) s.virtualLocked = true;
        cancelCaptureRetry(s);
        clearPending(s);
        s.status = "已取消未执行的施工；已施工世界保留";
        send(s);
    }

    public static void handle(EntityPlayerMP player, NBTTagCompound action) {
        Session s = SESSIONS.get(player.getUniqueID());
        if (s == null || s.cancelled || !s.id.equals(action.getString("session"))) return;
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
        s.materialContext = new HologramMaterials.Context(player, s.materialSettings);
        s.materialContext.flushRecovery();
        String op = action.getString("op");
        EditState beforeEdit = isPlanningEdit(op) && s.job != 1 && s.job != 2 ? new EditState(s) : null;
        if (s.virtual && beforeEdit != null
            && !"editCopy".equals(op)
            && !"hatchPreset".equals(op)
            && !"clearHatchPreset".equals(op)
            && s.capture != null) {
            int selected = action.hasKey("selected", 3) ? action.getInteger("selected") : s.selected;
            if (selected >= 0 && selected < s.capture.cells.size())
                s.virtualEdited.add(position(s.capture.cells.get(selected)));
        }
        if ("settings".equals(op) && s.job != 1 && s.job != 2) {
            applySettings(s, action);
        } else if ("savePreset".equals(op) && s.job != 1 && s.job != 2) {
            savePreset(s, action.getString("name"));
        } else if ("loadPreset".equals(op) && s.job != 1 && s.job != 2) {
            loadPreset(s, action.getString("presetId"));
        } else if ("resetDraft".equals(op) && s.job != 1 && s.job != 2) {
            resetDraft(s);
        } else if ("lockVirtual".equals(op)) {
            lockVirtual(player, s.x, s.y, s.z, s.side);
        } else if ("close".equals(op)) {
            s.open = false;
            s.settingsOnly = false;
        } else if (isCellEdit(op) || "editUndo".equals(op) || "editRedo".equals(op)) {
            edit(s, action, op);
        } else if ("pause".equals(op) && s.job == 1) {
            s.job = 2;
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
            if (s.virtual && action.getInteger("mode") == 2) {
                s.status = "虚拟预设不支持拆除；请先建立实体控制器";
                send(s);
                return;
            }
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
            if (action.hasKey("materialMain")) s.materialSettings.main = action.getBoolean("materialMain");
            if (action.hasKey("materialContainers"))
                s.materialSettings.containers = action.getBoolean("materialContainers");
            if (action.hasKey("materialMe")) s.materialSettings.me = action.getBoolean("materialMe");
            if (action.hasKey("materialPriority"))
                s.materialSettings.priority = clamp(action.getInteger("materialPriority"), 0, 2);
            s.materialContext = new HologramMaterials.Context(player, s.materialSettings);
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
        } else if ("build".equals(op) && s.job != 1 && s.job != 2) {
            directBuild(s);
        } else if ("removePosition".equals(op) && s.job != 1 && s.job != 2) {
            int index = action.getInteger("selected");
            if (s.capture != null && index >= 0 && index < s.capture.cells.size()) {
                HologramCapture.Cell cell = s.capture.cells.get(index);
                s.coordinatePins.remove(position(cell));
                s.manualPositions.remove(position(cell));
                s.pins.remove(index);
                if (s.autoTargets.containsKey(index)) s.suppressedAuto.add(position(s.capture.cells.get(index)));
                scan(s);
                s.status = "位置覆盖已清除，已恢复结构默认目标";
            }
        } else if ("addPosition".equals(op) && s.job != 1 && s.job != 2) {
            addPosition(s, action);
        } else if ("start".equals(op) && s.job != 1 && s.job != 2) {
            if (s.virtual && (!s.virtualLocked || s.mode == 2)) s.status = "虚拟预设须锁定位置且不能拆除";
            else if (s.capturePending) s.status = "正在采集完整结构，请等待自动重试回执";
            else if (s.capture == null || s.capture.incomplete) s.status = "该机器无法完整安全采集，请使用原版提示或搭建";
            else if ((s.scope == 2 || s.scope == 3) && (s.selected < 0 || s.selected >= s.capture.cells.size()))
                s.status = "请先选择施工范围的结构格";
            else if (s.scope == 3 && s.capture.cells.get(s.selected).family.isEmpty()) s.status = "选中格没有安全同族适配，请使用单格范围";
            else if (!s.virtual && s.machine.getExtendedFacing()
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
                s.work.clear();
                for (int i = 0; i < s.capture.cells.size(); i++) {
                    if (inScope(s, s.capture.cells.get(i), i) && "pending".equals(s.planStatus.get(i))) s.work.add(i);
                }
                s.phase = "RUNNING";
                s.status = "正在直接施工";
            }
        } else if ("native".equals(op) && s.job != 1 && s.job != 2) {
            if (s.mode == 0) nativeBuild(s);
            else s.status = "原版搭建只用于建造模式";
        } else if ("hints".equals(op)) {
            s.hints = true;
            s.status = "显示原版结构提示";
        } else if (("customPin".equals(op) || "clearPin".equals(op)) && s.job != 1 && s.job != 2) {
            int index = action.getInteger("selected");
            if (s.capturePending || s.capture == null || index < 0 || index >= s.capture.cells.size())
                s.status = "请先选择完整采集中的结构格";
            else if ("clearPin".equals(op)) {
                edit(s, action, "restoreCell");
            } else {
                int slot = action.getInteger("inventorySlot");
                ItemStack sample = slot < 0 || slot >= player.inventory.mainInventory.length ? null
                    : player.inventory.mainInventory[slot];
                HologramCapture.Cell cell = s.capture.cells.get(index);
                if (s.mode == 2) s.status = "拆除模式不接受建造目标，请先切换模式";
                else if (sample == null || sample.stackSize < 1 || cell.anchor) s.status = "库存样本或目标格无效";
                else if (!acceptableSample(s, cell, sample)) s.status = "该库存物品不是可放置方块或有效 GT 机器";
                else {
                    ItemStack pin = sample.copy();
                    pin.stackSize = 1;
                    s.selected = index;
                    s.scope = 2;
                    if (pin.getItem() instanceof ItemMachines) s.noHatches = false;
                    s.pins.put(index, pin);
                    s.coordinatePins.put(position(cell), pin.copy());
                    refreshPlan(s);
                    s.status = "库存样本已设为目标；点击施工才扣材料";
                }
            }
        } else if ("sourcePin".equals(op) && s.job != 1 && s.job != 2) {
            int index = action.getInteger("selected"), choice = action.getInteger("choice");
            if (s.capturePending || s.capture == null
                || s.capture.incomplete
                || index < 0
                || index >= s.capture.cells.size()
                || choice < 0
                || choice >= s.materialChoices.size()) {
                s.status = "来源目录或目标位置已失效，请重新选择";
            } else {
                HologramCapture.Cell cell = s.capture.cells.get(index);
                ItemStack pin = s.materialChoices.get(choice)
                    .copy();
                pin.stackSize = 1;
                if (s.mode == 2) s.status = "拆除模式不接受建造目标";
                else if (!acceptableSample(s, cell, pin)) s.status = "该来源物品不是有效放置目标";
                else if (!materialSource(s).takeOne(pin, true)) s.status = "来源物品已不可用，请刷新后重选";
                else {
                    s.selected = index;
                    s.scope = 2;
                    if (pin.getItem() instanceof ItemMachines) s.noHatches = false;
                    s.pins.put(index, pin);
                    s.coordinatePins.put(position(cell), pin.copy());
                    refreshPlan(s);
                    s.status = "来源物品已设为目标；点击施工才扣材料";
                }
            }
        } else if ("hatchPreset".equals(op) && s.job != 1 && s.job != 2) {
            int tier = action.getInteger("tier");
            if (tier < 0 || tier >= gregtech.api.enums.GTValues.VN.length || s.capture == null || s.capture.incomplete)
                s.status = "电压等级或完整结构无效";
            else {
                s.hatchTier = tier;
                s.hatchDowngrade = action.getBoolean("downgrade");
                s.presetEnabled = true;
                s.presetPaused = false;
                s.scope = 0;
                s.noHatches = false;
                s.suppressedAuto.clear();
                for (HologramCapture.Cell cell : s.capture.cells) cell.candidates = null;
                refreshPlan(s);
                s.status = s.presetReason;
            }
        } else if ("clearHatchPreset".equals(op) && s.job != 1 && s.job != 2) {
            s.presetEnabled = false;
            s.presetPaused = true;
            s.autoTargets.clear();
            s.suppressedAuto.clear();
            refreshPlan(s);
            s.status = "整机预设已撤销，已有仓室与手选目标保留";
        } else if ("hatchFacing".equals(op) && s.job != 1 && s.job != 2) {
            int index = action.getInteger("selected"), direction = action.getInteger("facing");
            if (s.capture == null || index < 0 || index >= s.capture.cells.size()) s.status = "请先选择仓室格";
            else {
                HologramCapture.Cell cell = s.capture.cells.get(index);
                boolean legal = direction == -1;
                for (int allowed : legalFacings(s, cell, targetFor(s, cell, index)))
                    if (allowed == direction) legal = true;
                if (cell.anchor || !legal) s.status = "控制器受保护，或该仓室不支持所选方向";
                else {
                    if (direction == -1) s.facingPins.remove(position(cell));
                    else s.facingPins.put(position(cell), direction);
                    refreshPlan(s);
                    s.status = direction == -1 ? "已恢复自动朝向" : "仓室朝向已指定，施工时生效";
                }
            }
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
                    if (cell.anchor) s.status = "控制器始终受保护";
                    else if (s.mode == 2) s.status = "拆除模式不接受建造目标";
                    else {
                        s.selected = index;
                        s.scope = 2;
                        s.pins.put(index, chosen.copy());
                        s.coordinatePins.put(position(cell), chosen.copy());
                        refreshPlan(s);
                        s.status = "目标已指定；点击施工才会扣材料并放置";
                    }
                }
            }
        } else if (s.job == 2 && ("scan".equals(op) || "configure".equals(op) || "native".equals(op)))
            s.status = "暂停任务保留进度；请先取消再改变配置或重新扫描";
        if (s.job != 1 && s.job != 2
            && ("configure".equals(op) || "customPin".equals(
                op) || "sourcePin".equals(op) || "pin".equals(op) || "addPosition".equals(op) || "clearPin".equals(op)))
            refreshMaterialChoices(s);
        if (beforeEdit != null && !beforeEdit.sameAs(new EditState(s))) {
            remember(s.undoEdits, beforeEdit);
            s.redoEdits.clear();
        }
        persist(s);
        send(s);
    }

    private static boolean isCellEdit(String op) {
        return "editCopy".equals(op) || "editCut".equals(op)
            || "editPaste".equals(op)
            || "restoreCell".equals(op)
            || "rotateCell".equals(op);
    }

    private static boolean isPlanningEdit(String op) {
        return isCellEdit(op) || "clearPin".equals(op)
            || "customPin".equals(op)
            || "sourcePin".equals(op)
            || "pin".equals(op)
            || "hatchFacing".equals(op)
            || "hatchPreset".equals(op)
            || "clearHatchPreset".equals(op);
    }

    private static void remember(List<EditState> history, EditState state) {
        if (history.size() >= 32) history.remove(0);
        history.add(state);
    }

    /** Editing never harvests a block or consumes a material. Clipboard samples carry no device state. */
    private static void edit(Session s, NBTTagCompound action, String op) {
        if (s.job == 1 || s.job == 2) {
            s.status = "施工任务保留当前计划；请先取消再编辑";
            return;
        }
        if (s.capturePending || s.capture == null || s.capture.incomplete) {
            s.status = "请等待完整结构采集后编辑";
            return;
        }
        if ("editUndo".equals(op) || "editRedo".equals(op)) {
            boolean undo = "editUndo".equals(op);
            List<EditState> from = undo ? s.undoEdits : s.redoEdits;
            if (from.isEmpty()) {
                s.status = undo ? "没有可撤销的规划编辑" : "没有可重做的规划编辑";
                return;
            }
            remember(undo ? s.redoEdits : s.undoEdits, new EditState(s));
            from.remove(from.size() - 1)
                .apply(s);
            refreshPlan(s);
            s.status = undo ? "已撤销规划编辑；已施工的世界保持现状" : "已重做规划编辑；点击施工才改变世界";
            return;
        }
        int index = action.hasKey("selected", 3) ? action.getInteger("selected") : s.selected;
        if (index < 0 || index >= s.capture.cells.size()) {
            s.status = "请先选择结构格";
            return;
        }
        HologramCapture.Cell cell = s.capture.cells.get(index);
        if (cell.anchor) {
            s.status = "控制器始终受保护";
            return;
        }
        if ("editCopy".equals(op) || "editCut".equals(op)) {
            ItemStack sample = targetFor(s, cell, index);
            TileEntity actual = s.player.worldObj.blockExists(cell.x, cell.y, cell.z)
                ? s.player.worldObj.getTileEntity(cell.x, cell.y, cell.z)
                : null;
            if (!explicitTarget(s, cell, index) && !s.autoTargets.containsKey(index)
                && actual instanceof IGregTechTileEntity
                && ((IGregTechTileEntity) actual).getMetaTileEntity() instanceof MTEHatch)
                sample = new ItemStack(GregTechAPI.sBlockMachines, 1, ((IGregTechTileEntity) actual).getMetaTileID());
            if (!acceptableSample(s, cell, sample)) {
                s.status = "本格没有可复制的放置目标";
                return;
            }
            if (HologramRecovery.hasSeal(sample)) {
                s.status = "封存设备携带真实库存，不能复制或剪切；请从普通仓室目标复制";
                return;
            }
            // Item identity only: inventories, fluids, covers and arbitrary item NBT never enter clipboard.
            ItemStack clean = new ItemStack(sample.getItem(), 1, sample.getItemDamage());
            int direction = s.facingPins.getOrDefault(position(cell), s.planFacings.getOrDefault(index, -1));
            if (direction < 0 && sample.getItem() instanceof ItemMachines
                && actual instanceof IGregTechTileEntity
                && ((IGregTechTileEntity) actual).getMetaTileID() == sample.getItemDamage())
                direction = ((IGregTechTileEntity) actual).getFrontFacing()
                    .ordinal();
            if ("editCut".equals(op) && !restoreCell(s, cell, index)) return;
            s.editClipboard = clean;
            s.editClipboardFacing = direction;
            s.status = "editCopy".equals(op) ? "已复制目标物品与朝向，不复制设备库存" : "已剪切规划目标；现场设备保留至施工";
            return;
        }
        if ("restoreCell".equals(op)) {
            if (restoreCell(s, cell, index)) s.status = "本格已设为原结构默认外壳；施工时安全回收旧设备";
            return;
        }
        if ("editPaste".equals(op)) {
            ItemStack sample = s.editClipboard;
            if (!acceptableSample(s, cell, sample)) {
                s.status = "剪贴板为空或该目标不可放置";
                return;
            }
            int direction = s.editClipboardFacing;
            int[] legal = legalFacings(s, cell, sample);
            if (hatchTier(sample) != Integer.MAX_VALUE && legal.length == 0) {
                s.status = "本格没有适用于剪贴板仓室的合法朝向";
                return;
            }
            if (legal.length > 0 && direction != -1) {
                boolean accepted = false;
                for (int side : legal) if (side == direction) accepted = true;
                if (!accepted) {
                    s.status = "剪贴板朝向不适用于本格，请先调整源格朝向";
                    return;
                }
            }
            s.pins.put(index, sample.copy());
            s.coordinatePins.put(position(cell), sample.copy());
            s.suppressedAuto.add(position(cell));
            s.facingPins.remove(position(cell));
            if (legal.length > 0 && direction >= 0) s.facingPins.put(position(cell), direction);
            if (sample.getItem() instanceof ItemMachines) s.noHatches = false;
            s.selected = index;
            s.scope = 2;
            s.mode = 1;
            refreshPlan(s);
            s.status = "已粘贴目标规划；施工时才扣除真实材料";
            return;
        }
        int[] legal = legalFacings(s, cell, targetFor(s, cell, index));
        if (legal.length < 2) {
            s.status = "本格目标没有可轮换的合法朝向";
            return;
        }
        int current = s.facingPins.getOrDefault(position(cell), s.planFacings.getOrDefault(index, -1));
        int next = legal[0];
        for (int i = 0; i < legal.length; i++) if (legal[i] == current) next = legal[(i + 1) % legal.length];
        s.facingPins.put(position(cell), next);
        s.selected = index;
        refreshPlan(s);
        s.status = "已轮换到下一合法朝向；施工时生效";
    }

    private static boolean restoreCell(Session s, HologramCapture.Cell cell, int index) {
        if ("templateController".equals(cell.role)) {
            s.status = "模板控制器不能恢复外壳";
            return false;
        }
        HologramCasingFallback.Target casing = HologramCasingFallback.resolve(cell.element);
        ItemStack target = casing == null ? null : new ItemStack(casing.block, 1, casing.meta);
        if (s.virtual && s.virtualPreset != null) for (HologramPresets.Cell saved : s.virtualPreset.cells)
            if (saved.x == cell.x - s.x && saved.y == cell.y - s.y && saved.z == cell.z - s.z)
                target = saved.defaultStack();
        if (target == null && !cell.userPosition
            && !cell.unknown
            && !"hatch".equals(cell.role)
            && cell.block != null
            && cell.block != Blocks.air) {
            ItemStack original = new ItemStack(cell.block, 1, cell.meta);
            if (!(original.getItem() instanceof ItemMachines) && acceptableSample(s, cell, original)) target = original;
        }
        if (!acceptableSample(s, cell, target)) {
            s.status = "本格没有可确认的结构默认外壳，规划未修改";
            return false;
        }
        s.pins.put(index, target.copy());
        s.coordinatePins.put(position(cell), target.copy());
        s.facingPins.remove(position(cell));
        s.suppressedAuto.add(position(cell));
        s.selected = index;
        s.scope = 2;
        s.mode = 1;
        refreshPlan(s);
        return true;
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
            if (!valid(s) || (!s.virtual && (s.machine == null || s.machine.getExtendedFacing()
                .ordinal() != s.facing))) {
                clearPending(s);
                s.job = 4;
                s.status = "工具、目标或控制器朝向已变化，请重新采集";
                send(s);
                continue;
            }
            s.materialContext = new HologramMaterials.Context(s.player, s.materialSettings);
            s.materialContext.flushRecovery();
            s.ackIndices.clear();
            s.ackSuccesses.clear();
            while (s.cursor < s.work.size()) {
                int index = s.work.get(s.cursor);
                HologramCapture.Cell cell = s.capture.cells.get(index);
                boolean unchanged = expectedCurrent(s, index);
                boolean success = unchanged && operate(s, cell, index);
                if (!unchanged) {
                    cell.status = "protected";
                    s.status = "现场已变化，请重新扫描";
                }
                if (s.defer) {
                    s.defer = false;
                    if (s.deferredIndex != index) {
                        s.deferredIndex = index;
                        s.deferAttempts = 0;
                    }
                    if (++s.deferAttempts < 3) break;
                    cell.status = "unsupported";
                    s.status = "原版元素持续要求等待，请重新规划或手动施工";
                }
                s.deferredIndex = -1;
                s.deferAttempts = 0;
                s.cursor++;
                boolean changed = success && ("placed".equals(cell.status) || "replaced".equals(cell.status)
                    || "removed".equals(cell.status));
                if (changed) s.completed++;
                else if (!success && "pending".equals(cell.status)) cell.status = "unsupported";
                s.ackIndices.add(index);
                s.ackSuccesses.add(changed ? 1 : 0);
                s.ackIndex = index;
                s.ackSuccess = changed;
                if (s.stopTick) {
                    s.stopTick = false;
                    break;
                }
            }
            s.phase = "ACK";
            s.batchSeq++;
            if (s.cursor >= s.work.size()) {
                int unresolved = 0;
                for (int i = 0; i < s.capture.cells.size(); i++)
                    if (inScope(s, s.capture.cells.get(i), i) && !s.capture.cells.get(i).anchor) {
                        String status = s.capture.cells.get(i).status;
                        if (!"satisfied".equals(status) && !"placed".equals(status)
                            && !"replaced".equals(status)
                            && !"removed".equals(status)) unresolved++;
                    }
                s.job = unresolved == 0 ? 3 : 5;
                s.status = unresolved == 0 ? "范围内施工完成（未执行机器成型检查）" : "部分完成：请检查未完成格的原因并重新规划";
            }
            // One full result per batch, never one packet per block.
            send(s);
        }
    }

    private static void nativeBuild(Session s) {
        if (s.virtual) {
            s.status = "虚拟预设仅支持安全逐格施工";
            return;
        }
        if (s.player.capabilities.isCreativeMode && s.constructable != null && !s.noHatches) {
            s.constructable.construct(trigger(s), false);
            scan(s);
            s.status = "沿用原版机器创造构造行为；不属于高级安全差分施工";
        } else if (s.constructable instanceof ISurvivalConstructable) {
            IItemSource nativeSource = s.player.capabilities.isCreativeMode
                ? new CreativeSource(Collections.emptyList())
                : materialSource(s);
            if (s.noHatches) nativeSource = new ShellSource(nativeSource);
            int result = ((ISurvivalConstructable) s.constructable)
                .survivalConstruct(trigger(s), BUDGET, ISurvivalBuildEnvironment.create(nativeSource, s.player));
            scan(s);
            s.status = result == -2 ? "原版生存搭建不支持此机器" : "沿用原版机器生存构造行为；不属于高级安全差分施工，请重新扫描";
        } else s.status = "该机器未提供原版生存搭建接口";
    }

    private static String position(HologramCapture.Cell cell) {
        return cell.x + "," + cell.y + "," + cell.z;
    }

    private static void addPosition(Session s, NBTTagCompound action) {
        if ((!s.virtual && s.target == null) || s.capture == null || s.capture.incomplete || s.capturePending) {
            s.status = "请先完成结构采集";
            return;
        }
        int dx = action.getInteger("dx"), dy = action.getInteger("dy"), dz = action.getInteger("dz");
        if (!action.hasKey("dx", 3) || !action.hasKey("dy", 3)
            || !action.hasKey("dz", 3)
            || dx < -64
            || dx > 64
            || dy < -64
            || dy > 64
            || dz < -64
            || dz > 64
            || dx * dx + dy * dy + dz * dz > 4096) {
            s.status = "位置必须在控制器 64 格半径内";
            return;
        }
        int x = s.x + dx, y = s.y + dy, z = s.z + dz;
        if (!s.player.worldObj.blockExists(x, y, z) || y < 0 || y >= s.player.worldObj.getHeight()) {
            s.status = "该位置未加载或超出世界范围";
            return;
        }
        for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell cell = s.capture.cells.get(i);
            if (cell.x == x && cell.y == y && cell.z == z) {
                s.selected = i;
                s.scope = 2;
                refreshPlan(s);
                s.status = cell.anchor ? "控制器始终受保护" : "已选择该位置，可从背包指定目标";
                return;
            }
        }
        if (s.capture.cells.size() >= HologramCapture.LIMIT) {
            s.status = "结构位置已达到 4096 格上限";
            return;
        }
        HologramCapture.Cell cell = new HologramCapture.Cell(null, x, y, z);
        cell.block = Blocks.air;
        cell.userPosition = true;
        cell.role = "manual";
        s.manualPositions.put(position(cell), cell);
        s.capture.cells.add(cell);
        s.selected = s.capture.cells.size() - 1;
        s.scope = 2;
        refreshPlan(s);
        s.status = "位置已添加；指定目标后才会施工";
    }

    private static void directBuild(Session s) {
        if (s.virtual && (!s.virtualLocked || s.mode == 2)) {
            s.status = s.mode == 2 ? "虚拟预设不支持拆除" : "请先锁定虚拟结构位置";
            return;
        }
        scan(s);
        if (s.capture == null || s.capture.incomplete || s.capturePending) {
            cancelCaptureRetry(s);
            if (s.mode == 0) nativeBuild(s);
            else s.status = "替换和拆除需要完整结构采集";
            return;
        }
        if ((s.scope == 2 || s.scope == 3) && (s.selected < 0 || s.selected >= s.capture.cells.size())) {
            s.status = "请先选择施工位置";
            return;
        }
        if (s.scope == 3 && s.capture.cells.get(s.selected).family.isEmpty()) {
            s.status = "选中格没有同族范围，请使用单格范围";
            return;
        }
        refreshResources(s);
        if (s.planMissing > 0 || !s.planRecoveryFits) {
            s.status = s.planMissing > 0 ? "施工材料不足，请补齐后重试" : "背包无法容纳回收，请整理后重试";
            return;
        }
        s.work.clear();
        for (int i = 0; i < s.capture.cells.size(); i++) {
            if (inScope(s, s.capture.cells.get(i), i) && "pending".equals(s.planStatus.get(i))) s.work.add(i);
        }
        if (s.work.isEmpty() && s.mode == 0 && !s.virtual) {
            nativeBuild(s);
            return;
        }
        s.cursor = 0;
        s.completed = 0;
        s.job = 1;
        s.phase = "RUNNING";
        s.status = "正在直接施工";
    }

    private static void clearPending(Session s) {
        s.ackIndices.clear();
        s.ackSuccesses.clear();
        s.work.clear();
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
            && (!s.virtual || (s.y >= 0 && s.y < s.player.worldObj.getHeight()
                && s.player.getDistanceSq(s.x + .5, s.y + .5, s.z + .5) <= 4096))
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
        if (HologramFrameSupport.accepts(c.element, old, meta)) return true;
        if (old instanceof BlockFrameBox && !old.hasTileEntity(meta)
            && old == c.block
            && meta == c.meta
            && factory(c.element, "ofBlock", new IdentityHashMap<>(), 0)) return true;
        HologramReplacementFamily.Family family = HologramReplacementFamily.resolve(c.element, c.block, c.meta);
        if (family != null && family.contains(old, meta)) return true;
        if (coil(c.block, c.meta) && coil(old, meta)) return factory(c.element, "ofCoil", new IdentityHashMap<>(), 0);
        return old == c.block && meta == c.meta
            && !old.hasTileEntity(meta)
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
                    (name.equals("ofCoil") || name.equals("ofFrame")) ? "gregtech.api.util.GTStructureUtility"
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
        if (pin != null) return pin.copy();
        if (c.userPosition) return null;
        ItemStack automatic = s.autoTargets.get(index);
        if (!s.noHatches && automatic != null) return automatic.copy();
        TileEntity actual = s.player.worldObj.getTileEntity(c.x, c.y, c.z);
        if (!s.noHatches && actual instanceof IGregTechTileEntity && !c.anchor) {
            ItemStack existing = new ItemStack(
                GregTechAPI.sBlockMachines,
                1,
                ((IGregTechTileEntity) actual).getMetaTileID());
            if (hatchElement(s, c, existing, true) != null) return existing;
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
        ItemStack target = targetFor(s, c, index);
        if (s.facingPins.containsKey(position(c))) {
            TileEntity actual = s.player.worldObj.getTileEntity(c.x, c.y, c.z);
            if (!(actual instanceof IGregTechTileEntity) || ((IGregTechTileEntity) actual).getFrontFacing()
                .ordinal() != s.facingPins.get(position(c))) return false;
        }
        if (explicitTarget(s, c, index) && HologramRecovery.hasSeal(target)) return HologramRecovery
            .matchesPlaced(s.player.worldObj, c.x, c.y, c.z, target, s.facingPins.getOrDefault(position(c), -1));
        if (s.facingPins.containsKey(position(c)) || s.autoTargets.containsKey(index)
            || explicitGrade(s, c)
            || explicitTarget(s, c, index)) return targetMatches(s, c, index, old, meta);
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
        if (old != Block.getBlockFromItem(wanted.getItem())) return false;
        if (explicitTarget(s, c, index)) {
            // Direction and half-block placement metadata need not equal the item's damage value.
            try {
                return old.getDamageValue(s.player.worldObj, c.x, c.y, c.z) == wanted.getItemDamage();
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
        }
        return meta == wanted.getItem()
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
            if (index >= 0 && (explicitTarget(s, c, index) || s.autoTargets.containsKey(index))) {
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
        if (c.y < 0 || c.y >= w.getHeight() || !w.blockExists(c.x, c.y, c.z)) return "unsupported";
        if (c.anchor) return "protected";
        if (c.userPosition && !explicitTarget(s, c, index)) return "satisfied";
        if (s.mode == 2 && c.block == Blocks.air && !explicitTarget(s, c, index)) return "satisfied";
        if (w.getTileEntity(c.x, c.y, c.z) != null && !recoverable(s, c)) return "protected";
        if (!w.canMineBlock(s.player, c.x, c.y, c.z) || !s.player.canPlayerEdit(c.x, c.y, c.z, 1, trigger(s)))
            return "protected";
        boolean empty = trivial(w, c.x, c.y, c.z);
        if (s.mode == 2) return empty ? "satisfied" : removable(s, c) ? "pending" : "protected";
        if ((c.element == null && !explicitTarget(s, c, index)) || (c.unknown && !explicitTarget(s, c, index)
            && !s.autoTargets.containsKey(index)
            && !(!s.noHatches && s.channels.hasKey("gt_hatch", 3) && "hatch".equals(c.role)))) return "unsupported";
        Block old = w.getBlock(c.x, c.y, c.z);
        int meta = w.getBlockMetadata(c.x, c.y, c.z);
        if (targetSatisfied(s, c, index, old, meta)) return "satisfied";
        if (s.facingPins.containsKey(position(c)) && targetMatches(s, c, index, old, meta)) return "pending";
        ItemStack target = targetFor(s, c, index);
        if (target == null || Block.getBlockFromItem(target.getItem()) == Blocks.air) return "unsupported";
        if (explicitTarget(s, c, index)) {
            if (!acceptableSample(s, c, target)) return "unsupported";
        }
        if (empty) return "pending";
        if ((s.mode == 0 && (s.virtual || !explicitTarget(s, c, index))) || !removable(s, c)) return "protected";
        return acceptableSample(s, c, target) ? "pending" : "protected";
    }

    private static String reason(Session s, HologramCapture.Cell c, int index) {
        World w = s.player.worldObj;
        if (!w.blockExists(c.x, c.y, c.z)) return "区块未加载";
        if (c.anchor) return "始终保留控制器锚点";
        if (s.mode != 2 && orientationOnly(s, c, index)) return "仅调整现有仓室朝向，不拆除、不扣材料";
        if (c.userPosition && !explicitTarget(s, c, index)) return "新增位置尚未指定目标，保持现场";
        if (s.mode == 2 && c.block == Blocks.air && !explicitTarget(s, c, index)) return "声明空气位置保持现场";
        if (w.getTileEntity(c.x, c.y, c.z) != null)
            return recoverable(s, c) ? "完整封存设备状态、库存、流体与覆盖板后回收" : "此设备尚无完整状态回收路径";
        if ("unsupported".equals(c.status) || (c.element == null && !explicitTarget(s, c, index)))
            return "此元素未提供安全施工目标";
        if ("protected".equals(c.status)) return "权限限制或旧块不属于受支持结构族";
        if ("satisfied".equals(c.status)) return "现有方块满足目标，保留";
        return "逐格重验；Forge 事件仅在实际操作时处理";
    }

    private static void refreshPlan(Session s) {
        s.job = 0;
        s.cursor = 0;
        s.completed = 0;
        s.recovered.clear();
        s.defer = false;
        s.stopTick = false;
        clearPending(s);
        s.status = "差分已更新，可查看材料、回收与保护原因";
        s.planRevision++;
        s.expectedBlocks.clear();
        s.expectedMeta.clear();
        s.expectedTiles.clear();
        s.expectedFacings.clear();
        s.planStatus.clear();
        s.planTargets.clear();
        s.planMissing = 0;
        s.planRecoveryFits = true;
        if (s.capture == null) return;
        World w = s.player.worldObj;
        if (s.virtual) allocateVirtualTargets(s);
        buildPreset(s);
        if (s.mode == 2) s.planFacings.clear();
        else refreshFacings(s);
        for (int i = 0; i < s.capture.cells.size(); i++) s.planTargets.add(draftTarget(s, s.capture.cells.get(i), i));
        for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell c = s.capture.cells.get(i);
            boolean loaded = w.blockExists(c.x, c.y, c.z);
            s.expectedBlocks.add(loaded ? w.getBlock(c.x, c.y, c.z) : Blocks.air);
            s.expectedMeta.add(loaded ? w.getBlockMetadata(c.x, c.y, c.z) : 0);
            TileEntity tile = loaded ? w.getTileEntity(c.x, c.y, c.z) : null;
            s.expectedTiles.add(tile);
            s.expectedFacings.add(
                tile instanceof IGregTechTileEntity ? ((IGregTechTileEntity) tile).getFrontFacing()
                    .ordinal() : -1);
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
            if (!inScope(s, c, i) || !"pending".equals(c.status)) continue;
            ItemStack target = targetFor(s, c, i);
            if (s.mode != 2 && orientationOnly(s, c, i)) continue;
            if (s.mode != 2 && (!s.player.capabilities.isCreativeMode || HologramRecovery.hasSeal(target))) {
                String key = stackKey(target);
                stacks.put(key, target);
                counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
            }
            if (!trivial(w, c.x, c.y, c.z)
                && (!s.player.capabilities.isCreativeMode || w.getTileEntity(c.x, c.y, c.z) != null)) try {
                    recovery.addAll(drops(w.getBlock(c.x, c.y, c.z), w, c, w.getBlockMetadata(c.x, c.y, c.z)));
                } catch (RuntimeException | LinkageError failure) {
                    s.planRecoveryFits = false;
                }
        }
        for (Map.Entry<String, ItemStack> entry : stacks.entrySet()) {
            ItemStack stack = entry.getValue();
            int available = materialSource(s).count(stack, 4096);
            s.planMissing += Math.max(0, counts.get(entry.getKey()) - available);
        }
        // Committed drops can remain in the player's persistent recovery queue when destinations are full.
    }

    private static String stackKey(ItemStack stack) {
        return stack.getItem() + ":" + stack.getItemDamage() + ":" + stack.getTagCompound();
    }

    private static boolean expectedCurrent(Session s, int index) {
        HologramCapture.Cell c = s.capture.cells.get(index);
        World w = s.player.worldObj;
        return w.blockExists(c.x, c.y, c.z) && w.getBlock(c.x, c.y, c.z) == s.expectedBlocks.get(index)
            && w.getBlockMetadata(c.x, c.y, c.z) == s.expectedMeta.get(index)
            && w.getTileEntity(c.x, c.y, c.z) == s.expectedTiles.get(index)
            && (!(s.expectedTiles.get(index) instanceof IGregTechTileEntity)
                || ((IGregTechTileEntity) s.expectedTiles.get(index)).getFrontFacing()
                    .ordinal() == s.expectedFacings.get(index));
    }

    private static boolean planCurrent(Session s) {
        if (s.capture == null || s.expectedBlocks.size() != s.capture.cells.size()) return false;
        for (int i = 0; i < s.capture.cells.size(); i++)
            if (inScope(s, s.capture.cells.get(i), i) && !expectedCurrent(s, i)) return false;
        return true;
    }

    private static boolean operate(Session s, HologramCapture.Cell c, int index) {
        try {
            return operateChecked(s, c, index);
        } catch (RuntimeException | LinkageError failure) {
            c.status = "unsupported";
            s.status = "设备状态或回收数据无法读取，该格未施工";
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean operateChecked(Session s, HologramCapture.Cell c, int index) {
        World w = s.player.worldObj;
        if (c.y < 0 || c.y >= w.getHeight() || !w.blockExists(c.x, c.y, c.z)) {
            c.status = "unsupported";
            s.status = "区块未加载";
            return false;
        }
        Block old = w.getBlock(c.x, c.y, c.z);
        int oldMeta = w.getBlockMetadata(c.x, c.y, c.z);
        if (c.anchor || (w.getTileEntity(c.x, c.y, c.z) != null && !recoverable(s, c))) {
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
        if (s.mode != 2 && orientationOnly(s, c, index)) {
            IGregTechTileEntity actual = (IGregTechTileEntity) w.getTileEntity(c.x, c.y, c.z);
            ForgeDirection before = actual.getFrontFacing();
            try {
                int requested = s.facingPins.get(position(c));
                boolean legal = false;
                for (int allowed : legalFacings(s, c, targetFor(s, c, index))) if (allowed == requested) legal = true;
                if (!legal) throw new IllegalStateException("illegal facing");
                actual.setFrontFacing(ForgeDirection.getOrientation(requested));
                if (actual.getFrontFacing()
                    .ordinal() != requested) throw new IllegalStateException("facing rejected");
                c.status = "replaced";
                return true;
            } catch (RuntimeException | LinkageError failure) {
                actual.setFrontFacing(before);
                c.status = "protected";
                s.status = "朝向修改失败，已恢复原方向";
                return false;
            }
        }
        if (!empty && ((s.mode == 0 && (s.virtual || !explicitTarget(s, c, index))) || !removable(s, c))) {
            c.status = "protected";
            return true;
        }
        if (s.mode != 2 && ((c.unknown && !explicitTarget(s, c, index)
            && !s.autoTargets.containsKey(index)
            && !(!s.noHatches && s.channels.hasKey("gt_hatch", 3) && "hatch".equals(c.role)))
            || (c.element == null && !explicitTarget(s, c, index))
            || targetFor(s, c, index) == null)) {
            c.status = "unsupported";
            return true;
        }
        TileEntity originalTile = w.getTileEntity(c.x, c.y, c.z);
        List<ItemStack> drops = empty
            || (s.player.capabilities.isCreativeMode && w.getTileEntity(c.x, c.y, c.z) == null)
                ? Collections.emptyList()
                : drops(old, w, c, oldMeta);
        ItemStack[] inventory = copyInventory(s.player);
        NBTTagCompound savedTile = w.getTileEntity(c.x, c.y, c.z) == null ? null
            : (NBTTagCompound) drops.get(0)
                .getTagCompound()
                .getCompoundTag(HologramRecovery.TAG)
                .getCompoundTag("tile")
                .copy();
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(w, c.x, c.y, c.z);
        HologramMaterials.Transaction transaction = materialSource(s).begin();
        ItemStack reserved = null;
        boolean changedWorld = false;
        try {
            if (s.mode != 2 && !empty
                && (!s.player.capabilities.isCreativeMode || HologramRecovery.hasSeal(targetFor(s, c, index)))) {
                ItemStack required = targetFor(s, c, index);
                if (transaction.takeOne(required, false)) reserved = required.copy();
                if (reserved == null) {
                    rollbackMaterials(s, transaction, inventory);
                    c.status = "missing";
                    s.status = "替换材料不足，旧方块已保留";
                    return false;
                }
            }
            if (!empty) {
                BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(c.x, c.y, c.z, w, old, oldMeta, s.player);
                if (MinecraftForge.EVENT_BUS.post(event)) {
                    rollbackMaterials(s, transaction, inventory);
                    c.status = "protected";
                    return true;
                }
                if (!sameWorldCell(w, c, old, oldMeta, originalTile)) {
                    rollbackMaterials(s, transaction, inventory);
                    c.status = "protected";
                    s.status = "拆除事件已改变现场，该格保留，请重新扫描";
                    return true;
                }
                if (originalTile != null) {
                    // A listener may change a hatch inventory/fluid without changing the block identity.
                    // Capture its latest state, rather than rolling back or recovering the obsolete preview.
                    drops = HologramRecovery.drops(w, c.x, c.y, c.z);
                    savedTile = (NBTTagCompound) drops.get(0)
                        .getTagCompound()
                        .getCompoundTag(HologramRecovery.TAG)
                        .getCompoundTag("tile")
                        .copy();
                    snapshot = BlockSnapshot.getBlockSnapshot(w, c.x, c.y, c.z);
                }
                boolean tile = originalTile != null;
                boolean removed;
                if (tile) {
                    changedWorld = true;
                    removed = HologramRecovery.removeWithoutDrops(w, c.x, c.y, c.z);
                } else {
                    boolean frame = old instanceof BlockFrameBox;
                    if (!frame) {
                        old.onBlockHarvested(w, c.x, c.y, c.z, oldMeta, s.player);
                        if (!sameWorldCell(w, c, old, oldMeta, originalTile)) {
                            rollbackMaterials(s, transaction, inventory);
                            c.status = "protected";
                            s.status = "采收回调已改变现场，该格保留，请重新扫描";
                            return true;
                        }
                    }
                    changedWorld = true;
                    removed = frame ? w.setBlockToAir(c.x, c.y, c.z)
                        : old.removedByPlayer(w, s.player, c.x, c.y, c.z, false);
                    if (removed) old.onBlockDestroyedByPlayer(w, c.x, c.y, c.z, oldMeta);
                }
                if (!removed) {
                    HologramRecovery.restore(w, c.x, c.y, c.z, old, oldMeta, savedTile);
                    rollbackMaterials(s, transaction, inventory);
                    c.status = "protected";
                    return true;
                }
            }
            if (s.mode != 2) {
                changedWorld = true;
                final ItemStack material = reserved;
                ReservedSource reservation = material == null ? null : new ReservedSource(material);
                IItemSource source = reservation == null ? transaction : reservation;
                // Lock the pure plan's chosen material. Native auto-selection must not consume a different item.
                ItemStack pin = targetFor(s, c, index);
                if (s.player.capabilities.isCreativeMode && !HologramRecovery.hasSeal(pin))
                    source = new CreativeSource(Collections.singletonList(pin));
                if (pin != null && material == null) source = new FilteredSource(source, pin);
                if (s.noHatches && !explicitTarget(s, c, index)) source = new ShellSource(source);
                boolean directCreative = s.player.capabilities.isCreativeMode && !s.noHatches && pin == null;
                boolean customBlock = explicitTarget(s, c, index) && pin != null
                    && !(pin.getItem() instanceof ItemMachines)
                    && acceptableSample(s, c, pin);
                PlaceResult result;
                if (directCreative) result = c.element.placeBlock(s.machine, w, c.x, c.y, c.z, elementTrigger(s, c))
                    ? PlaceResult.ACCEPT
                    : PlaceResult.REJECT;
                else if (customBlock) {
                    result = StructureUtility.survivalPlaceBlock(
                        pin,
                        ItemStackPredicate.NBTMode.EXACT,
                        null,
                        false,
                        w,
                        c.x,
                        c.y,
                        c.z,
                        source,
                        s.player,
                        s.player::addChatMessage);
                } else if ((explicitTarget(s, c, index) || s.autoTargets.containsKey(index))
                    && pin.getItem() instanceof ItemMachines
                    && acceptableSample(s, c, pin)) {
                        // Explicit overrides use the legal GT item directly, without structure candidate filters.
                        // Manual choices do not use the automatic atLeast fill quota.
                        result = StructureUtility.survivalPlaceBlock(
                            pin,
                            ItemStackPredicate.NBTMode.EXACT,
                            null,
                            true,
                            w,
                            c.x,
                            c.y,
                            c.z,
                            source,
                            s.player);
                    } else {
                        IStructureElement placement = pin.getItem() instanceof ItemMachines
                            ? hatchElement(s, c, pin, false)
                            : c.element;
                        result = placement == null ? PlaceResult.REJECT
                            : placement.survivalPlaceBlock(
                                s.machine,
                                w,
                                c.x,
                                c.y,
                                c.z,
                                elementTrigger(s, c),
                                AutoPlaceEnvironment.fromLegacy(source, s.player, s.player::addChatMessage));
                    }
                if (result == PlaceResult.STOP) {
                    HologramRecovery.restore(w, c.x, c.y, c.z, old, oldMeta, savedTile);
                    rollbackMaterials(s, transaction, inventory);
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
                    HologramRecovery.restore(w, c.x, c.y, c.z, old, oldMeta, savedTile);
                    rollbackMaterials(s, transaction, inventory);
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
                if (!HologramRecovery.restorePlaced(w, c.x, c.y, c.z, pin)
                    || (HologramRecovery.hasSeal(pin) && !HologramRecovery.matchesPlaced(w, c.x, c.y, c.z, pin)))
                    throw new IllegalStateException("stored device restore rejected");
                Integer direction = s.planFacings.get(index);
                TileEntity placedTile = w.getTileEntity(c.x, c.y, c.z);
                if (direction != null && direction >= 0
                    && placedTile instanceof IGregTechTileEntity
                    && (!HologramRecovery.hasSeal(pin) || s.facingPins.containsKey(position(c)))) {
                    IGregTechTileEntity gt = (IGregTechTileEntity) placedTile;
                    if (c.anchor || !gt.getMetaTileEntity()
                        .isFacingValid(ForgeDirection.getOrientation(direction)))
                        throw new IllegalStateException("hatch facing rejected");
                    gt.setFrontFacing(ForgeDirection.getOrientation(direction));
                    if (gt.getFrontFacing()
                        .ordinal() != direction) throw new IllegalStateException("hatch facing failed");
                }
                if (MinecraftForge.EVENT_BUS.post(new BlockEvent.PlaceEvent(snapshot, old, s.player))) {
                    HologramRecovery.restore(w, c.x, c.y, c.z, old, oldMeta, savedTile);
                    rollbackMaterials(s, transaction, inventory);
                    c.status = "protected";
                    return true;
                }
            }
            transaction.commit();
            s.player.inventory.markDirty();
            c.status = s.mode == 2 ? "removed" : empty ? "placed" : "replaced";
        } catch (RuntimeException | LinkageError e) {
            if (changedWorld) HologramRecovery.restore(w, c.x, c.y, c.z, old, oldMeta, savedTile);
            rollbackMaterials(s, transaction, inventory);
            s.status = "施工异常，已还原该格；请检查部分完成结果";
            return false;
        }
        // Delivery is outside the rollback region: ME insertion is an external, committed transfer.
        materialSource(s).recoverWorld(drops);
        for (ItemStack drop : drops) s.recovered.add(drop.copy());
        try {
            materialSource(s).flushRecovery();
        } catch (RuntimeException | LinkageError deliveryFailure) {
            s.status = "施工已完成；回收物保留在持久暂存，稍后继续交付";
        }
        return true;
    }

    private static boolean sameWorldCell(World world, HologramCapture.Cell cell, Block block, int meta,
        TileEntity tile) {
        return world.blockExists(cell.x, cell.y, cell.z) && world.getBlock(cell.x, cell.y, cell.z) == block
            && world.getBlockMetadata(cell.x, cell.y, cell.z) == meta
            && world.getTileEntity(cell.x, cell.y, cell.z) == tile;
    }

    private static List<ItemStack> drops(Block block, World world, HologramCapture.Cell cell, int meta) {
        if (world.getTileEntity(cell.x, cell.y, cell.z) != null)
            return HologramRecovery.drops(world, cell.x, cell.y, cell.z);
        if (block instanceof BlockFrameBox && !block.hasTileEntity(meta))
            return Collections.singletonList(((BlockFrameBox) block).getStackForm(1, meta));
        return block.getDrops(world, cell.x, cell.y, cell.z, meta, 0);
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
        // Large editor drafts are tool storage, not native structure signals copied into every captured cell.
        if (result.hasTagCompound()) result.getTagCompound()
            .removeTag(DRAFT);
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
        HologramMaterials.write(held, s.materialSettings);
        held.getTagCompound()
            .setInteger("gtitHologramMain", s.main);
        held.getTagCompound()
            .setBoolean("gtitHologramMainExplicit", s.mainExplicit);
        held.getTagCompound()
            .setBoolean("gtitHologramDowngrade", s.hatchDowngrade);
        List<String> existing = new ArrayList<>();
        ChannelDataAccessor.iterateChannelData(held)
            .forEach(entry -> existing.add(entry.getKey()));
        for (String name : existing) ChannelDataAccessor.unsetChannelData(held, name);
        for (Object key : s.channels.func_150296_c())
            ChannelDataAccessor.setChannelData(held, (String) key, s.channels.getInteger((String) key));
        writeDraft(s, held);
        s.player.inventory.markDirty();
    }

    private static String controllerType(Session s) {
        if (s.target instanceof IGregTechTileEntity) return s.context.getClass()
            .getName() + ":" + ((IGregTechTileEntity) s.target).getMetaTileID();
        return s.target == null ? ""
            : s.target.getClass()
                .getName();
    }

    private static String selectedPreset(Session s) {
        ItemStack held = currentTool(s);
        return held == null ? ""
            : held.getTagCompound()
                .getString(SELECTED_PRESET);
    }

    private static boolean draftMatches(Session s, NBTTagCompound draft) {
        return !s.virtual && s.target != null
            && draft.getInteger("dimension") == s.dimension
            && draft.getInteger("x") == s.x
            && draft.getInteger("y") == s.y
            && draft.getInteger("z") == s.z
            && draft.getString("controllerType")
                .equals(controllerType(s));
    }

    private static void writeDraft(Session s, ItemStack held) {
        if (s.virtual || s.target == null || s.capture == null || s.capture.incomplete || s.capturePending) return;
        NBTTagCompound draft = new NBTTagCompound();
        draft.setInteger("dimension", s.dimension);
        draft.setInteger("x", s.x);
        draft.setInteger("y", s.y);
        draft.setInteger("z", s.z);
        draft.setString("controllerType", controllerType(s));
        draft.setInteger("main", s.main);
        draft.setBoolean("mainExplicit", s.mainExplicit);
        draft.setTag("channels", s.channels.copy());
        draft.setBoolean("noHatches", s.noHatches);
        draft.setInteger("tier", s.hatchTier);
        draft.setBoolean("downgrade", s.hatchDowngrade);
        draft.setBoolean("presetEnabled", s.presetEnabled);
        draft.setBoolean("presetPaused", s.presetPaused);
        draft.setInteger("mode", s.mode);
        draft.setInteger("scope", s.scope);
        draft.setBoolean("materialMain", s.materialSettings.main);
        draft.setBoolean("materialContainers", s.materialSettings.containers);
        draft.setBoolean("materialMe", s.materialSettings.me);
        draft.setInteger("materialPriority", s.materialSettings.priority);
        draft.setInteger("recoveryDestination", s.materialSettings.recoveryDestination);
        NBTTagList edits = new NBTTagList();
        for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell cell = s.capture.cells.get(i);
            if (cell.anchor) continue;
            String key = position(cell);
            ItemStack target = s.pins.get(i);
            if (target == null && !s.facingPins.containsKey(key) && !s.suppressedAuto.contains(key)) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("dx", cell.x - s.x);
            entry.setInteger("dy", cell.y - s.y);
            entry.setInteger("dz", cell.z - s.z);
            if (target != null && !HologramRecovery.hasSeal(target)) {
                entry.setString("item", String.valueOf(Item.itemRegistry.getNameForObject(target.getItem())));
                entry.setInteger("damage", target.getItemDamage());
            }
            if (s.facingPins.containsKey(key)) entry.setInteger("facing", s.facingPins.get(key));
            entry.setBoolean("suppressed", s.suppressedAuto.contains(key));
            edits.appendTag(entry);
        }
        draft.setTag("edits", edits);
        held.getTagCompound()
            .setTag(DRAFT, draft);
    }

    private static void readDraft(Session s) {
        ItemStack held = currentTool(s);
        if (held == null || !held.getTagCompound()
            .hasKey(DRAFT, 10)) return;
        NBTTagCompound draft = held.getTagCompound()
            .getCompoundTag(DRAFT);
        if (!draftMatches(s, draft)) return;
        s.main = Math.max(1, draft.getInteger("main"));
        s.mainExplicit = draft.getBoolean("mainExplicit");
        s.channels = boundedChannels(draft.getCompoundTag("channels"));
        s.noHatches = draft.getBoolean("noHatches");
        s.hatchTier = clamp(draft.getInteger("tier"), 0, gregtech.api.enums.GTValues.VN.length - 1);
        s.hatchDowngrade = draft.getBoolean("downgrade");
        s.presetEnabled = draft.getBoolean("presetEnabled");
        s.presetPaused = draft.getBoolean("presetPaused");
        s.mode = clamp(draft.getInteger("mode"), 0, 2);
        s.scope = 0;
        s.materialSettings.main = draft.getBoolean("materialMain");
        s.materialSettings.containers = draft.getBoolean("materialContainers");
        s.materialSettings.me = draft.getBoolean("materialMe");
        s.materialSettings.priority = clamp(draft.getInteger("materialPriority"), 0, 2);
        s.materialSettings.recoveryDestination = clamp(draft.getInteger("recoveryDestination"), 0, 1);
        NBTTagList edits = draft.getTagList("edits", 10);
        for (int i = 0; i < Math.min(edits.tagCount(), HologramCapture.LIMIT); i++) {
            NBTTagCompound entry = edits.getCompoundTagAt(i);
            int dx = entry.getInteger("dx"), dy = entry.getInteger("dy"), dz = entry.getInteger("dz");
            if (Math.abs((long) dx) > 64 || Math.abs((long) dy) > 64
                || Math.abs((long) dz) > 64
                || (long) dx * dx + (long) dy * dy + (long) dz * dz > 4096) continue;
            HologramCapture.Cell cell = new HologramCapture.Cell(null, s.x + dx, s.y + dy, s.z + dz);
            if (dx == 0 && dy == 0 && dz == 0) continue;
            String key = position(cell);
            Object item = Item.itemRegistry.getObject(entry.getString("item"));
            ItemStack sample = item instanceof Item ? new ItemStack((Item) item, 1, entry.getInteger("damage")) : null;
            if (acceptableSample(s, cell, sample)) s.coordinatePins.put(key, sample);
            int direction = entry.getInteger("facing");
            if (entry.hasKey("facing", 3) && direction >= 0 && direction <= 5) s.facingPins.put(key, direction);
            if (entry.getBoolean("suppressed")) s.suppressedAuto.add(key);
        }
    }

    private static void resetDraft(Session s) {
        s.pins.clear();
        s.coordinatePins.clear();
        s.facingPins.clear();
        s.suppressedAuto.clear();
        s.autoTargets.clear();
        s.manualPositions.clear();
        s.undoEdits.clear();
        s.redoEdits.clear();
        s.presetEnabled = false;
        s.presetPaused = true;
        s.hatchDowngrade = false;
        s.hatchTier = 1;
        s.noHatches = false;
        s.main = 1;
        s.mainExplicit = false;
        s.channels = new NBTTagCompound();
        s.mode = 0;
        s.scope = 0;
        s.selected = -1;
        if (s.virtual) populateVirtual(s);
        else scan(s);
        s.status = "已恢复默认规划；现场方块、设备和库存保持现状";
    }

    private static void applySettings(Session s, NBTTagCompound action) {
        if (action.hasKey("materialMain")) s.materialSettings.main = action.getBoolean("materialMain");
        if (action.hasKey("materialContainers"))
            s.materialSettings.containers = action.getBoolean("materialContainers");
        if (action.hasKey("materialMe")) s.materialSettings.me = action.getBoolean("materialMe");
        if (action.hasKey("materialPriority"))
            s.materialSettings.priority = clamp(action.getInteger("materialPriority"), 0, 2);
        if (action.hasKey("hatchDowngrade")) s.hatchDowngrade = action.getBoolean("hatchDowngrade");
        if (action.hasKey("recoveryDestination"))
            s.materialSettings.recoveryDestination = clamp(action.getInteger("recoveryDestination"), 0, 1);
        if (action.hasKey("selectedPresetId")) try {
            String id = action.getString("selectedPresetId");
            if (!id.isEmpty() && HologramPresets.get(id) == null) throw new java.io.IOException("预设不存在");
            currentTool(s).getTagCompound()
                .setString(SELECTED_PRESET, id);
        } catch (java.io.IOException invalid) {
            s.status = "预设选择未保存：" + invalid.getMessage();
            return;
        }
        ItemStack tool = currentTool(s);
        if (tool != null && tool.hasTagCompound()
            && tool.getTagCompound()
                .hasKey(DRAFT, 10))
            updateDraftSettings(
                tool.getTagCompound()
                    .getCompoundTag(DRAFT),
                s.materialSettings,
                s.hatchDowngrade);
        s.materialContext = new HologramMaterials.Context(s.player, s.materialSettings);
        if (s.capture != null) refreshPlan(s);
        s.status = "设置已保存到当前投影仪";
    }

    /** Global settings also update a detached controller draft, so reopening it cannot undo the user's switches. */
    static void updateDraftSettings(NBTTagCompound draft, HologramMaterials.Settings settings, boolean downgrade) {
        draft.setBoolean("materialMain", settings.main);
        draft.setBoolean("materialContainers", settings.containers);
        draft.setBoolean("materialMe", settings.me);
        draft.setInteger("materialPriority", settings.priority);
        draft.setInteger("recoveryDestination", settings.recoveryDestination);
        draft.setBoolean("downgrade", downgrade);
    }

    private static void savePreset(Session s, String name) {
        if (s.capture == null || s.capture.incomplete
            || s.capturePending
            || s.virtual
            || s.machine == null
            || !(s.target instanceof IGregTechTileEntity)) {
            s.status = "请从实体控制器完整采集结构后保存预设";
            return;
        }
        HologramPresets.Preset preset = new HologramPresets.Preset();
        preset.name = name == null ? "" : name.trim();
        preset.controllerType = controllerType(s);
        preset.controllerId = ((IGregTechTileEntity) s.target).getMetaTileID();
        java.util.Set<String> palette = new java.util.HashSet<>();
        try {
            for (int i = 0; i < s.capture.cells.size(); i++) {
                HologramCapture.Cell cell = s.capture.cells.get(i);
                ItemStack target;
                int direction = s.facingPins.getOrDefault(position(cell), s.planFacings.getOrDefault(i, -1));
                if (cell.anchor) {
                    target = new ItemStack(GregTechAPI.sBlockMachines, 1, preset.controllerId);
                    direction = ((IGregTechTileEntity) s.target).getFrontFacing()
                        .ordinal();
                } else {
                    if (cell.block == Blocks.air && !s.pins.containsKey(i)) continue;
                    if (cell.unknown && !s.pins.containsKey(i)) {
                        s.status = "预设存在没有安全目标的未知元素，请先明确选择合法目标";
                        return;
                    }
                    target = targetFor(s, cell, i);
                    if (target == null || !acceptableSample(s, cell, target)) {
                        s.status = "存在无法安全保存的结构目标；预设未写入";
                        return;
                    }
                }
                if (!cell.anchor) {
                    TileEntity actual = s.player.worldObj.getTileEntity(cell.x, cell.y, cell.z);
                    Block placed = Block.getBlockFromItem(target.getItem());
                    if ((actual != null && !HologramRecovery.supported(actual))
                        || (!(target.getItem() instanceof ItemMachines) && placed.hasTileEntity(
                            target.getItem()
                                .getMetadata(target.getItemDamage())))) {
                        s.status = "预设含未支持的方块实体，不能保存为普通物品模板";
                        return;
                    }
                }
                HologramPresets.Cell saved = HologramPresets
                    .cell(target, cell.x - s.x, cell.y - s.y, cell.z - s.z, direction, cell.anchor);
                preset.cells.add(saved);
                addPalette(preset, palette, target);
                if (!cell.anchor) {
                    HologramCasingFallback.Target casing = HologramCasingFallback.resolve(cell.element);
                    ItemStack shell = casing == null ? null : new ItemStack(casing.block, 1, casing.meta);
                    if (shell == null && !cell.unknown
                        && !"hatch".equals(cell.role)
                        && cell.block != null
                        && cell.block != Blocks.air) shell = new ItemStack(cell.block, 1, cell.meta);
                    if (acceptableSample(s, cell, shell) && !(shell.getItem() instanceof ItemMachines)) {
                        saved.defaultItem = String.valueOf(Item.itemRegistry.getNameForObject(shell.getItem()));
                        saved.defaultDamage = shell.getItemDamage();
                        addPalette(preset, palette, shell);
                    }
                    if (cell.block != null && cell.block != Blocks.air) {
                        ItemStack original = new ItemStack(cell.block, 1, cell.meta);
                        if (acceptableSample(s, cell, original)) addPalette(preset, palette, original);
                    }
                    for (ItemStack candidate : candidates(s, cell))
                        if (acceptableSample(s, cell, candidate)) addPalette(preset, palette, candidate);
                }
            }
            // Some constructables omit the controller from their walked pieces. Save its server-known identity once.
            boolean controllerSaved = false;
            for (HologramPresets.Cell cell : preset.cells) if (cell.controller) controllerSaved = true;
            if (!controllerSaved) {
                ItemStack target = new ItemStack(GregTechAPI.sBlockMachines, 1, preset.controllerId);
                preset.cells.add(
                    HologramPresets.cell(
                        target,
                        0,
                        0,
                        0,
                        ((IGregTechTileEntity) s.target).getFrontFacing()
                            .ordinal(),
                        true));
                addPalette(preset, palette, target);
            }
            HologramPresets.save(preset);
            currentTool(s).getTagCompound()
                .setString(SELECTED_PRESET, preset.id);
            s.status = "预设已保存到服务器配置；包含控制器，施工按真实材料扣料";
        } catch (java.io.IOException invalid) {
            s.status = "预设保存失败：" + invalid.getMessage();
        }
    }

    private static void addPalette(HologramPresets.Preset preset, java.util.Set<String> keys, ItemStack stack) {
        String key = Item.itemRegistry.getNameForObject(stack.getItem()) + ":" + stack.getItemDamage();
        if (keys.add(key)) preset.palette.add(HologramPresets.cell(stack, 0, 0, 0, -1, false));
    }

    private static void loadPreset(Session s, String id) {
        try {
            HologramPresets.Preset preset = HologramPresets.get(id);
            if (preset == null) throw new java.io.IOException("预设不存在");
            if (preset.controllerId < 0 || preset.controllerId >= GregTechAPI.METATILEENTITIES.length
                || !(GregTechAPI.METATILEENTITIES[preset.controllerId] instanceof MTEEnhancedMultiBlockBase)
                || !preset.controllerType.equals(
                    GregTechAPI.METATILEENTITIES[preset.controllerId].getClass()
                        .getName() + ":" + preset.controllerId))
                throw new java.io.IOException("模板控制器身份已失效");
            if (!s.virtual) {
                currentTool(s).getTagCompound()
                    .setString(SELECTED_PRESET, id);
                s.status = "预设已选定；Alt 点击方块开始虚拟投影";
                return;
            }
            s.virtualPreset = preset;
            s.virtualLocked = false;
            s.cancelled = false;
            s.open = false;
            s.settingsOnly = false;
            s.mode = 0;
            s.scope = 0;
            currentTool(s).getTagCompound()
                .setString(SELECTED_PRESET, id);
            populateVirtual(s);
            s.status = "预设已加载；投影跟随鼠标，锁定位置后施工";
        } catch (java.io.IOException invalid) {
            s.status = "预设加载失败：" + invalid.getMessage();
        }
    }

    private static void populateVirtual(Session s) {
        if (!s.virtual || s.virtualPreset == null) return;
        s.capture = new HologramCapture(null, trigger(s));
        s.pins.clear();
        s.coordinatePins.clear();
        s.facingPins.clear();
        s.manualPositions.clear();
        s.suppressedAuto.clear();
        s.autoTargets.clear();
        s.undoEdits.clear();
        s.redoEdits.clear();
        s.virtualEdited.clear();
        s.virtualGroups.clear();
        s.selected = -1;
        List<HologramPresets.Cell> ordered = new ArrayList<>(s.virtualPreset.cells);
        ordered.sort((a, b) -> Boolean.compare(a.controller, b.controller));
        for (HologramPresets.Cell saved : ordered) {
            ItemStack target = saved.stack();
            HologramCapture.Cell cell = new HologramCapture.Cell(null, s.x + saved.x, s.y + saved.y, s.z + saved.z);
            cell.block = Block.getBlockFromItem(target.getItem());
            cell.meta = target.getItem()
                .getMetadata(target.getItemDamage());
            cell.userPosition = true;
            cell.role = saved.controller ? "templateController" : "prefab";
            if (!acceptableSample(s, cell, target)) {
                s.capture.incomplete = true;
                s.capture.failure = "INVALID_PREFAB_ITEM";
                break;
            }
            int index = s.capture.cells.size();
            s.capture.cells.add(cell);
            s.capture.positions.put(position(cell), cell);
            s.pins.put(index, target.copy());
            s.coordinatePins.put(position(cell), target.copy());
            for (int legal : legalFacings(s, cell, target))
                if (saved.facing == legal) s.facingPins.put(position(cell), saved.facing);
        }
        refreshPlan(s);
    }

    /** Allocate only untouched template targets, with cached class/tier groups and shared material reservations. */
    private static void allocateVirtualTargets(Session s) {
        if (s.virtualPreset == null || s.capture.incomplete) return;
        if (s.virtualGroups.isEmpty()) {
            for (HologramPresets.Cell entry : s.virtualPreset.palette) {
                ItemStack sample = entry.stack();
                if (sample == null || hatchTier(sample) == Integer.MAX_VALUE) continue;
                String role = ItemMachines.getMetaTileEntity(sample)
                    .getClass()
                    .getName();
                s.virtualGroups.computeIfAbsent(role, ignored -> new ArrayList<>())
                    .add(sample);
            }
            for (List<ItemStack> group : s.virtualGroups.values())
                group.sort((a, b) -> Integer.compare(hatchTier(b), hatchTier(a)));
        }
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> reserved = new HashMap<>();
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell cell = s.capture.cells.get(i);
            String key = position(cell);
            indices.put(key, i);
            ItemStack manual = s.pins.get(i);
            if (manual != null && s.virtualEdited.contains(key) && !plainTargetInstalled(s, cell, manual))
                reserved.merge(stackKey(manual), 1, Integer::sum);
        }
        for (HologramPresets.Cell saved : s.virtualPreset.cells) {
            String position = (s.x + saved.x) + "," + (s.y + saved.y) + "," + (s.z + saved.z);
            Integer index = indices.get(position);
            if (index == null || s.virtualEdited.contains(position)) continue;
            HologramCapture.Cell cell = s.capture.cells.get(index);
            ItemStack desired = saved.stack();
            if (desired == null) continue;
            ItemStack chosen = desired;
            int tier = hatchTier(desired);
            if (s.hatchDowngrade && tier != Integer.MAX_VALUE && !plainTargetInstalled(s, cell, desired)) {
                String role = ItemMachines.getMetaTileEntity(desired)
                    .getClass()
                    .getName();
                List<ItemStack> group = s.virtualGroups.getOrDefault(role, Collections.emptyList());
                ItemStack existing = null;
                for (ItemStack candidate : group)
                    if (hatchTier(candidate) <= tier && plainTargetInstalled(s, cell, candidate)) {
                        existing = candidate;
                        break;
                    }
                if (existing != null) chosen = existing;
                else for (ItemStack candidate : group) {
                    if (hatchTier(candidate) > tier || legalFacings(s, cell, candidate).length == 0) continue;
                    String key = stackKey(candidate);
                    int available = counts.computeIfAbsent(
                        key,
                        ignored -> s.player.capabilities.isCreativeMode ? HologramCapture.LIMIT
                            : materialSource(s).count(candidate, HologramCapture.LIMIT));
                    if (available > reserved.getOrDefault(key, 0)) {
                        chosen = candidate;
                        break;
                    }
                }
            }
            s.pins.put(index, chosen.copy());
            s.coordinatePins.put(position, chosen.copy());
            if (!plainTargetInstalled(s, cell, chosen)) reserved.merge(stackKey(chosen), 1, Integer::sum);
            Integer facing = s.facingPins.get(position);
            if (facing != null) {
                boolean legal = false;
                for (int direction : legalFacings(s, cell, chosen)) if (direction == facing) legal = true;
                if (!legal) s.facingPins.remove(position);
            }
        }
    }

    private static boolean plainTargetInstalled(Session s, HologramCapture.Cell cell, ItemStack target) {
        World world = s.player.worldObj;
        if (!world.blockExists(cell.x, cell.y, cell.z)) return false;
        if (target.getItem() instanceof ItemMachines) {
            TileEntity tile = world.getTileEntity(cell.x, cell.y, cell.z);
            return tile instanceof IGregTechTileEntity
                && ((IGregTechTileEntity) tile).getMetaTileID() == target.getItemDamage();
        }
        return world.getBlock(cell.x, cell.y, cell.z) == Block.getBlockFromItem(target.getItem())
            && world.getBlockMetadata(cell.x, cell.y, cell.z) == target.getItem()
                .getMetadata(target.getItemDamage());
    }

    private static NBTTagCompound boundedChannels(NBTTagCompound input) {
        NBTTagCompound result = new NBTTagCompound();
        int count = 0;
        for (Object key : input.func_150296_c()) {
            String name = (String) key;
            if (count++ >= 64) break;
            if (name.matches("[a-z0-9_.-]{1,48}") && input.getInteger(name) > 0)
                result.setInteger(name, input.getInteger(name));
        }
        return result;
    }

    private static boolean validChannels(NBTTagCompound input) {
        if (input.func_150296_c()
            .size() > 64) return false;
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
        if (s.virtual) {
            refreshPlan(s);
            return;
        }
        refreshMaterialChoices(s);
        cancelCaptureRetry(s);
        if (resetAttempts) s.captureAttempts = 0;
        s.captureAttempts++;
        s.job = 0;
        s.cursor = 0;
        s.completed = 0;
        s.recovered.clear();
        s.defer = false;
        s.stopTick = false;
        clearPending(s);
        Map<String, ItemStack> savedPins = new HashMap<>(s.coordinatePins);
        String selectedPosition = null;
        if (s.capture != null) for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell cell = s.capture.cells.get(i);
            if (s.pins.containsKey(i)) savedPins.put(
                position(cell),
                s.pins.get(i)
                    .copy());
            if (i == s.selected) selectedPosition = position(cell);
        }
        s.pins.clear();
        s.hatchCandidates.clear();
        s.hatchParts.clear();
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
        for (String key : savedPins.keySet()) {
            boolean found = false;
            for (HologramCapture.Cell cell : s.capture.cells) if (position(cell).equals(key)) {
                found = true;
                break;
            }
            if (!found && !s.manualPositions.containsKey(key)) {
                String[] xyz = key.split(",");
                HologramCapture.Cell cell = new HologramCapture.Cell(
                    null,
                    Integer.parseInt(xyz[0]),
                    Integer.parseInt(xyz[1]),
                    Integer.parseInt(xyz[2]));
                cell.userPosition = true;
                cell.block = Blocks.air;
                cell.role = "manual";
                s.manualPositions.put(key, cell);
            }
        }
        for (HologramCapture.Cell manual : s.manualPositions.values()) {
            boolean found = false;
            for (HologramCapture.Cell cell : s.capture.cells) if (position(cell).equals(position(manual))) {
                found = true;
                break;
            }
            if (!found && s.capture.cells.size() < HologramCapture.LIMIT) s.capture.cells.add(manual);
        }
        s.selected = -1;
        for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell cell = s.capture.cells.get(i);
            ItemStack savedPin = savedPins.get(position(cell));
            if (savedPin != null && !cell.anchor) s.pins.put(i, savedPin);
            if (position(cell).equals(selectedPosition)) s.selected = i;
            cell.candidates = candidates(s, cell);
        }
        refreshPlan(s);
        s.status = s.capture.incomplete ? "结构采集不完整，高级施工已阻止" : "预览已采集；未知元素及机器部件保持保护";
        scheduleCaptureRetry(s);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> candidates(Session s, HologramCapture.Cell cell) {
        if (s.virtual && s.virtualPreset != null) {
            List<ItemStack> result = new ArrayList<>();
            for (HologramPresets.Cell entry : s.virtualPreset.palette) {
                ItemStack sample = entry.stack();
                if (acceptableSample(s, cell, sample)) result.add(sample);
            }
            return result;
        }
        if (cell.element == null) return Collections.emptyList();
        if (cell.candidates != null) return cell.candidates;
        List<ItemStack> result = new ArrayList<>();
        try {
            IItemSource previewSource = (predicate, simulate, count) -> materialSource(s).take(predicate, true, count);
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
            List<ItemStack> hatches = Collections.emptyList();
            if (!s.noHatches && "hatch".equals(cell.role)) {
                ItemStack signal = elementTrigger(s, cell);
                ChannelDataAccessor.setChannelData(signal, "gt_hatch", 1);
                String signalKey = signal.stackSize + ":" + signal.getTagCompound();
                Map<String, List<ItemStack>> signals = s.hatchCandidates
                    .computeIfAbsent(cell.element, ignored -> new HashMap<>());
                hatches = signals.get(signalKey);
                if (hatches == null) {
                    hatches = new ArrayList<>();
                    for (gregtech.api.interfaces.metatileentity.IMetaTileEntity prototype : GregTechAPI.METATILEENTITIES) {
                        if (!(prototype instanceof MTEHatch)) continue;
                        ItemStack stack = prototype.getStackForm(1);
                        if (stack != null && hatchElement(s, cell, stack, true, signal) != null) hatches.add(stack);
                    }
                    hatches.sort((a, b) -> Integer.compare(hatchTier(a), hatchTier(b)));
                    signals.put(signalKey, hatches);
                }
            }
            for (ItemStack stack : hatches) {
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

    @SuppressWarnings("unchecked")
    private static IStructureElement hatchElement(Session s, HologramCapture.Cell cell, ItemStack sample) {
        return hatchElement(s, cell, sample, true);
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement hatchElement(Session s, HologramCapture.Cell cell, ItemStack sample,
        boolean manual) {
        ItemStack signal = elementTrigger(s, cell);
        ChannelDataAccessor.setChannelData(signal, "gt_hatch", 1);
        return hatchElement(s, cell, sample, manual, signal);
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement hatchElement(Session s, HologramCapture.Cell cell, ItemStack sample,
        boolean manual, ItemStack signal) {
        if (hatchTier(sample) == Integer.MAX_VALUE) return null;
        List<IStructureElement<?>> parts = s.hatchParts.get(cell.element);
        if (parts == null) {
            parts = new ArrayList<>();
            for (IStructureElement<?> part : HologramElementCatalog.parts(cell.element))
                if ("hatch".equals(HologramElementCatalog.hatchRole(part))) parts.add(part);
            s.hatchParts.put(cell.element, parts);
        }
        for (IStructureElement part : parts) {
            try {
                java.util.function.Predicate<ItemStack> declared = manual
                    ? HologramHatchPolicy.predicate(part, s.machine, signal)
                    : null;
                if (declared != null) {
                    if (declared.test(sample)) return part;
                    continue;
                }
                IStructureElement.BlocksToPlace blocks = part.getBlocksToPlace(
                    s.machine,
                    s.player.worldObj,
                    cell.x,
                    cell.y,
                    cell.z,
                    signal,
                    AutoPlaceEnvironment.fromLegacy(
                        (predicate, simulate, count) -> materialSource(s).take(predicate, true, count),
                        s.player,
                        message -> {}));
                if (blocks != null && blocks.getPredicate()
                    .test(sample)) return part;
            } catch (RuntimeException | LinkageError ignored) {}
        }
        return null;
    }

    private static boolean recoverable(Session s, HologramCapture.Cell c) {
        TileEntity tile = s.player.worldObj.getTileEntity(c.x, c.y, c.z);
        if (!HologramRecovery.supported(tile)) return false;
        if (c.anchor) return false;
        int index = s.capture == null ? -1 : s.capture.cells.indexOf(c);
        if (explicitTarget(s, c, index) && tile instanceof IGregTechTileEntity) return true;
        if (s.mode == 2 && !c.userPosition
            && c.block != null
            && c.block != Blocks.air
            && tile instanceof IGregTechTileEntity) return true;
        if (tile instanceof IGregTechTileEntity
            && ((IGregTechTileEntity) tile).getMetaTileEntity() instanceof MTEHatch) {
            int id = ((IGregTechTileEntity) tile).getMetaTileID();
            return hatchElement(s, c, new ItemStack(GregTechAPI.sBlockMachines, 1, id)) != null;
        }
        Block block = s.player.worldObj.getBlock(c.x, c.y, c.z);
        int meta = s.player.worldObj.getBlockMetadata(c.x, c.y, c.z);
        return block instanceof BlockFrameBox && (HologramFrameSupport.acceptsCovered(c.element, block, meta)
            || destructive(c, block, meta & ~BlockFrameBox.MTE_BIT));
    }

    private static boolean removable(Session s, HologramCapture.Cell c) {
        World world = s.player.worldObj;
        if (world.getTileEntity(c.x, c.y, c.z) != null) return recoverable(s, c);
        int index = s.capture == null ? -1 : s.capture.cells.indexOf(c);
        return !c.anchor && (explicitTarget(s, c, index)
            || destructive(c, world.getBlock(c.x, c.y, c.z), world.getBlockMetadata(c.x, c.y, c.z)));
    }

    private static boolean acceptableSample(Session s, HologramCapture.Cell cell, ItemStack sample) {
        if (cell.anchor || sample == null || sample.getItem() == null || sample.stackSize < 1) return false;
        if (sample.getItemDamage() < 0) return false;
        if (s.virtual && (s.virtualPreset == null || !s.virtualPreset.allows(sample))) return false;
        if (s.virtual && "templateController".equals(cell.role)
            && (!(sample.getItem() instanceof ItemMachines) || sample.getItemDamage() != s.virtualPreset.controllerId))
            return false;
        if (sample.getItem() instanceof ItemMachines) {
            int id = sample.getItemDamage();
            return id >= 0 && id < GregTechAPI.METATILEENTITIES.length && GregTechAPI.METATILEENTITIES[id] != null;
        }
        Block block = Block.getBlockFromItem(sample.getItem());
        return sample.getItem() instanceof ItemBlock && block != Blocks.air
            && (!s.virtual || !block.hasTileEntity(
                sample.getItem()
                    .getMetadata(sample.getItemDamage())));
    }

    private static int hatchTier(ItemStack stack) {
        int id = stack.getItemDamage();
        return stack.getItem() instanceof ItemMachines && id >= 0
            && id < GregTechAPI.METATILEENTITIES.length
            && GregTechAPI.METATILEENTITIES[id] instanceof MTEHatch
                ? ((MTEHatch) GregTechAPI.METATILEENTITIES[id]).mTier
                : Integer.MAX_VALUE;
    }

    private static Map<gregtech.api.interfaces.IHatchElement<?>, Integer> hatchRequirements(HologramCapture.Cell c) {
        Map<gregtech.api.interfaces.IHatchElement<?>, Integer> result = new LinkedHashMap<>();
        for (IStructureElement<?> part : HologramElementCatalog.parts(c.element)) HologramHatchPolicy.requirements(part)
            .forEach((type, count) -> result.merge(type, count, Math::max));
        return result;
    }

    private static String candidateRole(Session s, HologramCapture.Cell c, ItemStack stack) {
        if (hatchTier(stack) == Integer.MAX_VALUE) return "外壳/方块";
        gregtech.api.interfaces.metatileentity.IMetaTileEntity prototype = ItemMachines.getMetaTileEntity(stack);
        for (IStructureElement<?> part : HologramElementCatalog.parts(c.element))
            for (gregtech.api.interfaces.IHatchElement<?> type : HologramHatchPolicy.declared(part))
                if (type.matchesHatch(prototype)) return type.getDisplayName();
        return "仓室";
    }

    private static int[] legalFacings(Session s, HologramCapture.Cell c, ItemStack target) {
        if (c.anchor || target == null
            || (!(s.virtual && target.getItem() instanceof ItemMachines) && hatchTier(target) == Integer.MAX_VALUE))
            return new int[0];
        gregtech.api.interfaces.metatileentity.IMetaTileEntity prototype = ItemMachines.getMetaTileEntity(target);
        IStructureElement<?> part = s.virtual ? null : hatchElement(s, c, target, true);
        List<Integer> result = new ArrayList<>();
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
            if (prototype != null && prototype.isFacingValid(direction)
                && (part == null || HologramHatchPolicy.allowed(part, s.machine.getExtendedFacing(), direction)))
                result.add(direction.ordinal());
        int[] values = new int[result.size()];
        for (int i = 0; i < values.length; i++) values[i] = result.get(i);
        return values;
    }

    private static boolean orientationOnly(Session s, HologramCapture.Cell c, int index) {
        return !c.anchor && s.facingPins.containsKey(position(c))
            && s.player.worldObj.getTileEntity(c.x, c.y, c.z) instanceof IGregTechTileEntity
            && targetMatches(
                s,
                c,
                index,
                s.player.worldObj.getBlock(c.x, c.y, c.z),
                s.player.worldObj.getBlockMetadata(c.x, c.y, c.z))
            && (!HologramRecovery.hasSeal(targetFor(s, c, index)) || HologramRecovery.matchesPlaced(
                s.player.worldObj,
                c.x,
                c.y,
                c.z,
                targetFor(s, c, index),
                ((IGregTechTileEntity) s.player.worldObj.getTileEntity(c.x, c.y, c.z)).getFrontFacing()
                    .ordinal()));
    }

    private static void refreshFacings(Session s) {
        s.planFacings.clear();
        List<int[]> cells = new ArrayList<>();
        for (HologramCapture.Cell c : s.capture.cells)
            cells.add(new int[] { c.x, c.y, c.z, c.block == Blocks.air && !c.anchor ? 0 : 1 });
        java.util.Set<String> exterior = HologramHatchGeometry.exterior(
            cells,
            (x, y, z) -> s.player.worldObj.blockExists(x, y, z) && trivial(s.player.worldObj, x, y, z));
        for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell c = s.capture.cells.get(i);
            ItemStack target = draftTarget(s, c, i);
            int[] legal = legalFacings(s, c, target);
            if (legal.length == 0) continue;
            Integer pin = s.facingPins.get(position(c));
            if (pin != null) {
                for (int side : legal) if (side == pin) s.planFacings.put(i, pin);
                continue;
            }
            TileEntity actual = s.player.worldObj.getTileEntity(c.x, c.y, c.z);
            if (actual instanceof IGregTechTileEntity && target != null
                && !HologramRecovery.hasSeal(target)
                && ((IGregTechTileEntity) actual).getMetaTileID() == target.getItemDamage()) {
                s.planFacings.put(
                    i,
                    ((IGregTechTileEntity) actual).getFrontFacing()
                        .ordinal());
                continue;
            }
            if (HologramRecovery.hasSeal(target)) continue;
            int direction = HologramHatchGeometry.choose(c.x, c.y, c.z, legal, exterior);
            if (direction >= 0) s.planFacings.put(i, direction);
        }
    }

    /** Freeze a whole-machine target list; quotas and inventory are shared across every position. */
    private static void buildPreset(Session s) {
        s.autoTargets.clear();
        s.presetSupported = false;
        s.presetAvailable = false;
        s.presetReason = "此定义未声明完整仓室配额，不能保证自动成型";
        if (s.virtual || s.mode == 2) {
            s.presetReason = "拆除独立使用原结构范围，保留仓室预设设置供建造或替换使用";
            return;
        }
        Map<String, gregtech.api.interfaces.IHatchElement<?>> types = new LinkedHashMap<>();
        Map<String, Integer> quotas = new LinkedHashMap<>();
        Map<String, Integer> reserved = new HashMap<>();
        for (HologramCapture.Cell c : s.capture.cells) hatchRequirements(c).forEach((type, count) -> {
            types.putIfAbsent(type.name(), type);
            quotas.merge(type.name(), count, Math::max);
        });
        if (types.isEmpty()) return;
        s.presetAvailable = true;
        if (s.presetPaused) {
            s.presetReason = "预设已撤销；选择电压级后可重新生成";
            return;
        }
        if (s.noHatches || !(s.presetEnabled || s.channels.hasKey("gt_hatch", 3))) {
            s.presetReason = "选择电压级后生成目标；保留已有仓室与手动指定";
            return;
        }
        // Count legal existing devices and explicit targets without changing either of them.
        for (int i = 0; i < s.capture.cells.size(); i++) {
            HologramCapture.Cell c = s.capture.cells.get(i);
            if (c.anchor) continue;
            ItemStack stack = s.pins.get(i);
            TileEntity actual = s.player.worldObj.getTileEntity(c.x, c.y, c.z);
            if (stack == null && actual instanceof IGregTechTileEntity)
                stack = new ItemStack(GregTechAPI.sBlockMachines, 1, ((IGregTechTileEntity) actual).getMetaTileID());
            if (stack == null || hatchElement(s, c, stack, true) == null) continue;
            if (s.pins.containsKey(i) && !(actual instanceof IGregTechTileEntity))
                reserved.merge(stackKey(stack), 1, Integer::sum);
            gregtech.api.interfaces.metatileentity.IMetaTileEntity prototype = ItemMachines.getMetaTileEntity(stack);
            for (Map.Entry<String, gregtech.api.interfaces.IHatchElement<?>> type : types.entrySet())
                if (type.getValue()
                    .matchesHatch(prototype)) quotas.computeIfPresent(type.getKey(), (key, n) -> Math.max(0, n - 1));
        }
        List<String> ordered = new ArrayList<>(types.keySet());
        ordered.sort((a, b) -> Integer.compare(presetPositions(s, types.get(a)), presetPositions(s, types.get(b))));
        int unfilled = 0;
        for (String name : ordered) for (int remaining = quotas.get(name); remaining > 0; remaining--) {
            gregtech.api.interfaces.IHatchElement<?> type = types.get(name);
            ItemStack chosen = null;
            int chosenIndex = -1;
            for (int i = 0; i < s.capture.cells.size(); i++) {
                HologramCapture.Cell c = s.capture.cells.get(i);
                if (!presetPosition(s, c, i, type)) continue;
                List<ItemStack> options = new ArrayList<>();
                for (ItemStack option : candidates(s, c)) if (hatchTier(option) != Integer.MAX_VALUE
                    && type.matchesHatch(ItemMachines.getMetaTileEntity(option))) options.add(option);
                ItemStack pick = presetCandidate(s, options, reserved);
                if (pick == null) continue;
                if (chosen == null || hatchTier(pick) > hatchTier(chosen)) {
                    chosen = pick;
                    chosenIndex = i;
                }
                if (hatchTier(pick) == s.hatchTier) break;
            }
            if (chosen == null) {
                unfilled++;
                continue;
            }
            s.autoTargets.put(chosenIndex, chosen.copy());
            reserved.merge(stackKey(chosen), 1, Integer::sum);
        }
        s.presetSupported = unfilled == 0;
        s.presetReason = unfilled == 0 ? "已按声明配额生成仓室预设；已有仓室与手选目标保留，额外成型条件仍由原机检查"
            : "部分声明仓室没有可放置位置或对应等级候选，不能保证自动成型（缺 " + unfilled + " 格）";
    }

    private static boolean presetPosition(Session s, HologramCapture.Cell c, int i,
        gregtech.api.interfaces.IHatchElement<?> type) {
        if (c.anchor || c.userPosition
            || explicitTarget(s, c, i)
            || s.autoTargets.containsKey(i)
            || s.suppressedAuto.contains(position(c))
            || s.player.worldObj.getTileEntity(c.x, c.y, c.z) != null) return false;
        boolean declared = false;
        for (gregtech.api.interfaces.IHatchElement<?> allowed : hatchRequirements(c).keySet()) if (allowed.name()
            .equals(type.name())) declared = true;
        return declared && (trivial(s.player.worldObj, c.x, c.y, c.z) || s.mode == 1 && destructive(
            c,
            s.player.worldObj.getBlock(c.x, c.y, c.z),
            s.player.worldObj.getBlockMetadata(c.x, c.y, c.z)));
    }

    private static int presetPositions(Session s, gregtech.api.interfaces.IHatchElement<?> type) {
        int count = 0;
        for (int i = 0; i < s.capture.cells.size(); i++)
            if (presetPosition(s, s.capture.cells.get(i), i, type)) count++;
        return count;
    }

    private static ItemStack presetCandidate(Session s, List<ItemStack> options, Map<String, Integer> reserved) {
        int[] tiers = new int[options.size()], available = new int[options.size()];
        for (int i = 0; i < options.size(); i++) {
            ItemStack option = options.get(i);
            tiers[i] = hatchTier(option);
            available[i] = s.player.capabilities.isCreativeMode ? 1
                : materialSource(s).count(option, 4096) - reserved.getOrDefault(stackKey(option), 0);
        }
        int chosen = HologramHatchPreset.choose(tiers, available, s.hatchTier, s.hatchDowngrade);
        return chosen < 0 ? null : options.get(chosen);
    }

    private static void send(Session s) {
        NBTTagCompound state = new NBTTagCompound();
        state.setBoolean("open", s.open);
        state.setInteger("hatchTier", s.hatchTier);
        state.setInteger("hatchTierMax", gregtech.api.enums.GTValues.VN.length - 1);
        state.setBoolean("hatchDowngrade", s.hatchDowngrade);
        state.setBoolean("presetSupported", s.presetSupported);
        state.setBoolean("presetAvailable", s.presetAvailable);
        state.setBoolean("presetActive", s.presetEnabled || !s.autoTargets.isEmpty());
        boolean editable = s.job != 1 && s.job != 2 && !s.capturePending && s.capture != null && !s.capture.incomplete;
        state.setBoolean("editCanUndo", editable && !s.undoEdits.isEmpty());
        state.setBoolean("editCanRedo", editable && !s.redoEdits.isEmpty());
        state.setBoolean("editCanPaste", editable && s.editClipboard != null);
        state.setString("presetReason", s.presetReason);
        state.setBoolean("creativeMaterials", s.player.capabilities.isCreativeMode);
        s.open = false;
        state.setString("session", s.id);
        state.setString("generation", s.id);
        state.setBoolean("capturePending", s.capturePending);
        state.setInteger("captureAttempts", s.captureAttempts);
        state.setLong("captureRetryDue", s.captureRetryDue);
        state.setLong("planRevision", s.planRevision);
        state.setLong("uiSequence", s.uiSequence);
        state.setString("phase", s.phase);
        state.setIntArray("pending", new int[0]);
        state.setIntArray(
            "ackIndices",
            s.ackIndices.stream()
                .mapToInt(Integer::intValue)
                .toArray());
        state.setIntArray(
            "ackSuccesses",
            s.ackSuccesses.stream()
                .mapToInt(Integer::intValue)
                .toArray());
        state.setInteger("layerOrdinal", 0);
        state.setInteger("layerCount", 0);
        state.setInteger("layerCompleted", s.completed);
        state.setInteger("layerTotal", s.work.size());
        state.setInteger("layerY", s.y);
        state.setInteger("layerElapsed", 0);
        state.setInteger("layerLeadRemaining", 0);
        state.setInteger("layerDuration", 0);
        state.setInteger("layerGap", 0);
        state.setLong("due", 0);
        state.setLong("serverTick", s.player.worldObj.getTotalWorldTime());
        state.setLong("batchSeq", s.batchSeq);
        state.setIntArray("ack", s.ackIndex < 0 ? new int[0] : new int[] { s.ackIndex });
        state.setBoolean("ackSuccess", s.ackSuccess);
        state.setBoolean("recoveryFits", s.planRecoveryFits);
        state.setString(
            "recoveryDestinationLabel",
            s.materialSettings.recoveryDestination == 1 ? "优先 ME；余量回背包或持久暂存" : "主背包；满时持久暂存");
        state.setBoolean("formationChecked", false);
        state.setTag("capabilities", s.capabilities == null ? new NBTTagList() : s.capabilities.toNBT());
        state.setBoolean("target", s.target != null || s.virtual && s.capture != null);
        state.setBoolean("virtual", s.virtual);
        state.setBoolean("virtualLocked", s.virtualLocked);
        state.setBoolean("moving", s.virtual && !s.virtualLocked && s.job != 4);
        state.setBoolean("settingsOnly", s.settingsOnly);
        state.setBoolean("cancelled", s.cancelled);
        state.setString("selectedPresetId", selectedPreset(s));
        ItemStack heldDraft = currentTool(s);
        state.setBoolean(
            "draftAvailable",
            heldDraft != null && draftMatches(
                s,
                heldDraft.getTagCompound()
                    .getCompoundTag(DRAFT)));
        try {
            state.setTag("presets", HologramPresets.labels());
        } catch (java.io.IOException invalid) {
            state.setTag("presets", new NBTTagList());
            state.setString("presetConfigError", invalid.getMessage());
        }
        state.setBoolean("targetClosed", false);
        state.setInteger("x", s.x);
        state.setInteger("y", s.y);
        state.setInteger("z", s.z);
        state.setInteger("dimension", s.dimension);
        state.setInteger("side", s.side);
        state.setString("title", s.machine == null ? "全息构造器" : s.machine.getLocalName());
        state.setInteger("main", s.main);
        state.setTag("channels", s.channels.copy());
        state.setBoolean("noHatches", s.noHatches);
        state.setBoolean("keepController", !s.virtual);
        NBTTagList materialChoices = new NBTTagList();
        for (ItemStack sample : s.materialChoices) materialChoices.appendTag(sample.writeToNBT(new NBTTagCompound()));
        state.setTag("materialChoices", materialChoices);
        state.setBoolean("materialMain", s.materialSettings.main);
        state.setBoolean("materialContainers", s.materialSettings.containers);
        state.setBoolean("materialMe", s.materialSettings.me);
        state.setInteger("materialPriority", s.materialSettings.priority);
        state.setInteger("recoveryDestination", s.materialSettings.recoveryDestination);
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
        int recoveryRows = 0, recoveryItems = 0;
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
            row.setBoolean("hasPin", s.pins.containsKey(index));
            row.setBoolean("templateController", "templateController".equals(c.role));
            row.setBoolean("hasTargetOverride", s.pins.containsKey(index) || s.autoTargets.containsKey(index));
            row.setBoolean("hasFacingPin", s.facingPins.containsKey(position(c)));
            row.setInteger("hatchFacing", s.planFacings.getOrDefault(index, -1));
            TileEntity actualTile = world.getTileEntity(c.x, c.y, c.z);
            row.setInteger(
                "actualFacing",
                actualTile instanceof IGregTechTileEntity ? ((IGregTechTileEntity) actualTile).getFrontFacing()
                    .ordinal() : -1);
            row.setIntArray("hatchFacings", legalFacings(s, c, targetFor(s, c, index)));
            row.setBoolean("userPosition", c.userPosition);
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
                    && !explicitTarget(s, c, index) || s.autoTargets.containsKey(index));
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
            if (pinned != null) {
                Block pinnedBlock = Block.getBlockFromItem(pinned.getItem());
                if (pinnedBlock != Blocks.air) {
                    row.setString("wantId", String.valueOf(Block.blockRegistry.getNameForObject(pinnedBlock)));
                    row.setInteger(
                        "wantMeta",
                        pinned.getItem()
                            .getMetadata(pinned.getItemDamage()));
                }
            }
            if (s.mode == 2 && loaded && (removable(s, c) || "removed".equals(c.status))) {
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
            List<ItemStack> options = index == s.selected ? candidates(s, c) : Collections.emptyList();
            int chosenChoice = -1;
            for (int choice = 0; choice < options.size(); choice++) if (same(pinned, options.get(choice))) {
                chosenChoice = choice;
                break;
            }
            row.setInteger("chosenChoice", chosenChoice);
            row.setString("pinScope", "target");
            for (ItemStack option : options) {
                NBTTagCompound tag = new NBTTagCompound();
                option.writeToNBT(tag);
                choices.appendTag(tag);
            }
            row.setTag("candidates", choices);
            NBTTagList candidateRoles = new NBTTagList();
            int[] candidateTiers = new int[options.size()], candidateAvailable = new int[options.size()];
            for (int n = 0; n < options.size(); n++) {
                ItemStack option = options.get(n);
                candidateRoles.appendTag(new NBTTagString(candidateRole(s, c, option)));
                candidateTiers[n] = hatchTier(option) == Integer.MAX_VALUE ? -1 : hatchTier(option);
                candidateAvailable[n] = materialSource(s).count(option, 4096);
            }
            row.setTag("candidateRoles", candidateRoles);
            row.setIntArray("candidateTiers", candidateTiers);
            row.setIntArray("candidateAvailable", candidateAvailable);
            cells.appendTag(row);
            if (!"satisfied".equals(c.status) && !"protected".equals(c.status)
                && !"unsupported".equals(c.status)
                && !"placed".equals(c.status)
                && !"replaced".equals(c.status)
                && pinned != null
                && !orientationOnly(s, c, index)
                && inScope(s, c, index)
                && s.mode != 2
                && (!s.player.capabilities.isCreativeMode || HologramRecovery.hasSeal(pinned))) {
                ItemStack stack = pinned.copy();
                stack.stackSize = 1;
                String key = stackKey(stack);
                NBTTagCompound req = requirements.get(key);
                if (req == null) {
                    req = new NBTTagCompound();
                    stack.writeToNBT(req);
                    req.setInteger("available", materialSource(s).count(stack, 4096));
                    req.setInteger("mainAvailable", materialSource(s).mainCount(stack, 4096));
                    req.setInteger(
                        "controllerAvailable",
                        s.target instanceof net.minecraft.inventory.IInventory
                            && ((net.minecraft.inventory.IInventory) s.target).isUseableByPlayer(s.player)
                                ? HologramMaterials
                                    .controllerCount((net.minecraft.inventory.IInventory) s.target, stack, 4096)
                                : -1);
                    requirements.put(key, req);
                }
                req.setInteger("required", req.getInteger("required") + 1);
            }
            if (inScope(s, c, index) && "pending".equals(c.status)
                && !orientationOnly(s, c, index)
                && loaded
                && !trivial(world, c.x, c.y, c.z)
                && (!s.player.capabilities.isCreativeMode || world.getTileEntity(c.x, c.y, c.z) != null)) try {
                    for (ItemStack drop : drops(current, world, c, meta)) {
                        recoveryRows++;
                        recoveryItems += drop.stackSize;
                        if (recovery.tagCount() < 96) recovery.appendTag(recoverySummary(drop, true));
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
        for (ItemStack drop : s.recovered) {
            recoveryRows++;
            recoveryItems += drop.stackSize;
            if (recovery.tagCount() < 96) recovery.appendTag(recoverySummary(drop, false));
        }
        state.setInteger("recoveryMore", Math.max(0, recoveryRows - recovery.tagCount()));
        state.setInteger("recoveryTotal", recoveryItems);
        state.setTag("recovery", recovery);
        state.setInteger("protected", protectedCount);
        state.setInteger("missing", missing);
        state.setInteger("total", total);
        state.setInteger("workTotal", workTotal);
        state.setString("description", "控制器始终保留；直接施工会重新扫描并逐格重验。手选目标覆盖对应位置，不保证结构成型；差分可供查看材料与改动。");
        HologramNetwork.sendState(s.player, state);
    }

    private static NBTTagCompound recoverySummary(ItemStack drop, boolean estimated) {
        ItemStack plain = new ItemStack(drop.getItem(), Math.min(64, drop.stackSize), drop.getItemDamage());
        NBTTagCompound row = plain.writeToNBT(new NBTTagCompound());
        row.setInteger("expected", drop.stackSize);
        row.setBoolean("estimated", estimated);
        row.setBoolean("sealed", HologramRecovery.hasSeal(drop));
        return row;
    }

    /** Coordinate based planning snapshots; world, inventories and clipboard are deliberately outside history. */
    private static final class EditState {

        final Map<String, ItemStack> targets = new HashMap<>();
        final Map<String, Integer> facings;
        final java.util.Set<String> suppressed;
        final java.util.Set<String> virtualEdited;
        final boolean presetEnabled, presetPaused, hatchDowngrade, noHatches;
        final int hatchTier;

        EditState(Session s) {
            for (Map.Entry<String, ItemStack> entry : s.coordinatePins.entrySet()) targets.put(
                entry.getKey(),
                entry.getValue()
                    .copy());
            if (s.capture != null)
                for (int i = 0; i < s.capture.cells.size(); i++) if (s.pins.containsKey(i)) targets.put(
                    position(s.capture.cells.get(i)),
                    s.pins.get(i)
                        .copy());
            facings = new HashMap<>(s.facingPins);
            suppressed = new java.util.HashSet<>(s.suppressedAuto);
            virtualEdited = new java.util.HashSet<>(s.virtualEdited);
            presetEnabled = s.presetEnabled;
            presetPaused = s.presetPaused;
            hatchDowngrade = s.hatchDowngrade;
            noHatches = s.noHatches;
            hatchTier = s.hatchTier;
        }

        boolean sameAs(EditState other) {
            if (!targets.keySet()
                .equals(other.targets.keySet()) || !facings.equals(other.facings)
                || !suppressed.equals(other.suppressed)
                || !virtualEdited.equals(other.virtualEdited)
                || presetEnabled != other.presetEnabled
                || presetPaused != other.presetPaused
                || hatchDowngrade != other.hatchDowngrade
                || noHatches != other.noHatches
                || hatchTier != other.hatchTier) return false;
            for (String key : targets.keySet()) if (!same(targets.get(key), other.targets.get(key))) return false;
            return true;
        }

        void apply(Session s) {
            s.pins.clear();
            s.coordinatePins.clear();
            s.facingPins.clear();
            s.suppressedAuto.clear();
            s.virtualEdited.clear();
            s.virtualEdited.addAll(virtualEdited);
            s.presetEnabled = presetEnabled;
            s.presetPaused = presetPaused;
            s.hatchDowngrade = hatchDowngrade;
            s.noHatches = noHatches;
            s.hatchTier = hatchTier;
            for (int i = 0; i < s.capture.cells.size(); i++) {
                HologramCapture.Cell cell = s.capture.cells.get(i);
                if (cell.anchor) continue;
                String key = position(cell);
                ItemStack target = targets.get(key);
                if (acceptableSample(s, cell, target)) {
                    s.pins.put(i, target.copy());
                    s.coordinatePins.put(key, target.copy());
                }
                if (suppressed.contains(key)) s.suppressedAuto.add(key);
            }
            // Validate directions again against the current capture; rescans can change a cell's legal faces.
            for (int i = 0; i < s.capture.cells.size(); i++) {
                HologramCapture.Cell cell = s.capture.cells.get(i);
                Integer direction = facings.get(position(cell));
                if (direction == null || cell.anchor) continue;
                ItemStack target = s.pins.get(i);
                if (target == null) target = draftTarget(s, cell, i);
                for (int allowed : legalFacings(s, cell, target))
                    if (allowed == direction) s.facingPins.put(position(cell), direction);
            }
            for (HologramCapture.Cell cell : s.capture.cells) cell.candidates = null;
        }
    }

    private static final class Session {

        final EntityPlayerMP player;
        final String id = UUID.randomUUID()
            .toString();
        int x, y, z, side;
        final int dimension, slot;
        final Item tool;
        final String toolId;
        final long openTick;
        TileEntity target;
        MTEEnhancedMultiBlockBase<?> machine;
        IConstructable constructable;
        Object context;
        int main = 1, facing, mode, scope, layer, selected = -1, job, cursor, completed;
        boolean noHatches, hints, defer, stopTick, mainExplicit;
        boolean virtual, virtualLocked, settingsOnly, cancelled;
        HologramPresets.Preset virtualPreset;
        final java.util.Set<String> virtualEdited = new java.util.HashSet<>();
        final Map<String, List<ItemStack>> virtualGroups = new HashMap<>();
        String status = "";
        NBTTagCompound channels = new NBTTagCompound();
        HologramCapture capture;
        HologramCapabilities.Result capabilities;
        HologramMaterials.Settings materialSettings;
        HologramMaterials.Context materialContext;
        final List<ItemStack> materialChoices = new ArrayList<>();
        final Map<Integer, ItemStack> pins = new HashMap<>();
        ItemStack editClipboard;
        int editClipboardFacing = -1;
        final List<EditState> undoEdits = new ArrayList<>();
        final List<EditState> redoEdits = new ArrayList<>();
        final Map<Integer, ItemStack> autoTargets = new HashMap<>();
        final java.util.Set<String> suppressedAuto = new java.util.HashSet<>();
        final Map<String, Integer> facingPins = new HashMap<>();
        final Map<Integer, Integer> planFacings = new HashMap<>();
        int hatchTier = 1;
        boolean hatchDowngrade, presetEnabled, presetSupported, presetAvailable, presetPaused;
        String presetReason = "请选择电压级生成整机仓室预设";
        final Map<String, ItemStack> coordinatePins = new HashMap<>();
        final Map<String, HologramCapture.Cell> manualPositions = new LinkedHashMap<>();
        final IdentityHashMap<IStructureElement<?>, Map<String, List<ItemStack>>> hatchCandidates = new IdentityHashMap<>();
        final IdentityHashMap<IStructureElement<?>, List<IStructureElement<?>>> hatchParts = new IdentityHashMap<>();
        final List<Integer> work = new ArrayList<>();
        final List<ItemStack> recovered = new ArrayList<>();
        final List<Integer> ackIndices = new ArrayList<>(), ackSuccesses = new ArrayList<>();
        int deferAttempts, deferredIndex = -1;
        boolean open;
        long planRevision, uiSequence, batchSeq;
        int ackIndex = -1, planMissing;
        String phase = "IDLE";
        boolean planRecoveryFits = true, ackSuccess;
        boolean capturePending, retryNoHatches;
        int captureAttempts, retryFacing, retryMain, retryMode, retryScope, retryLayer, retrySelected;
        long captureRetryDue;
        NBTTagCompound retryChannels;
        final List<Block> expectedBlocks = new ArrayList<>();
        final List<Integer> expectedMeta = new ArrayList<>();
        final List<TileEntity> expectedTiles = new ArrayList<>();
        final List<Integer> expectedFacings = new ArrayList<>();
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
            materialSettings = HologramMaterials.read(heldStack);
            hatchDowngrade = heldStack != null && heldStack.hasTagCompound()
                && heldStack.getTagCompound()
                    .getBoolean("gtitHologramDowngrade");
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

    private static void refreshMaterialChoices(Session s) {
        s.materialChoices.clear();
        s.materialChoices.addAll(materialSource(s).catalog(96));
    }

    private static HologramMaterials.Context materialSource(Session s) {
        if (s.materialContext == null) s.materialContext = new HologramMaterials.Context(s.player, s.materialSettings);
        return s.materialContext;
    }

    private static void rollbackMaterials(Session s, HologramMaterials.Transaction transaction, ItemStack[] inventory) {
        try {
            transaction.rollbackExternal();
        } finally {
            restoreInventory(s.player, inventory);
            materialSource(s).afterInventoryRestore();
        }
    }

    private static final class FilteredSource implements IItemSource {

        final IItemSource source;
        final ItemStack pin;

        FilteredSource(IItemSource source, ItemStack pin) {
            this.source = source;
            this.pin = pin;
        }

        public boolean takeOne(ItemStack stack, boolean simulate) {
            return same(stack, pin) && source.takeOne(stack, simulate);
        }

        public boolean takeAll(ItemStack stack, boolean simulate) {
            return same(stack, pin) && source.takeAll(stack, simulate);
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
