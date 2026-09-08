package com.miaokatze.gtit.reincarnation.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 时间线指纹工具（周目系统，纯函数）。
 * <p>
 * 指纹 = SHA-256( 十进制 seed 字符串的 UTF-8 字节 ) 的小写 hex（64 字符）。
 * 同一 seed 恒得同一指纹；已确认轮回的指纹进入玩家记录的永久集合
 * （{@code ReincarnationCycle#getExecutedFingerprints}），用于判定"该时间线已周目过"。
 * <p>
 * 刻意不依赖任何日志/MC 类：纯 JVM 可用，供无头测试直跑。
 */
public final class ReincarnationFingerprints {

    private ReincarnationFingerprints() {}

    /**
     * 计算时间线指纹（纯函数，无副作用）。
     *
     * @param seed 时间线种子（世界 seed 或其派生值）
     * @return 64 位小写 hex 字符串
     */
    public static String fingerprintOf(long seed) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // JVM 规范强制所有实现提供 SHA-256，理论上不可达
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
        byte[] hashed = digest.digest(
            Long.toString(seed)
                // 十进制字符串形式：与 Locale/字节序无关，跨平台稳定
                .getBytes(StandardCharsets.UTF_8));
        return toLowerHex(hashed);
    }

    /** 字节数组转小写 hex */
    private static String toLowerHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
