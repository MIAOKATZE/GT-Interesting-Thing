package com.miaokatze.gtit.common.items.infinitycell;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

/**
 * Issue #16（嵌套元件「必须为空」探测崩服）回归套件，零依赖纯 Java 断言，入口为 {@code main}
 * （与 {@code DefaultTradeSyncTest} 同模式）。
 * <p>
 * 覆盖 {@link InfinityItemStorageCellInventory#handlerHasContent} 与
 * {@link InfinityItemStorageCellInventory#nestedCellHasContent} 的通道中立判定与 fail-open 纪律：
 * <ul>
 * <li>探测列表由被探测元件自己的通道给出（不是固定的物品列表），错配 handler 只可能被喂进外来列表</li>
 * <li>同通道且元件非空 → 拒收；同通道且空 → 放行；handler 为 null / 探测抛任何 Throwable → 放行且不外抛</li>
 * <li>探测只读返回列表的 {@code isEmpty()}，绝不触碰元素级 API（{@code ProbeList} 对其余调用直接判负）</li>
 * </ul>
 * <p>
 * 可测边界（实跑确认）：{@code IAEStackType} 在本仓 test source set 里无法被实例化——
 * {@code dependencies.gradle:46} 以 {@code { transitive = false }} 引入 AE2，其方法签名依赖的
 * {@code it.unimi.dsi.fastutil.objects.ObjectLongPair} 不在 test 编译/运行类路径上，
 * 于是 {@code implements IAEStackType} 编译报「找不到 ObjectLongPair 的类文件」，
 * {@code Proxy.newProxyInstance} 运行期报 {@code NoClassDefFoundError}（两者均已本机复现）。
 * 因此「按元件自身 {@code getStackType()} 向 registry 发问、由 {@code BasicCellHandler} 的
 * {@code cell.getStackType() == type} 守卫把物品 handler 挡在流体/源质元件之外」这一段，
 * 在本套件里以等价形式复现：守卫拒绝 → handler 为 null → 放行；守卫失守（第三方 handler 盲转元素）
 * → {@code ClassCastException} → 放行且不外抛。真实 ItemStack 元件的端到端行为由实机验证。
 */
public class InfinityNestedCellProbeTest {

    /** 流体/源质等非物品通道在测试里的替身标签；只在断言与日志里出现。 */
    private static final String FOREIGN_CHANNEL = "fluid-stub";
    private static final String ITEM_CHANNEL = "item-stub";

    public static void main(String[] args) {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases
            .put("mismatchedHandlerCastFailurePasses", InfinityNestedCellProbeTest::mismatchedHandlerCastFailurePasses);
        cases.put("sameChannelNonEmptyRejects", InfinityNestedCellProbeTest::sameChannelNonEmptyRejects);
        cases.put("sameChannelEmptyPasses", InfinityNestedCellProbeTest::sameChannelEmptyPasses);
        cases.put("probeListComesFromNestedChannel", InfinityNestedCellProbeTest::probeListComesFromNestedChannel);
        cases.put("unknownChannelOrHandlerPasses", InfinityNestedCellProbeTest::unknownChannelOrHandlerPasses);
        cases.put("probeFailureFailsOpen", InfinityNestedCellProbeTest::probeFailureFailsOpen);
        TestRunner.run(InfinityNestedCellProbeTest.class, cases);
    }

    // ==================== ① 错配通道：崩溃路径必须变成放行 ====================

    /**
     * 复刻 Issue #16 现场：物品侧 handler（{@code ItemCellInventoryHandler} 口径）拿到与本通道无关的
     * 列表时做无检查强转抛 {@code ClassCastException}。修复后该异常只能在探测内部被吞掉并放行，
     * 绝不能再冒泡到 {@code injectItems} 崩服。
     */
    static void mismatchedHandlerCastFailurePasses() {
        ProbeList foreignList = new ProbeList(FOREIGN_CHANNEL, true);
        ProbeHandler itemHandler = new ProbeHandler(
            foreignList,
            new ClassCastException("appeng.util.item.AEItemStack cannot be cast to appeng.util.item.AEFluidStack"));

        SimpleAssert.that(
            !InfinityItemStorageCellInventory.handlerHasContent(itemHandler, () -> foreignList),
            "错配 handler 抛 ClassCastException → 判定不可信 → 放行（不再崩服）");
        SimpleAssert.eq(1, itemHandler.calls, "异常已在探测内部吸收，不再向上冒泡");
        SimpleAssert.that(itemHandler.received == foreignList, "喂给 handler 的是嵌套元件自己通道的列表，不是物品通道列表");
    }

