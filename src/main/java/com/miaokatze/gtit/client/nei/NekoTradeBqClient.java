package com.miaokatze.gtit.client.nei;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.GuiQuest;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;

/** The BQ backend is resolved only after the optional mod gate; common/server classes never reference it. */
public final class NekoTradeBqClient {

    private NekoTradeBqClient() {}

    public static String title(UUID id) {
        if (id == null) return StatCollector.translateToLocal("gtit.nei.trade.quest_none");
        if (!Loader.isModLoaded("betterquesting")) {
            return StatCollector.translateToLocal("gtit.nei.trade.quest_unavailable");
        }
        try {
            return Backend.title(id);
        } catch (RuntimeException | LinkageError ignored) {
            return StatCollector.translateToLocal("gtit.nei.trade.quest_unavailable");
        }
    }

    public static boolean open(UUID id) {
        if (id == null || !Loader.isModLoaded("betterquesting")) return false;
        try {
            return Backend.open(id);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean canOpen(UUID id) {
        if (id == null || !Loader.isModLoaded("betterquesting")) return false;
        try {
            return Backend.exists(id);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static final class Backend {

        @Optional.Method(modid = "betterquesting")
        static String title(UUID id) {
            IQuest quest = QuestDatabase.INSTANCE.get(id);
            return quest == null ? StatCollector.translateToLocal("gtit.nei.trade.quest_missing")
                : QuestTranslation.translateQuestName(id, quest);
        }

        @Optional.Method(modid = "betterquesting")
        static boolean exists(UUID id) {
            return QuestDatabase.INSTANCE.get(id) != null;
        }

        @Optional.Method(modid = "betterquesting")
        static boolean open(UUID id) {
            if (QuestDatabase.INSTANCE.get(id) == null) return false;
            GuiScreen previous = Minecraft.getMinecraft().currentScreen;
            GuiQuest gui = new GuiQuest(previous, id);
            gui.setPreviousScreen(previous);
            Minecraft.getMinecraft()
                .displayGuiScreen(gui);
            return true;
        }
    }
}
