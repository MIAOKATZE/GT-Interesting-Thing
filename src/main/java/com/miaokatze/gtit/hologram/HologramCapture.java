package com.miaokatze.gtit.hologram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import tectech.thing.metaTileEntity.multi.godforge.MTEForgeOfGods;

/** Armed only during a server-thread preview. Hints describe appearance, never permission to write. */
public final class HologramCapture {

    /** Java collection capacity; no structure-size policy limit. */
    public static final int LIMIT = Integer.MAX_VALUE - 8;
    private static final int CELLS_PER_BATCH = 4096;
    /** Wall-clock threshold at checkpoints; native machine callbacks cannot be interrupted mid-call. */
    private static final long CAPTURE_TIME_NS = 100000000L;
    private static final Logger LOG = LogManager.getLogger("gtit");
    private static final ThreadLocal<HologramCapture> ACTIVE = new ThreadLocal<>();
    final Object context;
    final ItemStack trigger;
    final List<Piece> pieces = new ArrayList<>();
    final List<Cell> cells = new ArrayList<>();
    final Map<String, Cell> positions = new HashMap<>();
    final IdentityHashMap<IStructureElement<?>, Map<String, HologramCasingFallback.Target>> casingCache = new IdentityHashMap<>();
    final IdentityHashMap<IStructureElement<?>, HologramReplacementFamily.Family> familyCache = new IdentityHashMap<>();
    final IdentityHashMap<IStructureElement<?>, String> roleCache = new IdentityHashMap<>();
    final IdentityHashMap<IStructureElement<?>, Set<String>> declarationTrace = new IdentityHashMap<>();
    boolean incomplete;
    String failure = "";
    final long started = System.nanoTime();
    long deadline = started + CAPTURE_TIME_NS;
    boolean pending;
    int pieceCursor;
    Cell current;
    HologramChannelTrace.Report channelReport;

    HologramCapture(Object context, ItemStack trigger) {
        this.context = context;
        this.trigger = trigger;
    }

    public static boolean piece(Object controller, String name, ItemStack trigger, boolean hints, int a, int b, int c) {
        HologramCapture capture = ACTIVE.get();
        if (capture == null || capture.context != controller || !hints) return false;
        if (System.nanoTime() > capture.deadline) {
            capture.fail("TIME_LIMIT");
            throw new CaptureLimitException();
        }
        capture.pieces.add(new Piece(name, a, b, c, trigger == null ? capture.trigger.copy() : trigger.copy()));
        return true;
    }

    public static boolean hint(World world, int x, int y, int z, Block block, int meta) {
        HologramCapture capture = ACTIVE.get();
        if (capture == null) return false;
        Cell cell = capture.current;
        if (cell != null && cell.x == x && cell.y == y && cell.z == z && block != null) {
            cell.block = block;
            cell.meta = meta;
            if (block == StructureLibAPI.getBlockHint()) cell.hintIndex = meta;
        }
        return true;
    }

    public static boolean iconHint(World world, int x, int y, int z, IIcon[] icons, short[] tint) {
        HologramCapture capture = ACTIVE.get();
        if (capture == null) return false;
        Cell cell = capture.current;
        if (cell != null && cell.x == x && cell.y == y && cell.z == z) {
            cell.iconOnly = true;
            cell.renderKind = "icons";
            cell.hintIcons = new String[6];
            // Texture names are descriptors only. Never retain atlas objects or serialize IIcon instances.
            if (icons != null) for (int side = 0; side < Math.min(6, icons.length); side++) {
                if (icons[side] != null) cell.hintIcons[side] = icons[side].getIconName();
            }
            cell.hintTint = tint == null ? null : tint.clone();
        }
        return true;
    }

    public static boolean iconHint() {
        return ACTIVE.get() != null;
    }

