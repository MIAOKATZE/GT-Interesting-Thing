package com.miaokatze.gtit.trade.v2;

import java.util.Base64;
import java.util.UUID;

/**
 * BQ 任务 ID 解析器
 * <p>
 * 支持多种格式的任务 ID 解析：
 * <ul>
 * <li>high:low 格式（如 "123:456"）</li>
 * <li>UUID 格式（如 "550e8400-e29b-41d4-a716-446655440000"）</li>
 * <li>Base64 格式（16字节，如 "AAAAAAAAAAAAAAAAAAAADw=="）</li>
 * <li>URL-safe Base64 格式（16字节，可无 padding，如 "AAAAAAAAAAAAAAAAAAAAAA"）</li>
 * </ul>
 * 统一输出为 UUID，供 BQ API 使用。
 */
public class NekoBqQuestIdParser {

    /**
     * 私有构造器，工具类不应实例化
     */
    private NekoBqQuestIdParser() {}

    /**
     * 解析任务 ID
     * <p>
     * 自动检测输入格式并转换为 UUID。
     *
     * @param input 原始任务 ID 字符串
     * @return 解析后的 UUID，无效格式返回 null
     */
    public static UUID parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        try {
            // 格式1：high:low 格式（如 "0:0"）
            if (input.contains(":")) {
                String[] parts = input.split(":", 2);
                if (parts.length == 2) {
                    long msb = Long.parseLong(parts[0].trim());
                    long lsb = Long.parseLong(parts[1].trim());
                    return new UUID(msb, lsb);
                }
                return null;
            }

            // 格式2：标准 UUID 格式（如 "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"）
            if (input.contains("-")) {
                try {
                    return UUID.fromString(input);
                } catch (IllegalArgumentException e) {
                    // 含 '-' 但不是合法 UUID，继续尝试 URL-safe Base64 解码
                }
            }

            // 格式3：Base64 格式（16字节），支持标准 Base64 与 URL-safe Base64（可无 padding）
            String base64 = input;
            int padding = (4 - base64.length() % 4) % 4;
            if (padding > 0) {
                StringBuilder sb = new StringBuilder(base64);
                for (int i = 0; i < padding; i++) {
                    sb.append('=');
                }
                base64 = sb.toString();
            }

            byte[] bytes;
            if (base64.contains("-") || base64.contains("_")) {
                bytes = Base64.getUrlDecoder()
                    .decode(base64);
            } else {
                try {
                    bytes = Base64.getDecoder()
                        .decode(base64);
                } catch (IllegalArgumentException e) {
                    bytes = Base64.getUrlDecoder()
                        .decode(base64);
                }
            }
            if (bytes.length == 16) {
                long msb = 0;
                long lsb = 0;
                // 前8字节为 msb（Big-Endian）
                for (int i = 0; i < 8; i++) {
                    msb = (msb << 8) | (bytes[i] & 0xff);
                }
                // 后8字节为 lsb（Big-Endian）
                for (int i = 8; i < 16; i++) {
                    lsb = (lsb << 8) | (bytes[i] & 0xff);
                }
                return new UUID(msb, lsb);
            }

            return null;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 解析异常（格式不合法、Base64 解码失败等）
            return null;
        }
    }

    /**
     * 检查任务 ID 格式是否有效
     *
     * @param input 任务 ID 字符串
     * @return 有效返回 true
     */
    public static boolean isValidFormat(String input) {
        return input != null && !input.isEmpty() && parse(input) != null;
    }
}
