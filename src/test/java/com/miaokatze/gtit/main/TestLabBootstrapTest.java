// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.main;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs the actual production bootstrap with laboratory classes denied by its loader. */
public final class TestLabBootstrapTest {

    private TestLabBootstrapTest() {}

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("gtit-bootstrap-");
        java.lang.reflect.Field forgeHome = cpw.mods.fml.relauncher.FMLInjectionData.class
            .getDeclaredField("minecraftHome");
        forgeHome.setAccessible(true);
        Object previousForgeHome = forgeHome.get(null);
        forgeHome.set(null, directory.toFile());
        try {
            if (TestLabBootstrap.requested(directory.toFile())) throw new AssertionError("Missing config enabled lab");
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                if (files.findAny()
                    .isPresent()) throw new AssertionError("Disabled bootstrap wrote config");
            }
            Path config = directory.resolve("gtit/testlab.cfg");
            Files.createDirectories(config.getParent());
            byte[] disabled = "testlab {\n B:enabled=false\n}\n".getBytes(StandardCharsets.UTF_8);
            Files.write(config, disabled);
            if (TestLabBootstrap.requested(directory.toFile())) throw new AssertionError("False opt-in enabled lab");
            if (!java.util.Arrays.equals(disabled, Files.readAllBytes(config)))
                throw new AssertionError("Disabled bootstrap rewrote config");
            Files.write(config, "testlab {\n B:enabled=true\n}\n".getBytes(StandardCharsets.UTF_8));
            if (!TestLabBootstrap.requested(directory.toFile())) throw new AssertionError("Explicit opt-in lost");
            ClassLoader loader = new ClassLoader(TestLabBootstrapTest.class.getClassLoader()) {

                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("com.miaokatze.gtit.testlab.")) throw new ClassNotFoundException(name);
                    if (!name.equals(TestLabBootstrap.class.getName())) return super.loadClass(name, resolve);
                    Class<?> defined = findLoadedClass(name);
                    if (defined == null) {
                        try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            byte[] buffer = new byte[4096];
                            int read;
                            while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
                            byte[] data = bytes.toByteArray();
                            defined = defineClass(name, data, 0, data.length);
                        } catch (java.io.IOException error) {
                            throw new ClassNotFoundException(name, error);
                        }
                    }
                    if (resolve) resolveClass(defined);
                    return defined;
                }
            };
            Class<?> bootstrap = loader.loadClass(TestLabBootstrap.class.getName());
            bootstrap.getMethod("serverStopping")
                .invoke(null);
            bootstrap.getMethod("installClient")
                .invoke(null);
            java.lang.reflect.Field enabled = bootstrap.getDeclaredField("enabled");
            enabled.setAccessible(true);
            enabled.setBoolean(null, true);
            try {
                bootstrap.getMethod("installClient")
                    .invoke(null);
                throw new AssertionError("Missing testlab support accepted explicit enable");
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (!(cause instanceof IllegalStateException) || !cause.getMessage()
                    .contains("-testlab JAR"))
                    throw new AssertionError("Missing support must explain the required variant", cause);
            }
        } finally {
            forgeHome.set(null, previousForgeHome);
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder())
                    .toArray(Path[]::new)) Files.delete(path);
            }
        }
        System.out.println("GTIT production bootstrap boundaries passed");
    }
}
