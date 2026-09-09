package com.miaokatze.gtit.reincarnation.entity;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 周目系统「飞升载具」——服务端权威的缓缓升空隐形载具（v1.9.0 B批）。
 * <p>
 * 手法复刻 Botania {@code TileLightRelay.EntityPlayerMover}（只读先例：
 * Source_Code_Reference_Collection/Botania-1.13.34-GTNH/.../block/tile/TileLightRelay.java
 * :172-303，零依赖复刻）：
 * <ul>
 * <li>{@code setSize(0, 0)}：零尺寸包围盒（Botania :190 同款，多年实机验证无 AABB 异常，
 * 故不退避 0.01/0.01）；</li>
 * <li>{@code noClip = true}：与方块/实体零碰撞（Botania :191 同款）；</li>
 * <li>伤害免疫：{@link #attackEntityFrom} 恒 false（Botania :269-271 同款）+
 * {@link #isEntityInvulnerable()} 恒 true；</li>
 * <li>无重力无 AI：不使用 motionY/重力/寻路等移动逻辑，onUpdate 内<b>双端</b>按同一
 * 单式竖直匀加速积分推进 {@code v += RISE_ACCEL_PER_TICK}（双端同式确定性积分，见常量
 * javadoc；客户端不再冻结等待同步包跳变，骑者相机随客户端载具平滑上行），服务端仍
 * 独占 NBT 写/finish/自毁等裁决；骑乘者位置由原版骑乘机制自动跟随
 * （Entity#updateRidden → 本载具#updateRiderPosition，每 tick 调用，无需额外代码）；</li>
 * <li>到达时长后 {@link #finishAscension()}：骑乘者 {@code mountEntity(null)} 卸下
 * + ascensionComplete 标志位 + log 一行（杀玩家/后续演出由 S4/S5 切片负责）；</li>
 * <li>失去骑乘者即自毁（Botania :205-208 同款守卫，防载具残留）；</li>
 * <li>NBT 持久化 tick 计数/时长/完成标志，防区块卸载重置（Botania :273-283 同款读写位）。</li>
 * </ul>
 * <p>
 * S4 经 {@link ReincarnationEntities#spawnFor} 生成并绑定玩家；完成标志经
 * {@link #isAscensionComplete()} 读取。
 */
public class EntityAscensionCarrier extends Entity {

    /**
     * 匀加速初速（方块/tick）：升空由静止平滑起步（NBT 速度键越界时的回退值）。
     */
    public static final double INITIAL_SPEED_PER_TICK = 0.0D;

    /**
     * 单式匀加速度（方块/tick²）：双端每 tick {@code v += RISE_ACCEL_PER_TICK}，由静止
     * 单调加速（v1.8.5：替换旧三段式 0.00375 动量积分，双端同式确定性积分修卡顿）。
     * 默认时长 120t 下末速 0.01×120 = 0.84 方块/tick（≈17 方块/s）、总升程
     * Σ0.01t（t=1..120）≈ 72 方块。
     */
    public static final double RISE_ACCEL_PER_TICK = 0.01D;

    /** 默认飞升时长（tick）：120 tick = 6 秒 */
    public static final int DEFAULT_ASCEND_DURATION_TICKS = 120;

    /** NBT 键：已上升 tick 计数 */
    private static final String TAG_RISE_TICK = "gtitRiseTick";
    /** NBT 键：本次飞升时长上限（tick） */
    private static final String TAG_DURATION = "gtitDurationTicks";
    /** NBT 键：飞升完成标志 */
    private static final String TAG_COMPLETE = "gtitAscensionComplete";
    /** NBT 键：当前上升速度（积分状态，防区块卸载重置） */
    private static final String TAG_SPEED = "gtitRiseSpeed";

    /**
     * NBT 读入速度上界（方块/tick）：旧档（恒速 0.10 / 三段式 0.15 时代）0~0.15 值直读
     * 兼容；新单式匀加速档放宽到 2.0（否则区块卸载恢复时新速度被误钳回 0）；越界视为
     * 损坏数据回退初速 0。
     */
    private static final double NBT_MAX_RISE_SPEED_PER_TICK = 2.0D;

    /** 已上升 tick 计数（双端同式积分推进；NBT 仅服务端写入，防区块卸载重置） */
    private int riseTick;
    /** 本次飞升时长上限（tick），spawnFor 可按演出需要覆盖；NBT 持久化 */
    private int durationTicks = DEFAULT_ASCEND_DURATION_TICKS;
    /** 当前上升速度（方块/tick；单式匀加速积分状态，NBT 持久化） */
    private double riseSpeed = INITIAL_SPEED_PER_TICK;
    /** 飞升完成标志（finishAscension 置位；S4 经 {@link #isAscensionComplete()} 读取） */
    private boolean ascensionComplete;

    /** 反序列化/恢复用构造（FML 实体恢复路径走此签名） */
    public EntityAscensionCarrier(World world) {
        super(world);
    }

    /** 定点生成构造（S4 经 {@link ReincarnationEntities#spawnFor} 使用） */
    public EntityAscensionCarrier(World world, double x, double y, double z) {
        super(world);
        setPosition(x, y, z);
    }

    @Override
    protected void entityInit() {
        // Botania TileLightRelay.EntityPlayerMover:190-191 同款：零尺寸 + 无碰撞。
        // 0,0 尺寸经 Botania 长期实机验证无 AABB 异常，不退避 0.01/0.01
        setSize(0.0F, 0.0F);
        noClip = true;
    }

    @Override
    public void onUpdate() {
        // 保留基类 tick 基础设施（ticksExisted 推进、骑乘者死亡清理、updateRidden →
        // updateRiderPosition 每 tick 更新骑者位置）；刻意不使用基类 motion/重力/AI 移动逻辑
        super.onUpdate();

        if (worldObj.isRemote) {
            // 客户端：只做与服务器同式的确定性积分（修旧版「客户端实体冻结、posY 靠
            // 0.5s 一跳的同步包跳变」卡顿根因），并镜像服务器失骑守卫——finish 卸骑
            // 后、销毁包到达前不再多积分（残差≤1-2 tick，由位置同步包纠偏）；不裁决
            // 自毁/完成等状态——载具销毁由服务端 destroy 包驱动，本客户端实体无需
            // 也不应自毁
            if (riddenByEntity != null) {
                integrateRise();
            }
            return;
        }

        // 服务端权威裁决：Botania :205-208 同款守卫，失去骑乘者即自毁
        // （含 finishAscension 卸下后的下一 tick 收尾）
        if (riddenByEntity == null) {
            setDead();
            return;
        }
        // 已完成：不再上升，等待上面的骑乘者清空守卫在下一 tick 自毁
        if (ascensionComplete) return;

        integrateRise();

        if (riseTick >= durationTicks) {
            finishAscension();
        }
    }

    /**
     * 单式竖直匀加速积分（双端同式、同参数同结果；客户端浮点漂移由原版位置同步包
     * 自动纠偏）：{@code v += RISE_ACCEL_PER_TICK; motionY = v; posY += v}。
     */
    private void integrateRise() {
        this.riseSpeed += RISE_ACCEL_PER_TICK;
        if (this.riseSpeed < 0.0D) this.riseSpeed = 0.0D;
        motionY = this.riseSpeed;
        posY += this.riseSpeed;
        this.riseTick++;
    }

    /**
     * 飞升完成收尾：卸下骑乘者 + 置完成标志 + log 一行。
     * <p>
     * 刻意不在本方法内 setDead——交给 onUpdate 的骑乘者空守卫在下一 tick 自毁
     * （Botania 同款收尾路径），S4 在同一 tick 内仍可经实例引用读取完成标志。
     */
    private void finishAscension() {
        ascensionComplete = true;
        if (riddenByEntity != null) {
            riddenByEntity.mountEntity(null); // 1.7.10 dismount 语义：参数 null 即卸下
        }
        GTInterestingThing.LOG.info("[reincarnation] 飞升载具到达终点并卸下骑乘者：tick=" + riseTick + "，posY=" + Math.round(posY));
    }

    // ==================== 免疫/骑乘语义 ====================

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false; // Botania :269-271 同款：载具完全免伤
    }

    @Override
    public boolean isEntityInvulnerable() {
        return true; // isEntityInvulnerable 语义（覆盖 /kill、虚空伤害等免疫判定路径）
    }

    @Override
    public boolean shouldRiderSit() {
        return false; // Botania :263-266 同款：骑乘者保持站立姿态，升空演出更自然
    }

    // ==================== NBT 持久化（防区块卸载重置） ====================

    @Override
    protected void readEntityFromNBT(NBTTagCompound cmp) {
        riseTick = cmp.getInteger(TAG_RISE_TICK);
        durationTicks = cmp.getInteger(TAG_DURATION);
        // 旧档/异常兜底：非法值回退默认时长
        if (durationTicks <= 0) durationTicks = DEFAULT_ASCEND_DURATION_TICKS;
        // 旧档（恒速/三段式时代）速度键 0~0.15 值直读兼容；新档上界放宽到
        // {@link #NBT_MAX_RISE_SPEED_PER_TICK}（2.0），越界视为损坏数据回退初速 0 重新加速
        riseSpeed = cmp.getDouble(TAG_SPEED);
        if (riseSpeed < 0.0D || riseSpeed > NBT_MAX_RISE_SPEED_PER_TICK) riseSpeed = INITIAL_SPEED_PER_TICK;
        ascensionComplete = cmp.getBoolean(TAG_COMPLETE);
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound cmp) {
        cmp.setInteger(TAG_RISE_TICK, riseTick);
        cmp.setInteger(TAG_DURATION, durationTicks);
        cmp.setBoolean(TAG_COMPLETE, ascensionComplete);
        cmp.setDouble(TAG_SPEED, riseSpeed);
    }

    // ==================== 供 S4/S5 消费的读取口 ====================

    /** 已上升 tick 计数 */
    public int getRiseTick() {
        return riseTick;
    }

    /** 本次飞升时长上限（tick） */
    public int getDurationTicks() {
        return durationTicks;
    }

    /** 飞升是否已完成（finishAscension 置位后为 true） */
    public boolean isAscensionComplete() {
        return ascensionComplete;
    }

    /** 覆盖本次飞升时长（tick）；非正值回退默认时长 */
    public void setDurationTicks(int ticks) {
        durationTicks = ticks > 0 ? ticks : DEFAULT_ASCEND_DURATION_TICKS;
    }
}
