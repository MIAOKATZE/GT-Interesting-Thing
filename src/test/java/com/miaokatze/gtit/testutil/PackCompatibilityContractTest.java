// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.testutil;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Reads isolated dev artifacts without loading old upstream classes into the modern test runtime. */
public final class PackCompatibilityContractTest {

    private static final List<File> ARTIFACTS = new ArrayList<>();
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("pack version and isolated artifact paths required");
        for (int i = 1; i < args.length; i++) {
            File file = new File(args[i]);
            ARTIFACTS.add(file);
            System.out.println(args[0] + " artifact " + file.getName() + " SHA-256=" + digest(file));
        }
        String base = "gregtech/api/metatileentity/BaseMetaTileEntity";
        method(base, "setInitialValuesAsNBT", "(Lnet/minecraft/nbt/NBTTagCompound;S)V", false);
        field(base, "mMuffler", "Z");
        method("gregtech/common/tileentities/boilers/MTEBoiler", "doSound", "(BDDD)V", false);
        method("gregtech/common/tileentities/boilers/MTEBoiler", "updateSoundLoops", "(Z)V", false);
        method("gregtech/api/metatileentity/implementations/MTEFluidPipe", "doSound", "(BDDD)V", false);
        method(
            "gregtech/common/tileentities/machines/multi/compressor/MTEBlackHoleCompressor",
            "playBlackHoleSounds",
            "()V",
            false);
        method(
            "gregtech/common/tileentities/machines/multi/MTEBrickedBlastFurnace",
            "updateSound",
            "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;)V",
            false);
        method(
            "com/gtnewhorizon/gtnhlib/teams/TeamManager",
            "mergeTeams",
            "(Lcom/gtnewhorizon/gtnhlib/teams/Team;Lcom/gtnewhorizon/gtnhlib/teams/Team;)V",
            true);
        String drive = "appeng/tile/storage/TileDrive";
        method(drive, "updateState", "()V", false);
        field(drive, "isCached", "Z");
        field(drive, "priority", "I");
        field(drive, "inv", "Lappeng/tile/inventory/AppEngInternalInventory;");
        field(drive, "handlersBySlot", "[Lappeng/api/storage/ICellHandler;");
        field(drive, "invBySlot", "[Lappeng/me/storage/MEInventoryHandler;");
        field(drive, "cellsMap", "Ljava/util/Map;");
        String chest = "appeng/tile/storage/TileChest";
        method(
            chest,
            "getHandler",
            "(Lappeng/api/storage/data/IAEStackType;)Lappeng/api/storage/IMEInventoryHandler;",
            false);
        method(chest, "wrap", "(Lappeng/api/storage/IMEInventoryHandler;)Lappeng/api/storage/MEMonitorHandler;", false);
        field(chest, "isCached", "Z");
        field(chest, "cellHandler", "Lappeng/api/storage/ICellHandler;");
        field(chest, "inv", "Lappeng/tile/inventory/AppEngInternalInventory;");
        field(chest, "cellMap", "Ljava/util/Map;");
        String port = "appeng/tile/storage/TileIOPort";
        method(port, "getInv", "(Lnet/minecraft/item/ItemStack;)Lappeng/api/storage/IMEInventory;", false);
        method(
            port,
            "shouldMove",
            "(Lappeng/api/storage/IMEInventory;ZZZZLappeng/api/config/OperationMode;Lappeng/api/config/FullnessMode;)Z",
            false);
        method(
            port,
            "getAvailableStacks",
            "(Lappeng/api/storage/IMEInventory;)Lappeng/api/storage/data/IItemList;",
            false);
        field(port, "currentCell", "Lnet/minecraft/item/ItemStack;");
        field(port, "cachedInventory", "Lappeng/api/storage/IMEInventory;");
        String layout = "codechicken/nei/LayoutManager";
        method(layout, "layout", "(Lnet/minecraft/client/gui/inventory/GuiContainer;)V", true);
        method(layout, "mouseClicked", "(Lnet/minecraft/client/gui/inventory/GuiContainer;III)Z", false);
        invocation(layout, "updateWidgetVisiblities", layout, "addWidget", "(Lcodechicken/nei/Widget;)V");
        invocation(layout, "renderObjects", "codechicken/nei/ButtonCycled", "draw", "(II)V");
        System.out.println(
            args[0] + ": "
                + checks
                + " exact API/Mixin ABI contracts passed; Mixin application and game behavior NOT_RUN");
    }

    private static ClassNode type(String name) throws Exception {
        for (File file : ARTIFACTS) {
            try (JarFile jar = new JarFile(file)) {
                if (jar.getJarEntry(name + ".class") == null) continue;
                try (InputStream stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
                    ClassNode node = new ClassNode();
                    new ClassReader(stream).accept(node, 0);
                    return node;
                }
            }
        }
        throw new AssertionError("Missing class " + name);
    }

    private static void method(String owner, String name, String desc, boolean isStatic) throws Exception {
        for (Object value : type(owner).methods) {
            MethodNode method = (MethodNode) value;
            if (name.equals(method.name) && desc.equals(method.desc)
                && ((method.access & Opcodes.ACC_STATIC) != 0) == isStatic) {
                checks++;
                return;
            }
        }
        throw new AssertionError("Missing method/static contract " + owner + "." + name + desc);
    }

    private static void field(String owner, String name, String desc) throws Exception {
        for (Object value : type(owner).fields) {
            FieldNode field = (FieldNode) value;
            if (name.equals(field.name) && desc.equals(field.desc)) {
                checks++;
                return;
            }
        }
        throw new AssertionError("Missing shadow field " + owner + "." + name + ":" + desc);
    }

    private static void invocation(String owner, String methodName, String target, String name, String desc)
        throws Exception {
        for (Object value : type(owner).methods) {
            MethodNode method = (MethodNode) value;
            if (!methodName.equals(method.name)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (target.equals(call.owner) && name.equals(call.name) && desc.equals(call.desc)) {
                    checks++;
                    return;
                }
            }
        }
        throw new AssertionError(
            "Missing injection call " + owner + "." + methodName + " -> " + target + "." + name + desc);
    }

    private static String digest(File file) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(java.nio.file.Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format("%02x", value & 255));
        return result.toString();
    }
}
