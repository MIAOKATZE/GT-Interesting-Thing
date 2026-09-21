package com.miaokatze.gtit.crossmod.taum;

/**
 * 一份「aspect tag → 点数」的轻量快照（值对象，纯 JDK，不依赖 MC / Thaumcraft 类型）。
 * <p>
 * 桥接层与 TC/TT 交互后一律把结果转成本类再返回，{@link AspectList} 与 {@code Aspect}
 * 实例绝不外泄到 {@code crossmod.taum} 之外——上层（口袋物品/GUI/通道）因此可以在
 * Thaumcraft 缺席的环境里正常编译、加载与运行。
 * <p>
 * 不可变：构造后数组不再对外暴露引用（{@link #of} 会按需压缩并复用入参数组，
 * 调用方不得再改动传入数组）。空实例统一用 {@link #EMPTY}，语义为
 * 「无源质 / 不可蒸馏 / 容器是空的 / TC 缺席」，调用方无需区分 null 与 empty。
 * <p>
 * 顺序：条目顺序即桥接层读取顺序（蒸馏结果 = TC 查表序，容器读取 = AspectList 的
 * {@code LinkedHashMap} 插入序），上层若需要固定显示序，自行按
 * {@link TaumCompat#aspectOrder()} 对齐。
 */
public final class TaumAspectAmounts {

    /** 空快照（不可蒸馏、容器为空或 TC 缺席） */
    public static final TaumAspectAmounts EMPTY = new TaumAspectAmounts(new String[0], new int[0]);

    private final String[] tags;
    private final int[] amounts;

    private TaumAspectAmounts(String[] tags, int[] amounts) {
        this.tags = tags;
        this.amounts = amounts;
    }

    /**
     * 由平行数组构造；丢弃 tag 为 null/空 与 amount &lt;= 0 的条目（TC 侧真实会出现：
     * {@code AspectList.readFromNBT} 对未注册 tag 得到 null key）。
     *
     * @param tags    aspect tag 数组
     * @param amounts 对应点数数组，长度须与 {@code tags} 一致
     * @return 快照；长度为 0 时返回 {@link #EMPTY}
     * @throws IllegalArgumentException 两数组长度不一致
     */
    public static TaumAspectAmounts of(String[] tags, int[] amounts) {
        if (tags == null || amounts == null) {
            return EMPTY;
        }
        if (tags.length != amounts.length) {
            throw new IllegalArgumentException("tags/amounts 长度不一致: " + tags.length + " vs " + amounts.length);
        }
        int kept = 0;
        for (int i = 0; i < tags.length; i++) {
            if (tags[i] != null && !tags[i].isEmpty() && amounts[i] > 0) {
                if (kept != i) {
                    tags[kept] = tags[i];
                    amounts[kept] = amounts[i];
                }
                kept++;
            }
        }
        if (kept == 0) {
            return EMPTY;
        }
        if (kept == tags.length) {
            return new TaumAspectAmounts(tags, amounts);
        }
        String[] ct = new String[kept];
        int[] ca = new int[kept];
        System.arraycopy(tags, 0, ct, 0, kept);
        System.arraycopy(amounts, 0, ca, 0, kept);
        return new TaumAspectAmounts(ct, ca);
    }

    /**
     * 单条目快照。
     *
     * @param tag    aspect tag，null/空 时返回 {@link #EMPTY}
     * @param amount 点数，&lt;= 0 时返回 {@link #EMPTY}
     */
    public static TaumAspectAmounts single(String tag, int amount) {
        if (tag == null || tag.isEmpty() || amount <= 0) {
            return EMPTY;
        }
        return new TaumAspectAmounts(new String[] { tag }, new int[] { amount });
    }

    /** 条目数（0 表示空） */
    public int size() {
        return tags.length;
    }

    /** 第 index 项的 aspect tag */
    public String tagAt(int index) {
        return tags[index];
    }

    /** 第 index 项的点数 */
    public int amountAt(int index) {
        return amounts[index];
    }

    /** 全部点数之和（对应 TC 的 {@code AspectList.visSize()} 口径） */
    public int total() {
        int sum = 0;
        for (int amount : amounts) {
            sum += amount;
        }
        return sum;
    }

    /** @return 是否没有任何条目 */
    public boolean isEmpty() {
        return tags.length == 0;
    }

    /**
     * 按 tag 取点数（线性查找，条目数上限为 aspect 总数，无需建索引）。
     *
     * @return 该 tag 的点数，不存在时 0
     */
    public int getAmount(String tag) {
        if (tag == null) {
            return 0;
        }
        for (int i = 0; i < tags.length; i++) {
            if (tag.equals(tags[i])) {
                return amounts[i];
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TaumAspectAmounts{");
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(tags[i])
                .append('=')
                .append(amounts[i]);
        }
        return sb.append('}')
            .toString();
    }
}
