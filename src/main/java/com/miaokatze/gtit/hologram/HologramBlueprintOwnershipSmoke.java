package com.miaokatze.gtit.hologram;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.core.worlddata.WorldData;
import appeng.tile.misc.TileInterface;

/** Real FML/AE GridNode ownership; no external AE network or multipart support is claimed. */
public final class HologramBlueprintOwnershipSmoke {

    private static final int X = 544, Y = 88, Z = 544;
    private static int assertions;

    private HologramBlueprintOwnershipSmoke() {}

    public static void runOwnership(WorldServer world) throws Exception {
        assertions = 0;
        require(WorldData.instance() != null, "AE_world_player_registry_initialized");
        world.getChunkFromChunkCoords(X >> 4, Z >> 4);
        for (int x : new int[] { X, X + 4, X + 8 })
            require(world.getBlock(x, Y, Z) == Blocks.air, "isolated_fixture_space_" + x);
        EntityPlayerMP actor = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("258fb8a5-7d0e-48d8-a8ac-8ea463397e91"), "BlueprintAEActor"));
        EntityPlayerMP other = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("bfa0dc5a-4fc0-4dd2-8cdb-69ac066fb18f"), "BlueprintAEOther"));
        int actorId = WorldData.instance()
            .playerData()
            .getPlayerID(actor.getGameProfile());
        int otherId = WorldData.instance()
            .playerData()
            .getPlayerID(other.getGameProfile());
        require(actorId != otherId, "distinct_real_AE_numeric_player_ids");
        Block block = AEApi.instance()
            .definitions()
            .blocks()
            .iface()
            .maybeBlock()
            .get();
        try {
            TileInterface source = create(world, block, X);
            source.getProxy()
                .setOwner(other);
            source.onReady();
            require(
                source.getProxy()
                    .getNode() != null,
                "source_real_grid_node_created");
            require(
                source.getProxy()
                    .getNode()
                    .getPlayerID() == otherId,
                "source_node_owned_by_other_player");
            String root = HologramBlueprintOwnership.nodeKey(source.getProxy());
            NBTTagCompound captured = new NBTTagCompound();
            source.writeToNBT(captured);
            require(captured.hasKey(root, 10), "source_proxy_root_saved_by_native_AE");
            require(
                captured.getCompoundTag(root)
                    .getInteger("p") == otherId,
                "captured_numeric_owner_is_other");
            NBTTagCompound original = (NBTTagCompound) captured.copy();
            String encoded = HologramBlueprintNbt.encodeCaptured(captured);
            TileInterface target = create(world, block, X + 4);
            target.getProxy()
                .setOwner(actor);
            NBTTagCompound selected = HologramBlueprintNbt
                .forPlacement(encoded, actor.getUniqueID(), actor.getCommandSenderName(), X + 4, Y, Z, true, true);
            NBTTagCompound selectedBefore = (NBTTagCompound) selected.copy();
            NBTTagCompound prepared = HologramBlueprintOwnership
                .prepare(target, HologramBlueprintNbt.nativePayload(selected), actor);
            require(
                prepared.getCompoundTag(root)
                    .getInteger("p") == actorId,
                "prepared_proxy_numeric_owner_is_actor");
            require(
                prepared.getCompoundTag(root)
                    .getLong("k")
                    == selected.getCompoundTag(root)
                        .getLong("k"),
                "security_key_unchanged");
            require(
                prepared.getCompoundTag(root)
                    .getLong("g")
                    == selected.getCompoundTag(root)
                        .getLong("g"),
                "grid_storage_id_unchanged");
            require(selected.equals(selectedBefore), "prepare_does_not_mutate_selected_payload");
            target.readFromNBT(prepared);
            target.onReady();
            require(
                target.getProxy()
                    .getNode()
                    .getPlayerID() == actorId,
                "native_read_and_onReady_use_current_numeric_owner");
            require(
                source.getProxy()
                    .getNode()
                    .getPlayerID() == otherId,
                "source_live_numeric_owner_unchanged");
            require(captured.equals(original), "source_captured_NBT_unchanged");
            require(
                HologramBlueprintNbt.decode(encoded)
                    .getCompoundTag(root)
                    .getInteger("p") == otherId,
                "saved_blueprint_owner_unchanged");
            TileInterface cancelled = create(world, block, X + 8);
            cancelled.getProxy()
                .setOwner(other);
            NBTTagCompound withoutRoot = HologramBlueprintNbt
                .forPlacement(encoded, actor.getUniqueID(), actor.getCommandSenderName(), X + 8, Y, Z, true, true);
            withoutRoot.removeTag(root);
            NBTTagCompound cancelledPrepared = HologramBlueprintOwnership
                .prepare(cancelled, HologramBlueprintNbt.nativePayload(withoutRoot), actor);
            require(
                cancelledPrepared.getCompoundTag(root)
                    .getInteger("p") == actorId,
                "cancelled_proxy_root_still_receives_actor_owner");
            cancelled.readFromNBT(cancelledPrepared);
            cancelled.onReady();
            require(
                cancelled.getProxy()
                    .getNode()
                    .getPlayerID() == actorId,
                "cancelled_root_native_node_uses_actor");
            NBTTagCompound ordinary = new NBTTagCompound();
            ordinary.setInteger("p", 39);
            require(
                HologramBlueprintOwnership.prepare(new TileEntity(), ordinary, actor)
                    .getInteger("p") == 39,
                "ordinary_device_numeric_p_is_not_guessed_as_owner");
            System.out.println("[GTIT-HOLOGRAM-OWNERSHIP-SMOKE] FINAL PASS assertions=" + assertions);
        } finally {
            for (int x : new int[] { X, X + 4, X + 8 }) {
                TileEntity tile = world.getTileEntity(x, Y, Z);
                if (tile instanceof TileInterface) tile.invalidate();
                world.setBlockToAir(x, Y, Z);
            }
        }
    }

    private static TileInterface create(WorldServer world, Block block, int x) {
        require(world.setBlock(x, Y, Z, block, 0, 3), "interface_block_placed_" + x);
        TileEntity tile = world.getTileEntity(x, Y, Z);
        require(tile instanceof TileInterface, "real_interface_tile_created_" + x);
        return (TileInterface) tile;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
        System.out.println("[GTIT-HOLOGRAM-OWNERSHIP-SMOKE] PASS " + message);
    }
}
