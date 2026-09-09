package com.miaokatze.gtit.reincarnation.client.fx;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovementInput;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.reincarnation.network.ClientReincarnationFxState;
import com.miaokatze.gtit.reincarnation.network.GrantEffectPacket;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 周目系统客户端演出总成（v1.9.0 C批 / S8 切片）：升天环绕水晶、领取螺旋降下、
 * 中央炫彩大字 + 30 秒倒计时 HUD、升天期间输入封锁（仅保留鼠标视角）。
 * <p>
 * <b>加载纪律：</b>本包（{@code reincarnation.client.fx}）全部类仅可从
 * {@code ClientProxy.init()} 调用路径可达（由 S4 在 ClientProxy 调用
 * {@link #install()}），物理专用服务器不会触发本类类加载；本类自包含订阅
 * FML bus（ClientTick）+ MinecraftForge.EVENT_BUS（Render/Mouse/GuiOpen），
 * 不要求 ClientProxy 做任何额外接线（本切片禁止改 ClientProxy）。
 * <p>
 * <b>消费契约（S3 已冻结）：</b>{@link ClientReincarnationFxState} 全部读写；
 * 写侧仅 {@code setGrantEffectStartTick}（主线程回填，-1=未回填语义见其 javadoc）、
 * {@code clearGrantEffect}/{@code clearAscension}/{@code clearCountdown}（演出收尾）。
 * <p>
 * <b>渲染手法来源（只读复刻，零依赖零 import 跨仓）：</b>
 * <ul>
 * <li>additive 光斑：GTSR {@code GTSRBeamFX.renderParticle}（:149-257）同款——
 * {@code glBlendFunc(GL_SRC_ALPHA, GL_ONE) + glDepthMask(false) + setBrightness(0x00F000F0)
 * + glow 贴图 + 结束恢复 normal 混合/深度写入}；本类在
 * {@code RenderWorldLastEvent} 内直接 Tessellator 绘制 glow billboard
 * （任务包允许的两种手法之一；<b>择 RenderWorldLastEvent 而非 EffectRenderer/EntityFX：</b>
 * 1.7.10 粒子固定走 particles.png 贴图表，自定义 glow 贴图需 stitch 进粒子表，
 * 而 billboard 直接 bindTexture 零缝合，且水晶本体本就要在 RenderWorldLast 渲染）。</li>
 * <li>HUD 层：BetterQuesting {@code QuestNotification.onDrawScreen}（:223-240）同款——
 * {@code RenderGameOverlayEvent.Post} + {@code ElementType.HELMET} +
 * {@code event.resolution.getScaledWidth/Height()}。</li>
 * <li>世界内渲染物品：{@code RenderManager.instance.renderEntityWithPosYaw(虚拟 EntityItem, …)}
 * ——签名经本地反编译源核实：RenderManager.java:102 {@code public static RenderManager instance}
 * 是<b>字段不是方法</b>（任务包草案的 {@code instance()} 写法有误，勿改回）；
 * RenderManager.java:281-284 {@code renderEntityWithPosYaw(Entity,double,double,double,float,float)}
 * <b>直通 doRender、不做相机坐标扣除</b>，故传相机相对坐标（相机偏移用
 * {@code lastTickPos + (pos-lastTickPos)*partialTicks} 插值，任务包草案
 * "player.lastTickEntity" 系笔误）。</li>
 * <li>billboard 朝向：原版铭牌手法 Render.java:352-353——
 * {@code glRotatef(-RenderManager.instance.playerViewY, 0,1,0);
 * glRotatef(playerViewX, 1,0,0)}（playerViewX/Y 为 RenderManager.java:115-116 公开静态字段，
 * 每帧缓存插值视角）。</li>
 * </ul>
 * <p>
 * <b>输入封锁机制（新增硬需求；全部经本地反编译源实读核实，见各处 file:line）：</b>
 * <ol>
 * <li><b>主机制：{@code movementInput} 全零替换。</b>
 * {@code net.minecraft.util.MovementInput}（MovementInput.java:9-16）字段仅
 * {@code moveStrafe/moveForward/jump/sneak} + {@code updatePlayerMoveState()}；
 * {@code EntityPlayerSP} 每帧 {@code onLivingUpdate} 经
 * {@code movementInput.updatePlayerMoveState()} 采样驱动移动/跳跃/疾跑
 * （EntityPlayerSP.java:95-97、186-276：疾跑要求 {@code moveForward >= 0.8}，零值天然封死）；
 * 潜行下车经 {@code isSneaking()} = {@code movementInput.sneak && !sleeping}
 * （EntityPlayerSP.java:485-489），零值天然封死骑乘潜行下车。
 * {@link LockedMovementInput} 覆写采样恒清零，替换/还原各 log 一行。</li>
 * <li><b>辅机制核实结论（任务包要求的实读证据）：</b>
 * {@code cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent / MouseInputEvent}
 * （InputEvent.java:5-7）<b>extends Event、无 {@code @Cancelable}，在 1.7.10 不可取消</b>
 * （且为处理后发射：Minecraft.java:1964/1826，取消也无回退效果），
 * 故任务包草案"取消 InputEvent.*"不可行，<b>本类不订阅这两个事件</b>，改用以下替代：</li>
 * <li><b>替代机制 a：取消 {@code net.minecraftforge.client.event.MouseEvent}</b>
 * （MouseEvent.java:13 {@code @Cancelable}）——发射点 Minecraft.java:1778
 * {@code if (ForgeHooksClient.postMouseEvent()) continue;}，取消即跳过该鼠标事件全部
 * vanilla 处理（{@code KeyBinding.setKeyBindState/onTick}、滚轮换持、左右键攻击/使用），
 * 升天期间攻击/使用/换挡全部失效；鼠标视角不在该路径（EntityRenderer 的
 * Mouse.getDX/DY），<b>视角保留</b>。</li>
 * <li><b>替代机制 b：取消 {@code GuiOpenEvent}</b>（GuiOpenEvent.java:14
 * {@code @Cancelable}，发射点 Minecraft.java:841 displayGuiScreen 入口）——
 * 升天期间阻止打开任何新 GUI（背包/聊天/Esc 菜单），放行 {@code gui == null}
 * （关闭路径）与死亡/掉线场景（以 theWorld/thePlayer 存活为门槛，防卡死亡/断线界面）。</li>
 * <li><b>替代机制 c：closeScreen 周期守卫</b>（EntityPlayerSP.java:353-357：
 * {@code super.closeScreen()} 容器收尾 + {@code displayGuiScreen(null)}）——
 * 每客户端 tick 兜底关闭残留 GUI（防 GUI 在封锁开始前已打开、或经旁路打开）。</li>
 * </ol>
 * 已知残余（1.7.10 事件模型下不可事件级拦截、且 movementInput 不覆盖，属任务包
 * 替代机制的接受边界）：键盘数字 1-9 快捷栏切换、F1/F3/F5 等视图键仍生效。
 * <p>
 * <b>升天演出结束判定（任务包要求的 javadoc 记录；服务端包外无 active=false 通知）：</b>
 * 升天期间玩家骑乘隐形载具（{@code EntityAscensionCarrier}：finishAscension 时
 * {@code riddenByEntity.mountEntity(null)}（EntityAscensionCarrier.java:107-114），
 * 载具随即自毁），故客户端按<b>锚点实体死亡 / 载具消失</b>判定：
 * 玩家 {@code isDead} → 立即结束；锚点（targetEntityId 实体，缺省本地玩家）
 * {@code ridingEntity == null} 连续 {@link #ASCENSION_END_GRACE_TICKS} tick
 * （容错骑乘建立延迟与单 tick 抖动）→ 结束。结束时 {@code clearAscension()} +
 * 还原 movementInput，各 log 一行。登出/换维度（theWorld == null）全量清理。
 */
public final class ReincarnationClientFx {

    // ==================== 演出常量（任务包给定区间取值） ====================

    /** 升天环绕水晶数（任务包 6~8 取 7，奇数圈更均衡） */
    private static final int ASCENSION_CRYSTAL_COUNT = 7;
    /** 环绕半径基准（格，任务包 1.5~2 取中值 1.75） */
    private static final float ASCENSION_ORBIT_RADIUS = 1.75F;
    /** 环绕半径波动幅度（格；半径在 1.5~2.0 间正弦波动） */
    private static final float ASCENSION_RADIUS_AMPLITUDE = 0.25F;
    /** 环绕角速度常量（弧度/tick；≈1.8 弧度/秒） */
    private static final float ASCENSION_RAD_PER_TICK = 0.09F;
    /** 水晶环高度基准（锚点 posY + 眼高 + 此值；头顶上方约 0.5 格） */
    private static final float ASCENSION_HEAD_OFFSET = 0.4F;
    /** 水晶环高度波动幅度（格，任务包 ±1） */
    private static final float ASCENSION_HEIGHT_AMPLITUDE = 1.0F;
    /** glow 光斑尺寸（格，billboard 半宽） */
    private static final float ASCENSION_GLOW_SIZE = 0.55F;

    /** 领取降下演出总时长（tick，任务包 80~120 取 100 = 5 秒） */
    private static final int GRANT_DESCENT_TICKS = 100;
    /** 单件物品自身降落段时长（tick；错峰后仍在总时长内） */
    private static final int GRANT_PER_ITEM_TICKS = 60;
    /** 相邻物品错峰间隔（tick；"依次"降下，7 件时末件 (7-1)*5+60=90 < 100） */
    private static final int GRANT_STAGGER_TICKS = 5;
    /** 降下起点高度（玩家头顶上方格数，任务包 6~8 取 7） */
    private static final float GRANT_START_HEIGHT = 7.0F;
    /** 降下螺旋起始半径（格，随进度收拢到 0） */
    private static final float GRANT_SPIRAL_RADIUS = 1.2F;
    /** 降下螺旋角速度（弧度/tick） */
    private static final float GRANT_SPIRAL_RAD_PER_TICK = 0.22F;
    /** 演出动画最多展示的物品数（防超大清单刷屏；实际发放由服务端完成，与本演出无关） */
    private static final int GRANT_MAX_ANIMATED_ITEMS = 7;
    /** 发放 glow 光斑尺寸（格） */
    private static final float GRANT_GLOW_SIZE = 0.45F;

    /** 升天结束判定：锚点 ridingEntity == null 的连续 tick 宽限（容错骑乘建立延迟） */
    private static final int ASCENSION_END_GRACE_TICKS = 20;

    /**
     * 飞升渐强音效键（sounds.json 注册键 {@code ascension_ramp}，资源
     * {@code gtit:ascension_ramp}；音频资产由音效管线另行生成，本切片仅注册+接线）。
     */
    private static final String ASCENSION_SOUND_KEY = "gtit:ascension_ramp";
    /** 音效分段间隔（tick）：每段重播一次，音量按演出进度递增（1.7.10 无法调制播放中实例） */
    private static final int ASCENSION_SOUND_SEGMENT_TICKS = 30;
    /** 音效分段总数（演出时长 120t → 4 段；越界即停，防演出异常滞留时叠音） */
    private static final int ASCENSION_SOUND_MAX_SEGMENTS = 4;
    /** 音效起始音量（第 1 段） */
    private static final float ASCENSION_SOUND_MIN_VOLUME = 0.25F;
    /** 音效峰值音量（末段） */
    private static final float ASCENSION_SOUND_MAX_VOLUME = 1.0F;

    /**
     * 渐强音效时长基准（tick，与载具默认时长同步；超过按满值封顶）。v1.9.1：
     * 原同基准的屏幕渐变 overlay 已整体摘除（视野表现改由 MixinEntityRenderer
     * 视野渐模糊承担），本基准现仅音效分段进度使用，数值不变。
     */
    private static final float ASCENSION_RAMP_TICKS = 120.0F;

    /** 倒计时大数字字号（glScalef 倍率；死亡大标题式放大手法） */
    private static final float COUNTDOWN_NUMBER_SCALE = 3.0F;
    /** 庆祝大字基准字号（glScalef 倍率，随 tick 呼吸脉动） */
    private static final float CELEBRATION_BASE_SCALE = 2.2F;
    /** 炫彩色相循环节奏（tick/圈；60 tick = 3 秒一环） */
    private static final int CELEBRATION_HUE_CYCLE_TICKS = 60;

    // ==================== lang 键（只引用；文案由 S4 统一落地） ====================

    /** 领取庆祝大字（en "Reincarnation Effective" / zh "轮回已生效" 系，S4 落地） */
    private static final String LANG_CELEBRATION = "gtit.reincarnation.celebration";
    /** 倒计时说明行（S4 落地） */
    private static final String LANG_COUNTDOWN_LINE = "gtit.reincarnation.countdown_line";
    /** 倒计时归零后等待服务端处理期间的附加提示行（S4 落地） */
    private static final String LANG_TIMELINE_ENDED = "gtit.reincarnation.timeline_ended";

    /** glow 光斑贴图（本切片生成资产，见 tools/artgen_catalog/reincarnation_fx/gen_reincarnation_glow.py） */
    private static final ResourceLocation GLOW_TEXTURE = new ResourceLocation(
        "gtit",
        "textures/misc/reincarnation_glow.png");

    // ==================== 运行时状态（仅客户端主线程访问） ====================

    /** install 幂等位 */
    private static boolean installed;
    /** 输入封锁进行位 */
    private static boolean inputLocked;
    /** 封锁时的玩家实例（还原时校验实例未被 respawn 换新） */
    private static EntityClientPlayerMP lockedPlayer;
    /** 封锁前原 movementInput 实例（结束时还原） */
    private static MovementInput savedMovementInput;
    /** 全零 movementInput 实例（结束时按实例比对还原） */
    private static LockedMovementInput lockedMovementInput;
    /** 锚点骑乘为空的连续 tick 计数（升天结束判定） */
    private static int rideNullStreak;
    /** 已播放的渐强音效分段号（升天期间递增；演出结束/取消/离开时复位防叠音） */
    private static int ascensionSoundSegment = -1;
    /** 环绕水晶虚拟 EntityItem 缓存（不生成真实实体=仅图像无法拾取） */
    private static List<EntityItem> orbitItems;
    /** 缓存所属 world（换维度重建） */
    private static World itemsWorld;
    /** 领取降下虚拟 EntityItem 缓存（演出开始时构建；null 条目=无法解析的 ItemRef，跳过渲染） */
    private static List<EntityItem> grantItemEntities;

    private ReincarnationClientFx() {
        // 纯静态安装器，禁止实例化
    }

    // ==================== 安装契约 ====================

    /**
     * 安装全部客户端演出/输入封锁事件订阅（幂等；重复调用只生效一次）。
     * <p>
     * <b>S4 调用点（唯一接线）：</b>在 {@code ClientProxy.init(FMLInitializationEvent)}
     * 内、{@code ReincarnationNetwork.registerClientHandlers()} 之后：
     *
     * <pre>
     * try {
     *     com.miaokatze.gtit.reincarnation.client.fx.ReincarnationClientFx.install();
     *     GTInterestingThing.LOG.info("[2/3] 周目系统客户端演出/输入封锁已注册");
     * } catch (Throwable t) {
     *     GTInterestingThing.LOG.error("[2/3] 周目系统客户端演出/输入封锁注册失败", t);
     * }
     * </pre>
     * 
     * 本类不经任何注解自动注册（无 @Mod 事件总线扫描），只能由该调用进入，
     * 保证 client/fx 包仅 ClientProxy 可达（import 自查口径）。
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        ReincarnationClientFx handler = new ReincarnationClientFx();
        FMLCommonHandler.instance()
            .bus()
            .register(handler); // ClientTick
        MinecraftForge.EVENT_BUS.register(handler); // RenderWorldLast / RenderGameOverlay / MouseEvent / GuiOpenEvent
        installed = true;
        GTInterestingThing.LOG.info("[reincarnation] 客户端演出/输入封锁事件已安装（client.fx install）");
    }

    // ==================== ClientTick：演出生命周期 + 输入封锁状态机 ====================

    /**
     * 客户端 tick 驱动（GTSR SingularityClientFXHandler.java:38-41 同款 END 相位手法）：
     * 发放演出采样/收尾、升天结束判定、输入封锁的建立/维持/解除、GUI 兜底关闭。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        EntityClientPlayerMP player = mc.thePlayer;
        if (world == null || player == null) {
            // 登出/换维度加载期：全量清理（输入还原 + FX 状态清空），各只有活跃时才 log
            cleanupAll("world_null");
            return;
        }

        tickGrantEffect(world);
        tickAscension(mc, world, player);
    }

    /**
     * 发放演出推进：主线程回填起始 tick（契约 -1=未回填）→ 到时
     * {@code clearGrantEffect()} 收尾。虚拟物品缓存在回填同帧构建。
     */
    private void tickGrantEffect(World world) {
        List<GrantEffectPacket.ItemRef> items = ClientReincarnationFxState.getPendingGrantItems();
        if (items.isEmpty()) {
            return;
        }
        if (ClientReincarnationFxState.getGrantEffectStartTick() == -1L) {
            // 主线程回填（S3 契约：消费方回填起始 tick）
            ClientReincarnationFxState.setGrantEffectStartTick(world.getTotalWorldTime());
            buildGrantItemEntities(world, items);
            GTInterestingThing.LOG.info(
                "[reincarnation] 发放演出开始（客户端）：items=" + items.size()
                    + "，startTick="
                    + ClientReincarnationFxState.getGrantEffectStartTick()
                    + "，duration="
                    + GRANT_DESCENT_TICKS
                    + " tick");
            return;
        }
        long elapsed = world.getTotalWorldTime() - ClientReincarnationFxState.getGrantEffectStartTick();
        if (elapsed >= GRANT_DESCENT_TICKS) {
            ClientReincarnationFxState.clearGrantEffect();
            grantItemEntities = null;
            GTInterestingThing.LOG.info("[reincarnation] 发放演出结束（客户端）：clearGrantEffect");
        }
    }

    /**
     * 升天演出推进 + 输入封锁状态机：
     * <ul>
     * <li>未激活：若封锁仍在（外部 {@code clear()} 防御）→ 解除并 log；</li>
     * <li>激活：首次建立输入封锁（log）→ GUI 兜底关闭 →
     * 结束判定（玩家死亡立即；锚点骑乘空连续 {@link #ASCENSION_END_GRACE_TICKS} tick）→
     * {@code clearAscension()} + 解除封锁（log）。</li>
     * </ul>
     */
    private void tickAscension(Minecraft mc, World world, EntityClientPlayerMP player) {
        boolean active = ClientReincarnationFxState.isAscensionActive();
        if (!active) {
            if (inputLocked) {
                endInputLock(player, "ascension_cleared_externally");
            }
            rideNullStreak = 0;
            ascensionSoundSegment = -1; // 演出已结束：复位分段号，防下次演出叠音
            return;
        }

        if (!inputLocked) {
            beginInputLock(player);
        }
        // 替代机制 c：closeScreen 周期守卫（KeyInputEvent 不可取消的兜底，见类 javadoc）
        if (mc.currentScreen != null) {
            player.closeScreen();
        }
        // 起始 tick 主线程回填（契约同 grant：-1 = 尚未回填）；渐强音效与
        // MixinEntityRenderer 视野渐模糊共用该时长基准
        if (ClientReincarnationFxState.getAscensionStartTick() == -1L) {
            ClientReincarnationFxState.setAscensionStartTick(world.getTotalWorldTime());
            ascensionSoundSegment = 0;
            // 段 0 随回填立即起播（最低音量），此后每 SEGMENT_TICKS 重播一次音量递增
            playAscensionSegment(world, player, 0.0F);
        }
        tickAscensionSound(world, player);

        // ---- 升天结束判定 ----
        // 玩家死亡：立即结束（死亡界面需正常弹出，GuiOpenEvent 门槛已放行死亡场景）
        if (player.isDead) {
            ClientReincarnationFxState.clearAscension();
            ascensionSoundSegment = -1; // 播放结束清理：不再调度后续分段（防叠音）
            endInputLock(player, "player_dead");
            rideNullStreak = 0;
            return;
        }
        // 载具消失：锚点实体（targetEntityId，缺省本地玩家）骑乘为空连续计数
        Entity anchor = resolveAscensionAnchor(world, player);
        if (anchor == null || anchor.isDead || anchor.ridingEntity == null) {
            rideNullStreak++;
        } else {
            rideNullStreak = 0;
        }
        if (rideNullStreak >= ASCENSION_END_GRACE_TICKS) {
            ClientReincarnationFxState.clearAscension();
            ascensionSoundSegment = -1; // 演出取消/结束清理（同上）
            endInputLock(player, "carrier_gone_or_dismounted");
            rideNullStreak = 0;
        }
    }

    /**
     * 渐强音效推进：按 {@link #ASCENSION_SOUND_SEGMENT_TICKS} 分段重播，音量由 FX 状态
     * （起始 tick 派生的演出进度）线性驱动 0.25 → 1.0；分段号越界即停
     * （1.7.10 无法调制播放中实例的音量，分段重播即"渐强"的最小实现；已起播的短音效
     * 自然结束，演出结束/取消/离开仅停止调度新分段，不残留调度状态）。
     */
    private void tickAscensionSound(World world, EntityClientPlayerMP player) {
        long startTick = ClientReincarnationFxState.getAscensionStartTick();
        if (startTick < 0L || ascensionSoundSegment < 0) {
            return;
        }
        long elapsed = world.getTotalWorldTime() - startTick;
        if (elapsed < 0L) {
            return;
        }
        int segment = (int) (elapsed / ASCENSION_SOUND_SEGMENT_TICKS);
        if (segment > ascensionSoundSegment) {
            if (segment > ASCENSION_SOUND_MAX_SEGMENTS) {
                ascensionSoundSegment = -1; // 演出超长：停止调度，防叠音
                return;
            }
            ascensionSoundSegment = segment;
            float progress = Math.min(1.0F, (float) elapsed / ASCENSION_RAMP_TICKS);
            playAscensionSegment(world, player, progress);
        }
    }

    /** 按进度播放一段渐强音效（volume = MIN + (MAX-MIN) × progress；progress=0 即段 0 起播） */
    private void playAscensionSegment(World world, EntityClientPlayerMP player, float progress) {
        float volume = ASCENSION_SOUND_MIN_VOLUME
            + (ASCENSION_SOUND_MAX_VOLUME - ASCENSION_SOUND_MIN_VOLUME) * Math.min(1.0F, Math.max(0.0F, progress));
        world.playSound(player.posX, player.posY, player.posZ, ASCENSION_SOUND_KEY, volume, 1.0F, false);
    }

    /**
     * 解析升天锚点实体：{@code AscensionStartPacket.targetEntityId}（-1=未指定）→
     * {@code world.getEntityByID}（WorldClient.java:269 实现确认）；null/找不到回退本地玩家。
     */
    private static Entity resolveAscensionAnchor(World world, EntityClientPlayerMP player) {
        int id = ClientReincarnationFxState.getAscensionTargetEntityId();
        if (id >= 0) {
            Entity byId = world.getEntityByID(id);
            if (byId != null) {
                return byId;
            }
        }
        return player;
    }

    // ==================== 输入封锁（主机制：movementInput 全零替换/还原） ====================

    /**
     * 建立输入封锁：替换 {@code movementInput} 为全零实现（主机制）。
     * 替换后立即采样一次清零，防替换前一帧的残留移动/跳跃/潜行状态泄漏进当帧。
     */
    private void beginInputLock(EntityClientPlayerMP player) {
        if (inputLocked) {
            return;
        }
        lockedPlayer = player;
        savedMovementInput = player.movementInput;
        lockedMovementInput = new LockedMovementInput();
        player.movementInput = lockedMovementInput; // 替换点
        lockedMovementInput.updatePlayerMoveState();
        inputLocked = true;
        rideNullStreak = 0;
        GTInterestingThing.LOG.info("[reincarnation] 升天输入封锁开始：movementInput 已替换为全零" + "（仅保留鼠标视角）");
    }

    /**
     * 解除输入封锁：还原原 movementInput 实例。
     * 玩家实例已换新（死亡重生/换维度）时新实例自带原生
     * {@code MovementInputFromOptions}，无需也无法还原，跳过并记录。
     */
    private void endInputLock(EntityClientPlayerMP player, String reason) {
        if (!inputLocked) {
            return;
        }
        if (player == lockedPlayer && lockedMovementInput != null && player.movementInput == lockedMovementInput) {
            player.movementInput = savedMovementInput; // 还原点
        } else {
            GTInterestingThing.LOG.info("[reincarnation] 升天输入封锁解除（" + reason + "）：玩家实例已变更，跳过 movementInput 还原");
        }
        inputLocked = false;
        lockedPlayer = null;
        savedMovementInput = null;
        lockedMovementInput = null;
        rideNullStreak = 0;
        GTInterestingThing.LOG.info("[reincarnation] 升天输入封锁结束：" + reason);
    }

    /**
     * 全量清理（登出/换维度）：仅在确有活跃内容时清空契约状态并 log 一行。
     */
    private void cleanupAll(String scene) {
        boolean touched = false;
        if (inputLocked) {
            if (lockedPlayer != null) {
                endInputLock(lockedPlayer, "cleanup_" + scene);
            } else {
                inputLocked = false;
                lockedMovementInput = null;
            }
            touched = true;
        }
        boolean ascActive = ClientReincarnationFxState.isAscensionActive();
        boolean grantActive = !ClientReincarnationFxState.getPendingGrantItems()
            .isEmpty();
        boolean countdownActive = ClientReincarnationFxState.getCountdownDeadlineMillis() != 0L;
        if (ascActive || grantActive || countdownActive || ClientReincarnationFxState.getLastSync() != null) {
            ClientReincarnationFxState.clear();
            touched = true;
        }
        orbitItems = null;
        grantItemEntities = null;
        itemsWorld = null;
        rideNullStreak = 0;
        ascensionSoundSegment = -1; // 登出/换维度离开：停调度防叠音（已起播短音效自然结束）
        if (touched) {
            GTInterestingThing.LOG.info("[reincarnation] 客户端 FX 状态全量清理（" + scene + "）");
        }
    }

    /**
     * 全零 movementInput（主机制载体）：1.7.10 的
     * {@code MovementInputFromOptions.updatePlayerMoveState()} 每帧从键盘采样
     * （MovementInputFromOptions.java:16-52），本实现覆写采样为恒清零——
     * WASD/跳/潜行全部无效；疾跑（moveForward>=0.8 判定）与骑乘潜行下车
     * （isSneaking()=movementInput.sneak）被天然封死（EntityPlayerSP.java:209/221/489）。
     */
    private static final class LockedMovementInput extends MovementInput {

        @Override
        public void updatePlayerMoveState() {
            this.moveStrafe = 0.0F;
            this.moveForward = 0.0F;
            this.jump = false;
            this.sneak = false;
        }
    }

    // ==================== 输入封锁（辅机制：可取消事件） ====================

    /**
     * 替代机制 a：升天封锁期间取消全部鼠标按钮事件（攻击/使用/滚轮换挡）。
     * <p>
     * 可取消性实读证据：MouseEvent.java:13 {@code @Cancelable}；取消效果
     * Minecraft.java:1778 {@code if (ForgeHooksClient.postMouseEvent()) continue;}
     * （跳过该事件全部 vanilla 处理）。鼠标视角不经过本事件（EntityRenderer
     * Mouse.getDX/DY 直读），故视角保留。注意 cpw 的
     * {@code InputEvent.MouseInputEvent}（Minecraft.java:1826，处理后发射）
     * 与本事件是两回事——前者不可取消，本类不订阅（见类 javadoc 核实结论）。
     */
    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (inputLocked) {
            event.setCanceled(true);
        }
    }

    /**
     * 替代机制 b：升天封锁期间阻止打开任何新 GUI（背包/聊天/Esc 菜单等）。
     * <p>
     * 可取消性实读证据：GuiOpenEvent.java:14 {@code @Cancelable}；发射点
     * Minecraft.java:841（displayGuiScreen 入口，取消即不打开、不触发 onGuiClosed）。
     * 放行两类：<b>{@code gui == null}</b>（关闭路径——closeScreen 守卫依赖它关屏）；
     * <b>theWorld/thePlayer 缺失或玩家已死亡</b>（死亡界面 GuiGameOver 由
     * displayGuiScreen(null) 强制转换而来（Minecraft.java:835-837），掉线/回主菜单
     * 前 theWorld 已置空（loadWorld），二者若被取消会卡死玩家，必须放行）。
     */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!inputLocked || event.gui == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0.0F) {
            return;
        }
        event.setCanceled(true);
    }

    // ==================== 世界层渲染：升天环绕 + 领取降下 ====================

    /**
     * 世界末尾渲染（相机空间）：升天环绕水晶 + 发放螺旋降下物品，均以
     * 虚拟 {@code EntityItem} 经 {@code RenderManager.instance.renderEntityWithPosYaw}
     * 绘制（仅图像、非真实实体，无法拾取），后叠 additive glow billboard（GTSR 手法）。
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        EntityClientPlayerMP player = mc.thePlayer;
        if (world == null || player == null) {
            return;
        }
        boolean ascActive = ClientReincarnationFxState.isAscensionActive();
        boolean grantActive = !ClientReincarnationFxState.getPendingGrantItems()
            .isEmpty() && ClientReincarnationFxState.getGrantEffectStartTick() != -1L;
        if (!ascActive && !grantActive) {
            return;
        }
        float pt = event.partialTicks;
        // 相机偏移：lastTickPos 插值（renderEntityWithPosYaw 不做相机扣除，需传相机相对坐标）
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * pt;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * pt;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * pt;

        List<double[]> glowSpots = new ArrayList<>();
        if (ascActive) {
            renderAscensionOrbit(world, resolveAscensionAnchor(world, player), camX, camY, camZ, pt, glowSpots);
        }
        if (grantActive) {
            renderGrantDescent(world, player, camX, camY, camZ, pt, glowSpots);
        }
        if (!glowSpots.isEmpty()) {
            drawGlowBillboards(mc, glowSpots, camX, camY, camZ);
        }
    }

    /**
     * 升天环绕：以锚点实体为圆心，{@link #ASCENSION_CRYSTAL_COUNT} 枚轮回水晶
     * 常角速度环绕（半径 1.5~2 正弦波动、高度头顶 ±1 正弦波动），
     * 每枚伴一枚 glow billboard。
     */
    private void renderAscensionOrbit(World world, Entity anchor, double camX, double camY, double camZ, float pt,
        List<double[]> glowSpots) {
        if (anchor == null) {
            return;
        }
        ensureOrbitItems(world);
        if (orbitItems == null) {
            return;
        }
        double ax = anchor.lastTickPosX + (anchor.posX - anchor.lastTickPosX) * pt;
        double ay = anchor.lastTickPosY + (anchor.posY - anchor.lastTickPosY) * pt;
        double az = anchor.lastTickPosZ + (anchor.posZ - anchor.lastTickPosZ) * pt;
        double fxTime = world.getTotalWorldTime() + pt;
        float basePhase = (float) (fxTime * ASCENSION_RAD_PER_TICK);
        float headBase = anchor.getEyeHeight() + ASCENSION_HEAD_OFFSET;

        for (int i = 0; i < orbitItems.size(); i++) {
            float angle = (float) (basePhase + i * (Math.PI * 2.0D / orbitItems.size()));
            float radius = ASCENSION_ORBIT_RADIUS
                + ASCENSION_RADIUS_AMPLITUDE * (float) Math.sin(basePhase * 0.35D + i * 1.3D);
            float height = headBase + ASCENSION_HEIGHT_AMPLITUDE * (float) Math.sin(basePhase * 0.5D + i * 0.9D);
            double x = ax + Math.cos(angle) * radius;
            double y = ay + height;
            double z = az + Math.sin(angle) * radius;
            EntityItem item = orbitItems.get(i);
            applyVirtualItemFrame(item, world, x, y, z, (int) (fxTime * 2.0D), i * 0.7F);
            RenderManager.instance
                .renderEntityWithPosYaw(item, x - camX, y - camY, z - camZ, (float) (fxTime * 2.5D + i * 47.0D), pt);
            glowSpots.add(new double[] { x, y, z, ASCENSION_GLOW_SIZE });
        }
    }

    /**
     * 领取降下：物品从头顶 {@link #GRANT_START_HEIGHT} 格高处沿收拢螺旋依次降下至
     * 玩家位置（错峰 {@link #GRANT_STAGGER_TICKS}，单件 smoothstep 缓落），
     * 全程伴 additive glow（"期间物品发光明亮"）。
     */
    private void renderGrantDescent(World world, EntityClientPlayerMP player, double camX, double camY, double camZ,
        float pt, List<double[]> glowSpots) {
        if (grantItemEntities == null) {
            return;
        }
        double elapsed = world.getTotalWorldTime() + pt - ClientReincarnationFxState.getGrantEffectStartTick();
        if (elapsed < 0.0D || elapsed > GRANT_DESCENT_TICKS) {
            return;
        }
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * pt;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * pt;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * pt;

        for (int i = 0; i < grantItemEntities.size(); i++) {
            EntityItem item = grantItemEntities.get(i);
            if (item == null) {
                continue; // 无法解析的 ItemRef（本客户端缺注册），跳过渲染
            }
            double local = elapsed - (double) i * GRANT_STAGGER_TICKS;
            if (local <= 0.0D) {
                continue; // 未轮到该件（"依次"）
            }
            double t = Math.min(1.0D, local / GRANT_PER_ITEM_TICKS);
            double ease = t * t * (3.0D - 2.0D * t); // smoothstep 缓入缓出
            float angle = (float) (local * GRANT_SPIRAL_RAD_PER_TICK);
            float radius = GRANT_SPIRAL_RADIUS * (1.0F - (float) t);
            double x = px + Math.cos(angle) * radius;
            double y = py + GRANT_START_HEIGHT * (1.0D - ease);
            double z = pz + Math.sin(angle) * radius;
            applyVirtualItemFrame(item, world, x, y, z, (int) ((world.getTotalWorldTime() + pt) * 2.0D), i * 0.55F);
            RenderManager.instance.renderEntityWithPosYaw(
                item,
                x - camX,
                y - camY,
                z - camZ,
                (float) (world.getTotalWorldTime() * 3.0D + i * 31.0D),
                pt);
            // glow 强度随落定收敛（快落地时收一点，落定即演出收尾）
            float glowScale = GRANT_GLOW_SIZE * (1.0F - 0.35F * (float) t);
            glowSpots.add(new double[] { x, y, z, glowScale });
        }
    }

    /**
     * 构建/重建环绕水晶虚拟 EntityItem（GTITItemList.ReincarnationCrystal.get(1)，
     * 不入世界=无法拾取）；world 实例变化（换维度）时重建。
     */
    private void ensureOrbitItems(World world) {
        ItemStack stack = GTITItemList.ReincarnationCrystal.get(1);
        if (stack == null || stack.getItem() == null) {
            return; // 水晶物品未注册（异常场景），演出静默跳过，不崩客户端
        }
        if (orbitItems == null || itemsWorld != world || orbitItems.size() != ASCENSION_CRYSTAL_COUNT) {
            orbitItems = new ArrayList<>(ASCENSION_CRYSTAL_COUNT);
            for (int i = 0; i < ASCENSION_CRYSTAL_COUNT; i++) {
                // 虚拟实体：仅本地图像载体，不入世界无 tick，无法拾取
                orbitItems.add(new EntityItem(world, 0.0D, 0.0D, 0.0D, stack.copy()));
            }
            itemsWorld = world;
        }
    }

    /**
     * 构建领取降下虚拟 EntityItem 列表：ItemRef.id 经注册表解析
     * （GameData.itemRegistry + FMLControlledNamespacedRegistry.getObject，
     * FMLControlledNamespacedRegistry.java:184-190——未命中返回 null
     * （items 无缺省对象），直接跳过该条目，不做任何回退猜测）；
     * 首查全 id、失败再剥 "minecraft:" 前缀二查（1.7.10 vanilla 注册键为裸名，
     * 容错契约样例 "minecraft:diamond"）。
     */
    private void buildGrantItemEntities(World world, List<GrantEffectPacket.ItemRef> items) {
        grantItemEntities = new ArrayList<>();
        int limit = Math.min(items.size(), GRANT_MAX_ANIMATED_ITEMS);
        for (int i = 0; i < limit; i++) {
            GrantEffectPacket.ItemRef ref = items.get(i);
            ItemStack stack = resolveItemStack(ref);
            if (stack == null) {
                grantItemEntities.add(null); // 占位保持"依次"错峰节奏
                continue;
            }
            grantItemEntities.add(new EntityItem(world, 0.0D, 0.0D, 0.0D, stack));
        }
    }

    /**
     * ItemRef → ItemStack（仅演出展示用；实际发放由服务端完成，与本解析无关）。
     */
    private static ItemStack resolveItemStack(GrantEffectPacket.ItemRef ref) {
        if (ref == null || ref.id == null || ref.id.isEmpty()) {
            return null;
        }
        Item item = (Item) Item.itemRegistry.getObject(ref.id); // 原始类型注册表，getObject 静态签名返回 Object
        if (item == null && ref.id.startsWith("minecraft:")) {
            item = (Item) Item.itemRegistry.getObject(ref.id.substring("minecraft:".length()));
        }
        if (item == null) {
            return null;
        }
        return new ItemStack(item, 1, ref.meta);
    }

    /**
     * 逐帧驱动虚拟 EntityItem 的展示字段（age=自旋相位、hoverStart=错相浮动、
     * posX/Y/Z 供亮度采样取正确方块光照）。
     * <p>
     * 抑制阴影说明：本映射下 Entity 无公开 shadowSize 字段（仅派生 getter
     * {@code getShadowSize()}=height/2，Entity.java:1743），虚拟物品（height=0.5）
     * 只在正下方地面留一枚 0.25 格的淡影，为原版飘浮物品同款视觉，接受不处理。
     */
    private static void applyVirtualItemFrame(EntityItem item, World world, double x, double y, double z, int age,
        float hoverStart) {
        item.worldObj = world;
        item.posX = x;
        item.posY = y;
        item.posZ = z;
        item.age = age;
        item.hoverStart = hoverStart;
    }

    /**
     * additive glow billboard（GTSR GTSRBeamFX 手法复刻）：
     * {@code GL_SRC_ALPHA/GL_ONE} 混合 + 深度只读 + 全亮光值 + glow 贴图，
     * 原版铭牌旋转法朝向相机，绘制完完整恢复混合/深度/剔除状态。
     * 每格 double[] = {x, y, z, halfSize}。
     */
    private void drawGlowBillboards(Minecraft mc, List<double[]> spots, double camX, double camY, double camZ) {
        GL11.glPushMatrix();
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // additive（GTSR 同款）
        mc.renderEngine.bindTexture(GLOW_TEXTURE);
        Tessellator tess = Tessellator.instance; // 本映射为公开静态字段（Tessellator.java:72），非 getInstance()
        tess.startDrawingQuads();
        tess.setBrightness(0x00F000F0); // 全亮光值（GTSR 同款）
        tess.setColorRGBA_F(0.75F, 0.95F, 1.0F, 0.85F);
        for (double[] spot : spots) {
            GL11.glPushMatrix();
            GL11.glTranslated(spot[0] - camX, spot[1] - camY, spot[2] - camZ);
            // 原版铭牌朝向法（Render.java:352-353 同款）
            GL11.glRotatef(-RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(RenderManager.instance.playerViewX, 1.0F, 0.0F, 0.0F);
            float s = (float) spot[3];
            tess.addVertexWithUV(-s, -s, 0.0D, 0.0D, 0.0D);
            tess.addVertexWithUV(-s, s, 0.0D, 0.0D, 1.0D);
            tess.addVertexWithUV(s, s, 0.0D, 1.0D, 1.0D);
            tess.addVertexWithUV(s, -s, 0.0D, 1.0D, 0.0D);
            GL11.glPopMatrix();
        }
        tess.draw();
        // 状态完整恢复（GTSR 同款收尾），防污染同层后续渲染
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    // ==================== HUD 层：HELMET Post（BQ 手法） ====================

    /**
     * HUD：HELMET Post 层绘制（BQ QuestNotification.java:223-240 手法）——
     * ① 发放演出期间中央炫彩大字（HSL 色相按 tick 循环 + glScalef 放大）；
     * ② 倒计时中央大数字 + 说明行（deadline 驱动，客户端只显示，归零由服务端处理）；
     * ③ 倒计时归零未清期间附加提示行（该时间线已轮回）。
     * （v1.9.1：原 ⓪ 飞升屏幕渐变 overlay 已整体摘除，视野表现改由
     * MixinEntityRenderer 视野渐模糊承担。）
     */
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HELMET) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || mc.thePlayer == null) {
            return;
        }
        int width = event.resolution.getScaledWidth();
        int height = event.resolution.getScaledHeight();
        FontRenderer font = mc.fontRenderer; // 本映射字段名（Minecraft.java:218）

        // ① 发放庆祝大字（演出期间全程显示）
        boolean grantActive = !ClientReincarnationFxState.getPendingGrantItems()
            .isEmpty() && ClientReincarnationFxState.getGrantEffectStartTick() != -1L;
        if (grantActive) {
            drawCelebration(world, font, width, height);
        }

        // ② ③ 倒计时（deadline != 0 = 有进行中的倒计时，契约口径）
        long deadline = ClientReincarnationFxState.getCountdownDeadlineMillis();
        if (deadline != 0L) {
            long remaining = deadline - Minecraft.getSystemTime();
            if (remaining > 0L) {
                int seconds = (int) ((remaining + 999L) / 1000L); // 向上取整显示
                drawScaledCenteredText(
                    font,
                    String.valueOf(seconds),
                    width / 2,
                    height / 4,
                    COUNTDOWN_NUMBER_SCALE,
                    0xFFFFFFFF);
                drawScaledCenteredText(
                    font,
                    I18n.format(LANG_COUNTDOWN_LINE),
                    width / 2,
                    height / 4 + 28,
                    1.0F,
                    0xFFD0D0D0);
            } else {
                // 归零后等待服务端处理：客户端只显示提示行，不自行清 deadline
                drawScaledCenteredText(
                    font,
                    I18n.format(LANG_TIMELINE_ENDED),
                    width / 2,
                    height / 4 + 10,
                    1.0F,
                    0xFFFFD24A);
            }
        }
    }

    /**
     * 中央炫彩大字：色相按 {@link #CELEBRATION_HUE_CYCLE_TICKS} tick 循环（炫彩），
     * 字号随 tick 呼吸脉动（glScalef 放大，死亡大标题手法式）。
     */
    private void drawCelebration(World world, FontRenderer font, int width, int height) {
        long fxTime = world.getTotalWorldTime();
        float hue = (float) (fxTime % CELEBRATION_HUE_CYCLE_TICKS) / CELEBRATION_HUE_CYCLE_TICKS;
        int rgb = hsvToRgb(hue, 0.85F, 1.0F);
        float scale = CELEBRATION_BASE_SCALE + 0.15F * (float) Math.sin(fxTime * 0.15D);
        drawScaledCenteredText(font, I18n.format(LANG_CELEBRATION), width / 2, height / 4 - 8, scale, 0xFF000000 | rgb);
    }

    /**
     * 缩放居中文字（glPushMatrix + glScalef + drawStringWithShadow，死亡大标题手法式；
     * 1.7.10 FontRenderer.drawStringWithShadow 仅 int 重载（FontRenderer.java:288），
     * 缩放矩阵下 int 坐标仍具亚像素精度，与原版缩放文字一致）。
     */
    private static void drawScaledCenteredText(FontRenderer font, String text, int cx, int cy, float scale, int argb) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0.0F);
        GL11.glScalef(scale, scale, scale);
        font.drawStringWithShadow(text, -font.getStringWidth(text) / 2, -font.FONT_HEIGHT / 2, argb);
        GL11.glPopMatrix();
    }

    /**
     * HSV → RGB（标准色彩空间换算，纯函数；刻意不用 java.awt.Color，
     * 避免客户端引入 AWT 头初始化）。
     */
    private static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) h;
        float frac = h - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * frac);
        float t = value * (1.0F - saturation * (1.0F - frac));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0:
                r = value;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = value;
                b = p;
                break;
            case 2:
                r = p;
                g = value;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = value;
                break;
            case 4:
                r = t;
                g = p;
                b = value;
                break;
            default:
                r = value;
                g = p;
                b = q;
                break;
        }
        return ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }
}
