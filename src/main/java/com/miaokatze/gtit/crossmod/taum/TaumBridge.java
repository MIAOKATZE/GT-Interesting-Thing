package com.miaokatze.gtit.crossmod.taum;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/**
 * Thaumcraft 4.2.3.5 / Thaumic Tinkerer 的<b>唯一</b>类型引用点（对齐
 * {@code crossmod/bq/BqQuestInjector} 的收敛范式）。
 * <p>
 * 装配方式：本类<b>只</b>由 {@link TaumCompat} 在双哨兵（{@code Loader.isModLoaded("Thaumcraft")}
 * + {@code Class.forName("thaumcraft.api.aspects.Aspect")}）都命中后用 {@code Class.forName} 反射创建；
 * Thaumcraft 缺席时本类永不被加载，其静态引用不会触发 {@code NoClassDefFoundError}。
 * <p>
 * 取证位（详见 {@code plan/_taskpack/ultra-04-thaumcraft4-essentia.md}）：
 * <ul>
 * <li>蒸馏判据 {@link ThaumcraftCraftingManager#getObjectTags(ItemStack)} + {@code getBonusTags}
 * ＝ TC4 {@code TileAlchemyFurnace.canSmelt():307-325} 的同源查表，空 {@link AspectList} 即不可蒸馏</li>
 * <li>显示序取 {@code Aspect.aspects}（{@code Aspect.java:19} 的 {@code LinkedHashMap}、
 * {@code :79} 的 {@code aspects.put(tag,this)}）迭代序，数量不假设恰为 48（addon 可追加）</li>
 * <li>染色/显示名运行时取 {@link Aspect#getColor()} / {@link Aspect#getName()}（反编译转储的 int 常量不可信）</li>
 * <li>晶化源质 1 点/个（{@code TileEssentiaCrystalizer.java:293}）、源质瓶 8 点/次
 * （{@code ItemEssence.java:109-185}，meta 0 空瓶 / meta 1 装瓶、可堆 64 但<b>整叠共享同一份
 * {@link AspectList}</b> ⇒ "一叠多少点" = 单件 amount × stackSize）</li>
 * </ul>
 * <p>
 * ★<b>R88 载体裁定</b>：口袋侧的源质搬运载体是<b>源质瓶</b>，不是晶化源质。本类里
 * {@link #newFilledContainer(String, int)} / {@link #capacityOf(ItemStack)} /
 * {@link #addEssentia(ItemStack, String, int)}（含 meta 0→1）/ {@link #drainAll(ItemStack)} /
 * {@link #filledContainerCapacity()} 这一整条瓶能力自 R31 起实现完整却零调用方，本轮起被通道支
 * （{@code PocketEssenceChannelOps}）、12 格注入支（{@code PocketSlots}）与面板取出支
 * （{@code NekoPocketPanel}）接上，{@link #preferredContainerItem()} 同时<b>钉为瓶</b>。
 * {@link #newCrystalStack(String, int)} 与档位 {@code TaumDistillRules#CRYSTAL_CAPACITY}
 * <b>按裁定保留但退役为只读</b>：任何路径都不再产出晶，旧晶仍能被 {@link #readContainer(ItemStack)}
 * 读回点数（不吃件）。
 * <p>
 * <b>禁止改动 TC 返回值</b>：{@code getObjectTags} 命中的是 {@code ThaumcraftApi.objectTags}
 * 注册表内<b>共享</b>的 {@link AspectList} 实例（{@code ThaumcraftCraftingManager.java:243/256/260}），
 * 本类所有读取路径都立即转成 {@link TaumAspectAmounts} 值对象，不回传、不 add/merge/remove。
 * <p>
 * <b>永不抛出</b>：每个公开方法都以 {@code try/catch(Throwable)} 兜底，运行期 TC 版本漂移
 * （方法缺失 = {@code NoSuchMethodError}）只降级为「空结果」，日志首告一次。
 */
public final class TaumBridge implements TaumBridgeApi {

    /** Thaumcraft modId（取门面常量；两者同侧、无 TC 依赖） */
    static final String MODID_THAUMCRAFT = TaumCompat.MODID_THAUMCRAFT;

    /** Thaumic Tinkerer modId */
    static final String MODID_THAUMIC_TINKERER = TaumCompat.MODID_THAUMIC_TINKERER;

    /** 晶化源质注册名（{@code ConfigItems.java:475} 的 registerItem 第二参） */
    static final String ITEM_CRYSTAL = "ItemCrystalEssence";

    /** 源质瓶注册名（{@code ConfigItems.java:359}） */
    static final String ITEM_PHIAL = "ItemEssence";

