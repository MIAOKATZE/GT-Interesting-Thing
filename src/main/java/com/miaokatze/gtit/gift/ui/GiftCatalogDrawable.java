package com.miaokatze.gtit.gift.ui;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.miaokatze.gtit.common.items.StarterGift;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Uses Minecraft's item atlas so animated gift icons share its animation clock. */
final class GiftCatalogDrawable implements IDrawable {

    private final Supplier<String> key;

    GiftCatalogDrawable(Supplier<String> key) {
        this.key = key;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        IIcon icon = StarterGift.catalogIcon(key.get());
        if (icon == null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + height, 0, icon.getMinU(), icon.getMaxV());
            tessellator.addVertexWithUV(x + width, y + height, 0, icon.getMaxU(), icon.getMaxV());
            tessellator.addVertexWithUV(x + width, y, 0, icon.getMaxU(), icon.getMinV());
            tessellator.addVertexWithUV(x, y, 0, icon.getMinU(), icon.getMinV());
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }
}
