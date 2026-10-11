package com.miaokatze.gtit.hologram;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;

/** Air connected to the outside of the planned structure, excluding enclosed process cavities. */
final class HologramHatchGeometry {

    private HologramHatchGeometry() {}

    interface Air {

        boolean test(int x, int y, int z);
    }

    static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    static Set<String> exterior(List<int[]> cells, Air air) {
        Set<String> occupied = new HashSet<>(), exterior = new HashSet<>();
        if (cells.isEmpty()) return exterior;
        int minX = Integer.MAX_VALUE, minY = minX, minZ = minX;
        int maxX = Integer.MIN_VALUE, maxY = maxX, maxZ = maxX;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]);
            minY = Math.min(minY, c[1]);
            minZ = Math.min(minZ, c[2]);
            maxX = Math.max(maxX, c[0]);
            maxY = Math.max(maxY, c[1]);
            maxZ = Math.max(maxZ, c[2]);
            if (c.length < 4 || c[3] != 0) occupied.add(key(c[0], c[1], c[2]));
        }
        minX--;
        minY--;
        minZ--;
        maxX++;
        maxY++;
        maxZ++;
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        // Sparse, huge definitions have no safe bounded exterior proof; retain native/sealed direction.
        if (volume > 131072 || volume <= 0) return exterior;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            if (x != minX && x != maxX && y != minY && y != maxY && z != minZ && z != maxZ) continue;
            if (air.test(x, y, z) && exterior.add(key(x, y, z))) queue.add(new int[] { x, y, z });
        }
        while (!queue.isEmpty()) {
            int[] c = queue.remove();
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int x = c[0] + d.offsetX, y = c[1] + d.offsetY, z = c[2] + d.offsetZ;
                String key = key(x, y, z);
                if (x < minX || x > maxX
                    || y < minY
                    || y > maxY
                    || z < minZ
                    || z > maxZ
                    || occupied.contains(key)
                    || exterior.contains(key)
                    || !air.test(x, y, z)) continue;
                exterior.add(key);
                queue.add(new int[] { x, y, z });
            }
        }
        // Open, unfinished cavities may connect to outside air. A declared template
        // position still belongs to the structure and must never be chosen as its exterior.
        for (int[] c : cells) exterior.remove(key(c[0], c[1], c[2]));
        return exterior;
    }

    static int choose(int x, int y, int z, int[] legal, Set<String> exterior) {
        // Prefer a horizontal external air face; never fall through to an enclosed cavity.
        for (int pass = 0; pass < 2; pass++) for (int side : legal) {
            if (pass == 0 && side < 2) continue;
            ForgeDirection d = ForgeDirection.getOrientation(side);
            if (exterior.contains(key(x + d.offsetX, y + d.offsetY, z + d.offsetZ))) return side;
        }
        return -1;
    }
}
