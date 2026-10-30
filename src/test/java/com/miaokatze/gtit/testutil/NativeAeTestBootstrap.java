package com.miaokatze.gtit.testutil;

/** Actual FML/AE2 coremod prerequisites for suites exercising native AE stacks, not substitute APIs. */
public final class NativeAeTestBootstrap {

    private NativeAeTestBootstrap() {}

    public static boolean enter(Class<?> suite, String[] args) throws Exception {
        verifyAsmToolchain();
        // Declared by FMLCorePlugin: installs each event's native no-arg constructor and listener list.
        if (!MinecraftTestBootstrap
            .enter(suite, args, "cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer")) return false;
        // Native integration filtering uses FMLLaunchHandler.side(), not the logging side.
        java.lang.reflect.Field launchSide = cpw.mods.fml.relauncher.FMLLaunchHandler.class.getDeclaredField("side");
        launchSide.setAccessible(true);
        if (launchSide.get(null) == null) launchSide.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        if (launchSide.get(null) != cpw.mods.fml.relauncher.Side.SERVER) {
            throw new IllegalStateException("Native AE headless fixture requires the existing server side");
        }
        java.lang.reflect.Field names = cpw.mods.fml.common.Loader.class.getDeclaredField("namedMods");
        names.setAccessible(true);
        if (names.get(cpw.mods.fml.common.Loader.instance()) == null) {
            names.set(cpw.mods.fml.common.Loader.instance(), new java.util.HashMap<>());
        }
        cpw.mods.fml.common.FMLModContainer fixtureContainer = installNativeOwner();
        if (appeng.core.AEConfig.instance == null) {
            java.io.File directory = java.nio.file.Files.createTempDirectory("gtit-native-ae-")
                .toFile();
            // AppEng.preInit installs this real configuration before stack/API initialization.
            appeng.core.AEConfig.instance = new appeng.core.AEConfig(
                new java.io.File(directory, "AppliedEnergistics2.cfg"));
        }
        net.minecraft.launchwrapper.LaunchClassLoader loader = (net.minecraft.launchwrapper.LaunchClassLoader) suite
            .getClassLoader();
        // Declared by AppEngCore: strips optional interfaces according to native AUTO mod discovery.
        String transformer = "appeng.transformer.asm.ASMIntegration";
        if (loader.getTransformers()
            .stream()
            .noneMatch(
                t -> t.getClass()
                    .getName()
                    .equals(transformer))) {
            loader.registerTransformer(transformer);
        }
        if (loader.getTransformers()
            .stream()
            .noneMatch(
                t -> t.getClass()
                    .getName()
                    .equals(transformer))) {
            throw new IllegalStateException("Native AE integration transformer did not register");
        }
        if (fixtureContainer != null) {
            // Native AppEng uses its singleton factory; retain that exact object, not a surrogate mod.
            Object instance = appeng.core.AppEng.instance();
            field(cpw.mods.fml.common.FMLModContainer.class, "modInstance").set(fixtureContainer, instance);
            cpw.mods.fml.common.LoadController controller = (cpw.mods.fml.common.LoadController) field(
                cpw.mods.fml.common.Loader.class,
                "modController").get(cpw.mods.fml.common.Loader.instance());
            field(cpw.mods.fml.common.LoadController.class, "modObjectList")
                .set(controller, controller.buildModObjectList());
            field(cpw.mods.fml.common.LoadController.class, "activeContainer").set(controller, null);
            if (cpw.mods.fml.common.FMLCommonHandler.instance()
                .findContainerFor(instance) != fixtureContainer) {
                throw new IllegalStateException("Native AE object/container ownership did not bind");
            }
        }
        return true;
    }