    public static void hintTint(int x, int y, int z, short[] tint) {
        HologramCapture capture = ACTIVE.get();
        Cell cell = capture == null ? null : capture.current;
        if (cell != null && cell.x == x && cell.y == y && cell.z == z)
            cell.hintTint = tint == null ? null : tint.clone();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static HologramCapture collect(MTEEnhancedMultiBlockBase machine, ItemStack trigger, ExtendedFacing facing) {
        return collect(machine, trigger, facing, null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static HologramCapture collect(MTEEnhancedMultiBlockBase<?> machine, ItemStack trigger, ExtendedFacing facing,
        HologramCapture resumed) {
        HologramCapture capture = resumed == null ? new HologramCapture(machine, trigger) : resumed;
        capture.deadline = System.nanoTime() + CAPTURE_TIME_NS;
        capture.pending = false;
        HologramCapture previous = ACTIVE.get();
        HologramChannelTrace.Scope scope = HologramChannelTrace.begin(machine);
        ACTIVE.set(capture);
        try {
            if (resumed == null) {
                if (machine instanceof MTEForgeOfGods && ((MTEForgeOfGods) machine).getData()
                    .isRenderActive()) {
                    // The native hint constructor always shows the dormant main ring. The live machine
                    // checks shaft + air rings after converting its rings into the star renderer.
                    int rings = ((MTEForgeOfGods) machine).getData()
                        .getRingAmount();
                    capture.pieces.add(new Piece("beam_shaft", 63, 14, 1, trigger.copy()));
                    capture.pieces.add(new Piece("first_ring_air", 63, 14, -59, trigger.copy()));
                    if (trigger.stackSize > 1) capture.pieces
                        .add(new Piece(rings >= 2 ? "second_ring_air" : "second_ring", 55, 11, -67, trigger.copy()));
                    if (trigger.stackSize > 2) capture.pieces
                        .add(new Piece(rings >= 3 ? "third_ring_air" : "third_ring", 47, 13, -76, trigger.copy()));
                } else((IConstructable) machine).construct(trigger, true);
            }
            IStructureDefinition definition = machine.getStructureDefinition();
            final World world = machine.getBaseMetaTileEntity()
                .getWorld();
            int x = machine.getBaseMetaTileEntity()
                .getXCoord(),
                y = machine.getBaseMetaTileEntity()
                    .getYCoord(),
                z = machine.getBaseMetaTileEntity()
                    .getZCoord();
            walkPieces(capture, definition, world, machine.getExtendedFacing(), x, y, z);
        } catch (CaptureYieldException yielded) {
            // Resume on a later server tick, retaining captured cells and declaration caches.
        } catch (RuntimeException | LinkageError e) {
            capture.incomplete = true;
            if (capture.failure.isEmpty()) capture.failure = e.getClass()
                .getSimpleName();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
            HologramChannelTrace.Report report = HologramChannelTrace.end(scope);
            capture.channelReport = HologramChannelTrace.merge(capture.channelReport, report);
        }
        if (capture.pending) return capture;
        capture.addAnchor(machine);
        if (capture.pieces.isEmpty()) capture.fail("NO_BUILD_PIECES");
        if (capture.incomplete) {
            capture.failure += " elapsedMs=" + ((System.nanoTime() - capture.started) / 1000000L)
                + " pieces="
                + capture.pieces.size()
                + " cells="
                + capture.cells.size()
                + " casingFactories="
                + capture.casingCache.size();
            LOG.warn(
                "Hologram preview incomplete: {} ({})",
                machine.getClass()
                    .getName(),
                capture.failure);
        }
        return capture;
    }

    /** Resumable traversal of declared pieces; collection scope owns hint interception and completion. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void walkPieces(HologramCapture capture, IStructureDefinition definition, World world,
        ExtendedFacing facing, int x, int y, int z) {
        final int batchStart = capture.cells.size();
        for (; capture.pieceCursor < capture.pieces.size(); capture.pieceCursor++) {
            Piece piece = capture.pieces.get(capture.pieceCursor);
            definition.iterate(piece.name, world, facing, x, y, z, piece.a, piece.b, piece.c, new IStructureWalker() {

                public boolean visit(IStructureElement element, World w, int xx, int yy, int zz, int a, int b, int c) {
                    String position = xx + "," + yy + "," + zz;
                    Cell existing = capture.positions.get(position);
                    if (existing != null) {
                        if (existing.element == element
                            && ItemStack.areItemStacksEqual(existing.elementTrigger, piece.trigger)) return true;
                        capture.fail("CONFLICTING_CELL " + position);
                        return false;
                    }
                    if (capture.cells.size() - batchStart >= CELLS_PER_BATCH
                        || (capture.cells.size() > batchStart && System.nanoTime() > capture.deadline)) {
                        capture.pending = true;
                        throw new CaptureYieldException();
                    }
                    Cell cell = new Cell(element, xx, yy, zz, piece.trigger);
                    capture.cells.add(cell);
                    capture.positions.put(position, cell);
                    capture.current = cell;
                    try {
                        String signalKey = piece.trigger.stackSize + ":" + piece.trigger.getTagCompound();
                        boolean observeDeclaration = capture.declarationTrace
                            .computeIfAbsent(element, ignored -> new HashSet<>())
                            .add(signalKey);
                        if (observeDeclaration) HologramDeclarationWrappers.observeChannels(element, piece.trigger);
                        if (element == StructureUtility.isAir()) {
                            cell.block = Blocks.air;
                            cell.meta = 0;
                        } else element.spawnHint(capture.context, w, xx, yy, zz, piece.trigger);
                        if (observeDeclaration) HologramDeclarationWrappers.observeMaterials(element);
                        if (cell.block == StructureLibAPI.getBlockHint()) {
                            HologramCasingFallback.Target casing = capture.casing(element, piece.trigger);
                            if (casing == null) cell.unknown = true;
                            else {
                                cell.block = casing.block;
                                cell.meta = casing.meta;
                            }
                        }
                        net.minecraft.item.ItemStack frame = HologramFrameSupport.target(element);
                        if (frame != null) {
                            cell.block = Block.getBlockFromItem(frame.getItem());
                            cell.meta = frame.getItem()
                                .getMetadata(frame.getItemDamage());
                            cell.unknown = false;
                            cell.iconOnly = false;
                        }
                        if (!capture.roleCache.containsKey(element))
                            capture.roleCache.put(element, HologramElementCatalog.hatchRole(element));
                        cell.role = capture.roleCache.get(element);
                        if (!capture.familyCache.containsKey(element)) capture.familyCache
                            .put(element, HologramReplacementFamily.resolve(element, cell.block, cell.meta));
                        HologramReplacementFamily.Family family = capture.familyCache.get(element);
                        if (family != null) cell.family = family.id;
                        if (cell.block == null) {
                            cell.unknown = true;
                            if (cell.iconOnly) cell.role = "interface";
                        }
                        if (cell.unknown && "block".equals(cell.role)) cell.role = "interface";
                    } catch (RuntimeException | LinkageError e) {
                        cell.unknown = true;
                        capture.failure = e.getClass()
                            .getSimpleName();
                    } finally {
                        try {
                            capture.describeActual(cell, w);
                        } catch (RuntimeException | LinkageError e) {
                            cell.unknown = true;
                        }
                        capture.current = null;
                    }
                    return true;
                }

                public boolean blockNotLoaded(IStructureElement e, World w, int xx, int yy, int zz, int a, int b,
                    int c) {
                    capture.fail("UNLOADED " + xx + "," + yy + "," + zz);
                    return false;
                }
            });
        }
    }

    private void fail(String reason) {
        incomplete = true;
        if (failure.isEmpty()) failure = reason;
        else if ("TIME_LIMIT".equals(reason) && !failure.startsWith("TIME_LIMIT")) failure = reason + ": " + failure;
    }

    private static final class CaptureYieldException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private void describeActual(Cell cell, World world) {
        TileEntity tile = world.getTileEntity(cell.x, cell.y, cell.z);
        if (tile == null) return;
        cell.renderKind = "item";
        if (tile instanceof IGregTechTileEntity) {
            IGregTechTileEntity gt = (IGregTechTileEntity) tile;
            if (gt.getMetaTileEntity() != null) cell.actualStack = gt.getMetaTileEntity()
                .getStackForm(1);
        }
        if ("block".equals(cell.role)) cell.role = "interface";
    }

    private void addAnchor(MTEEnhancedMultiBlockBase<?> machine) {
        World world = machine.getBaseMetaTileEntity()
            .getWorld();
        int x = machine.getBaseMetaTileEntity()
            .getXCoord();
        int y = machine.getBaseMetaTileEntity()
            .getYCoord();
        int z = machine.getBaseMetaTileEntity()
            .getZCoord();
        Cell anchor = new Cell(null, x, y, z);
        anchor.anchor = true;
        anchor.role = "controller";
        anchor.renderKind = "item";
        anchor.status = "protected";
        anchor.block = world.getBlock(x, y, z);
        anchor.meta = world.getBlockMetadata(x, y, z);
        anchor.actualStack = machine.getStackForm(1);
        anchor.actualFacing = machine.getBaseMetaTileEntity()
            .getFrontFacing()
            .ordinal();
        cells.removeIf(cell -> cell.x == x && cell.y == y && cell.z == z);
        cells.add(anchor);
    }

    private HologramCasingFallback.Target casing(IStructureElement<?> element, ItemStack trigger) {
        Map<String, HologramCasingFallback.Target> signals = casingCache
            .computeIfAbsent(element, ignored -> new HashMap<>());
        String signal = trigger == null ? "" : trigger.stackSize + ":" + trigger.getTagCompound();
        if (!signals.containsKey(signal)) signals.put(signal, HologramCasingFallback.resolve(element, trigger));
        return signals.get(signal);
    }

    static final class Piece {

        final String name;
        final int a, b, c;
        final ItemStack trigger;

        Piece(String name, int a, int b, int c, ItemStack trigger) {
            this.name = name;
            this.a = a;
            this.b = b;
            this.c = c;
            this.trigger = trigger;
        }
    }

    static final class Cell {

        final IStructureElement element;
        final int x, y, z;
        Block block;
        int meta;
        boolean unknown;
        String status = "pending";
        List<ItemStack> candidates;
        boolean candidateComplete;
        String candidateBoundary = "尚未解析结构声明";
        final ItemStack elementTrigger;
        boolean anchor;
        boolean userPosition;
        boolean iconOnly;
        String role = "block";
        String family = "";
        String renderKind = "block";
        ItemStack actualStack;
        int actualFacing = -1;
        String[] hintIcons;
        short[] hintTint;
        int hintIndex = -1;

        Cell(IStructureElement element, int x, int y, int z) {
            this(element, x, y, z, null);
        }

        Cell(IStructureElement element, int x, int y, int z, ItemStack elementTrigger) {
            this.element = element;
            this.x = x;
            this.y = y;
            this.z = z;
            this.elementTrigger = elementTrigger == null ? null : elementTrigger.copy();
        }
    }

    private static final class CaptureLimitException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CaptureLimitException() {
            super("hologram capture budget exhausted", null, false, false);
        }
    }
}
