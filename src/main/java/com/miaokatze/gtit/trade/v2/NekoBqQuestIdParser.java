// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.trade.v2;

import java.nio.ByteBuffer;
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
        if (input == null) return null;
        String value = input.trim();

        try {
            int separator = value.indexOf(':');
            if (separator >= 0) {
                return new UUID(
                    Long.parseLong(
                        value.substring(0, separator)
                            .trim()),
                    Long.parseLong(
                        value.substring(separator + 1)
                            .trim()));
            }

            // UUID.fromString also accepts abbreviated UUIDs, which can look like an unpadded BQ ID.
            UUID compact = parseCompact(value);
            if (compact != null) return compact;
            return value.contains("-") ? UUID.fromString(value) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID parseCompact(String value) {
        if (value.length() < 22 || value.length() > 24) return null;
        // Accept an existing ID with only one of its two padding characters.
        if (value.length() == 23 && value.endsWith("=")) value += "=";
        Base64.Decoder decoder = value.contains("-") || value.contains("_") ? Base64.getUrlDecoder()
            : Base64.getDecoder();
        try {
            byte[] bytes = decoder.decode(value);
            if (bytes.length != 16) return null;
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (IllegalArgumentException e) {
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
