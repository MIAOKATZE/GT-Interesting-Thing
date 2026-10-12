package com.miaokatze.gtit.client.hologram;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.miaokatze.gtit.testutil.SimpleAssert;

/** Volume selection includes hidden interior cells; axis movement remains world-oriented after camera turns. */
public final class HologramSelectionRegression {

    private HologramSelectionRegression() {}

    public static void run() {
        HologramSelection selection = new HologramSelection();
        selection.click(1, false);
        selection.click(2, true);
        SimpleAssert.eq(2, selection.size(), "control click extends selection");
        selection.click(1, true);
        SimpleAssert.that(!selection.contains(1) && selection.contains(2), "control click toggles only its cell");
        HologramState state = volume();
        SimpleAssert.that(
            state.cells.get(8).sourceMissing && !state.cells.get(8).missingBlock
                && state.cells.get(8).sourceWarning.equals("source unavailable"),
            "vanilla or legacy members preserve missing-source warning independently of missing block and mod version rows");
        selection.box(state, 0, 7, false);
        SimpleAssert.eq(8, selection.size(), "shift volume includes all eight cells regardless of visible faces");
        SimpleAssert.that(!selection.contains(8), "volume excludes adjacent cell outside all three bounds");
        selection.box(state, 7, 0, false);
        SimpleAssert.eq(8, selection.size(), "reverse corner order produces the same volume");
        selection.click(8, true);
        selection.structure(state, "machine-a", true);
        SimpleAssert.eq(9, selection.size(), "additive whole structure keeps unrelated selection");
        selection.structure(state, "machine-a", false);
        SimpleAssert
            .eq(8, selection.size(), "whole structure selection follows membership rather than screen rectangle");
        selection.retain(4);
        SimpleAssert.eq(4, selection.size(), "snapshot shrink never leaves invalid indices");
        sharedWallRegression();
        placementConfirmationRegression();
        HologramGizmo gizmo = new HologramGizmo();
        gizmo.project(100, 100, Math.PI / 4, Math.PI / 6);
        for (int axis = 0; axis < 3; axis++) {
            double[] end = gizmo.end(axis);
            SimpleAssert.eq(
                axis,
                gizmo.pick((int) Math.round(end[0]), (int) Math.round(end[1])),
                "each RGB handle has independent hit region");
        }
        gizmo.project(100, 100, Math.PI, 0);
        gizmo.begin(0, 100, 100);
        SimpleAssert.eq(2, gizmo.update(76, 100), "rotated camera drag still produces positive world X");
        gizmo.cancel();
        SimpleAssert.eq(-1, gizmo.axis(), "escape/release cancels active axis");
        gizmo.project(100, 100, 0, 0);
        gizmo.begin(1, 100, 100);
        SimpleAssert.eq(2, gizmo.update(100, 76), "screen-up drag produces positive world Y");
        gizmo.project(100, 100, Math.PI / 2, 0);
        gizmo.begin(2, 100, 100);
        SimpleAssert.eq(2, gizmo.update(76, 100), "Z handle follows camera orientation");
        SimpleAssert.that(HologramGizmo.distance(5, 3, 0, 0, 10, 0) == 3, "axis pick uses segment distance");
        HologramNbtSelection storage = new HologramNbtSelection();
        storage.load(true, new NBTTagList());
        java.util.List<String> available = java.util.Arrays.asList("Items", "Tank", "Configuration");
        storage.toggle("Items", available);
        SimpleAssert.that(
            !storage.all() && !storage.selected("Items")
                && storage.selected("Tank")
                && storage.selected("Configuration"),
            "deselecting one root key materializes all other actual entries");
        HologramNbtSelection placement = new HologramNbtSelection();
        placement.load(storage.all(), storage.keys());
        placement.toggle("Tank", available);
        SimpleAssert.that(
            storage.selected("Tank") && !placement.selected("Tank"),
            "one-placement downgrade never edits stored blueprint key selection");
        placement.retainCovers(false);
        SimpleAssert.that(
            storage.retainCovers() && !placement.retainCovers(),
            "one-placement cover downgrade never changes the blueprint cover option");
        storage.all(false);
        SimpleAssert.that(storage.retainCovers(), "root-key cancellation leaves covers independently retained");
        placement.all(false);
        SimpleAssert.eq(
            0,
            placement.keys()
                .tagCount(),
            "empty key list explicitly cancels optional root entries");
        storage.all(true);
        SimpleAssert.that(storage.selected("NewMachineKey"), "default all retains newly encountered actual keys");
    }