    // ==================== ② 同通道非空 → 拒收 ====================

    static void sameChannelNonEmptyRejects() {
        ProbeList ownList = new ProbeList(FOREIGN_CHANNEL, false);
        ProbeHandler handler = new ProbeHandler(ownList, null);

        SimpleAssert.that(
            InfinityItemStorageCellInventory.handlerHasContent(handler, () -> ownList),
            "同通道且元件已有内容 → 拒收（原「嵌套元件必须为空」语义保持）");
        SimpleAssert.eq(1, handler.calls, "只发一次探测，且为 SIMULATE 口径（不改元件内容）");
    }

    // ==================== ③ 同通道为空 → 放行 ====================

    static void sameChannelEmptyPasses() {
        ProbeList ownList = new ProbeList(ITEM_CHANNEL, true);
        ProbeHandler handler = new ProbeHandler(ownList, null);

        SimpleAssert
            .that(!InfinityItemStorageCellInventory.handlerHasContent(handler, () -> ownList), "同通道且元件为空 → 放行入仓");
        SimpleAssert.eq(onlyIsEmpty(ownList.invoked), ownList.invoked, "探测对列表只读 isEmpty()，不做任何元素级访问（故不可能强转元素类型）");
    }

    private static Set<String> onlyIsEmpty(Set<String> invoked) {
        Set<String> expected = new LinkedHashSet<>();
        expected.add("isEmpty");
        return expected;
    }

    // ==================== ③' 探测列表来源：必须由被探测通道自己造 ====================

    static void probeListComesFromNestedChannel() {
        ProbeList ownList = new ProbeList(FOREIGN_CHANNEL, true);
        ProbeHandler handler = new ProbeHandler(ownList, null);
        CountingSupplier supplier = new CountingSupplier(ownList);

        SimpleAssert.that(!InfinityItemStorageCellInventory.handlerHasContent(handler, supplier), "空元件放行");
        SimpleAssert.eq(1, supplier.calls, "探测列表由通道自己的 createList 供给，且只造一次");
        SimpleAssert.that(handler.received == ownList, "handler 收到的正是该通道造出的列表（type 中立）");
    }

    // ==================== ④ 不可判定一律放行 ====================

    static void unknownChannelOrHandlerPasses() {
        CountingLookup lookup = new CountingLookup(null);
        SimpleAssert.that(
            !InfinityItemStorageCellInventory.nestedCellHasContent(null, lookup),
            "通道取不到（非 IStorageCell / getStackType 返回 null）→ 不可判定 → 放行");
        SimpleAssert.eq(0, lookup.calls, "通道未知时根本不向 registry 发问");

        ProbeList ownList = new ProbeList(FOREIGN_CHANNEL, false);
        SimpleAssert.that(
            !InfinityItemStorageCellInventory.handlerHasContent(null, () -> ownList),
            "registry 无 handler 或元件通道守卫拒绝错配（BasicCellHandler 返回 null）→ 放行");
        SimpleAssert.that(
            !InfinityItemStorageCellInventory.handlerHasContent(new ProbeHandler(ownList, null), null),
            "列表供给口缺失 → 放行");
    }

    static void probeFailureFailsOpen() {
        ProbeHandler throwing = new ProbeHandler(null, new IllegalStateException("AE2 内部状态未就绪"));
        SimpleAssert.that(
            !InfinityItemStorageCellInventory.handlerHasContent(throwing, () -> new ProbeList(FOREIGN_CHANNEL, false)),
            "探测抛 RuntimeException → 放行且不外抛");

        CountingSupplier boom = new CountingSupplier(null);
        boom.fail = new RuntimeException("createList 背后依赖未引导");
        SimpleAssert.that(
            !InfinityItemStorageCellInventory.handlerHasContent(new ProbeHandler(null, null), boom),
            "列表构造抛错 → 放行且不外抛");
        // 「registry 查询本身抛错」这一层要穿过 IAEStackType 实参，见类注释的可测边界说明（实机验证）。
    }

    // ==================== stub ====================

    /** 列表替身：带通道标签；除 isEmpty / size 外的任何调用都判负（探测不得触碰元素级 API）。 */
    private static final class ProbeList implements IItemList<IAEItemStack> {

