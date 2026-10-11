package com.miaokatze.gtit.client.nei;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtit.Tags;
import com.miaokatze.gtit.common.api.enums.GTITItemList;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.recipe.HandlerInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Discovered by NEI's native NEI*Config scanner; do not call loadConfig from ClientProxy. */
public final class NEIGtitConfig implements IConfigureNEI {

    private static boolean registered;
    private static boolean eventsRegistered;

    /** HandlerInfo is requested before native loadConfig; install its listener during client init. */
    public static void registerHandlerInfoEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;
        MinecraftForge.EVENT_BUS.register(new NEIGtitConfig());
    }

    @Override
    public void loadConfig() {
        if (registered) return;
        registered = true;
        NekoTradeRecipeHandler handler = new NekoTradeRecipeHandler();
        API.registerRecipeHandler(handler);
        API.registerUsageHandler(handler);
        API.addRecipeCatalyst(GTITItemList.NekoVendingMachine.get(1), NekoTradeRecipeHandler.ID);
        registerHandlerInfoEvents();
    }

    @Override
    public String getName() {
        return "GT Interesting Thing";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }

    @SubscribeEvent
    public void registerHandlerInfo(NEIRegisterHandlerInfosEvent event) {
        ItemStack machine = GTITItemList.NekoVendingMachine.get(1);
        event.registerHandlerInfo(
            new HandlerInfo.Builder(NekoTradeRecipeHandler.ID, getName(), "gtit")
                .setHeight(NekoTradeRecipeHandler.HEIGHT)
                .setWidth(166)
                .setMaxRecipesPerPage(2)
                .setDisplayStack(machine)
                .build());
    }
}
