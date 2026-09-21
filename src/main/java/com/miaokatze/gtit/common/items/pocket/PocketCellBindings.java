package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 口袋的元件绑定表：一串有序的绑定条目，每条携带
 * <b>{@code {id, mode, dim, x, y, z, slotIndex}}</b>。
 * <p>
 * <b>内存形状与落档形状必须一致</b>：旧实现在表级只放一个 {@code mode} 字节、却在 NBT 里
 * 按条目读写 {@code ENTRY_MODE}，两者不对齐即是 bug 面（条目间无法分化、往返后信息漂移）。
 * 现在每条 entry 自带 mode 与位置快照，读写逐条对应。
 * <p>
 * 四条不变量（均由 {@code NekoPocketModelTest} 断言）：
 * <ul>
 * <li><b>顺序即轮转外层序</b>：批次外层按绑定序遍历，所以读档/写档都必须保持列表顺序，
 * 不允许换成 {@code Set} 的迭代序或排序。</li>
 * <li><b>身份去重</b>：{@code id}（元件 {@code diskuuid}）是<b>唯一身份</b>；重复绑定同一身份
 * 不追加第二条目，而是<b>覆盖其位置快照</b>（返回 false 表示"没有新增条目"）。</li>
 * <li><b>条数上限</b>：{@link PocketConstants#MAX_BOUND_CELLS}，超出即拒收，避免口袋 NBT 无界膨胀。</li>
 * <li><b>位置是可选快照，不是第二真相</b>：未定位时五键整体为
 * {@link PocketConstants#UNLOCATED}；当前生效位置只由 {@code PocketCellProbe}（推送式观测）给出，
 * GUI 右栏的「维度+xyz」也只读探针。本表内的快照是跨重启缓存（R6 复验失败即视为"位置已失效"）。</li>
 * </ul>
 * <p>
 * {@code mode} <b>不再是表级互斥枚举</b>：定位是每条都可带的可选快照，与身份载荷模式无关；
 * 本期仍只写 {@link PocketConstants#MODE_DISK_UUID}，模式字节照样逐条写出，
 * 使「日后按位置绑定原装元件」成为纯增量（老档读回缺省即 0）。
 * <p>
 * 纯 JVM 件：只依赖 {@link NBTTagCompound}，不触达 {@code ItemStack}/{@code World}/AE2。
 */
public final class PocketCellBindings {

    /** NBTTagList 里复合条目的 tag id（10），用作 {@code getTagList} 的元素类型。 */
    private static final int TAG_COMPOUND = 10;
    /** NBT 的 list tag id（9），外层键的形状判定用它——用 10 会让整张表读空。 */
    private static final int TAG_LIST = 9;

    /**
     * 一条绑定：身份 + 预留模式字节 + 位置快照。
     * <p>
     * 不可变——改位置走 {@link PocketCellBindings#recordLocation}（整条替换），
     * 以免外部持有者看到半更新状态。
     */
    public static final class Entry {

        public final String id;
        public final byte mode;
        public final int dim;
        public final int x;
        public final int y;
        public final int z;
        public final int slotIndex;

        public Entry(String id, byte mode, int dim, int x, int y, int z, int slotIndex) {
            this.id = id;
            this.mode = mode;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.slotIndex = slotIndex;
        }

        /** 五键是否全空（未定位）。 */
        public boolean located() {
            return dim != PocketConstants.UNLOCATED && x != PocketConstants.UNLOCATED
                && y != PocketConstants.UNLOCATED
                && z != PocketConstants.UNLOCATED
                && slotIndex != PocketConstants.UNLOCATED;
        }

        /** 同一身份、换位置快照后的新条目（mode 保持首绑时的值）。 */
        Entry withLocation(int newDim, int newX, int newY, int newZ, int newSlot) {
            return new Entry(id, mode, newDim, newX, newY, newZ, newSlot);
        }

        @Override
        public String toString() {
            return "BindingEntry{" + id
                + ",mode="
                + mode
                + ",dim="
                + dim
                + ','
                + x
                + '/'
                + y
                + '/'
                + z
                + ",slot="
                + slotIndex
                + '}';
        }
    }

    /** 绑定序列表；顺序有语义，故用 List 而非 Set（去重靠 {@link #bind} 的显式判定）。 */
    private final List<Entry> entries = new ArrayList<>();

    public static PocketCellBindings readFrom(NBTTagCompound root) {
        final PocketCellBindings bindings = new PocketCellBindings();
        if (root == null || !root.hasKey(PocketConstants.BOUND_CELLS, TAG_LIST)) {
            return bindings;
        }
        final NBTTagList list = root.getTagList(PocketConstants.BOUND_CELLS, TAG_COMPOUND);
        final Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound entry = list.getCompoundTagAt(i);
            final String uuid = entry.getString(PocketConstants.ENTRY_ID);
            if (uuid == null || uuid.isEmpty() || !seen.add(uuid)) {
                continue;
            }
            if (bindings.entries.size() >= PocketConstants.MAX_BOUND_CELLS) {
                break;
            }
            bindings.entries.add(
                new Entry(
                    uuid,
                    entry.getByte(PocketConstants.ENTRY_MODE),
                    readCoord(entry, PocketConstants.ENTRY_DIM),
                    readCoord(entry, PocketConstants.ENTRY_X),
                    readCoord(entry, PocketConstants.ENTRY_Y),
                    readCoord(entry, PocketConstants.ENTRY_Z),
                    readCoord(entry, PocketConstants.ENTRY_SLOT)));
        }
        return bindings;
    }

    public void writeTo(NBTTagCompound root) {
        final NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            final NBTTagCompound out = new NBTTagCompound();
            out.setString(PocketConstants.ENTRY_ID, entry.id);
            out.setByte(PocketConstants.ENTRY_MODE, entry.mode);
            // 位置快照逐条写：未定位也写哨兵，让"缺键"与"已定位到 0"在读档时可区分
            out.setInteger(PocketConstants.ENTRY_DIM, entry.dim);
            out.setInteger(PocketConstants.ENTRY_X, entry.x);
            out.setInteger(PocketConstants.ENTRY_Y, entry.y);
            out.setInteger(PocketConstants.ENTRY_Z, entry.z);
            out.setInteger(PocketConstants.ENTRY_SLOT, entry.slotIndex);
            list.appendTag(out);
        }
        root.setTag(PocketConstants.BOUND_CELLS, list);
    }

    /**
     * 追加一枚元件；已在表内、身份为空或表已满都拒收。
     * <p>
     * 重复绑定同一身份时<b>覆盖其位置快照</b>（不追加第二条目），此时返回 false。
     *
     * @param dim/x/y/z/slotIndex 位置快照；未定位一律传 {@link PocketConstants#UNLOCATED}
     * @param mode                载荷模式字节（本期恒 {@link PocketConstants#MODE_DISK_UUID}）
     * @return true 表示这次绑定<b>新增</b>了条目
     */
    public boolean bind(String diskuuid, int dim, int x, int y, int z, int slotIndex, byte mode) {
        if (diskuuid == null || diskuuid.isEmpty()) {
            return false;
        }
        final int existing = indexOf(diskuuid);
        if (existing >= 0) {
            entries.set(
                existing,
                entries.get(existing)
                    .withLocation(dim, x, y, z, slotIndex));
            return false;
        }
        if (entries.size() >= PocketConstants.MAX_BOUND_CELLS) {
            return false;
        }
        entries.add(new Entry(diskuuid, mode, dim, x, y, z, slotIndex));
        return true;
    }

    /**
     * 绑定一枚此刻无法定位的元件（右下绑定格的正常入口：只读得到身份，位置留哨兵）。
     *
     * @return true 表示新增了条目
     */
    public boolean bind(String diskuuid, byte mode) {
        return bind(
            diskuuid,
            PocketConstants.UNLOCATED,
            PocketConstants.UNLOCATED,
            PocketConstants.UNLOCATED,
            PocketConstants.UNLOCATED,
            PocketConstants.UNLOCATED,
            mode);
    }

    /**
     * 回填/刷新某条绑定的位置快照（{@code PocketCellProbe} 观测到元件所在容器后由通道侧调用）。
     *
     * @return true 表示快照发生了变化（身份不在表内即 false）
     */
    public boolean recordLocation(String diskuuid, int dim, int x, int y, int z, int slotIndex) {
        final int existing = indexOf(diskuuid);
        if (existing < 0) {
            return false;
        }
        final Entry current = entries.get(existing);
        final Entry updated = current.withLocation(dim, x, y, z, slotIndex);
        if (updated.located() == current.located() && current.dim == dim
            && current.x == x
            && current.y == y
            && current.z == z
            && current.slotIndex == slotIndex) {
            return false;
        }
        entries.set(existing, updated);
        return true;
    }

    /** 解绑一枚元件；不存在则无副作用。 */
    public boolean unbind(String diskuuid) {
        final int existing = indexOf(diskuuid);
        if (existing < 0) {
            return false;
        }
        entries.remove(existing);
        return true;
    }

    public boolean contains(String diskuuid) {
        return diskuuid != null && indexOf(diskuuid) >= 0;
    }

    /** 某身份的位置快照；不在表内返回 null。 */
    public Entry entry(String diskuuid) {
        final int existing = indexOf(diskuuid);
        return existing < 0 ? null : entries.get(existing);
    }

    /** 绑定序的只读视图（轮转外层遍历用它，切勿就地修改）。 */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * 绑定序身份列表的只读视图——批次外层遍历与轮转游标键都用它（R9：键必须是 {@code diskuuid}
     * 字符串，不得用列表下标或 {@code AEStackTypeRegistry} 索引）。
     */
    public List<String> cells() {
        final List<String> ids = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            ids.add(entry.id);
        }
        return Collections.unmodifiableList(ids);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    /** 是否仍有空位可绑。 */
    public boolean hasRoom() {
        return entries.size() < PocketConstants.MAX_BOUND_CELLS;
    }

    private int indexOf(String diskuuid) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(diskuuid)) {
                return i;
            }
        }
        return -1;
    }

    /** 缺键（老档/外来档）回落为未定位哨兵，而不是 0。 */
    private static int readCoord(NBTTagCompound entry, String key) {
        return entry.hasKey(key, 3) ? entry.getInteger(key) : PocketConstants.UNLOCATED;
    }
}
