package com.miaokatze.gtit.hologram;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/** World-axis quarter turns shared by geometry, machine fronts and StructureLib controller alignment. */
final class HologramVirtualTransform {

    private HologramVirtualTransform() {}

    static int[] turn(int x, int y, int z, boolean vertical) {
        return vertical ? new int[] { x, -z, y } : new int[] { -z, y, x };
    }

    static int front(int facing, boolean vertical) {
        if (facing < 0 || facing > 5) return facing;
        ForgeDirection direction = ForgeDirection.getOrientation(facing);
        int[] vector = turn(direction.offsetX, direction.offsetY, direction.offsetZ, vertical);
        for (ForgeDirection candidate : ForgeDirection.VALID_DIRECTIONS)
            if (candidate.offsetX == vector[0] && candidate.offsetY == vector[1] && candidate.offsetZ == vector[2])
                return candidate.ordinal();
        throw new IllegalStateException("quarter turn did not preserve a direction");
    }

    static ExtendedFacing alignment(ExtendedFacing original, boolean vertical) {
        int direction = front(
            original.getDirection()
                .ordinal(),
            vertical);
        int left = front(
            original.getRelativeLeftInWorld()
                .ordinal(),
            vertical);
        int down = front(
            original.getRelativeDownInWorld()
                .ordinal(),
            vertical);
        // Preserve handedness/flip; matching only front would lose roll when pitching an east/west controller.
        for (ExtendedFacing candidate : ExtendedFacing.values()) if (candidate.getDirection()
            .ordinal() == direction && candidate.getFlip() == original.getFlip()
            && candidate.getRelativeLeftInWorld()
                .ordinal() == left
            && candidate.getRelativeDownInWorld()
                .ordinal() == down)
            return candidate;
        throw new IllegalStateException("quarter turn did not preserve controller alignment");
    }

    static ExtendedFacing controller(HologramPresets.Preset preset) {
        if (preset.controllerFacing != null) return ExtendedFacing.byIndex(preset.controllerFacing);
        for (HologramPresets.Cell cell : preset.cells)
            if (cell.controller) return ExtendedFacing.of(ForgeDirection.getOrientation(cell.facing));
        return ExtendedFacing.DEFAULT;
    }

    static HologramCapture.Cell at(HologramCapture.Cell old, int x, int y, int z) {
        HologramCapture.Cell cell = new HologramCapture.Cell(old.element, x, y, z, old.elementTrigger);
        cell.block = old.block;
        cell.meta = old.meta;
        cell.unknown = old.unknown;
        cell.anchor = old.anchor;
        cell.userPosition = old.userPosition;
        cell.role = old.role;
        cell.family = old.family;
        cell.iconOnly = old.iconOnly;
        cell.renderKind = old.renderKind;
        cell.hintIcons = old.hintIcons;
        cell.hintTint = old.hintTint;
        cell.hintIndex = old.hintIndex;
        cell.candidates = old.candidates;
        cell.candidateComplete = old.candidateComplete;
        cell.candidateBoundary = old.candidateBoundary;
        return cell;
    }
}
