// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.loot;

import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.config.Config;

public final class NekoCoinRewards {

    private NekoCoinRewards() {}

    static boolean eligible(EntityPlayer p) {
        return p instanceof net.minecraft.entity.player.EntityPlayerMP && !(p instanceof FakePlayer)
            && !p.worldObj.isRemote;
    }

    // Gate the whole fishing lottery so BOTH final probabilities scale exactly, including rare-first priority.
    static int roll(Random rng, double scale, double rare, double ordinary) {
        if ((rare <= 0 && ordinary <= 0) || scale <= 0 || scale < 1 && rng.nextDouble() >= scale) return 0;
        if (rare > 0 && rng.nextDouble() * 100 < rare) return 2;
        return ordinary > 0 && rng.nextDouble() * 100 < ordinary ? 1 : 0;
    }

    public static void award(EntityPlayer p, double scale) {
        if (!eligible(p)) return;
        int result = roll(p.worldObj.rand, scale, Config.fishingShimmeringNekoCoinChance, Config.fishingNekoCoinChance);
        if (result == 0) return;
        ItemStack coin = (result == 2 ? GTITItemList.ShimmeringNekoCoin : GTITItemList.NekoCoin).get(1);
        if (coin != null && !p.inventory.addItemStackToInventory(coin)) p.entityDropItem(coin, 1);
    }
}