    /**
     * 「源质罐子」在 Thaumic Tinkerer 里的注册名候选。
     * <p>
     * 实测（见 {@code plan/_taskpack/impl-s2-taum-bridge.md}）：本地锁定的
     * TT 2.12.22 / 2.12.27 / 2.12.32 dev jar 内<b>不存在</b> Vessel 类、条目名或贴图资源，
     * 故本仓<b>不</b>对 TT 做任何编译期类引用；这里只做运行期按名探测，
     * 命中且实现 {@link IEssentiaContainerItem} 才启用，否则静默回落源质瓶。
     * 玩家包内若换回带罐子的 TT 变体，此探测自动生效，无需改代码。
     */
    static final String[] VESSEL_CANDIDATES = { "ItemVessel", "Vessel" };

    /**
     * 晶化源质可堆上限（{@code ItemCrystalEssence.java:30} 的 setMaxStackSize(64)）。
     * <p>
     * ★<b>R85 小项 5：这里不再自己写一份 64，而是引用
     * {@link PocketConstants#ESSENCE_OUT_MAX_POINTS_PER_ACTION}</b>。这两枚 64 不是"数值巧合"，
     * 而是<b>同一个事实的两面</b>：一次源质取出动作最多能给出的点数，就是"能装进一个物品堆"的点数，
     * 而那个堆叠上限由 TC 的 {@code ItemCrystalEssence} 钉死。两处各写一份 ⇒ 只抬一边就是
     * "扣了 256 点、只造出 64 晶、退回的 192 点被 {@code PocketEssenceStore} 的入账截断吃掉"的
     * <b>净吞点数</b>（取证档案 D4）。跨包引用在本仓有先例：{@code TaumDistillRules.java:3} 就 import
     * 了 {@code PocketInventory} 做单源转发。
     * <p>
     * ★派生方向是"物化侧读取出侧"：取出上界是<b>我们的</b>设计量，TC 的堆叠上限是<b>外部的</b>事实；
     * 两者今天等值。若哪天 TC 把 64 改了，改的应该是<b>本行右边那个符号所代表的事实</b>——
     * 真到那一步就得把两枚常量拆开并在这里补一条"取 min"的判据，而不是让两边各自漂。
     * <p>
     * ★★<b>R88 起这条"同一事实的两面"不再成立，必须读成两件独立的事</b>：载体改判为源质瓶后，
     * 一次取出动作的 64 点上限是 {@link PocketConstants#ESSENCE_OUT_MAX_POINTS_PER_ACTION}
     * <b>自己</b>的设计量（= {@link PocketConstants#ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 瓶 ×
     * {@link PocketConstants#ESSENCE_OUT_UNIT_POINTS} 点，见那里的派生式），而晶的 64 堆叠上限
     * 只服务<b>退役只读支</b>（旧晶仍可被读回点数，但本仓不再产出晶 ⇒ 这一句唯一还有意义的地方是
     * "万一外部产出晶，读它时别把叠数放大成负数/溢出"）。本行仍引用那个符号，是<b>刻意留下的旧链接</b>：
     * 拆掉它会让"晶单堆 64"这条 TC 事实失去仓内锚点，留着它则必须连同本段一起读，不许只读常量名。
     */
    private static final int CRYSTAL_STACK_LIMIT = PocketConstants.ESSENCE_OUT_MAX_POINTS_PER_ACTION;

    /** 降级日志只发一次，避免每 tick 的蒸馏/渲染调用刷屏 */
    private static boolean warned = false;

    /*
     * 以下三个物品缓存与 warned 走 benign race（不加锁、可被两线程各解析一次）：
     * 解析结果幂等、发布对象由 Forge 注册表早已安全发布，重复解析只多一次 HashMap 取；
     * 日志闩最坏情况是打两行。桥接层刻意不引入同步点，避免渲染线程被服务端线程牵住。
     */
    /** 缓存：源质瓶物品（TC 注册完成后首次取用时解析） */
    private Item phial;
    /** 缓存：晶化源质物品 */
    private Item crystal;
    /** 缓存：探测到的第三方罐（可能为 null，{@link #vesselProbed} 标记已探过） */
    private Item vessel;
    private boolean vesselProbed;
    private boolean phialProbed;
    private boolean crystalProbed;

    /**
     * 由 {@link TaumCompat} 反射调用；构造即二次确认 TC 在场（双哨兵的第二颗），
     * 不满足直接抛出由调用方吞掉并保持整体「不可用」。
     */
    public TaumBridge() {
        if (!Loader.isModLoaded(MODID_THAUMCRAFT)) {
            throw new IllegalStateException("Thaumcraft 未加载，TaumBridge 不应被实例化");
        }
        if (Aspect.aspects == null || Aspect.aspects.isEmpty()) {
            throw new IllegalStateException("Aspect 注册表尚未初始化（TaumBridge 触发过早？）");
        }
    }

