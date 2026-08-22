package com.miaokatze.gtit.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 配置文件旧路径迁移公共工具
 * <p>
 * 收编 DailySignInConfig / OnlineTimeConfig / NekoPageConfig / NekoTradeConfig 四处
 * 手写重复的「旧文件重命名为 {@code .bak} + 统一迁移日志」收尾语义（v1.7.7 G4 约定）。
 * 各调用点的迁移内容转换（原文复制 / 解析重写 / 拆分保存）仍由调用方自行完成，
 * 本类只负责旧文件退役与日志格式统一。
 */
public final class ConfigMigrationUtil {

    private ConfigMigrationUtil() {}

    /**
     * 计算同目录备份路径（原文件名 + 后缀，如 {@code .bak} / {@code .v1.bak}）
     *
     * @param filePath 原文件路径
     * @param suffix   备份后缀（含起始点号）
     * @return 同目录下的备份文件路径
     */
    public static Path siblingBackupPath(Path filePath, String suffix) {
        return filePath.resolveSibling(
            filePath.getFileName()
                .toString() + suffix);
    }

    /**
     * 旧配置迁移统一收尾：旧文件重命名为 {@code <原名>.bak}（备份已存在则覆盖），并输出统一迁移/保留日志
     * <p>
     * 迁移内容的写出须在调用本方法之前完成；本方法执行后旧路径不再存在。
     *
     * @param legacyPath      旧配置文件路径（迁移源）
     * @param newLocation     新位置（日志用，为新文件路径或新目录路径）
     * @param configTitle     配置名（日志一：{@code {configTitle}已从旧路径迁移: {legacyPath} -> {newLocation}}）
     * @param legacyFileTitle 旧文件描述（日志二：{@code 旧{legacyFileTitle}已重命名保留: {backupPath}}）
     */
    public static void retireLegacyAsBak(Path legacyPath, Path newLocation, String configTitle, String legacyFileTitle)
        throws IOException {
        Path backupPath = siblingBackupPath(legacyPath, ".bak");
        Files.move(legacyPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        GTInterestingThing.LOG.info("{}已从旧路径迁移: {} -> {}", configTitle, legacyPath, newLocation);
        GTInterestingThing.LOG.info("旧{}已重命名保留: {}", legacyFileTitle, backupPath);
    }
}