    private static HologramState volume() {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagList cells = new NBTTagList();
        for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) {
            NBTTagCompound cell = new NBTTagCompound();
            cell.setInteger("dx", x);
            cell.setInteger("dy", y);
            cell.setInteger("dz", z);
            cell.setString("structureId", "machine-a");
            cells.appendTag(cell);
        }
        NBTTagCompound outside = new NBTTagCompound();
        outside.setInteger("dx", 2);
        outside.setBoolean("sourceMissing", true);
        outside.setString("sourceWarning", "source unavailable");
        outside.setString("sourceClass", "missing.Controller");
        cells.appendTag(outside);
        data.setTag("cells", cells);
        return new HologramState(data);
    }

    private static void placementConfirmationRegression() {
        NBTTagCompound state = new NBTTagCompound();
        state.setLong("planRevision", 42);
        state.setInteger("missing", 1);
        SimpleAssert.that(
            !HologramPlacementConfirmation.canBuild(state, 42),
            "ordinary structure still blocks missing materials");
        state.setBoolean("virtual", true);
        state.setBoolean("virtualPlacement", true);
        SimpleAssert
            .that(!HologramPlacementConfirmation.canBuild(state, 42), "unlocked blueprint cannot request construction");
        state.setBoolean("virtualLocked", true);
        SimpleAssert.that(
            HologramPlacementConfirmation.canBuild(state, 42),
            "locked missing-material blueprint can request first server confirmation");
        SimpleAssert.that(
            !HologramPlacementConfirmation.forceReview(state, 42),
            "client cannot invent the second confirmation capability");
        state.setBoolean("forcePlacementReady", true);
        SimpleAssert.that(
            HologramPlacementConfirmation.canBuild(state, 42) && HologramPlacementConfirmation.forceReview(state, 42),
            "second confirmation directly requests build without a scan that would clear server capability");
        state.setLong("planRevision", 43);
        SimpleAssert.that(
            !HologramPlacementConfirmation.canBuild(state, 42) && !HologramPlacementConfirmation.forceReview(state, 42),
            "editing/source revision change invalidates the reviewed first and second construction intents");
        state.setBoolean("forcePlacementReady", false);
        SimpleAssert.that(
            !HologramPlacementConfirmation.forceReview(state, 43),
            "server source/placement changes revoke force review");
        state.setBoolean("virtual", false);
        state.setInteger("missing", 0);
        SimpleAssert.that(
            HologramPlacementConfirmation.canBuild(state, 43),
            "fully stocked ordinary construction remains supported");
    }

    private static void sharedWallRegression() {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagList cells = new NBTTagList();
        NBTTagCompound first = new NBTTagCompound();
        first.setInteger("dx", -2);
        first.setInteger("x", -2);
        first.setString("structureId", "machine-a");
        cells.appendTag(first);
        NBTTagCompound shared = new NBTTagCompound();
        shared.setInteger("dx", 4);
        shared.setInteger("x", 4);
        shared.setString("structureId", "machine-a");
        NBTTagList memberships = new NBTTagList();
        memberships.appendTag(new NBTTagString("machine-a"));
        memberships.appendTag(new NBTTagString("machine-b"));
        memberships.appendTag(new NBTTagString("machine-b"));
        memberships.appendTag(new NBTTagString(""));
        shared.setTag("structureIds", memberships);
        cells.appendTag(shared);
        NBTTagCompound last = new NBTTagCompound();
        last.setInteger("dx", 9);
        last.setInteger("x", 9);
        last.setString("structureId", "machine-b");
        last.setTag("structureIds", new NBTTagList());
        cells.appendTag(last);
        data.setTag("cells", cells);
        HologramState state = new HologramState(data);
        SimpleAssert.eq(3, state.cells.size(), "shared wall remains one captured/indexed/renderable cell");
        SimpleAssert.that(
            state.cells.get(0)
                .belongsToStructure("machine-a")
                && state.cells.get(2)
                    .belongsToStructure("machine-b"),
            "absent and empty membership lists retain legacy primary ID");
        SimpleAssert
            .eq(2, state.cells.get(1).structureIds.size(), "membership snapshot filters duplicate and empty IDs");
        SimpleAssert.that(
            state.cells.get(1)
                .belongsToStructure("machine-a")
                && state.cells.get(1)
                    .belongsToStructure("machine-b"),
            "shared wall belongs to both declared structures");
        HologramSelection selection = new HologramSelection();
        selection.structure(state, "machine-a", false);
        SimpleAssert.that(
            selection.size() == 2 && selection.contains(0) && selection.contains(1) && !selection.contains(2),
            "first structure selects shared wall plus its own exclusive cell");
        selection.structure(state, "machine-b", false);
        SimpleAssert.that(
            selection.size() == 2 && !selection.contains(0) && selection.contains(1) && selection.contains(2),
            "secondary membership selects shared wall even when primary ID belongs to another structure");
        SimpleAssert.that(
            "machine-b".equals(selection.structureId(state, "machine-a", selection.first())),
            "clicked secondary group takes precedence over both active group and shared first cell primary ID");
        selection.click(1, false);
        SimpleAssert.that(
            "machine-a".equals(selection.structureId(state, "", selection.first())),
            "single-cell click resets edge intent and resolves its primary group");
        SimpleAssert.that(
            "machine-b".equals(selection.structureId(state, "machine-b", selection.first())),
            "without edge intent an explicit active group takes precedence over shared primary membership");
        selection.structure(state, "machine-b", false);
        NBTTagCompound missingGroupData = (NBTTagCompound) data.copy();
        NBTTagList declared = new NBTTagList();
        NBTTagCompound remaining = new NBTTagCompound();
        remaining.setString("id", "machine-a");
        declared.appendTag(remaining);
        missingGroupData.setTag("structures", declared);
        HologramState missingGroup = new HologramState(missingGroupData);
        SimpleAssert.that(
            "machine-a".equals(selection.structureId(missingGroup, "machine-b", selection.first())),
            "snapshot declaration removal rejects stale clicked and active group even if shared membership tags remain");
        SimpleAssert.that(
            "machine-a".equals(selection.structureId(state, "", selection.first())),
            "returning group cannot resurrect the cleared clicked-edge intent");
        selection.clear();
        SimpleAssert.that(
            selection.structureId(state, "", -1)
                .isEmpty(),
            "clearing selection resets clicked group");
        selection.structure(state, "machine-b", false);
        selection.structure(state, "machine-a", true);
        SimpleAssert.eq(3, selection.size(), "additive two-structure selection never duplicates shared wall index");
        java.util.Map<String, int[]> bounds = HologramRenderer.structureBounds(state);
        SimpleAssert.eq(2, bounds.size(), "renderer retains independently selectable boxes for both structures");
        SimpleAssert.eq(-2, bounds.get("machine-a")[0], "first box starts at its exclusive cell");
        SimpleAssert.eq(4, bounds.get("machine-a")[3], "shared wall extends first box maximum");
        SimpleAssert.eq(4, bounds.get("machine-b")[0], "shared wall extends second box minimum");
        SimpleAssert.eq(9, bounds.get("machine-b")[3], "second box ends at its exclusive cell");
    }
}
