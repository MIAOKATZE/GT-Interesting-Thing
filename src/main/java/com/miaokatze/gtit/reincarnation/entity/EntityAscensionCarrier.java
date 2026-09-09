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
 * <li>无重力无 AI：不使用 motionY/重力/寻路等移动逻辑，onUpdate 内服务端每 tick 按
 * 三段式动量积分推进 {@code v += ACCEL_PER_TICK}（匀加速—巡航—终点缓冲，
 * D3 口径见常量 javadoc）；骑乘者位置由原版骑乘机制自动跟随
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
     * 匀加速初速（方块/tick）。D3 动量积分口径：每 tick {@code v += ACCEL_PER_TICK}，
     * 升空由静止平滑起步（旧恒速 0.10 起步突兀，替换为三段式）。
     */
    public static final double INITIAL_SPEED_PER_TICK = 0.0D;

    /**
     * 匀加速度（方块/tick²）：加速段每 tick 递增、终点缓冲段每 tick 等量递减。
     * 取值使默认时长（120t，加速 40t / 巡航 40t / 缓冲减速 40t）总升程 ≈ 12 方块，
     * 峰值速度 {@link #MAX_SPEED_PER_TICK} = 0.15。
     */
    public static final double ACCEL_PER_TICK = 0.00375D;

    /** 峰值速度（方块/tick）：加速段终点 = 巡航段速度（钳制上界，防越界加速） */
    public static final double MAX_SPEED_PER_TICK = 0.15D;

    /** 默认飞升时长（tick）：120 tick = 6 秒，总升程 ≈ 12 方块（三段式 40/40/40） */
    public static final int DEFAULT_ASCEND_DURATION_TICKS = 120;

    /** NBT 键：已上升 tick 计数 */
    private static final String TAG_RISE_TICK = "gtitRiseTick";
    /** NBT 键：本次飞升时长上限（tick） */
    private static final String TAG_DURATION = "gtitDurationTicks";
    /** NBT 键：飞升完成标志 */
    private static final String TAG_COMPLETE = "gtitAscensionComplete";
    /** NBT 键：当前上升速度（动量积分状态，防区块卸载重置） */
    private static final String TAG_SPEED = "gtitRiseSpeed";

    /** 已上升 tick 计数（服务端推进；NBT 持久化，防区块卸载重置） */
    private int riseTick;
    /** 本次飞升时长上限（tick），spawnFor 可按演出需要覆盖；NBT 持久化 */
    private int durationTicks = DEFAULT_ASCEND_DURATION_TICKS;
    /** 当前上升速度（方块/tick；动量积分，NBT 持久化） */
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

        // 服务端权威：位移与状态只在服务端推进（客户端零逻辑）
        if (worldObj.isRemote) return;

        // Botania :205-208 同款守卫：失去骑乘者即自毁（含 finishAscension 卸下后的下一 tick 收尾）
        if (riddenByEntity == null) {
            setDead();
            return;
        }
        // 已完成：不再上升，等待上面的骑乘者清空守卫在下一 tick 自毁
        if (ascensionComplete) return;

        // D3 三段式动量积分（明确参数）：
        // 初速 0（INITIAL_SPEED_PER_TICK），加速度 0.00375/t²（ACCEL_PER_TICK），
        // 前 1/3 时长匀加速至峰值 0.15（MAX_SPEED_PER_TICK），中段巡航，
        // 终点缓冲 = 后 1/3 时长反相匀减速，到达时长时速度恰好回落至 0——
        // 速度恒被 [0, 0.15] 钳制，总升程 ≈ 0.15 × 2/3 时长（120t ≈ 12 格），不越界。
        int accelTicks = this.durationTicks / 3;
        int decelStartTick = this.durationTicks - accelTicks;
        if (this.riseTick < accelTicks) {
            this.riseSpeed += ACCEL_PER_TICK;
        } else if (this.riseTick >= decelStartTick) {
            this.riseSpeed -= ACCEL_PER_TICK;
        }
        if (this.riseSpeed < 0.0D) this.riseSpeed = 0.0D;
        if (this.riseSpeed > MAX_SPEED_PER_TICK) this.riseSpeed = MAX_SPEED_PER_TICK;

        posY += this.riseSpeed;
        riseTick++;

        if (riseTick >= durationTicks) {
            finishAscension();
        }
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
        // 旧档（恒速时代）无速度键：从 0 平滑重新加速，动量连续无跳变
        riseSpeed = cmp.getDouble(TAG_SPEED);
        if (riseSpeed < 0.0D || riseSpeed > MAX_SPEED_PER_TICK) riseSpeed = INITIAL_SPEED_PER_TICK;
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
