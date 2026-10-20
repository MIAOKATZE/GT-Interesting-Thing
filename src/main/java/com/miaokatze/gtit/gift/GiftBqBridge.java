package com.miaokatze.gtit.gift;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;

import com.miaokatze.gtit.trade.v2.NekoBqQuestIdParser;

import betterquesting.api.events.QuestEvent;
import betterquesting.api.questing.IQuest;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.party.PartyManager;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** BQ is optional. Missing mods, quests or query errors always leave gifts locked. */
public final class GiftBqBridge {

    private static boolean registered;

    private GiftBqBridge() {}

    public static void register() {
        if (!registered && Loader.isModLoaded("betterquesting")) {
            MinecraftForge.EVENT_BUS.register(new GiftBqBridge());
            registered = true;
        }
    }

    public static boolean isComplete(UUID player, String questId) {
        if (!Loader.isModLoaded("betterquesting")) return false;
        UUID parsed = NekoBqQuestIdParser.parse(questId);
        if (parsed == null) return false;
        try {
            return query(player, parsed);
        } catch (RuntimeException | LinkageError e) {
            LogManager.getLogger("gtit")
                .debug("Gift BQ completion check failed: {} / {}", player, questId, e);
            return false;
        }
    }

    @Optional.Method(modid = "betterquesting")
    private static boolean query(UUID player, UUID quest) {
        IQuest value = QuestDatabase.INSTANCE.get(quest);
        return value != null && value.isComplete(player);
    }

    @SubscribeEvent
    @Optional.Method(modid = "betterquesting")
    public void onQuestEvent(QuestEvent event) {
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient() || !GiftService.INSTANCE.isReady()
            || event.getType() != QuestEvent.Type.COMPLETED
            || event.getPlayerID() == null) return;
        Set<UUID> members = new LinkedHashSet<>();
        members.add(event.getPlayerID());
        var party = PartyManager.INSTANCE.getParty(event.getPlayerID());
        if (party != null) members.addAll(
            party.getValue()
                .getMembers());
        // Copy now, not at a later login: later party membership must not change the recipients.
        for (UUID quest : event.getQuestIDs())
            GiftService.INSTANCE.onQuestCompleted(event.getPlayerID(), members, quest);
    }
}