        private final String channel;
        private final boolean empty;
        private final Set<String> invoked = new LinkedHashSet<>();

        ProbeList(String channel, boolean empty) {
            this.channel = channel;
            this.empty = empty;
        }

        @Override
        public boolean isEmpty() {
            invoked.add("isEmpty");
            return empty;
        }

        @Override
        public int size() {
            invoked.add("size");
            return empty ? 0 : 1;
        }

        @Override
        public void add(IAEItemStack option) {
            throw unexpected("add");
        }

        @Override
        public IAEItemStack findPrecise(IAEItemStack i) {
            throw unexpected("findPrecise");
        }

        @Override
        public Collection<IAEItemStack> findFuzzy(IAEItemStack input, FuzzyMode fuzzy) {
            throw unexpected("findFuzzy");
        }

        @Override
        public void addStorage(IAEItemStack option) {
            throw unexpected("addStorage");
        }

        @Override
        public void addCrafting(IAEItemStack option) {
            throw unexpected("addCrafting");
        }

        @Override
        public void addRequestable(IAEItemStack option) {
            throw unexpected("addRequestable");
        }

        @Override
        public IAEItemStack getFirstItem() {
            throw unexpected("getFirstItem");
        }

        @Override
        public Iterator<IAEItemStack> iterator() {
            throw unexpected("iterator");
        }

        @Override
        public void resetStatus() {
            throw unexpected("resetStatus");
        }

        @Override
        public IAEStackType<IAEItemStack> getStackType() {
            throw unexpected("getStackType");
        }

        private AssertionError unexpected(String member) {
            invoked.add(member);
            return new AssertionError("探测越界调用列表成员 " + channel + "." + member + "（只允许 isEmpty）");
        }
    }

    /** handler 替身：只回答 getAvailableItems，failure 非空时按该异常复现第三方 handler 的盲转崩溃。 */
    private static final class ProbeHandler implements IMEInventoryHandler<IAEItemStack> {

        private final IItemList<IAEItemStack> answer;
        private final RuntimeException failure;
        private IItemList<IAEItemStack> received;
        private int calls;

        ProbeHandler(IItemList<IAEItemStack> answer, RuntimeException failure) {
            this.answer = answer;
            this.failure = failure;
        }

        @Override
        public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
            calls++;
            received = out;
            if (failure != null) {
                throw failure;
            }
            return answer;
        }

        @Override
        public IAEItemStack injectItems(IAEItemStack input, Actionable type, BaseActionSource src) {
            throw unexpected("injectItems");
        }

        @Override
        public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
            throw unexpected("extractItems");
        }

        @Override
        public AccessRestriction getAccess() {
            throw unexpected("getAccess");
        }

        @Override
        public boolean isPrioritized(IAEItemStack input) {
            throw unexpected("isPrioritized");
        }

        @Override
        public boolean canAccept(IAEItemStack input) {
            throw unexpected("canAccept");
        }

        @Override
        public int getPriority() {
            throw unexpected("getPriority");
        }

        @Override
        public int getSlot() {
            throw unexpected("getSlot");
        }

        @Override
        public boolean validForPass(int pass) {
            throw unexpected("validForPass");
        }

        private AssertionError unexpected(String member) {
            calls++;
            return new AssertionError("探测越界调用 handler 成员 " + member + "（只允许 getAvailableItems）");
        }
    }

    /** registry 查询替身：复刻「通道守卫命中才给 handler」的口径，这里恒给 handler（可为 null）。 */
    private static final class CountingLookup
        implements InfinityItemStorageCellInventory.NestedCellLookup<IAEItemStack> {

        private final IMEInventoryHandler<IAEItemStack> handler;
        private int calls;

        CountingLookup(IMEInventoryHandler<IAEItemStack> handler) {
            this.handler = handler;
        }

        @Override
        public IMEInventoryHandler<IAEItemStack> get(IAEStackType<IAEItemStack> type) {
            calls++;
            return handler;
        }
    }

    private static final class CountingSupplier implements Supplier<IItemList<IAEItemStack>> {

        private final IItemList<IAEItemStack> list;
        private int calls;
        private RuntimeException fail;

        CountingSupplier(IItemList<IAEItemStack> list) {
            this.list = list;
        }

        @Override
        public IItemList<IAEItemStack> get() {
            calls++;
            if (fail != null) {
                throw fail;
            }
            return list;
        }
    }
}
