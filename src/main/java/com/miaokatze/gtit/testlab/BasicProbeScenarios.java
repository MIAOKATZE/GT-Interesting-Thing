package com.miaokatze.gtit.testlab;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

final class BasicProbeScenarios {

    private BasicProbeScenarios() {}

    static void register() {
        TestLabRuntime.register(new ProbeScenario() {

            @Override
            public String id() {
                return "harness-smoke";
            }

            @Override
            public ProbeTask start(ProbeContext context) {
                return new ProbeTask() {

                    int step;

                    @Override
                    public boolean tick() {
                        if (step++ == 0) {
                            context.check(
                                "dimension",
                                TestLabDimension.isLab(context.world()),
                                "Dedicated provider identity");
                            context.check(
                                "place",
                                context.setBlock(0, 0, 0, Blocks.quartz_block, 0),
                                "Owned fixture in void canvas");
                            context.check(
                                "immediate-readback",
                                context.world()
                                    .getBlock(context.x(0), context.y(0), context.z(0)) == Blocks.quartz_block,
                                "Successful placement must update a real loaded chunk immediately");
                            context.check(
                                "loaded-chunk",
                                !(context.world()
                                    .getChunkFromBlockCoords(
                                        context.x(0),
                                        context.z(0)) instanceof net.minecraft.world.chunk.EmptyChunk),
                                "Unattended fixture uses an actual server chunk, never EmptyChunk");
                            ItemStack stack = context.mark(new ItemStack(Items.stick));
                            context.check(
                                "test-marker",
                                stack.getTagCompound()
                                    .getCompoundTag(ProbeContext.TEST_ITEM_TAG)
                                    .getBoolean("testOnly"),
                                "Special NBT marker");
                            context.check(
                                "actors",
                                !context.player("owner")
                                    .getUniqueID()
                                    .equals(
                                        context.player("outsider")
                                            .getUniqueID()),
                                "Isolated synthetic identities");
                            return false;
                        }
                        context.check(
                            "tick-readback",
                            context.world()
                                .getBlock(context.x(0), context.y(0), context.z(0)) == Blocks.quartz_block,
                            "Fixture survives a real world tick");
                        context.skip(
                            "client-ui",
                            "Use explicit /gtitlab ui pocket with an opted-in test client; server execution cannot verify UI");
                        return true;
                    }
                };
            }
        });
        TestLabRuntime.register(new ProbeScenario() {

            @Override
            public String id() {
                return "harness-stress";
            }

            @Override
            public boolean stress() {
                return true;
            }

            @Override
            public ProbeTask start(ProbeContext context) {
                return new ProbeTask() {

                    int cursor;
                    int successfulPlacements;
                    int matchingReadbacks;

                    @Override
                    public boolean tick() {
                        // Sixteen placements per tick, separated by over one million blocks from the smoke canvas.
                        if (cursor < 256) {
                            for (int i = 0; i < 16 && cursor < 256; i++, cursor++)
                                if (context.setBlock(cursor % 16, 0, cursor / 16, Blocks.quartz_block, 0))
                                    successfulPlacements++;
                            return false;
                        }
                        // Read sixteen positions per later tick. Every one must survive the native world update.
                        for (int i = 0; i < 16 && cursor < 512; i++, cursor++) {
                            int index = cursor - 256;
                            int x = context.x(index % 16), y = context.y(0), z = context.z(index / 16);
                            if (context.world()
                                .getBlock(x, y, z) == Blocks.quartz_block
                                && context.world()
                                    .getBlockMetadata(x, y, z) == 0)
                                matchingReadbacks++;
                        }
                        if (cursor != 512) return false;
                        context.check(
                            "stress-place-all",
                            successfulPlacements == 256,
                            "Successful placements=" + successfulPlacements + "/256");
                        context.check(
                            "stress-readback-all",
                            matchingReadbacks == 256,
                            "Real block/meta readbacks=" + matchingReadbacks + "/256 across later world ticks");
                        context.check("isolated-canvas", context.x(0) == 1048576, "Stress canvas origin X=1048576");
                        context.metric("fixtureBlocks", successfulPlacements);
                        context.metric("matchingReadbacks", matchingReadbacks);
                        return true;
                    }
                };
            }
        });
    }
}
