package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import appeng.core.worlddata.WorldData;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;

/** AE's numeric node player ID is distinct from UUID ownership stored by other devices. */
public final class HologramBlueprintOwnership {

    private static Field proxyNbtName;

    private HologramBlueprintOwnership() {}

    /** Return a detached payload. Other AE node fields, including security/storage IDs, stay unchanged. */
    public static NBTTagCompound prepare(TileEntity target, NBTTagCompound data, EntityPlayerMP actor)
        throws IOException {
        if (!(target instanceof IGridProxyable)) return data;
        AENetworkProxy proxy = ((IGridProxyable) target).getProxy();
        if (proxy == null) return data;
        if (actor == null || actor.getGameProfile() == null
            || !actor.getGameProfile()
                .isComplete())
            throw new IOException("AE 蓝图所有权需要有效的当前玩家身份");
        String root = nodeKey(proxy);
        int playerId = WorldData.instance()
            .playerData()
            .getPlayerID(actor.getGameProfile());
        if (playerId < 0) throw new IOException("AE 当前玩家数字身份无效");
        NBTTagCompound prepared;
        if (data == null) {
            prepared = new NBTTagCompound();
            target.writeToNBT(prepared);
        } else prepared = (NBTTagCompound) data.copy();
        NBTTagCompound node = (NBTTagCompound) prepared.getCompoundTag(root)
            .copy();
        node.setInteger("p", playerId);
        prepared.setTag(root, node);
        return prepared;
    }

    /** AE 1080 declares this as a private final String; fail explicitly if the contract changes. */
    static String nodeKey(AENetworkProxy proxy) throws IOException {
        try {
            if (proxyNbtName == null) {
                Field field = AENetworkProxy.class.getDeclaredField("nbtName");
                if (field.getType() != String.class || Modifier.isStatic(field.getModifiers()))
                    throw new IOException("AE 代理 NBT 名称字段类型已变化");
                field.setAccessible(true);
                proxyNbtName = field;
            }
            String name = (String) proxyNbtName.get(proxy);
            if (name == null || name.isEmpty() || name.length() > 128) throw new IOException("AE 代理 NBT 名称无效");
            return name;
        } catch (ReflectiveOperationException | SecurityException failure) {
            throw new IOException("无法确认 AE 代理的所有权存储位置", failure);
        }
    }
}
