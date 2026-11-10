package com.miaokatze.gtit.testlab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.world.World;

import com.miaokatze.miaokatzecore.api.testlab.LabCase;
import com.miaokatze.miaokatzecore.api.testlab.LabModule;

/** Adapts the existing native scenario scheduler to the shared command and environment driver. */
public final class TestLabModule implements LabModule {

    @Override
    public String id() {
        return "gtit";
    }

    @Override
    public boolean enabled() {
        return TestLabConfig.enabled && TestLabDimension.problem() == null;
    }

    @Override
    public List<LabCase> cases() {
        List<LabCase> result = new ArrayList<>();
        for (ProbeScenario scenario : TestLabRuntime.scenarios()) {
            String id = scenario.id();
            boolean ui = id.startsWith("client-ui-");
            boolean host = id.startsWith("native-gift-host-");
            LinkedHashSet<String> tags = new LinkedHashSet<>();
            tags.add(scenario.stress() ? "stress" : scenario.defaultEnabled() ? "suite" : "explicit");
            if (ui) tags.addAll(Arrays.asList("dedicated-test-client", "empty-inventory", "client-ui-opt-in"));
            if (id.equals("client-ui-vending")) tags.add("singleplayer");
            if (host) tags.addAll(Arrays.asList("integrated-host", "cross-jvm", "retains-real-reward"));
            if (id.startsWith("native-persistence-")) tags.add("cross-jvm");
            if (id.equals("native-remote-vending-team")) tags.add("empty-server-native-sessions");
            result.add(
                new LabCase(
                    id,
                    id,
                    ui ? "client-server" : host ? "client-server" : "server",
                    tags,
                    scenario.defaultEnabled() && !scenario.stress(),
                    ui || host));
        }
        return result;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        execute(sender, args, null);
    }

    @Override
    public void execute(ICommandSender sender, String[] args, String requestId) {
        String[] command = args.clone();
        if (command.length > 0 && "report".equals(command[0])) command[0] = "status";
        if (command.length > 0 && "run".equals(command[0])) {
            if (command.length == 1) command = new String[] { "suite" };
            else if ("all".equals(command[1])) {
                if (command.length != 2) throw new IllegalArgumentException("run all accepts no additional arguments");
                command = new String[] { "suite" };
            } else if (command.length == 2 && sender instanceof net.minecraft.entity.player.EntityPlayerMP
                && Arrays.asList("client-ui-pocket", "client-ui-vending")
                    .contains(command[1]))
                command = new String[] { "ui", "client-ui-pocket".equals(command[1]) ? "pocket" : "vending" };
            else if (Arrays.asList("suite", "stress", "profile")
                .contains(command[1])) command = Arrays.copyOfRange(command, 1, command.length);
        }
        if (requestId != null) {
            command = Arrays.copyOf(command, command.length + 2);
            command[command.length - 2] = "--request";
            command[command.length - 1] = requestId;
        }
        new TestLabCommand().executeStrict(sender, command);
    }

    @Override
    public String status() {
        return TestLabRuntime.INSTANCE.status();
    }

    @Override
    public java.util.Map<String, Object> snapshot() {
        return TestLabRuntime.INSTANCE.snapshot();
    }

    @Override
    public boolean ownsWorld(World world) {
        return enabled() && TestLabDimension.isLab(world)
            && net.minecraftforge.common.DimensionManager.getWorld(TestLabConfig.dimensionId) == world;
    }
}
