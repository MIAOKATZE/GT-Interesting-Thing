// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.pocket;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;

import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.mojang.authlib.GameProfile;

import appeng.api.config.AccessRestriction;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;

/** Fault-injects synchronous target callbacks through the real AE operation and a concrete PocketRuntimeSession. */
public final class PocketInjectionFailureTest {

    /** Omit AE's GUI-only encoded-pattern icon initializer; native stack transfer methods are unchanged. */
    public static final class HeadlessStackBootstrap implements net.minecraft.launchwrapper.IClassTransformer {

        @Override
        public byte[] transform(String name, String mapped, byte[] bytes) {
            if (!"appeng.util.item.AEStack".equals(name)) return bytes;
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
            for (org.objectweb.asm.tree.MethodNode method : node.methods) if (method.name.equals("<clinit>")) {
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ACONST_NULL));
                method.instructions.add(
                    new org.objectweb.asm.tree.FieldInsnNode(
                        org.objectweb.asm.Opcodes.PUTSTATIC,
                        "appeng/util/item/AEStack",
                        "PATTERN",
                        "Lnet/minecraft/item/ItemStack;"));
                method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
                method.localVariables = null;
            }
            org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(
                org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        }
    }

    private static final class Player extends EntityPlayerMP {

        GameProfile profile;

        private Player() {
            super(null, null, null, null);
        }

        @Override
        public GameProfile getGameProfile() {
            return profile;
        }

        @Override
        public UUID getUniqueID() {
            return profile.getId();
        }
    }

    private static final class Fixture {

        final Player player;
        final ItemStack carrier = new ItemStack(Items.stick);
        final PocketRuntimeSession session;

        Fixture() throws Exception {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            player = (Player) ((sun.misc.Unsafe) field.get(null)).allocateInstance(Player.class);
            player.profile = new GameProfile(UUID.randomUUID(), "inject-failure");
            player.inventory = new InventoryPlayer(player);
            player.inventory.mainInventory[0] = carrier;
            session = new PocketRuntimeSession(
                () -> player,
                () -> player.inventory.mainInventory[0],
                0,
                carrier,
                false);
        }

        PocketAeChannelOps ops(String type, long[] accepted, Runnable callback) throws Exception {
            PocketAeChannelOps ops = new PocketAeChannelOps(player, carrier, session);
            Object handler = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { IMEInventoryHandler.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("getAccess")) return AccessRestriction.READ_WRITE;
                    if (method.getName()
                        .equals("canAccept")) return true;
                    if (method.getName()
                        .equals("injectItems")) {
                        accepted[0] += ((IAEStack<?>) args[0]).getStackSize();
                        callback.run();
                        return null;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
            set(ops, "cachedTick", 0L);
            set(ops, "cachedFoundId", "fixture");
            set(ops, "cachedFound", new PocketAeChannelOps.Found(carrier, null));
            set(ops, "cachedHandlerKey", "fixture#" + type);
            set(ops, "cachedHandler", handler);
            return ops;
        }
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap
            .enter(PocketInjectionFailureTest.class, args, HeadlessStackBootstrap.class.getName())) return;
        Field mods = cpw.mods.fml.common.Loader.class.getDeclaredField("namedMods");
        mods.setAccessible(true);
        mods.set(cpw.mods.fml.common.Loader.instance(), new java.util.HashMap<>());
        AEStackTypeRegistry.register(InfinityStackTypes.ITEM_STACK_TYPE);
        AEStackTypeRegistry.register(InfinityStackTypes.FLUID_STACK_TYPE);
        Fixture item = new Fixture();
        item.session.setStorageStackAt(0, new ItemStack(Items.iron_ingot, 64));
        long[] acceptedItem = { 0 };
        PocketAeChannelOps itemOps = item.ops(InfinityStackTypes.ITEM_STACK_TYPE.getId(), acceptedItem, () -> {
            ItemStack source = item.session.storageStackAt(0);
            if (source != null) {
                item.session.setStorageStackAt(1, source);
                item.session.setStorageStackAt(0, null);
            }
        });
        // Use the real snapshot's content key, including registry/NBT encoding.
        PocketChannelOps.SourceSlot itemSource = itemOps.snapshotSources()
            .get(0);
        // Snapshot deliberately clears per-batch caches; reinstall the exact test target after obtaining it.
        itemOps = item.ops(InfinityStackTypes.ITEM_STACK_TYPE.getId(), acceptedItem, () -> {
            ItemStack source = item.session.storageStackAt(0);
            if (source != null) {
                item.session.setStorageStackAt(1, source);
                item.session.setStorageStackAt(0, null);
            }
        });
        itemOps.inject(itemSource, "fixture", InfinityStackTypes.ITEM_STACK_TYPE.getId());
        long remainingItem = item.session.storageStackAt(1) == null ? 0 : item.session.storageStackAt(1).stackSize;
        Fixture fluid = new Fixture();
        fluid.session.depositFluidL(0, FluidRegistry.WATER, 1000);
        long[] acceptedFluid = { 0 }, callbackFluid = { 0 };
        PocketAeChannelOps fluidOps = fluid.ops(
            InfinityStackTypes.FLUID_STACK_TYPE.getId(),
            acceptedFluid,
            () -> callbackFluid[0] += fluid.session.drainOwnTankL(0, 1000));
        fluidOps.inject(
            new PocketChannelOps.SourceSlot(
                0,
                PocketFilterConfig.fluidKey("water"),
                1000,
                PocketChannelOps.SourceKind.FLUID),
            "fixture",
            InfinityStackTypes.FLUID_STACK_TYPE.getId());
        long totalItem = acceptedItem[0] + remainingItem;
        long totalFluid = acceptedFluid[0] + callbackFluid[0]
            + fluid.session.inventory()
                .tankTruthAt(0);
        System.out.println(
            "INJECT_CALLBACK itemInitial=64 itemTarget=" + acceptedItem[0]
                + " itemSource="
                + remainingItem
                + " itemTotal="
                + totalItem
                + " fluidInitial=1000 fluidTarget="
                + acceptedFluid[0]
                + " callbackDrain="
                + callbackFluid[0]
                + " fluidTotal="
                + totalFluid);
        if (args.length > 0 && args[0].equals("reproduce")) {
            check(totalItem > 64 && totalFluid > 1000, "baseline unsafe callback must be reproduced");
        } else {
            check(totalItem == 64 && totalFluid == 1000, "callback must not create resources");
            com.miaokatze.gtit.testlab.scenario.PocketInjectionChecks
                .run(new Fixture().player, () -> new ItemStack(Items.stick), (name, pass) -> {
                    check(pass, name);
                    System.out.println("INJECTION_CONTRACT PASS " + name);
                });
        }
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = PocketAeChannelOps.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
