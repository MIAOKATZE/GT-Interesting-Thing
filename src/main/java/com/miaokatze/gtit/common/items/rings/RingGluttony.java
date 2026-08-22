package com.miaokatze.gtit.common.items.rings;

import java.lang.reflect.Field;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.FoodStats;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Optional;

/**
 * 戒指·饕餮 / Ring of Gluttony
 * 持续恢复饥饿度(1/秒)，回满后恢复饱和度
 * 饥饿度<5时一次性补满饥饿度和饱和度，冷却60秒
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RingGluttony extends BaseRing {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private static final int TICKS_PER_SECOND = 20;
    private static final int EMERGENCY_COOLDOWN_TICKS = 60 * TICKS_PER_SECOND;
    private static final int EMERGENCY_HUNGER_THRESHOLD = 5;

    private static Field saturationField;

    public RingGluttony() {
        super("ring_gluttony");
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayer entityPlayer)) return;

        if (player.ticksExisted % TICKS_PER_SECOND != 0) return;

        FoodStats foodStats = entityPlayer.getFoodStats();
        int hunger = foodStats.getFoodLevel();

        // 应急饱食：饥饿度<5时一次性补满
        if (hunger < EMERGENCY_HUNGER_THRESHOLD) {
            long lastEmergency = itemstack.hasTagCompound() ? itemstack.getTagCompound()
                .getLong("lastEmergency") : 0;
            long currentTime = player.worldObj.getTotalWorldTime();
            if (currentTime - lastEmergency >= EMERGENCY_COOLDOWN_TICKS) {
                foodStats.addStats(20, 20.0f);
                if (!itemstack.hasTagCompound()) itemstack.setTagCompound(new NBTTagCompound());
                itemstack.getTagCompound()
                    .setLong("lastEmergency", currentTime);
                return;
            }
        }

        // 持续恢复：饥饿度<20时+1，否则恢复饱和度
        if (hunger < 20) {
            foodStats.addStats(1, 0.0f);
        } else if (foodStats.getSaturationLevel() < 20.0f) {
            setSaturation(foodStats, Math.min(foodStats.getSaturationLevel() + 1.0f, 20.0f));
        }
    }

    private static void setSaturation(FoodStats foodStats, float value) {
        try {
            if (saturationField == null) {
                try {
                    saturationField = FoodStats.class.getDeclaredField("foodSaturationLevel");
                } catch (NoSuchFieldException e) {
                    // 运行时可能使用 SRG 名
                    saturationField = FoodStats.class.getDeclaredField("field_75125_b");
                }
                saturationField.setAccessible(true);
            }
            saturationField.setFloat(foodStats, value);
        } catch (Exception e) {
            // 饱和度反射失败会导致饕餮戒指的饱和度恢复静默失效，记录便于诊断
            LOG.warn("[GTIT] RingGluttony 设置饱和度失败（反射 foodSaturationLevel 失败）", e);
        }
    }
}
