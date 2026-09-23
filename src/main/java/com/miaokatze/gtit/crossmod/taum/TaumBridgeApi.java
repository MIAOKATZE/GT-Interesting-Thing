package com.miaokatze.gtit.crossmod.taum;

import net.minecraft.item.ItemStack;

/**
 * Thaumcraft / Thaumic Tinkerer 桥接面的形状定义。
 * <p>
 * 本接口<b>不出现任何 TC/TT 类型</b>（只用 MC 的 {@link ItemStack} 与本包值对象），
 * 因此 {@link TaumCompat} 可以安全持有它并做强制转换，即使 Thaumcraft 缺席
 * ——接口本身的加载不会牵动 {@link TaumBridge}。
 * <p>
 * 唯一实现 {@link TaumBridge} 由 {@link TaumCompat} 在双哨兵（{@code Loader.isModLoaded}
 * + {@code Class.forName} 探测）都通过后才用 {@code Class.forName(...).newInstance()} 反射装配，
 * TC 不在场时该实现类<b>永不被加载</b>，不会触发 {@code NoClassDefFoundError}。
 * <p>
 * 契约：所有方法都<b>不得抛出</b>给上层（实现内以 try/catch(Throwable) 兜底并降级为
 * 「不可用」空结果），且都允许 {@code null} 入参。
 */
public interface TaumBridgeApi {

    /**
     * @return 运行时派生的 aspect 注册序 tag 快照（{@code Aspect.aspects.keySet()} 迭代序）；
     *         数量不假设恰为 48（addon 可追加）
     */
    String[] aspectOrder();

    /**
     * @param tag aspect tag
     * @return RGB 染色（{@code Aspect.getColor()}），未知或不可用时 -1
     */
    int colorOf(String tag);

    /**
     * @param tag aspect tag
     * @return 显示名（{@code Aspect.getName()}），未知时回落为 tag 本身
     */
    String nameOf(String tag);

    /**
     * 蒸馏判据与产出集合（照 TC4 {@code TileAlchemyFurnace.canSmelt()} 的查表口径）。
     *
     * @param stack 待蒸馏物品
     * @return aspect → 点数（原量）；空结果即「不可蒸馏 / TC 缺席 / 入参为 null」
     */
    TaumAspectAmounts distill(ItemStack stack);

    /**
     * 读容器内源质（晶化源质、源质瓶，以及任何实现 {@code IEssentiaContainerItem} 的第三方容器）。
     *
     * @param stack 容器物品
     * @return 内容快照；空结果表示「非容器 / 容器是空的 / 不可用」
     */
    TaumAspectAmounts readContainer(ItemStack stack);

    /**
     * 单容器容量档位。
     *
     * @param stack 容器物品
     * @return 晶化源质 {@value TaumDistillRules#CRYSTAL_CAPACITY}、源质瓶
     *         {@value TaumDistillRules#PHIAL_CAPACITY}；识别为容器但容量无证据时
     *         {@value TaumDistillRules#CAPACITY_UNKNOWN}；非容器
     *         {@value TaumDistillRules#CAPACITY_NOT_A_CONTAINER}
     */
    int capacityOf(ItemStack stack);