    @Override
    public String[] aspectOrder() {
        try {
            Collection<?> keys = Aspect.aspects.keySet();
            List<String> out = new ArrayList<>(keys.size());
            for (Object key : keys) {
                if (key instanceof String) {
                    out.add((String) key);
                }
            }
            return out.toArray(new String[0]);
        } catch (Throwable t) {
            fail("aspectOrder", t);
            return new String[0];
        }
    }

    @Override
    public int colorOf(String tag) {
        try {
            Aspect aspect = Aspect.getAspect(tag);
            return aspect == null ? -1 : aspect.getColor();
        } catch (Throwable t) {
            fail("colorOf", t);
            return -1;
        }
    }

    @Override
    public String nameOf(String tag) {
        try {
            Aspect aspect = Aspect.getAspect(tag);
            return aspect == null ? tag : aspect.getName();
        } catch (Throwable t) {
            fail("nameOf", t);
            return tag;
        }
    }

    @Override
    public TaumAspectAmounts distill(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return TaumAspectAmounts.EMPTY;
        }
        try {
            AspectList base = ThaumcraftCraftingManager.getObjectTags(stack);
            AspectList withBonus = ThaumcraftCraftingManager.getBonusTags(stack, base);
            AspectList effective = withBonus != null ? withBonus : base;
            if (effective == null || effective.size() == 0) {
                // 与 TC4 canSmelt() 同判：空 AspectList ⇒ 不可蒸馏（进度不推进）
                return TaumAspectAmounts.EMPTY;
            }
            return snapshot(effective);
        } catch (Throwable t) {
            fail("distill", t);
            return TaumAspectAmounts.EMPTY;
        }
    }

    @Override
    public TaumAspectAmounts readContainer(ItemStack stack) {
        IEssentiaContainerItem container = asContainer(stack);
        if (container == null) {
            return TaumAspectAmounts.EMPTY;
        }
        try {
            AspectList list = container.getAspects(stack);
            // TC 的 ItemEssence/ItemCrystalEssence 在内容为空时返回 null（不是空 AspectList）
            return list == null || list.size() == 0 ? TaumAspectAmounts.EMPTY : snapshot(list);
        } catch (Throwable t) {
            fail("readContainer", t);
            return TaumAspectAmounts.EMPTY;
        }
    }

    @Override
    public int capacityOf(ItemStack stack) {
        if (asContainer(stack) == null) {
            return TaumDistillRules.CAPACITY_NOT_A_CONTAINER;
        }
        if (isCrystal(stack)) {
            return TaumDistillRules.CRYSTAL_CAPACITY;
        }
        if (isPhial(stack)) {
            return TaumDistillRules.PHIAL_CAPACITY;
        }
        // 第三方容器（罐一类）：本地无容量证据，交由调用方按口袋单格上限 64 自缚
        return TaumDistillRules.CAPACITY_UNKNOWN;
    }

    @Override
    public int addEssentia(ItemStack stack, String tag, int points) {
        IEssentiaContainerItem container = asContainer(stack);
        if (container == null || points <= 0 || isCrystal(stack)) {
            // 晶化源质从来不走"就地注入"：旧形状是产新晶（★R88 起连产新晶也退役，见 newCrystalStack 的注），
            // 在这里往晶里灌源质会让 1 点/晶的口径失真，还会造出 TC 会随机重赋型的半吊子晶
            return 0;
        }
        try {
            Aspect aspect = Aspect.getAspect(tag);
            if (aspect == null) {
                return 0;
            }
            TaumAspectAmounts current = readContainer(stack);
            if (!current.isEmpty() && !isSingleTag(current, tag)) {
                // 容器已装别的源质：整笔拒绝（不混装、不覆盖 = 不销毁玩家价值）
                return 0;
            }
            int held = current.getAmount(tag);
            int capacity = capacityOf(stack);
            int room = capacity == TaumDistillRules.CAPACITY_UNKNOWN ? points : capacity - held;
            int stored = Math.min(points, Math.max(0, room));
            if (stored <= 0) {
                return 0;
            }
            container.setAspects(stack, new AspectList().add(aspect, held + stored));
            if (isPhial(stack)) {
                // meta 0 是空瓶（不画内容 overlay），装瓶必须切到 meta 1（ItemEssence.java:117/144）
                stack.setItemDamage(1);
            }
            return stored;
        } catch (Throwable t) {
            fail("addEssentia", t);
            return 0;
        }
    }

    @Override
    public TaumAspectAmounts drainAll(ItemStack stack) {
        IEssentiaContainerItem container = asContainer(stack);
        if (container == null || isCrystal(stack)) {
            // 无 NBT 的晶会被 TC 重新随机赋型（ItemCrystalEssence.java:98-110），清空后的晶属危险态：
            // 晶化源质由上层「读出点数 + 消耗整件」表达，不在原地清内容
            return TaumAspectAmounts.EMPTY;
        }
        TaumAspectAmounts content = readContainer(stack);
        if (content.isEmpty()) {
            return TaumAspectAmounts.EMPTY;
        }
        try {
            container.setAspects(stack, new AspectList());
            if (isPhial(stack)) {
                stack.setItemDamage(0);
            }
            return content;
        } catch (Throwable t) {
            fail("drainAll", t);
            return TaumAspectAmounts.EMPTY;
        }
    }

    /**
     * ★<b>R88：退役为只读支，本仓任何路径不得再调用本方法产出晶</b>（裁定 C2）。签名与实现按兄弟切片的
     * 编译依赖原样保留；"旧晶仍能被读回点数"走的是 {@link #readContainer(ItemStack)}，不经过这里。
     */
    @Override
    public ItemStack newCrystalStack(String tag, int points) {
        if (points <= 0) {
            return null;
        }
        try {
            Item item = crystalItem();
            Aspect aspect = item == null ? null : Aspect.getAspect(tag);
            if (aspect == null || !(item instanceof IEssentiaContainerItem)) {
                // 拿不到可写源质的晶物品时宁可不产出，也不产出「无 aspect 的晶」（TC 会随机重赋型）
                return null;
            }
            ItemStack stack = new ItemStack(item, Math.min(points, CRYSTAL_STACK_LIMIT), 0);
            ((IEssentiaContainerItem) item).setAspects(stack, new AspectList().add(aspect, 1));
            return stack;
        } catch (Throwable t) {
            fail("newCrystalStack", t);
            return null;
        }
    }

    /**
     * ★R88：本方法是<b>现役载体</b>的唯一出件口（面板取出、通道上传/下传、12 格回灌都用它）。
     * <p>
     * 调用方一律按"一次一只满瓶"传 {@code points == TaumDistillRules.PHIAL_CAPACITY}
     * （= {@link PocketConstants#ESSENCE_OUT_UNIT_POINTS}，C1 的整瓶粒度）；下面那句
     * {@code Math.min} 只是<b>兜底</b>，不是给上层"半个瓶子"的许可 —— TC 的 {@code ItemEssence}
     * 没有半瓶语义，半瓶会把自造形状送进第三方容器兼容面。
     */
    @Override
    public ItemStack newFilledContainer(String tag, int points) {
        if (points <= 0) {
            return null;
        }
        try {
            Aspect aspect = Aspect.getAspect(tag);
            if (aspect == null) {
                return null;
            }
            Item preferred = preferredContainerItem();
            if (preferred == null) {
                return null;
            }
            boolean phial = preferred == phialItem();
            int stored = phial ? Math.min(points, TaumDistillRules.PHIAL_CAPACITY) : points;
            if (stored <= 0) {
                return null;
            }
            ItemStack stack = new ItemStack(preferred, 1, phial ? 1 : 0);
            ((IEssentiaContainerItem) preferred).setAspects(stack, new AspectList().add(aspect, stored));
            return stack;
        } catch (Throwable t) {
            fail("newFilledContainer", t);
            return null;
        }
    }

    @Override
    public int filledContainerCapacity() {
        Item preferred = preferredContainerItem();
        if (preferred == null) {
            return TaumDistillRules.CAPACITY_NOT_A_CONTAINER;
        }
        return preferred == phialItem() ? TaumDistillRules.PHIAL_CAPACITY : TaumDistillRules.CAPACITY_UNKNOWN;
    }

    // ------------------------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------------------------

    /** 把 TC 的 AspectList 立刻转成本包值对象（只读，不改动入参实例） */
    private static TaumAspectAmounts snapshot(AspectList list) {
        Aspect[] aspects = list.getAspects();
        String[] tags = new String[aspects.length];
        int[] amounts = new int[aspects.length];
        int n = 0;
        for (Aspect aspect : aspects) {
            if (aspect == null) {
                // AspectList.readFromNBT 对未注册 tag 会塞入 null key
                continue;
            }
            String tag = aspect.getTag();
            if (tag == null || tag.isEmpty()) {
                continue;
            }
            tags[n] = tag;
            amounts[n] = list.getAmount(aspect);
            n++;
        }
        if (n == 0) {
            return TaumAspectAmounts.EMPTY;
        }
        String[] ct = new String[n];
        int[] ca = new int[n];
        System.arraycopy(tags, 0, ct, 0, n);
        System.arraycopy(amounts, 0, ca, 0, n);
        return TaumAspectAmounts.of(ct, ca);
    }

    private static boolean isSingleTag(TaumAspectAmounts current, String tag) {
        return current.size() == 1 && tag.equals(current.tagAt(0));
    }

    /** 该 stack 的物品是否源质容器（只认 TC 的公开接口，不认具体物品类） */
    private static IEssentiaContainerItem asContainer(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Item item = stack.getItem();
        return item instanceof IEssentiaContainerItem ? (IEssentiaContainerItem) item : null;
    }

    private boolean isPhial(ItemStack stack) {
        Item item = phialItem();
        return item != null && stack != null && stack.getItem() == item;
    }

    private boolean isCrystal(ItemStack stack) {
        Item item = crystalItem();
        return item != null && stack != null && stack.getItem() == item;
    }

    /** TC 源质瓶（按注册名解析，注册名与 modId 均取 TC 源码字面量；未命中只探一次） */
    private Item phialItem() {
        if (!phialProbed) {
            phialProbed = true;
            phial = lookup(ITEM_PHIAL);
        }
        return phial;
    }

    /** TC 晶化源质（未命中只探一次） */
    private Item crystalItem() {
        if (!crystalProbed) {
            crystalProbed = true;
            crystal = lookup(ITEM_CRYSTAL);
        }
        return crystal;
    }

    /**
     * 出件时的首选容器。★<b>R88 裁定：钉为 TC 源质瓶</b>（{@code ItemEssence}，8 点/瓶、meta 0 空 /
     * meta 1 满、可堆 64）。旧口径"探测到的第三方罐（TT 一类）优先、回落瓶"作废：口袋侧的取出粒度、
     * 通道单位语义与 tooltip 文案全部按"一瓶 8 点"这一档真值算（
     * {@link PocketConstants#ESSENCE_OUT_UNIT_POINTS}），换罐就等于把该真值挂到一个本仓
     * <b>拿不到容量证据</b>的容器上（罐只能落 {@link TaumDistillRules#CAPACITY_UNKNOWN}）。
     * <p>
     * 罐探测本身（{@link #vesselItem()}）<b>保留但降级为在场读数</b>：它现在只留下一条命中才打的 INFO，
     * 不再是任何出件路径的落点。要彻底删它属独立裁决（它同时是 {@code TaumCompat.isThaumicTinkererLoaded()}
     * 那条读数的存在理由）。
     */
    private Item preferredContainerItem() {
        vesselItem();
        return phialItem();
    }

    /**
     * 按名探测第三方罐；只在 TT 已加载时探一次，结果（含未命中）缓存。
     * <p>
     * ★R88 起本方法的结果<b>不参与出件</b>（见 {@link #preferredContainerItem()}），只留下面这条
     * 命中才打的在场读数 —— 文案因此不得再写成"优先使用"。
     */
    private Item vesselItem() {
        if (!vesselProbed) {
            vesselProbed = true;
            if (Loader.isModLoaded(MODID_THAUMIC_TINKERER)) {
                for (String candidate : VESSEL_CANDIDATES) {
                    Item found = lookupIn(MODID_THAUMIC_TINKERER, candidate);
                    if (found instanceof IEssentiaContainerItem) {
                        vessel = found;
                        GTInterestingThing.LOG.info(
                            "[GTIT-Taum] 检测到第三方源质容器 {}:{}（★R88 起仅作在场读数，" + "出件载体钉为 TC 源质瓶）",
                            MODID_THAUMIC_TINKERER,
                            candidate);
                        break;
                    }
                }
            }
        }
        return vessel;
    }

    private Item lookup(String registryName) {
        return lookupIn(MODID_THAUMCRAFT, registryName);
    }

    private Item lookupIn(String modId, String registryName) {
        try {
            return GameRegistry.findItem(modId, registryName);
        } catch (Throwable t) {
            fail("lookupIn " + modId + ":" + registryName, t);
            return null;
        }
    }

    private static void fail(String where, Throwable t) {
        if (!warned) {
            warned = true;
            GTInterestingThing.LOG
                .warn("[GTIT-Taum] Thaumcraft 桥接调用失败（{}），源质栏降级为不可用；" + "多为 TC 版本与本仓编译期 api 漂移所致", where, t);
        }
    }
}
