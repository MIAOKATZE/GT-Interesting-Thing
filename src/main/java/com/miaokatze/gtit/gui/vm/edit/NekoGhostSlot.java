package com.miaokatze.gtit.gui.vm.edit;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

/** A normal synchronized phantom slot explicitly authorized to receive an embedded catalog copy. */
public class NekoGhostSlot extends PhantomItemSlot implements NekoGhostTarget {

    public NekoGhostSlot() {
        tooltipBuilder(t -> {
            t.addLine("目录放入不同物品时使用所选数量（受本槽堆叠上限限制）");
            t.addLine("同物品沿用幻影槽规则：左键减少 1、右键增加 1；滚轮调整数量");
        });
    }

    @Override
    public boolean acceptNeiGhost(ItemStack stack, int button) {
        if (stack == null) return false;
        int limit = getSyncHandler().getSlot()
            .getItemStackLimit(stack);
        if (button != 1 && stack.stackSize > limit) {
            Minecraft.getMinecraft().thePlayer
                .addChatMessage(new ChatComponentText("§e此幻影配置槽最多 " + limit + " 个；请调整目录数量后重选。目录选取仍保留。"));
            return false;
        }
        return handleDragAndDrop(stack.copy(), button == 1 ? 1 : 0);
    }
}
