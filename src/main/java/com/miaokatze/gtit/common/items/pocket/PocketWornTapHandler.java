package com.miaokatze.gtit.common.items.pocket;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.miaokatze.gtit.main.GTInterestingThing;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * 穿戴口袋时 Alt + 空手右击 GT 机器的服务端入口。
 * <p>
 * 客户端在 Forge MouseEvent 中取消原版 use，再发一枚专用 C2S 请求，避免同次点击打开机器 GUI。
 * 此处不订阅 PlayerInteractEvent：普通右击和 Shift + 空手右击保持机器交互。
 * <p>
 * 手持口袋抽液仍走
 * {@code ItemNekoDimensionPocket#onItemUseFirst}（潜行 + 手持右击），那两段的源码文本
 * 保持原样。两条腿<b>互斥</b>：
 * 本腿的硬门是 {@code getCurrentEquippedItem() == null}（空手），手持口袋时本腿一个字都不做。
 * <p>
 * <b>空手这道硬门不是保守，是避撞</b>：GT 的机器方块对"手持螺丝刀/扳手一类工具"的右击有自己的
 * 白名单语义（{@code BlockMachines.java:412-419}），潜行 + 手持工具是玩家配置机器的常规手势；
 * 本腿若放过任何手持物，就会把那些手势按下去顺手掏空机器。用例钉这一条。
 * <p>
 * C2S 只表达点击意图。实际装备、世界、目标、距离、视线和方块权限均在服务端主线程重新校验。
 * 独立请求不继承原版激活包上游检查，必须在此完整执行。公共入口无客户端类引用。
 */
public final class PocketWornTapHandler {

    /** 由 CommonProxy 注册网络，客户端经 ClientProxy 的鼠标监听发请求。 */
    public static final PocketWornTapHandler INSTANCE = new PocketWornTapHandler();

    private static SimpleNetworkWrapper channel;
    private final Map<EntityPlayerMP, Long> lastTapTicks = new WeakHashMap<>();

    private PocketWornTapHandler() {}

    /** 双端注册同一枚 C2S 包；专用服仅加载公共包和服务端 handler。 */
    public static synchronized void initNetwork() {
        if (channel != null) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("gtit_pocket_tap");
        channel.registerMessage(PocketWornTapPacket.Handler.class, PocketWornTapPacket.class, 0, Side.SERVER);
    }

    public static void requestTap(int dimension, int x, int y, int z, int face) {
        if (channel != null) channel.sendToServer(new PocketWornTapPacket(dimension, x, y, z, face));
    }

    /** 仅由服务器任务队列消费；所有守卫都在任何世界或口袋写入之前。 */
    void handleRequest(EntityPlayerMP player, PocketWornTapPacket request) {
        final MinecraftServer server = MinecraftServer.getServer();
        if (player == null || request == null
            || server == null
            || player.isDead
            || player.worldObj == null
            || player.worldObj.isRemote
            || !(player.worldObj instanceof WorldServer world)
            || server.getConfigurationManager()
                .func_152612_a(player.getCommandSenderName()) != player)
            return;
        if (world.provider.dimensionId != request.dimension || player.dimension != request.dimension
            || player.openContainer != player.inventoryContainer
            || player.getCurrentEquippedItem() != null) return;
        final ItemStack pocket = findWorn(player);
        if (pocket == null) return;
        if (request.face < 0 || request.face > 5
            || request.y < 0
            || request.y >= world.getHeight()
            || !world.blockExists(request.x, request.y, request.z)) return;
        final double reach = player.theItemInWorldManager.getBlockReachDistance();
        if (!inReach(
            player.posX,
            player.posY + player.getEyeHeight(),
            player.posZ,
            request.x,
            request.y,
            request.z,
            reach)) return;
        final Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        final Vec3 look = player.getLook(1.0F);
        final MovingObjectPosition hit = world
            .rayTraceBlocks(eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || hit.blockX != request.x
            || hit.blockY != request.y
            || hit.blockZ != request.z
            || hit.sideHit != request.face) return;
        if (server.isBlockProtected(world, request.x, request.y, request.z, player)
            || !world.canMineBlock(player, request.x, request.y, request.z)
            || !player.canPlayerEdit(request.x, request.y, request.z, request.face, null)) return;
        final TileEntity at = world.getTileEntity(request.x, request.y, request.z);
        if (!(at instanceof BaseMetaTileEntity tile) || tile.getWorldObj() != world || tile.isInvalid()) return;
        // 独立 C2S 也经过 Forge 交互门禁，保留领地保护等模组取消/禁止方块交互的能力。
        final PlayerInteractEvent interaction = ForgeEventFactory.onPlayerInteract(
            player,
            PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,
            request.x,
            request.y,
            request.z,
            request.face,
            world);
        if (interaction.isCanceled() || interaction.useBlock == Result.DENY) return;
        final long tick = world.getTotalWorldTime();
        if (Long.valueOf(tick)
            .equals(lastTapTicks.get(player))) return;
        lastTapTicks.put(player, tick);
        PocketWorldFluidTap.tap(player, pocket, tile);
    }

    /** 以方块包围盒最近点检查距离；视线与点击面再由服务器 ray trace 确认。 */
    static boolean inReach(double eyeX, double eyeY, double eyeZ, int x, int y, int z, double reach) {
        if (!Double.isFinite(reach) || reach <= 0
            || !Double.isFinite(eyeX)
            || !Double.isFinite(eyeY)
            || !Double.isFinite(eyeZ)) return false;
        final double dx = Math.max(x - eyeX, Math.max(eyeX - ((double) x + 1), 0));
        final double dy = Math.max(y - eyeY, Math.max(eyeY - ((double) y + 1), 0));
        final double dz = Math.max(z - eyeZ, Math.max(eyeZ - ((double) z + 1), 0));
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    // ------------------------------------------------------------------ bauble 栏的遍历（★面板与抽液共用这一份）

    /**
     * 取该玩家的 bauble 栏；★Baubles 侧任何漂移都不允许成为崩溃源 ⇒ 拿不到就返回 {@code null}
     * （调用方必须判空，这是门禁与用例都点名过的那道守卫）。
     */
    public static IInventory safeBaubles(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            return BaublesApi.getBaubles(player);
        } catch (Throwable t) {
            GTInterestingThing.LOG.warn("[pocket] 取不到 bauble 栏 ⇒ 按「没穿戴」处理（不崩，也不抽液）", t);
            return null;
        }
    }

    /**
     * 找到穿在该玩家饰品栏里的那一枚口袋（★扫描面 = 全部槽位，不写死 0..3：本仓自己的读法与
     * SalisArcana 那类放宽 bauble 栏的 mod 共存时，写死上界会把"确实穿着"读成"没穿"）。
     *
     * @return 那一枚栈，没穿则 {@code null}
     */
    public static ItemStack findWorn(EntityPlayer player) {
        final IInventory baubles = safeBaubles(player);
        if (baubles == null) {
            return null;
        }
        for (int i = 0; i < baubles.getSizeInventory(); i++) {
            final ItemStack at = baubles.getStackInSlot(i);
            if (at == null) {
                continue;
            }
            if (at.getItem() instanceof ItemNekoDimensionPocket) {
                return at;
            }
        }
        return null;
    }

    /** 同 {@link #findWorn}，但要的是<b>槽号</b>（客户端开饰品背包那一路要用它当 {@code openFromBaubles} 的索引）。 */
    public static int findWornSlot(EntityPlayer player) {
        final IInventory baubles = safeBaubles(player);
        if (baubles == null) {
            return -1;
        }
        for (int i = 0; i < baubles.getSizeInventory(); i++) {
            final ItemStack at = baubles.getStackInSlot(i);
            if (at != null && at.getItem() instanceof ItemNekoDimensionPocket) {
                return i;
            }
        }
        return -1;
    }
}
