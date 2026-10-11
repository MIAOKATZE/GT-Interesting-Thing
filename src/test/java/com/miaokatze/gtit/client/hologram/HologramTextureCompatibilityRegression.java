package com.miaokatze.gtit.client.hologram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.minecraftforge.common.util.ForgeDirection;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import com.miaokatze.gtit.testutil.SimpleAssert;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** Executes the production resolver against the actual GT interface with and without its optional getter. */
public final class HologramTextureCompatibilityRegression {

    private static final String MACHINE = IMetaTileEntity.class.getName();
    private static final String RESOLVER = HologramMachineTextures.class.getName();

    private HologramTextureCompatibilityRegression() {}

    public static void run() {
        try {
            modernOverride();
            legacyInterface();
            callbackFailures();
        } catch (ReflectiveOperationException | IOException | RuntimeException failure) {
            failure.printStackTrace();
            throw new AssertionError(failure);
        }
    }

    private static void modernOverride() throws ReflectiveOperationException {
        Method getter = IMetaTileEntity.class.getMethod("getInventoryTextures");
        SimpleAssert.that(getter != null, "baseline includes the real optional GT getter");
        MachineProbe probe = new MachineProbe();
        probe.inventory = faces();
        IMetaTileEntity machine = (IMetaTileEntity) probe.proxy(IMetaTileEntity.class);
        int[] sources = identity();
        SimpleAssert.that(
            HologramMachineTextures.resolve(machine, -1, sources) == probe.inventory,
            "new API keeps the exact custom inventory model");
        SimpleAssert.eq(1, probe.getterCalls, "optional getter delegates to the machine override");
        SimpleAssert.eq(0, probe.legacyCalls, "valid override avoids legacy reconstruction");
        for (int front = 0; front < 6; front++) {
            probe.world = faces();
            sources = identity();
            ITexture[][] resolved = HologramMachineTextures.resolve(machine, front, sources);
            for (int side = 0; side < 6; side++) {
                SimpleAssert.that(resolved[side] == probe.world[side], "world callback retains all overlay layers");
                SimpleAssert.eq(side, sources[side], "world callback retains the world-side icon orientation");
                SimpleAssert.eq(front, probe.worldFronts[side], "world callback receives the actual facing");
            }
        }
        SimpleAssert.eq(0, probe.legacyCalls, "world-side resolution also keeps the new inventory override");
    }

    private static void legacyInterface() throws ReflectiveOperationException, IOException {
        // A new loader binds the unchanged production bytecode to the real interface minus only the new method.
        ClassLoader loader = new LegacyApiLoader();
        Class<?> machineType = loader.loadClass(MACHINE);
        boolean absent = false;
        try {
            machineType.getMethod("getInventoryTextures");
        } catch (NoSuchMethodException expected) {
            absent = true;
        }
        SimpleAssert.that(absent, "legacy interface actually lacks the optional getter");
        Class<?> resolver = loader.loadClass(RESOLVER);
        Field cached = resolver.getDeclaredField("INVENTORY_TEXTURES");
        cached.setAccessible(true);
        SimpleAssert.that(cached.get(null) == null, "missing API is cached once rather than failing every render");
        Method resolve = resolver.getDeclaredMethod("resolve", machineType, int.class, int[].class);
        resolve.setAccessible(true);
        MachineProbe probe = new MachineProbe();
        Object machine = probe.proxy(machineType);
        ITexture[][] inventory = invoke(resolve, machine, -1, identity());
        for (int side = 0; side < 6; side++) {
            SimpleAssert.that(inventory[side] == probe.legacy[side], "old shared API supplies each inventory face");
        }
        SimpleAssert.eq(6, probe.legacyCalls, "old inventory model reads six sides");
        for (int front = 0; front < 6; front++) {
            int[] sources = identity();
            ITexture[][] resolved = invoke(resolve, machine, front, sources);
            for (int side = 0; side < 6; side++) {
                int source = HologramTextures.inventorySide(front, side);
                SimpleAssert
                    .that(resolved[side] == probe.legacy[source], "old inventory fallback follows every facing");
                SimpleAssert.eq(source, sources[side], "sided icons use the remapped inventory face");
                SimpleAssert.eq(front, probe.worldFronts[side], "old world callback still receives actual facing");
            }
            SimpleAssert.that(resolved[front] == probe.legacy[4], "WEST inventory front follows world front");
        }
        probe.world = faces();
        int[] sources = identity();
        ITexture[][] resolved = invoke(resolve, machine, 3, sources);
        for (int side = 0; side < 6; side++) {
            SimpleAssert.that(resolved[side] == probe.world[side], "old API preserves world overlay callback results");
            SimpleAssert.eq(side, sources[side], "old world results retain original side identity");
        }
        SimpleAssert.eq(0, probe.getterCalls, "old interface never attempts the missing getter");
        SimpleAssert.that(cached.get(null) == null, "repeated resolves reuse the cached missing capability");
    }

