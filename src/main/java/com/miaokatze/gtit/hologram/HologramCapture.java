package com.miaokatze.gtit.hologram;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
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

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;

/** Armed only during a server-thread preview. Hints describe appearance, never permission to write. */
public final class HologramCapture {

    public static final int LIMIT = 4096;
    /** Wall-clock threshold at checkpoints; native machine callbacks cannot be interrupted mid-call. */
    private static final long CAPTURE_TIME_NS = 100000000L;
    private static final Logger LOG = LogManager.getLogger("gtit");
    private static final ThreadLocal<HologramCapture> ACTIVE = new ThreadLocal<>();
    final Object context;
    final ItemStack trigger;
    final List<Piece> pieces = new ArrayList<>();
    final List<Cell> cells = new ArrayList<>();
    final IdentityHashMap<IStructureElement<?>, HologramCasingFallback.Target> casingCache = new IdentityHashMap<>();
    boolean incomplete;
    String failure = "";
    final long started = System.nanoTime();
    final long deadline = started + CAPTURE_TIME_NS;
    Cell current;

    private HologramCapture(Object context, ItemStack trigger) {
        this.context = context;
        this.trigger = trigger;
    }

    public static boolean piece(Object controller, String name, ItemStack trigger, boolean hints, int a, int b, int c) {
        HologramCapture capture = ACTIVE.get();
        if (capture == null || capture.context != controller || !hints) return false;
        if (capture.pieces.size() >= 128 || System.nanoTime() > capture.deadline) {
            capture.fail(capture.pieces.size() >= 128 ? "PIECE_LIMIT" : "TIME_LIMIT");
            throw new CaptureLimitException();
        }
        capture.pieces.add(new Piece(name, a, b, c));
        return true;
    }

    public static boolean hint(World world, int x, int y, int z, Block block, int meta) {
        HologramCapture capture = ACTIVE.get();
        if (capture == null) return false;
        Cell cell = capture.current;
        if (cell != null && cell.x == x && cell.y == y && cell.z == z && block != null) {
            cell.block = block;
            cell.meta = meta;
        }
        return true;
    }

    public static boolean iconHint() {
        return ACTIVE.get() != null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static HologramCapture collect(MTEEnhancedMultiBlockBase machine, ItemStack trigger, ExtendedFacing facing) {
        HologramCapture capture = new HologramCapture(machine, trigger);
        ACTIVE.set(capture);
        try {
            ((IConstructable) machine).construct(trigger, true);
            IStructureDefinition definition = machine.getStructureDefinition();
            final World world = machine.getBaseMetaTileEntity()
                .getWorld();
            int x = machine.getBaseMetaTileEntity()
                .getXCoord(),
                y = machine.getBaseMetaTileEntity()
                    .getYCoord(),
                z = machine.getBaseMetaTileEntity()
                    .getZCoord();
            for (Piece piece : capture.pieces) {
                definition
                    .iterate(piece.name, world, facing, x, y, z, piece.a, piece.b, piece.c, new IStructureWalker() {

                        public boolean visit(IStructureElement element, World w, int xx, int yy, int zz, int a, int b,
                            int c) {
                            if (capture.cells.size() >= LIMIT || System.nanoTime() > capture.deadline) {
                                capture.fail(capture.cells.size() >= LIMIT ? "CELL_LIMIT" : "TIME_LIMIT");
                                return false;
                            }
                            Cell cell = new Cell(element, xx, yy, zz);
                            capture.cells.add(cell);
                            capture.current = cell;
                            try {
                                if (element == StructureUtility.isAir()) {
                                    cell.block = Blocks.air;
                                    cell.meta = 0;
                                } else element.spawnHint(machine, w, xx, yy, zz, trigger);
                                if (cell.block == StructureLibAPI.getBlockHint()) {
                                    HologramCasingFallback.Target casing = capture.casing(element);
                                    if (casing == null) cell.unknown = true;
                                    else {
                                        cell.block = casing.block;
                                        cell.meta = casing.meta;
                                    }
                                }
                            } catch (RuntimeException | LinkageError e) {
                                cell.unknown = true;
                                capture.failure = e.getClass()
                                    .getSimpleName();
                            } finally {
                                capture.current = null;
                            }
                            return true;
                        }

                        public boolean blockNotLoaded(IStructureElement e, World w, int xx, int yy, int zz, int a,
                            int b, int c) {
                            capture.fail("UNLOADED " + xx + "," + yy + "," + zz);
                            return false;
                        }
                    });
            }
        } catch (RuntimeException | LinkageError e) {
            capture.incomplete = true;
            if (capture.failure.isEmpty()) capture.failure = e.getClass()
                .getSimpleName();
        } finally {
            ACTIVE.remove();
        }
        if (System.nanoTime() > capture.deadline) capture.fail("TIME_LIMIT");
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

    private void fail(String reason) {
        incomplete = true;
        if (failure.isEmpty()) failure = reason;
        else if ("TIME_LIMIT".equals(reason) && !failure.startsWith("TIME_LIMIT")) failure = reason + ": " + failure;
    }

    private HologramCasingFallback.Target casing(IStructureElement<?> element) {
        if (!casingCache.containsKey(element)) casingCache.put(element, HologramCasingFallback.resolve(element));
        return casingCache.get(element);
    }

    static final class Piece {

        final String name;
        final int a, b, c;

        Piece(String name, int a, int b, int c) {
            this.name = name;
            this.a = a;
            this.b = b;
            this.c = c;
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

        Cell(IStructureElement element, int x, int y, int z) {
            this.element = element;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class CaptureLimitException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CaptureLimitException() {
            super("hologram capture budget exhausted", null, false, false);
        }
    }
}
