package com.miaokatze.gtit.common.machine.neko;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 猫猫售货机 BGM 事件处理器
 * <p>
 * 替代原版 VM 的 VMMusicManager，为猫猫售货机播放自定义 BGM。
 * NekoVendingMachineGui 的 build() 方法已经不调用 VMMusicManager，
 * 所以原版 BGM 不会被播放。本处理器负责播放猫猫 BGM。
 * <p>
 * 使用静态标志位 {@link NekoVendingMachineGui#isNekoGuiOpen} 检测 GUI 状态，
 * 在 GUI 打开时播放 BGM，关闭时停止。
 * <p>
 * 注意：实际的 BGM 音频文件（neko_theme.ogg）需要用户提供。
 * 如果缺少该文件，BGM 不会播放但不会崩溃（try-catch 处理了）。
 */
@SideOnly(Side.CLIENT)
public class NekoMusicEventHandler {

    // 猫猫 BGM 资源路径（对应 sounds.json 中的 track.neko_theme）
    private static final ResourceLocation NEKO_BGM = new ResourceLocation("gtit", "track.neko_theme");

    // 当前正在播放的 BGM 声音实例（保存引用以便停止）
    private ISound currentSound = null;

    // 上一次 GUI 是否打开
    private boolean wasOpen = false;

    // tick 计数器（避免每 tick 都检测）
    private int tickCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        // 每 20 tick 检测一次（1 秒）
        if (tickCounter % 20 != 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        boolean isOpen = NekoVendingMachineGui.isNekoGuiOpen;

        if (isOpen && !wasOpen) {
            // GUI 刚打开，播放 BGM
            startNekoBGM(mc);
        } else if (!isOpen && wasOpen && currentSound != null) {
            // GUI 刚关闭，停止 BGM
            stopNekoBGM(mc);
        }

        wasOpen = isOpen;
    }

    /**
     * 开始播放猫猫 BGM
     */
    private void startNekoBGM(Minecraft mc) {
        if (currentSound != null) {
            stopNekoBGM(mc);
        }
        try {
            currentSound = PositionedSoundRecord.func_147673_a(NEKO_BGM);
            mc.getSoundHandler()
                .playSound(currentSound);
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("无法播放猫猫售货机 BGM: {}", e.getMessage());
            currentSound = null;
        }
    }

    /**
     * 停止播放猫猫 BGM
     */
    private void stopNekoBGM(Minecraft mc) {
        if (currentSound != null) {
            try {
                mc.getSoundHandler()
                    .stopSound(currentSound);
            } catch (Exception e) {
                // 静默忽略
            }
            currentSound = null;
        }
    }
}
