package com.miaokatze.gtit.gui.vm.edit;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;

/** Client-local ghost editor. No phantom synchronization can overwrite the full choice list. */
final class TradeChoiceSlot extends ItemDisplayWidget
    implements Interactable, RecipeViewerGhostIngredientSlot<ItemStack> {

    private final TradeEditor editor;
    private final int index;

    TradeChoiceSlot(TradeEditor editor, int index) {
        this.editor = editor;
        this.index = index;
        item(new ObjectValue.Dynamic<>(ItemStack.class, () -> {
            NekoBigItemStack choice = editor.choice(index);
            if (choice == null) return null;
            ItemStack icon = choice.getBaseStack()
                .copy();
            icon.stackSize = choice.getStackSize();
            return icon;
        }, value -> {}));
        displayAmount(true);
        background(GuiTextures.SLOT_ITEM);
        tooltipAutoUpdate(true);
        tooltipBuilder(t -> {
            t.addLine("Alt+左键：开启/关闭多重叠加；开启后放入物品追加选项");
            t.addLine("中键：编辑最后选项矿词与数量（空矿词恢复精确物品）");
            t.addLine("右键：移除最后选项；Shift+右键：清空；滚轮：调整最后选项数量");
            t.addLine(editor.isAppend(index) ? "§a多重叠加已开启" : "§7多重叠加未开启");
            NekoBigItemStack choice = editor.choice(index);
            if (choice != null) {
                t.addLine(index < 16 ? "每笔完整满足一项；矿词可混合同词物品" : "每笔随机选择一项；矿词再随机选择一种物品");
                describe(t, choice);
                for (NekoBigItemStack option : choice.getAlternatives()) describe(t, option);
                if (index < 16 && (choice.hasOreDict() || !choice.getAlternatives()
                    .isEmpty())) t.addLine("需求中的精确猫猫币选项可使用钱包；矿词选项仅匹配实体物品");
            }
        });
    }

    private static void describe(com.cleanroommc.modularui.screen.RichTooltip t, NekoBigItemStack option) {
        t.addLine(
            option.getStackSize() + " × "
                + (option.hasOreDict() ? "矿词 [" + option.getOreDict() + "]"
                    : option.getBaseStack()
                        .getDisplayName()));
    }

    @Override
    public Result onMousePressed(int button) {
        if (button == 2) editor.openOre(index);
        else if (button == 1) editor.removeChoice(index, Interactable.hasShiftDown());
        else if (button == 0) {
            ItemStack cursor = net.minecraft.client.Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
            if (Interactable.hasAltDown()) {
                if (!editor.isAppend(index)) {
                    editor.toggleAppend(index);
                    if (cursor != null && (editor.choice(index) == null || editor.choice(index)
                        .getStackSize() != cursor.stackSize
                        || !editor.choice(index)
                            .getBaseStack()
                            .isItemEqual(cursor)
                        || !ItemStack.areItemStackTagsEqual(
                            editor.choice(index)
                                .getBaseStack(),
                            cursor))) {
                        editor.putChoice(index, cursor);
                    }
                } else if (cursor == null) editor.toggleAppend(index);
                else editor.putChoice(index, cursor);
            } else if (cursor != null) editor.putChoice(index, cursor);
        }
        return Result.SUCCESS;
    }

    @Override
    public boolean onMouseRelease(int button) {
        return true;
    }

    @Override
    public boolean onMouseScroll(UpOrDown direction, int amount) {
        NekoBigItemStack target = editor.choice(index);
        if (target != null) {
            if (editor.isAppend(index) && !target.getAlternatives()
                .isEmpty())
                target = target.getAlternatives()
                    .get(
                        target.getAlternatives()
                            .size() - 1);
            long count = (long) target.getStackSize() + direction.modifier * (Interactable.hasShiftDown() ? 64 : 1);
            target.setStackSize((int) Math.max(1, Math.min(Integer.MAX_VALUE, count)));
        }
        return true;
    }

    @Override
    public boolean handleDragAndDrop(ItemStack stack, int button) {
        if (!areAncestorsEnabled()) return false;
        editor.putChoice(index, stack);
        stack.stackSize = 0;
        return true;
    }
}
