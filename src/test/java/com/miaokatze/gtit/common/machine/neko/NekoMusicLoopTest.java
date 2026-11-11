// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.machine.neko;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;

/** Verify the native loop contract without starting an audio device or a game client. */
public final class NekoMusicLoopTest {

    private NekoMusicLoopTest() {}

    public static void main(String[] args) {
        ISound sound = NekoMusicEventHandler.createLoopingMusic();
        ISound previous = PositionedSoundRecord.func_147673_a(NekoMusicEventHandler.NEKO_BGM);
        check(sound instanceof PositionedSoundRecord, "existing source-capture Mixin recognizes the sound");
        check(sound.canRepeat(), "native streaming source receives loop=true");
        check(sound.getRepeatDelay() == 0, "native loop has no scheduled delayed replay");
        check(
            sound.getPositionedSoundLocation()
                .equals(previous.getPositionedSoundLocation()),
            "random track event retained");
        check(sound.getAttenuationType() == ISound.AttenuationType.NONE, "GUI music has no distance attenuation");
        check(sound.getVolume() == previous.getVolume(), "configured volume ownership retained");
        check(sound.getPitch() == previous.getPitch(), "playback speed retained");
        check(sound.getXPosF() == 0 && sound.getYPosF() == 0 && sound.getZPosF() == 0, "GUI sound origin retained");
        check(sound != NekoMusicEventHandler.createLoopingMusic(), "each GUI opening owns a distinct stoppable source");
        System.out.println("NekoMusicLoopTest: 9 native sound contracts passed; no game or audio device started");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
