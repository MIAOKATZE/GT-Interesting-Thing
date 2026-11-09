package com.miaokatze.gtit.common.items.pocket;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModAPIManager;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.asm.transformers.ModAPITransformer;
import cpw.mods.fml.common.discovery.ASMDataTable;

/** Real FML Optional transformer in an isolated LaunchClassLoader, including the API-only difference. */
public final class PocketOptionalClassloadingTest {

    private static final String TARGET = "com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket";
    private static final String API = "thaumcraft.api.IRunicArmor";
    private static byte[] candidate;
    private static ModAPITransformer transformer;

    public static final class Bridge implements IClassTransformer {

        @Override
        public byte[] transform(String name, String mapped, byte[] bytes) {
            return TARGET.equals(name) ? transformer.transform(name, mapped, candidate) : bytes;
        }
    }

    private static final class Isolated extends LaunchClassLoader {

        private final boolean api;

        Isolated(URL[] urls, boolean api) {
            super(urls);
            this.api = api;
            addClassLoaderExclusion(PocketOptionalClassloadingTest.class.getName());
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.startsWith("thaumcraft.")) {
                if (!api || !name.equals(API)) throw new ClassNotFoundException(name);
                return super.findClass(name);
            }
            if (TARGET.equals(name)) return super.findClass(name);
            return PocketOptionalClassloadingTest.class.getClassLoader()
                .loadClass(name);
        }
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(PocketOptionalClassloadingTest.class, args)) return;
        try (InputStream input = PocketOptionalClassloadingTest.class.getClassLoader()
            .getResourceAsStream(TARGET.replace('.', '/') + ".class")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            candidate = output.toByteArray();
        }
        ClassNode declared = new ClassNode();
        new ClassReader(candidate).accept(declared, 0);
        check(
            declared.interfaces.contains(API.replace('.', '/')),
            "production Item must implement the optional interface");
        URL[] urls = ((LaunchClassLoader) PocketOptionalClassloadingTest.class.getClassLoader()).getSources()
            .toArray(new URL[0]);
        check(
            Class.forName(API, false, PocketOptionalClassloadingTest.class.getClassLoader()) != null,
            "test runtime deliberately supplies TC; isolated absence must still block it");
        for (boolean client : new boolean[] { false, true }) {
            set(
                cpw.mods.fml.relauncher.FMLRelaunchLog.class,
                null,
                "side",
                client ? cpw.mods.fml.relauncher.Side.CLIENT : cpw.mods.fml.relauncher.Side.SERVER);
            for (int mode = 0; mode < 3; mode++) {
                Map<String, ModContainer> mods = new HashMap<>();
                if (mode == 1) mods.put("Thaumcraft", new DummyModContainer(new ModMetadata()));
                set(Loader.class, Loader.instance(), "namedMods", mods);
                set(Loader.class, Loader.instance(), "modController", new LoadController(Loader.instance()));
                Map<String, Object> apis = new HashMap<>();
                if (mode == 2) apis.put("Thaumcraft", null);
                set(ModAPIManager.class, ModAPIManager.INSTANCE, "apiContainers", apis);
                transformer = new ModAPITransformer();
                ASMDataTable table = new ASMDataTable();
                new cpw.mods.fml.common.discovery.asm.ASMModParser(new java.io.ByteArrayInputStream(candidate))
                    .sendToTable(table, null);
                transformer.initTable(table);
                try (Isolated loader = new Isolated(urls, mode != 0)) {
                    loader.registerTransformer(Bridge.class.getName());
                    assertBlocked(loader, "thaumcraft.common.Thaumcraft");
                    assertBlocked(loader, "thaumcraft.api.aspects.Aspect");
                    if (mode == 0) assertBlocked(loader, API);
                    else check(
                        Class.forName(API, false, loader)
                            .getClassLoader() == loader,
                        "optional API must be defined by the isolated loader, never inherited from test runtime");
                    Class<?> target = Class.forName(TARGET, true, loader);
                    boolean runic = java.util.Arrays.stream(target.getInterfaces())
                        .anyMatch(
                            i -> i.getName()
                                .equals(API));
                    check(runic == (mode != 0), "interface presence mode=" + mode);
                    if (mode == 0) {
                        try {
                            target.getMethod("getRunicCharge", net.minecraft.item.ItemStack.class);
                            throw new AssertionError("method survived absent TC");
                        } catch (NoSuchMethodException expected) { /* physically removed */ }
                    } else {
                        Object item = unsafe().allocateInstance(target);
                        int charge = (Integer) target.getMethod("getRunicCharge", net.minecraft.item.ItemStack.class)
                            .invoke(item, new Object[] { null });
                        check(charge == (mode == 1 ? 20 : 0), "API-only must preserve mod-only gameplay gate");
                    }
                    System.out.println("Optional PASS side=" + (client ? "client" : "server") + " mode=" + mode);
                }
            }
        }
        // Loading before FML's Optional table is initialized is intentionally unsupported and must be visible.
        transformer = new ModAPITransformer();
        try (Isolated early = new Isolated(urls, false)) {
            early.registerTransformer(Bridge.class.getName());
            try {
                Class.forName(TARGET, false, early);
                throw new AssertionError("uninitialized Optional transformer accepted missing interface");
            } catch (ClassNotFoundException | LinkageError expected) {
                System.out.println("Optional early-load rejection PASS");
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertBlocked(ClassLoader loader, String name) throws Exception {
        try {
            Class.forName(name, false, loader);
            throw new AssertionError("isolated TC package leaked from test runtime: " + name);
        } catch (ClassNotFoundException expected) { /* explicit absence despite parent/test dependency */ }
    }

    private static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
