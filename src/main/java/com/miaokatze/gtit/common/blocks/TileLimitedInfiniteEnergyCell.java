package com.miaokatze.gtit.common.blocks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.events.MENetworkPowerStorage;
import appeng.api.networking.events.MENetworkPowerStorage.PowerEventType;
import appeng.api.util.AECableType;
import appeng.me.GridAccessException;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;

public class TileLimitedInfiniteEnergyCell extends AENetworkTile implements IAEPowerStorage {

    public static final double BASE_MAX_POWER = 2000000.0;
    private static final double BASE_AUTO_CHARGE_RATE = 1000.0;

    private double internalCurrentPower = 0.0;
    private byte currentMeta = -1;

    public TileLimitedInfiniteEnergyCell() {
        this.getProxy()
            .setIdlePowerUsage(0);
    }

    @TileEvent(TileEventType.TICK)
    public void tick_AutoCharge() {
        if (this.worldObj.isRemote) return;
        double maxPower = this.getAEMaxPower();
        if (this.internalCurrentPower < maxPower) {
            double chargeRate = BASE_AUTO_CHARGE_RATE * PowerMultiplier.CONFIG.multiplier;
            double toAdd = Math.min(chargeRate, maxPower - this.internalCurrentPower);
            this.internalCurrentPower += toAdd;
            this.changePowerLevel();
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileLimitedInfiniteEnergyCell(final NBTTagCompound data) {
        if (!this.worldObj.isRemote) {
            data.setDouble("internalCurrentPower", this.internalCurrentPower);
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileLimitedInfiniteEnergyCell(final NBTTagCompound data) {
        this.internalCurrentPower = data.getDouble("internalCurrentPower");
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.COVERED;
    }

    @Override
    public void onReady() {
        super.onReady();
        this.currentMeta = (byte) this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);
        this.changePowerLevel();
    }

    private void changePowerLevel() {
        if (this.notLoaded()) return;
        double maxPower = this.getAEMaxPower();
        byte boundMetadata = (byte) (8.0 * (this.internalCurrentPower / maxPower));
        if (boundMetadata > 7) boundMetadata = 7;
        if (boundMetadata < 0) boundMetadata = 0;
        if (this.currentMeta != boundMetadata) {
            this.currentMeta = boundMetadata;
            this.worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, this.currentMeta, 2);
        }
    }

    @Override
    public boolean canBeRotated() {
        return false;
    }

    @Override
    public double injectAEPower(double amt, final Actionable mode) {
        double maxPower = this.getAEMaxPower();
        if (mode == Actionable.SIMULATE) {
            double fakeBattery = this.internalCurrentPower + amt;
            if (fakeBattery > maxPower) {
                return fakeBattery - maxPower;
            }
            return 0;
        }

        if (this.internalCurrentPower < 0.01 && amt > 0.01) {
            this.getProxy()
                .getNode()
                .getGrid()
                .postEvent(new MENetworkPowerStorage(this, PowerEventType.PROVIDE_POWER));
        }

        this.internalCurrentPower += amt;
        if (this.internalCurrentPower > maxPower) {
            amt = this.internalCurrentPower - maxPower;
            this.internalCurrentPower = maxPower;
            this.changePowerLevel();
            return amt;
        }

        this.changePowerLevel();
        return 0;
    }

    @Override
    public double extractAEPower(final double amt, final Actionable mode, final PowerMultiplier pm) {
        return pm.divide(this.extractAEPower(pm.multiply(amt), mode));
    }

    private double extractAEPower(double amt, final Actionable mode) {
        double maxPower = this.getAEMaxPower();
        if (mode == Actionable.SIMULATE) {
            if (this.internalCurrentPower > amt) {
                return amt;
            }
            return this.internalCurrentPower;
        }

        boolean wasFull = this.internalCurrentPower >= maxPower - 0.001;

        if (wasFull && amt > 0.001) {
            try {
                this.getProxy()
                    .getGrid()
                    .postEvent(new MENetworkPowerStorage(this, PowerEventType.REQUEST_POWER));
            } catch (final GridAccessException ignored) {}
        }

        if (this.internalCurrentPower > amt) {
            this.internalCurrentPower -= amt;
            this.changePowerLevel();
            return amt;
        }

        amt = this.internalCurrentPower;
        this.internalCurrentPower = 0;
        this.changePowerLevel();
        return amt;
    }

    @Override
    public double getAEMaxPower() {
        return BASE_MAX_POWER * PowerMultiplier.CONFIG.multiplier;
    }

    @Override
    public double getAECurrentPower() {
        return this.internalCurrentPower;
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }
}
