package com.miaokatze.gtit.trade.v2;

import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;

import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoTradeConfig;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 交易配置全量同步包（服务端→客户端，v1.7.0 目标 5）
 * <p>
 * 携带服务端权威的交易配置（{@code config/gtit/nekovm_trades.json}）与标签页配置
 * （{@code config/gtit/nekovm_pages.json}）的完整 JSON 文本。
 * 玩家登录、配置编辑保存、/gtit nekovm reload、/gtit nekovm sync 后由服务端推送，
 * 客户端接收后刷新 {@link NekoTradeDatabase} 与 {@link NekoPageRegistry} 的内存数据
 * （<b>仅内存，不写盘</b>——配置修改默认只在服务端进行）。
 * <p>
 * JSON 以「长度前缀 + UTF-8 字节数组」编码，避开 {@code ByteBufUtils.writeUTF8String}
 * 的 32767 字符上限（交易配置随条目增多可能超过该上限）。
 */
public class NekoTradeSyncPacket implements IMessage {

    /** 交易配置 JSON（{@link NekoTradeConfig#toJson} 产物） */
    private String tradesJson = "";
    /** 标签页配置 JSON（{@link NekoPageConfig#toJson} 产物） */
    private String pagesJson = "";

    public NekoTradeSyncPacket() {
        // 反序列化需要无参构造
    }

    public NekoTradeSyncPacket(String tradesJson, String pagesJson) {
        this.tradesJson = tradesJson == null ? "" : tradesJson;
        this.pagesJson = pagesJson == null ? "" : pagesJson;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.tradesJson = readString(buf);
        this.pagesJson = readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, this.tradesJson);
        writeString(buf, this.pagesJson);
    }

    public String getTradesJson() {
        return tradesJson;
    }

    public String getPagesJson() {
        return pagesJson;
    }

    /** 写入「长度前缀 + UTF-8 字节」编码的字符串 */
    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    /** 读取「长度前缀 + UTF-8 字节」编码的字符串（带长度防御，异常时回退空串） */
    private static String readString(ByteBuf buf) {
        int len = buf.readInt();
        if (len <= 0) return "";
        // 防御：限制单次读取上限（16MB），避免畸形包撑爆内存
        if (len > 16 * 1024 * 1024) {
            buf.skipBytes(len);
            return "";
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<NekoTradeSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(NekoTradeSyncPacket message, MessageContext ctx) {
            // 本包只发往客户端；1.7.10 的 onMessage 运行在 Netty 线程，
            // 需切回客户端主线程再刷新注册表（GUI 在主线程读取）
            if (ctx.side == Side.CLIENT) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(final NekoTradeSyncPacket message) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    // 单人存档：集成服务端与客户端共享静态注册表，
                    // 服务端侧的 initialize/reload 已直接刷新同一份数据，无需重复应用
                    // （同时避免客户端线程清空数据库与服务端线程交易执行的并发窗口）。
                    if (Minecraft.getMinecraft()
                        .isSingleplayer()) {
                        return;
                    }
                    try {
                        // 先页签后交易：交易分类按 tabId 映射，页签先就绪保证分类一致
                        NekoPageRegistry.applySyncedPages(NekoPageConfig.fromJson(message.getPagesJson()));
                        NekoTradeRegistryV2.applySyncedTrades(NekoTradeConfig.fromJson(message.getTradesJson()));
                        GTInterestingThing.LOG.info("[NekoSync] 已应用服务端交易/标签页配置同步");
                    } catch (Throwable t) {
                        GTInterestingThing.LOG.error("[NekoSync] 应用交易配置同步失败", t);
                    }
                });
        }
    }
}
