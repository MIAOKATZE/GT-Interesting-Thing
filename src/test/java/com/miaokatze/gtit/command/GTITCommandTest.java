package com.miaokatze.gtit.command;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.miaokatze.gtit.gift.GiftDefinition;
import com.miaokatze.gtit.gift.GiftRegistry;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Exercises the actual parser, completion positions and non-player mutation boundary. */
public final class GTITCommandTest {

    private GTITCommandTest() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(GTITCommandTest.class, args)) return;
        // The real server handler installs FML's sided delegate during singleton construction.
        cpw.mods.fml.server.FMLServerHandler.instance();
        check(
            cpw.mods.fml.common.FMLCommonHandler.instance()
                .getSide() == cpw.mods.fml.relauncher.Side.SERVER,
            "Command completion requires the actual physical server-side FML fixture");
        GTITGiftCommand command = new GTITGiftCommand();
        Sender sender = new Sender();
        List<String> root = command.addTabCompletionOptions(sender, new String[] { "" });
        check(
            root.contains("presets") && root.contains("help") && root.contains("tutorial"),
            "Root completion must expose the working management and teaching entry points");
        check(!root.contains("terminal"), "Physical dedicated servers must not advertise the client-only terminal");
        check(
            !root.contains("trade") && !root.contains("achievement"),
            "Unregistered historical or unfinished command trees must not be taught");
        check(
            command.addTabCompletionOptions(sender, new String[] { "nekovm", "def" })
                .contains("defultreset"),
            "Completion must retain the exact reset spelling requested by the user");
        List<String> giftOptions = command.addTabCompletionOptions(sender, new String[] { "gift", "" });
        check(
            giftOptions.containsAll(Arrays.asList("reset", "claimlist", "claimreset", "help", "tutorial"))
                && !giftOptions.contains("certain")
                && !giftOptions.contains("random")
                && !giftOptions.contains("ui"),
            "Gift completion must retain management commands and omit removed execution routes");
        for (String[] request : new String[][] { { "gift", "certain", "" }, { "gift", "random", "" },
            { "gift", "random", "2", "" } }) {
            check(
                command.addTabCompletionOptions(sender, request) == null,
                "Removed gift routes must not suggest inventory counts or NBT flags");
        }
        check(
            command.addTabCompletionOptions(sender, new String[] { "signin", "admin", "unknown", "" }) == null,
            "An invalid sign-in admin verb must not be completed as a valid player argument");
        sender.allowed = false;
        check(
            command.getRequiredPermissionLevel() == 2 && !command.canCommandSenderUseCommand(sender),
            "The existing OP2 root command boundary must remain intact");
        sender.allowed = true;
        check(command.canCommandSenderUseCommand(sender), "Authorized command senders retain access to help");
        for (String[] request : new String[][] { { "help", "nekovm" }, { "tutorial", "presets" }, { "gift", "help" },
            { "nekovm", "help" }, { "signin", "tutorial" }, { "mail", "help" } }) {
            sender.messages.clear();
            command.processCommand(sender, request);
            check(!sender.messages.isEmpty(), "Read-only help must respond without a player or running world");
        }
        sender.messages.clear();
        command.processCommand(sender, new String[] { "gift" });
        List<String> giftHelp = new ArrayList<>(sender.messages);
        for (String removed : new String[] { "certain", "random", "ui" }) {
            sender.messages.clear();
            command.processCommand(sender, new String[] { "gift", removed });
            check(
                sender.messages.equals(giftHelp),
                "Removed gift routes must fall back to help without entering their former player-only handlers");
        }
        sender.messages.clear();
        command.processCommand(sender, new String[] { "signin", "help" });
        List<String> signInHelp = new ArrayList<>(sender.messages);
        sender.messages.clear();
        command.processCommand(sender, new String[] { "signin" });
        check(sender.messages.equals(signInHelp), "Bare sign-in must show help without executing a sign-in");
        for (String alias : new String[] { "defultreset", "defaultreset" }) {
            sender.messages.clear();
            command.processCommand(sender, new String[] { "nekovm", alias });
            check(
                sender.messages.size() == 1 && sender.messages.get(0)
                    .contains("只有玩家"),
                "Both reset routes must reject non-player senders before touching core state");
        }
        GiftLibraryCommand gifts = new GiftLibraryCommand();
        check(
            gifts.addTabCompletionOptions(sender, new String[] { "" })
                .containsAll(Arrays.asList("ui", "claim", "help", "tutorial")),
            "The player gift library must retain all of its public routes");
        sender.messages.clear();
        gifts.processCommand(sender, new String[] { "help" });
        check(
            gifts.getRequiredPermissionLevel() == 0 && sender.messages.size() == 2,
            "Player gift help must work before the service is ready and without administrator permission");
        checkGiftCompletions(gifts, sender);
        System.out.println(
            "GTITCommandTest: removed command fallbacks, retained completions, help/tutorial routes, OP2 boundary, reset aliases and gift claim filtering passed");
    }

    private static void checkGiftCompletions(GiftLibraryCommand command, Sender sender) throws Exception {
        GiftRegistry.INSTANCE.init(
            Files.createTempDirectory("gtit-command-gifts-")
                .resolve("gifts.json"));
        for (String id : new String[] { "cmd_manual", "cmd_automatic", "cmd_disabled" }) {
            GiftDefinition gift = new GiftDefinition();
            gift.id = id;
            gift.name = id;
            gift.trigger = "cmd_automatic".equals(id) ? GiftDefinition.Trigger.FIRST_LOGIN
                : GiftDefinition.Trigger.BQ_UNLOCK;
            gift.questId = "00000000-0000-0000-0000-000000000001";
            gift.enabled = !"cmd_disabled".equals(id);
            GiftRegistry.INSTANCE.save(gift, 0);
        }
        List<String> claims = command.addTabCompletionOptions(sender, new String[] { "claim", "cmd_" });
        check(
            claims.size() == 1 && claims.contains("cmd_manual"),
            "Claim completion must omit automatic and disabled gifts while retaining manual unlock gifts");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Sender implements ICommandSender {

        final List<String> messages = new ArrayList<>();
        boolean allowed = true;

        @Override
        public String getCommandSenderName() {
            return "CommandTest";
        }

        @Override
        public IChatComponent func_145748_c_() {
            return new net.minecraft.util.ChatComponentText(getCommandSenderName());
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            messages.add(message.getUnformattedText());
        }

        @Override
        public boolean canCommandSenderUseCommand(int level, String name) {
            return allowed && level <= 2;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 0, 0);
        }

        @Override
        public World getEntityWorld() {
            return null;
        }
    }
}
