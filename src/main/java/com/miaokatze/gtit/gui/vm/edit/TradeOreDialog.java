package com.miaokatze.gtit.gui.vm.edit;

import net.minecraftforge.oredict.OreDictionary;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;

/** Local modal edits the newest option in append mode, otherwise the primary option. */
final class TradeOreDialog extends Dialog<Boolean> {

    private int index;
    private String ore = "", amount = "1", error = "";

    TradeOreDialog(TradeEditor editor) {
        super("nekoV2:trade_ore", result -> {});
        size(260, 125);
        setDisablePanelsBelow(true);
        setDraggable(false);
        child(
            IKey.str("矿词匹配 / 当前选项数量")
                .asWidget()
                .left(10)
                .top(8));
        child(
            new TextFieldWidget().value(new StringValue.Dynamic(() -> ore, value -> ore = value))
                .setMaxLength(128)
                .left(10)
                .top(28)
                .size(240, 18));
        child(
            new TextFieldWidget().value(new StringValue.Dynamic(() -> amount, value -> amount = value))
                .setNumbers(1, Integer.MAX_VALUE)
                .left(10)
                .top(50)
                .size(100, 18));
        child(
            IKey.dynamic(() -> error)
                .asWidget()
                .left(10)
                .top(72));
        child(
            new ButtonWidget<>().overlay(IKey.str("确定"))
                .left(40)
                .bottom(8)
                .size(65, 18)
                .onMouseTapped(mouse -> {
                    try {
                        editor.applyOre(index, ore, Integer.parseInt(amount));
                        closeIfOpen();
                    } catch (RuntimeException e) {
                        error = "§c矿词无可用物品或数量非法";
                    }
                    return true;
                }));
        child(
            new ButtonWidget<>().overlay(IKey.str("取消"))
                .right(40)
                .bottom(8)
                .size(65, 18)
                .onMouseTapped(mouse -> {
                    closeIfOpen();
                    return true;
                }));
        this.editor = editor;
    }

    private final TradeEditor editor;

    void edit(int index) {
        this.index = index;
        error = "";
        NekoBigItemStack choice = editor.choice(index);
        if (choice != null && editor.isAppend(index)
            && !choice.getAlternatives()
                .isEmpty())
            choice = choice.getAlternatives()
                .get(
                    choice.getAlternatives()
                        .size() - 1);
        ore = choice == null ? "" : choice.getOreDict();
        amount = choice == null ? "1" : String.valueOf(choice.getStackSize());
        if (choice != null && ore.isEmpty()) {
            int[] ids = OreDictionary.getOreIDs(choice.getBaseStack());
            if (ids.length > 0) ore = OreDictionary.getOreName(ids[0]);
        }
    }
}
