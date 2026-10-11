package com.miaokatze.gtit.client.hologram;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.items.hologram.ItemNekoHologramProjector;

/** The projector's partial alpha needs blending even on Forge's ordinary item paths. */
public final class NekoHologramItemRenderer implements IItemRenderer {

    public static void install() {
        MinecraftForgeClient
            .registerItemRenderer(GTITItemList.NekoHologramProjector.getItem(), new NekoHologramItemRenderer());
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return ItemNekoHologramProjector.isProjector(item) && type != ItemRenderType.FIRST_PERSON_MAP;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        IIcon icon = item.getIconIndex();
        if (icon == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .01f);
            GL11.glColor4f(1, 1, 1, 1);
            // A translucent sprite must not hide later geometry through an opaque depth write.
            GL11.glDepthMask(false);
            Tessellator t = Tessellator.instance;
            if (type == ItemRenderType.INVENTORY) {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_CULL_FACE);
                t.startDrawingQuads();
                t.addVertexWithUV(0, 16, 0, icon.getMinU(), icon.getMaxV());
                t.addVertexWithUV(16, 16, 0, icon.getMaxU(), icon.getMaxV());
                t.addVertexWithUV(16, 0, 0, icon.getMaxU(), icon.getMinV());
                t.addVertexWithUV(0, 0, 0, icon.getMinU(), icon.getMinV());
                t.draw();
            } else {
                // Cull the opposite sprite face so two blended faces do not make alpha opaque again.
                GL11.glEnable(GL11.GL_CULL_FACE);
                GL11.glCullFace(GL11.GL_BACK);
                GL11.glFrontFace(GL11.GL_CCW);
                // Forge applies the usual .5 scale, bob and rotation before this call.
                // Match vanilla's one-item dropped sprite origin and thickness offset.
                if (type == ItemRenderType.ENTITY) GL11.glTranslatef(-.5f, -.25f, .0203125f);
                ItemRenderer.renderItemIn2D(
                    t,
                    icon.getMaxU(),
                    icon.getMinV(),
                    icon.getMinU(),
                    icon.getMaxV(),
                    icon.getIconWidth(),
                    icon.getIconHeight(),
                    .0625f);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
