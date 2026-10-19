package com.miaokatze.gtit.testlab;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Explicit console/OP automation commands; no real player is ever teleported. */
public final class TestLabCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtitlab";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtitlab help|status|reset|refresh|run <id>|suite|stress [iterations]|profile [id] [iterations]|ui pocket";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && ("help".equals(args[0]) || "tutorial".equals(args[0]))) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            sender.addChatMessage(
                new ChatComponentText(
                    "Enabled test lab only. reset/refresh affect owned fixtures; ui pocket requires an online OP test player."));
            return;
        }
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        String requestId = null;
        try {
            for (int i = 0; i < args.length; i++) {
                if (!"--request".equals(args[i])) continue;
                if (i != args.length - 2) throw new IllegalArgumentException("--request <UUID> must be the final pair");
                requestId = args[i + 1];
                if (!java.util.UUID.fromString(requestId)
                    .toString()
                    .equals(requestId)) throw new IllegalArgumentException("Invalid request UUID");
                args = java.util.Arrays.copyOf(args, i);
                break;
            }
            if (args.length == 0) throw new IllegalArgumentException(getCommandUsage(sender));
            int maximumArgs = switch (args[0]) {
                case "status", "reset", "refresh", "suite" -> 1;
                case "run", "stress", "ui" -> 2;
                case "profile" -> 3;
                default -> 0;
            };
            if (args.length > maximumArgs) throw new IllegalArgumentException(getCommandUsage(sender));
            String result;
            switch (args[0]) {
                case "status" -> result = TestLabRuntime.INSTANCE.status();
                case "reset", "refresh" -> {
                    TestLabRuntime.INSTANCE.reset("refresh".equals(args[0]), requestId);
                    result = "Owned-fixture reset scheduled";
                }
                case "suite" -> result = "run=" + TestLabRuntime.INSTANCE.run("suite", null, 1, requestId);
                case "run" -> {
                    if (args.length != 2) throw new IllegalArgumentException("run requires one scenario id");
                    result = "run=" + TestLabRuntime.INSTANCE.run("run", args[1], 1, requestId);
                }
                case "stress" -> result = "run=" + TestLabRuntime.INSTANCE
                    .run("stress", null, args.length > 1 ? Integer.parseInt(args[1]) : 1, requestId);
                case "profile" -> {
                    String id = args.length > 1 && !args[1].matches("[0-9]+") ? args[1] : null;
                    int countIndex = id == null ? 1 : 2;
                    if (args.length > countIndex + 1) throw new IllegalArgumentException("profile [id] [iterations]");
                    int count = args.length > countIndex ? Integer.parseInt(args[countIndex]) : 5;
                    result = "run=" + TestLabRuntime.INSTANCE.run("profile", id, count, requestId);
                }
                case "ui" -> {
                    if (!(sender instanceof EntityPlayerMP player) || args.length != 2 || !"pocket".equals(args[1]))
                        throw new IllegalArgumentException("ui pocket requires an online OP test client");
                    result = "run=" + TestLabUiNetwork.run(player);
                }
                default -> throw new IllegalArgumentException(getCommandUsage(sender));
            }
            sender.addChatMessage(new ChatComponentText("[GTIT-LAB] " + result));
        } catch (IllegalArgumentException | IllegalStateException error) {
            String rejection = "[GTIT-LAB] REJECT request=" + (requestId == null ? "-" : requestId)
                + " reason="
                + error.getMessage();
            com.miaokatze.gtit.main.GTInterestingThing.LOG.warn(rejection);
            sender.addChatMessage(new ChatComponentText(rejection));
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(
            args,
            "status",
            "reset",
            "refresh",
            "run",
            "suite",
            "stress",
            "profile",
            "ui",
            "help",
            "tutorial");
        if (args.length == 2 && "ui".equals(args[0])) return getListOfStringsMatchingLastWord(args, "pocket");
        if (args.length == 2 && ("run".equals(args[0]) || "profile".equals(args[0])))
            return getListOfStringsMatchingLastWord(
                args,
                TestLabRuntime.scenarioIds()
                    .toArray(new String[0]));
        return null;
    }
}
