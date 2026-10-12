package com.miaokatze.gtit.hologram;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;

/** Real registry/NBT regressions; independent entry point keeps the production persistence tests reusable. */
public final class HologramBlueprintPersistenceRegression {

    private HologramBlueprintPersistenceRegression() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(HologramBlueprintPersistenceRegression.class, args)) return;
        run();
        System.out.println("Hologram blueprint persistence regressions passed");
    }

    public static void run() {
        try {
            missingAndSchema();
            legacyReadOnlyUpgrade();
            metadataLabelsDoNotReadPayload();
            ownershipAndNbt();
            selectedNbt();
            stableItemIdentities();
            hostileNbt();
            sharingTransaction();
            versions();
            fluidProviderVersions();
        } catch (Exception failure) {
            throw new AssertionError("blueprint persistence regression", failure);
        }
    }

    private static void fluidProviderVersions() throws Exception {
        String name = "gtit_blueprint_provider_fixture";
        net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        if (fluid == null) {
            fluid = new net.minecraftforge.fluids.Fluid(name);
            SimpleAssert
                .that(net.minecraftforge.fluids.FluidRegistry.registerFluid(fluid), "fixture fluid registers once");
        }
        SimpleAssert.eq(
            "minecraft:" + name,
            net.minecraftforge.fluids.FluidRegistry.getDefaultFluidName(fluid),
            "fixture outside a mod container has Forge-recorded Minecraft provider");
        HologramPresets.Preset preset = blueprint();
        NBTTagCompound tank = new NBTTagCompound();
        tank.setString("FluidName", name);
        tank.setInteger("Amount", 1234);
        NBTTagList tanks = new NBTTagList();
        tanks.appendTag(tank);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Tanks", tanks);
        preset.cells.get(0).tileNbt = HologramBlueprintNbt.encodeCaptured(tile);
        HologramBlueprintMetadata.captureVersions(preset);
        SimpleAssert.eq(
            "1.7.10",
            preset.modVersions.get("minecraft"),
            "selected nested fluid records its actual Forge provider version");
        preset.modVersions.put("missing_fluid_provider", "2.8");
        tank.setString("FluidName", "missing_fluid_provider_fixture");
        preset.cells.get(0).tileNbt = HologramBlueprintNbt.encodeCaptured(tile);
        HologramBlueprintMetadata.captureVersions(preset);
        SimpleAssert.eq(
            "2.8",
            preset.modVersions.get("missing_fluid_provider"),
            "missing fluid does not erase previously captured provider version");
    }

    private static void metadataLabelsDoNotReadPayload() throws Exception {
        Field cache = HologramPresets.class.getDeclaredField("cached");
        cache.setAccessible(true);
        Object previous = cache.get(null);
        HologramPresets.Preset preset = blueprint();
        preset.cells = new java.util.AbstractList<HologramPresets.Cell>() {

            @Override
            public HologramPresets.Cell get(int index) {
                throw new AssertionError("label retrieval must never read or serialize a device payload");
            }

            @Override
            public int size() {
                return 65536;
            }
        };
        try {
            cache.set(null, java.util.Collections.singletonList(preset));
            NBTTagCompound label = HologramPresets.labels()
                .getCompoundTagAt(0);
            SimpleAssert.eq("fixture", label.getString("id"), "metadata labels retain identity without payload access");
            SimpleAssert.eq(
                65536,
                label.getInteger("cells"),
                "metadata labels retain large structure count without visiting cells");
            label.setString("name", "Caller mutation");
            SimpleAssert.eq(
                "Blueprint fixture",
                HologramPresets.labels()
                    .getCompoundTagAt(0)
                    .getString("name"),
                "label results cannot mutate server-owned metadata");
        } finally {
            cache.set(null, previous);
        }
    }

    private static void legacyReadOnlyUpgrade() throws Exception {
        HologramPresets.Preset old = new HologramPresets.Preset();
        old.id = "legacy_upgrade";
        old.name = "Legacy source";
        old.controllerType = "missing.legacy.Controller:7";
        old.controllerId = 7;
        old.controllerFacing = com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.EAST_NORMAL_NONE
            .ordinal();
        HologramPresets.Cell controller = new HologramPresets.Cell();
        controller.item = "missing_legacy_mod:controller";
        controller.damage = 7;
        controller.facing = com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.EAST_NORMAL_NONE
            .getDirection()
            .ordinal();
        controller.controller = true;
        old.cells.add(controller);
        old.palette.add(controller);
        HologramPresets.Cell stone = HologramPresets
            .cell(new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.stone), 1, 2, 3, -1, false);
        old.cells.add(stone);
        old.palette.add(stone);
        HologramPresets.Preset upgraded = HologramPresets.upgradeLegacy(old);
        SimpleAssert.eq(1, old.schema, "runtime upgrade never changes caller's legacy schema");
        SimpleAssert.that(old.cells.get(1).block == null, "runtime upgrade never changes caller's cell identity");
        SimpleAssert.eq(2, upgraded.schema, "legacy runtime enables schema 2 bulk editing");
        SimpleAssert.eq("minecraft:stone", upgraded.cells.get(1).block, "upgrade infers available block identity");
        SimpleAssert.that(
            upgraded.cells.get(0)
                .missing(),
            "legacy missing controller remains inspectable");
        SimpleAssert
            .eq(old.controllerFacing, upgraded.controllerFacing, "upgrade preserves global controller roll and flip");
        HologramPresets.Cell secondController = new HologramPresets.Cell();
        secondController.item = "missing_legacy_mod:controller";
        secondController.damage = 7;
        secondController.controller = true;
        secondController.x = 4;
        upgraded.cells.add(secondController);
        HologramPresets.validate(upgraded);
        expectIo(() -> HologramPresets.validate(old));

        Field fileField = HologramPresets.class.getDeclaredField("FILE");
        Field cacheField = HologramPresets.class.getDeclaredField("cached");
        fileField.setAccessible(true);
        cacheField.setAccessible(true);
        Path file = (Path) fileField.get(null);
        Object previousCache = cacheField.get(null);
        byte[] previousBytes = Files.exists(file) ? Files.readAllBytes(file) : null;
        String json = "{\"schema\":1,\"presets\":[" + new com.google.gson.Gson().toJson(old) + "]}";
        byte[] legacyBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.createDirectories(file.getParent());
        try {
            Files.write(file, legacyBytes);
            cacheField.set(null, null);
            List<HologramPresets.Preset> loaded = HologramPresets.all();
            SimpleAssert.eq(2, loaded.get(0).schema, "disk-loaded legacy preset exposes schema 2 editing contract");
            loaded.get(0).name = "Caller mutation";
            SimpleAssert
                .eq("Legacy source", HologramPresets.get(old.id).name, "loaded cache is detached from reader mutation");
            SimpleAssert.that(
                java.util.Arrays.equals(legacyBytes, Files.readAllBytes(file)),
                "read-only upgrade does not rewrite legacy configuration");
            HologramPresets.Preset edit = HologramPresets.get(old.id);
            edit.name = "Explicit update";
            HologramPresets.Structure first = new HologramPresets.Structure();
            first.id = "shared_a";
            HologramPresets.Structure second = new HologramPresets.Structure();
            second.id = "shared_b";
            second.enabled = false;
            edit.structures.add(first);
            edit.structures.add(second);
            edit.cells.get(1).structureId = first.id;
            edit.cells.get(1).structureIds = java.util.Arrays.asList(first.id, second.id);
            HologramPresets.update(edit);
            SimpleAssert.that(
                !java.util.Arrays.equals(legacyBytes, Files.readAllBytes(file)),
                "explicit update persists upgraded format");
            cacheField.set(null, null);
            HologramPresets.Preset sharedReloaded = HologramPresets.get(old.id);
            SimpleAssert.that(
                sharedReloaded.cells.get(1)
                    .belongsTo("shared_a")
                    && sharedReloaded.cells.get(1)
                        .belongsTo("shared_b"),
                "saved shared wall reloads both structure memberships");
            sharedReloaded.cells.get(1)
                .memberships()
                .clear();
            SimpleAssert.that(
                sharedReloaded.cells.get(1)
                    .belongsTo("shared_b"),
                "membership getter returns a detached list");
            Files.write(
                file,
                json.replace("\"x\":1", "\"x\":1.5")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            cacheField.set(null, null);
            expectIo(HologramPresets::all);
            HologramPresets.Preset invalidNbt = HologramPresets.copy(old);
            invalidNbt.cells.get(0).tileNbt = HologramBlueprintNbt.encodeCaptured(new NBTTagCompound());
            expectIo(() -> HologramPresets.upgradeLegacy(invalidNbt));
        } finally {
            if (previousBytes == null) Files.deleteIfExists(file);
            else Files.write(file, previousBytes);
            cacheField.set(null, previousCache);
        }
    }

    private static void stableItemIdentities() throws Exception {
        NBTTagCompound original = new net.minecraft.item.ItemStack(net.minecraft.init.Items.feather, 4, 2)
            .writeToNBT(new NBTTagCompound());
        NBTTagList inventory = new NBTTagList();
        inventory.appendTag(original);
        NBTTagList nestedList = new NBTTagList();
        nestedList.appendTag(inventory);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "ordinary_tile_identity");
        tile.setTag("Inventory", nestedList);
        String captured = HologramBlueprintNbt.encodeCaptured(tile);
        NBTTagCompound first = HologramBlueprintNbt.decode(captured);
        NBTTagCompound second = HologramBlueprintNbt.decode(HologramBlueprintNbt.encodeCaptured(first));
        SimpleAssert.that(first.equals(second), "repeated capture preserves stable item markers and material identity");
        SimpleAssert.that(
            HologramBlueprintNbt.nativePayload(first)
                .equals(tile),
            "native payload removes all nested transport markers");
        SimpleAssert.eq("ordinary_tile_identity", first.getString("id"), "ordinary tile id is untouched");

        NBTTagCompound remapped = (NBTTagCompound) original.copy();
        remapped.setShort("id", (short) net.minecraft.item.Item.getIdFromItem(net.minecraft.init.Items.diamond));
        remapped.setString(HologramBlueprintNbt.ITEM_REGISTRY, "minecraft:feather");
        String moved = Base64.getEncoder()
            .encodeToString(net.minecraft.nbt.CompressedStreamTools.compress(remapped));
        NBTTagCompound decoded = HologramBlueprintNbt.decode(moved);
        SimpleAssert.that(
            HologramBlueprintNbt.stableItem(decoded) == net.minecraft.init.Items.feather,
            "stable registry marker wins over a numeric id from another installation");
        SimpleAssert.eq(
            net.minecraft.item.Item.getIdFromItem(net.minecraft.init.Items.feather),
            (int) decoded.getShort("id"),
            "decode remaps numeric id to current registry");
        SimpleAssert.that(decoded.hasKey("id", 2), "remapped item id retains native short NBT type");

        remapped.setString(HologramBlueprintNbt.ITEM_REGISTRY, "missing_inventory_mod:retained_item");
        String missing = Base64.getEncoder()
            .encodeToString(net.minecraft.nbt.CompressedStreamTools.compress(remapped));
        NBTTagCompound unavailable = HologramBlueprintNbt.decode(missing);
        SimpleAssert.eq(
            "missing_inventory_mod:retained_item",
            unavailable.getString(HologramBlueprintNbt.ITEM_REGISTRY),
            "missing inventory item retains stable registry identity");
        SimpleAssert.eq(
            -1,
            (int) unavailable.getShort("id"),
            "missing item cannot accidentally restore a numeric-id collision");
        SimpleAssert.that(
            unavailable.getBoolean(HologramBlueprintNbt.MISSING_ITEM)
                && HologramBlueprintNbt.hasMissingItems(unavailable),
            "missing nested item remains explicitly unsupported");
        expectIo(() -> HologramBlueprintNbt.nativePayload(unavailable));
        NBTTagCompound unavailableAgain = HologramBlueprintNbt.decode(HologramBlueprintNbt.encodeCaptured(unavailable));
        SimpleAssert.eq(
            "missing_inventory_mod:retained_item",
            unavailableAgain.getString(HologramBlueprintNbt.ITEM_REGISTRY),
            "missing item remains retained on subsequent saves");
        NBTTagCompound cover = new NBTTagCompound();
        cover.setByte("s", (byte) 0);
        cover.setInteger("id", net.minecraft.item.Item.getIdFromItem(net.minecraft.init.Items.diamond) | 2 << 16);
        cover.setString(HologramBlueprintNbt.COVER_REGISTRY, "minecraft:feather");
        cover.setInteger(HologramBlueprintNbt.COVER_DAMAGE, 7);
        NBTTagList coverList = new NBTTagList();
        coverList.appendTag(cover);
        NBTTagCompound coverTile = new NBTTagCompound();
        coverTile.setTag("gt.covers", coverList);
        String coverBytes = Base64.getEncoder()
            .encodeToString(net.minecraft.nbt.CompressedStreamTools.compress(coverTile));
        NBTTagCompound coverDecoded = HologramBlueprintNbt.decode(coverBytes);
        SimpleAssert.eq(
            net.minecraft.item.Item.getIdFromItem(net.minecraft.init.Items.feather) | 7 << 16,
            coverDecoded.getTagList("gt.covers", 10)
                .getCompoundTagAt(0)
                .getInteger("id"),
            "GT cover packed id remaps by stable item registry and damage");
    }

    private static HologramPresets.Preset blueprint() {
        HologramPresets.Preset preset = new HologramPresets.Preset();
        preset.id = "fixture";
        preset.name = "Blueprint fixture";
        preset.schema = 2;
        HologramPresets.Cell cell = new HologramPresets.Cell();
        cell.item = "missing_mod:missing_item";
        cell.block = "missing_mod:missing_block";
        cell.metadata = 7;
        cell.damage = 8;
        cell.prompt = "Replace missing block";
        preset.cells.add(cell);
        return preset;
    }

    private static void missingAndSchema() throws Exception {
        HologramPresets.Preset preset = blueprint();
        HologramPresets.validate(preset);
        SimpleAssert.that(
            preset.cells.get(0)
                .stack() == null,
            "missing registry item remains unresolved");
        SimpleAssert.that(
            preset.cells.get(0)
                .missing(),
            "missing block is previewable as missing");
        preset.cells.get(0).controller = true;
        HologramPresets.Cell second = new HologramPresets.Cell();
        second.item = "another_mod:controller";
        second.x = 3;
        second.controller = true;
        preset.cells.add(second);
        HologramPresets.validate(preset);
        SimpleAssert.eq(2, preset.cells.size(), "multiple controller cells are accepted");
        preset.schema = 3;
        expectIo(() -> HologramPresets.validate(preset));
        preset.schema = 1;
        expectIo(() -> HologramPresets.validate(preset));

        Method decode = HologramPresets.class.getDeclaredMethod("decode", String.class);
        decode.setAccessible(true);
        Object file = decode.invoke(
            null,
            "{\"schema\":1,\"presets\":[{\"id\":\"legacy\",\"name\":\"Legacy\",\"cells\":[],\"palette\":[]}]}");
        Field entries = file.getClass()
            .getDeclaredField("presets");
        entries.setAccessible(true);
        HologramPresets.Preset legacy = ((List<HologramPresets.Preset>) entries.get(file)).get(0);
        SimpleAssert.eq(1, legacy.schema, "old schema 1 entries retain legacy semantics");
        SimpleAssert.that(legacy.retainNbt && legacy.retainCovers, "missing optional settings have safe defaults");
        SimpleAssert.that(legacy.retainedNbtKeys == null, "missing legacy selector retains full baseline");
    }

    private static void selectedNbt() throws Exception {
        NBTTagCompound source = new NBTTagCompound();
        source.setString("id", "fixture_tile");
        source.setInteger("mID", 23);
        source.setInteger("x", 900);
        source.setString("ownerID", "original-owner");
        NBTTagCompound inventory = new NBTTagCompound();
        inventory.setInteger("Count", 12);
        source.setTag("Inventory", inventory);
        NBTTagCompound fluid = new NBTTagCompound();
        fluid.setInteger("Amount", 48000);
        source.setTag("mFluid", fluid);
        NBTTagList covers = new NBTTagList();
        covers.appendTag(new NBTTagCompound());
        source.setTag("gt.covers", covers);
        String all = HologramBlueprintNbt.encodeCaptured(source);
        List<String> keys = java.util.Collections.singletonList("mFluid");
        String filtered = HologramBlueprintNbt.filterCaptured(all, keys, true);
        NBTTagCompound stored = HologramBlueprintNbt.decode(filtered);
        SimpleAssert.that(!stored.hasKey("Inventory"), "unselected resource entry is really absent from saved NBT");
        SimpleAssert.eq(
            48000,
            stored.getCompoundTag("mFluid")
                .getInteger("Amount"),
            "selected resource entry survives");
        SimpleAssert.eq("fixture_tile", stored.getString("id"), "selector protects tile identity");
        SimpleAssert.eq(23, stored.getInteger("mID"), "selector protects GT machine identity");
        SimpleAssert.eq(900, stored.getInteger("x"), "selector protects relocation metadata");
        SimpleAssert.that(
            stored.hasKey("ownerID") && stored.hasKey("gt.covers"),
            "selector protects owner field and independent covers");
        SimpleAssert.that(
            HologramBlueprintNbt.decode(HologramBlueprintNbt.filterCaptured(all, null, true))
                .hasKey("Inventory"),
            "null selector keeps complete legacy baseline");
        UUID user = UUID.fromString("11111111-2222-3333-4444-555555555555");
        NBTTagCompound placed = HologramBlueprintNbt.forPlacement(all, user, "New player", 1, 2, 3, true, false, keys);
        SimpleAssert.that(
            !placed.hasKey("Inventory") && !placed.hasKey("gt.covers"),
            "placement selector and cover switch both apply");
        SimpleAssert
            .eq(user.toString(), placed.getString("ownerID"), "owner rewrite remains mandatory after selection");
        SimpleAssert.eq(1, placed.getInteger("x"), "placement rewrites protected coordinates");
        NBTTagCompound cancelled = HologramBlueprintNbt
            .forPlacement(all, user, "New player", 1, 2, 3, true, true, java.util.Collections.emptyList());
        SimpleAssert.that(
            !cancelled.hasKey("mFluid") && cancelled.hasKey("gt.covers"),
            "empty selector explicitly cancels ordinary resources");
    }

    private static void ownershipAndNbt() throws Exception {
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "fixture_tile");
        tile.setString("mOwnerUuid", "original-player-uuid");
        tile.setString("mOwnerName", "Original player");
        tile.setInteger("x", 900);
        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("ownerID", "original-player-uuid");
        nested.setString("UUID", "device-identity");
        NBTTagCompound owner = new NBTTagCompound();
        owner.setString("UUID", "original-player-uuid");
        owner.setString("Name", "Original player");
        owner.setLong("UUIDMost", 100);
        owner.setLong("UUIDLeast", 200);
        nested.setTag("owner", owner);
        nested.setInteger("Count", 512);
        NBTTagList inventory = new NBTTagList();
        inventory.appendTag(nested);
        tile.setTag("Inventory", inventory);
        NBTTagCompound fluid = new NBTTagCompound();
        fluid.setInteger("Amount", 64000);
        tile.setTag("mFluid", fluid);
        NBTTagList covers = new NBTTagList();
        NBTTagCompound cover = new NBTTagCompound();
        cover.setInteger("coverData", 321);
        covers.appendTag(cover);
        tile.setTag("gt.covers", covers);
        String encoded = HologramBlueprintNbt.encodeCaptured(tile);
        NBTTagCompound saved = HologramBlueprintNbt.decode(encoded);
        SimpleAssert.eq("", saved.getString("mOwnerUuid"), "stored blueprint does not retain original UUID");
        SimpleAssert.eq("", saved.getString("mOwnerName"), "stored blueprint does not retain original owner name");
        SimpleAssert.eq("original-player-uuid", tile.getString("mOwnerUuid"), "capture never mutates live owner");
        UUID user = UUID.fromString("11111111-2222-3333-4444-555555555555");
        NBTTagCompound restored = HologramBlueprintNbt.forPlacement(encoded, user, "New player", 2, 65, -4, true, true);
        SimpleAssert.eq(user.toString(), restored.getString("mOwnerUuid"), "GT owner rewritten to placing user");
        SimpleAssert.eq("New player", restored.getString("mOwnerName"), "GT owner name rewritten");
        NBTTagCompound item = restored.getTagList("Inventory", 10)
            .getCompoundTagAt(0);
        SimpleAssert.eq(user.toString(), item.getString("ownerID"), "nested list ownerID rewritten");
        SimpleAssert.eq("device-identity", item.getString("UUID"), "unrelated device UUID preserved");
        SimpleAssert.eq(
            user.toString(),
            item.getCompoundTag("owner")
                .getString("UUID"),
            "owner compound UUID rewritten");
        SimpleAssert.eq(
            "New player",
            item.getCompoundTag("owner")
                .getString("Name"),
            "owner compound name rewritten");
        SimpleAssert.that(
            item.getCompoundTag("owner")
                .getLong("UUIDMost") == user.getMostSignificantBits(),
            "owner UUID most bits rewritten");
        SimpleAssert.that(
            item.getCompoundTag("owner")
                .getLong("UUIDLeast") == user.getLeastSignificantBits(),
            "owner UUID least bits rewritten");
        SimpleAssert.eq(512, item.getInteger("Count"), "full item count survives");
        SimpleAssert.eq(
            64000,
            restored.getCompoundTag("mFluid")
                .getInteger("Amount"),
            "fluid data survives");
        SimpleAssert.eq(2, restored.getInteger("x"), "placement relocates tile");
        SimpleAssert.eq(
            321,
            restored.getTagList("gt.covers", 10)
                .getCompoundTagAt(0)
                .getInteger("coverData"),
            "covers retained by default");
        SimpleAssert.that(
            !HologramBlueprintNbt.forPlacement(encoded, user, "New player", 0, 0, 0, true, false)
                .hasKey("gt.covers"),
            "cover toggle strips actual GT cover key");
        NBTTagCompound coverOnly = HologramBlueprintNbt.forPlacement(encoded, user, "New player", 0, 0, 0, false, true);
        SimpleAssert.that(
            coverOnly.hasKey("gt.covers") && !coverOnly.hasKey("Inventory") && !coverOnly.hasKey("mFluid"),
            "covers toggle remains independent when ordinary NBT is disabled");
        SimpleAssert.that(
            HologramBlueprintNbt.forPlacement(encoded, user, "New player", 0, 0, 0, false, false) == null,
            "disabling both toggles yields no restored payload");
        SimpleAssert.eq(
            "",
            saved.getTagList("Inventory", 10)
                .getCompoundTagAt(0)
                .getString("ownerID"),
            "placement never changes saved owner placeholder");
    }

    private static void hostileNbt() throws Exception {
        expectIo(() -> HologramBlueprintNbt.decode("!!!not-base64!!!"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(11);
            out.writeUTF("overflow");
            out.writeInt(Integer.MAX_VALUE);
            out.writeByte(0);
        }
        expectIo(
            () -> HologramBlueprintNbt.decode(
                Base64.getEncoder()
                    .encodeToString(bytes.toByteArray())));
        NBTTagCompound oversized = new NBTTagCompound();
        oversized.setByteArray("bomb", new byte[HologramBlueprintNbt.MAX_EXPANDED + 1]);
        expectIo(() -> HologramBlueprintNbt.encodeCaptured(oversized));
        byte[] bomb = net.minecraft.nbt.CompressedStreamTools.compress(oversized);
        expectIo(
            () -> HologramBlueprintNbt.decode(
                Base64.getEncoder()
                    .encodeToString(bomb)));
        expectIo(() -> HologramBlueprintSharing.resolve("../outside"));
        expectIo(() -> HologramBlueprintSharing.decode("{\"schema\":\"2\",\"blueprint\":{}}"));
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 40; i++) deep.append('[');
        expectIo(() -> HologramBlueprintSharing.boundedJson(deep.toString()));
    }

    private static void sharingTransaction() throws Exception {
        Path directory = Files.createTempDirectory("gtit-blueprint-share-");
        Path file = directory.resolve("blueprint.json");
        Path blocked = directory.resolve("blocked");
        try {
            HologramPresets.Preset preset = blueprint();
            preset.prompt = "Global prompt";
            preset.cells.get(0).excluded = true;
            NBTTagCompound external = new NBTTagCompound();
            external.setString("ownerID", "external-original-owner");
            external.setInteger("Amount", 96000);
            external.setString("unselected", "must-not-be-stored");
            // An external share may contain the original owner; import/export validation must sanitize it.
            preset.cells.get(0).tileNbt = Base64.getEncoder()
                .encodeToString(net.minecraft.nbt.CompressedStreamTools.compress(external));
            preset.retainedNbtKeys = java.util.Collections.singletonList("Amount");
            preset.modVersions.put("missing_mod", "1.10.3");
            HologramPresets.Structure structure = new HologramPresets.Structure();
            structure.id = "structure_a";
            structure.name = "First outline";
            structure.sourceMod = "missing_source_mod";
            structure.sourceClass = "missing_source_mod.FirstController";
            structure.x = 2;
            preset.structures.add(structure);
            HologramPresets.Structure disabled = new HologramPresets.Structure();
            disabled.id = "structure_b";
            disabled.name = "Second outline";
            disabled.enabled = false;
            disabled.x = -3;
            preset.structures.add(disabled);
            preset.cells.get(0).structureId = structure.id;
            preset.cells.get(0).structureIds = java.util.Arrays.asList(structure.id, disabled.id);
            preset.modVersions.put("missing_source_mod", "2.7");
            HologramPresets.HintArea area = new HologramPresets.HintArea();
            area.x2 = 10;
            area.prompt = "Wire this area";
            preset.hintAreas.add(area);
            HologramBlueprintSharing.write(file, preset);
            byte[] original = Files.readAllBytes(file);
            HologramPresets.Preset loaded = HologramBlueprintSharing.read(file);
            SimpleAssert.eq(
                "missing_mod:missing_block",
                loaded.cells.get(0).block,
                "share retains missing block registry identity");
            SimpleAssert.eq(7, loaded.cells.get(0).metadata, "share retains block metadata");
            SimpleAssert.that(loaded.cells.get(0).excluded, "excluded cell survives share");
            SimpleAssert.eq("Global prompt", loaded.prompt, "global prompt survives share");
            SimpleAssert.eq("Wire this area", loaded.hintAreas.get(0).prompt, "hint area survives share");
            SimpleAssert.eq("1.10.3", loaded.modVersions.get("missing_mod"), "dependency version survives share");
            NBTTagCompound payload = HologramBlueprintNbt.decode(loaded.cells.get(0).tileNbt);
            SimpleAssert.eq("", payload.getString("ownerID"), "shared external owner is sanitized before writing");
            SimpleAssert.eq(96000, payload.getInteger("Amount"), "full NBT survives JSON share round trip");
            SimpleAssert.eq("Amount", loaded.retainedNbtKeys.get(0), "NBT root selector survives sharing");
            SimpleAssert.that(!payload.hasKey("unselected"), "share file contains no unselected root payload");
            SimpleAssert.eq(2, loaded.structures.size(), "all independent structure outlines survive sharing");
            SimpleAssert
                .eq("structure_a", loaded.cells.get(0).structureId, "cell retains captured structure membership");
            SimpleAssert.that(
                loaded.cells.get(0)
                    .belongsTo("structure_a")
                    && loaded.cells.get(0)
                        .belongsTo("structure_b"),
                "share retains all shared-wall memberships without duplicating the cell");
            SimpleAssert
                .eq("missing_source_mod", loaded.structures.get(0).sourceMod, "missing source mod remains provenance");
            SimpleAssert.that(!loaded.structures.get(1).enabled, "independent disabled structure survives sharing");
            SimpleAssert.eq(-3, loaded.structures.get(1).x, "independent structure anchor survives sharing");
            HologramPresets.Preset invalid = HologramPresets.copy(loaded);
            invalid.cells.get(0).structureId = "unknown_structure";
            expectIo(() -> HologramPresets.validate(invalid));
            HologramPresets.Preset duplicateMembership = HologramPresets.copy(loaded);
            duplicateMembership.cells.get(0).structureIds = java.util.Arrays.asList("structure_a", "structure_a");
            expectIo(() -> HologramPresets.validate(duplicateMembership));
            HologramPresets.Cell legacyMembership = new HologramPresets.Cell();
            legacyMembership.structureId = "structure_a";
            SimpleAssert.that(
                legacyMembership.belongsTo("structure_a"),
                "absent multi-membership falls back to legacy primary");
            legacyMembership.structureIds = java.util.Collections.emptyList();
            SimpleAssert
                .that(legacyMembership.belongsTo("structure_a"), "empty multi-membership falls back to legacy primary");
            Files.createDirectory(blocked);
            Files.write(blocked.resolve("keep"), new byte[] { 1 });
            expectIo(() -> HologramBlueprintSharing.write(blocked, preset));
            SimpleAssert.that(
                java.util.Arrays.equals(original, Files.readAllBytes(file)),
                "failed atomic export preserves prior file");
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                SimpleAssert.eq(2, (int) files.count(), "failed atomic write removes temporary file");
            }
            expectIo(
                () -> HologramBlueprintSharing.atomicWrite(file, new byte[HologramBlueprintSharing.MAX_BYTES + 1]));
            SimpleAssert.that(
                java.util.Arrays.equals(original, Files.readAllBytes(file)),
                "oversize export preserves prior file");
        } finally {
            Files.deleteIfExists(blocked.resolve("keep"));
            Files.deleteIfExists(blocked);
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void versions() {
        SimpleAssert.that(
            HologramBlueprintMetadata.compareVersions("1.10.0", "1.9.99") > 0,
            "versions compare numeric segments");
        SimpleAssert
            .eq(0, HologramBlueprintMetadata.compareVersions("1.10", "1.10.0"), "trailing zero version is equivalent");
        HologramPresets.Preset preset = blueprint();
        preset.modVersions.put("structure_source", "1.10");
        preset.modVersions.put("missing_mod", "2.0");
        Map<String, String> installed = new LinkedHashMap<>();
        installed.put("structure_source", "1.9");
        SimpleAssert.eq(
            2,
            HologramBlueprintMetadata.warnings(preset, installed)
                .size(),
            "missing and lower source versions both warn");
        HologramPresets.Structure structure = new HologramPresets.Structure();
        structure.id = "source_only";
        structure.sourceMod = "vanilla_material_structure_source";
        preset.structures.add(structure);
        HologramBlueprintMetadata.captureVersions(preset);
        SimpleAssert.eq(
            "unknown",
            preset.modVersions.get("vanilla_material_structure_source"),
            "structure source remains a dependency even without corresponding material registry entries");
        NBTTagCompound missingItem = new NBTTagCompound();
        missingItem.setShort("id", (short) 1);
        missingItem.setByte("Count", (byte) 1);
        missingItem.setShort("Damage", (short) 0);
        missingItem.setString(HologramBlueprintNbt.ITEM_REGISTRY, "missing_inventory_dependency:stored_item");
        NBTTagList contents = new NBTTagList();
        contents.appendTag(missingItem);
        NBTTagCompound missingCover = new NBTTagCompound();
        missingCover.setByte("s", (byte) 0);
        missingCover.setInteger("id", 0);
        missingCover.setString(HologramBlueprintNbt.COVER_REGISTRY, "missing_cover_dependency:stored_cover");
        missingCover.setInteger(HologramBlueprintNbt.COVER_DAMAGE, 0);
        NBTTagList covers = new NBTTagList();
        covers.appendTag(missingCover);
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Inventory", contents);
        tile.setTag("gt.covers", covers);
        try {
            preset.cells.get(0).tileNbt = HologramBlueprintNbt.encodeCaptured(tile);
        } catch (IOException invalid) {
            throw new AssertionError(invalid);
        }
        HologramBlueprintMetadata.captureVersions(preset);
        SimpleAssert.eq(
            "unknown",
            preset.modVersions.get("missing_inventory_dependency"),
            "stable inventory marker contributes its mod dependency without numeric guessing");
        SimpleAssert.eq(
            "unknown",
            preset.modVersions.get("missing_cover_dependency"),
            "stable cover marker contributes its mod dependency even while registration is missing");
    }

    private interface IoAction {

        void run() throws Exception;
    }

    private static void expectIo(IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected bounded input or IO rejection");
        } catch (IOException expected) {
            // Required rejection.
        }
    }
}
