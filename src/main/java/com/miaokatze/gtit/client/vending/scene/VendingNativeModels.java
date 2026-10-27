package com.miaokatze.gtit.client.vending.scene;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtit.client.vending.scene.VendingNativeModelPolicy.Geometry;
import com.miaokatze.gtit.common.machine.v2.NekoOutputModelShape;
import com.miaokatze.miaokatzecore.client.miaovision.SceneSpriteTransform;

/** Audited native GT paths only; no newly constructed or spawned entity and no replacement item inventory. */
final class VendingNativeModels {

    private static boolean selectorChecked;
    private static Method selector;

    private VendingNativeModels() {}

    static boolean render(ItemStack stack) {
        IItemRenderer equipped = MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.EQUIPPED);
        IItemRenderer inventory = MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
        IItemRenderer registered = equipped != null ? equipped : inventory;
        if (registered == null || !registered.getClass()
            .getName()
            .startsWith("gregtech.")) return false;
        IItemRenderer target = selected(registered, stack);
        Geometry geometry = target == null ? Geometry.NATIVE_FALLBACK
            : VendingNativeModelPolicy.geometry(
                target.getClass()
                    .getName());
        // A client-only registration may replace a common identity; never give it a mismatched thin transform.
        if (geometry == Geometry.TESSERACT && !NekoOutputModelShape.tesseract(stack)
            || geometry == Geometry.ROTATING_SPRITE && !NekoOutputModelShape.rotatingMaterial(stack))
            geometry = Geometry.NATIVE_FALLBACK;
        if ((geometry == Geometry.SPRITE || geometry == Geometry.ROTATING_SPRITE) && equipped == null
            || geometry == Geometry.TESSERACT && inventory == null) geometry = Geometry.NATIVE_FALLBACK;
        if (geometry == Geometry.NATIVE_FALLBACK && inventory == null) return false;
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(
                stack.getItemSpriteNumber() == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);
        GL11.glEnable(GL11.GL_NORMALIZE);
        RenderHelper.enableStandardItemLighting();
        boolean oldFrame = RenderItem.renderInFrame;
        try {
            // Some special GT effects read this static flag; restore it independently of the GL scope.
            RenderItem.renderInFrame = false;
            if (geometry == Geometry.TESSERACT && inventory != null) {
                // This renderer's INVENTORY geometry is centered at zero, unlike Forge's block helper [0,1].
                GL11.glScaled(
                    VendingNativeModelPolicy.TESSERACT_SCALE,
                    VendingNativeModelPolicy.TESSERACT_SCALE,
                    VendingNativeModelPolicy.TESSERACT_SCALE);
                inventory.renderItem(IItemRenderer.ItemRenderType.INVENTORY, stack, new RenderBlocks());
            } else if (geometry == Geometry.ROTATING_SPRITE && equipped != null) {
                // Its own animation rotates a plane around (.5,.5,0); use isotropic bounds, never Z extrusion.
                GL11.glScaled(
                    VendingNativeModelPolicy.ROTATING_SCALE,
                    VendingNativeModelPolicy.ROTATING_SCALE,
                    VendingNativeModelPolicy.ROTATING_SCALE);
                GL11.glTranslated(-.5, -.5, 0);
                equipped.renderItem(IItemRenderer.ItemRenderType.EQUIPPED, stack, new RenderBlocks(), mc.thePlayer);
            } else if (geometry == Geometry.SPRITE && equipped != null) {
                GL11.glScaled(1, 1, SceneSpriteTransform.SCALE_Z);
                GL11.glTranslated(
                    SceneSpriteTransform.OFFSET_XY,
                    SceneSpriteTransform.OFFSET_XY,
                    SceneSpriteTransform.OFFSET_Z);
                equipped.renderItem(IItemRenderer.ItemRenderType.EQUIPPED, stack, new RenderBlocks(), mc.thePlayer);
            } else {
                // Unknown GT custom geometry keeps the existing native inventory contract, not an assumed sprite.
                if (inventory.shouldUseRenderHelper(
                    IItemRenderer.ItemRenderType.INVENTORY,
                    stack,
                    IItemRenderer.ItemRendererHelper.INVENTORY_BLOCK)) GL11.glTranslated(-.5, -.5, -.5);
                else {
                    GL11.glTranslated(-.5, .5, 0);
                    GL11.glScaled(1.0 / 16, -1.0 / 16, 1.0 / 16);
                }
                inventory.renderItem(IItemRenderer.ItemRenderType.INVENTORY, stack, new RenderBlocks());
            }
            return true;
        } finally {
            RenderItem.renderInFrame = oldFrame;
        }
    }

    /** Read only the pinned dispatch decision. Missing/inaccessible internals select native fallback, not a guess. */
    private static IItemRenderer selected(IItemRenderer registered, ItemStack stack) {
        if (!VendingNativeModelPolicy.DISPATCHER.equals(
            registered.getClass()
                .getName()))
            return registered;
        if (!selectorChecked) {
            selectorChecked = true;
            try {
                Method method = registered.getClass()
                    .getDeclaredMethod("getRendererForItemStack", ItemStack.class);
                if (method.getReturnType() == IItemRenderer.class
                    && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    selector = method;
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                selector = null;
            }
        }
        if (selector == null || selector.getDeclaringClass() != registered.getClass()) return null;
        try {
            Object result = selector.invoke(registered, stack);
            return result instanceof IItemRenderer ? (IItemRenderer) result : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }
}