    private static void callbackFailures() {
        MachineProbe probe = new MachineProbe();
        IMetaTileEntity machine = (IMetaTileEntity) probe.proxy(IMetaTileEntity.class);
        probe.getterFailure = new IllegalStateException("model needs world");
        probe.legacyFailureSide = 0;
        ITexture[][] inventory = HologramMachineTextures.resolve(machine, -1, identity());
        SimpleAssert.that(inventory[0] == null, "failed legacy face remains eligible for block-icon fallback");
        for (int side = 1; side < 6; side++) {
            SimpleAssert
                .that(inventory[side] == probe.legacy[side], "one failed legacy face does not hide other faces");
        }
        probe.getterFailure = null;
        probe.inventory = faces();
        probe.world = faces();
        probe.worldFailureSide = 2;
        probe.world[4] = new ITexture[0];
        int[] sources = identity();
        ITexture[][] resolved = HologramMachineTextures.resolve(machine, 3, sources);
        for (int side = 0; side < 6; side++) {
            int source = HologramTextures.inventorySide(3, side);
            if (side == 2 || side == 4) {
                SimpleAssert
                    .that(resolved[side] == probe.inventory[source], "failed or empty world face uses inventory");
                SimpleAssert.eq(source, sources[side], "fallback icon orientation uses the inventory side");
            } else {
                SimpleAssert.that(resolved[side] == probe.world[side], "other world faces keep overlay layers");
            }
        }
        AssertionError fatal = new AssertionError("texture invariant failed");
        probe.getterFailure = fatal;
        boolean propagated = false;
        try {
            HologramMachineTextures.resolve(machine, -1, identity());
        } catch (AssertionError actual) {
            propagated = actual == fatal;
        }
        SimpleAssert.that(propagated, "serious inventory callback errors are not hidden by fallback");
        probe.getterFailure = null;
        probe.worldFailure = fatal;
        propagated = false;
        try {
            HologramMachineTextures.resolve(machine, 3, identity());
        } catch (AssertionError actual) {
            propagated = actual == fatal;
        }
        SimpleAssert.that(propagated, "serious world callback errors are not hidden by fallback");
    }

    private static int[] identity() {
        return new int[] { 0, 1, 2, 3, 4, 5 };
    }

    private static ITexture[][] faces() {
        ITexture[][] faces = new ITexture[6][];
        // Distinct layer arrays prove that both base and overlay slots survive resolution unchanged.
        for (int side = 0; side < 6; side++) faces[side] = new ITexture[2];
        return faces;
    }

    private static ITexture[][] invoke(Method resolve, Object machine, int front, int[] sources)
        throws ReflectiveOperationException {
        try {
            return (ITexture[][]) resolve.invoke(null, machine, front, sources);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) throw (Error) cause;
            throw failure;
        }
    }

    private static final class MachineProbe implements InvocationHandler {

        final ITexture[][] legacy = faces();
        final int[] worldFronts = new int[6];
        ITexture[][] inventory, world;
        Throwable getterFailure;
        Error worldFailure;
        int legacyCalls, getterCalls;
        int legacyFailureSide = -1, worldFailureSide = -1;

        Object proxy(Class<?> type) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getInventoryTextures":
                    getterCalls++;
                    if (getterFailure != null) throw getterFailure;
                    return inventory;
                case "getBaseMetaTileEntity":
                    return null;
                case "getTexture":
                    int side = ((ForgeDirection) args[1]).ordinal();
                    SimpleAssert.eq(null, args[0], "test prototype is read without a fabricated base tile");
                    SimpleAssert.eq(-1, args[3], "inventory and world calls use neutral color");
                    SimpleAssert.eq(false, args[5], "inventory and world calls are not redstone-active");
                    if ((boolean) args[4]) {
                        legacyCalls++;
                        SimpleAssert.eq(ForgeDirection.WEST, args[2], "legacy inventory uses GT's WEST front");
                        if (side == legacyFailureSide) throw new IllegalStateException("legacy face needs world");
                        return legacy[side];
                    }
                    worldFronts[side] = ((ForgeDirection) args[2]).ordinal();
                    if (worldFailure != null) throw worldFailure;
                    if (side == worldFailureSide) throw new IllegalStateException("world face needs tile");
                    return world == null ? null : world[side];
                default:
                    throw new AssertionError("Unexpected prototype operation: " + method.getName());
            }
        }
    }

    private static final class LegacyApiLoader extends ClassLoader {

        LegacyApiLoader() {
            super(HologramTextureCompatibilityRegression.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!MACHINE.equals(name) && !RESOLVER.equals(name)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        byte[] bytes = classBytes(name);
                        if (MACHINE.equals(name)) {
                            ClassReader reader = new ClassReader(bytes);
                            ClassWriter writer = new ClassWriter(0);
                            // ASM9 at runtime, while compilation still uses Forge's older API declarations.
                            reader.accept(new ClassVisitor(9 << 16, writer) {

                                @Override
                                public MethodVisitor visitMethod(int access, String method, String descriptor,
                                    String signature, String[] exceptions) {
                                    if ("getInventoryTextures".equals(method) && descriptor.startsWith("()"))
                                        return null;
                                    return super.visitMethod(access, method, descriptor, signature, exceptions);
                                }
                            }, 0);
                            bytes = writer.toByteArray();
                        }
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        private byte[] classBytes(String name) throws IOException {
            try (InputStream stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (stream == null) throw new IOException("Missing class bytes: " + name);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                return bytes.toByteArray();
            }
        }
    }
}
