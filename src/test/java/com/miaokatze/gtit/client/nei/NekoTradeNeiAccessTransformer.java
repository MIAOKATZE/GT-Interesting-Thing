// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.client.nei;

import java.io.IOException;

import com.google.common.io.CharSource;

import cpw.mods.fml.common.asm.transformers.AccessTransformer;

/** MCP-name equivalents of NEI's nei_at.cfg NBT rules for the headless Forge test loader. */
public final class NekoTradeNeiAccessTransformer extends AccessTransformer {

    public NekoTradeNeiAccessTransformer() throws IOException {
        super();
        processATFile(
            CharSource
                .wrap("public net.minecraft.nbt.NBTTagCompound tagMap\npublic net.minecraft.nbt.NBTTagList tagList\n"));
    }
}
