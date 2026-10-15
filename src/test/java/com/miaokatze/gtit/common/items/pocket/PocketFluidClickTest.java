package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.Unpooled;

/** Real MUI click packets, Forge buckets, dirty-gated close and NBT reopen. No fluid skip probe. */
public final class PocketFluidClickTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Only the cursor transport is replaced: the library click/transfer implementation runs unchanged. */
    private static final class Cursor extends ModularSyncManager {

        private ItemStack held;

        private Cursor(ItemStack held) {
            super(false);
            this.held = held;
        }

        @Override
        public ItemStack getCursorItem() {
            return held;
        }

        @Override
        public void setCursorItem(ItemStack stack) {
            held = stack;
        }

        @Override
        public EntityPlayer getPlayer() {
            return null;
        }
    }

    private static void click(PocketInventory inventory, int tank, ItemStack container, boolean fill) {
        click(inventory.tankAt(tank), container, fill);
    }

    private static void click(IFluidTank tank, ItemStack container, boolean fill) {
        Cursor cursor = new Cursor(container);
        FluidSlotSyncHandler handler = new FluidSlotSyncHandler(tank);
        handler.init("test_tank", new PanelSyncManager(cursor, true));
        check(handler.getFluidTank() == tank, "click handler must use session tank");
        handler.updateCacheFromSource(true);
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new MouseData(Side.CLIENT, 0, false, false, false).writeToPacket(packet);
            handler.readOnServer(FluidSlotSyncHandler.SYNC_CLICK, packet);
        } finally {
            packet.release();
        }
        check(
            cursor.held != null && cursor.held.getItem() == (fill ? Items.bucket : Items.water_bucket),
            "real click must transfer water and replace cursor container");
    }

    private static void roundtrip(long before, boolean fill) {
        NBTTagCompound carrier = new NBTTagCompound();
        PocketInventory seeded = PocketInventory.readFrom(carrier);
        if (before > 0) seeded.fillOwnTankL(0, FluidRegistry.WATER, before);
        seeded.writeTo(carrier);
        PocketInventory session = PocketInventory.readFrom(carrier);
        check(!session.isDirty(), "reopened session starts clean");
        click(session, 0, new ItemStack(fill ? Items.water_bucket : Items.bucket), fill);
        long expected = before + (fill ? 1000 : -1000);
        check(session.tankTruthAt(0) == expected, "click must update authoritative amount");
        check(session.isDirty(), "successful click must mark session dirty");
        // Same gate as NekoPocketPanel.writeSessionToCarrier: no other inventory writes can help this click.
        if (session.isDirty()) {
            session.writeTo(carrier);
            session.markClean();
        }
        PocketInventory reopened = PocketInventory.readFrom(carrier);
        check(reopened.tankTruthAt(0) == expected, "closing and reopening must retain clicked amount");
        check(
            expected == 0 ? reopened.tankAt(0)
                .getFluid() == null
                : reopened.tankAt(0)
                    .getFluidAmount() == expected,
            "persisted head and truth must agree");
        System.out.println("PASS click " + (fill ? "fill" : "drain") + " " + before + " -> " + expected);
    }

    /** v1.8.39's exact getter/setter tank wiring, with native MUI implementations rather than mock fill/drain. */
    private static void oldSetterOnlyRoundtrip(int before, boolean fill) {
        NBTTagCompound carrier = new NBTTagCompound();
        if (before > 0) new FluidStack(FluidRegistry.WATER, before).writeToNBT(carrier);
        FluidStack[] head = { FluidStack.loadFluidStackFromNBT(carrier) };
        boolean[] dirty = { false };
        FluidStackTank tank = new FluidStackTank(() -> head[0], fluid -> {
            head[0] = fluid == null || fluid.amount <= 0 ? null : fluid;
            dirty[0] = true;
        }, 16_000_000);
        click(tank, new ItemStack(fill ? Items.water_bucket : Items.bucket), fill);
        int expected = before + (fill ? 1000 : -1000);
        check(tank.getFluidAmount() == expected, "old path really transfers fluid before close");
        boolean crossesEmpty = before == 0 || expected == 0;
        check(dirty[0] == crossesEmpty, "old setter fires only when crossing empty boundary");
        if (dirty[0]) {
            carrier = new NBTTagCompound();
            if (head[0] != null) head[0].writeToNBT(carrier);
        }
        FluidStack reopened = FluidStack.loadFluidStackFromNBT(carrier);
        int persisted = reopened == null ? 0 : reopened.amount;
        check(persisted == (crossesEmpty ? expected : before), "old partial click is lost at dirty-gated close");
        if (!crossesEmpty) {
            // Another inventory mutation raises the shared dirty flag, exposing the already changed live head.
            dirty[0] = true;
            head[0].writeToNBT(carrier);
            check(
                FluidStack.loadFluidStackFromNBT(carrier).amount == expected,
                "old partial click is saved once another item/essence mutation marks dirty");
        }
        System.out.println(
            "PASS v1.8.39 reproduction " + (fill ? "fill" : "drain")
                + " "
                + before
                + " -> "
                + expected
                + ", close reopens "
                + persisted);
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(PocketFluidClickTest.class, args)) return;
        // The headless Forge bootstrap has no discovery pass; no optional NEI mod is loaded in this suite.
        java.lang.reflect.Field names = cpw.mods.fml.common.Loader.class.getDeclaredField("namedMods");
        names.setAccessible(true);
        names.set(cpw.mods.fml.common.Loader.instance(), new java.util.HashMap<>());
        System.out.println(
            "MUI handler: " + FluidSlotSyncHandler.class.getProtectionDomain()
                .getCodeSource()
                .getLocation());
        oldSetterOnlyRoundtrip(0, true);
        oldSetterOnlyRoundtrip(1000, true);
        oldSetterOnlyRoundtrip(2000, false);
        oldSetterOnlyRoundtrip(1000, false);
        roundtrip(0, true);
        roundtrip(1000, true);
        roundtrip(2000, false);
        roundtrip(1000, false);
    }
}
