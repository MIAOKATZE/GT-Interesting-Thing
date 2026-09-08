package com.miaokatze.gtit.reincarnation.entity;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.relauncher.Side;

/**
 * 周目系统实体注册入口（v1.9.0 B批）。
 * <p>
 * 侧门控（与 {@code ReincarnationNetwork.init()} 同口径内联判定，不建共享 gate 类）：
 * 物理专用服务器（{@code FMLCommonHandler.getSide() == Side.SERVER}）上周目系统整体拒绝注册，
 * {@link #register()} 直接返回 false，周目实体不进专用服。
 * <p>
 * 注册分两步（与网络件同构）：
 * <ul>
 * <li>实体本体：{@code CommonProxy.init()} 中经 {@link #register()} 创建（双端物理侧判定，
 * 集成服物理侧为 CLIENT 正常注册）；</li>
 * <li>客户端渲染器：{@code ClientProxy.init()} 中经
 * {@code AscensionCarrierRenderRegistrar.register()} 注册（空渲染，载具刻意不可见）——
 * 1.7.10 下实体若未注册渲染器，客户端 track 到即崩溃，故必须配对调用。</li>
 * </ul>
 * <p>
 * 本类全类零客户端类型引用（v1.9.0 冒烟修正 20260909 R1/R2：渲染注册器已按
 * wiki client-class-contamination 规避清单条款 2"登记与渲染分文件"拆至仅客户端可达的
 * {@code AscensionCarrierRenderRegistrar}——原同体形态下 JVM 类校验期验证整类字节码，
 * 专用服调用 {@link #register()} 即触发 client 类链接失败）。
 */
public class ReincarnationEntities {

    /**
     * 本模组实体注册 ID（mod 内自增域）。本仓唯一实体占用；后续如新增实体请递增避免冲突。
     */
    private static final int ENTITY_ID_ASCENSION_CARRIER = 1901;

    /** 实体本体注册幂等标志 */
    private static boolean registered = false;

    /**
     * 注册飞升载具实体（幂等）
     * <p>
     * trackingRange=256（大间距：飞升全程 12 方块 + 演出余量，跨区块高度仍被 track）、
     * updateFrequency=10、sendsVelocityUpdates=true。
     * <p>
     * 注：1.7.10 {@code EntityRegistry} 机制下全局注册名为 {@code <modid>.<entityName>}，
     * 本实体实际保存名 = {@code "gtit.gtit.AscensionCarrier"}（entityName 参数按冻结契约
     * 传 {@code "gtit.AscensionCarrier"}，前缀重复为 1.7.10 预期行为，非笔误）。
     *
     * @return true = 实体已就绪（含此前已注册）；false = 物理专用服务器门控跳过
     */
    public static boolean register() {
        if (registered) return true;
        // 物理侧门控（与 ReincarnationNetwork.init 同款内联判定写法）：
        // 物理专用服务器上周目系统整体不注册
        if (FMLCommonHandler.instance()
            .getSide() == Side.SERVER) {
            GTInterestingThing.LOG.info("[reincarnation] 物理专用服务器：周目系统拒绝注册（实体）");
            return false;
        }
        EntityRegistry.registerModEntity(
            EntityAscensionCarrier.class,
            "gtit.AscensionCarrier",
            ENTITY_ID_ASCENSION_CARRIER,
            GTInterestingThing.instance,
            256,
            10,
            true);
        registered = true;
        return true;
    }

    // ==================== 供 S4 消费的便捷生成 ====================

    /**
     * 服务端：在玩家位置生成飞升载具并立即绑定玩家为骑乘者（供 S4 演出触发调用）
     * <p>
     * 生成 + mountEntity 同 tick 完成，不会触发载具的"失去骑乘者即自毁"守卫；
     * 骑乘后载具每 tick 服务端上升 {@link EntityAscensionCarrier#RISE_SPEED_PER_TICK}，
     * 玩家位置由原版骑乘机制自动跟随。杀玩家/后续演出由 S4/S5 切片负责。
     *
     * @param player        目标玩家（服务端玩家；客户端调用返回 null）
     * @param durationTicks 本次飞升时长（tick），总升程 ≈ RISE_SPEED_PER_TICK × durationTicks；
     *                      非正值回退 {@link EntityAscensionCarrier#DEFAULT_ASCEND_DURATION_TICKS}
     * @return 已生成并绑定的载具实体（可经 {@link EntityAscensionCarrier#isAscensionComplete()}
     *         轮询完成状态）；玩家为空或处于客户端物理侧时返回 null
     */
    public static EntityAscensionCarrier spawnFor(EntityPlayerMP player, int durationTicks) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return null;
        EntityAscensionCarrier carrier = new EntityAscensionCarrier(
            player.worldObj,
            player.posX,
            player.posY,
            player.posZ);
        carrier.setDurationTicks(durationTicks);
        player.worldObj.spawnEntityInWorld(carrier);
        player.mountEntity(carrier);
        GTInterestingThing.LOG.info(
            "[reincarnation] 飞升载具已生成并绑定玩家：" + player.getCommandSenderName()
                + " @ ("
                + String.format("%.1f", player.posX)
                + ", "
                + String.format("%.1f", player.posY)
                + ", "
                + String.format("%.1f", player.posZ)
                + ")，时长 "
                + carrier.getDurationTicks()
                + " tick");
        return carrier;
    }

    public static boolean isRegistered() {
        return registered;
    }
}
