package com.miaokatze.gtit.mixin;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.audio.SoundPoolEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.miaokatze.gtit.common.machine.neko.NekoMusicEventHandler;

/**
 * Mixin for SoundManager to capture neko BGM sound source name.
 * <p>
 * When our neko BGM is played via SoundHandler.playSound(), this Mixin
 * intercepts the setVolume call inside SoundManager.playSound() and
 * captures the sound source name (e.g., "snd_001"). This source name
 * is needed for per-frame volume control via SoundSystem.setVolume().
 * <p>
 * This is the same injection point used by VM's SoundManagerMixin,
 * but we only capture sounds matching our neko BGM ResourceLocation.
 */
@Mixin(SoundManager.class)
public class NekoSoundManagerMixin {

    @Inject(
        method = { "playSound" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/audio/SoundManager$SoundSystemStarterThread;setVolume(Ljava/lang/String;F)V",
            shift = At.Shift.AFTER,
            remap = false))
    private static void neko$onPlaySound(ISound sound, CallbackInfo ci, @Local String sourceName,
        @Local SoundCategory category, @Local SoundPoolEntry entry) {
        // Only capture sounds that match our neko BGM
        if (sound instanceof PositionedSoundRecord) {
            if (sound.getPositionedSoundLocation()
                .equals(NekoMusicEventHandler.NEKO_BGM)) {
                NekoMusicEventHandler.onSoundCreated(sourceName, sound, entry, category);
            }
        }
    }
}
