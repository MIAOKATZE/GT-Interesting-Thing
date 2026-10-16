package com.miaokatze.gtit.trade;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import cpw.mods.fml.common.Loader;

/** Keep presets, pages, trade entries and integration records in the same actual game directory. */
public final class TradeConfigPaths {

    private TradeConfigPaths() {}

    public static Path gameRoot() {
        try {
            File config = Loader.instance()
                .getConfigDir();
            if (config != null) return config.toPath()
                .toAbsolutePath()
                .normalize()
                .getParent();
        } catch (LinkageError | RuntimeException uninitializedLoader) {
            // Standalone JVM regression suites run before FML has a configured game directory.
        }
        return Paths.get("")
            .toAbsolutePath()
            .normalize();
    }

    public static Path resolve(String relative) {
        Path root = gameRoot();
        Path resolved = root.resolve(relative)
            .normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("贸易配置路径越界");
        return resolved;
    }
}
