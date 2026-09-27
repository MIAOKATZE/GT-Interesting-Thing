package com.miaokatze.gtit.mixin.thaum;

import java.util.ArrayList;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.crossmod.taum.PocketVisSupport;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.nodes.INode;
import thaumcraft.common.tiles.TileJarNode;
import thaumcraft.common.tiles.TileWandPedestal;

/**
 * ★R96 S10 · P3：基座<b>给口袋充能</b>（搬运腿）。与 P1/P2 同轮，否则就是
 * 「放得进、永远不涨」——{@code r96-plan.md} §5 S10 点名的典型绿≠有效。
 * <p>
 * <b>落点为什么只有 {@code updateEntity} 的 TAIL</b>（TC {@code TileWandPedestal.java:173-358}
 * = {@code func_145845_h}，取证 {@code r96-ret10.md} §4 P3）：
 * <ul>
 * <li>{@code :207} 法杖支与 {@code :282} 护身符支是一条 {@code if / else if} 的
 * {@code instanceof} 具体类判据 ⇒ 口袋两支都不命中，<b>原逻辑自然空转</b>，TAIL 支自己按
 * {@code this.nodes} 搬运，不去改任何一个已有分支；</li>
 * <li>{@code :169-171 canUpdate()} 返回 true ⇒ 基座确实被 tick；{@code :112-139} 的 NBT 读写是
 * 通用的 {@code ItemStack.writeToNBT}（无 wand 专属 owner/绑定逻辑）⇒ 存档侧对口袋安全。</li>
 * </ul>
 * <p>
 * <b>搬运节拍逐字对齐 TC</b>：{@code :186} 的 {@code counter % 5 == 0} 一轮只搬 1 点
 * （{@code :231} 一次 {@code addVis(...,1,...)} + {@code :232} 一次 {@code takeFromContainer(aspect,1)}，
 * 且 {@code break label141} 让一轮最多一笔）。本腿同样「{@code % 5} 闸门 + 一轮一笔 + {@code return}」，
 * 速率与法杖完全对称，不给口袋开出快通道。节点侧也照 {@code min = 1}（{@code :209}，
 * 即节点里该要素要 {@code > 1} 才抽，留 1 点不把节点抽干）。
 * <p>
 * ★<b>三条已知局限，都写在这里而不是留给玩家猜</b>：
 * <ol>
 * <li><b>TAIL 读不到局部 {@code recalc}</b>（{@code :180} 声明、{@code :278-280}/{@code :347-349} 赋值、
 * {@code :354-356} 消费）。TAIL 只能按 {@code @Shadow} 拿字段，所以 {@code recalc} 这条
 * 「本轮没抽到就 100 tick 重扫节点」的分支<b>不覆盖口袋</b>：口袋抽不满时不会触发
 * {@code findNodes()} 重扫（影响仅是「节点空了以后最长 100 tick 才重扫」，与 TC 原生空基座同形）。</li>
 * <li><b>不置 {@code somethingChanged} 就不触发 {@code :181-184} 的 {@code markBlockForUpdate}</b>
 * （比较器/外观刷新）。本腿<b>每成功入账一笔就置一次</b>，让 TC 自己的 {@code % 20} 支去刷方块，
 * 不另开第二条刷新路径。</li>
 * <li><b>不动 {@code draining} / {@code drainX}/{@code drainY}/{@code drainZ} / {@code drainColor}</b>：
 * 那四个字段是 TC 客户端<b>自己在自己那一遍 {@code updateEntity} 里现算现写</b>的
 * （{@code :235} 的 {@code worldObj.isRemote} 闸就是证据，没有同步包），而口袋那一支在客户端也不会命中
 * 任何 {@code instanceof} 分支 ⇒ 要让抽能光束亮，客户端得<b>再复刻一遍节点扫描</b>。
 * 本轮刻意不做（本腿整个 {@code isRemote} 早退），代价是<b>基座给口袋充能时没有光束</b>，
 * 已列入 README 代价。留一个 sticky 的 {@code draining = true} 反而是有害的：它永不被复位
 * （复位点在 {@code :215}/{@code :286} 两个分支里，口袋两支都不跑）。</li>
 * </ol>
 * <p>
 * <b>只认 6 条元始，不做复合折算</b>：口袋容量表的行就是 {@link PocketConstants#PRIMAL_TAGS}
 * （R96 P-7 定案），而 TC 的复合→元始折算（{@code :188-190 hasThingy} + {@code :252 reduceToPrimals}）
 * 依赖基座上方的 {@code blockStoneDevice} meta 8。本腿<b>不</b>接这条支：复合要素一律不抽，
 * 玩家看到的是「只有元始节点能给口袋充」。这是刻意收窄（少一条与 Hodgepodge/SalisArcana 的 CV 支
 * 交叉的状态），代价进 README。
 * <p>
 * <b>★注入点争抢（正面写，不照抄别人的结论）</b>：{@code updateEntity} 的 TAIL 在本包内是
 * <b>四枚共存</b>——Hodgepodge {@code MixinTileWandPedestal}（1 枚 {@code @At("FIELD",
 * target="…draining:Z", ordinal=0, shift=AFTER)} + 1 枚 TAIL）与 {@code MixinTileWandPedestal_VisDuplication}
 * （2 枚 {@code @At("INVOKE")} 分别钉在 {@code ItemWandCasting.addVis} / {@code ItemAmuletVis.addVis}）、
 * SalisArcana {@code MixinTileWandPedestal_WandPedestalCv}（{@code draining} 的 ordinal=0 与 ordinal=3
 * 两处 FIELD + TAIL）＋本枚，读数来自对三个投放件的 {@code javap -v} 直读。两枚既有 mod 已经在
 * <b>同宿主同方法</b>共存 = TAIL 多枚共存本身可跑（间接可证）；但 TAIL 之间的<b>执行顺序 = 施加顺序 = mod 加载顺序</b>，
 * 若某一方在 TAIL 里改了 {@code nodes} / {@code draining}，我们读到的就是它改后的值 ⇒
 * 属真机项（{@code r96-plan.md} §8 V-6），<b>不得离线断言</b>。
 * 本腿对争抢的两处自我约束：① 只在 {@code % 5} 那一拍动手、其余整 tick 直接返回（不放大共享状态的窗口）；
 * ② 写完只置 {@code somethingChanged}，不复位也不置 {@code draining}／{@code nodes}，
 * 免得成为别人 locator 里那个「被改过的值」。
 * <p>
 * 仅在 TC 已加载时由 {@code com.miaokatze.gtit.asm.GtitThaumLateMixinLoader} 条件施加。
 */
