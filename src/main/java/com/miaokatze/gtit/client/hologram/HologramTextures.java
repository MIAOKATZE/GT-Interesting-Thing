package com.miaokatze.gtit.client.hologram;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.IBlockWithTextures;
import gregtech.api.interfaces.IColorModulationContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.render.GTMultiTextureRender;
import gregtech.common.render.GTRenderedTexture;
import gregtech.common.render.GTSidedTextureRender;
import gregtech.common.render.IIconTexture;

/** Read-only atlas samples, resolved once per snapshot; never creates tile entities or invokes model renderers. */
final class HologramTextures {

    static final class Sample {

        final IIcon icon;
        final int color;

        Sample(IIcon icon, int color) {
            this.icon = icon;
            this.color = color;
        }
    }

    static final class Material {

        final List<Sample>[] sides;
        final boolean gregTech, fallback;

        Material(List<Sample>[] sides, boolean gregTech, boolean fallback) {
            this.sides = sides;
            this.gregTech = gregTech;
            this.fallback = fallback;
        }
    }

    private static final Map<String, Material> CACHE = new HashMap<>();
    private static final Map<HologramState.Cell, Material[]> RESOLVED = new IdentityHashMap<>();
    private static HologramState snapshot;
    private static final Field MULTI = texturesField(GTMultiTextureRender.class);
    private static final Field SIDED = texturesField(GTSidedTextureRender.class);
    private static boolean reloadInstalled;

    private HologramTextures() {}

    static void clear() {
        CACHE.clear();
        RESOLVED.clear();
        snapshot = null;
    }

    static void begin(HologramState state) {
        installReloadListener();
        if (snapshot != state) {
            CACHE.clear();
            RESOLVED.clear();
            snapshot = state;
        }
    }

    static void installReloadListener() {
        if (!reloadInstalled) {
            reloadInstalled = true;
            if (Minecraft.getMinecraft()
                .getResourceManager() instanceof IReloadableResourceManager)
                ((IReloadableResourceManager) Minecraft.getMinecraft()
                    .getResourceManager()).registerReloadListener(manager -> HologramRenderer.release());
        }
    }

    static Material material(HologramState.Cell cell, boolean current) {
        installReloadListener();
        current = current || cell.anchor || (cell.status.equals("protected") && cell.actualStack != null);
        Material[] pair = RESOLVED.get(cell);
        if (pair == null) {
            pair = new Material[2];
            RESOLVED.put(cell, pair);
        }
        int index = current ? 1 : 0;
        if (pair[index] != null) return pair[index];
        ItemStack stack = current ? cell.actualStack : cell.targetStack;
        String key = (current ? cell.id : cell.wantId) + ":"
            + (current ? cell.meta : cell.wantMeta)
            + ":"
            + (stack == null ? "" : stack.getItem() + ":" + stack.getItemDamage() + ":" + stack.getTagCompound())
            + ":"
            + cell.iconOnly
            + ":"
            + Arrays.toString(cell.hintIcons)
            + ":"
            + Arrays.toString(cell.hintTint);
        Material material = CACHE.get(key);
        if (material == null) {
            material = resolve(cell, current);
            CACHE.put(key, material);
        }
        pair[index] = material;
        return material;
    }

    @SuppressWarnings("unchecked")
    private static Material resolve(HologramState.Cell cell, boolean current) {
        List<Sample>[] sides = (List<Sample>[]) new List<?>[6];
        for (int side = 0; side < 6; side++) sides[side] = new ArrayList<>();
        current = current || cell.anchor || (cell.status.equals("protected") && cell.actualStack != null);
        Block block = cell.block(current);
        int meta = current || cell.anchor ? cell.meta : cell.wantMeta;
        ItemStack stack = current || cell.anchor ? cell.actualStack : cell.targetStack;
        boolean gt = stack != null && stack.getItem() instanceof ItemMachines;
        ITexture[][] textures = null;
        try {
            if (gt) {
                int id = stack.getItemDamage();
                IMetaTileEntity machine = id >= 0 && id < GregTechAPI.METATILEENTITIES.length
                    ? GregTechAPI.METATILEENTITIES[id]
                    : null;
                if (machine != null) {
                    textures = machine.getInventoryTextures();
                    if (textures == null) {
                        textures = new ITexture[6][];
                        // Prototype only: no NBT load, facing mutation, or synthetic base tile.
                        for (int side = 0; side < 6; side++) textures[side] = machine.getTexture(
                            null,
                            ForgeDirection.getOrientation(side),
                            ForgeDirection.NORTH,
                            -1,
                            false,
                            false);
                    }
                }
            } else if (block instanceof IBlockWithTextures) {
                textures = ((IBlockWithTextures) block).getInventoryTextures(meta);
            }
        } catch (RuntimeException ignored) {
            // Custom textures may require a world/base tile; fall back to their ordinary block icon.
        }
        boolean fallback = false;
        TextureMap atlas = Minecraft.getMinecraft()
            .getTextureMapBlocks();
        for (int side = 0; side < 6; side++) {
            try {
                if (textures != null && side < textures.length && textures[side] != null)
                    for (ITexture texture : textures[side]) append(sides[side], texture, side, 0);
                if (gt && sides[side].isEmpty()) fallback = true;
                if (sides[side].isEmpty() && cell.iconOnly && !cell.hintIcons[side].isEmpty())
                    sides[side].add(new Sample(atlas.getAtlasSprite(cell.hintIcons[side]), cell.textureColor()));
                if (sides[side].isEmpty() && block != null && block != Blocks.air) {
                    IIcon icon = block.getIcon(side, meta);
                    if (icon != null) sides[side].add(new Sample(icon, block.getRenderColor(meta)));
                }
            } catch (RuntimeException ignored) {
                // A broken optional icon must not hide the rest of the schematic.
            }
            if (sides[side].isEmpty()) {
                fallback = true;
                sides[side].add(new Sample(atlas.getAtlasSprite("missingno"), 0xffffff));
            }
        }
        return new Material(sides, gt, fallback);
    }

    private static void append(List<Sample> samples, ITexture texture, int side, int depth) {
        if (texture == null || depth > 8) return;
        Field field = texture instanceof GTMultiTextureRender ? MULTI
            : texture instanceof GTSidedTextureRender ? SIDED : null;
        if (field != null) {
            try {
                ITexture[] children = (ITexture[]) field.get(texture);
                if (texture instanceof GTSidedTextureRender) {
                    if (side < children.length) append(samples, children[side], side, depth + 1);
                } else for (ITexture child : children) append(samples, child, side, depth + 1);
            } catch (IllegalAccessException ignored) {}
        } else if (texture instanceof IIconTexture) {
            IIcon icon = ((IIconTexture) texture).getIcon(side, null);
            int color = 0xffffff;
            if (texture instanceof IColorModulationContainer) {
                short[] rgba = ((IColorModulationContainer) texture).getRGBA();
                if (rgba != null && rgba.length >= 3)
                    color = (rgba[0] & 255) << 16 | (rgba[1] & 255) << 8 | (rgba[2] & 255);
            }
            if (icon != null) samples.add(new Sample(icon, color));
            if (texture instanceof GTRenderedTexture) {
                IIcon overlay = ((GTRenderedTexture) texture).getIcon(ForgeDirection.getOrientation(side), true, null);
                if (overlay != null) samples.add(new Sample(overlay, 0xffffff));
            }
        }
    }

    private static Field texturesField(Class<?> type) {
        try {
            Field field = type.getDeclaredField("mTextures");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }
}