    private static cpw.mods.fml.common.FMLModContainer installNativeOwner() throws Exception {
        cpw.mods.fml.common.Loader loader = cpw.mods.fml.common.Loader.instance();
        java.lang.reflect.Field controllerField = field(cpw.mods.fml.common.Loader.class, "modController");
        if (controllerField.get(loader) != null) {
            cpw.mods.fml.common.ModContainer existing = loader.getIndexedModList()
                .get("appliedenergistics2");
            if (existing instanceof cpw.mods.fml.common.FMLModContainer
                && "rv3-beta-1080-GTNH".equals(existing.getVersion())
                && existing.getMod() == appeng.core.AppEng.instance()
                && loader.getReversedModObjectList()
                    .get(existing.getMod()) == existing)
                return null;
            throw new IllegalStateException("Refusing to replace existing native loader ownership state");
        }
        Object priorMods = field(cpw.mods.fml.common.Loader.class, "mods").get(loader);
        if (!loader.getIndexedModList()
            .isEmpty() || (priorMods != null && !((java.util.List<?>) priorMods).isEmpty())) {
            throw new IllegalStateException("Native AE fixture requires absent discovery state");
        }
        java.io.File jar = loadedArtifact(appeng.core.AEConfig.class);
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(java.nio.file.Files.readAllBytes(jar.toPath()));
        StringBuilder hash = new StringBuilder();
        for (byte value : digest) hash.append(String.format("%02x", value & 255));
        if (jar.length() != 5011325L
            || !"325d58c44250aae283899296eb8832ab184afc0d0d2c25b15acf6e806031415a".equals(hash.toString())) {
            throw new IllegalStateException("Unexpected AE artifact for native loader fixture: " + jar);
        }
        cpw.mods.fml.common.discovery.ASMDataTable table = new cpw.mods.fml.common.discovery.ASMDataTable();
        cpw.mods.fml.common.discovery.ModCandidate candidate = new cpw.mods.fml.common.discovery.ModCandidate(
            jar,
            jar,
            cpw.mods.fml.common.discovery.ContainerType.JAR);
        java.util.List<cpw.mods.fml.common.ModContainer> discovered = candidate.explore(table);
        cpw.mods.fml.common.FMLModContainer ae = null;
        for (cpw.mods.fml.common.ModContainer mod : discovered) {
            if ("appliedenergistics2".equals(mod.getModId()) && "rv3-beta-1080-GTNH".equals(mod.getVersion())
                && mod instanceof cpw.mods.fml.common.FMLModContainer) {
                if (ae != null) throw new IllegalStateException("Duplicate native AE mod metadata");
                ae = (cpw.mods.fml.common.FMLModContainer) mod;
            }
        }
        if (ae == null) throw new IllegalStateException("Verified AE jar did not yield native FML mod metadata");
        cpw.mods.fml.common.LoadController controller = new cpw.mods.fml.common.LoadController(loader);
        controller.getActiveModList()
            .add(ae);
        field(cpw.mods.fml.common.LoadController.class, "activeContainer").set(controller, ae);
        field(cpw.mods.fml.common.Loader.class, "mods").set(loader, java.util.Collections.singletonList(ae));
        java.util.Map<String, cpw.mods.fml.common.ModContainer> names = new java.util.HashMap<>();
        names.put(ae.getModId(), ae);
        field(cpw.mods.fml.common.Loader.class, "namedMods").set(loader, names);
        controllerField.set(loader, controller);
        return ae;
    }

    private static java.io.File loadedArtifact(Class<?> type) throws Exception {
        java.net.URL origin = type.getProtectionDomain()
            .getCodeSource()
            .getLocation();
        if ("jar".equals(origin.getProtocol())) {
            origin = ((java.net.JarURLConnection) origin.openConnection()).getJarFileURL();
        }
        if (!"file".equals(origin.getProtocol())) {
            throw new IllegalStateException("Native test fixture accepts local artifact origins only: " + origin);
        }
        return new java.io.File(origin.toURI()).getCanonicalFile();
    }

    private static void verifyAsmToolchain() throws Exception {
        if (!"1.8".equals(System.getProperty("java.specification.version"))) {
            throw new IllegalStateException(
                "Native Forge/AE headless lane requires explicit Java 8; modern server acceptance is separate");
        }
        String[] expected = { "org.objectweb.asm.ClassReader", "org.objectweb.asm.tree.ClassNode",
            "org.objectweb.asm.commons.RemappingClassAdapter" };
        for (String name : expected) {
            Class<?> actual = Class.forName(name, false, NativeAeTestBootstrap.class.getClassLoader());
            java.io.File jar = loadedArtifact(actual);
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(jar.toPath()));
            StringBuilder hash = new StringBuilder();
            for (byte value : bytes) hash.append(String.format("%02x", value & 255));
            if (!jar.getName()
                .equals("asm-debug-all-5.0.3.jar")
                || !"2969356ac1e40fe4141948c563690238f5d79d76aec7fd873536867a331ede81".equals(hash.toString())) {
                throw new IllegalStateException("Wrong native Forge ASM5 artifact/order: " + name + " from " + jar);
            }
        }
        String target = "gregtech/api/metatileentity/implementations/MTEEnhancedMultiBlockBase.class";
        java.net.URL origin = NativeAeTestBootstrap.class.getClassLoader()
            .getResource(target);
        if (origin == null || !"jar".equals(origin.getProtocol())
            || !origin.toExternalForm()
                .endsWith("!/" + target)) {
            throw new IllegalStateException(
                "Expected actual GT base class resource, not a multi-release/fixture replacement: " + origin);
        }
        java.net.JarURLConnection connection = (java.net.JarURLConnection) origin.openConnection();
        if (!"file".equals(
            connection.getJarFileURL()
                .getProtocol())) {
            throw new IllegalStateException("Expected local GT artifact: " + origin);
        }
        try (java.io.DataInputStream in = new java.io.DataInputStream(origin.openStream())) {
            if (in.readInt() != 0xcafebabe) throw new IllegalStateException("Invalid GT class resource");
            in.readUnsignedShort();
            if (in.readUnsignedShort() != 52)
                throw new IllegalStateException("Native Java8 lane did not select actual GT base major52");
        }
        System.out.println(
            "Native AE headless lane: Java " + System.getProperty("java.version")
                + ", verified Forge ASM5.0.3, GT base major52 from "
                + origin
                + "; modern server/MR acceptance remains separate");
    }

    private static java.lang.reflect.Field field(Class<?> owner, String name) throws Exception {
        java.lang.reflect.Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
