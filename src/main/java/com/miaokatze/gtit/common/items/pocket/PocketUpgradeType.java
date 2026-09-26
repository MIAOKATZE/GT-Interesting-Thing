package com.miaokatze.gtit.common.items.pocket;

/**
 * 口袋升级插件的五种效果（R95 升级插件体系）。
 * <p>
 * ★<b>位序 = {@link #ordinal()}</b>：效果位图（根键 {@link PocketConstants#UPGRADES_KEY}，byte）
 * 的第 0-4 位恰好对应本枚举的五个值。位序一旦落档就<b>不可再改</b>——插入新值、调换顺序都会让
 * 已固化在玩家存档里的位图读出另一种升级（与 NBT 键「可加不可改」同一条纪律的位图版：
 * 只允许追加到末尾成为第 5 位及以后，且 {@link PocketConstants#UPGRADE_SLOTS} 同步扩位）。
 * 效果的读写入口只有一个：{@link PocketUpgrades}。
 */
public enum PocketUpgradeType {

    /** 容量：流体条容量 16G。 */
    CAPACITY,
    /** 堆叠：单格堆叠 ×16。 */
    STACK,
    /** 磁力：自动拾取周围 8 格。 */
    MAGNET,
    /** 通道持续化：通道常开 + 禁用通道按钮 + 帧带常亮。 */
    CHANNEL_PERSIST,
    /**
     * ★R96 S8 改名（原名「蒸馏加速」）：蒸馏间隔 5s → 1s。
     * <p>
     * 名字面（注册名 / lang 键 / 贴图基名）已全族换到 token {@code mage}，但<b>位序不动</b>（ordinal 仍为 4
     * ⇒ 效果位图第 4 位、插件槽第 5 格一字不变），旧注册名由 {@code ItemRegistrar} 的<b>影子注册</b>钉住，
     * 见本片的 README 代价条目。行为层（{@code PocketDistillDriver} / {@code TaumDistillRules} 与其
     * lang 键 {@code gtit.pocket.tooltip.distill_fast}）本轮<b>不改名</b>：它说的是"做什么"，型名说的是"是谁"。
     */
    MAGE
}
