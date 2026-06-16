package com.miaokatze.gtit.common.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtit.config.GiftConfig;
import com.miaokatze.gtit.register.CreativeTabManager;

/**
 * 新手宝箱
 * 右击打开获得一系列物品（必中物品+随机物品）
 * 默认携带附魔光效
 */
public class StarterGift extends Item {

    private static final Random RANDOM = new Random();

    public StarterGift() {
        super();
        setUnlocalizedName("starter_gift");
        setTextureName("gtit:starter_gift");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
        setHasSubtypes(false);
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return true; // 附魔光效
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        for (int i = 0;; i++) {
            String key = "item.starter_gift.tooltip." + i;
            String line = StatCollector.translateToLocal(key);
            if (line.equals(key)) break;
            tooltip.add(EnumChatFormatting.LIGHT_PURPLE + line);
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            List<ItemStack> itemsToGive = generateItems();
            for (ItemStack gift : itemsToGive) {
                if (!player.inventory.addItemStackToInventory(gift.copy())) {
                    // 背包满了，丢到地上
                    world.spawnEntityInWorld(
                        new EntityItem(world, player.posX, player.posY + 1, player.posZ, gift.copy()));
                }
            }
            stack.stackSize--;
        }
        return stack;
    }

    private List<ItemStack> generateItems() {
        List<ItemStack> items = new ArrayList<>();

        // 必中物品
        for (GiftConfig.ItemEntry entry : GiftConfig.getGuaranteedItems()) {
            ItemStack stack = entry.toItemStack();
            if (stack != null) {
                items.add(stack);
            }
        }

        // 随机物品
        List<GiftConfig.ItemEntry> randomPool = GiftConfig.getRandomItems();
        int randomCount = GiftConfig.getRandomCount();
        if (!randomPool.isEmpty() && randomCount > 0) {
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < randomPool.size(); i++) {
                indices.add(i);
            }
            Collections.shuffle(indices, RANDOM);
            int count = Math.min(randomCount, indices.size());
            for (int i = 0; i < count; i++) {
                ItemStack stack = randomPool.get(indices.get(i))
                    .toItemStack();
                if (stack != null) {
                    items.add(stack);
                }
            }
        }

        return items;
    }
}
