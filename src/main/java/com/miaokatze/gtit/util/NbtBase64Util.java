package com.miaokatze.gtit.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NBT 与 Base64 编解码工具类
 * <p>
 * 提供统一的 NBTTagCompound 与 Base64 字符串互转能力，供新手礼包、猫猫交易机等模块复用。
 * 使用 Minecraft 的 {@link CompressedStreamTools} 压缩 NBT 数据，再通过 Base64 编码为字符串，
 * 便于在 JSON 配置中存储任意 NBT 数据。
 */
public class NbtBase64Util {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /**
     * 将 NBTTagCompound 序列化为 Base64 字符串。
     *
     * @param nbt 待序列化的 NBT 数据；为 null 时返回 null
     * @return Base64 编码后的字符串；序列化失败时返回 null
     */
    public static String nbtToBase64(NBTTagCompound nbt) {
        if (nbt == null) {
            return null;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            CompressedStreamTools.write(nbt, dos);
            dos.close();
            return Base64.getEncoder()
                .encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOG.error("NbtBase64Util: NBT 序列化为 Base64 失败", e);
            return null;
        }
    }

    /**
     * 从 Base64 字符串反序列化为 NBTTagCompound。
     *
     * @param base64 Base64 编码的字符串；为 null 或空字符串时返回 null
     * @return 反序列化后的 NBT 数据；解码失败时返回 null
     */
    public static NBTTagCompound nbtFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder()
                .decode(base64);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            NBTTagCompound nbt = CompressedStreamTools.read(dis);
            dis.close();
            return nbt;
        } catch (Exception e) {
            LOG.error("NbtBase64Util: Base64 反序列化为 NBT 失败 [base64={}]", base64, e);
            return null;
        }
    }
}