    /**
     * ★<b>R91-⑦：源质伪物品（配方显示件）承载的 aspect tag</b>——「拖入栈 → tag」反解的第三方支。
     * <p>
     * 存在理由：NEI 的物品列表/书签面板里，源质条目<b>不只有容器</b>。AspectRecipeIndex 会往面板塞
     * 一种伪物品（game id {@code aspectrecipeindex:aspect}），它<b>不是</b>
     * {@code IEssentiaContainerItem}、也<b>不在</b> TC 蒸馏注册表里 ⇒ 本仓既有两条探针
     * （{@link #readContainer} / {@link #distill}）对它<b>结构性双空</b>（取证
     * {@code plan/_taskpack/r91-ret-nei-carrier.md} §2 的 jar 实拆）。玩家拖它声明绑定就会被拒收，
     * 这正是用户报的「无法绑定标记」的第二条根因（根-3）。
     * <p>
     * <b>三条硬边界</b>（本方法的实现必须逐条满足，判据侧由 {@code verify-pocket.sh} 的 {@code R91-a2} 段钉）：
     * <ol>
     * <li><b>注册名精确白名单</b>：只认 {@code aspectrecipeindex} 的 {@code aspect} 那一个 Item
     * <b>身份</b>（按注册名解析出的 {@code Item} 实例做 {@code ==} 比对）。<b>禁止</b>扫全注册表、
     * 禁止按 unlocalized name 前缀匹配、禁止猜别家 mod 的 key 写法；</li>
     * <li><b>唯一键</b>：读 NBT 的 String 型 aspect 键（ARI 的 {@code ItemAspect#setAspect} 只写这一个键，
     * 实测其类内字符串常量全集里没有任何数量字段）；</li>
     * <li><b>TC 复核</b>：解出的字符串必须过 {@code Aspect.getAspect(tag) != null} 才算数——
     * NBT 是第三方写的，不接受自证；返回的是<b>复核后的 canonical tag</b>。</li>
     * </ol>
     * ★<b>只声明、绝不入库存计点</b>（R91-⑦）：本方法<b>不</b>参与任何点数/消耗路径；计点闸门仍是
     * {@code TaumDistillRules#PHIAL_CAPACITY} / {@code CRYSTAL_CAPACITY} 两档（伪物品天然落
     * {@code CAPACITY_NOT_A_CONTAINER}），<b>不新增第二道门</b>。
     * <p>
     * 降级：TC 或 ARI 缺席、无 NBT、键缺失、非白名单物品、运行期漂移 ⇒ {@code null}（永不抛出）。
     */
    String pseudoAspectTag(ItemStack stack);

    /**
     * 往容器里注入源质（合并语义：容器已有<b>不同</b> aspect 时整笔拒绝，不做混合、不清空）。
     * <p>
     * 晶化源质不走本方法（1 点/枚；★R88 载体改判后晶已退役为「只读不产」，见 {@link #newCrystalStack}
     * 上那条 ★R88 注），返回 0。
     * <p>
     * <b>单容器语义</b>：本方法按「一个容器」写入，不拆堆也不改动 {@code stackSize}；
     * {@code stackSize > 1} 时改动的是整堆共享的 NBT，调用方须自行先拆成 1 个。
     *
     * @param stack  容器物品
     * @param tag    aspect tag
     * @param points 期望注入点数
     * @return 实际注入点数（0 表示未改动容器）
     */
    int addEssentia(ItemStack stack, String tag, int points);

    /**
     * 取空容器（读出全部源质并把容器清成空态；瓶会退回 meta 0 的空瓶态）。
     *
     * @param stack 容器物品
     * @return 被取出的内容快照；空结果表示没取出任何东西（容器未改动）
     */
    TaumAspectAmounts drainAll(ItemStack stack);

    /**
     * 产出晶化源质（1 点 = 1 个晶，可堆到 64）。
     * <p>
     * ★★<b>R88 载体改判后本方法已退役为「只读不产」</b>（自立口径 C2）：源质盘的点数、上传/下传的搬运件、
     * 入槽与绑定接受的形状一律改走 {@link #newFilledContainer(String, int)}（一瓶
     * {@code TaumDistillRules#PHIAL_CAPACITY} 点）。本方法与 {@code CRYSTAL_CAPACITY} 档位<b>保留</b>只为
     * 识别<b>存档里已有的旧晶</b>——旧晶仍能被读回点数溶进盘，不吃件；但口袋任何一条路都不再产出晶，
     * 生产调用方已归零。
     *
     * @param tag    aspect tag
     * @param points 点数，实际产出 {@code min(points, 64)} 个
     * @return 新物品栈；points &lt;= 0、tag 未知或不可用时 null
     */
    ItemStack newCrystalStack(String tag, int points);

    /**
     * 产出一个装好源质的容器：优先第三方罐（Thaumic Tinkerer 一类，运行时探测），
     * 缺席时回落 TC 源质瓶（{@code ItemEssence}，一次 8 点）。
     *
     * @param tag    aspect tag
     * @param points 期望装点数；瓶按 8 点封顶（不足 8 按实际点数装）
     * @return 装好的容器栈（stackSize 1）；不可用时 null
     */
    ItemStack newFilledContainer(String tag, int points);

    /**
     * @return {@link #newFilledContainer} 使用的单容器容量（无容量证据的罐返回
     *         {@value TaumDistillRules#CAPACITY_UNKNOWN}，此时按调用方给的点数装）
     */
    int filledContainerCapacity();
}
