// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.common.items.pocket.PocketSessions;

@Mixin(Container.class)
public abstract class MixinContainerPocketPersistence {

    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtit$flushPocketBeforeMove(int slot, int button, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        Container container = (Container) (Object) this;
        ItemStack clicked = slot >= 0 && slot < container.inventorySlots.size()
            ? ((Slot) container.inventorySlots.get(slot)).getStack()
            : null;
        if (!PocketSessions.prepareContainerMove(player, clicked, button, mode)) cir.setReturnValue(null);
    }
}