@Mixin(value = TileWandPedestal.class, remap = false)
public abstract class MixinTileWandPedestal_PocketCharge extends TileEntity {

    /** TC 自有字段（明文不混淆）⇒ 全部 {@code remap = false}；访问级与宿主一致（包私有）。 */
    @Shadow(remap = false)
    int counter;

    @Shadow(remap = false)
    boolean somethingChanged;

    @Shadow(remap = false)
    ArrayList<ChunkCoordinates> nodes;

    /**
     * 口袋充能一笔。{@code method = "updateEntity"} 是 MC 派生名（{@code TileEntity.updateEntity} =
     * {@code func_145845_h}）⇒ {@code remap = true}，且这条形状有<b>三个</b>已知全绿构件的 refmap 背书：
     * Hodgepodge 与 SalisArcana 都把它对 {@code TileWandPedestal} 的注入映射成
     * {@code Lthaumcraft/common/tiles/TileWandPedestal;func_145845_h()V}（本次直读两份 refmap json 所得）。
     */
    @Inject(method = "updateEntity", at = @At("TAIL"), remap = true)
    private void gtit$chargePocketFromNode(CallbackInfo ci) {
        // 服务端独占：入账写的是载体栈 NBT，客户端跑一遍就是双写（口径同 PocketWandChargeDriver）
        if (this.worldObj == null || this.worldObj.isRemote) {
            return;
        }
        // 与 TC :186 同拍；不占别人的 tick
        if (this.counter % 5 != 0) {
            return;
        }
        final ArrayList<ChunkCoordinates> scan = this.nodes;
        if (scan == null || scan.isEmpty()) {
            return;
        }
        // 读槽走 MC 接口（owner = IInventory，映射面由 AP 负责），不 shadow 宿主的 getStackInSlot
        final ItemStack stack = ((IInventory) (Object) this).getStackInSlot(0);
        // 认栈 + 魔法使开关（关掉 ⇒ 一次 NBT 都不摸，S1 的执法点）
        if (!PocketVisSupport.mageActive(stack)) {
            return;
        }
        for (ChunkCoordinates co : scan) {
            if (co == null) {
                continue;
            }
            final TileEntity te = this.worldObj.getTileEntity(co.posX, co.posY, co.posZ);
            // 与 TC :223 同形：认 INode、但排除罐子里的节点（TileJarNode）
            if (!(te instanceof INode) || te instanceof TileJarNode) {
                continue;
            }
            final INode node = (INode) te;
            final AspectList inNode = node.getAspects();
            if (inNode == null) {
                continue;
            }
            for (String tag : PocketConstants.PRIMAL_TAGS) {
                final Aspect aspect = PocketVisSupport.aspectOf(tag);
                if (aspect == null) {
                    continue;
                }
                // 与 TC 的 min = 1 同口径：节点里这一条要 > 1 点才抽，不把节点抽干
                if (inNode.getAmount(aspect) <= 1) {
                    continue;
                }
                // ★整笔预检在前（一笔 = 1 点），判不过就一分不抽
                if (PocketVisSupport.roomFor(stack, tag) < 1) {
                    continue;
                }
                if (!node.takeFromContainer(aspect, 1)) {
                    continue;
                }
                if (PocketVisSupport.credit(stack, aspect, 1) <= 0) {
                    // 抽到了却没入账（理论不可达：预检与写账同一份判据）⇒ 原路退回，绝不吞节点的点
                    node.addToContainer(aspect, 1);
                    continue;
                }
                // 让 TC 自己的 :181-184 去 markBlockForUpdate（不另开刷新路径）
                this.somethingChanged = true;
                return;
            }
        }
    }
}
