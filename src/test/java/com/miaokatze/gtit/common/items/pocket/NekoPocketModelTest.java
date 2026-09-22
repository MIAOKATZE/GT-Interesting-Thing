package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.UpOrDown;
import com.miaokatze.gtit.common.items.infinitycell.IInfinityCellItem;
import com.miaokatze.gtit.common.items.infinitycell.InfinityCellConstants;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig.Kind;
import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.common.items.pocket.distill.PocketDistillDriver;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;
import com.miaokatze.gtit.gui.pocket.NekoEssenceGhostCell;
import com.miaokatze.gtit.gui.pocket.NekoPocketBottomBand;
import com.miaokatze.gtit.gui.pocket.NekoPocketEssenceColumn;
import com.miaokatze.gtit.gui.pocket.NekoPocketFluidSlot;
import com.miaokatze.gtit.gui.pocket.NekoPocketLeftColumn;
import com.miaokatze.gtit.gui.pocket.NekoPocketPanel;
import com.miaokatze.gtit.gui.pocket.NekoPocketStorageColumn;
import com.miaokatze.gtit.gui.pocket.PocketGhostRequest;
import com.miaokatze.gtit.gui.pocket.PocketGuiTextureContract;
import com.miaokatze.gtit.gui.pocket.PocketInventory;
import com.miaokatze.gtit.gui.pocket.PocketSlots;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;

/**
 * 猫猫次元口袋「数据模型 + 通道状态机」零依赖回归套件：手写断言 + {@code main} 入口
 * （与 {@code InfinityStorageTypeKeyTest}、{@code ReincarnationStoreTest} 同模式，内部驱动
 * {@link TestRunner}，任一失败以非零退出码结束）。
 * <p>
 * 覆盖本切片声称可测的纯 JVM 面：
 * <ul>
 * <li>绑定表 NBT 往返、去重、顺序保持、条数上限、{@code mode} 字节预留</li>
 * <li>瞬时冷却剩余计算（墙钟口径，照 {@code NekoTradeHistory.getCooldownRemaining}）与设备/玩家双维校验</li>
 * <li>轮转序号按 {@code typeId} 推进且键为 {@code disksuuid} 字符串（两枚同类型不同 uuid 的桩件证明不复用序号）</li>
 * <li>remainder 顺延、分区拒收与元件满分开发码、批尾一次网络通知与 delta 合并</li>
 * <li>{@code ess} 表 64 点截断与「0 值不落 NBT」</li>
 * <li>配置过滤器键往返不含通道索引</li>
 * <li>★S-E：ghost 请求文法（CLR 带区域字母、SET 按载荷前缀分派 kind、分区域越界 135/18/72 各一个负例，R75）与流体条、源质格两个 NEI 拖入入口的纯判定面（探针链顺序与兜底、tag
 * 匹配、ESSENCE
 * 空间落点）</li>
 * </ul>
 * <p>
 * 可测边界：{@link PocketAeChannelOps}（AE2 handler、{@code postAlterationOfStoredItems}、驱动器 tile 解析）
 * 与 {@link PocketCellProbe}（{@code World}/{@code TileEntity}）需要真实游戏对象，本套件用桩件替代；
 * 真实端到端行为（含 typed post 是否被 {@code GridStorageCache} 接住，即实验 E1）属需玩家实机证据项，
 * 不在此声称已验证。
 */
public class NekoPocketModelTest {

    private static final String CELL_A = "11111111-1111-1111-1111-111111111111";
    private static final String CELL_B = "22222222-2222-2222-2222-222222222222";
    private static final List<String> CHANNELS = Arrays.asList("item", "fluid", "essentia");
    /** 墙钟基准值：刻意取正数，冷却口径里 {@code <=0} 表示"从未触发"。 */
    private static final long BASE_MS = 1_700_000_000_000L;

    public static void main(String[] args) {
        final Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("绑定表NBT往返与顺序保持", NekoPocketModelTest::bindingRoundTripKeepsOrder);
        cases.put("绑定表去重与空值与上限", NekoPocketModelTest::bindingDedupeAndCap);
        cases.put("绑定表陈旧重复档折叠", NekoPocketModelTest::bindingReadFoldsLegacyDuplicates);
        cases.put("bind_position_roundtrip", NekoPocketModelTest::bindPositionRoundTrip);
        cases.put("bind_identity_dedup_overwrite_position", NekoPocketModelTest::bindIdentityDedupOverwritesPosition);
        cases.put("绑定表逐条mode与缺键回落", NekoPocketModelTest::bindingPerEntryModeAndMissingKeys);
        cases.put("源质表按每格上限封顶", NekoPocketModelTest::essenceCapsFollowPerTagCap);
        cases.put(
            "essence_all_or_nothing_rejects_whole_round",
            NekoPocketModelTest::essenceAllOrNothingRejectsWholeRound);
        cases.put("源质表0值不落NBT", NekoPocketModelTest::essenceZeroIsNotWritten);
        cases.put("essence_aspects_list_roundtrip_zero_absent", NekoPocketModelTest::essenceAspectsListRoundTrip);
        cases.put("冷却剩余按墙钟口径", NekoPocketModelTest::cooldownUsesWallClock);
        cases.put("冷却设备维与玩家维取严", NekoPocketModelTest::cooldownChecksBothDimensions);
        cases.put("瞬时冷却写入口袋NBT", NekoPocketModelTest::burstCooldownPersistsToDeviceNbt);
        cases.put("同tick不可重入", NekoPocketModelTest::sameTickLatchBlocksSecondRequest);
        cases.put("轮转序号按typeId推进", NekoPocketModelTest::rotationAdvancesByTypeId);
        cases.put("轮转键为diskuuid字符串不复用序号", NekoPocketModelTest::rotationKeyedByUuidString);
        cases.put("轮转游标不被注入批读写（R87-b）", NekoPocketModelTest::rotationSurvivesHandlerRecreation);
        cases.put("remainder留源槽并顺延", NekoPocketModelTest::remainderStaysInSourceSlot);
        cases.put("分区拒收与元件满分开发码", NekoPocketModelTest::filterRejectAndFullAreDistinct);
        cases.put("批尾一次通知且delta合并", NekoPocketModelTest::oneMergedPostPerBatch);
        cases.put("配置载荷键不含通道索引", NekoPocketModelTest::filterKeysCarryNoChannelIndex);
        cases.put("filter_slotindex_roundtrip", NekoPocketModelTest::filterSlotIndexRoundTrip);
        cases.put("filter_slot_overwrite_not_append", NekoPocketModelTest::filterSlotOverwriteNotAppend);
        cases.put("probe_observed_then_removed", NekoPocketModelTest::probeObserveLocateThenGone);
        cases.put("NBT键名字面量未被改动", NekoPocketModelTest::nbtKeyLiteralsUnchanged);
        // ---- D 批：四条运行时通路透到底（S6 服务端 / S7 蒸馏 / R63 分流 / S5 ghost + R45b 两支）
        cases.put("short_channel_uses_relative_countdown", NekoPocketModelTest::shortChannelCadence);
        cases.put("short_channel_survives_tick_base_rewind", NekoPocketModelTest::tickBaseRewindKeepsCadence);
        cases.put("burst_cooldown_wallclock_remaining", NekoPocketModelTest::burstCooldownUsesWallClockOnly);
        cases.put("pull_mode_when_filter_present_else_push", NekoPocketModelTest::pullModeWhenFilterPresentElsePush);
        cases.put(
            "partition_whitelist_receipt_differs_from_cell_full",
            NekoPocketModelTest::partitionWhitelistReceiptDiffersFromCellFull);
        cases.put("ghost_slot_composite_key_roundtrip", NekoPocketModelTest::ghostSlotCompositeKeyRoundTrip);
        cases.put("distill_no_aspect_does_not_advance", NekoPocketModelTest::distillNoAspectDoesNotAdvance);
        cases.put("distill_all_or_nothing_keeps_item_and_store", NekoPocketModelTest::distillAllOrNothingKeepsBoth);
        cases.put("distill_path_never_sees_container", NekoPocketModelTest::distillPathNeverSeesContainer);
        // ---- R83 A2（缺陷 3 的 (2)(3)）：β 粒度与"超单格上限"的可判定出口
        cases.put(
            "distill_same_item_across_slots_counts_per_cell",
            NekoPocketModelTest::distillSameItemAcrossSlotsCountsPerCell);
        cases.put("distill_over_cap_cell_leaves_reading", NekoPocketModelTest::distillOverCapCellLeavesReading);
        cases.put("inject_vessel_drains_into_store", NekoPocketModelTest::injectVesselDrainsIntoStore);
        cases.put(
            "inject_full_store_leaves_vessel_untouched",
            NekoPocketModelTest::injectFullStoreLeavesVesselUntouched);
        cases.put("extract_fluid_branch_moves_fluid", NekoPocketModelTest::extractFluidBranchMovesFluid);
        cases.put("extract_essence_branch_yields_crystal", NekoPocketModelTest::extractEssenceBranchYieldsCrystal);
        // ---- S-E 批：ghost 请求文法（CLR 带 kind / 分区域越界）+ 流体条与源质格两个 NEI 拖入入口
        cases.put("ghost_request_grammar_and_letters", NekoPocketModelTest::ghostRequestGrammarAndLetters);
        cases
            .put("ghost_clear_carries_kind_no_cross_clobber", NekoPocketModelTest::ghostClearCarriesKindNoCrossClobber);
        cases.put("ghost_clear_without_kind_rejected", NekoPocketModelTest::ghostClearWithoutKindIsRejected);
        cases.put("ghost_set_dispatches_kind_from_payload", NekoPocketModelTest::ghostSetDispatchesKindFromPayload);
        cases.put("ghost_set_region_bounds_rejected", NekoPocketModelTest::ghostSetRegionBoundsRejected);
        cases.put(
            "ghost_set_empty_or_foreign_payload_rejected",
            NekoPocketModelTest::ghostSetEmptyOrForeignPayloadRejected);
        cases.put("ghost_request_repeat_is_unchanged", NekoPocketModelTest::ghostRequestRepeatIsUnchanged);
        cases.put("ghost_request_malformed_rejected", NekoPocketModelTest::ghostRequestMalformedRejected);
        cases.put("ghost_applied_survives_blob_roundtrip", NekoPocketModelTest::ghostAppliedSurvivesBlobRoundTrip);
        cases.put("fluid_bar_probe_chain_order_and_fallback", NekoPocketModelTest::fluidBarProbeChainOrderAndFallback);
        cases.put("fluid_bar_drag_key_accept_and_reject", NekoPocketModelTest::fluidBarDragKeyAcceptAndReject);
        cases.put("essence_cell_tag_match_required", NekoPocketModelTest::essenceCellTagMatchRequired);
        cases.put("essence_ghost_drag_lands_essence_space", NekoPocketModelTest::essenceGhostDragLandsEssenceSpace);
        cases.put("essence_key_survives_namespaced_typeid", NekoPocketModelTest::essenceKeySurvivesNamespacedTypeId);
        // ---- S-U3（R75）/ S-U4（R78）：220 槽口径、三组流体、存档兼容、解绑新语义、C2 契约与几何闭合
        cases.put("slot_math_220_and_row_column_products", NekoPocketModelTest::slotMathAndProducts);
        cases.put(
            "real_slot_count_assertion_accepts_220_rejects_219_and_221",
            NekoPocketModelTest::realSlotCountAssertion);
        cases.put(
            "fluid_ghost_index_space_is_eighteen_tanks_with_zero_still_valid",
            NekoPocketModelTest::fluidGhostIndexSpaceSixColumns);
        cases.put(
            "storage_group_shape_roundtrip_pins_library_keys",
            NekoPocketModelTest::storageGroupShapeRoundTripPinsLibraryKeys);
        cases.put(
            "eighteen_tank_fluid_nbt_new_shape_and_two_legacy_shapes_compat",
            NekoPocketModelTest::sixTankFluidNbtCompat);
        cases.put("unbind_last_and_clear_all_semantics", NekoPocketModelTest::unbindLastAndClearAllSemantics);
        cases.put(
            "c2_texture_contract_table_matches_geometry_limits",
            NekoPocketModelTest::c2TextureContractTableMatchesGeometry);
        // ★R81④：面板宽从 416 收到主区实占 398 ⇒ 用例名跟着改（旧名 panel_geometry_closes_416x360 已在
        // 本轮成为假话，留着一个说 416 的名字比没有名字更坏）
        cases.put("panel_geometry_closes_398x360", NekoPocketModelTest::panelGeometryCloses);
        // ---- S-U4（R78）三条交付判据：D-1 内容层按库存、格序按首次入账且持久化不回收、背包格序映射
        cases.put("essence_cell_content_layer_follows_stock", NekoPocketModelTest::essenceCellContentLayerFollowsStock);
        cases.put(
            "essence_cell_order_is_first_credit_and_persists",
            NekoPocketModelTest::essenceCellOrderIsFirstCreditAndPersists);
        cases.put(
            "essence_blob_carries_cell_order_round_trip",
            NekoPocketModelTest::essenceBlobCarriesCellOrderRoundTrip);
        cases.put("backpack_slot_mapping_is_bijection", NekoPocketModelTest::backpackSlotMappingIsBijection);
        // ---- S-U7（R80）：中栏 9 列 / 220 槽 / 撤流体列标题边条 / tick→秒单源 / 双元件绑定复现
        cases.put(
            "slot_math_220_positive_with_219_and_221_negative_controls",
            NekoPocketModelTest::slotMath220WithNegativeControls);
        cases.put(
            "storage_matrix_is_fifteen_rows_of_nine_single_char",
            NekoPocketModelTest::storageMatrixIsNineColumnsSingleChar);
        cases.put("backpack_band_shares_storage_x_and_width", NekoPocketModelTest::backpackBandSharesStorageXAndWidth);
        cases.put(
            "left_column_vertical_sum_closes_without_title_strips",
            NekoPocketModelTest::leftColumnVerticalSumWithoutStrips);
        cases.put(
            "legacy_150_slot_save_shrinks_to_135_dropping_only_out_of_range",
            NekoPocketModelTest::legacyFiftySlotSaveShrinksTo135);
        cases.put("ticks_per_second_is_single_source_and_ceils", NekoPocketModelTest::ticksPerSecondSingleSource);
        cases.put(
            "bind_two_distinct_uuids_keeps_two_rows_in_memory_nbt_and_blob",
            NekoPocketModelTest::bindTwoDistinctUuidsKeepsTwoRows);
        // ---- S-U8（R81）：绑定入口面的身份门禁（★真正修根因的两条）+ 右段常驻绑定行与 398 收宽 ----
        // ★护栏说明：任务包 §2.1 那条"两枚不同 uuid ⇒ size/往返/blob 三条必须为 2"的护栏用例，
        // 磁盘上的名字是上面那条 S-U7 落的 bind_two_distinct_uuids_keeps_two_rows_in_memory_nbt_and_blob
        // （任务包写作 bind_two_distinct_cells_persists_and_roundtrips，同一判据、两个名字）；
        // 本轮**一格未动**它，也没为它改过绑定表（R81 的裁定：那三条本来就绿）。
        cases.put(
            "bind_new_cell_without_identity_materializes_and_succeeds",
            NekoPocketModelTest::bindNewCellWithoutIdentityMaterializesAndSucceeds);
        cases.put("bind_receipt_distinguishes_four_states", NekoPocketModelTest::bindReceiptDistinguishesFourStates);
        cases.put(
            "bind_persistent_rows_close_right_band_vertical_sum",
            NekoPocketModelTest::bindPersistentRowsCloseRightBandVerticalSum);
        // ---- R83 B1（缺陷 2 的执法点 + 需求 5 的"绑定格除外"与满覆盖件的 hover 链放行）
        cases.put("ghost_slots_reject_placement_at_open", NekoPocketModelTest::ghostSlotsRejectPlacementAtOpen);
        cases.put("sort_targets_skip_bound_slots", NekoPocketModelTest::sortTargetsSkipBoundSlots);
        cases.put(
            "take_out_overlay_declares_hover_pass_through",
            NekoPocketModelTest::takeOutOverlayDeclaresHoverPassThrough);
        // ---- R83 C2（缺陷 6：alt 绑定 + 每条声明的组上限滚轮）——★本批由收口片补，C2 死前零用例
        cases.put("cap_step_table_per_kind", NekoPocketModelTest::capStepTablePerKind);
        cases.put("cap_fast_multiplier_only_scales_the_step", NekoPocketModelTest::capFastMultiplierOnlyScalesTheStep);
        cases.put(
            "unset_cap_falls_back_to_today_global_amount",
            NekoPocketModelTest::unsetCapFallsBackToTodayGlobalAmount);
        cases.put("cap_clamped_and_unset_never_read_as_zero", NekoPocketModelTest::capClampedAndUnsetNeverReadAsZero);
        // ★用例名 capDirectiveRoundTrip 被生产代码 PocketGhostRequest:95 的注释点名引用 ⇒ 名字不得改（改了就是第二条悬空引用）
        cases.put("capDirectiveRoundTrip", NekoPocketModelTest::capDirectiveRoundTrip);
        cases.put("cap_not_part_of_filter_identity", NekoPocketModelTest::capNotPartOfFilterIdentity);
        cases.put("cap_consumers_actually_call_resolve_cap", NekoPocketModelTest::capConsumersActuallyCallResolveCap);
        // ---- R85 批 0（正确性修复）三条新增判据：入包契约算术 / 抽取方向停批 / ghost blob 长度预算 ----
        cases
            .put("move_to_player_counts_by_vanilla_contract", NekoPocketModelTest::moveToPlayerCountsByVanillaContract);
        cases.put("extract_batch_stops_only_on_cell_side_receipts", NekoPocketModelTest::extractBatchStopCondition);
        cases.put("ghost_blob_budget_stops_tail_byte_identical_prefix", NekoPocketModelTest::ghostBlobBudgetShape);
        // ---- R86（实机 6 条）：源质清零腾格与旧档空洞折叠（缺陷 5，改判 R78③）/ 晶化源质整叠消耗（缺陷 2）
        cases.put(
            "essence_cell_is_released_when_drained_and_legacy_hole_folds",
            NekoPocketModelTest::essenceCellReleasedAndLegacyHoleFolds);
        cases.put(
            "inject_crystal_consumes_whole_stack_into_store",
            NekoPocketModelTest::injectCrystalConsumesWholeStack);
        // ---- R87 批（A 切片）：通道方向修复——双相并存 + 反成环铁律 + 全通道轮转 + 饿死收窄
        cases.put("dual_phase_runs_inject_then_refill", NekoPocketModelTest::dualPhaseRunsInjectThenRefill);
        cases.put("report_merge_semantics_pinned", NekoPocketModelTest::reportMergeSemanticsPinned);
        cases.put(
            "anti_loop_fluid_declaration_excludes_same_fluid",
            NekoPocketModelTest::antiLoopFluidDeclarationExcludesSameFluid);
        cases.put(
            "anti_loop_essence_matches_by_tag_not_raw_key",
            NekoPocketModelTest::antiLoopEssenceMatchesByTagNotRawKey);
        cases.put(
            "multi_channel_cell_serves_all_channels_with_pair_cap",
            NekoPocketModelTest::multiChannelCellServesAllChannelsWithPairCap);
        cases.put("inject_batch_no_longer_stops_on_full", NekoPocketModelTest::injectBatchNoLongerStopsOnFull);
        // ---- R87 批（B 切片）：源质 GUI 面修复——carriesTag 晶族特判 / 声明保格 / 结晶点击入槽四态
        cases.put(
            "essence_carries_tag_crystal_family_passes_only_stocked_cells",
            NekoPocketModelTest::essenceCarriesTagCrystalFamilyPassesOnlyStockedCells);
        cases.put(
            "essence_declared_tag_keeps_cell_on_drain_and_fold",
            NekoPocketModelTest::essenceDeclaredTagKeepsCellOnDrainAndFold);
        cases.put(
            "essence_intake_crystal_from_cursor_four_states",
            NekoPocketModelTest::essenceIntakeCrystalFromCursorFourStates);
        TestRunner.run(NekoPocketModelTest.class, cases);
    }

    // ================================================================== R85 批 0（正确性修复）三条新判据
    //
    // 共同点：这三件事在 R85 之前<b>全仓零用例</b>（档案 r85-review §三-3 与 r85-ret-sync N1/N3 都点名
    // "覆盖情况：JVM 套件对 runRefillBatch 零用例"、"现无测试钉这一点"），所以修复本身可以被人
    // 一行改回去而不红。下面三条各自钉住一处"改坏了没人知道"的算术。

    /**
     * ★R85：{@code moveToPlayer} 的成交数算术（R84 服务器主线程死循环修复的<b>本体</b>，
     * 取证档案 r85-ret-sync S10 说它"已有回归"，盘上实测<b>零用例</b> ⇒ 本条补上）。
     * <p>
     * 钉三件事：① 契约形态（原版返回 true ⇔ 入参 {@code stackSize} 已被改写为 0）下
     * <b>成交数 == 请求数 − 余量</b>；② <b>永不超发</b>（成交数 &gt; 请求数就意味着同一件被算两次，
     * 也就是 R83 那版"逐 maxStackSize 切块 + 拿循环计数当成交数"的形状）；③ 生产面只有一次调用
     * （源码机检半边：{@code moveToPlayer} 方法体内 {@code addItemStackToInventory} 命中数恰为 1，
     * 出现第 2 处 ⇒ 说明有人又把循环加回来了）。
     */
    private static void moveToPlayerCountsByVanillaContract() {
        // ---- ① / ② 算术半边 ----
        SimpleAssert.eq(
            64,
            NekoPocketPanel.movedByVanillaContract(true, 64, 0),
            "契约形态：返回 true 时入参已被置 0 ⇒ 成交 = 64 − 0 = 64（与旧口径等值，R84 语义一字未动）");
        SimpleAssert.eq(
            44,
            NekoPocketPanel.movedByVanillaContract(false, 64, 20),
            "★真正的余量分支：装下 44 件后背包满了 ⇒ 成交 = 请求 64 − 余量 20（不是 0、也不是 64）");
        SimpleAssert.eq(0, NekoPocketPanel.movedByVanillaContract(false, 64, 64), "一件都没进 ⇒ 0");
        SimpleAssert.eq(0, NekoPocketPanel.movedByVanillaContract(false, 10, 20), "余量大于请求（异常入参）⇒ 0，不得造出负成交数去污染调用方账目");
        SimpleAssert.eq(0, NekoPocketPanel.movedByVanillaContract(true, 0, 0), "请求 0 件 ⇒ 0（不报'成功的空搬运'）");
        SimpleAssert.eq(0, NekoPocketPanel.movedByVanillaContract(true, -5, 0), "负请求 ⇒ 0（外来入参不得变成负成交）");
        // ★可 damaged 那一支：原版把入参对象整个塞进空槽后 return true，stackSize <b>留在原值</b>
        // （r85-review §三-1）。这一支必须仍按 want 计 ⇒ 按余量算会把它报成 0 件，调用方随后
        // 再落一次同一件 = 复制物品。
        SimpleAssert
            .eq(1, NekoPocketPanel.movedByVanillaContract(true, 1, 1), "★受损件那一支（true 但余量没置 0）⇒ 仍按请求数计，<b>不得</b>改成按余量算");
        for (int want = 0; want <= 64; want += 8) {
            for (int rest = 0; rest <= 64; rest += 8) {
                final int moved = NekoPocketPanel.movedByVanillaContract(false, want, rest);
                SimpleAssert.that(moved >= 0 && moved <= want, "false 支永不越界：want=" + want + " rest=" + rest);
                SimpleAssert.that(
                    NekoPocketPanel.movedByVanillaContract(true, want, rest) <= Math.max(0, want),
                    "true 支永不超发（超发 == 同一件入包两次）：want=" + want);
            }
        }
        // ---- ③ 接线半边（源码机检）----
        final java.util.List<String> panel = sourceLinesOrNull(
            "src/main/java/com/miaokatze/gtit/gui/pocket/NekoPocketPanel.java");
        if (panel == null) {
            System.out.println("[NOTE] 读不到 NekoPocketPanel.java ⇒ 「moveToPlayer 只有一次入包调用」的接线半边【未验】（★不是通过）");
        } else {
            final int body = methodStart(panel, "private int moveToPlayer(ItemStack toPlayer) {");
            final int after = methodStart(panel, "public static int movedByVanillaContract(");
            SimpleAssert.that(body >= 0 && after > body, "★必须能按符号定位 moveToPlayer 与它后面的纯函数（改名/挪动即红）");
            final int calls = countCodeLinesIn(panel.subList(body, after), "addItemStackToInventory(");
            SimpleAssert.eq(
                1,
                calls,
                "★moveToPlayer 方法体内 addItemStackToInventory 必须恰 <b>1</b> 处（读到 " + calls
                    + "）：0 = 入包面被摘掉，≥2 = R83 那个主线程死循环被加回来了");
        }
    }

    /**
     * ★R85：抽取方向的停批判据（钉 {@code PocketReceipt#stopsExtractBatch}，档案 r85-review A1/A3）。
     * <p>
     * 两条判据分开的理由（★不是口味）：注入方向的 {@code stopsBatch()} 是为"元件侧拒收 ⇒ 余量顺延
     * 下一轮"设计的；而抽取方向<b>每条声明有自己的落点</b>（物品 = 各自的声明格、流体 = 各自的 tank），
     * "这一条的落点满了 / 这只元件没有这个通道"与本条以外的一条都不相干。沿用 {@code stopsBatch} 的
     * 形状是：队首一条补满 ⇒ 后面全部<b>永久饿死</b>（每秒重跑仍卡在同一条）。
     * <p>
     * 本条同时钉<b>行为面</b>（走真的 {@code runRefillBatch}，取证档案点名"JVM 套件对该方法零用例"）
     * 与<b>语义面</b>（枚举断言），并把"注入方向仍照旧停批"钉在同一处 ⇒ 有人把两条并回一条就会红。
     */
    private static void extractBatchStopCondition() {
        // ---- 语义面：抽取方向只有"元件此刻给不出"才停 ----
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.OK.stopsExtractBatch(), "OK 不停批");
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.NO_CHANNEL.stopsExtractBatch(), "★没有该通道 = 这一条声明的事，不停批");
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.TARGET_FULL.stopsExtractBatch(), "★落点已满 = 这一条声明的事，不停批");
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.PARTIAL.stopsExtractBatch(), "部分成交不停批（余量本就该由下一条继续要）");
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.FULL.stopsExtractBatch(), "元件满是注入方向的结论，抽取侧不停批");
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.FILTER_REJECTED.stopsExtractBatch(), "分区拒收同上（抽取本就不受分区限制）");
        SimpleAssert.eq(Boolean.TRUE, PocketReceipt.LOST.stopsExtractBatch(), "★失联 ⇒ 停批（这只元件给不出更多，顺延下一轮）");
        SimpleAssert.eq(Boolean.TRUE, PocketReceipt.NO_ACCESS.stopsExtractBatch(), "★无读权限 ⇒ 停批（同上一条同类）");
        // ★旧谓词的本体语义一字未动（R87 只给注入批换了新谓词 stopsInjectBatch，别处还在用旧的两个）
        SimpleAssert.eq(
            Boolean.TRUE,
            PocketReceipt.NO_CHANNEL.stopsBatch(),
            "stopsBatch() 本体未动（R87 起注入批不再读它，改读 stopsInjectBatch）");
        SimpleAssert.eq(Boolean.TRUE, PocketReceipt.TARGET_FULL.stopsBatch(), "同上（本体语义保持，防把三条判据的本体合并改写）");
        // ★R87 饿死收窄：注入方向的新停批判据——只有"元件此刻给不出"才停批
        SimpleAssert.eq(Boolean.TRUE, PocketReceipt.LOST.stopsInjectBatch(), "★失联仍停批（顺延下一轮）");
        SimpleAssert.eq(Boolean.TRUE, PocketReceipt.NO_ACCESS.stopsInjectBatch(), "★无写权限仍停批（同类）");
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.FULL.stopsInjectBatch(), "★元件满不停批（记失败后跳过该来源继续）");
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.FILTER_REJECTED.stopsInjectBatch(), "★分区拒收不停批（同上）");
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.NO_CHANNEL.stopsInjectBatch(), "★无该通道不停批（计数留痕后继续）");
        SimpleAssert.eq(Boolean.FALSE, PocketReceipt.PARTIAL.stopsInjectBatch(), "部分成交不停批（余量留原槽，后续来源继续）");

        // ---- 行为面：走真的 runRefillBatch（档案点名的零覆盖面）----
        // 队首那条声明"没有该通道" ⇒ 后面两条必须照样被叫到
        final PocketCellBindings bindings = bindingsOf(CELL_A);
        final PocketFilterConfig filters = new PocketFilterConfig();
        final PocketFilterConfig.Filter first = item(1, 2621, 0, "");
        final PocketFilterConfig.Filter second = item(2, 2622, 0, "");
        final PocketFilterConfig.Filter third = item(3, 2623, 0, "");
        SimpleAssert.that(filters.add(1, first), "声明第 1 格");
        SimpleAssert.that(filters.add(2, second), "声明第 2 格");
        SimpleAssert.that(filters.add(3, third), "声明第 3 格");
        final StubOps skipper = new StubOps();
        skipper.forcedExtract.put(first.key(), PocketReceipt.NO_CHANNEL);
        skipper.extractable.put(second.key(), 5);
        skipper.extractable.put(third.key(), 5);
        final PocketChannelRunner.Report keptGoing = PocketChannelRunner
            .runRefillBatch(bindings, null, filters, skipper, 1, 64);
        SimpleAssert.eq(3, skipper.extractCalls.size(), "★队首 NO_CHANNEL 之后，第 2、3 条声明仍被叫到（旧形状在此 break ⇒ 只有 1 次）");
        SimpleAssert.eq(1, keptGoing.noChannel, "NO_CHANNEL 计了数（不停批但必须留痕，玩家读得到）");
        SimpleAssert.eq(10, keptGoing.transferred, "后面的声明真的搬到了东西（10 件）");
        SimpleAssert.eq(0, keptGoing.failures(), "NO_CHANNEL 不进 failures()（它不是「哪条声明挡住了全队」那种失败）");

        // 队首那条失联 ⇒ 必须停批（这是"元件此刻给不出"的那一档，顺延下一轮）
        final StubOps stopper = new StubOps();
        stopper.forcedExtract.put(first.key(), PocketReceipt.LOST);
        stopper.extractable.put(second.key(), 5);
        stopper.extractable.put(third.key(), 5);
        final PocketChannelRunner.Report stopped = PocketChannelRunner
            .runRefillBatch(bindings, null, filters, stopper, 1, 64);
        SimpleAssert.eq(1, stopper.extractCalls.size(), "★队首 LOST 必须 break 掉本对的剩余声明（顺延下一轮）");
        SimpleAssert.eq(1, stopped.lost, "LOST 计数走 accountFailure（玩家读得到「没认出的元件」）");
        // 队首 TARGET_FULL 同样不停批（R85 A1 的稳态：每条声明最后都会补满，一停就全队饿死）
        final StubOps fullTarget = new StubOps();
        fullTarget.forcedExtract.put(first.key(), PocketReceipt.TARGET_FULL);
        fullTarget.extractable.put(second.key(), 5);
        fullTarget.extractable.put(third.key(), 5);
        PocketChannelRunner.runRefillBatch(bindings, null, filters, fullTarget, 1, 64);
        SimpleAssert.eq(3, fullTarget.extractCalls.size(), "★队首 TARGET_FULL 不停批（「落点满」是这一条的稳态）");
    }

    /**
     * ★R85 N3：ghost blob 的<b>长度预算</b>形状（钉 {@code NekoPocketPanel#ghostBlobOf(config, budget)}）。
     * <p>
     * 上游 {@code NetworkUtils.writeStringSafe} 对超 32,693 字节的串<b>静默截断、只 WARN</b> ⇒
     * 本仓必须自己收口。三条判据各自对应"不许出现"的一种坏形状：
     * <ol>
     * <li><b>超预算时尾部整条不写</b>（解析回来的条数 &lt; 声明条数，且差额 = 未同步条数）；</li>
     * <li><b>已写部分逐字节不变</b>（与"只放前 k 条、预算无限"的拼装结果<b>完全相等</b>）⇒
     * 解析器一个字都不用改，旧客户端拿到前缀也能正常虚化（★不许出现"截半条记录"那种形状）；</li>
     * <li><b>条数差可算</b>（读数就是 {@code 权威条数 − 解析到的条数}，{@code ghost.not_synced} 那行文案
     * 用的正是它 ⇒ 文案里的数字与真实差额必须同源，不是另造一处）。</li>
     * </ol>
     */
    private static void ghostBlobBudgetShape() {
        final String fatNbt = repeated('A', 4_000);
        final PocketFilterConfig filters = new PocketFilterConfig();
        final PocketFilterConfig.Filter head = new PocketFilterConfig.ItemFilter(1, 2621, 0, repeated('a', 20));
        final PocketFilterConfig.Filter fat = new PocketFilterConfig.ItemFilter(2, 2622, 0, fatNbt);
        final PocketFilterConfig.Filter tail = new PocketFilterConfig.ItemFilter(3, 2623, 0, repeated('z', 20));
        SimpleAssert.that(filters.add(1, head), "声明第 1 格（短 NBT）");
        SimpleAssert.that(filters.add(2, fat), "声明第 2 格（★带肥 NBT，一条就能顶爆整根通道）");
        SimpleAssert.that(filters.add(3, tail), "声明第 3 格（短 NBT，排在肥条目<b>之后>）");
        SimpleAssert.eq(3, filters.size(), "三条声明都进了表");

        // ---- ① 预算内的形状：整串不超预算、尾部整条不写 ----
        final int budget = 1_000;
        final String trimmed = NekoPocketPanel.ghostBlobOf(filters, budget);
        SimpleAssert.that(trimmed.length() <= budget, "★超预算时必须收在预算内（读到 " + trimmed.length() + " > " + budget + "）");
        final PocketFilterConfig parsedBack = NekoPocketPanel.parseGhostBlob(trimmed);
        SimpleAssert.eq(1, parsedBack.size(), "预算只装得下第一条 ⇒ 尾部两条整条不写（★不是截半条）");
        // ---- ② 已写部分逐字节不变：与"表里本来就只有第一条、预算无限"的拼装结果相等 ----
        final PocketFilterConfig onlyHead = new PocketFilterConfig();
        SimpleAssert.that(onlyHead.add(1, head), "对照组：只声明第一条");
        SimpleAssert.eq(
            NekoPocketPanel.ghostBlobOf(onlyHead, Integer.MAX_VALUE),
            trimmed,
            "★已写部分必须与「没有预算时」逐字节相同（分隔符都不许多一个少一个 ⇒ 解析器与旧客户端都不用改）");
        // ---- ③ 条数差可算：读数是"权威条数 − 解析到的条数" ----
        SimpleAssert
            .eq(filters.size() - parsedBack.size(), 2, "未同步条数 = 3 − 1 = 2（★ghost.not_synced 那行文案用的就是这个差，不得另造第二个数）");
        // ---- 预算本身与上游硬顶的关系（钉住"为什么是 24,000"）----
        SimpleAssert.that(
            PocketConstants.GHOST_BLOB_MAX_CHARS > 0 && PocketConstants.GHOST_BLOB_MAX_CHARS <= 32_693,
            "★预算必须落在 (0, 32693] 里——上游 NetworkUtils 的硬顶是 Short.MAX_VALUE - 74 = 32,693 字节，"
                + "本 blob 全是 ASCII 单字节字符 ⇒ 字符数就是字节数；超出去就是静默截断");
        // ---- 不超预算时一个字节都不许多（★预算不得变成新的分叉源）----
        final PocketFilterConfig small = new PocketFilterConfig();
        SimpleAssert.that(small.add(3, tail), "一条短声明（槽号必须与声明自带的 slotIndex 一致）");
        SimpleAssert.eq(
            "ITEM|3|" + tail.key() + "|-1",
            NekoPocketPanel.ghostBlobOf(small),
            "★默认预算下正常条目逐字节照旧（三段式载荷键 + 第 4 段 cap 原始值 -1）");
    }

    /** 造一个指定长度的字符重复串（★预算用例要的是"长度可控的载荷键"，不是内容）。 */
    private static String repeated(char c, int times) {
        final char[] buffer = new char[Math.max(0, times)];
        java.util.Arrays.fill(buffer, c);
        return new String(buffer);
    }

    // ================================================================== S-U7（R80）批次
    //
    // 这一批是"9 列定稿"那一轮的验收面（任务包 §1.3 点名的就是下面七个用例名）：
    // 220 加总与 219/221 双负控、中栏矩阵的"15 行 × 9 列 + 单布局字符"、背包段与中栏**同 x 同宽**、
    // 撤掉 18 条流体列标题边条后的左栏纵向加总、旧档 150 格**收缩**到 135 的兼容、
    // tick→秒的 TICKS_PER_SECOND 单源，以及★用户点名的「绑定只能绑定一个」JVM 复现用例。

    /** ★R80① 的 220 加总正例 + 219 / 221 两个负控必抛（判据本体是 {@code assertTotalRealSlots(int,int)}）。 */
    private static void slotMath220WithNegativeControls() {
        // ---- 正例：五块加总 = 220，且每一块都能单独归因 ----
        final int storage = PocketInventory.STORAGE_SLOTS;
        final int fluid = PocketInventory.FLUID_INTERACTION_SLOTS;
        final int distill = PocketInventory.DISTILL_INPUT_SLOTS;
        final int bind = PocketInventory.BIND_SLOTS;
        final int backpack = PocketConstants.PLAYER_BACKPACK_SLOTS;
        SimpleAssert.eq(135, storage, "中栏 = 15 行 × 9 列 = 135（R80①）");
        SimpleAssert.eq(36, fluid, "流体交互 = 3 组 × 6 列 × 进/出 = 36");
        SimpleAssert.eq(12, distill, "蒸馏输入 = 2 行 × 6 列 = 12");
        SimpleAssert.eq(1, bind, "绑定格 = 1");
        SimpleAssert.eq(36, backpack, "玩家背包 = 9 × 4 = 36（框架造，不经工厂）");
        SimpleAssert.eq(220, storage + fluid + distill + bind + backpack, "★五块加总 = 135+36+12+1+36 = 220");
        SimpleAssert.eq(184, storage + fluid + distill + bind, "工厂四块 = 184");
        SimpleAssert.eq(220, PocketSlots.TOTAL_REAL_SLOTS, "容器口径常量必须等于上面的加总");
        // ---- 负控：219 与 221 都必须抛（★两条各测两个入参形态，不只测合计）----
        SimpleAssert.eq(Boolean.FALSE, throwsIllegalState(184, 36), "184 + 36 = 220 ⇒ 不抛");
        SimpleAssert.that(throwsIllegalState(183, 36), "★219 必抛：工厂少一格（中栏少接一列就是这个形态）");
        SimpleAssert.that(throwsIllegalState(185, 36), "★221 必抛：工厂多一格");
        SimpleAssert.that(throwsIllegalState(184, 35), "★219 的另一半：背包少一格（隐形槽）");
        SimpleAssert.that(throwsIllegalState(184, 37), "★221 的另一半：背包多一格");
        SimpleAssert.that(throwsIllegalState(183, 37), "★合计仍 220 但两项各自都错 ⇒ 必须抛");
    }

    /**
     * ★R80① 中栏矩阵：15 行、每行 9 格、<b>只有一个布局字符</b>。
     * <p>
     * "只有一个布局字符"这一条在这里是<b>反证</b>：{@code SlotGroupWidget$Builder} 按字符各自从 0
     * 计数（R77 实测），第二种字符会把 135 格劈成两段重叠索引 ⇒ 装配期静态块直接抛，
     * 类根本初始化不完工 ⇒ 本用例连读都读不到数。所以这里钉的是"行数 / 行宽 / 总数三者和常量同源"，
     * 静态块钉的是"不许有第二个字符"（两边都是必抛形态，见 {@code NekoPocketStorageColumn} 的 static 块）。
     */
    private static void storageMatrixIsNineColumnsSingleChar() {
        SimpleAssert.eq(15, NekoPocketStorageColumn.layoutRowCount(), "矩阵必须正好 15 行（★纵向 360 未动）");
        SimpleAssert.eq(9, NekoPocketStorageColumn.layoutMinRowWidth(), "★每行必须正好 9 格（旧口径是 10）");
        SimpleAssert
            .eq(135, NekoPocketStorageColumn.layoutSlotCount(), "矩阵产出 135 格 = 15 × 9（★数量与形状都要判：只数总数会放过「第 7 行少画一格」）");
        SimpleAssert.eq(
            PocketConstants.STORAGE_ROWS * PocketConstants.STORAGE_COLUMNS,
            NekoPocketStorageColumn.layoutSlotCount(),
            "矩阵格数 = 行数常量 × 列数常量（改矩阵忘改 {@code GHOST_ITEM_SLOT_LIMIT} 就红）");
        SimpleAssert.eq(9, NekoPocketStorageColumn.COLUMNS, "列数单源 = SlotGroup.rowSize = 矩阵行宽");
        SimpleAssert.eq(15, NekoPocketStorageColumn.ROWS, "行数 = 格数 / 列数（派生，不写第二个字面量）");
        SimpleAssert.eq(
            NekoPocketStorageColumn.HEIGHT,
            NekoPocketStorageColumn.ROWS * NekoPocketPanel.GRID,
            "列高等于行数 × 栅格（矩阵行数与像素高不得各说各话）");
    }

    /** ★R80① 用户新增的硬判据：底部带背包段与中栏<b>同 x 同宽</b>；三段必须铺满可用宽。 */
    private static void backpackBandSharesStorageXAndWidth() {
        SimpleAssert.eq(
            NekoPocketStorageColumn.X,
            NekoPocketBottomBand.BACKPACK_X,
            "★同 x：背包段左沿 = 中栏左沿（118）——不等就说明背包画歪了，而两端都不会报错");
        SimpleAssert
            .eq(NekoPocketStorageColumn.WIDTH, NekoPocketBottomBand.BACKPACK_WIDTH, "★同宽：背包段宽 = 中栏宽（162 = 9 列 × 18）");
        SimpleAssert.eq(118, NekoPocketBottomBand.BACKPACK_X, "数值钉住：118 = 6 + 112（左段）");
        SimpleAssert.eq(162, NekoPocketBottomBand.BACKPACK_WIDTH, "数值钉住：162 = 9 × 18");
        SimpleAssert.eq(112, NekoPocketBottomBand.COIN_WIDTH, "R80①：左段 112（= 中栏左沿 - 外边距）");
        SimpleAssert.eq(112, NekoPocketBottomBand.BIND_WIDTH, "★R81④：右段 112 = 6×18+4（面板收到 398 后右段的唯一解）");
        SimpleAssert.eq(0, NekoPocketBottomBand.BIND_SLACK, "★段间余量必须为 0（不是 0 = 无主空白）");
        SimpleAssert.eq(
            112 + 162 + 112,
            NekoPocketPanel.WIDTH - 2 * NekoPocketPanel.MARGIN,
            "★加总算式（R81④ 定稿）：三段之和 = 398 - 12 = 386（回执要复算的就是这条）");
        SimpleAssert.eq(
            NekoPocketPanel.WIDTH - NekoPocketPanel.MARGIN,
            NekoPocketBottomBand.BIND_X + NekoPocketBottomBand.BIND_WIDTH,
            "右段右边贴到 398-6 = 392 ⇒ 带子横向闭合");
    }

    /**
     * ★R80②：撤掉上一轮那 18 条"流体列标题边条"后的<b>左栏纵向加总</b>。
     * <p>
     * 展示稿给每组 6 个流体槽各画一条 9px 高的标题条（{@code 3 组 × 6 列 = 18} 条，
     * 覆盖在 18×36 流体槽的上半部）。用户裁定"我觉得要撤"⇒ 本列纵向必须<b>逐字回到 R78 的原式</b>
     * {@code 每组 18+36+18 = 72 ; 3×72 + 2×18 = 252 ; 252 + 末行 18 = 270 = 列高}，
     * ★既不给那 9px 留缝（撤完出现无主空白），也不把它挪去别处。
     */
    private static void leftColumnVerticalSumWithoutStrips() {
        SimpleAssert.eq(18, NekoPocketLeftColumn.CELL, "一个交互格 = 一格栅格");
        SimpleAssert.eq(36, NekoPocketLeftColumn.TANK_HEIGHT, "流体槽拉长为 36（R78② 未动）");
        SimpleAssert.eq(
            NekoPocketLeftColumn.CELL + NekoPocketLeftColumn.TANK_HEIGHT + NekoPocketLeftColumn.CELL,
            NekoPocketLeftColumn.GROUP_HEIGHT,
            "★一组只有三段：进 18 + 槽 36 + 出 18 = 72（撤边条后不得出现第四段，也不得变成 72+9）");
        SimpleAssert.eq(72, NekoPocketLeftColumn.GROUP_HEIGHT, "一组 = 72（★不是 81）");
        SimpleAssert.eq(18, NekoPocketLeftColumn.GROUP_GAP, "组间距 = 空一行");
        SimpleAssert.eq(3 * 72 + 2 * 18, NekoPocketLeftColumn.FLUID_AREA_HEIGHT, "流体块 = 3×72 + 2×18 = 252");
        SimpleAssert.eq(252, NekoPocketLeftColumn.FLUID_AREA_HEIGHT, "数值钉住：252");
        SimpleAssert.eq(18, NekoPocketLeftColumn.STATUS_HEIGHT, "末行（按钮 + 状态回显）= 18");
        SimpleAssert.eq(
            NekoPocketStorageColumn.HEIGHT,
            NekoPocketLeftColumn.FLUID_AREA_HEIGHT + NekoPocketLeftColumn.STATUS_HEIGHT,
            "★纵向恰闭合：252 + 18 = 270 = 列高 ⇒ 撤边条没留缝、也没挤掉任何一行");
        SimpleAssert.eq(
            NekoPocketLeftColumn.FLUID_AREA_HEIGHT,
            NekoPocketLeftColumn.STATUS_Y,
            "末行起点紧接流体块底部（★缝长只能等于组间距，不得给标题条留位）");
        SimpleAssert.eq(0, NekoPocketLeftColumn.groupTop(0), "第 1 组从列顶开始");
        SimpleAssert.eq(90, NekoPocketLeftColumn.groupTop(1), "第 2 组 y = 72 + 18");
        SimpleAssert.eq(180, NekoPocketLeftColumn.groupTop(2), "第 3 组 y = 2×90");
        SimpleAssert.eq(
            NekoPocketLeftColumn.FLUID_AREA_HEIGHT - NekoPocketLeftColumn.GROUP_HEIGHT,
            NekoPocketLeftColumn.groupTop(2),
            "最后一组底边正好贴到末行（不重叠、不留缝）");
        // ---- 源码半边：撤下来的边条不得在 Java 侧留形状，撤下来的信息必须有落点 ----
        assertNoFluidColumnTitleStripInSource();
    }

    /**
     * ★R80① 的旧档收缩兼容：一份"中栏 150 格"时代写的档读进 135 格的形状。
     * <p>
     * 三条判据一一对应用户那句"越界槽号丢弃 + 一次性 WARN，既不静默丢件也不炸容器"：
     * ① 读档<b>不抛</b>（面板打不开是比丢件更重的失败）；② handler 仍是构造期的 135 格
     * （档里的 {@code Size=150} <b>不得</b>把形状带大，正如 {@code Size=128} 不得把它带小）；
     * ③ 0…134 原样落位、135…149 这 15 条越界条目被丢弃且不挪到别的格上。
     * "一次性 WARN" 本身是日志面，纯 JVM 里不重跑（{@code GTInterestingThing.LOG} 不可用）⇒
     * 归【实机/日志核验项】，本用例只钉"丢的范围正确"。
     */
    private static void legacyFiftySlotSaveShrinksTo135() {
        final int legacySlots = 150;
        final NBTTagCompound legacyRoot = new NBTTagCompound();
        final NBTTagCompound group = new NBTTagCompound();
        group.setInteger("Size", legacySlots);
        final NBTTagList items = new NBTTagList();
        for (int index = 0; index < legacySlots; index++) {
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Slot", index);
            entry.setInteger("id", 1);
            entry.setInteger("Count", 1);
            items.appendTag(entry);
        }
        group.setTag("Items", items);
        legacyRoot.setTag(PocketConstants.ITEM_CONTENTS, group);

        // ① 读档不得抛（越界条目走丢弃分支）
        final PocketInventory shrunk = PocketInventory.readFrom(legacyRoot);
        SimpleAssert.eq(
            135,
            shrunk.storage()
                .getSlots(),
            "★旧档的 Size=150 不得把 handler 带大：格数永远由构造期决定");
        int kept = 0;
        for (int index = 0; index < 135; index++) {
            if (shrunk.storageStack(index) != null) {
                kept++;
            }
        }
        if (itemNbtUsable()) {
            SimpleAssert.eq(135, kept, "0…134 条目原样落位，一件不多一件不少");
        } else {
            System.out.println("[NOTE] 本 JVM 里 ItemStack 的 NBT 往返解不出物品 ⇒ 「0…134 一件不少」这一半属【实机项】；此处仍验形状与格数");
        }
        // ② 越界那 15 条不得"挪回来"：末格只可能是第 134 格有货，且再往后不存在格子
        SimpleAssert.that(shrunk.storageStack(134) != null || !itemNbtUsable(), "末格（134）必须还在合法索引内");
        boolean outOfRangeThrows = false;
        try {
            shrunk.storageStack(135);
        } catch (RuntimeException expected) {
            outOfRangeThrows = true;
        }
        SimpleAssert.that(outOfRangeThrows, "★135 号已不是合法槽位：读它必须越界抛（说明形状真的收成了 135，没被旧档带大）");
        // ③ 写档一律按新形状（旧档的 150 号条目不会复活，Size 也不得写回 150）
        final NBTTagCompound rewritten = new NBTTagCompound();
        shrunk.writeTo(rewritten);
        final NBTTagCompound written = rewritten.getCompoundTag(PocketConstants.ITEM_CONTENTS);
        if (itemNbtUsable()) {
            SimpleAssert.eq(135, written.getInteger("Size"), "★写档按新形状 Size=135（旧档的 150 不得被写回去）");
            SimpleAssert.eq(
                kept,
                written.getTagList("Items", 10)
                    .tagCount(),
                "写回的条目数 = 读到的条目数（越界条目已丢弃，不得被写档复活）");
        } else {
            // 本 JVM 里物品解不出来 ⇒ handler 全空 ⇒ saveGroup 的"空区不留壳"把整个键 removeTag。
            // 这一半（Size/条目数）因此属【实机项】，此处只钉"绝不复活出 150 格形状"。
            System.out.println("[NOTE] 物品 NBT 在本 JVM 解不出 ⇒ 写回的 Size/条目数属【实机项】；此处只验空区不留壳与形状");
            SimpleAssert.eq(
                Boolean.FALSE,
                rewritten.hasKey(PocketConstants.ITEM_CONTENTS),
                "空区不留壳：读回 0 件时不得写出 contents 壳，更不得写出 Size=150");
            SimpleAssert.eq(
                135,
                shrunk.storage()
                    .getSlots(),
                "★形状仍由构造期决定（写档往返不影响 handler 格数）");
        }
    }

    /**
     * ★R80③ tick→秒收成 {@code PocketConstants.TICKS_PER_SECOND} 单源。
     * <p>
     * 两半：① 语义半边纯 JVM 可测（基数、派生量、向上取整表的每个边界）；
     * ② "除定义处外不得再出现 {@code / 20} 与 {@code + 19}"是<b>源码文本</b>判据 ⇒
     * 走 {@link #countPocketSourceLinesMatching}：仓库根找不到（在别的目录跑测试）就打 NOTE 并跳过，
     * <b>不把"没测"写成"测过"</b>。
     */
    private static void ticksPerSecondSingleSource() {
        SimpleAssert.eq(20, PocketConstants.TICKS_PER_SECOND, "Minecraft 固定 20 tick / 秒（★不可配，也不可在别处写 20）");
        SimpleAssert.eq(50, PocketConstants.MILLISECONDS_PER_TICK, "一刻 = 1000/20 = 50 ms（★同理不得在别处写 50）");
        SimpleAssert.eq(
            PocketConstants.TICKS_PER_SECOND,
            PocketConstants.CHANNEL_TICK_PERIOD,
            "短效通道节拍 = 每 tick→秒基数一批（旧实现里这里是第二个字面量 20）");
        SimpleAssert.eq(
            100,
            PocketConstants.BURST_SHOW_TICKS,
            "burst 显示窗口 = 5000ms / 50ms = 100 tick（★派生自 TICKS_PER_SECOND，不写 50）");
        SimpleAssert.eq(
            600,
            PocketConstants.SHORT_CHANNEL_SECONDS * PocketConstants.TICKS_PER_SECOND,
            "短效通道 30 秒 = 600 tick（★秒↔tick 只走 TICKS_PER_SECOND 这一个基数）");
        SimpleAssert.eq(
            (int) (PocketConstants.BURST_ANIMATION_MS / PocketConstants.MILLISECONDS_PER_TICK),
            PocketConstants.BURST_SHOW_TICKS,
            "burst 显示窗口的 tick 数必须由 MILLISECONDS_PER_TICK 派生（不得再写 50）");
        // ---- 向上取整的每一个边界 ----
        SimpleAssert.eq(0, PocketConstants.ticksToSecondsCeil(0), "没有剩余 ⇒ 0 秒（不得显示成「还剩 1 秒」）");
        SimpleAssert.eq(0, PocketConstants.ticksToSecondsCeil(-5), "负数按 0 处理（倒计时键被外来的档写坏也不炸）");
        SimpleAssert.eq(1, PocketConstants.ticksToSecondsCeil(1), "1 tick ⇒ 1 秒");
        SimpleAssert.eq(1, PocketConstants.ticksToSecondsCeil(19), "19 tick ⇒ 1 秒（★向下取整会给 0 秒 = 骗玩家说已结束）");
        SimpleAssert.eq(1, PocketConstants.ticksToSecondsCeil(20), "20 tick ⇒ 1 秒（整除边界）");
        SimpleAssert.eq(2, PocketConstants.ticksToSecondsCeil(21), "21 tick ⇒ 2 秒");
        SimpleAssert.eq(5, PocketConstants.ticksToSecondsCeil(100), "100 tick ⇒ 5 秒（蒸馏一轮的间隔）");
        SimpleAssert.eq(6, PocketConstants.ticksToSecondsCeil(101), "101 tick ⇒ 6 秒");
        SimpleAssert.eq(
            5,
            PocketConstants.ticksToSecondsCeil(TaumDistillRules.DISTILL_INTERVAL_TICKS),
            "★tooltip 的「蒸馏一轮几秒」必须走同一个换算函数（旧实现是内联 / 20）");
        // ---- 源码文本半边 ----
        final int inlineDivide20 = countPocketSourceLinesMatching("/\\s*20\\b", true);
        final int inlinePlus19 = countPocketSourceLinesMatching("\\+\\s*19\\b", true);
        final int helperUses = countPocketSourceLinesMatching("ticksToSecondsCeil\\(", false);
        if (inlineDivide20 < 0) {
            System.out.println("[NOTE] 找不到仓库根 ⇒ TICKS_PER_SECOND 的「源码内联」半边未验（★不是通过）");
            return;
        }
        SimpleAssert.eq(0, inlineDivide20, "★pocket 源码里（注释行除外）不得再有内联的 / 20（全部走 ticksToSecondsCeil）");
        SimpleAssert.eq(0, inlinePlus19, "★向上取整的 + 19 也只能活在 PocketConstants 那一处（注释行除外）");
        SimpleAssert.that(helperUses >= 3, "三处换算点必须都改走 ticksToSecondsCeil（读到 " + helperUses + " 处）");
    }

    /**
     * ★R80④「绑定只能绑定一个」的 JVM 复现用例（与只读的 S-E3 并行；代码归本片）。
     * <p>
     * 三条断言按用户给的三环逐条证真/证伪：
     * ① 数据面：连续绑两枚<b>不同 uuid</b> ⇒ {@code size()} 必须是 2；
     * ② 持久面：{@code writeTo} → {@code readFrom} 往返后仍是 2（NBT 是列表形状、条目键不撞）；
     * ③ 显示面：绑定行 blob（{@code SYNC_BIND_ROWS} 的线格式）编出来再解码后仍是 2 行。
     * <p>
     * ★本用例<b>不许</b>因为"现状如此"被改掉或删掉：三条都绿 ⇒ 缺陷不在 JVM 可证的这三环，
     * 结论只能往显示面/实机面（元件 uuid 是否真的不同、归还是否成功、tooltip 是否被截断）去追。
     * <p>
     * ★R84 说明（防后来人误读成"用户裁定没落地"）：本用例走的是<b>数据层</b> {@code PocketCellBindings.bind}，
     * 那一层的上限仍是 {@code MAX_BOUND_CELLS = 64}（防 NBT 无界膨胀，且旧档多条目往返、逐条 mode、
     * 身份去重回填位置这些机制只有多条目才表达得出来）。玩家可感知的"只能绑一枚"钉在<b>入口面</b>
     * {@code PocketBindFlow.bind}（见 {@code bind_new_cell_without_identity_materializes_and_succeeds}）
     * 与<b>服务面</b> {@code PocketChannelRunner}，两处都不碰这里。
     */
    private static void bindTwoDistinctUuidsKeepsTwoRows() {
        final PocketCellBindings bindings = new PocketCellBindings();
        SimpleAssert.that(bindings.bind(CELL_A, PocketConstants.MODE_DISK_UUID), "第一枚（CELL_A）绑定应新增条目");
        SimpleAssert.that(
            bindings.bind(CELL_B, PocketConstants.MODE_DISK_UUID),
            "★第二枚（CELL_B，不同 uuid）绑定也必须新增条目 —— 复现点就在这一步的返回值");
        SimpleAssert.eq(2, bindings.size(), "★环①：连续绑两枚不同 uuid 后 size() 必须是 2");
        SimpleAssert.eq(
            2,
            bindings.entries()
                .size(),
            "entries() 也必须是两条（顺序即轮转外层序）");
        SimpleAssert.eq(
            CELL_A,
            bindings.entries()
                .get(0).id,
            "第一条是先到的那枚（★不得被后一条挤掉）");
        SimpleAssert.eq(
            CELL_B,
            bindings.entries()
                .get(1).id,
            "第二条是后到那枚");
        SimpleAssert.that(bindings.contains(CELL_A), "contains 认得第一枚");
        SimpleAssert.that(bindings.contains(CELL_B), "contains 认得第二枚");
        SimpleAssert.that(bindings.hasRoom(), "两枚远未到 MAX_BOUND_CELLS=64 ⇒ 仍应有空位");

        // ---- 环②：NBT 往返 ----
        final NBTTagCompound root = new NBTTagCompound();
        bindings.writeTo(root);
        final PocketCellBindings back = PocketCellBindings.readFrom(root);
        SimpleAssert.eq(2, back.size(), "★环②：NBT 往返后仍必须是 2 条（塌回 1 = 写侧覆盖或读侧键撞）");
        SimpleAssert.eq(
            CELL_A,
            back.entries()
                .get(0).id,
            "往返后第一条身份不变");
        SimpleAssert.eq(
            CELL_B,
            back.entries()
                .get(1).id,
            "往返后第二条身份不变");

        // ---- 环③：绑定行 blob（显示面的线格式）----
        final List<NekoPocketBottomBand.Row> rows = new ArrayList<>();
        for (PocketCellBindings.Entry entry : back.entries()) {
            rows.add(
                new NekoPocketBottomBand.Row(
                    entry.id,
                    NekoPocketBottomBand.Row.STATUS_UNLOCATED,
                    PocketConstants.UNLOCATED,
                    PocketConstants.UNLOCATED,
                    PocketConstants.UNLOCATED,
                    PocketConstants.UNLOCATED,
                    PocketConstants.UNLOCATED));
        }
        final String blob = NekoPocketBottomBand.Row.compose(rows);
        SimpleAssert.eq(
            2,
            NekoPocketBottomBand.Row.parse(blob)
                .size(),
            "★环③：绑定行 blob 解码后必须仍是 2 行（只解出 1 行 = 玩家「只能看到一个」的直接根因）");
        // 生产写侧（NekoPocketPanel#composeBindRows 的未定位分支）逐字同形的第二种取法：
        // 手写串，钉住"|分隔 7 段、; 分隔多行"这一线格式本身
        final String unlocated = "|" + PocketConstants.UNLOCATED
            + "|"
            + PocketConstants.UNLOCATED
            + "|"
            + PocketConstants.UNLOCATED
            + "|"
            + PocketConstants.UNLOCATED
            + "|"
            + PocketConstants.UNLOCATED;
        final String productionShape = CELL_A + "|"
            + NekoPocketBottomBand.Row.STATUS_UNLOCATED
            + unlocated
            + ";"
            + CELL_B
            + "|"
            + NekoPocketBottomBand.Row.STATUS_UNLOCATED
            + unlocated;
        final List<NekoPocketBottomBand.Row> parsed = NekoPocketBottomBand.Row.parse(productionShape);
        SimpleAssert.eq(2, parsed.size(), "★生产形状的 blob（两枚未定位）也必须解出 2 行");
        SimpleAssert.eq(CELL_A, parsed.get(0).id, "生产形状解出的第一枚身份");
        SimpleAssert.eq(CELL_B, parsed.get(1).id, "生产形状解出的第二枚身份");
        SimpleAssert
            .that(parsed.get(0).status != NekoPocketBottomBand.Row.STATUS_LOCATED, "未定位行不得被解成已定位（bind.unlocated 是正常态）");
    }

    // ================================================================== S-U8（R81）批次
    //
    // ★这一批钉的是"绑定只能绑定一个"的**根因面**（R81：入口面身份门禁 + performBind 不分态），
    // 与上面那条 S-U7 的复现用例（任务包 §2.1 里曾用名 bind_two_distinct_cells_persists_and_roundtrips，
    // 落盘名 = bind_two_distinct_uuids_keeps_two_rows_in_memory_nbt_and_blob）是**互补**关系：
    // 那条证明"数据/持久/显示三面本来就支持多条"（护栏，本轮一格未动），这一批证明
    // "新元件根本进不到数据面"这条门禁已被拆掉，且四种结果各有可见回执。

    /**
     * 假元件的<b>身份读写面</b>（★逐字照 {@code PocketCellProbe.bindIdentity} 的语义建模，
     * 而不是照 {@code StorageManager} 的实现建模）：
     * <ul>
     * <li>{@link #read()} 在物化之前返回 {@code null} —— 这就是 R81 的根因形状
     * （{@code diskuuid} 由服务端<b>首次取用时惰性分配</b>，新元件从没进过驱动器 ⇒ 读不到）；</li>
     * <li>{@link #materialize()} 才把身份写进去，且<b>调用次数被计数</b>：客户端那一跳必须一次都不调；</li>
     * <li>{@link #reads}/{@link #materializations} 存在就是为了机检"物化后必须<b>重读</b>"这一跳：
     * 只调一次 read 的实现（拿 materialize 的返回值当真相）会在这里红。</li>
     * </ul>
     */
    private static final class LazyIdentityCell implements PocketBindFlow.Identity {

        private final String uuid;
        /** {@code false} = 一枚"服务端也物化不出身份"的元件（StorageManager 未初始化那一族）。 */
        private final boolean materializable;
        private String written;
        int reads;
        int materializations;

        LazyIdentityCell(String uuid, boolean materializable) {
            this.uuid = uuid;
            this.materializable = materializable;
        }

        @Override
        public String read() {
            reads++;
            return written;
        }

        @Override
        public void materialize() {
            materializations++;
            if (materializable && written == null) {
                written = uuid;
            }
        }
    }

    /**
     * ★R81②：新元件（NBT 里还没有 {@code diskuuid}）绑不上这条根因，修复后必须
     * <b>在服务端物化一次身份 → 重读 → 绑定成功</b>，且客户端那一跳<b>一次都不许物化</b>。
     * <p>
     * 为什么这条能钉住症状：修前 {@code performBind} 只看 {@code cellUuid() == null}，新元件被发回
     * 「把元件放入此格」这句<b>反向误导</b>文案 ⇒ 玩家换个位置再试还是失败，观感就是"只能绑一个"。
     * <p>
     * ★R84 改口径：本用例原来连着绑<b>两枚</b>新元件并断言条数走到 2，那一条已被用户的裁定
     * （"必须要求只能绑 1 个，不能对多个"）反转为"第二枚必须 FULL 拒收"。但 R81 的真根因面
     * <b>一个字都没放松</b>：拒收也要先把身份物化出来，且回执必须是"先解绑"而不是"把元件放入此格"。
     */
    private static void bindNewCellWithoutIdentityMaterializesAndSucceeds() {
        // ---- 前置：新元件的身份读不出来（★不是"格子里没放元件"，两者必须在四态里分得开）----
        final LazyIdentityCell precondition = new LazyIdentityCell(CELL_A, true);
        SimpleAssert.eq(null, precondition.read(), "★前置条件：从未进过驱动器的新元件读不到身份（R81 根因形状）");
        SimpleAssert.eq(1, precondition.reads, "读一次就够（读侧不写 NBT，两端都能读）");

        final PocketCellBindings bindings = new PocketCellBindings();

        // ---- 客户端那一跳：不得物化（客户端写物品 NBT 永不到达服务端，本仓已证死）----
        final LazyIdentityCell clientCell = new LazyIdentityCell(CELL_A, true);
        SimpleAssert.eq(
            PocketBindFlow.Result.NO_IDENTITY,
            PocketBindFlow.bind(bindings, true, clientCell, false),
            "客户端点绑定 ⇒ 无身份（★不是 slot_hint：格子里确实有元件）");
        SimpleAssert.eq(0, clientCell.materializations, "★客户端一次都不许调用 materialize()");
        SimpleAssert.eq(1, clientCell.reads, "客户端只读一次，读到空就收工");
        SimpleAssert.eq(0, bindings.size(), "客户端那一跳不写表");

        // ---- 服务端那一跳：物化 → **重读** → 新增 ----
        final LazyIdentityCell serverCell = new LazyIdentityCell(CELL_A, true);
        SimpleAssert.eq(
            PocketBindFlow.Result.ADDED,
            PocketBindFlow.bind(bindings, true, serverCell, true),
            "服务端点绑定 ⇒ 物化身份后新增成功（★修前这里是误导回执）");
        SimpleAssert.eq(1, serverCell.materializations, "服务端只物化一次（第二次点击不该再写 NBT）");
        SimpleAssert.eq(2, serverCell.reads, "★读两次 = 物化前 1 次 + 物化后**重读** 1 次（少一次就是没重读、拿返回值当真相）");
        SimpleAssert.eq(1, bindings.size(), "新元件绑定后 size = 1");
        SimpleAssert.that(bindings.contains(CELL_A), "物化出来的身份就是表里那条");

        // ---- ★R84 反转：第二枚<b>不同身份</b>必须被拒（用户裁定"只能绑 1 个，不能对多个"）----
        // 拒收也必须走 FULL（"请先解绑"），不得退化成 NO_IDENTITY / slot_hint 那种反向误导——
        // 那正是 R81 点名的形态，所以这一支仍然要先把身份物化出来。
        final LazyIdentityCell secondCell = new LazyIdentityCell(CELL_B, true);
        SimpleAssert.eq(
            PocketBindFlow.Result.FULL,
            PocketBindFlow.bind(bindings, true, secondCell, true),
            "★第二枚不同身份 ⇒ FULL（已达 ALLOWED_BOUND_CELLS）");
        SimpleAssert.eq(1, bindings.size(), "绑定表仍只有第一枚，第二枚不得进表");
        SimpleAssert.that(!bindings.contains(CELL_B), "第二枚的身份没有进表");
        SimpleAssert.eq(1, secondCell.materializations, "★被拒也要物化过一次（否则玩家解绑第一枚后还得把元件再塞回去重新触发）");
        // ---- ★上限收到 1 之后最容易踩的坑：同身份重绑必须仍是 DUPLICATE，不能被"表满"挡掉 ----
        final LazyIdentityCell rebindSame = new LazyIdentityCell(CELL_A, true);
        SimpleAssert.eq(
            PocketBindFlow.Result.DUPLICATE,
            PocketBindFlow.bind(bindings, true, rebindSame, true),
            "表已满时同身份重绑仍走 DUPLICATE（R81 口径：contains 那一支永远先行放行）");
        SimpleAssert.eq(1, bindings.size(), "同身份重绑不涨条目");

        // ---- 落档往返（★护栏：物化出来的身份必须能存能读，否则下次开面板又是 0 条）----
        final NBTTagCompound root = new NBTTagCompound();
        bindings.writeTo(root);
        final PocketCellBindings back = PocketCellBindings.readFrom(root);
        SimpleAssert.eq(1, back.size(), "物化 + 绑定后的 NBT 往返仍是 1 条");
        SimpleAssert.eq(
            CELL_A,
            back.entries()
                .get(0).id,
            "往返后第一条身份不变");

        // ---- 表满这一支不算在四态里，但也不能被物化路径绕过（★不写第二条目）----
        final PocketCellBindings stuffed = new PocketCellBindings();
        for (int index = 0; index < PocketConstants.MAX_BOUND_CELLS; index++) {
            SimpleAssert.that(stuffed.bind(uuidOf(index), PocketConstants.MODE_DISK_UUID), "装满上限内每条都新增");
        }
        final LazyIdentityCell overflow = new LazyIdentityCell(uuidOf(900), true);
        SimpleAssert.eq(
            PocketBindFlow.Result.FULL,
            PocketBindFlow.bind(stuffed, true, overflow, true),
            "表满 ⇒ FULL（★身份照样被物化出来，但条目不涨，旧口径逐字保留）");
        SimpleAssert.eq(PocketConstants.MAX_BOUND_CELLS, stuffed.size(), "★MAX_BOUND_CELLS 未被本次修复改动，且表满不产生第二条目");
    }

    /**
     * ★R81①：一次绑定请求的<b>四态各回一条不同的 lang 键</b>。
     * <p>
     * 修前的形态是"三种结果共用一条误导文案 + 同身份重绑<b>完全静默</b>"（{@code bind()} 的返回值
     * 被丢掉，就是 R81 点名的次因）。这条用例做两件事：
     * <ol>
     * <li>把 {@link PocketBindFlow.Result#FOUR_STATES} 的四条 lang 键取出来<b>两两比不等</b>，
     * 并逐字钉住"哪一态发哪一个键"（换键名必须连同文案一起过一遍"是否反向误导"）；</li>
     * <li>用真实的 {@link PocketCellBindings} 把四态各<b>跑出来</b>一次（不是只查枚举存在）。</li>
     * </ol>
     */
    private static void bindReceiptDistinguishesFourStates() {
        // ---- ① 四态 → 四个互不相同的键 ----
        final Map<String, String> keys = new LinkedHashMap<>();
        for (PocketBindFlow.Result result : PocketBindFlow.Result.FOUR_STATES) {
            final String key = PocketBindFlow.langKeyOf(result);
            SimpleAssert.that(key != null && key.startsWith("gtit.pocket.bind."), result + " 必须有 gtit.pocket.bind.* 键");
            keys.put(result.name(), key);
        }
        SimpleAssert.eq(4, PocketBindFlow.Result.FOUR_STATES.length, "四态枚举恰好四个（表满 FULL 是第五态，沿用 R43a 的旧键）");
        SimpleAssert.eq("gtit.pocket.bind.added", keys.get("ADDED"), "成功 → bind.added（修前静默不发回执）");
        SimpleAssert.eq("gtit.pocket.bind.slot_hint", keys.get("NOT_A_CELL"), "非元件 → 旧键 slot_hint（★唯一措辞与事实相符的一支）");
        SimpleAssert
            .eq("gtit.pocket.bind.no_identity", keys.get("NO_IDENTITY"), "无身份 → 新键 no_identity（★不得复用 slot_hint）");
        SimpleAssert.eq("gtit.pocket.bind.dup", keys.get("DUPLICATE"), "同身份重绑 → 新键 dup（修前完全静默）");
        final Set<String> distinct = new LinkedHashSet<>(keys.values());
        SimpleAssert.eq(4, distinct.size(), "★四态回执键必须两两不同（相同 = 玩家仍分不清这四种情况）");
        SimpleAssert.that(
            !distinct.contains("gtit.pocket.bind.slot_hint") || distinct.size() == 4,
            "slot_hint 只许出现在 NOT_A_CELL 一支");

        // ---- ② 四态各真跑一次 ----
        final PocketCellBindings bindings = new PocketCellBindings();

        // (a) 格内非元件：空格子 / 别的物品 ⇒ 不碰表、不物化
        final LazyIdentityCell notCell = new LazyIdentityCell(CELL_A, true);
        SimpleAssert.eq(
            PocketBindFlow.Result.NOT_A_CELL,
            PocketBindFlow.bind(bindings, false, notCell, true),
            "格内非元件 ⇒ NOT_A_CELL");
        SimpleAssert.eq(0, notCell.materializations, "★非元件不得触发物化（否则误导文案换了个方向）");
        SimpleAssert.eq(0, bindings.size(), "非元件不写表");

        // (b) 元件但连服务端都物化不出身份（StorageManager 未初始化那一族）⇒ NO_IDENTITY，
        // ★措辞必须是"身份没能写入"，不得再叫玩家"把元件放入此格"
        final LazyIdentityCell stubborn = new LazyIdentityCell(CELL_A, false);
        SimpleAssert.eq(
            PocketBindFlow.Result.NO_IDENTITY,
            PocketBindFlow.bind(bindings, true, stubborn, true),
            "物化失败 ⇒ NO_IDENTITY");
        SimpleAssert.eq(1, stubborn.materializations, "服务端确实试过物化一次");
        SimpleAssert.eq(0, bindings.size(), "无身份不写表");

        // (c) 成功 ⇒ ADDED
        SimpleAssert.eq(
            PocketBindFlow.Result.ADDED,
            PocketBindFlow.bind(bindings, true, new LazyIdentityCell(CELL_A, true), true),
            "有身份（或物化得出）⇒ ADDED");

        // (d) 同身份重绑 ⇒ DUPLICATE（★条目数不变，但玩家必须看到一条回执；位置快照仍按旧口径刷新）
        final PocketBindFlow.Result again = PocketBindFlow
            .bind(bindings, true, new LazyIdentityCell(CELL_A, true), true);
        SimpleAssert.eq(PocketBindFlow.Result.DUPLICATE, again, "同身份第二次 ⇒ DUPLICATE（修前静默）");
        SimpleAssert.eq(1, bindings.size(), "★同身份重绑不涨条目（追加语义只认不同身份，R81 无罪面①）");
        SimpleAssert.that(
            !keys.get("DUPLICATE")
                .equals(keys.get("ADDED")),
            "同身份重绑的回执必须与新增不同（修前两者都无回执）");
    }

    /**
     * ★R81③ + R81④：底部带<b>右段</b>在面板宽收到 398 之后的横纵加总，含两条<b>常驻</b>绑定行的行位。
     * <p>
     * 与 S-U7 的 {@code backpack_band_shares_storage_x_and_width} 同一口径（★每条都是加算式，
     * 不是抄来的数），只是对象从背包段换到右段：
     * <ul>
     * <li>横向 {@code 6 + 112 + 162 + 112 + 6 = 398 = 面板宽}（★旧口径那个"让位量"具名常量已删）；</li>
     * <li>右段内部 {@code 4(绳缝) + 108(内容) = 112}，且内容区与<b>源质列同 x 同宽</b>；</li>
     * <li>右段纵向 {@code 4 行 × 18 = 72 = 带高}（★零富余），常驻绑定行位 = {@code 4 − 2} = 2。</li>
     * </ul>
     */
    private static void bindPersistentRowsCloseRightBandVerticalSum() {
        final int grid = NekoPocketPanel.GRID;
        final int margin = NekoPocketPanel.MARGIN;
        // ---- ① 面板宽与三段（R81④）----
        SimpleAssert.eq(398, NekoPocketPanel.WIDTH, "★R81④：面板宽 = 主区实占 = 6+108+4+162+4+108+6");
        SimpleAssert.eq(
            NekoPocketPanel.MAIN_OCCUPIED_WIDTH,
            NekoPocketPanel.WIDTH,
            "★面板宽与实占相等 ⇒ 右侧没有一像素无主空白（旧口径的 18px 让位量已随具名量一起删除）");
        SimpleAssert.eq(6 + 108 + 4 + 162 + 4 + 108 + 6, NekoPocketPanel.WIDTH, "★加算式逐字（回执里要复算的就是这条）");
        SimpleAssert.eq(
            margin + NekoPocketBottomBand.COIN_WIDTH
                + NekoPocketBottomBand.BACKPACK_WIDTH
                + NekoPocketBottomBand.BIND_WIDTH
                + margin,
            NekoPocketPanel.WIDTH,
            "三段 + 两个外边距 = 面板宽（左 112 | 背包 162 | 右 112）");
        SimpleAssert.eq(112, NekoPocketBottomBand.BIND_WIDTH, "★R81④：右段从 130 收到 112 = 6×18 + 4");
        SimpleAssert.eq(0, NekoPocketBottomBand.BIND_SLACK, "段间余量仍必须为 0（R80① 的具名账未破）");
        SimpleAssert.eq(112 + 162 + 112, NekoPocketPanel.WIDTH - 2 * margin, "★加算式：三段之和 = 398 − 12 = 386");
        SimpleAssert.eq(
            NekoPocketPanel.WIDTH - margin,
            NekoPocketBottomBand.BIND_X + NekoPocketBottomBand.BIND_WIDTH,
            "右段右边贴到 398 − 6 ⇒ 带子横向闭合");
        // ---- ② 右段内部：绳缝 + 内容（内容区与源质列同 x 同宽）----
        SimpleAssert.eq(4, NekoPocketBottomBand.BIND_ROPE_WIDTH, "绳缝位 = 一条列间距宽");
        SimpleAssert.eq(108, NekoPocketBottomBand.BIND_CONTENT_WIDTH, "★内容区宽 = 112 − 4 = 108");
        SimpleAssert.eq(
            NekoPocketEssenceColumn.X,
            NekoPocketBottomBand.BIND_X + NekoPocketBottomBand.BIND_CONTENT_X,
            "★右段内容区与源质列**同 x**（284）——背包段那条硬判据搬到右段");
        SimpleAssert
            .eq(NekoPocketEssenceColumn.WIDTH, NekoPocketBottomBand.BIND_CONTENT_WIDTH, "★右段内容区与源质列**同宽**（108）");
        SimpleAssert.eq(
            NekoPocketBottomBand.BIND_CONTENT_X + NekoPocketBottomBand.BIND_CONTENT_WIDTH,
            NekoPocketBottomBand.BIND_WIDTH,
            "绳缝 + 内容 = 段宽（★rope 不再画到背包段的槽位上）");
        // ---- ③ 每一行的横向归属（★每一像素都有主）----
        final int buttonWidth = PocketGuiTextureContract.widthOf("POCKET_C2_bindbtn");
        SimpleAssert.eq(106, buttonWidth, "绑定按钮原生宽取契约表");
        SimpleAssert.eq(
            NekoPocketBottomBand.BIND_BUTTON_X + buttonWidth
                + (NekoPocketBottomBand.BIND_BUTTON_X - NekoPocketBottomBand.BIND_CONTENT_X),
            NekoPocketBottomBand.BIND_WIDTH,
            "★行 0：左余 + 按钮 + 右余 = 112（按钮在 108 的内容区里居中，左右各 1px）");
        SimpleAssert.eq(
            NekoPocketBottomBand.BIND_BUTTON_X - NekoPocketBottomBand.BIND_CONTENT_X,
            NekoPocketBottomBand.BIND_CONTENT_X + NekoPocketBottomBand.BIND_CONTENT_WIDTH
                - NekoPocketBottomBand.BIND_BUTTON_X
                - buttonWidth,
            "按钮左右余量必须相等（居中，不是随手偏移）");
        SimpleAssert.eq(26, NekoPocketBottomBand.BIND_TEXT_X, "★行 1/行 3 的文字起点 = 4(绳) + 18(控件) + 4");
        SimpleAssert.eq(
            NekoPocketBottomBand.BIND_TEXT_X + NekoPocketBottomBand.BIND_TEXT_WIDTH,
            NekoPocketBottomBand.BIND_WIDTH,
            "行 1（读数）与行 3（常驻绑定行 1）铺到段宽右沿：26 + 86 = 112");
        SimpleAssert.eq(86, NekoPocketBottomBand.BIND_TEXT_WIDTH, "文字宽 86（★0.5 缩放下 172 逻辑像素，装得下读数与截断提示）");
        SimpleAssert.eq(
            NekoPocketBottomBand.BIND_CONTENT_X + NekoPocketBottomBand.BIND_ROW_WIDTH,
            NekoPocketBottomBand.BIND_WIDTH,
            "★行 2（常驻绑定行 0）整幅铺到段宽右沿：4 + 108 = 112");
        // ---- ④ 纵向：四行 × 18 = 72 = 带高，常驻行位 = 2（★修前是 0）----
        SimpleAssert.eq(4, NekoPocketBottomBand.BIND_ROWS, "右段行位 = 带高 / 栅格 = 72 / 18 = 4");
        SimpleAssert.eq(
            NekoPocketBottomBand.BIND_ROWS * grid,
            NekoPocketBottomBand.HEIGHT,
            "★纵向加总恰闭合（零富余 ⇒ 既没有无主空白，也不会把绑定行挤出带子）");
        SimpleAssert.eq(2, NekoPocketBottomBand.PERSISTENT_ROWS, "★常驻绑定行位 = 4 − 按钮行 − 绑定格行 = 2（修前 = 0）");
        SimpleAssert.that(NekoPocketBottomBand.PERSISTENT_ROWS >= 1, "常驻行位不得回到 0（取证记录 §3 点名的「面缺失」）");
        SimpleAssert.eq(36, NekoPocketBottomBand.persistentRowY(0), "常驻行 0 的 y = 2×18 = 36");
        SimpleAssert.eq(54, NekoPocketBottomBand.persistentRowY(1), "常驻行 1 的 y = 3×18 = 54");
        SimpleAssert.eq(
            NekoPocketBottomBand.persistentRowY(NekoPocketBottomBand.PERSISTENT_ROWS - 1) + grid,
            NekoPocketBottomBand.HEIGHT,
            "★最后一条常驻行的下沿正好落在带底（下面没有余量，上面也没有缝）");
        SimpleAssert.eq(
            NekoPocketBottomBand.BIND_HELP_Y + grid,
            NekoPocketBottomBand.HEIGHT,
            "帮助按钮占满最后一行（★旧口径「18+2+18+4+18 = 60，余 12」的富余已改成常驻行）");
    }

    /**
     * 数 pocket 两个源码目录下匹配某正则的<b>行</b>数（注释行除外）。
     *
     * @param skipComments true = 跳过 {@code //} 与 javadoc 续行（历史叙述里出现"150/20"是合法的）
     * @return 命中行数；{@code -1} = 找不到仓库根（在别的目录跑测试）⇒ 调用方必须打 NOTE，不得当 0 用
     */
    private static int countPocketSourceLinesMatching(String regex, boolean skipComments) {
        final java.nio.file.Path root = repoRootOrNull();
        if (root == null) {
            return -1;
        }
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        int hits = 0;
        for (String relative : new String[] { "src/main/java/com/miaokatze/gtit/common/items/pocket",
            "src/main/java/com/miaokatze/gtit/gui/pocket" }) {
            final java.nio.file.Path base = root.resolve(relative);
            if (!java.nio.file.Files.isDirectory(base)) {
                return -1;
            }
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(base)) {
                final java.util.Iterator<java.nio.file.Path> it = walk.iterator();
                while (it.hasNext()) {
                    final java.nio.file.Path file = it.next();
                    final String name = file.getFileName()
                        .toString();
                    if (!name.endsWith(".java")) {
                        continue;
                    }
                    for (String line : java.nio.file.Files
                        .readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                        final String trimmed = line.trim();
                        if (skipComments
                            && (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*"))) {
                            continue;
                        }
                        if (pattern.matcher(line)
                            .find()) {
                            hits++;
                        }
                    }
                }
            } catch (java.io.IOException ioFailure) {
                return -1;
            }
        }
        return hits;
    }

    /** 从当前工作目录逐级上溯找仓库根（认 {@code settings.gradle.kts} 或 {@code build.gradle.kts}）。 */
    private static java.nio.file.Path repoRootOrNull() {
        java.nio.file.Path dir = java.nio.file.Paths.get("")
            .toAbsolutePath();
        for (int up = 0; up < 8 && dir != null; up++) {
            if (java.nio.file.Files.exists(dir.resolve("settings.gradle.kts"))
                || java.nio.file.Files.exists(dir.resolve("build.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /** 撤边条的源码半边：pocket 源码里不得再出现「列标题边条」的任何形状（展示稿用的那几个名字）。 */
    private static void assertNoFluidColumnTitleStripInSource() {
        final int stripHits = countPocketSourceLinesMatching("TANK_HEAD|chead|HEAD_H|GRID / 2", true);
        if (stripHits < 0) {
            System.out.println("[NOTE] 找不到仓库根 ⇒ 「无流体列标题边条」的源码半边未验（★不是通过）");
            return;
        }
        SimpleAssert.eq(0, stripHits, "★R80②：撤下来的 18 条列标题边条不得在 Java 侧留形状（9px 条 / chead / TANK_HEAD）");
        final int tankLabelHits = countPocketSourceLinesMatching("legend\\.tank_of", true);
        SimpleAssert.that(tankLabelHits >= 2, "★组号的落点必须≥两处（交互格 tooltip + 流体槽本体 tooltip），撤边条不等于删信息：读到 " + tankLabelHits);
    }

    // ------------------------------------------------------------------ 绑定表

    private static void bindingRoundTripKeepsOrder() {
        final PocketCellBindings bindings = new PocketCellBindings();
        SimpleAssert.that(bind(bindings, CELL_B), "首次绑定应成功");
        SimpleAssert.that(bind(bindings, CELL_A), "第二枚绑定应成功");
        final NBTTagCompound root = new NBTTagCompound();
        bindings.writeTo(root);

        final PocketCellBindings back = PocketCellBindings.readFrom(root);
        SimpleAssert.eq(2, back.size(), "往返后条数不变");
        // 绑定序 = 轮转外层序，顺序绝不能被排序或换成 Set 的迭代序
        SimpleAssert.eq(Arrays.asList(CELL_B, CELL_A), back.cells(), "往返后必须保持绑定顺序");
    }

    private static void bindingDedupeAndCap() {
        final PocketCellBindings bindings = new PocketCellBindings();
        SimpleAssert.that(bind(bindings, CELL_A), "绑定 CELL_A 应成功");
        SimpleAssert.eq(Boolean.FALSE, bind(bindings, CELL_A), "重复绑定同一身份必须幂等失败");
        SimpleAssert.eq(1, bindings.size(), "去重后仍是一枚");
        SimpleAssert.eq(Boolean.FALSE, bind(bindings, ""), "空身份必须拒收");
        SimpleAssert.eq(Boolean.FALSE, bind(bindings, null), "null 身份必须拒收");
        SimpleAssert.that(bindings.contains(CELL_A), "contains 认得已绑身份");
        SimpleAssert.that(bindings.unbind(CELL_A), "解绑应成功");
        SimpleAssert.eq(Boolean.FALSE, bindings.unbind(CELL_A), "重复解绑必须无副作用");

        for (int i = 0; i < PocketConstants.MAX_BOUND_CELLS; i++) {
            SimpleAssert.that(bind(bindings, uuidOf(i)), "上限内第 " + i + " 枚应可绑定");
        }
        SimpleAssert.eq(PocketConstants.MAX_BOUND_CELLS, bindings.size(), "表应恰好装满");
        SimpleAssert.eq(Boolean.FALSE, bind(bindings, uuidOf(999)), "超上限必须拒收");
        SimpleAssert.eq(Boolean.FALSE, bindings.hasRoom(), "装满后不再有空位");
    }

    private static void bindingReadFoldsLegacyDuplicates() {
        final NBTTagList list = new NBTTagList();
        list.appendTag(bindingEntry(CELL_A));
        list.appendTag(bindingEntry(CELL_A));
        list.appendTag(bindingEntry(CELL_B));
        list.appendTag(new NBTTagCompound());
        final NBTTagCompound root = new NBTTagCompound();
        root.setTag(PocketConstants.BOUND_CELLS, list);

        final PocketCellBindings back = PocketCellBindings.readFrom(root);
        SimpleAssert.eq(Arrays.asList(CELL_A, CELL_B), back.cells(), "陈旧档里的重复/空条目必须在读档时折叠");
        // 上面手搓的条目只有 id，没有位置五键：必须整体回落为未定位哨兵（而不是 0，0 是合法坐标）
        SimpleAssert.eq(
            Boolean.FALSE,
            back.entry(CELL_A)
                .located(),
            "缺位置键的条目读回即未定位");
        SimpleAssert.eq(PocketConstants.UNLOCATED, back.entry(CELL_A).slotIndex, "未定位哨兵 = Integer.MIN_VALUE");
    }

    /** 带位置快照的条目逐字段往返（R39c：绑定条目必须含 dim/x/y/z/slotIndex）。 */
    private static void bindPositionRoundTrip() {
        final PocketCellBindings bindings = new PocketCellBindings();
        SimpleAssert.that(bindings.bind(CELL_A, 0, 128, 64, -200, 5, PocketConstants.MODE_DISK_UUID), "带位置快照的绑定应成功");
        SimpleAssert.that(
            bindings.bind(CELL_B, -1, 9_999, 7, -8_888, PocketConstants.UNLOCATED, PocketConstants.MODE_DISK_UUID),
            "第二枚带位置的绑定应成功");
        SimpleAssert.that(
            bindings.entry(CELL_A)
                .located(),
            "已定位条目 located() 为真");
        SimpleAssert.eq(
            Boolean.FALSE,
            bindings.entry(CELL_B)
                .located(),
            "槽号仍是哨兵 ⇒ 整体算未定位（五键要么齐、要么不算定位过）");

        final NBTTagCompound root = new NBTTagCompound();
        bindings.writeTo(root);
        final PocketCellBindings back = PocketCellBindings.readFrom(root);
        assertEntryEquals(
            new PocketCellBindings.Entry(CELL_A, PocketConstants.MODE_DISK_UUID, 0, 128, 64, -200, 5),
            back.entry(CELL_A),
            "带位置条目逐字段往返");
        assertEntryEquals(
            new PocketCellBindings.Entry(
                CELL_B,
                PocketConstants.MODE_DISK_UUID,
                -1,
                9_999,
                7,
                -8_888,
                PocketConstants.UNLOCATED),
            back.entry(CELL_B),
            "半哨兵条目逐字段往返");

        // 未定位条目（右下绑定格的正常态）也要能往返，且五键落档后仍是哨兵
        final PocketCellBindings bare = new PocketCellBindings();
        SimpleAssert.that(bind(bare, CELL_A), "未定位绑定应成功");
        final NBTTagCompound bareRoot = new NBTTagCompound();
        bare.writeTo(bareRoot);
        final PocketCellBindings bareBack = PocketCellBindings.readFrom(bareRoot);
        assertEntryEquals(bare.entry(CELL_A), bareBack.entry(CELL_A), "未定位条目逐字段往返");
        SimpleAssert.eq(
            Boolean.FALSE,
            bareBack.entry(CELL_A)
                .located(),
            "未定位条目往返后仍未定位");
    }

    /** 身份重复不追加第二条目，只覆盖位置快照（轮转外层序因此稳定）。 */
    private static void bindIdentityDedupOverwritesPosition() {
        final PocketCellBindings bindings = new PocketCellBindings();
        SimpleAssert.that(bindings.bind(CELL_A, 0, 1, 2, 3, 4, PocketConstants.MODE_DISK_UUID), "首绑成功");
        SimpleAssert.that(bindings.bind(CELL_B, 0, 9, 9, 9, 9, PocketConstants.MODE_DISK_UUID), "第二枚成功");
        SimpleAssert
            .eq(Boolean.FALSE, bindings.bind(CELL_A, 0, 70, 71, 72, 1, PocketConstants.MODE_DISK_UUID), "重复身份不追加条目");
        SimpleAssert.eq(2, bindings.size(), "表里仍是两枚");
        SimpleAssert.eq(Arrays.asList(CELL_A, CELL_B), bindings.cells(), "绑定序不受重绑影响（首绑位置保持）");
        final PocketCellBindings.Entry moved = bindings.entry(CELL_A);
        SimpleAssert.eq(70, moved.x, "重复绑定覆盖位置快照的 x");
        SimpleAssert.eq(1, moved.slotIndex, "重复绑定覆盖位置快照的槽号");

        // 回填通路：探针定位到之后由通道侧写回快照，同值再写回报"没变化"
        SimpleAssert.that(bindings.recordLocation(CELL_B, 0, 5, 6, 7, 2), "回填应报变化");
        SimpleAssert.eq(5, bindings.entry(CELL_B).x, "回填写进 x");
        SimpleAssert.eq(Boolean.FALSE, bindings.recordLocation(CELL_B, 0, 5, 6, 7, 2), "同值回填不算变化");
        SimpleAssert.eq(Boolean.FALSE, bindings.recordLocation(uuidOf(12345), 0, 1, 1, 1, 1), "未绑定身份不得回填");
    }

    /** mode 逐条读写（不再是表级互斥字段）+ 缺键回落。 */
    private static void bindingPerEntryModeAndMissingKeys() {
        final PocketCellBindings bindings = new PocketCellBindings();
        bindings.bind(CELL_A, PocketConstants.MODE_DISK_UUID);
        bindings.bind(CELL_B, 0, 1, 2, 3, 4, (byte) 7);
        final NBTTagCompound root = new NBTTagCompound();
        bindings.writeTo(root);
        final NBTTagList list = root.getTagList(PocketConstants.BOUND_CELLS, 10);
        SimpleAssert.eq(
            PocketConstants.MODE_DISK_UUID,
            list.getCompoundTagAt(0)
                .getByte(PocketConstants.ENTRY_MODE),
            "第一条 mode 逐条落档");
        SimpleAssert.eq(
            (byte) 7,
            list.getCompoundTagAt(1)
                .getByte(PocketConstants.ENTRY_MODE),
            "第二条 mode 独立落档（表级字段做不到这一点）");
        for (int i = 0; i < 2; i++) {
            final NBTTagCompound entry = list.getCompoundTagAt(i);
            for (String key : new String[] { PocketConstants.ENTRY_DIM, PocketConstants.ENTRY_X,
                PocketConstants.ENTRY_Y, PocketConstants.ENTRY_Z, PocketConstants.ENTRY_SLOT }) {
                SimpleAssert.that(entry.hasKey(key, 3), "位置五键必须逐条写出（缺键即内存/落档形状不一致），实缺 " + key);
            }
        }
        final PocketCellBindings back = PocketCellBindings.readFrom(root);
        SimpleAssert.eq((byte) 7, back.entry(CELL_B).mode, "mode 往返");
        SimpleAssert.eq(PocketConstants.MODE_DISK_UUID, back.entry(CELL_A).mode, "未定位条目的 mode 往返");

        // 无关键不得干扰绑定表读取
        final NBTTagCompound foreign = new NBTTagCompound();
        foreign.setTag(PocketConstants.BOUND_CELLS, listOf(bindingEntry(CELL_A), bindingEntry(CELL_B)));
        foreign.setTag(PocketConstants.ESSENCE, new NBTTagCompound());
        SimpleAssert.eq(
            2,
            PocketCellBindings.readFrom(foreign)
                .size(),
            "无关键不得干扰绑定表读取");
    }

    private static void assertEntryEquals(PocketCellBindings.Entry expected, PocketCellBindings.Entry actual,
        String label) {
        if (actual == null) {
            throw new AssertionError(label + "：读回为空");
        }
        SimpleAssert.eq(expected.id, actual.id, label + " 的 id");
        SimpleAssert.eq(expected.mode, actual.mode, label + " 的 mode");
        SimpleAssert.eq(expected.dim, actual.dim, label + " 的 dim");
        SimpleAssert.eq(expected.x, actual.x, label + " 的 x");
        SimpleAssert.eq(expected.y, actual.y, label + " 的 y");
        SimpleAssert.eq(expected.z, actual.z, label + " 的 z");
        SimpleAssert.eq(expected.slotIndex, actual.slotIndex, label + " 的 slotIndex");
        SimpleAssert.eq(expected.located(), actual.located(), label + " 的 located()");
    }

    // ------------------------------------------------------------------ 源质表

    /**
     * ★R84：每格上限 64 → <b>256</b>，且它是用户裁定的<b>可配值</b> ⇒ 本用例全程按
     * {@code PocketConstants.ESSENCE_CAP_PER_TAG} 符号断言，不再钉死字面量（钉死数字的下次抬头就会
     * 让人去删断言而不是改常量）。
     */
    private static void essenceCapsFollowPerTagCap() {
        final int cap = PocketConstants.ESSENCE_CAP_PER_TAG;
        final PocketEssenceStore store = new PocketEssenceStore();
        SimpleAssert.eq(40, store.add("aer", 40), "首次入账 40 点应全额");
        SimpleAssert.eq(cap - 40, store.add("aer", cap), "超过单格上限的部分被逐格上限挡住（原语回报实收量）");
        SimpleAssert.eq(cap, store.get("aer"), "每格上限 = ESSENCE_CAP_PER_TAG");
        SimpleAssert.eq(0, store.add("aer", 5), "已满格不再入账");
        SimpleAssert.that(store.isFull("aer"), "到单格上限即满格");
        SimpleAssert.eq(Boolean.FALSE, store.isFull("ignis"), "未入账的 tag 不算满");
        SimpleAssert.eq(0, store.add("terra", -1), "非正数请求不入账");
        SimpleAssert.eq(0, store.add("", 10), "空 tag 不入账");
        SimpleAssert.eq(10, store.add("ignis", 10), "新 tag 仍可入账：存储与显示格数解耦（★R78② 后是 72 格，旧 48 口径不得留在文案里）");
        SimpleAssert.eq(cap - 10, store.roomFor("ignis"), "剩余容量按上限差给出");
        SimpleAssert.eq(
            2,
            store.tags()
                .size(),
            "只登记有货的 tag");
        // ⚠ isFull() 只服务 GUI 置灰：表里只有一格到顶时它仍是 false，"本轮消耗与否"不得用它
        SimpleAssert.eq(Boolean.FALSE, store.isFull(), "aer 到顶但 ignis 未满 ⇒ 表级 isFull 为 false");
    }

    /**
     * 全有全无（R29）：一轮候选里任一 tag 放不下 ⇒ 整轮零入账。
     * <p>
     * 这条替代了旧的"截断入账"用例：旧口径（放多少算多少、然后照扣物品）= 静默销毁价值，
     * 两说不得并存，故不保留任何"截断后仍算成功"的断言。
     */
    private static void essenceAllOrNothingRejectsWholeRound() {
        final PocketEssenceStore store = new PocketEssenceStore();
        store.add("aer", 60);
        store.add("ignis", PocketConstants.ESSENCE_CAP_PER_TAG); // ★R84：按符号顶满，不写死 64

        final Map<String, Integer> round = new LinkedHashMap<>();
        round.put("aer", 10);
        round.put("ignis", 3);
        SimpleAssert.eq(Boolean.FALSE, store.canAcceptAll(round), "ignis 已满 ⇒ 整轮判放不下（哪怕 aer 还收得进 4 点）");
        final int beforeAer = store.get("aer");
        final int beforeIgnis = store.get("ignis");
        // 上层（蒸馏侧）判 false 后不得提交，因此这里断言"整轮零变化"
        if (store.canAcceptAll(round)) {
            store.putAll(round);
        }
        SimpleAssert.eq(beforeAer, store.get("aer"), "预检不过 ⇒ aer 零变化");
        SimpleAssert.eq(beforeIgnis, store.get("ignis"), "预检不过 ⇒ ignis 零变化（一格都没进）");

        // 换成放得下的一份：两段式必须整体入账
        final Map<String, Integer> fits = new LinkedHashMap<>();
        fits.put("aer", 4);
        fits.put("terra", 30);
        SimpleAssert.that(store.canAcceptAll(fits), "整份候选都放得下");
        SimpleAssert.eq(34, store.putAll(fits), "putAll 提交全部候选");
        SimpleAssert.eq(64, store.get("aer"), "aer 补满到 64");
        SimpleAssert.eq(30, store.get("terra"), "新 tag 建立");

        // 未预检直接提交时按逐格上限兜底（宁少不炸），但仍不得据此消耗物品。
        // ★R84：cap 抬到 256 后"aer 补到 64"不再是满格样本 ⇒ 越界那份改用 cap 造，兜底分支必须继续被真实走到。
        final Map<String, Integer> over = new LinkedHashMap<>();
        over.put("aer", PocketConstants.ESSENCE_CAP_PER_TAG);
        over.put("terra", 5);
        SimpleAssert.eq(
            PocketConstants.ESSENCE_CAP_PER_TAG - store.get("aer") + 5,
            store.putAll(over),
            "兜底只收放得下的部分（aer 收到顶 + terra 全收），且不炸档");
        SimpleAssert.eq(PocketConstants.ESSENCE_CAP_PER_TAG, store.get("aer"), "★兜底不会把超出单格上限的量硬塞进去（越界那份被丢弃在候选侧，不写进存储）");
        SimpleAssert.eq(35, store.get("terra"), "terra 累计 30+5（多次提交是叠加不是覆盖）");
        SimpleAssert.eq(0, store.putAll(null), "null 候选不入账");
        SimpleAssert.eq(0, store.putAll(new LinkedHashMap<>()), "空候选不入账");
        SimpleAssert.that(store.canAcceptAll(null), "null 候选天然放得下");
    }

    private static void essenceZeroIsNotWritten() {
        final PocketEssenceStore store = new PocketEssenceStore();
        store.add("aer", 10);
        store.add("terra", 7);
        store.add("ignis", 5);
        SimpleAssert.eq(10, store.extract("aer", 10), "整格取出应成功");
        SimpleAssert.eq(0, store.get("aer"), "取出后归零");
        SimpleAssert.eq(Boolean.FALSE, store.has("aer"), "0 点不算含有");
        SimpleAssert.eq(3, store.extract("terra", 3), "按请求量取出");
        SimpleAssert.eq(4, store.get("terra"), "扣减后余量正确");
        SimpleAssert.eq(5, store.extract("ignis", 99), "请求量超过存量时按存量给");
        SimpleAssert.eq(0, store.extract("aer", 1), "空格取出为 0");

        final NBTTagCompound root = new NBTTagCompound();
        store.writeTo(root);
        final NBTTagList aspects = aspectsOf(root);
        SimpleAssert.eq(1, aspects.tagCount(), "只有非 0 条目落档（0 值不落 NBT，防存量档膨胀）");
        final NBTTagCompound only = aspects.getCompoundTagAt(0);
        SimpleAssert.eq("terra", only.getString(PocketConstants.ASPECT_KEY), "条目 key 是 tag 字符串");
        SimpleAssert.eq((short) 4, only.getShort(PocketConstants.ASPECT_AMOUNT), "非零值必须落档");

        final PocketEssenceStore back = PocketEssenceStore.readFrom(root);
        SimpleAssert.eq(0, back.get("aer"), "读档后缺条目即 0");
        SimpleAssert.eq(4, back.get("terra"), "非零值往返一致");
        SimpleAssert.eq(
            1,
            back.tags()
                .size(),
            "读档不复活 0 值条目");
    }

    /** TC {@code AspectList} 形状：{@code ess → NBTTagList "Aspects" → [{key:String, amount:Short}]}。 */
    private static void essenceAspectsListRoundTrip() {
        final PocketEssenceStore store = new PocketEssenceStore();
        store.add("lux", 64);
        store.add("aer", 3);
        final NBTTagCompound root = new NBTTagCompound();
        store.writeTo(root);

        final NBTTagCompound ess = root.getCompoundTag(PocketConstants.ESSENCE);
        SimpleAssert.that(ess.hasKey(PocketConstants.ASPECTS, 9), "\"Aspects\" 必须是 NBTTagList（TAG_List=9）");
        SimpleAssert.eq(Boolean.FALSE, ess.hasKey("lux"), "旧的扁平 \"ess\"{tag:short} 形状必须已清除（不再是 TC 家族形状）");
        SimpleAssert.eq(Boolean.FALSE, ess.hasKey("aer"), "旧的扁平 \"ess\"{tag:short} 形状必须已清除");
        final NBTTagList aspects = aspectsOf(root);
        SimpleAssert.eq(2, aspects.tagCount(), "两条目");
        for (int i = 0; i < aspects.tagCount(); i++) {
            final NBTTagCompound entry = aspects.getCompoundTagAt(i);
            SimpleAssert.that(entry.hasKey(PocketConstants.ASPECT_KEY, 8), "key 必须是 TAG_String=8");
            SimpleAssert.that(entry.hasKey(PocketConstants.ASPECT_AMOUNT, 2), "amount 必须是 TAG_Short=2");
        }

        final PocketEssenceStore back = PocketEssenceStore.readFrom(root);
        SimpleAssert.eq(64, back.get("lux"), "按 tag 往返（存的是字符串，不依赖注册序）");
        SimpleAssert.eq(3, back.get("aer"), "第二条目往返");
        SimpleAssert.eq(Arrays.asList("lux", "aer"), new ArrayList<>(back.tags()), "入账序即列表序（无排序、无索引）");
        SimpleAssert.eq(67, back.totalPoints(), "总点数按在表条目求和");
        SimpleAssert.eq(
            64,
            store.snapshot()
                .get("lux"),
            "快照按 tag 取值（不可变副本）");

        // 读档钳制：外部/陈旧档写进 70 点不能直接吃下；0 值条目读档即丢；无 key 条目忽略
        final NBTTagCompound dirty = new NBTTagCompound();
        final NBTTagList dirtyList = new NBTTagList();
        // ★R84：上限抬到 256 后"70 点"不再是越界样本 ⇒ 改造 cap+6，钳制分支必须继续被真实走到
        dirtyList.appendTag(aspectEntry("lux", PocketConstants.ESSENCE_CAP_PER_TAG + 6));
        dirtyList.appendTag(aspectEntry("mortuus", 0));
        dirtyList.appendTag(new NBTTagCompound());
        final NBTTagCompound dirtyEss = new NBTTagCompound();
        dirtyEss.setTag(PocketConstants.ASPECTS, dirtyList);
        dirty.setTag(PocketConstants.ESSENCE, dirtyEss);
        final PocketEssenceStore clamped = PocketEssenceStore.readFrom(dirty);
        SimpleAssert.eq(PocketConstants.ESSENCE_CAP_PER_TAG, clamped.get("lux"), "读档时超过上限的条目按单格上限截断（★不是丢弃整条，也不是照收越界值）");
        SimpleAssert.eq(0, clamped.get("mortuus"), "0 值条目读档即丢");
        SimpleAssert.eq(
            1,
            clamped.tags()
                .size(),
            "无 key 的条目不得建格");

        // 二次落档必须等价：证明形状里没有隐式索引/排序，且读档钳制是幂等的
        final NBTTagCompound again = new NBTTagCompound();
        clamped.writeTo(again);
        final PocketEssenceStore twice = PocketEssenceStore.readFrom(again);
        SimpleAssert
            .eq(PocketConstants.ESSENCE_CAP_PER_TAG, twice.get("lux"), "钳制后再往返仍等于单格上限（★幂等：第二次读档不得继续往下削，也不得把越界值放回来）");
        SimpleAssert.eq(
            1,
            twice.tags()
                .size(),
            "无 key / 0 值条目不会被复活");
        final NBTTagCompound third = new NBTTagCompound();
        twice.writeTo(third);
        SimpleAssert.eq(again.toString(), third.toString(), "二次与三次落档必须逐字符一致");
    }

    /** {@code ess → "Aspects"} 列表（TC AspectList 的读写落点）。 */
    private static NBTTagList aspectsOf(NBTTagCompound root) {
        return root.getCompoundTag(PocketConstants.ESSENCE)
            .getTagList(PocketConstants.ASPECTS, 10);
    }

    private static NBTTagCompound aspectEntry(String tag, int amount) {
        final NBTTagCompound entry = new NBTTagCompound();
        entry.setString(PocketConstants.ASPECT_KEY, tag);
        entry.setShort(PocketConstants.ASPECT_AMOUNT, (short) amount);
        return entry;
    }

    // ------------------------------------------------------------------ 冷却

    private static void cooldownUsesWallClock() {
        SimpleAssert.eq(0L, PocketChannelState.remainingCooldownSeconds(0L, BASE_MS, 10), "从未触发即无冷却");
        SimpleAssert.eq(0L, PocketChannelState.remainingCooldownSeconds(BASE_MS, BASE_MS, 0), "冷却秒数非正即无冷却");
        SimpleAssert.eq(10L, PocketChannelState.remainingCooldownSeconds(BASE_MS, BASE_MS, 10), "刚触发即满冷却");
        // 口径照 NekoTradeHistory:61：(now - last) / 1000 后与冷却秒数相减
        SimpleAssert
            .eq(6L, PocketChannelState.remainingCooldownSeconds(BASE_MS, BASE_MS + 4_500L, 10), "过 4.5 秒余 6 秒（整除向下取整）");
        SimpleAssert.eq(1L, PocketChannelState.remainingCooldownSeconds(BASE_MS, BASE_MS + 9_000L, 10), "过 9 秒余 1 秒");
        SimpleAssert.eq(0L, PocketChannelState.remainingCooldownSeconds(BASE_MS, BASE_MS + 10_000L, 10), "恰好到点即无冷却");
        SimpleAssert.eq(0L, PocketChannelState.remainingCooldownSeconds(BASE_MS, BASE_MS + 60_000L, 10), "过期不返回负数");

        final PocketChannelState state = new PocketChannelState();
        state.markBurst(BASE_MS);
        SimpleAssert.that(state.showBurstAnimation(BASE_MS + 4_999L), "5 秒动画窗口内仍显示");
        SimpleAssert.eq(Boolean.FALSE, state.showBurstAnimation(BASE_MS + 5_000L), "满 5 秒即停止显示");
    }

    private static void cooldownChecksBothDimensions() {
        // 设备维（口袋 NBT，随物品共享给任何持有者）与玩家维（内存）取严
        SimpleAssert.eq(
            1L,
            PocketChannelState.burstCooldownRemaining(BASE_MS, 0L, BASE_MS + 9_000L, 10),
            "只有设备维刚用过 ⇒ 仍受 1 秒约束");
        SimpleAssert.eq(
            9L,
            PocketChannelState.burstCooldownRemaining(0L, BASE_MS + 8_000L, BASE_MS + 9_000L, 10),
            "只有玩家维刚用过 ⇒ 受 9 秒约束");
        SimpleAssert.eq(
            9L,
            PocketChannelState.burstCooldownRemaining(BASE_MS, BASE_MS + 8_000L, BASE_MS + 9_000L, 10),
            "双维都用过 ⇒ 取严 max(1,9)");
        SimpleAssert.eq(
            0L,
            PocketChannelState.burstCooldownRemaining(BASE_MS - 10_000L, BASE_MS - 10_000L, BASE_MS + 1_000L, 10),
            "双维都过期 ⇒ 放行");
    }

    private static void burstCooldownPersistsToDeviceNbt() {
        PocketChannelManager.INSTANCE.reset();
        final UUID player = UUID.fromString(CELL_A);
        final StubOps ops = new StubOps();
        ops.fillSources("i:1:0:", 12);
        final PocketCellBindings bindings = bindingsOf(CELL_A);
        final NBTTagCompound pocketTag = new NBTTagCompound();

        SimpleAssert.that(PocketChannelManager.INSTANCE.requestBurst(player, bindings, pocketTag, ops), "首次瞬时通道应受理");
        SimpleAssert.eq(BASE_MS, PocketChannelState.readDeviceLastBurstAtMs(pocketTag), "冷却起点必须写入口袋 NBT（设备维）");
        SimpleAssert.eq(
            BASE_MS,
            PocketChannelManager.INSTANCE.peek(player)
                .lastBurstAtMs(),
            "玩家维内存条目同步刷新");
        SimpleAssert.eq(1, ops.announcements.size(), "瞬时通道一次调用内只发一次网络通知");

        ops.tick += 20L;
        ops.nowMs = BASE_MS + 5_000L;
        SimpleAssert.eq(
            Boolean.FALSE,
            PocketChannelManager.INSTANCE.requestBurst(player, bindings, pocketTag, ops),
            "冷却期内二次瞬时通道必须被挡住");
        SimpleAssert.eq(BASE_MS, PocketChannelState.readDeviceLastBurstAtMs(pocketTag), "被挡时不得刷新冷却起点");

        ops.tick += 20L;
        ops.nowMs = BASE_MS + 10_000L;
        SimpleAssert.that(PocketChannelManager.INSTANCE.requestBurst(player, bindings, pocketTag, ops), "满 10 秒后应可再用");
        SimpleAssert.eq(BASE_MS + 10_000L, PocketChannelState.readDeviceLastBurstAtMs(pocketTag), "再次触发要写回新的冷却起点");
        PocketChannelManager.INSTANCE.reset();
    }

    private static void sameTickLatchBlocksSecondRequest() {
        final PocketChannelState state = new PocketChannelState();
        SimpleAssert.that(state.tryLatchTick(100L), "同 tick 首个请求放行");
        SimpleAssert.eq(Boolean.FALSE, state.tryLatchTick(100L), "同 tick 第二个请求必须拒");
        SimpleAssert.that(state.tryLatchTick(101L), "下一 tick 重新放行");

        SimpleAssert.that(state.enterBatch(), "进入传输段应成功");
        SimpleAssert.eq(Boolean.FALSE, state.enterBatch(), "段内再入必须拒（防 injectItems 经宿主回调链自重入）");
        state.leaveBatch();
        SimpleAssert.eq(Boolean.FALSE, state.inBatch(), "finally 释放后不在段内");
        SimpleAssert.that(state.enterBatch(), "释放后可再进");
        state.leaveBatch();
    }

    // ------------------------------------------------------------------ 轮转

    private static void rotationAdvancesByTypeId() {
        final PocketRotationCursor cursor = new PocketRotationCursor();
        SimpleAssert.eq("item", cursor.next(CELL_A, CHANNELS), "首轮从第 0 个通道开始");
        SimpleAssert.eq("fluid", cursor.next(CELL_A, CHANNELS), "次轮推进到第 1 个 typeId");
        SimpleAssert.eq("essentia", cursor.next(CELL_A, CHANNELS), "第三轮到最后一个 typeId");
        SimpleAssert.eq("item", cursor.next(CELL_A, CHANNELS), "末尾回绕到 0");
        SimpleAssert.eq("item", cursor.lastServedOf(CELL_A), "游标记录的是上次实际服务的 typeId");
        SimpleAssert.eq(null, cursor.next(CELL_A, Collections.<String>emptyList()), "候选为空返回 null（据此报 NO_CHANNEL）");
        cursor.forget(CELL_A);
        SimpleAssert.eq("item", cursor.next(CELL_A, CHANNELS), "forget 后从头开始");
        SimpleAssert.eq(1, cursor.trackedCells(), "只跟踪一枚元件");
        // 上次服务过的通道不再在候选里（mod 掉线）⇒ 从 0 重来，不做隐式对齐
        SimpleAssert.eq("fluid", cursor.next(CELL_A, CHANNELS), "先推进一轮");
        SimpleAssert.eq("item", cursor.next(CELL_A, Arrays.asList("item", "essentia")), "候选收缩后找不到上次 typeId 即从 0 开始");
    }

    private static void rotationKeyedByUuidString() {
        final PocketRotationCursor cursor = new PocketRotationCursor();
        // 两枚同类型（候选列表完全相同）但 diskuuid 不同的桩件：
        // 若序号按实例或全局共享（v1.8.26 修过的同款 bug），CELL_B 首轮就会被推到 fluid。
        SimpleAssert.eq("item", cursor.next(CELL_A, CHANNELS), "CELL_A 首轮 item");
        SimpleAssert.eq("item", cursor.next(CELL_B, CHANNELS), "CELL_B 必须独立从 item 开始，不复用 A 的序号");
        SimpleAssert.eq("fluid", cursor.next(CELL_A, CHANNELS), "CELL_A 推进到 fluid");
        SimpleAssert.eq("fluid", cursor.next(CELL_B, CHANNELS), "CELL_B 同步推进到自己的 fluid");
        SimpleAssert.eq(2, cursor.trackedCells(), "两枚元件各占一个游标条目");

        cursor.forget(CELL_A);
        SimpleAssert.eq(1, cursor.trackedCells(), "按 uuid 字符串逐个清理");
        SimpleAssert.eq("essentia", cursor.next(CELL_B, CHANNELS), "清理 A 不影响 B 的推进");
        SimpleAssert.eq("item", cursor.next(CELL_A, CHANNELS), "A 重新开始且不干扰 B");
        SimpleAssert.eq("item", cursor.next(CELL_B, CHANNELS), "B 也正好回绕到 item");
    }

    /**
     * ★R87-b 改钉（旧钉"轮转不因换实例而重置"已被全通道轮转覆盖）：注入向<b>不再读也不再推进</b>
     * 轮转游标——每批对该元件的全部可用通道各服务一轮，"轮转选一"没有意义；游标只剩补满相在推进
     * （见 {@code runRefillBatch} 的 typeId 记账）。注入批前后 {@code lastServedOf} 必须逐字不变，
     * 且批内服务的通道序 = {@code channelIdsOf} 的稳定序（不被任何游标状态影响）。
     */
    private static void rotationSurvivesHandlerRecreation() {
        final StubOps ops = new StubOps();
        final PocketRotationCursor cursor = new PocketRotationCursor();
        // 先用补满相把游标推到一个非零位置：候选序 item→fluid→essentia，推两次 ⇒ 停在 fluid
        final PocketCellBindings bindings = bindingsOf(CELL_A);
        final PocketFilterConfig filters = new PocketFilterConfig();
        filters.add(0, item(0, 2621, 0, ""));
        ops.extractable.put(
            filters.at(Kind.ITEM, 0)
                .key(),
            1);
        PocketChannelRunner.runRefillBatch(bindings, cursor, filters, ops, 1, 64);
        PocketChannelRunner.runRefillBatch(bindings, cursor, filters, ops, 1, 64);
        SimpleAssert.eq("fluid", cursor.lastServedOf(CELL_A), "前置：补满相确实把游标推进到了 fluid");
        // 注入批跑完：游标一字不动，服务序也不受游标影响（不再从 fluid 的下一个开始）
        ops.fillSources(4);
        ops.servedChannels.clear();
        ops.injectCalls = 0;
        final PocketChannelRunner.Report report = PocketChannelRunner
            .runInjectBatch(bindings, ops, Integer.MAX_VALUE, null);
        SimpleAssert.eq("fluid", cursor.lastServedOf(CELL_A), "★注入向不推进游标（R87-b：全通道轮转后游标只剩补满相在写）");
        SimpleAssert.eq(3, report.pairsServed, "★三通道各自服务一轮（服务序 = channelIdsOf 的稳定序）");
        SimpleAssert.eq(Arrays.asList("item", "fluid", "essentia"), ops.servedChannels, "不被游标位置影响（旧形状会从 essentia 开始）");
    }

    // ------------------------------------------------------------------ 批次与回执

    /**
     * ★R87 改钉（旧钉"首个非 OK 即停批"已被饿死收窄覆盖）：PARTIAL 的 remainder 纪律不变——
     * 余量留在原槽、<b>本批不重试同一槽</b>；但 FULL 记失败后<b>跳过该来源继续</b>，
     * 不再截断后面的来源槽（队首一格满饿死整批的形状已废除）。
     */
    private static void remainderStaysInSourceSlot() {
        final StubOps ops = new StubOps();
        ops.capacity.put(CELL_A + "#item", 5);
        ops.fillSources(10, 10, 10);

        final PocketChannelRunner.Report first = PocketChannelRunner.runInjectBatch(bindingsOf(CELL_A), ops, 1, null);
        SimpleAssert.eq(5, first.transferred, "容量只剩 5 点时一批最多穿 5 点");
        SimpleAssert.eq(3, ops.injectCalls, "★PARTIAL/FULL 都不再停批：三个来源槽各被叫一次（旧形状只有 1 次）");
        SimpleAssert.eq(5, ops.sources.get(0).count, "余量留在原槽（不落地上、不进队列）");
        SimpleAssert.eq(10, ops.sources.get(1).count, "第 2 槽被继续尝试（不再被队首的 PARTIAL 截断）但元件已满 ⇒ 原样保留");
        SimpleAssert.eq(10, ops.sources.get(2).count, "第 3 槽同上");
        SimpleAssert.eq(2, first.full, "★元件满记满计数（两次），但不停批");
        SimpleAssert.eq(PocketReceipt.FULL, first.lastReceipt, "批内最后一次回执是元件满");

        // 下一轮：容量已空 ⇒ 三槽全满
        ops.injectCalls = 0;
        final PocketChannelRunner.Report second = PocketChannelRunner.runInjectBatch(bindingsOf(CELL_A), ops, 1, null);
        SimpleAssert.eq(0, second.transferred, "元件已满时不再穿任何东西");
        SimpleAssert.eq(PocketReceipt.FULL, second.lastReceipt, "元件满必须是独立回执码");
        SimpleAssert.eq(3, ops.injectCalls, "★满照样跳过该来源继续（3 槽 3 次），不再一次就 break");
        SimpleAssert.eq(3, second.full, "满的计数与拒收分开");
        SimpleAssert.eq(0, second.filterRejected, "满不得算成拒收");
    }

    private static void filterRejectAndFullAreDistinct() {
        final StubOps ops = new StubOps();
        ops.capacity.put(CELL_A + "#item", 100);
        ops.rejected.add("i:1:0:");
        ops.fillSources(8);

        final PocketChannelRunner.Report report = PocketChannelRunner.runInjectBatch(bindingsOf(CELL_A), ops, 1, null);
        SimpleAssert.eq(PocketReceipt.FILTER_REJECTED, report.lastReceipt, "分区 WHITELIST 未列出的物品要报「拒收」");
        SimpleAssert.eq(1, report.filterRejected, "拒收计数独立于满");
        SimpleAssert.eq(0, report.full, "拒收不得算成元件满");
        SimpleAssert.eq(0, report.transferred, "被拒收的东西一件都没进");
        SimpleAssert.eq(8, ops.sources.get(0).count, "拒收时余量原样留在源槽（否则玩家体验为丢件）");
        SimpleAssert.that(report.deltas.isEmpty(), "拒收不发网络通知");
        SimpleAssert.eq(0, ops.announcements.size(), "没有 delta 就完全不 post，避免空批白刷全网");

        // classify 的优先级：失联 > 无请求量/全量入仓 > 无写权限 > 分区拒收 > 满 > 部分
        SimpleAssert.eq(PocketReceipt.LOST, PocketReceipt.classify(false, true, true, 8, 8), "解析不到元件优先报失联");
        SimpleAssert.eq(PocketReceipt.NO_ACCESS, PocketReceipt.classify(true, false, false, 8, 8), "无写权限不得被误报成分区问题");
        SimpleAssert.eq(PocketReceipt.FULL, PocketReceipt.classify(true, true, true, 8, 8), "可接受但原样退回即元件满");
        SimpleAssert.eq(PocketReceipt.PARTIAL, PocketReceipt.classify(true, true, true, 8, 3), "退回量小于请求量即部分写入");
        SimpleAssert.eq(PocketReceipt.OK, PocketReceipt.classify(true, true, true, 8, 0), "无余量即全部成功");
        SimpleAssert.eq(PocketReceipt.OK, PocketReceipt.classify(true, true, false, 8, 0), "请求量为正但零余量按成功");
        SimpleAssert.that(PocketReceipt.FILTER_REJECTED.stopsBatch(), "拒收要停批顺延下一轮");
        SimpleAssert.that(!PocketReceipt.OK.stopsBatch(), "全部成功不停批");
    }

    private static void oneMergedPostPerBatch() {
        final StubOps ops = new StubOps();
        // 三格同一内容的物品 + 一格另一种内容，全走同一 (元件,通道) 对
        ops.fillSources(16, 16, 16, 5);
        ops.sources.set(1, new PocketChannelOps.SourceSlot(1, "i:2:0:", 16));
        ops.sources.set(3, new PocketChannelOps.SourceSlot(3, "i:2:0:", 5));

        final PocketChannelRunner.Report report = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A, CELL_B), ops, Integer.MAX_VALUE, null);
        SimpleAssert.eq(53, report.transferred, "瞬时通道一次穿完全部");
        // ★R84：服务面只吃绑定序前 ALLOWED_BOUND_CELLS(=1) 枚 ⇒ 第二枚即使还在表里也不再被服务。
        // 旧断言"两枚各服务一对"钉的是已被推翻的口径；本用例<b>刻意</b>让 CELL_B 继续留在表里，
        // 正是为了钉住"在表 ≠ 在跑"（数据层不收缩与入口面拒收是同一枚硬币的两面）。
        // ★R87-b：单枚元件的三条通道各服务一轮（全通道轮转），fluid/essentia 对物品来源按 typeId 门
        // "无事可做"跳过（桩件已镜像真实实现的成对早退）——所以 transferred 不变、pairsServed 变 3。
        SimpleAssert.eq(3, report.pairsServed, "★一对元件 × 三通道 = 3 对（R87-b 全通道轮转）");
        SimpleAssert.eq(1, ops.announcements.size(), "一次调用内只发一次网络通知（delta 合并）");
        final List<PocketChannelOps.Delta> posted = ops.announcements.get(0);
        SimpleAssert.eq(2, posted.size(), "同 (元件,通道,内容) 的多笔 delta 必须合并成一条");
        long total = 0L;
        for (PocketChannelOps.Delta delta : posted) {
            total += delta.amount;
            SimpleAssert.that(delta.amount > 0L, "注入方向的 delta 必须为正（抽取方向为负）");
            SimpleAssert.eq(CELL_A, delta.diskuuid, "第二枚元件本轮零入仓，不产生 delta");
        }
        SimpleAssert.eq(53L, total, "合并后的带符号量总和等于实际穿入量");
        SimpleAssert.eq("item", posted.get(0).typeId, "首轮通道 = item（轮转按 diskuuid 独立）");
    }

    /**
     * 短效通道的节拍 = <b>相对 tick 倒计时</b>（R62 修正 R60 后的定稿口径）。
     * <p>
     * 本用例在 D 批被改写过一次：旧写法驱动的是"绝对到期时刻"（把 {@code ops.tick} 一次 +20
     * 然后调用一次 {@code tickShortChannel}），而 {@code nextDueTick = nowTick + 20} 与
     * 跨维不连续的 {@code player.ticksExisted} 绑在一起正是 R59e 点名的静默停摆根因。
     * 现在的驱动方式与真实宿主一致：<b>每 tick 调一次</b> {@code tickShortChannel}
     * （vanilla 的 {@code InventoryPlayer#decrementAnimations} 就是这个节奏）。
     */
    private static void shortChannelCadence() {
        PocketChannelManager.INSTANCE.reset();
        final UUID player = UUID.fromString(CELL_A);
        final StubOps ops = new StubOps();
        ops.fillSources(2, 2);
        final PocketCellBindings bindings = bindingsOf(CELL_A);
        final NBTTagCompound pocketTag = new NBTTagCompound();

        SimpleAssert.that(
            PocketChannelManager.INSTANCE
                .openChannel(player, PocketChannelState.Mode.SHORT, bindings, pocketTag, ops, 1),
            "短效通道应受理");
        final PocketChannelState state = PocketChannelManager.INSTANCE.peek(player);
        SimpleAssert.eq(PocketConstants.SHORT_CHANNEL_BATCHES, state.remainingBatches(), "短效通道共 30 批（30 秒）");
        SimpleAssert.eq(PocketConstants.CHANNEL_TICK_PERIOD, state.ticksUntilDue(), "激活时装填整拍倒计时（不在激活那一拍跑第一批）");
        SimpleAssert.eq(0L, PocketChannelState.readDeviceLastBurstAtMs(pocketTag), "短效通道不写瞬时冷却字段（两码事）");
        SimpleAssert.eq(PocketChannelState.Mode.SHORT, state.mode(), "模式已登记");

        SimpleAssert.eq(
            Boolean.FALSE,
            PocketChannelManager.INSTANCE.tickShortChannel(player, bindings, ops, 1),
            "激活的同一 tick 不跑第一批（倒计时刚装填为整拍）");
        // 第 2…19 次驱动都不到拍（倒计时 19→1），第 20 次才到期
        for (int tick = 1; tick < PocketConstants.CHANNEL_TICK_PERIOD - 1; tick++) {
            SimpleAssert.eq(
                Boolean.FALSE,
                PocketChannelManager.INSTANCE.tickShortChannel(player, bindings, ops, 1),
                "第 " + (tick + 1) + " 次驱动未到拍（倒计时尚未归零）");
        }
        ops.fillSources(2, 2);
        SimpleAssert.that(
            PocketChannelManager.INSTANCE.tickShortChannel(player, bindings, ops, 1),
            "满 " + PocketConstants.CHANNEL_TICK_PERIOD + " 次驱动跑第一批");
        SimpleAssert.eq(1, ops.announcements.size(), "短效通道每拍一次 post");
        SimpleAssert
            .eq(PocketConstants.CHANNEL_TICK_PERIOD, state.ticksUntilDue(), "跑完一批重新装填整拍（finishBatch 不再引用 nowTick）");

        int batches = 1;
        while (batches < PocketConstants.SHORT_CHANNEL_BATCHES) {
            // 每拍重新装满源槽：等价于玩家往背包里补货，用来验证"每秒一次 post"的节拍而非容量
            for (int tick = 1; tick < PocketConstants.CHANNEL_TICK_PERIOD; tick++) {
                // 每拍整批要 20 次驱动：这里喂满 19 次，第 20 次由下面的调用完成
                PocketChannelManager.INSTANCE.tickShortChannel(player, bindings, ops, 1);
            }
            ops.fillSources(2, 2);
            if (PocketChannelManager.INSTANCE.tickShortChannel(player, bindings, ops, 1)) {
                batches++;
            }
        }
        SimpleAssert.eq(PocketConstants.SHORT_CHANNEL_BATCHES, batches, "共跑满 30 批");
        SimpleAssert.eq(PocketConstants.SHORT_CHANNEL_BATCHES, ops.announcements.size(), "短效通道每一拍各发一次网络通知（每秒一次 post）");
        SimpleAssert.eq(
            Boolean.FALSE,
            PocketChannelManager.INSTANCE.tickShortChannel(player, bindings, ops, 1),
            "30 批用尽后不再传输（短效通道故意不持久化，重载即止）");
        SimpleAssert.eq(0, PocketChannelManager.INSTANCE.trackedPlayers(), "停用的条目自然回收，不留内存");
        PocketChannelManager.INSTANCE.reset();
    }

    /**
     * ★R62 的正向断言：<b>tick 基准回退不影响推进</b>。
     * <p>
     * 1.7.10 跨维会重建 {@code EntityPlayerMP}，{@code ticksExisted} 因此不连续（R59e）。
     * 绝对到期在这种基准上会"永远等不到"；相对倒计时只认"被驱动了几次"，所以把
     * {@code ops.currentTick()} 一路往回拨，通道仍必须每 20 次驱动跑一批。
     */
    private static void tickBaseRewindKeepsCadence() {
        PocketChannelManager.INSTANCE.reset();
        final UUID player = UUID.fromString(CELL_A);
        final StubOps ops = new StubOps();
        final PocketCellBindings bindings = bindingsOf(CELL_A);
        ops.tick = 10_000L;
        SimpleAssert.that(
            PocketChannelManager.INSTANCE
                .openChannel(player, PocketChannelState.Mode.SHORT, bindings, new NBTTagCompound(), ops, 1),
            "短效通道应受理");

        int batches = 0;
        for (int i = 0; i < PocketConstants.CHANNEL_TICK_PERIOD * 3; i++) {
            // 每 7 次驱动就把 tick 基准往回拨一大截（模拟跨维重建后的计数归零/回退）
            if (i % 7 == 0) {
                ops.tick -= 500L;
            }
            ops.fillSources(2);
            if (PocketChannelManager.INSTANCE.tickShortChannel(player, bindings, ops, 1)) {
                batches++;
            }
        }
        SimpleAssert.eq(3, batches, "tick 基准一路回退仍按 20 次驱动一批推进（3 批）");
        SimpleAssert.that(ops.tick < 10_000L, "本用例确实把 tick 拨回了（否则是空转）");
        SimpleAssert.eq(
            PocketConstants.SHORT_CHANNEL_BATCHES - 3,
            PocketChannelManager.INSTANCE.peek(player)
                .remainingBatches(),
            "批数只随实际跑掉的批次递减");
        PocketChannelManager.INSTANCE.reset();
    }

    /**
     * ★R16/R62 的分工：<b>冷却走墙钟，节拍走倒计时</b>。
     * <p>
     * 这里刻意只用 {@code ops.tick} 大步前进（等价于"跑了很久"）而墙钟不动 ⇒ 冷却必须<b>纹丝不动</b>；
     * 反过来墙钟推进而 tick 不动 ⇒ 冷却照减。两个方向一起断言，才能证明冷却没有被偷偷挂到 tick 上。
     */
    private static void burstCooldownUsesWallClockOnly() {
        PocketChannelManager.INSTANCE.reset();
        final UUID player = UUID.fromString(CELL_A);
        final StubOps ops = new StubOps();
        ops.fillSources("i:1:0:", 4);
        final PocketCellBindings bindings = bindingsOf(CELL_A);
        final NBTTagCompound pocketTag = new NBTTagCompound();

        SimpleAssert.that(PocketChannelManager.INSTANCE.requestBurst(player, bindings, pocketTag, ops), "首次瞬时通道应受理");
        SimpleAssert.eq(
            (long) PocketConstants.BURST_COOLDOWN_SECONDS,
            PocketChannelManager.burstRemaining(PocketChannelManager.INSTANCE.peek(player), pocketTag, BASE_MS + 1L),
            "刚触发即满冷却");

        final long tickBefore = ops.tick;
        ops.tick += 20L * 600L; // 十分钟的游戏内 tick
        SimpleAssert.eq(
            (long) PocketConstants.BURST_COOLDOWN_SECONDS - 1L,
            PocketChannelManager
                .burstRemaining(PocketChannelManager.INSTANCE.peek(player), pocketTag, BASE_MS + 1_000L),
            "tick 大步前进而墙钟只走 1 秒 ⇒ 冷却几乎不减（证明冷却不吃 tick）");
        SimpleAssert.that(ops.tick > tickBefore, "本用例确实推进了 tick 基准");

        SimpleAssert.eq(
            5L,
            PocketChannelManager
                .burstRemaining(PocketChannelManager.INSTANCE.peek(player), pocketTag, BASE_MS + 5_000L),
            "墙钟过 5 秒 ⇒ 余 5 秒");
        SimpleAssert.eq(
            1L,
            PocketChannelManager
                .burstRemaining(PocketChannelManager.INSTANCE.peek(player), pocketTag, BASE_MS + 9_999L),
            "墙钟过 9.999 秒 ⇒ 整除向下取整仍余 1 秒（口径照 NekoTradeHistory）");
        SimpleAssert.eq(
            0L,
            PocketChannelManager
                .burstRemaining(PocketChannelManager.INSTANCE.peek(player), pocketTag, BASE_MS + 10_000L),
            "满 10 秒 ⇒ 放行");
        ops.nowMs = BASE_MS + 10_000L;
        ops.fillSources("i:1:0:", 4);
        SimpleAssert
            .that(PocketChannelManager.INSTANCE.requestBurst(player, bindings, pocketTag, ops), "冷却到点后再次瞬时通道应受理");
        SimpleAssert.eq(BASE_MS + 10_000L, PocketChannelState.readDeviceLastBurstAtMs(pocketTag), "设备维冷却起点随新一次触发刷新");
        PocketChannelManager.INSTANCE.reset();
    }

    // ------------------------------------------------------------------ ghost 配置

    private static void filterKeysCarryNoChannelIndex() {
        final PocketFilterConfig config = new PocketFilterConfig();
        SimpleAssert.that(config.add(7, item(7, 2621, 7, "AAA")), "物品声明可加入");
        SimpleAssert.eq(Boolean.FALSE, config.add(7, item(7, 2621, 7, "AAA")), "同槽二次声明是覆盖，不报新增");
        SimpleAssert.that(config.add(0, fluid(0, "water")), "流体声明可加入（索引 0 合法）");
        SimpleAssert.that(config.add(47, essence(47, "essentia", "aer")), "源质声明可加入");
        SimpleAssert.eq(3, config.size(), "三条声明");

        final NBTTagCompound root = new NBTTagCompound();
        config.writeTo(root);
        assertNoChannelIndexCarried(root);

        final PocketFilterConfig back = PocketFilterConfig.readFrom(root);
        // 载荷键式样不变：三类键里都没有任何索引
        SimpleAssert.eq(
            Arrays.asList("i:2621:7:AAA", "f:water", "e:essentia:aer"),
            keysOf(back),
            "载荷键式样与往返必须逐字稳定（声明序 = 插入序，跨区域不重排）");

        // 通道注册序变化（整合包 mod 增减）后落档内容必须一字不差：证明载荷键里确实没存通道索引
        final NBTTagCompound again = new NBTTagCompound();
        back.writeTo(again);
        SimpleAssert.eq(root.toString(), again.toString(), "二次往返必须完全一致（任何通道索引参与落档都会在这里暴露）");

        SimpleAssert
            .eq("e:essentia:aer", PocketFilterConfig.essenceKey("essentia", "aer"), "源质键 = typeId 字符串 + aspect tag");
        SimpleAssert.eq("f:lava", PocketFilterConfig.fluidKey("lava"), "流体键 = fluidName");
        SimpleAssert.eq("i:2621:7:", PocketFilterConfig.itemKey(2621, 7, ""), "无 nbt 的物品键尾部仍是空串");
        final PocketFilterConfig.Filter parsed = PocketFilterConfig.parseKey("i:2621:7:AAA");
        SimpleAssert.that(parsed instanceof PocketFilterConfig.ItemFilter, "物品键可反解");
        SimpleAssert.eq("i:2621:7:AAA", parsed.key(), "反解后键不变");
        SimpleAssert.eq(PocketConstants.FILTER_SLOT_UNSET, parsed.slotIndex(), "反解出的载荷不带槽位（未设置哨兵）");
        SimpleAssert.eq(Boolean.FALSE, config.add(1, parsed), "未挂槽的载荷不得入表");
        SimpleAssert.that(config.removeAt(Kind.FLUID, 0), "按（区域, 槽）解绑");
        SimpleAssert.eq(2, config.size(), "解绑后剩两条");
        SimpleAssert.eq(Boolean.FALSE, config.contains("f:water"), "解绑后不再含该载荷键");
        SimpleAssert.eq(Boolean.FALSE, config.removeAt(Kind.FLUID, 0), "重复解绑必须无副作用");
    }

    /** 槽索引逐条落档、往返后逐字段相等，且"索引 0"与"未设置"可区分。 */
    private static void filterSlotIndexRoundTrip() {
        final PocketFilterConfig config = new PocketFilterConfig();
        SimpleAssert.that(config.add(0, item(0, 2621, 7, "")), "中栏第 0 格可以声明");
        SimpleAssert.that(
            config.add(
                PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1,
                item(PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1, 1, 0, "")),
            "中栏最后一格（149）可以声明");
        SimpleAssert.that(config.add(0, fluid(0, "lava")), "流体槽第 0 格可声明：与中栏第 0 格同索引但跨区域，互不覆盖");
        SimpleAssert.that(
            config.add(
                PocketConstants.GHOST_FLUID_SLOT_LIMIT - 1,
                fluid(PocketConstants.GHOST_FLUID_SLOT_LIMIT - 1, "water")),
            "流体槽最后一格（上界前一格）可声明（R75① 扩列、R78② 扩组）");
        SimpleAssert.that(
            config.add(
                PocketConstants.GHOST_ESSENCE_SLOT_LIMIT - 1,
                essence(PocketConstants.GHOST_ESSENCE_SLOT_LIMIT - 1, "essentia", "ignis")),
            "★源质上界前一格可声明（R78② 后是第 71 格；上界取常量而不是抄数字）");
        SimpleAssert.eq(
            Boolean.FALSE,
            config.add(PocketConstants.GHOST_ITEM_SLOT_LIMIT, item(PocketConstants.GHOST_ITEM_SLOT_LIMIT, 1, 0, "")),
            "★越界槽索引（= 上界常量本身，R80① 后是 135）必须拒收：上界随轮次改，越界仍越界");
        SimpleAssert.eq(
            Boolean.FALSE,
            config.add(
                PocketConstants.GHOST_ESSENCE_SLOT_LIMIT,
                essence(PocketConstants.GHOST_ESSENCE_SLOT_LIMIT, "essentia", "aer")),
            "★源质侧越界同样拒收（上界取常量，R78 后是 72 而不是 48）");
        SimpleAssert.eq(
            Boolean.FALSE,
            config.add(PocketConstants.GHOST_FLUID_SLOT_LIMIT, fluid(PocketConstants.GHOST_FLUID_SLOT_LIMIT, "water")),
            "★流体槽上界那一格越界拒收（R78② 后是第 18 格；旧口径合法到 5）");
        SimpleAssert.eq(Boolean.FALSE, config.add(-1, item(-1, 1, 0, "")), "负索引拒收");

        final NBTTagCompound root = new NBTTagCompound();
        config.writeTo(root);
        final PocketFilterConfig back = PocketFilterConfig.readFrom(root);
        SimpleAssert.eq(5, back.size(), "五条声明往返后条数不变（中栏两条 + 流体两条 + 源质一条）");
        // 逐字段：槽索引 + 载荷 + 种类
        assertFilterEquals(0, Kind.ITEM, "i:2621:7:", back.at(Kind.ITEM, 0));
        assertFilterEquals(
            PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1,
            Kind.ITEM,
            "i:1:0:",
            back.at(Kind.ITEM, PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1));
        assertFilterEquals(0, Kind.FLUID, "f:lava", back.at(Kind.FLUID, 0));
        assertFilterEquals(
            PocketConstants.GHOST_FLUID_SLOT_LIMIT - 1,
            Kind.FLUID,
            "f:water",
            back.at(Kind.FLUID, PocketConstants.GHOST_FLUID_SLOT_LIMIT - 1));
        assertFilterEquals(
            PocketConstants.GHOST_ESSENCE_SLOT_LIMIT - 1,
            Kind.ESSENCE,
            "e:essentia:ignis",
            back.at(Kind.ESSENCE, PocketConstants.GHOST_ESSENCE_SLOT_LIMIT - 1));

        // NBT 层面：键名确实带 slotIndex，且落的是 int（不是字符串）
        final NBTTagCompound itemEntry = domainOf(root).getTagList(PocketConstants.FILTER_ITEMS, 10)
            .getCompoundTagAt(0);
        SimpleAssert.that(itemEntry.hasKey(PocketConstants.FILTER_SLOT, 3), "落档必须带 slotIndex 键（TAG_Int=3）");
        SimpleAssert.eq(0, itemEntry.getInteger(PocketConstants.FILTER_SLOT), "索引 0 必须原样落档");

        // 索引 0 与"未设置"必须可区分：手搓一条缺 slotIndex 的档，读回既不算 0 也不入表
        final NBTTagCompound orphan = new NBTTagCompound();
        orphan.setInteger(PocketConstants.FILTER_ITEM_ID, 2621);
        orphan.setInteger(PocketConstants.FILTER_META, 7);
        final NBTTagList orphanList = new NBTTagList();
        orphanList.appendTag(orphan);
        final NBTTagCompound orphanDomain = new NBTTagCompound();
        orphanDomain.setTag(PocketConstants.FILTER_ITEMS, orphanList);
        final NBTTagCompound orphanRoot = new NBTTagCompound();
        orphanRoot.setTag(PocketConstants.FILTERS, orphanDomain);
        final PocketFilterConfig orphans = PocketFilterConfig.readFrom(orphanRoot);
        SimpleAssert.eq(0, orphans.size(), "缺 slotIndex 的条目不得挂到第 0 格上（读回即丢弃）");
        SimpleAssert.that(PocketConstants.FILTER_SLOT_UNSET != 0, "未设置哨兵不得占用合法索引 0（否则缺键条目会错挂到中栏第一格）");
        SimpleAssert.eq(
            Boolean.FALSE,
            PocketFilterConfig.isAllowedSlotIndex(Kind.ITEM, PocketConstants.FILTER_SLOT_UNSET),
            "未设置哨兵不可作为合法槽位");
    }

    /** 同一槽二次 add = 覆盖（绝不追加第二条），且首次声明序保持。 */
    private static void filterSlotOverwriteNotAppend() {
        final PocketFilterConfig config = new PocketFilterConfig();
        SimpleAssert.that(config.add(5, item(5, 2621, 0, "")), "第 5 格首次声明");
        SimpleAssert.that(config.add(6, item(6, 1, 0, "")), "第 6 格首次声明");
        SimpleAssert.eq(Boolean.FALSE, config.add(5, item(5, 262, 3, "BB")), "同槽二次声明返回 false（覆盖，不是新增）");
        SimpleAssert.eq(2, config.size(), "覆盖后仍是两条，绝不追加第三格");

        final List<String> keys = new ArrayList<>();
        for (PocketFilterConfig.Filter filter : config.filters()) {
            keys.add(filter.slotIndex() + "=" + filter.key());
        }
        SimpleAssert.eq(Arrays.asList("5=i:262:3:BB", "6=i:1:0:"), keys, "覆盖生效且首次声明序保持");
        SimpleAssert.eq(Boolean.FALSE, config.contains("i:2621:0:"), "被覆盖的旧载荷不再在表内");
        SimpleAssert.eq(
            "i:1:0:",
            config.at(Kind.ITEM, 6)
                .key(),
            "邻槽不受影响");

        // 不同槽声明同一载荷是合法的（两格都要同一种东西）—— 槽位才是身份
        SimpleAssert.that(config.add(9, item(9, 262, 3, "BB")), "另一格声明同一载荷应可加入");
        SimpleAssert.eq(3, config.size(), "同载荷不同槽 ⇒ 三条");
    }

    private static void assertFilterEquals(int slot, Kind kind, String key, PocketFilterConfig.Filter actual) {
        if (actual == null) {
            throw new AssertionError("槽 " + slot + " 读回为空");
        }
        SimpleAssert.eq(slot, actual.slotIndex(), "槽索引往返");
        SimpleAssert.eq(kind, actual.kind(), "声明种类往返");
        SimpleAssert.eq(key, actual.key(), "载荷键往返");
    }

    /**
     * 载荷侧不得出现任何"通道索引/列表下标"：源质与流体声明每个字段都必须是 TAG_String=8；
     * 物品声明允许的整数字段只有 {@code itemId}、{@code meta}（它们本身就是身份）与
     * {@code slotIndex}（R38 要求的被转换槽索引，与通道索引无关）。
     */
    private static void assertNoChannelIndexCarried(NBTTagCompound root) {
        final NBTTagCompound domain = domainOf(root);
        final NBTTagList essentia = domain.getTagList(PocketConstants.FILTER_ESSENTIA, 10);
        SimpleAssert.eq(1, essentia.tagCount(), "源质声明一条");
        assertAllStringFields(essentia.getCompoundTagAt(0), "源质声明");
        final NBTTagList fluids = domain.getTagList(PocketConstants.FILTER_FLUIDS, 10);
        assertAllStringFields(fluids.getCompoundTagAt(1), "流体声明");
        final NBTTagCompound itemEntry = domain.getTagList(PocketConstants.FILTER_ITEMS, 10)
            .getCompoundTagAt(0);
        SimpleAssert.that(
            itemEntry.hasKey(PocketConstants.FILTER_ITEM_ID, 3) && itemEntry.hasKey(PocketConstants.FILTER_META, 3)
                && itemEntry.hasKey(PocketConstants.FILTER_SLOT, 3),
            "物品声明只允许 itemId/meta/slotIndex 三个整数字段");
        for (String key : itemEntry.func_150296_c()) {
            SimpleAssert.that(
                PocketConstants.FILTER_ITEM_ID.equals(key) || PocketConstants.FILTER_META.equals(key)
                    || PocketConstants.FILTER_NBT.equals(key)
                    || PocketConstants.FILTER_SLOT.equals(key),
                "物品声明出现额外字段：只允许 itemId/meta/nbt/slotIndex，实得 " + key);
        }
    }

    private static NBTTagCompound domainOf(NBTTagCompound root) {
        return root.getCompoundTag(PocketConstants.FILTERS);
    }

    private static void assertAllStringFields(NBTTagCompound entry, String label) {
        for (String key : entry.func_150296_c()) {
            if (PocketConstants.FILTER_SLOT.equals(key)) {
                SimpleAssert.that(entry.hasKey(key, 3), label + " 的 slotIndex 必须是 TAG_Int=3");
                continue;
            }
            SimpleAssert.that(entry.hasKey(key, 8), label + " 的字段 " + key + " 必须是字符串（TAG_String=8）");
        }
    }

    private static List<String> keysOf(PocketFilterConfig config) {
        final List<String> keys = new ArrayList<>();
        for (PocketFilterConfig.Filter filter : config.filters()) {
            keys.add(filter.key());
        }
        return keys;
    }

    private static PocketFilterConfig.Filter item(int slot, int itemId, int meta, String nbt) {
        return new PocketFilterConfig.ItemFilter(slot, itemId, meta, nbt);
    }

    private static PocketFilterConfig.Filter fluid(int slot, String name) {
        return new PocketFilterConfig.FluidFilter(slot, name);
    }

    private static PocketFilterConfig.Filter essence(int slot, String typeId, String tag) {
        return new PocketFilterConfig.EssenceFilter(slot, typeId, tag);
    }

    // ------------------------------------------------------------------ D 批 · S6 通道服务端

    /**
     * ★R87-a 改钉（旧钉"推送/拉取互斥"已被双相并存覆盖）+ R39b 保留点：
     * <ul>
     * <li>无 ghost ⇒ 纯推送：只跑注入相，{@code extract} 一次都不调；</li>
     * <li>有 ghost ⇒ <b>双相</b>：注入相照常跑（推送不再被声明关死），补满相按声明抽取，
     * 两相合并成一份 Report、批尾仍只发一次网络通知；补满相 delta 为<b>负</b>（R7 带符号通知，
     * 与注入相同键合并后可为净负）。</li>
     * </ul>
     * 中途把 ghost 配置清空/加上也不能改变本次运行的相构成（{@link PocketChannelState#pullMode()}
     * 是激活时算的那一次，R87-a 起语义 = "本次运行含补满相"），这条也在本用例里钉住。
     */
    private static void pullModeWhenFilterPresentElsePush() {
        PocketChannelManager.INSTANCE.reset();
        final UUID player = UUID.fromString(CELL_A);
        final PocketCellBindings bindings = bindingsOf(CELL_A);

        // 1) 无 ghost ⇒ 纯推送（只有注入相）
        final StubOps push = new StubOps();
        push.fillSources(3, 3);
        SimpleAssert.that(
            PocketChannelManager.INSTANCE
                .openChannel(player, PocketChannelState.Mode.SHORT, bindings, new NBTTagCompound(), push, 1),
            "推送模式通道应受理");
        final PocketChannelState pushed = PocketChannelManager.INSTANCE.peek(player);
        SimpleAssert.eq(Boolean.FALSE, pushed.pullMode(), "无 ghost 声明 ⇒ pullMode=false（本次运行不含补满相）");
        driveToBatch(player, bindings, push);
        SimpleAssert.that(push.injectCalls > 0, "推送模式确实调了 inject");
        SimpleAssert.eq(0, push.extractCalls.size(), "推送模式一次都没调 extract");
        SimpleAssert.eq(Boolean.FALSE, pushed.pullMode(), "跑过一批后模式位不变（激活时算一次）");
        PocketChannelManager.INSTANCE.reset();

        // 2) 有 ghost ⇒ 双相：注入照跑 + 按声明补满（R87-a：互斥口径作废）
        final StubOps pull = new StubOps();
        pull.extractable.put("i:1:0:", 7);
        pull.fillSources(4);
        final PocketFilterConfig filters = new PocketFilterConfig();
        SimpleAssert.that(filters.add(5, item(5, 1, 0, "")), "第 5 格声明要 i:1:0:");
        SimpleAssert.that(
            PocketChannelManager.INSTANCE
                .openChannel(player, PocketChannelState.Mode.SHORT, bindings, filters, new NBTTagCompound(), pull, 1),
            "双相模式通道应受理");
        final PocketChannelState pulling = PocketChannelManager.INSTANCE.peek(player);
        SimpleAssert.that(pulling.pullMode(), "ghost 配置非空 ⇒ pullMode=true（服务端算的那一次 = 本次运行含补满相）");
        driveToBatch(player, bindings, pull);
        SimpleAssert.that(pull.injectCalls > 0, "★R87-a：有声明也照常注入（推送向不再被互斥关死）");
        SimpleAssert.that(!pull.extractCalls.isEmpty(), "补满相确实按声明抽取");
        SimpleAssert.eq("i:1:0:", pull.extractCalls.get(0), "抽取用的就是那条 ghost 声明的载荷键");
        SimpleAssert.eq(5, pull.extractSlots.get(0), "抽取回填时带回了声明占用的槽号（复合键的后半段）");
        SimpleAssert.eq(1, pull.announcements.size(), "★双相合并后一批仍只发一次网络通知（R7）");
        // 两相合并的账目：注入 +4、补满 -7，同键合并成一条净负 delta
        final PocketChannelRunner.Report merged = pulling.lastReport();
        SimpleAssert.eq(11, merged.transferred, "★Report 合并：两相 transferred 累加（4 + 7）");
        SimpleAssert.eq(2, merged.pairsServed, "★Report 合并：pairsServed 累加（注入相 1 对 + 补满相 1 对；pairLimit 按相生效）");
        for (PocketChannelOps.Delta delta : pull.announcements.get(0)) {
            SimpleAssert.eq("i:1:0:", delta.contentKey, "delta 用载荷键标识内容（不含通道索引，R17）");
            SimpleAssert.eq(-3L, delta.amount, "★同键带符号合并：+4（注入）与 -7（抽取）合为净负一条");
        }

        // 3) 运行中途清空 ghost 配置 ⇒ 模式位仍是激活时算的那一次（不得中途换轨）
        filters.clear();
        SimpleAssert.that(pulling.pullMode(), "模式在激活时算一次：运行中删光 ghost 也不改本次轨道");
        PocketChannelManager.INSTANCE.reset();
    }

    /** 把短效通道推到第一批真的跑起来（按倒计时逐 tick 驱动）。 */
    private static void driveToBatch(UUID player, PocketCellBindings bindings, StubOps ops) {
        for (int tick = 0; tick < PocketConstants.CHANNEL_TICK_PERIOD + 1; tick++) {
            PocketChannelManager.INSTANCE.tickShortChannel(player, bindings, ops, 1);
        }
    }

    // ================================================================== R87 批 · 通道方向修复（A 切片）

    /**
     * ★R87-a：双相批的可测本体——一次 {@code runDualPhase} 调用里注入相与补满相都跑，
     * 合并成一份 Report 且批尾只发一次网络通知；{@code refillPhase=false}（激活时算好的那次）
     * 则补满相整相不跑。
     */
    private static void dualPhaseRunsInjectThenRefill() {
        final StubOps ops = new StubOps();
        ops.fillSources(6);
        final PocketCellBindings bindings = bindingsOf(CELL_A);
        final PocketFilterConfig filters = new PocketFilterConfig();
        SimpleAssert.that(filters.add(3, item(3, 2622, 0, "")), "第 3 格声明要 i:2622:0:（与注入的 i:1:0: 不同键）");
        ops.extractable.put(
            filters.at(Kind.ITEM, 3)
                .key(),
            4);
        final PocketChannelRunner.Report report = PocketChannelRunner
            .runDualPhase(bindings, new PocketRotationCursor(), filters, true, ops, 8);
        SimpleAssert.eq(10, report.transferred, "★两相累加：注入 6 + 补满 4");
        SimpleAssert.eq(4, report.pairsServed, "注入相 3 对（全通道轮转）+ 补满相 1 对");
        SimpleAssert.eq(1, ops.announcements.size(), "★两相合并后批尾只 post 一次（R7）");
        final List<PocketChannelOps.Delta> posted = ops.announcements.get(0);
        SimpleAssert.eq(2, posted.size(), "两个不同载荷键各一条带符号 delta（+6 与 -4，同键才合并）");

        // refillPhase=false：同一会话、同一声明，纯推送形状 ⇒ 补满相整相不跑
        final StubOps pure = new StubOps();
        pure.fillSources(6);
        final PocketChannelRunner.Report pushOnly = PocketChannelRunner
            .runDualPhase(bindingsOf(CELL_A), new PocketRotationCursor(), filters, false, pure, 8);
        SimpleAssert.eq(0, pure.extractCalls.size(), "refillPhase=false ⇒ 一次都不 extract（R39b 单点保留的语义）");
        SimpleAssert.eq(6, pushOnly.transferred, "纯推送照常");
    }

    /**
     * ★R87-a：Report 合并语义（用测试钉住）——计数与 deltas 全累加；lastReceipt
     * <b>失败码优先于成功码</b>、同为失败取后相、加数为 null 让位。
     */
    private static void reportMergeSemanticsPinned() {
        final PocketChannelRunner.Report inject = new PocketChannelRunner.Report();
        inject.pairsServed = 3;
        inject.transferred = 5;
        inject.full = 1;
        inject.lastReceipt = PocketReceipt.FULL;
        inject.deltas.add(new PocketChannelOps.Delta(CELL_A, "item", "i:1:0:", 5L));

        final PocketChannelRunner.Report refill = new PocketChannelRunner.Report();
        refill.pairsServed = 1;
        refill.transferred = 7;
        refill.lost = 1;
        refill.noChannel = 2;
        refill.lastReceipt = PocketReceipt.LOST;
        refill.deltas.add(new PocketChannelOps.Delta(CELL_A, "item", "i:2622:0:", -7L));

        final PocketChannelRunner.Report merged = new PocketChannelRunner.Report();
        PocketChannelRunner.mergeInto(merged, inject);
        PocketChannelRunner.mergeInto(merged, refill);
        SimpleAssert.eq(4, merged.pairsServed, "pairsServed 累加");
        SimpleAssert.eq(12, merged.transferred, "transferred 累加");
        SimpleAssert.eq(1, merged.full, "full 累加");
        SimpleAssert.eq(1, merged.lost, "lost 累加");
        SimpleAssert.eq(2, merged.noChannel, "noChannel 累加");
        SimpleAssert.eq(2, merged.deltas.size(), "deltas 拼接（批尾 flush 前合并，双相批正是这么用的）");
        SimpleAssert.eq(PocketReceipt.LOST, merged.lastReceipt, "★同为失败码取后相（补满相的 LOST 覆盖注入相的 FULL）");

        // 失败码优先于成功码（两个方向都钉）
        final PocketChannelRunner.Report failThenOk = new PocketChannelRunner.Report();
        failThenOk.lastReceipt = PocketReceipt.FULL;
        final PocketChannelRunner.Report okAdd = new PocketChannelRunner.Report();
        okAdd.lastReceipt = PocketReceipt.OK;
        PocketChannelRunner.mergeInto(failThenOk, okAdd);
        SimpleAssert.eq(PocketReceipt.FULL, failThenOk.lastReceipt, "★失败码优先：补满相的 OK 不得盖掉注入相的 FULL");
        final PocketChannelRunner.Report okThenFail = new PocketChannelRunner.Report();
        okThenFail.lastReceipt = PocketReceipt.OK;
        final PocketChannelRunner.Report failAdd = new PocketChannelRunner.Report();
        failAdd.lastReceipt = PocketReceipt.FULL;
        PocketChannelRunner.mergeInto(okThenFail, failAdd);
        SimpleAssert.eq(PocketReceipt.FULL, okThenFail.lastReceipt, "★反向同规则：后相的失败照样胜出");

        // null 让位：加数没有回执 ⇒ 保留原值
        final PocketChannelRunner.Report keep = new PocketChannelRunner.Report();
        keep.lastReceipt = PocketReceipt.PARTIAL;
        PocketChannelRunner.mergeInto(keep, new PocketChannelRunner.Report());
        SimpleAssert.eq(PocketReceipt.PARTIAL, keep.lastReceipt, "加数 lastReceipt 为 null ⇒ 让位");
    }

    /**
     * ★R87-a 反成环铁律（流体面）：存在 FLUID 声明时，<b>同名流体</b>的 tank 来源不进注入——
     * 否则"推出去一拍、按声明抽回来一拍"就是 tank ⇄ 元件的永动环。不同名流体照常推。
     */
    private static void antiLoopFluidDeclarationExcludesSameFluid() {
        final StubOps ops = new StubOps();
        ops.addSource(0, PocketFilterConfig.fluidKey("water"), 1000, PocketChannelOps.SourceKind.FLUID);
        ops.addSource(1, PocketFilterConfig.fluidKey("lava"), 500, PocketChannelOps.SourceKind.FLUID);
        ops.addSource(2, "i:1:0:", 3, PocketChannelOps.SourceKind.ITEM);
        final PocketFilterConfig filters = new PocketFilterConfig();
        SimpleAssert.that(filters.add(4, new PocketFilterConfig.FluidFilter(4, "water")), "声明第 4 tank 补水");
        final PocketChannelRunner.Report report = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), ops, Integer.MAX_VALUE, filters);
        SimpleAssert.eq(503, report.transferred, "★同名流体的 tank 被剔除：只推 lava 500 + 物品 3（水一克都不推）");
        SimpleAssert.that(!ops.servedKeys.contains(PocketFilterConfig.fluidKey("water")), "water 从未被投递（铁律逐字成立）");
        SimpleAssert.that(ops.servedKeys.contains(PocketFilterConfig.fluidKey("lava")), "不同名流体照常推");
        SimpleAssert.that(ops.servedKeys.contains("i:1:0:"), "物品来源不受流体声明牵连");

        // 负控：filters 为 null（纯推送）⇒ 全量（铁律只剔与声明匹配的，不多剔）
        final StubOps bare = new StubOps();
        bare.addSource(0, PocketFilterConfig.fluidKey("water"), 1000, PocketChannelOps.SourceKind.FLUID);
        final PocketChannelRunner.Report pushed = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), bare, Integer.MAX_VALUE, null);
        SimpleAssert.eq(1000, pushed.transferred, "无声明 ⇒ 全量推送");
    }

    /**
     * ★R87-a 反成环铁律（源质面）：剔除按<b>语义载荷</b>（tag 字符串）匹配，<b>不按原始键串</b>——
     * 声明侧键的 typeId 段可以是第三方回落后的 "item"（{@code e:item:aer}），来源侧是空
     * （{@code e::aer}，口袋源质不属于任何通道），逐字比键会漏剔。同 tag 声明 ⇒ 该 tag 的
     * 口袋源质不进注入；异 tag 照常推。
     */
    private static void antiLoopEssenceMatchesByTagNotRawKey() {
        final StubOps ops = new StubOps();
        ops.addSource(0, PocketFilterConfig.essenceKey("", "aer"), 10, PocketChannelOps.SourceKind.ESSENCE);
        ops.addSource(1, PocketFilterConfig.essenceKey("", "ignis"), 20, PocketChannelOps.SourceKind.ESSENCE);
        final PocketFilterConfig filters = new PocketFilterConfig();
        SimpleAssert.that(
            filters.add(9, new PocketFilterConfig.EssenceFilter(9, "item", "aer")),
            "声明第 9 源质格补 aer（typeId=第三方回落 item）");
        final PocketChannelRunner.Report report = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), ops, Integer.MAX_VALUE, filters);
        SimpleAssert.eq(20, report.transferred, "★同 tag（aer）来源被剔除：只推 ignis 20 点");
        SimpleAssert
            .that(!ops.servedKeys.contains(PocketFilterConfig.essenceKey("", "aer")), "aer 从未被投递（按 tag 匹配，键串不同也剔）");
        SimpleAssert.that(ops.servedKeys.contains(PocketFilterConfig.essenceKey("", "ignis")), "异 tag 照常推");

        // 负控：filters 为 null ⇒ 两支都推（铁律只剔与声明语义匹配的，不多剔）
        final StubOps bare = new StubOps();
        bare.addSource(0, PocketFilterConfig.essenceKey("", "aer"), 10, PocketChannelOps.SourceKind.ESSENCE);
        bare.addSource(1, PocketFilterConfig.essenceKey("", "ignis"), 20, PocketChannelOps.SourceKind.ESSENCE);
        final PocketChannelRunner.Report pushed = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), bare, Integer.MAX_VALUE, null);
        SimpleAssert.eq(30, pushed.transferred, "无声明 ⇒ 全量推送");
    }

    /**
     * ★R87-b：多通道件每批对 {@code channelIdsOf} 的<b>全部</b>通道各服务一轮（顺序 = 列表稳定序），
     * {@code pairLimit} 仍是硬上限。三通道都出现在候选里，正是 R87-c（handlerOf 直问元件）的
     * 消费侧表现：通道发现不再被宿主 cellsMap 的"只注册第一个命中"截成一条。
     */
    private static void multiChannelCellServesAllChannelsWithPairCap() {
        final StubOps ops = new StubOps();
        ops.fillSources(2);
        final PocketChannelRunner.Report all = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), ops, Integer.MAX_VALUE, null);
        SimpleAssert.eq(3, all.pairsServed, "★三通道都在 channelIdsOf ⇒ 各服务一轮");
        SimpleAssert.eq(Arrays.asList("item", "fluid", "essentia"), ops.servedChannels, "服务序 = channelIdsOf 的稳定序");

        // pairLimit=2 ⇒ 硬上限：只服务前两条通道，不开新对（重灌来源：上一轮已把桩件来源搬空）
        ops.fillSources(2);
        ops.servedChannels.clear();
        final PocketChannelRunner.Report capped = PocketChannelRunner.runInjectBatch(bindingsOf(CELL_A), ops, 2, null);
        SimpleAssert.eq(2, capped.pairsServed, "pairLimit 仍是硬上限");
        SimpleAssert.eq(Arrays.asList("item", "fluid"), ops.servedChannels, "超限后不再服务第三对");

        // 候选为空 ⇒ noChannel 计数留痕（玩家读得到"这只元件没有通道"）
        final StubOps blind = new StubOps();
        blind.fillSources(2);
        blind.lost.add(CELL_A);
        final PocketChannelRunner.Report gone = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), blind, Integer.MAX_VALUE, null);
        SimpleAssert.eq(1, gone.lost, "失联元件走 lost 计数（不进通道循环）");
    }

    /**
     * ★R87 饿死收窄（行为面）：FULL 记失败后<b>跳过该来源继续</b>（旧形状一次就 break）；
     * LOST/NO_ACCESS（元件此刻给不出）仍停批顺延下一轮。
     */
    private static void injectBatchNoLongerStopsOnFull() {
        final StubOps full = new StubOps();
        full.capacity.put(CELL_A + "#item", 0);
        full.fillSources(8, 8);
        final PocketChannelRunner.Report stuffed = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), full, 1, null);
        SimpleAssert.eq(2, full.injectCalls, "★第一格 FULL 后第二格仍被尝试（旧形状 1 次即 break ⇒ 收窄后 2 次）");
        SimpleAssert.eq(2, stuffed.full, "两次满各自计数");
        SimpleAssert.eq(0, stuffed.transferred, "满当然是零成交");

        // LOST：仍停批
        final StubOps lost = new StubOps();
        lost.fillSources(8, 8);
        lost.forcedInject.put("i:1:0:", PocketReceipt.LOST);
        final PocketChannelRunner.Report halted = PocketChannelRunner.runInjectBatch(bindingsOf(CELL_A), lost, 1, null);
        SimpleAssert.eq(1, lost.injectCalls, "★LOST 停批：后续来源不再被叫（顺延下一轮）");
        SimpleAssert.eq(1, halted.lost, "失联计数");
        SimpleAssert.eq(PocketReceipt.LOST, halted.lastReceipt, "回执是失联");

        // NO_ACCESS：与 LOST 同一档
        final StubOps locked = new StubOps();
        locked.fillSources(8, 8);
        locked.forcedInject.put("i:1:0:", PocketReceipt.NO_ACCESS);
        final PocketChannelRunner.Report denied = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), locked, 1, null);
        SimpleAssert.eq(1, locked.injectCalls, "★NO_ACCESS 同样停批");
        SimpleAssert.eq(1, denied.noAccess, "无权限计数");
    }

    /**
     * ★R10：「分区 WHITELIST 拒收」与「元件已满」必须是两条玩家可见的通道。
     * <p>
     * 除了断言两个回执码本身不同，还断言<b>玩家读到的文案键</b>不同 —— 映射表
     * （{@code NekoPocketPanel#receiptOfReport}）把两者并成一条就是本条裁定的实质失效，
     * 而编译与运行都不会报错。
     */
    private static void partitionWhitelistReceiptDiffersFromCellFull() {
        // 元件容量无限、但该载荷不在分区白名单里
        final StubOps rejected = new StubOps();
        rejected.capacity.put(CELL_A + "#item", 100);
        rejected.rejected.add("i:1:0:");
        rejected.fillSources(8);
        final PocketChannelRunner.Report denied = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), rejected, 1, null);

        // 分区放行、但元件一点空间都没有
        final StubOps full = new StubOps();
        full.capacity.put(CELL_A + "#item", 0);
        full.fillSources(8);
        final PocketChannelRunner.Report stuffed = PocketChannelRunner
            .runInjectBatch(bindingsOf(CELL_A), full, 1, null);

        SimpleAssert.eq(PocketReceipt.FILTER_REJECTED, denied.lastReceipt, "分区未列出 ⇒ FILTER_REJECTED");
        SimpleAssert.eq(PocketReceipt.FULL, stuffed.lastReceipt, "容量为零 ⇒ FULL");
        SimpleAssert.that(denied.lastReceipt != stuffed.lastReceipt, "两个码不得是同一个常量");
        SimpleAssert.eq(0, denied.full, "拒收不得计成元件满");
        SimpleAssert.eq(0, stuffed.filterRejected, "元件满不得计成分区拒收");
        SimpleAssert.eq(1, denied.filterRejected, "拒收自己计数");
        SimpleAssert.eq(1, stuffed.full, "满自己计数");
        SimpleAssert.eq(8, rejected.sources.get(0).count, "两种失败余量都原样留在源槽（不吞件）");
        SimpleAssert.eq(8, full.sources.get(0).count, "同上（元件满）");

        final String deniedKey = NekoPocketPanel.receiptOfReport(denied);
        final String fullKey = NekoPocketPanel.receiptOfReport(stuffed);
        SimpleAssert.eq("gtit.pocket.receipt.partition_denied", deniedKey, "拒收 → 分区文案键");
        SimpleAssert.eq("gtit.pocket.receipt.cell_full", fullKey, "元件满 → 满文案键");
        SimpleAssert.that(!deniedKey.equals(fullKey), "玩家可见文案必须两条（混用即'东西不见了'）");
    }

    // ------------------------------------------------------------------ D 批 · S5 ghost 就地转换

    /**
     * ★R59b 偏离④ / R38 第 1 条：ghost 的身份是<b>复合键 {@code (kind, slotIndex)}</b>，
     * 同步 blob 的编解码必须把三段都无损带回来。
     * <p>
     * 三条声明都用索引 0（中栏第 0 格 / 流体条第 0 格 / 源质第 0 格）：若键退化成裸索引，
     * 这三条会互相覆盖成一条 —— 那正是 S1fix 首轮 24/26 暴露的缺陷，本用例把它钉死。
     */
    private static void ghostSlotCompositeKeyRoundTrip() {
        final PocketFilterConfig config = new PocketFilterConfig();
        SimpleAssert.that(config.add(0, item(0, 2621, 7, "AAA")), "中栏第 0 格声明");
        SimpleAssert.that(config.add(0, fluid(0, "lava")), "流体条第 0 格声明（同索引、跨区域 ⇒ 不得互相覆盖）");
        SimpleAssert.that(config.add(0, essence(0, "essentia", "aer")), "源质第 0 格声明（同上）");
        SimpleAssert.eq(3, config.size(), "三条各自独立");

        final String blob = NekoPocketPanel.ghostBlobOf(config);
        final PocketFilterConfig back = NekoPocketPanel.parseGhostBlob(blob);
        SimpleAssert.eq(3, back.size(), "blob 往返后三条都在（裸索引会只剩一条）");
        assertFilterEquals(0, Kind.ITEM, "i:2621:7:AAA", back.at(Kind.ITEM, 0));
        assertFilterEquals(0, Kind.FLUID, "f:lava", back.at(Kind.FLUID, 0));
        assertFilterEquals(0, Kind.ESSENCE, "e:essentia:aer", back.at(Kind.ESSENCE, 0));

        // 二次编解码幂等（任何一段被格式化重写都会在下一轮暴露）
        SimpleAssert.eq(blob, NekoPocketPanel.ghostBlobOf(back), "blob 二次编码逐字符一致");

        // 越界槽位不得被 blob 复活（白名单仍是 PocketConstants 的三个上界）
        final PocketFilterConfig backAgain = NekoPocketPanel.parseGhostBlob(blob);
        SimpleAssert.eq(
            Boolean.FALSE,
            backAgain.add(PocketConstants.GHOST_ITEM_SLOT_LIMIT, item(PocketConstants.GHOST_ITEM_SLOT_LIMIT, 1, 0, "")),
            "中栏越界槽位（= 上界常量本身，R80① 后是 135）拒收");
        SimpleAssert.eq(3, backAgain.size(), "拒收不改变条数");
        // 外来/损坏条目丢弃而不炸（面板不得因一条坏 blob 崩掉）
        SimpleAssert.eq(
            0,
            NekoPocketPanel.parseGhostBlob("WAT|notanumber|i:1:0:;x;y")
                .size(),
            "坏条目全部丢弃");
        SimpleAssert.eq(
            0,
            NekoPocketPanel.parseGhostBlob(null)
                .size(),
            "null blob 视为空表");
        // 解绑按 (kind, slot) 精确生效：摘掉流体条第 0 格不得影响中栏第 0 格
        SimpleAssert.that(backAgain.removeAt(Kind.FLUID, 0), "按复合键解绑");
        SimpleAssert.that(backAgain.at(Kind.ITEM, 0) != null, "同索引的中栏声明不受影响");
        SimpleAssert.eq(2, backAgain.size(), "解绑后剩两条");
        // NBT 侧同样逐条带 slotIndex（R38 第 1 条），且与 blob 侧读回一致
        final NBTTagCompound root = new NBTTagCompound();
        config.writeTo(root);
        final PocketFilterConfig fromNbt = PocketFilterConfig.readFrom(root);
        SimpleAssert.eq(NekoPocketPanel.ghostBlobOf(fromNbt), blob, "NBT 往返与 blob 往返给出同一份声明表（两处形状不得分叉）");
    }

    // ------------------------------------------------------------------ D 批 · S7 蒸馏

    /** 无源质产物 ⇒ 不推进、不消耗（判据照 {@code TileAlchemyFurnace.canSmelt()}，R28/C4）。 */
    private static void distillNoAspectDoesNotAdvance() {
        final StubGate gate = new StubGate();
        final ItemStack rock = stack(1);
        gate.putDistill(rock, TaumAspectAmounts.EMPTY);
        final PocketEssenceStore store = new PocketEssenceStore();

        final PocketDistillDriver.Batch batch = PocketDistillDriver
            .planDistillBatch(new ItemStack[] { rock, null }, gate, store);
        SimpleAssert.eq(Boolean.FALSE, batch.advanceable, "该物品不含源质 ⇒ 本轮不可推进");
        SimpleAssert.eq(Boolean.FALSE, batch.accepted, "★唯一消耗判据：不可推进必然不消耗");
        SimpleAssert.eq(0, batch.sourceCount, "没有格参与本轮");
        SimpleAssert.eq(0, batch.points, "零候选");
        SimpleAssert.eq(0, store.totalPoints(), "源质表分毫未动");
        SimpleAssert.eq(1, gate.aspectQueries.size(), "只问了那一个非容器格（null 格不问）");
        SimpleAssert.eq(rock, gate.aspectQueries.get(0), "问的就是格 0 的那一枚");

        // 全是空容器/空格 ⇒ 同样不推进（且一个 aspectsOf 都不该问）
        final PocketDistillDriver.Batch empty = PocketDistillDriver
            .planDistillBatch(new ItemStack[] { null, null }, gate, store);
        SimpleAssert.eq(Boolean.FALSE, empty.advanceable, "12 格全空 ⇒ 不推进");
        SimpleAssert.eq(1, gate.aspectQueries.size(), "全空时不再产生新的 TC 侧调用");
    }

    /**
     * ★R29 + R83 A2：全有全无仍然不许"截断了还照扣物品"，但作用单位由<b>整轮</b>收到<b>一组物品</b>——
     * 放不下的一组零入账、零消耗，放得下的组照常入账（旧实现让放得下的组陪绑 = 玩家读到"什么都不发生"）。
     */
    private static void distillAllOrNothingKeepsBoth() {
        final StubGate gate = new StubGate();
        // ★R84：工作单位是<b>格</b>，两格就是两个落点；这里换 damage 只为了让桩件产物是两种 tag
        // （aer / ignis），不再承担旧 β 口径的"分组"语义。
        final ItemStack aerItem = stackDistill(2, 0);
        final ItemStack ignisItem = stackDistill(3, 1);
        gate.putDistill(aerItem, TaumAspectAmounts.of(new String[] { "aer" }, new int[] { 30 }));
        gate.putDistill(ignisItem, TaumAspectAmounts.of(new String[] { "ignis" }, new int[] { 40 }));
        final PocketEssenceStore store = new PocketEssenceStore();
        store.add("ignis", PocketConstants.ESSENCE_CAP_PER_TAG - 30); // ignis 只剩 30 点空间 < 40 ⇒ 那格放不下

        final ItemStack[] slots = new ItemStack[] { aerItem, ignisItem };
        final PocketDistillDriver.Batch batch = PocketDistillDriver.planDistillBatch(slots, gate, store);
        SimpleAssert.that(batch.advanceable, "两格都可蒸");
        SimpleAssert.eq(1, batch.sourceCount, "★按<b>格</b>全有全无：只有放得下的 aer 那格参与本轮（R84）");
        SimpleAssert.eq(0, batch.sourceSlots[0], "扣件落点就是 aer 那一格");
        SimpleAssert.eq(Boolean.TRUE, batch.accepted, "aer 那格收得进 ⇒ 本轮推进");
        SimpleAssert.eq(Boolean.FALSE, batch.needsRoom, "有格建效 ⇒ 不判'停在满格不重跑'（否则倒计时被冻住）");
        SimpleAssert.eq(1, batch.discardedGroups, "★被放弃的<b>格</b>要留下读数（R83 偏差 3c 不得静默；单位 R84 起是格）");
        SimpleAssert.eq(40, batch.discardedPoints, "被放弃的点数 = ignis 那格的 40 点");
        SimpleAssert.eq(Boolean.FALSE, batch.candidates.containsKey("ignis"), "★放不下的一格整份不进候选（不做截断）");
        // 生产侧的消耗动作被 accepted 门住（见 PocketDistillDriver#runBatch 按 sourceSlots 逐组扣件）
        store.putAll(batch.candidates);
        SimpleAssert
            .eq(PocketConstants.ESSENCE_CAP_PER_TAG - 30, store.get("ignis"), "预检不过的那格 ⇒ ignis 零变化（★按符号读，抬上限不得让这条假绿）");
        SimpleAssert.eq(30, store.get("aer"), "放得下的那组整份入账");
        SimpleAssert.eq(2, slots[0].stackSize, "planDistillBatch 是纯函数：自己不吃件");
        SimpleAssert.eq(3, slots[1].stackSize, "★被放弃的那组一件都不消耗 ⇒ 不销毁价值");

        // 只有一格、且它是被"存量挤满格子"挡下 ⇒ 这才是可解卡的"停在满格不重跑"。
        // ★R84：越界样本按符号造（只留 20 点空间 < 40）；旧桩件写死 `add(ignis, 40)` 靠的是"40+40>64"，
        // 上限抬到 256 后那个前提<b>静默失效</b> ⇒ 这条会假绿成"没在钉满格支"。
        final PocketEssenceStore jammed = new PocketEssenceStore();
        jammed.add("ignis", PocketConstants.ESSENCE_CAP_PER_TAG - 20);
        final PocketDistillDriver.Batch wait = PocketDistillDriver
            .planDistillBatch(new ItemStack[] { ignisItem }, gate, jammed);
        SimpleAssert.eq(Boolean.FALSE, wait.accepted, "一格都没收下");
        SimpleAssert.eq(Boolean.TRUE, wait.needsRoom, "取走晶化源质即解卡 ⇒ 仍按 R29 停在满格");
        SimpleAssert.eq(Boolean.FALSE, wait.overCap, "★这与'单件原量超上限'是两件事，不得混成一个状态");

        // 换成放得下的局面：三格两组（中间那格无可蒸物）⇒ 两组各扣一件、一次入账
        final PocketEssenceStore room = new PocketEssenceStore();
        final ItemStack[] two = new ItemStack[] { stackDistill(2, 2), stackDistill(2, 3), stackDistill(2, 4) };
        gate.putDistill(two[0], TaumAspectAmounts.of(new String[] { "aer" }, new int[] { 20 }));
        gate.putDistill(two[2], TaumAspectAmounts.of(new String[] { "terra", "mortuus" }, new int[] { 5, 3 }));
        final PocketDistillDriver.Batch ok = PocketDistillDriver.planDistillBatch(two, gate, room);
        SimpleAssert.that(ok.accepted, "两组都放得下");
        SimpleAssert.eq(2, ok.sourceCount, "只有 2 组有产物参与（第 2 格无可蒸物不计）");
        SimpleAssert.eq(28, ok.points, "原量合计 20+5+3=28 点（R28：不是每 aspect +1；★β：也不乘 stackSize=2）");
        SimpleAssert.eq(2, ok.sourceSlots.length, "扣件落点数 = 组数（多出来的下标不存在 ⇒ 不会重复吃件）");
        SimpleAssert.eq(0, ok.discardedGroups, "没有组被放弃");
        SimpleAssert.eq(28, room.putAll(ok.candidates), "putAll 一次提交");
        SimpleAssert.eq(20, room.get("aer"), "aer 原量入账");
        SimpleAssert.eq(5, room.get("terra"), "terra 原量入账（多 tag 同物也按原量）");
    }

    /**
     * ★R84（作废 R83 A2 / D-3 β 的"组"口径，玩家定档"对象应该是格子，每个格子蒸 1 件"）：
     * 同一物品散在几格 ⇒ <b>各格每轮各蒸 1 件、各留一个扣件落点</b>；产出仍<b>不乘堆叠数</b>
     * （乘了会撞单格上限 + 全有全无 ⇒ 整堆永久卡死，那条理由照旧成立）。
     * TC 递归查表由驱动内的 {@code (item,damage)} 记忆表压成"每种签名一次"（省的是查询，不是落点）。
     */
    private static void distillSameItemAcrossSlotsCountsPerCell() {
        final StubGate gate = new StubGate();
        final ItemStack first = stack(60);
        final ItemStack second = stack(60);
        gate.putDistill(first, TaumAspectAmounts.of(new String[] { "aer" }, new int[] { 5 }));
        gate.putDistill(second, TaumAspectAmounts.of(new String[] { "aer" }, new int[] { 5 }));
        final PocketDistillDriver.Batch one = PocketDistillDriver
            .planDistillBatch(new ItemStack[] { first, second, null }, gate, new PocketEssenceStore());
        SimpleAssert.eq(2, one.sourceCount, "★R84：两格同物 = <b>两个</b>扣件落点（旧 β 口径只给 1 个）");
        SimpleAssert.eq(2, one.sourceSlots.length, "落点数组长度 = 参与格数（旧写法留 11 个 0 ⇒ 会把格 0 吃空）");
        SimpleAssert.eq(0, one.sourceSlots[0], "落点按<b>格序升序</b>：第 1 个是格 0");
        SimpleAssert.eq(1, one.sourceSlots[1], "★第 2 个必须是格 1（重复写同一格 = 另一格永远吃不到）");
        SimpleAssert.eq(10, one.points, "产出 = 两格各 5 点（仍不乘 60 的堆叠数）");
        SimpleAssert.eq(1, gate.aspectQueries.size(), "同物同量只问 TC 一次（省的是查询，不是落点）");
        SimpleAssert.eq(0, one.discardedGroups, "没有格被放弃");

        // ★R84：damage 不再决定工作单位（格才决定），这里换 damage 只是为了让签名/产物不同
        final ItemStack otherDamage = stackDistill(60, 9);
        gate.putDistill(otherDamage, TaumAspectAmounts.of(new String[] { "aer" }, new int[] { 5 }));
        final PocketDistillDriver.Batch three = PocketDistillDriver
            .planDistillBatch(new ItemStack[] { first, otherDamage, second }, gate, new PocketEssenceStore());
        SimpleAssert.eq(3, three.sourceCount, "三格 ⇒ 三落点（同物同理，damage 不并格）");
        SimpleAssert.eq(15, three.points, "三格各产 5 点");
        SimpleAssert.eq(3, gate.aspectQueries.size(), "★记忆表是<b>每批一份</b>：本批两种签名 ⇒ 再问两次（累计 3）");
    }

    /**
     * ★R83 偏差 3c 的出口，★R84 把单位由"组"改为<b>格</b>：单格原量就超 {@code ESSENCE_CAP_PER_TAG} 的东西，
     * 旧实现是<b>整轮永久放弃</b>（needsRoom → stalledFull → 不消耗也不倒计时 → 进度条钉死满格、什么都不说）。
     * 现在它只作废自己那一格，并留下"放弃了几格、几点"的读数与 {@code OVER_CAP} 状态位。
     * <p>
     * ★越界样本按符号造（{@code cap + 44}）：上限抬到 256 后，写死 70 的旧桩件<b>根本不再越界</b>，
     * 那条分支会静默地不再被走到（用例照样绿，但什么都没钉）。
     */
    private static void distillOverCapCellLeavesReading() {
        final StubGate gate = new StubGate();
        final int over = PocketConstants.ESSENCE_CAP_PER_TAG + 44;
        // aer 一项就 over 点：TC 的 getBonusTags 在 capAspects(ret,64) 之后继续 add/merge（护甲/工具类）
        final ItemStack armor = stackDistill(1, 20);
        final ItemStack ore = stackDistill(1, 21);
        gate.putDistill(armor, TaumAspectAmounts.of(new String[] { "aer", "terra" }, new int[] { over, 4 }));
        gate.putDistill(ore, TaumAspectAmounts.of(new String[] { "metallum" }, new int[] { 6 }));

        final PocketDistillDriver.Batch only = PocketDistillDriver
            .planDistillBatch(new ItemStack[] { armor }, gate, new PocketEssenceStore());
        SimpleAssert.that(only.advanceable, "它确实含源质（不是'没东西可蒸'）");
        SimpleAssert.eq(Boolean.FALSE, only.accepted, "整格放不下 ⇒ 一格都不收");
        SimpleAssert.eq(Boolean.FALSE, only.needsRoom, "★不再冒充'需要空间'⇒ 不会被永久冻在满格");
        SimpleAssert.eq(Boolean.TRUE, only.overCap, "回报'超单格上限'这一可判定结论");
        SimpleAssert.eq(1, only.discardedGroups, "★读数单位是<b>格</b>：放弃了几格");
        SimpleAssert.eq(over + 4, only.discardedPoints, "放弃了几点（格内一起放弃，terra 那 4 点不截断入账）");
        SimpleAssert.eq(0, only.points, "零入账");
        SimpleAssert.eq(0, only.sourceSlots.length, "★一件都不吃（旧实现同样不吃，但当时没人知道为什么不动）");

        // 同一轮里另一格照常：超上限只作废自己那一格（旧实现里它把可蒸的 ore 一起拖成零产出）
        final PocketDistillDriver.Batch mixed = PocketDistillDriver
            .planDistillBatch(new ItemStack[] { armor, ore }, gate, new PocketEssenceStore());
        SimpleAssert.eq(1, mixed.sourceCount, "只有可蒸又可放的格参与本轮");
        SimpleAssert.eq(6, mixed.points, "另一格按原量入账");
        SimpleAssert.eq(Boolean.TRUE, mixed.accepted, "本轮推进");
        SimpleAssert.eq(Boolean.FALSE, mixed.needsRoom, "有建效 ⇒ 不停在满格");
        SimpleAssert.eq(Boolean.TRUE, mixed.overCap, "超上限那格仍被记下来（状态位与读数分开）");
        SimpleAssert.eq(1, mixed.discardedGroups, "读数：这轮放弃了 1 格");
        SimpleAssert.eq(over + 4, mixed.discardedPoints, "读数：放弃了 " + (over + 4) + " 点");
    }

    /**
     * ★R44c（R63b 改述：<b>容器不得进入蒸馏判定路径</b>）的可执行版本：
     * 桩件记录每一次 {@code aspectsOf} 调用，断言容器格<b>从未</b>被问过产物。
     * <p>
     * 这条判据的真实危害（S2 点出）：{@code getBonusTags} 会把栈内已有源质并入结果 ⇒
     * 满瓶进蒸馏 = 内容被当产出重复计入，甚至与"免蒸馏注入"成回路。分流器在入口就把它判给注入支。
     */
    private static void distillPathNeverSeesContainer() {
        final StubGate gate = new StubGate();
        final ItemStack ore = stack(4);
        final ItemStack vessel = stack(5);
        gate.putDistill(ore, TaumAspectAmounts.of(new String[] { "metallum" }, new int[] { 6 }));
        // 容器里"已有" 40 点：若它进了蒸馏判定，就会被当成产出重复计入
        gate.putContainer(vessel, TaumAspectAmounts.of(new String[] { "aquamen" }, new int[] { 40 }));

        SimpleAssert.that(gate.isContainer(vessel), "桩件认定 vessel 是容器");
        SimpleAssert
            .eq(PocketSlots.IncomingAction.INJECT, PocketSlots.classifyIncoming(vessel, gate), "有内容的容器 ⇒ 判给注入支（不是蒸馏）");
        SimpleAssert.eq(PocketSlots.IncomingAction.DISTILL, PocketSlots.classifyIncoming(ore, gate), "普通物品 ⇒ 判给蒸馏支");

        final PocketDistillDriver.Batch batch = PocketDistillDriver
            .planDistillBatch(new ItemStack[] { ore, vessel }, gate, new PocketEssenceStore());
        SimpleAssert.eq(0, gate.containerQueries, "★蒸馏判定从未问过任何容器的产物");
        SimpleAssert.eq(1, batch.sourceCount, "只有那枚普通物品参与本轮");
        SimpleAssert.eq(6, batch.points, "产量只有物品自身的 6 点，不含容器里的 40 点");
        SimpleAssert.eq(Boolean.FALSE, batch.candidates.containsKey("aquamen"), "容器内容没被重复计成产出");
        SimpleAssert.eq(0, gate.drainCalls, "蒸馏本轮不注入，所以 drainContainer 也不该被调");

        // 晶化源质（同样是容器）也走注入支，不开第二条路（R63b）
        final ItemStack crystal = stack(6);
        gate.putContainer(crystal, TaumAspectAmounts.single("ignis", 1));
        PocketDistillDriver.planDistillBatch(new ItemStack[] { crystal }, gate, new PocketEssenceStore());
        SimpleAssert.eq(0, gate.containerQueries, "晶化源质同样绝不进入蒸馏判定路径");
        SimpleAssert
            .eq(PocketSlots.IncomingAction.INJECT, PocketSlots.classifyIncoming(crystal, gate), "晶化源质入格 = 注入支（同一分流器）");
    }

    // ------------------------------------------------------------------ D 批 · R63 注入支

    /** 容器入 12 格 ⇒ 当场排空进源质表（原量、全有全无），容器本身非消耗（R40a）。 */
    private static void injectVesselDrainsIntoStore() {
        final StubGate gate = new StubGate();
        final ItemStack vessel = stack(7);
        gate.putContainer(vessel, TaumAspectAmounts.of(new String[] { "aer", "ignis" }, new int[] { 10, 5 }));
        final PocketEssenceStore store = new PocketEssenceStore();

        final PocketSlots.IntakeResult result = PocketSlots.injectContainer(vessel, store, gate);
        SimpleAssert.eq(PocketSlots.Intake.DRAINED, result.kind, "装得下 ⇒ 抽干并入账");
        SimpleAssert.eq(15, result.points, "原量合计 15 点");
        SimpleAssert.eq(1, gate.drainCalls, "抽干动作恰好一次");
        SimpleAssert.eq(10, store.get("aer"), "aer 入账");
        SimpleAssert.eq(5, store.get("ignis"), "ignis 入账");
        SimpleAssert.eq(
            PocketSlots.IncomingAction.REJECT,
            PocketSlots.classifyIncoming(vessel, gate),
            "排空后的容器再判一次 ⇒ REJECT（空容器不收，等玩家取走）");
        // 非消耗：容器还在玩家手里（注入执行只动源质，不动 stackSize，也不 return null）
        SimpleAssert.eq(7, vessel.stackSize, "容器 stackSize 未变（R40a 非消耗；退回动作由槽位侧执行）");
        // 已经空了的容器不得被反复"注入"出东西来
        final PocketSlots.IntakeResult twice = PocketSlots.injectContainer(vessel, store, gate);
        SimpleAssert.eq(PocketSlots.Intake.NOTHING, twice.kind, "空容器注入无事发生");
        SimpleAssert.eq(1, gate.drainCalls, "没有第二次抽干");
        SimpleAssert.eq(15, store.totalPoints(), "源质表没被重复入账");
    }

    /** ★R29 的注入侧：任一 tag 放不下 ⇒ 整瓶不抽（容器分毫未动，{@code still.inject_full}）。 */
    private static void injectFullStoreLeavesVesselUntouched() {
        final StubGate gate = new StubGate();
        final ItemStack vessel = stack(8);
        gate.putContainer(vessel, TaumAspectAmounts.of(new String[] { "aer", "ignis" }, new int[] { 10, 5 }));
        final PocketEssenceStore store = new PocketEssenceStore();
        store.add("ignis", PocketConstants.ESSENCE_CAP_PER_TAG); // ignis 到顶，但 aer 还有满格空间

        final PocketSlots.IntakeResult result = PocketSlots.injectContainer(vessel, store, gate);
        SimpleAssert.eq(PocketSlots.Intake.STORE_FULL, result.kind, "任一 tag 放不下 ⇒ 整轮判满");
        SimpleAssert.eq(0, result.points, "零入账");
        SimpleAssert.eq(0, gate.drainCalls, "★容器分毫未动（预检在抽取之前）");
        SimpleAssert.eq(
            15,
            gate.readContainer(vessel)
                .total(),
            "★内容也分毫未动（还能原样取走）");
        SimpleAssert.eq(0, store.get("aer"), "收得进的那格也不进（全有全无，不留半份）");
        SimpleAssert.eq(PocketConstants.ESSENCE_CAP_PER_TAG, store.get("ignis"), "原有值未被覆盖或截断（★按符号读，抬上限不得让这条假绿）");

        // 腾出空间后同一个容器即可注入（证明卡住的原因是"放不下"而不是"容器被拒"）
        store.extract("ignis", 5);
        final PocketSlots.IntakeResult after = PocketSlots.injectContainer(vessel, store, gate);
        SimpleAssert.eq(PocketSlots.Intake.DRAINED, after.kind, "腾出空间后注入成功");
        SimpleAssert.eq(15, after.points, "整份 15 点一次入账");
    }

    // ------------------------------------------------------------------ D 批 · R45b extract 两支

    /**
     * 流体支的三步算式：先问落点空间 → 按空间钳制请求 → 落不进的部分原路注回。
     * <p>
     * ★本用例覆盖的是流体支<b>唯一会静默吞流体</b>的那三个数字判断（纯算术）。
     * 如实声明边界：{@code Fluid}/{@code FluidRegistry} 在纯 JVM 里连类初始化都过不去
     * （实测 {@code ExceptionInInitializerError}，因此这里刻意不构造任何流体实例），
     * 而 AE2 handler 的 {@code extractItems}/{@code FluidStackTank.fill} 需要真实元件与网络
     * ⇒ 那两段属需玩家实机项（实验 E1/E3），本用例不声称已验证。
     */
    private static void extractFluidBranchMovesFluid() {
        // ①落点空间算式（PocketInventory#barRoom）：异种流体 ⇒ 0；同种 ⇒ 容量减现有量
        final int capacity = PocketConstants.FLUID_BAR_CAPACITY_ML;
        SimpleAssert.eq(capacity, PocketInventory.barRoom(capacity, 0, true), "空条 ⇒ 整条可收");
        SimpleAssert.eq(capacity - 1_000, PocketInventory.barRoom(capacity, 1_000, true), "已装 1000 ⇒ 按差值回报");
        SimpleAssert.eq(0, PocketInventory.barRoom(capacity, capacity, true), "条满 ⇒ 0 ⇒ 本拍根本不动");
        SimpleAssert.eq(0, PocketInventory.barRoom(capacity, 500, false), "★条内是别的流体 ⇒ 0（混装被拒）");
        SimpleAssert.eq(0, PocketInventory.barRoom(0, 0, true), "容量非正 ⇒ 0（不出现负空间）");

        // ②抽取前的钳制：拉取配额是"不设限"，实际请求量必须恰好等于落点空间
        SimpleAssert.eq(
            capacity,
            PocketAeChannelOps.fluidRequestFor(capacity, PocketConstants.REFILL_AMOUNT_PER_FILTER_UNBOUNDED),
            "先问落点再抽：请求量 = 落点空间");
        SimpleAssert.eq(500, PocketAeChannelOps.fluidRequestFor(500, 800), "有配额时取较小值");
        SimpleAssert.eq(0, PocketAeChannelOps.fluidRequestFor(0, 800), "没有空间 ⇒ 一个 mB 都不抽");
        SimpleAssert.eq(0, PocketAeChannelOps.fluidRequestFor(1_000, 0), "配额为 0 ⇒ 不抽");
        SimpleAssert.eq(0, PocketAeChannelOps.fluidRequestFor(-5, 800), "负空间（外来/异常入参）⇒ 0，不得变成'抽走 |room|'");

        // ③抽出后落不进的部分必须原路注回元件（这是流体支唯一会静默吞流体的地方）
        SimpleAssert.eq(0, PocketAeChannelOps.fluidFallback(500L, 500), "全部落下 ⇒ 无需退回");
        SimpleAssert.eq(200, PocketAeChannelOps.fluidFallback(500L, 300), "落进 300 ⇒ 退回 200");
        SimpleAssert.eq(500, PocketAeChannelOps.fluidFallback(500L, 0), "一格都没落 ⇒ 整份退回");
        SimpleAssert.eq(0, PocketAeChannelOps.fluidFallback(0L, 0), "什么都没抽 ⇒ 无退回");
        SimpleAssert.eq(0, PocketAeChannelOps.fluidFallback(-1L, 0), "异常入参 ⇒ 0（不造出负退回量）");
        SimpleAssert.eq(3, PocketAeChannelOps.fluidFallback(10L, 7), "差额按整数算，不吞零头");

        // ④三步串起来：剩余空间越小 ⇒ 请求越小 ⇒ 只要 moved<=request 就不可能吞件
        for (int room = 0; room <= 2_000; room += 250) {
            final int request = PocketAeChannelOps
                .fluidRequestFor(room, PocketConstants.REFILL_AMOUNT_PER_FILTER_UNBOUNDED);
            SimpleAssert.that(request <= room, "请求量不得超过落点空间（room=" + room + "）");
            final int moved = request / 2;
            final int back = PocketAeChannelOps.fluidFallback(request, moved);
            SimpleAssert.eq(request, moved + back, "落下的 + 退回的 必须恰好等于抽出的（room=" + room + "）");
        }

        // ⑤流体条内容的落档形状（关屏后必须还在；读写走 FluidStack 自身，不在此构造流体实例）
        final NBTTagCompound empty = new NBTTagCompound();
        PocketInventory.readFrom(null)
            .writeTo(empty);
        SimpleAssert
            .eq(Boolean.FALSE, empty.hasKey(PocketConstants.FLUID_BAR), "空条不落档（与 ITEM_CONTENTS 等同口径：空区一律 removeTag）");
    }

    /**
     * 源质支的物化换算：把"通道单位 ⇄ 晶化源质个数"这条纯算术钉住（1 点 = 1 晶，R31/R44e③），
     * 并断言落点侧（口袋中栏，ghost 格除外）真的收得下晶化源质。
     * <p>
     * 边界如实声明：AE2 第三方通道的 {@code convertStackFromItem}/{@code extractItems} 属实机项
     * （实验 E3：元件侧源质栈格式仍属该 mod 私有），本用例覆盖的是<b>换算与落点</b>，
     * 也就是"物化"两字里唯一会在纯 JVM 里算错的那一半。
     */
    private static void extractEssenceBranchYieldsCrystal() {
        SimpleAssert.eq(0, PocketEssenceChannelOps.crystalsFromUnits(0L, 1L, 64), "元件没有该 tag ⇒ 0 晶");
        SimpleAssert.eq(0, PocketEssenceChannelOps.crystalsFromUnits(5L, 0L, 64), "单位换算未知(0) ⇒ 不猜，返回 0");
        SimpleAssert.eq(0, PocketEssenceChannelOps.crystalsFromUnits(5L, 1L, 0), "本轮不要 ⇒ 0");
        SimpleAssert.eq(5, PocketEssenceChannelOps.crystalsFromUnits(5L, 1L, 64), "零头不足整晶时按可得给");
        SimpleAssert.eq(64, PocketEssenceChannelOps.crystalsFromUnits(1_000L, 1L, 64), "受单批上限自缚（一整堆晶）");
        SimpleAssert.eq(
            12,
            PocketEssenceChannelOps.crystalsFromUnits(100L, 8L, 64),
            "该通道 1 晶 = 8 单位时：100 单位只够 12 晶，余 4 单位留在元件侧（向下取整 = 不销毁价值）");
        SimpleAssert.eq(96L, PocketEssenceChannelOps.unitsForCrystals(12, 8L), "逆运算与取整口径一致");
        SimpleAssert.eq(0L, PocketEssenceChannelOps.unitsForCrystals(0, 8L), "非正数一律 0");
        // 取整后再回乘，必须永远不超过可用量（这是"抽了放不下"的唯一防线）
        for (long available = 0; available < 200; available++) {
            for (long unit = 1; unit <= 8; unit++) {
                final int crystals = PocketEssenceChannelOps.crystalsFromUnits(available, unit, 64);
                SimpleAssert.that(
                    PocketEssenceChannelOps.unitsForCrystals(crystals, unit) <= available,
                    "换算不得超出元件可得量：available=" + available + " unit=" + unit);
            }
        }

        // 落点侧：晶化源质进口袋中栏，ghost 格不算落点，装满后必须回报 0（调用方据此退回源侧）
        final PocketInventory inventory = PocketInventory.readFrom(null);
        final ItemStack crystals = stack(9);
        crystals.stackSize = 30;
        final int crystalId = Item.getIdFromItem(crystals.getItem());
        final PocketFilterConfig ghostZero = new PocketFilterConfig();
        ghostZero.add(0, item(0, crystalId, 0, ""));
        inventory.replaceFilters(ghostZero);
        SimpleAssert.that(inventory.isGhostItemSlot(0), "第 0 格已被就地转成配置格");
        SimpleAssert.eq(30, inventory.depositIntoStorage(crystals.copy()), "别的格仍是落点");
        SimpleAssert.eq(null, inventory.storageStack(0), "★配置格里一个产物都没落（R38 第 2 条'产物不能进自己'）");
        SimpleAssert.eq(30, inventory.storageStack(1).stackSize, "落到第一个非 ghost 空槽");
        SimpleAssert.that(!inventory.isGhostItemSlot(1), "第 1 格仍是真实格");

        final PocketInventory allGhost = PocketInventory.readFrom(null);
        final PocketFilterConfig everywhere = new PocketFilterConfig();
        for (int index = 0; index < PocketInventory.STORAGE_SLOTS; index++) {
            everywhere.add(index, item(index, 1, 0, ""));
        }
        allGhost.replaceFilters(everywhere);
        SimpleAssert
            .eq(0, allGhost.depositIntoStorage(crystals.copy()), "全栏都是配置格 ⇒ 回报 0 个都没放下（调用方据此发 TARGET_FULL 并回滚源侧）");
        // 一格真实容量换一条声明：128 是上限口径，转 N 格 ghost 就只剩 128−N 格能装东西（R38 第 3 条）
        final PocketInventory oneReal = PocketInventory.readFrom(null);
        final PocketFilterConfig allButOne = new PocketFilterConfig();
        for (int index = 1; index < PocketInventory.STORAGE_SLOTS; index++) {
            allButOne.add(index, item(index, 1, 0, ""));
        }
        oneReal.replaceFilters(allButOne);
        SimpleAssert
            .eq(64, oneReal.depositIntoStorage(stackOf(crystalId, 64 + 30)), "只剩 1 格真实容量 ⇒ 一次只收得下一堆（64），其余 30 个由调用方退回");
        SimpleAssert.eq(64, oneReal.storageStack(0).stackSize, "那一格收满 64");
    }

    private static ItemStack stackOf(int itemId, int size) {
        final Item item = Item.getItemById(itemId);
        final ItemStack stack = new ItemStack(item == null ? FakePlainItem.INSTANCE : item, size, 0);
        return stack;
    }

    // ------------------------------------------------------------------ 元件位置探针（推送式观测接线）

    /**
     * R42 的接线语义：元件被宿主容器取用 → {@code observe} 上报 → {@code locationOf} 给出
     * {@code Located{dim,x,y,z,slot}} → 把栈从宿主移除后 {@code stillPresent()} 转 false。
     * <p>
     * 槽号是 {@code PocketCellProbe} 侧扫宿主的 {@code IInventory} 比对 {@code disksuuid} 得出的
     * （自家 {@code ICellHandler} 的 {@code getCellInventory} 参数里没有槽号），因此这里刻意传
     * {@link PocketCellProbe#SLOT_UNKNOWN} 并同时验一条显式给槽号的路径。
     * <p>
     * 世界触点由 {@link PocketCellProbe.HostResolver} 的假实现顶掉（纯 JVM 拿不到真 World），
     * 其余判定结构与生产实现走的是同一段代码。
     */
    private static void probeObserveLocateThenGone() {
        PocketCellProbe.INSTANCE.reset();
        try {
            final FakeHost host = new FakeHost();
            final PocketCellProbe probe = PocketCellProbe.INSTANCE;
            final ItemStack cell = cellStack(CELL_A);
            host.stacks[3] = cell;
            probe.useHostResolverForTest(host);

            // 1) 未上报前：没有任何记录，也不得误判为"已消失"（UNKNOWN ⇒ stillPresent 为 true）
            SimpleAssert.eq(null, probe.locationOf(CELL_A), "未观测到 ⇒ 无位置记录");
            SimpleAssert.eq(0, probe.trackedCells(), "注册表初始为空");

            // 2) 宿主上报（槽号未知，由探针扫槽得出）
            probe.observe(cell, host, PocketCellProbe.SLOT_UNKNOWN);
            final PocketCellProbe.Located located = probe.locationOf(CELL_A);
            SimpleAssert.that(located != null, "观测后必须有位置");
            SimpleAssert.eq(FakeHost.DIM, located.dim, "维度");
            SimpleAssert.eq(FakeHost.AT_X, located.x, "x");
            SimpleAssert.eq(FakeHost.AT_Y, located.y, "y");
            SimpleAssert.eq(FakeHost.AT_Z, located.z, "z");
            SimpleAssert.eq(3, located.slot, "槽号必须由扫槽得出（handler 参数里没有槽号）");
            SimpleAssert.that(probe.isResolvable(CELL_A), "在槽 ⇒ 写入通路可解析");
            SimpleAssert.that(probe.stillPresent(CELL_A), "在槽 ⇒ stillPresent 为真");
            SimpleAssert.eq(cell, probe.cellAt(CELL_A), "回读到的必须是宿主槽内那枚栈本体");
            SimpleAssert.that(probe.foundAt(CELL_A) != null, "foundAt 必须给出栈 + 宿主");

            // 3) 显式给槽号时以入参为准（未来若有别的宿主能直接报槽号）
            probe.observe(cell, host, 5);
            SimpleAssert.eq(5, probe.locationOf(CELL_A).slot, "显式槽号优先");
            probe.observe(cell, host, PocketCellProbe.SLOT_UNKNOWN);
            SimpleAssert.eq(3, probe.locationOf(CELL_A).slot, "再次扫槽会校正回真实槽位");

            // 4) 元件从宿主移除 ⇒ 确认 GONE ⇒ stillPresent 转 false（绑定不删，但本轮拒绝传输）
            host.stacks[3] = null;
            SimpleAssert.eq(Boolean.FALSE, probe.stillPresent(CELL_A), "从宿主移除后必须转 false");
            SimpleAssert.eq(Boolean.FALSE, probe.isResolvable(CELL_A), "移除后写入通路不可解析");
            SimpleAssert.eq(null, probe.foundAt(CELL_A), "移除后 foundAt 为 null");
            SimpleAssert.that(probe.isTracked(CELL_A), "位置记录仍在（只是元件不在了，由通道侧决定后续）");

            // 4b) 同一格换上了别的元件 ⇒ 身份不符，原身份同样判 GONE
            host.stacks[3] = cellStack(CELL_B);
            SimpleAssert.eq(Boolean.FALSE, probe.stillPresent(CELL_A), "槽内换了别的身份 ⇒ 原身份判 GONE");
            host.stacks[3] = null;

            // 5) 区块未加载 ⇒ "无法判定"：既不判丢失也不判可解析
            host.stacks[3] = cell;
            probe.observe(cell, host, PocketCellProbe.SLOT_UNKNOWN);
            host.chunkLoaded = false;
            SimpleAssert.that(probe.stillPresent(CELL_A), "区块未加载算无法判定，不删绑定");
            SimpleAssert.eq(Boolean.FALSE, probe.isResolvable(CELL_A), "区块未加载时不得声称可解析");
            host.chunkLoaded = true;

            // 6) 门禁：container 为 null / 不是 TileEntity / 非元件栈 都不得写入注册表
            probe.observe(cell, null, PocketCellProbe.SLOT_UNKNOWN);
            probe.observe(cell, new Object(), PocketCellProbe.SLOT_UNKNOWN);
            probe.observe(plainStack(), host, PocketCellProbe.SLOT_UNKNOWN);
            SimpleAssert.eq(1, probe.trackedCells(), "三条非法上报都必须被跳过（注册表仍只有 CELL_A）");
            probe.forget(CELL_A);
            SimpleAssert.eq(0, probe.trackedCells(), "forget 后注册表清空");
        } finally {
            PocketCellProbe.INSTANCE.reset();
        }
    }

    // ------------------------------------------------------------------ 常量与键名

    private static void nbtKeyLiteralsUnchanged() {
        SimpleAssert.eq("boundCells", PocketConstants.BOUND_CELLS, "绑定表外层键名");
        SimpleAssert.eq("id", PocketConstants.ENTRY_ID, "绑定条目身份键名");
        SimpleAssert.eq("mode", PocketConstants.ENTRY_MODE, "绑定条目模式键名（逐条，不是表级）");
        SimpleAssert.eq("dim", PocketConstants.ENTRY_DIM, "绑定条目维度快照键名");
        SimpleAssert.eq("x", PocketConstants.ENTRY_X, "绑定条目 x 快照键名");
        SimpleAssert.eq("y", PocketConstants.ENTRY_Y, "绑定条目 y 快照键名");
        SimpleAssert.eq("z", PocketConstants.ENTRY_Z, "绑定条目 z 快照键名");
        SimpleAssert.eq("slotIndex", PocketConstants.ENTRY_SLOT, "绑定条目槽号快照键名");
        SimpleAssert.eq(Integer.MIN_VALUE, PocketConstants.UNLOCATED, "未定位哨兵必须是 Integer.MIN_VALUE");
        SimpleAssert.eq(-2147483648, PocketConstants.UNLOCATED, "哨兵字面值（R39c/R40a 定稿）");
        SimpleAssert.eq("ess", PocketConstants.ESSENCE, "源质表键名");
        SimpleAssert.eq("Aspects", PocketConstants.ASPECTS, "源质列表键名（TC AspectList 同名字面量）");
        SimpleAssert.eq("key", PocketConstants.ASPECT_KEY, "源质条目 tag 键名（TC AspectList 同名）");
        SimpleAssert.eq("amount", PocketConstants.ASPECT_AMOUNT, "源质条目点数键名（TC AspectList 同名）");
        SimpleAssert.eq("lastBurstAtMs", PocketConstants.LAST_BURST_AT_MS, "设备维冷却键名");
        SimpleAssert.eq("filters", PocketConstants.FILTERS, "配置过滤器键名");
        SimpleAssert.eq("slotIndex", PocketConstants.FILTER_SLOT, "ghost 被转换槽索引键名（R38 第 1 条）");
        SimpleAssert.eq(-1, PocketConstants.FILTER_SLOT_UNSET, "ghost 槽索引未设置标记（不得用 0）");
        SimpleAssert.eq("typeId", PocketConstants.FILTER_TYPE_ID, "源质通道 typeId 键名（字符串 id，不是索引）");
        SimpleAssert.eq("tag", PocketConstants.FILTER_TAG, "aspect tag 键名");
        SimpleAssert.eq(
            PocketConstants.SHORT_CHANNEL_SECONDS * PocketConstants.CHANNEL_TICK_PERIOD,
            PocketConstants.SHORT_CHANNEL_BATCHES * PocketConstants.CHANNEL_TICK_PERIOD,
            "短效通道总时长 = 30 批 × 20 tick = 600 tick = 30 秒");
        SimpleAssert.eq((byte) 0, PocketConstants.MODE_DISK_UUID, "diskuuid 模式为 0（MODE_POSITION 只预留不实现）");
        SimpleAssert.eq(5_000L, PocketConstants.BURST_ANIMATION_MS, "瞬时动画显示窗口 5 秒");
        SimpleAssert.eq(10, PocketConstants.BURST_COOLDOWN_SECONDS, "瞬时通道冷却 10 秒");
        SimpleAssert.eq(
            135,
            PocketConstants.GHOST_ITEM_SLOT_LIMIT,
            "中栏 ghost 白名单上界 = 15 行 × 9 列 = 135（★R80① 定稿，覆盖 R75 的 10 列 = 150）");
        SimpleAssert.eq(9, PocketConstants.STORAGE_COLUMNS, "中栏列数（R80①：10 → 9）");
        SimpleAssert.eq(15, PocketConstants.STORAGE_ROWS, "中栏行数（R80① 未动，纵向 360 是硬天花板）");
        SimpleAssert.eq(
            PocketConstants.STORAGE_ROWS * PocketConstants.STORAGE_COLUMNS,
            PocketConstants.GHOST_ITEM_SLOT_LIMIT,
            "★ghost 上界由行列常量派生（改列数忘改上界 = 这一条红）");
        // ---- R78 的白名单上界（★全部断言读常量，不抄面板字面量）----
        SimpleAssert.eq(18, PocketConstants.GHOST_FLUID_SLOT_LIMIT, "流体槽 ghost 白名单上界 = tank 总数（R78②：3 组 × 6 列）");
        SimpleAssert.eq(18, PocketConstants.FLUID_TANK_TOTAL, "tank 总数 = 组数 × 每组列数（派生自常量，不得各写一个 18）");
        SimpleAssert.eq(3, PocketConstants.FLUID_GROUP_COUNT, "流体组数 = 3（R78②）");
        SimpleAssert.eq(6, PocketConstants.FLUID_COLUMN_COUNT, "每组流体列数（R75①：6 列，每列 in/fluid/out）");
        SimpleAssert.eq(72, PocketConstants.GHOST_ESSENCE_SLOT_LIMIT, "源质 ghost 白名单上界 = 12 行 × 6 列 = 72（R78②③）");
        SimpleAssert.eq(72, PocketConstants.ESSENCE_DISPLAY_GRID, "显示格数与白名单上界同源（★旧 48 不得留在任何一处）");
        SimpleAssert.eq(288_000_000, PocketConstants.FLUID_TOTAL_CAPACITY_ML, "★18 槽合计 288,000,000 mB（R78② 的双处声明数）");
        SimpleAssert.eq(36, PocketConstants.PLAYER_BACKPACK_SLOTS, "★R78①：面板内玩家背包 36 格（9×4，代价 = E4 包放大）");
        SimpleAssert
            .eq(16_000_000, PocketConstants.FLUID_BAR_CAPACITY_ML, "★单流体槽容量 16,000,000 mB（R75 规格外自立项，须双处对玩家声明）");
        SimpleAssert.eq(128, PocketConstants.MAX_ROTATION_ENTRIES, "★轮转表清理阈值仍是 128：它与格数无关，是 R75 点名的批量替换豁免项");
        SimpleAssert.that(
            PocketConstants.MAX_ROTATION_ENTRIES != PocketConstants.GHOST_ITEM_SLOT_LIMIT,
            "轮转阈值与中栏格数<b>必须</b>不相等（相等就说明有人把 128 一起替换了）");
    }

    // ================================================================== S-U3（R75）/ S-U4（R78）批次
    //
    // 这一批是"128/149 → 150/175 → R78 的 235 → R80 的 220"这次批量口径重做的验收面。
    // 每条都刻意写成"改坏了就一定红"的形式：加总用字面量、行列用乘积、兼容用两代形状各自断言。

    /** R78→R80 的 220 加总自证 + 全部行列乘积（★数字来自任务包 §1.1，逐条按加算式核对）。 */
    private static void slotMathAndProducts() {
        // ★加总（R80①）：中栏 135（15×9）+ 流体交互 36（3 组 × 6 列 × 进/出）+ 蒸馏 12 + 绑定 1
        // + 玩家背包 36（框架绑的 9×4） = 220
        SimpleAssert.eq(220, PocketSlots.TOTAL_REAL_SLOTS, "真实 Container 槽总数（R80：184 工厂 + 36 背包）");
        SimpleAssert.eq(184, PocketSlots.FACTORY_REAL_SLOTS, "本工厂产出口径 = 135 + 36 + 12 + 1 = 184（★回执要求的 184 加总自证就是这一条）");
        SimpleAssert.eq(
            135 + 36 + 12 + 1 + 36,
            PocketInventory.STORAGE_SLOTS + PocketInventory.FLUID_INTERACTION_SLOTS
                + PocketInventory.DISTILL_INPUT_SLOTS
                + PocketInventory.BIND_SLOTS
                + PocketConstants.PLAYER_BACKPACK_SLOTS,
            "五块加总必须等于 220（235 / 175 / 185 / 149 都是被覆盖的旧口径）");
        SimpleAssert.eq(135, PocketInventory.STORAGE_SLOTS, "中栏格数 = GHOST_ITEM_SLOT_LIMIT 单源（R80①：150 → 135）");
        SimpleAssert.eq(15, PocketSlots.STORAGE_ROWS, "中栏 15 行（★R80① 纵向一行不删：360 是 GUI Scale 3 上限）");
        SimpleAssert.eq(9, PocketSlots.STORAGE_COLUMNS, "★中栏 9 列（R80① 定稿，旧 10 列）");
        SimpleAssert.eq(
            PocketInventory.STORAGE_SLOTS,
            PocketSlots.STORAGE_ROWS * PocketSlots.STORAGE_COLUMNS,
            "行数 × 列数必须等于格数（矩阵与常量不得半改）");
        // ---- 流体：三级乘积（组 × 列 × 进/出），全部派生自 PocketConstants ----
        SimpleAssert.eq(3, PocketConstants.FLUID_GROUP_COUNT, "流体组数 = 3（R78②）");
        SimpleAssert.eq(6, PocketConstants.FLUID_COLUMN_COUNT, "每组流体列数 = 6（R75①，R78 未改）");
        SimpleAssert.eq(18, PocketConstants.FLUID_TANK_TOTAL, "tank 总数 = 组数 × 列数 = 18（★派生自常量，不是字面量）");
        SimpleAssert.eq(18, PocketConstants.GHOST_FLUID_SLOT_LIMIT, "Kind.FLUID 的索引空间 = tank 总数（同源）");
        SimpleAssert.eq(
            PocketConstants.FLUID_GROUP_COUNT * PocketConstants.FLUID_COLUMN_COUNT
                * PocketConstants.FLUID_INTERACTION_PER_COLUMN,
            PocketInventory.FLUID_INTERACTION_SLOTS,
            "流体交互格 = 组数 × 列数 × 每列格数（三级乘积）");
        SimpleAssert.eq(36, PocketInventory.FLUID_INTERACTION_SLOTS, "流体交互格 36（旧 12 作废）");
        SimpleAssert.eq(12, PocketInventory.DISTILL_INPUT_SLOTS, "蒸馏输入仍是 12 格（R78 只下移位置）");
        SimpleAssert.eq(1, PocketInventory.BIND_SLOTS, "绑定格仍 1");
        // ---- 源质：6 × 12 = 72（R78②③）----
        SimpleAssert.eq(
            PocketConstants.ESSENCE_DISPLAY_GRID,
            NekoPocketEssenceColumn.ESSENCE_COLUMNS * NekoPocketEssenceColumn.ESSENCE_ROWS,
            "源质盘 6 × 12 = 72");
        SimpleAssert.eq(6, NekoPocketEssenceColumn.ESSENCE_COLUMNS, "源质盘列数与流体块同列数（规整由列数对齐达成）");
        SimpleAssert.eq(12, NekoPocketEssenceColumn.ESSENCE_ROWS, "源质盘 12 行（R78②）");
        SimpleAssert.eq(72, PocketConstants.ESSENCE_DISPLAY_GRID, "★格数 72 ≥ 用户包内实测 aspect 注册数 69（溢出兜底因此常态不触发，但代码路径保留）");
        SimpleAssert.eq(
            PocketConstants.ESSENCE_DISPLAY_GRID,
            PocketConstants.GHOST_ESSENCE_SLOT_LIMIT,
            "Kind.ESSENCE 的索引空间与显示格数同源（不留 48 的第二份真相）");
        SimpleAssert.eq(
            PocketConstants.ESSENCE_DISPLAY_GRID,
            PocketConstants.ESSENCE_OUT_SHIFT_FLAG,
            "ESSENCE_OUT 的 Shift 偏移 = 格数（arg 空间：0…71 与 72…143，都小于 ACTION_ARG_BASE=1024）");
        SimpleAssert.eq(
            PocketInventory.DISTILL_INPUT_SLOTS,
            NekoPocketEssenceColumn.DISTILL_COLUMNS * NekoPocketEssenceColumn.DISTILL_ROWS,
            "蒸馏盘 6 × 2 = 12");
        SimpleAssert.that(
            PocketConstants.ESSENCE_OUT_SHIFT_FLAG + PocketConstants.ESSENCE_DISPLAY_GRID < 1024,
            "格号 + Shift 偏移的最大 arg 必须小于动作参数基数 1024");
        // ---- 玩家背包（R78①）----
        SimpleAssert.eq(36, PocketConstants.PLAYER_BACKPACK_SLOTS, "框架绑定的背包格数 = 9 × 4（★与面板画的格数同源，否则出现隐形槽）");
        SimpleAssert.eq(36, PocketSlots.PLAYER_BACKPACK_SLOTS, "PocketSlots 侧的背包口径必须与常量一致（不另立数字）");
        SimpleAssert.eq(
            PocketConstants.PLAYER_BACKPACK_COLUMNS * PocketConstants.PLAYER_BACKPACK_ROWS,
            PocketConstants.PLAYER_BACKPACK_SLOTS,
            "背包格数 = 列 × 行（派生）");
        // ---- tank 映射：组号 × 列数 + 组内列号（★含"旧 12 格档同解"这条兼容判据）----
        SimpleAssert.eq(0, PocketInventory.tankOfInteractionSlot(0), "交互格 0 → tank 0（组 0 进格首）");
        SimpleAssert.eq(5, PocketInventory.tankOfInteractionSlot(5), "交互格 5 → tank 5（组 0 进格末）");
        SimpleAssert.eq(0, PocketInventory.tankOfInteractionSlot(6), "交互格 6 → tank 0（组 0 出格首，与旧取模同解）");
        SimpleAssert.eq(5, PocketInventory.tankOfInteractionSlot(11), "交互格 11 → tank 5（组 0 出格末）");
        SimpleAssert.eq(6, PocketInventory.tankOfInteractionSlot(12), "★交互格 12 → tank 6（组 1 进格首，不再折回 0）");
        SimpleAssert.eq(17, PocketInventory.tankOfInteractionSlot(35), "交互格 35 → tank 17（全局末格）");
        SimpleAssert.eq(0, PocketInventory.groupOfInteractionSlot(11), "组 0 的出格仍属组 0");
        SimpleAssert.eq(1, PocketInventory.groupOfInteractionSlot(12), "索引 12 起是第 2 组");
        SimpleAssert.eq(2, PocketInventory.groupOfInteractionSlot(24), "索引 24 起是第 3 组");
        SimpleAssert.eq(Boolean.FALSE, PocketInventory.isLowerInteractionRow(0), "索引 0 是本组上行（进格）");
        SimpleAssert.eq(Boolean.FALSE, PocketInventory.isLowerInteractionRow(5), "索引 5 是本组上行末格");
        SimpleAssert.that(PocketInventory.isLowerInteractionRow(6), "索引 6 是本组下行（出格）");
        SimpleAssert.eq(Boolean.FALSE, PocketInventory.isLowerInteractionRow(17), "索引 17 是组 1 的上行末格");
        SimpleAssert.that(PocketInventory.isLowerInteractionRow(18), "索引 18 是组 1 的下行首格");
        // 全部 36 格各自映射到的 tank 必须两两不重复覆盖 0…17 恰好两次（进 + 出）
        final int[] tankHits = new int[PocketConstants.FLUID_TANK_TOTAL];
        for (int index = 0; index < PocketInventory.FLUID_INTERACTION_SLOTS; index++) {
            final int tank = PocketInventory.tankOfInteractionSlot(index);
            SimpleAssert.that(tank >= 0 && tank < tankHits.length, "交互格 " + index + " 必须映射到合法 tank");
            tankHits[tank]++;
        }
        for (int tank = 0; tank < tankHits.length; tank++) {
            SimpleAssert.eq(2, tankHits[tank], "每个 tank 恰好被两个交互格指向（进格 + 出格，两格同权）");
        }
        SimpleAssert.that(PocketInventory.isValidTank(17), "tank 17 合法（上界是 tank 总数）");
        SimpleAssert.eq(Boolean.FALSE, PocketInventory.isValidTank(18), "tank 18 越界");
        SimpleAssert.eq(Boolean.FALSE, PocketInventory.isValidTank(-1), "tank -1 越界");
        // ★布局字符的出现总数必须等于 handler 格数：MUI2 的 builder 是<b>按字符</b>各自从 0 计数
        // （Char2IntOpenHashMap，字节码实证）⇒ 一块矩阵出现第二个布局字符就会把索引空间劈成
        // 两条重叠的 0…n（本仓真踩到过一次，R77 记实）
        SimpleAssert.eq(135, NekoPocketStorageColumn.layoutSlotCount(), "中栏矩阵产出 135 格（15 × 9）");
        SimpleAssert.eq(36, NekoPocketLeftColumn.layoutSlotCount(), "流体交互矩阵产出 36 格（单一布局字符）");
        SimpleAssert
            .eq(14, NekoPocketLeftColumn.layoutRowCount(), "流体矩阵 14 行 = 3 组 × 4 行 + 2 个组间空行（R78② 的 252 = 14×18）");
        SimpleAssert.eq(12, NekoPocketEssenceColumn.layoutSlotCount(), "蒸馏矩阵产出 12 格");
        SimpleAssert.eq(1, NekoPocketBottomBand.layoutSlotCount(), "绑定格矩阵产出 1 格");
        SimpleAssert.eq(
            36,
            NekoPocketBottomBand.backpackLayoutSlotCount(),
            "★背包矩阵产出 36 格 = 框架那 36 格（不等就会有背包格只存在于 Container 而看不见）");
        SimpleAssert.eq(
            PocketSlots.FACTORY_REAL_SLOTS,
            NekoPocketStorageColumn.layoutSlotCount() + NekoPocketLeftColumn.layoutSlotCount()
                + NekoPocketEssenceColumn.layoutSlotCount()
                + NekoPocketBottomBand.layoutSlotCount(),
            "四块矩阵的产出之和必须恰好等于工厂口径 184（R80①）");
        SimpleAssert.eq(
            PocketSlots.TOTAL_REAL_SLOTS,
            PocketSlots.FACTORY_REAL_SLOTS + NekoPocketBottomBand.backpackLayoutSlotCount(),
            "工厂四块 + 背包 = 容器口径 220（★背包不经工厂，所以必须单列一项）");
    }

    /**
     * 装配计数断言的正反两面（R80①）：184 + 36 = <b>220</b> 必须过、<b>219 与 221 必须抛</b>。
     * <p>
     * ★三条判据都要（见 {@code PocketSlots#assertTotalRealSlots(int, int)} 的注释）：
     * 只判合计的话"工厂少一格 + 背包多一格"会抵消成 220 而静默放过。
     * 异常文本也必须带上新口径（且由常量拼出而不是再抄一份数），否则读到"应为 199 个 (150 …)"
     * 就是把玩家指向一个已经不存在的形状。
     */
    private static void realSlotCountAssertion() {
        final PocketInventory inventory = PocketInventory.readFrom(null);

        final PocketSlots full = new PocketSlots();
        for (int index = 0; index < PocketInventory.STORAGE_SLOTS; index++) {
            full.storage(inventory, index);
        }
        for (int index = 0; index < PocketInventory.FLUID_INTERACTION_SLOTS; index++) {
            full.fluidInteraction(inventory, index);
        }
        for (int index = 0; index < PocketInventory.DISTILL_INPUT_SLOTS; index++) {
            full.distillInput(inventory, index);
        }
        full.bind(inventory);
        SimpleAssert.eq(184, full.createdRealSlots(), "四类槽工厂各按格数接完正好 184（★背包不经这里）");
        full.assertTotalRealSlots();

        // ---- 负控 219 / 221：直接喂静态判据（不必真造两百个槽）----
        SimpleAssert.eq(Boolean.FALSE, throwsIllegalState(184, 36), "184 + 36 = 220 ⇒ 不抛（正例基线）");
        SimpleAssert.that(throwsIllegalState(183, 36), "★219 必须抛（工厂少一格 = 有区域漏接）");
        SimpleAssert.that(throwsIllegalState(185, 36), "★221 必须抛（工厂多一格 = 有区域重复接入）");
        SimpleAssert.that(throwsIllegalState(184, 35), "★219 的另一半：背包少一格也必须抛（隐形槽不算过关）");
        SimpleAssert.that(throwsIllegalState(184, 37), "★221 的另一半：背包多一格同样抛");
        SimpleAssert.that(throwsIllegalState(183, 37), "★合计恰好 220 但两项各自都错 ⇒ 仍必须抛（只判合计就会放过这一类）");

        // ---- 少接一格的实例路径（装配里真少造一个槽）----
        final PocketSlots shortByOne = new PocketSlots();
        for (int index = 0; index < PocketInventory.STORAGE_SLOTS - 1; index++) {
            shortByOne.storage(inventory, index);
        }
        for (int index = 0; index < PocketInventory.FLUID_INTERACTION_SLOTS; index++) {
            shortByOne.fluidInteraction(inventory, index);
        }
        for (int index = 0; index < PocketInventory.DISTILL_INPUT_SLOTS; index++) {
            shortByOne.distillInput(inventory, index);
        }
        shortByOne.bind(inventory);
        SimpleAssert.eq(183, shortByOne.createdRealSlots(), "故意少接一格 ⇒ 183");
        SimpleAssert.that(throwsIllegalState(shortByOne), "★实例路径也要抛");
        SimpleAssert
            .eq(Boolean.FALSE, messageOf(shortByOne).contains("175"), "异常文本不得再引用旧口径 175：" + messageOf(shortByOne));
        SimpleAssert
            .eq(Boolean.FALSE, messageOf(shortByOne).contains("12 流体交互"), "异常文本不得再引用 12 流体交互：" + messageOf(shortByOne));
        SimpleAssert.that(messageOf(shortByOne).contains("184"), "异常文本必须给出新的工厂口径 184：" + messageOf(shortByOne));
        SimpleAssert.that(messageOf(shortByOne).contains("36"), "异常文本必须给出流体交互 36（3 组×6×2）");
        SimpleAssert.that(messageOf(shortByOne).contains("135"), "异常文本必须给出中栏 135：" + messageOf(shortByOne));
        // ★异常文本本身也得是派生式：把旧口径钉成"不得出现"，否则改常量不改文案会静默指错形状
        SimpleAssert
            .eq(Boolean.FALSE, messageOf(shortByOne).contains("150"), "异常文本不得再出现旧口径 150：" + messageOf(shortByOne));
        SimpleAssert
            .eq(Boolean.FALSE, messageOf(shortByOne).contains("199"), "异常文本不得再出现旧口径 199：" + messageOf(shortByOne));

        final PocketSlots extra = new PocketSlots();
        for (int index = 0; index < PocketInventory.STORAGE_SLOTS + 1; index++) {
            extra.storage(inventory, index % PocketInventory.STORAGE_SLOTS);
        }
        SimpleAssert.that(throwsIllegalState(reseed(extra, inventory)), "★185 也要抛（重复接入同样炸）");
    }

    /** 再补三类槽，使计数恰好 185（多接一格）。 */
    private static PocketSlots reseed(PocketSlots slots, PocketInventory inventory) {
        for (int index = 0; index < PocketInventory.FLUID_INTERACTION_SLOTS; index++) {
            slots.fluidInteraction(inventory, index);
        }
        for (int index = 0; index < PocketInventory.DISTILL_INPUT_SLOTS; index++) {
            slots.distillInput(inventory, index);
        }
        slots.bind(inventory);
        slots.bind(inventory);
        return slots;
    }

    private static boolean throwsIllegalState(PocketSlots slots) {
        try {
            slots.assertTotalRealSlots();
        } catch (IllegalStateException expected) {
            return true;
        }
        return false;
    }

    /** 直接喂静态判据的负控（★234 / 236 两条就是走这里，不必真造两百个槽）。 */
    private static boolean throwsIllegalState(int factorySlots, int playerSlots) {
        try {
            PocketSlots.assertTotalRealSlots(factorySlots, playerSlots);
        } catch (IllegalStateException expected) {
            return true;
        }
        return false;
    }

    private static String messageOf(PocketSlots slots) {
        try {
            slots.assertTotalRealSlots();
        } catch (IllegalStateException expected) {
            return String.valueOf(expected.getMessage());
        }
        return "";
    }

    /**
     * 流体 ghost 的索引空间 0…17 逐个可用、18 越界；★并钉住"旧档的 FLUID:0 与 FLUID:5 仍然有效"。
     * <p>
     * 旧口径（R75）下合法值是 0…5，R78② 扩到 0…17 ⇒ 必须是<b>只增</b>：原来那 6 条声明在新读法
     * 下解出来还是同一个 tank（尤其 tank 0 与 tank 5），不得被重排或失效。
     */
    private static void fluidGhostIndexSpaceSixColumns() {
        final int tanks = PocketConstants.FLUID_TANK_TOTAL;
        SimpleAssert.eq(18, tanks, "本用例的前提：tank 总数是 18（3 组 × 6 列）");
        final PocketFilterConfig config = new PocketFilterConfig();
        for (int tank = 0; tank < tanks; tank++) {
            SimpleAssert.eq(
                PocketGhostRequest.Outcome.APPLIED,
                set(config, tank, PocketFilterConfig.fluidKey("water" + tank)).outcome,
                "流体槽第 " + tank + " 格应可声明（上界 18）");
        }
        SimpleAssert.eq(18, config.size(), "十八列各一条，互不覆盖");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            set(config, tanks, PocketFilterConfig.fluidKey("water18")).outcome,
            "第 18 格越界 ⇒ 拒收");
        // 旧档键：FLUID|0 与 FLUID|5（R75 的合法边界）读回来还在同一个 tank 上
        final PocketFilterConfig legacy = new PocketFilterConfig();
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            set(legacy, 0, PocketFilterConfig.fluidKey("lava")).outcome,
            "索引 0 合法（旧 FLUID:0 键的形状）");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            set(legacy, PocketConstants.FLUID_COLUMN_COUNT - 1, PocketFilterConfig.fluidKey("oil")).outcome,
            "索引 5 合法（旧口径的上界那一格）");
        SimpleAssert.eq(
            0,
            legacy.at(Kind.FLUID, 0)
                .slotIndex(),
            "旧 FLUID:0 解出来的槽号仍是 0");
        SimpleAssert.eq(
            5,
            legacy.at(Kind.FLUID, 5)
                .slotIndex(),
            "★旧 FLUID:5 解出来的槽号仍是 5（扩组只往后加，不重编号）");
        SimpleAssert.that(PocketFilterConfig.isAllowedSlotIndex(Kind.FLUID, 0), "isAllowedSlotIndex 对 0 为真（只增不减）");
        SimpleAssert
            .that(PocketFilterConfig.isAllowedSlotIndex(Kind.FLUID, 5), "isAllowedSlotIndex 对旧上界 5 仍为真（★旧声明不会被判非法）");
        SimpleAssert.that(PocketFilterConfig.isAllowedSlotIndex(Kind.FLUID, 17), "isAllowedSlotIndex 对新上界 17 为真");
        SimpleAssert.eq(
            Boolean.FALSE,
            PocketFilterConfig.isAllowedSlotIndex(Kind.FLUID, tanks),
            "isAllowedSlotIndex 对 tank 总数本身为假");
        // blob 往返（S2C 同步的编解码）：多组流体声明 + 中栏末格 + 源质末格都不能变形
        final PocketFilterConfig built = new PocketFilterConfig();
        for (int tank = 0; tank < tanks; tank++) {
            set(built, tank, PocketFilterConfig.fluidKey("fluid" + tank));
        }
        set(built, PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1, PocketFilterConfig.itemKey(2621, 7, "AAA"));
        set(built, PocketConstants.GHOST_ESSENCE_SLOT_LIMIT - 1, PocketFilterConfig.essenceKey("ess", "ignis"));
        final PocketFilterConfig back = NekoPocketPanel.parseGhostBlob(NekoPocketPanel.ghostBlobOf(built));
        SimpleAssert.eq(20, back.size(), "blob 往返后条数不变（18 流体 + 1 物品 + 1 源质）");
        SimpleAssert.eq(
            "f:fluid17",
            back.at(Kind.FLUID, 17)
                .key(),
            "最后一组最后一列的声明原样回来");
        SimpleAssert.eq(
            "f:fluid5",
            back.at(Kind.FLUID, 5)
                .key(),
            "第 1 组末列的声明原样回来（旧档位置不变）");
        SimpleAssert.eq(
            PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1,
            back.at(Kind.ITEM, PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1)
                .slotIndex(),
            "中栏末格（R80① 后是 134）能声明也能同步回来");
        SimpleAssert.eq(
            PocketConstants.GHOST_ESSENCE_SLOT_LIMIT - 1,
            back.at(Kind.ESSENCE, PocketConstants.GHOST_ESSENCE_SLOT_LIMIT - 1)
                .slotIndex(),
            "★源质末格（71，R78 新增的那 24 格之一）能声明也能同步回来");
        SimpleAssert.eq(
            Boolean.FALSE,
            back.at(Kind.ITEM, PocketConstants.GHOST_ITEM_SLOT_LIMIT) != null,
            "越界的中栏索引不会被 blob 解出来");
    }

    /**
     * 中栏读档形状的两条硬性质：<b>格数永远由构造期决定</b>（档里的 {@code Size} 既不放大也不缩小
     * handler）+ <b>越界键丢弃</b>，并顺手把上游 {@code ItemStackHandler} 的形状键钉住
     * （{@code PocketInventory#loadGroup} 要绕开 {@code deserializeNBT}，绕开就得知道形状）。
     * <p>
     * ★R80①：本轮形状是<b>收缩</b>（150 → 135），所以"旧档比新形状大"这一支从"理论可能"变成
     * <b>必然发生</b>：135…149 号条目一定越界。收缩场景的独立用例在
     * {@code legacy_150_slot_save_shrinks_to_135_dropping_only_out_of_range}。
     */
    private static void storageGroupShapeRoundTripPinsLibraryKeys() {
        final PocketInventory source = PocketInventory.readFrom(null);
        SimpleAssert.eq(
            135,
            source.storage()
                .getSlots(),
            "新形状是 135 格（R80①）");
        for (int index = 0; index < 128; index++) {
            source.storage()
                .setStackInSlot(index, stackOf(1, index + 1));
        }
        final NBTTagCompound root = new NBTTagCompound();
        source.writeTo(root);
        final NBTTagCompound group = root.getCompoundTag(PocketConstants.ITEM_CONTENTS);
        SimpleAssert.that(group != null, "有货的中栏必须落档");
        SimpleAssert.eq(135, group.getInteger("Size"), "★写档一律按新形状（Size=135），旧档的 Size=128/150 只出现在读侧");
        SimpleAssert.eq(
            128,
            group.getTagList("Items", 10)
                .tagCount(),
            "只写有货的格（128 件）");

        // ① 模拟"旧档 128 格"（Size 被写回 128）⇒ 读进来仍必须是 135 格，且 128 件一件不少
        group.setInteger("Size", 128);
        final PocketInventory grown = PocketInventory.readFrom(root);
        SimpleAssert.eq(
            135,
            grown.storage()
                .getSlots(),
            "★读旧档不跟着缩：handler 仍必须是构造期的 135 格（Size=128 不得把形状带小）");
        int kept = 0;
        for (int index = 0; index < 128; index++) {
            if (grown.storageStack(index) != null) {
                kept++;
            }
        }
        if (itemNbtUsable()) {
            SimpleAssert.eq(128, kept, "旧档的 128 件全部读回");
        } else {
            System.out.println("[NOTE] 本 JVM 里 ItemStack 的 NBT 往返解不出物品（未走 Forge 注册）⇒" + "「件数一件不少」这一半属【实机项】；此处仍验形状与格数");
        }
        SimpleAssert.eq(null, grown.storageStack(134), "新增的格子是空的（不是垃圾）");

        // ② 越界槽号：外来/未来档 ⇒ 丢弃而不是让点槽越界炸
        final NBTTagList items = group.getTagList("Items", 10);
        final NBTTagCompound far = new NBTTagCompound();
        far.setInteger("Slot", 200);
        far.setInteger("id", 1);
        far.setInteger("Count", 1);
        items.appendTag(far);
        final NBTTagCompound dirtyRoot = new NBTTagCompound();
        dirtyRoot.setTag(PocketConstants.ITEM_CONTENTS, group);
        final PocketInventory dropped = PocketInventory.readFrom(dirtyRoot);
        SimpleAssert.eq(
            135,
            dropped.storage()
                .getSlots(),
            "越界条目不得把 handler 形状带坏");
        SimpleAssert.eq(null, dropped.storageStack(134), "越界条目不会挪到末格上");
        int still = 0;
        for (int index = 0; index < 135; index++) {
            if (dropped.storageStack(index) != null) {
                still++;
            }
        }
        if (itemNbtUsable()) {
            SimpleAssert.eq(128, still, "越界那一条被丢弃，其余 128 条仍在");
        } else {
            SimpleAssert.eq(0, still, "（物品 NBT 不可用 ⇒ 这里只能是 0；不假装验过）");
        }

        // ③ 空档不写壳
        final NBTTagCompound empty = new NBTTagCompound();
        PocketInventory.readFrom(null)
            .writeTo(empty);
        SimpleAssert.eq(Boolean.FALSE, empty.hasKey(PocketConstants.ITEM_CONTENTS), "空区一律 removeTag");
    }

    /**
     * 十八个 tank 的落档：新形状（带 tank 号的列表）往返 + <b>两代旧形状</b>的兼容
     * （R75 的"6 tank 列表"与更早的"单 compound"）。
     * <p>
     * ★这一条同时是任务包 §5-2 点名的"18 tank 的 NBT 读写与旧 6 tank/单 tank 兼容"。
     * 用带 {@code tank} 号的列表而不是定长列表，正是 R78 能把 6 变 18 而<b>不写迁移代码</b>的原因：
     * 旧档的 tank 0…5 在新读法下落到同一批 tank，6…17 为空。
     * <p>
     * 边界如实声明：本用例需要真实 {@code FluidStack}，而 Forge 的流体注册表在纯 JVM 里
     * 可能连类初始化都过不去（R59b 偏离①同形）。因此先探一次可用性，不可用时走
     * <b>纯算术那一半</b>并显式说明真实读写属【实机项】（不假装验过）。
     */
    private static void sixTankFluidNbtCompat() {
        final int capacity = PocketConstants.FLUID_BAR_CAPACITY_ML;
        SimpleAssert.eq(16_000_000, capacity, "单槽容量 16M（R75 定的值，R78 未改）");
        SimpleAssert.eq(18, PocketConstants.FLUID_TANK_TOTAL, "tank 总数 18（R78②：3 组 × 6 列）");
        SimpleAssert
            .eq(288_000_000, PocketConstants.FLUID_TOTAL_CAPACITY_ML, "★总容量 = 18 × 16M = 288M（R78 的双处声明口径，派生自常量）");
        SimpleAssert.eq(capacity * 18, PocketConstants.FLUID_TOTAL_CAPACITY_ML, "合计必须等于单槽 × tank 数（不许另立数字）");
        SimpleAssert.eq(18, PocketInventory.FLUID_TANK_COUNT, "PocketInventory 侧同源");
        SimpleAssert.eq(18, PocketInventory.tankCount(), "tankCount() 读数同源（GUI 循环用它，别处不得内联 18）");
        SimpleAssert.eq(0, PocketInventory.barRoom(capacity, capacity, true), "满槽 ⇒ 0");
        SimpleAssert.eq(capacity, PocketInventory.barRoom(capacity, 0, true), "空槽 ⇒ 整槽可收");
        SimpleAssert.eq(0, PocketInventory.barRoom(capacity, 1000, false), "异种流体 ⇒ 0（一个 tank 只装一种）");
        SimpleAssert.eq(capacity - 1000, PocketInventory.barRoom(capacity, 1000, true), "同种部分填充 ⇒ 剩余空间（18 个槽各自算自己的）");
        if (!fluidsUsable()) {
            SimpleAssert.that(
                NekoPocketPanel.class.getName() != null,
                "流体注册表在本 JVM 不可用 ⇒ 十八 tank 的真实读写属【实机项】（见 in-game-checklist 第 2 组）");
            return;
        }
        // ---- ① 新形状：三个不同组的 tank 各写各的，逐号往返 ----
        final PocketInventory source = PocketInventory.readFrom(null);
        source.depositFluidIntoBar(2, new FluidStack(FluidRegistry.getFluid("water"), 1234));
        source.depositFluidIntoBar(5, new FluidStack(FluidRegistry.getFluid("lava"), 777));
        source.depositFluidIntoBar(17, new FluidStack(FluidRegistry.getFluid("water"), 999));
        final NBTTagCompound root = new NBTTagCompound();
        source.writeTo(root);
        SimpleAssert.eq(Boolean.TRUE, root.hasKey(PocketConstants.FLUID_BAR, 9), "★写出的是 NBTTagList（新形状）");
        final PocketInventory back = PocketInventory.readFrom(root);
        SimpleAssert.eq(
            1234,
            back.tankAt(2)
                .getFluidAmount(),
            "2 号 tank 原样读回");
        SimpleAssert.eq(
            777,
            back.tankAt(5)
                .getFluidAmount(),
            "5 号 tank 原样读回（第 1 组末列）");
        SimpleAssert.eq(
            999,
            back.tankAt(17)
                .getFluidAmount(),
            "★17 号 tank 原样读回（第 3 组末列，R78 新增段）");
        SimpleAssert.eq(
            0,
            back.tankAt(0)
                .getFluidAmount(),
            "其余 tank 不受影响");
        SimpleAssert.eq(
            0,
            back.tankAt(16)
                .getFluidAmount(),
            "相邻的空 tank 没被串到");
        // ---- ② 上一代形状（R75 的 6 tank 档）：条目 tank 号 0…5 必须落到同一批 tank，6…17 为空 ----
        final NBTTagCompound prevRoot = new NBTTagCompound();
        final NBTTagList prevList = new NBTTagList();
        for (int tank = 0; tank < 6; tank++) {
            final NBTTagCompound entry = new NBTTagCompound();
            new FluidStack(FluidRegistry.getFluid("water"), 100 + tank).writeToNBT(entry);
            entry.setInteger(PocketConstants.FLUID_BAR_TANK, tank);
            prevList.appendTag(entry);
        }
        prevRoot.setTag(PocketConstants.FLUID_BAR, prevList);
        final PocketInventory prev = PocketInventory.readFrom(prevRoot);
        for (int tank = 0; tank < 6; tank++) {
            SimpleAssert.eq(
                100 + tank,
                prev.tankAt(tank)
                    .getFluidAmount(),
                "★旧 6 tank 档的第 " + tank + " 号落到同一个 tank 号（扩组不改编号）");
        }
        for (int tank = 6; tank < 18; tank++) {
            SimpleAssert.eq(
                0,
                prev.tankAt(tank)
                    .getFluidAmount(),
                "旧档没有的 tank " + tank + " 读出来是空（不是垃圾）");
        }
        // ---- ③ 更旧的单 compound 形状：整份落到 0 号 ----
        final NBTTagCompound legacyRoot = new NBTTagCompound();
        final NBTTagCompound legacy = new NBTTagCompound();
        new FluidStack(FluidRegistry.getFluid("water"), 4321).writeToNBT(legacy);
        legacyRoot.setTag(PocketConstants.FLUID_BAR, legacy);
        final PocketInventory legacyRead = PocketInventory.readFrom(legacyRoot);
        SimpleAssert.eq(
            4321,
            legacyRead.tankAt(0)
                .getFluidAmount(),
            "★旧单 tank 内容落到 0 号 tank");
        SimpleAssert.eq(
            0,
            legacyRead.tankAt(1)
                .getFluidAmount(),
            "旧档不会把内容摊到别的列");
        // ---- ④ 越界 tank 号与缺号条目：丢弃 / 按 0 号读，但都不炸、都不挪别人的液体 ----
        final NBTTagCompound dirtyRoot = new NBTTagCompound();
        final NBTTagList dirty = new NBTTagList();
        final NBTTagCompound far = new NBTTagCompound();
        new FluidStack(FluidRegistry.getFluid("water"), 500).writeToNBT(far);
        far.setInteger(PocketConstants.FLUID_BAR_TANK, 99);
        dirty.appendTag(far);
        final NBTTagCompound noTank = new NBTTagCompound();
        new FluidStack(FluidRegistry.getFluid("lava"), 600).writeToNBT(noTank);
        dirty.appendTag(noTank);
        dirtyRoot.setTag(PocketConstants.FLUID_BAR, dirty);
        final PocketInventory dirtyRead = PocketInventory.readFrom(dirtyRoot);
        SimpleAssert.eq(
            0,
            dirtyRead.tankAt(99 % 18)
                .getFluidAmount(),
            "越界 tank 号被丢弃（不得挪到别人身上）");
        SimpleAssert.eq(
            600,
            dirtyRead.tankAt(0)
                .getFluidAmount(),
            "缺 tank 号按 0 号读（与旧单 tank 同义，另有 WARN）");
        SimpleAssert.eq(18, PocketInventory.FLUID_TANK_COUNT, "越界条目不得把 handler 形状带坏");
        // ---- ⑤ 全空 ⇒ removeTag（不留空壳）----
        final NBTTagCompound emptyRoot = new NBTTagCompound();
        PocketInventory.readFrom(null)
            .writeTo(emptyRoot);
        SimpleAssert.eq(Boolean.FALSE, emptyRoot.hasKey(PocketConstants.FLUID_BAR), "全空即 removeTag");
    }

    /** 本 JVM 能否构造真实流体（只探一次）。 */
    private static Boolean fluidsUsable;

    private static boolean fluidsUsable() {
        if (fluidsUsable == null) {
            boolean usable;
            try {
                usable = new FluidStack(FluidRegistry.getFluid("water"), 1) != null;
            } catch (Throwable t) {
                usable = false;
            }
            fluidsUsable = Boolean.valueOf(usable);
        }
        return fluidsUsable.booleanValue();
    }

    /**
     * 解绑新语义（R74）：<b>解绑最后一条</b> + <b>清空全部</b>，且不打乱其余条目的顺序。
     * <p>
     * ★这里钉的是"绑定序即轮转外层序"这条不变量在解绑后仍成立：只从尾部摘，
     * 中间顺序绝不重排（否则 A 批的轮转起点会莫名漂走）。
     */
    private static void unbindLastAndClearAllSemantics() {
        final PocketCellBindings bindings = new PocketCellBindings();
        bindings.bind(CELL_A, PocketConstants.MODE_DISK_UUID);
        bindings.bind(CELL_B, PocketConstants.MODE_DISK_UUID);
        final String third = uuidOf(7);
        bindings.bind(third, PocketConstants.MODE_DISK_UUID);
        SimpleAssert.eq(Arrays.asList(CELL_A, CELL_B, third), bindings.cells(), "绑定序就是 entries 序（前置条件）");
        SimpleAssert.that(bindings.unbindLast(), "解绑最后一条应成功");
        SimpleAssert.eq(Arrays.asList(CELL_A, CELL_B), bindings.cells(), "★只摘尾部，前两条顺序不变");
        SimpleAssert.that(bindings.unbindLast(), "再摘一次尾部");
        SimpleAssert.eq(Arrays.asList(CELL_A), bindings.cells(), "只剩首条");
        SimpleAssert.that(bindings.unbindLast(), "摘掉最后一条");
        SimpleAssert.eq(Boolean.FALSE, bindings.unbindLast(), "★空表必须返回 false（调用方据此给回执）");
        bindings.bind(CELL_A, PocketConstants.MODE_DISK_UUID);
        bindings.bind(CELL_B, PocketConstants.MODE_DISK_UUID);
        bindings.clear();
        SimpleAssert.that(bindings.isEmpty(), "清空全部 ⇒ 空表");
        SimpleAssert.that(bindings.hasRoom(), "清空后仍可绑定（上限只约束条目数）");
    }

    /** 本 JVM 能否让 ItemStack 走一次 NBT 往返（只探一次；探不通就说明物品未注册）。 */
    private static Boolean itemNbtUsable;

    private static boolean itemNbtUsable() {
        if (itemNbtUsable == null) {
            boolean usable;
            try {
                final ItemStack probe = new ItemStack(Items.iron_ingot, 3);
                final NBTTagCompound tag = probe.writeToNBT(new NBTTagCompound());
                final ItemStack back = ItemStack.loadItemStackFromNBT(tag);
                usable = back != null && back.getItem() == Items.iron_ingot && back.stackSize == 3;
            } catch (Throwable t) {
                usable = false;
            }
            itemNbtUsable = Boolean.valueOf(usable);
        }
        return itemNbtUsable.booleanValue();
    }

    /** C2 契约表（HTML 355–366 行）与几何/边距裁定的对账。 */
    private static void c2TextureContractTableMatchesGeometry() {
        SimpleAssert.eq(
            13,
            PocketGuiTextureContract.contractSize(),
            "契约 11 行 + 派生的按钮按下态 1 行 + 进度条填充 1 行（★两条派生行都是对契约的补全，不是冗余；不得为凑数删行）");
        final int[][] expected = { { 32, 32 }, { 64, 64 }, { 14, 14 }, { 6, 6 }, { 18, 18 }, { 18, 18 }, { 88, 18 },
            { 88, 18 }, { 12, 12 }, { 106, 18 }, { 8, 8 } };
        final String[] names = { "POCKET_C2_cloth", "POCKET_C2_panel", "POCKET_C2_corner", "POCKET_C2_rivet",
            "POCKET_C2_slot", "POCKET_C2_slot_tall", "POCKET_C2_coinbar", "POCKET_C2_btn", "POCKET_C2_scrollbar",
            "POCKET_C2_bindbtn", "POCKET_C2_rope" };
        for (int index = 0; index < names.length; index++) {
            SimpleAssert
                .eq(expected[index][0], PocketGuiTextureContract.widthOf(names[index]), names[index] + " 宽度必须逐字等于契约行");
            SimpleAssert
                .eq(expected[index][1], PocketGuiTextureContract.heightOf(names[index]), names[index] + " 高度必须逐字等于契约行");
            SimpleAssert.that(
                PocketGuiTextureContract.specOf(names[index]) != null,
                names[index] + " 必须能按契约名查到契约行（注册与查表同一份常量）");
        }
        // ★槽位边距上限 3（超过就分不出空槽/有货槽 ⇒ 撞 R73②）
        SimpleAssert.eq(3, PocketGuiTextureContract.sliceMarginOf("POCKET_C2_slot"), "槽位底边距 = 3（R75 上限）");
        SimpleAssert.eq(3, PocketGuiTextureContract.sliceMarginOf("POCKET_C2_slot_tall"), "流体槽底边距 = 3");
        SimpleAssert.that(
            PocketGuiTextureContract.sliceMarginOf("POCKET_C2_slot") <= PocketGuiTextureContract.MAX_SLOT_SLICE_MARGIN,
            "槽位边距不得超过 MAX_SLOT_SLICE_MARGIN");
        SimpleAssert.eq(-1, PocketGuiTextureContract.sliceMarginOf("POCKET_C2_corner"), "包角非 9-slice ⇒ 1:1 贴");
        SimpleAssert.eq(-1, PocketGuiTextureContract.sliceMarginOf("POCKET_C2_rivet"), "铆钉非 9-slice ⇒ 1:1 贴");
        // 9-slice 可用性的算术前提：图宽 > 2N+1（否则中心区被吃掉）
        final String[] sliced = { "POCKET_C2_panel", "POCKET_C2_slot", "POCKET_C2_slot_tall", "POCKET_C2_coinbar",
            "POCKET_C2_btn", "POCKET_C2_scrollbar", "POCKET_C2_bindbtn" };
        for (String name : sliced) {
            final int margin = PocketGuiTextureContract.sliceMarginOf(name);
            SimpleAssert.that(
                PocketGuiTextureContract.widthOf(name) > 2 * margin
                    && PocketGuiTextureContract.heightOf(name) > 2 * margin,
                name + " 的 9-slice 边距吃掉了图心");
        }
        SimpleAssert.eq(Boolean.FALSE, PocketGuiTextureContract.isNineSlice("POCKET_C2_cloth"), "平铺底不是 9-slice");
        // 落地文件路径也钉一次：兄弟片按这个名字写文件，两边改名必须同时改（单源 = 契约表）
        SimpleAssert.eq(
            "src/main/resources/assets/gtit/textures/gui/pocket/POCKET_C2_panel.png",
            PocketGuiTextureContract.assetPathOf("POCKET_C2_panel"),
            "★落地路径与兄弟片的交付目录必须逐字一致（本片不写该目录，只引用）");
        // 注册前自校验（validate 只读契约表，因此纯 JVM 可跑）
        PocketGuiTextureContract.validate();
    }

    /**
     * 面板 398×360 的加总闭合（R75 钉外框与纵向、R78 改内部三段与两列、R80① 中栏收到 9 列、
     * ★R81④ 把面板宽收到主区实占 398；纵向仍<b>一格余量都没有</b>）。
     * <p>
     * ★每一条都是"加算式"而不是抄来的数：外部把加总表算错时，这里会跟着错 ⇒
     * 主代理只需读断言文本就能复核算式（任务包 §1.1 的纪律）。
     */
    private static void panelGeometryCloses() {
        // ---- 外框（★R81④：面板宽 = 主区实占，360 = GUI Scale 3 硬上限未动）----
        SimpleAssert.eq(398, NekoPocketPanel.WIDTH, "★面板宽 = 主区实占 = 398（R81④ 收掉旧口径保留的那 18px 无主空白）");
        SimpleAssert.eq(360, NekoPocketPanel.HEIGHT, "★面板高 = 6+270+6+72+6 = 360 = 1080p/GUI Scale 3 上限");
        SimpleAssert.eq(108, NekoPocketLeftColumn.WIDTH, "流体块每组 6 列 × 18");
        SimpleAssert.eq(162, NekoPocketStorageColumn.WIDTH, "★中栏 9 列 × 18 = 162（R80①，旧 10 列 = 180）");
        SimpleAssert.eq(108, NekoPocketEssenceColumn.WIDTH, "源质 6 列");
        SimpleAssert.eq(270, NekoPocketStorageColumn.HEIGHT, "主区 15 行（R80① 一行不删）");
        SimpleAssert.eq(118, NekoPocketStorageColumn.X, "★中栏 x = 6+108+4 = 118（收窄只发生在右边界，x 未动）");
        SimpleAssert.eq(284, NekoPocketEssenceColumn.X, "源质列 x = 6+108+4+162+4 = 284");
        // ---- ★R81④：主区实占 = 面板宽 ⇒ 没有"让位量"这种东西可具名 ----
        SimpleAssert.eq(398, NekoPocketPanel.MAIN_OCCUPIED_WIDTH, "主区实占 = 6+108+4+162+4+108+6 = 398");
        SimpleAssert.eq(
            NekoPocketPanel.MAIN_OCCUPIED_WIDTH,
            NekoPocketPanel.WIDTH,
            "★实占 == 面板宽：右侧 0 像素无主空白（旧口径那个 18px 让位量常量已整体删除，不是置 0）");
        SimpleAssert.eq(6 + 108 + 4 + 162 + 4 + 108 + 6, NekoPocketPanel.WIDTH, "★加算式逐字（任务包 §追加④ 点名的那条断言）");
        // ---- 左栏（R78②：3 组 × 72 + 2 个 18 组间距 = 252，余 18 给状态行）----
        SimpleAssert.eq(36, NekoPocketLeftColumn.TANK_HEIGHT, "★流体槽拉长为 36 高（用户：流体槽应该拉长一点）");
        SimpleAssert.eq(72, NekoPocketLeftColumn.GROUP_HEIGHT, "一组 = 18 + 36 + 18 = 72");
        SimpleAssert.eq(18, NekoPocketLeftColumn.GROUP_GAP, "组间距 = 空一行 = 18");
        SimpleAssert.eq(3 * 72 + 2 * 18, NekoPocketLeftColumn.FLUID_AREA_HEIGHT, "流体块 = 3×72 + 2×18 = 252（R78 加总表原式）");
        SimpleAssert.eq(252, NekoPocketLeftColumn.FLUID_AREA_HEIGHT, "流体块高 252 ≤ 270");
        SimpleAssert.eq(
            NekoPocketStorageColumn.HEIGHT - NekoPocketLeftColumn.FLUID_AREA_HEIGHT,
            NekoPocketLeftColumn.STATUS_HEIGHT,
            "★余量 270-252 = 18 全部给末行（R78 的 余 18 给状态行）");
        SimpleAssert.eq(18, NekoPocketLeftColumn.STATUS_HEIGHT, "末行高 18 = 只放得下一行状态回显（D-2）");
        SimpleAssert.eq(0, NekoPocketLeftColumn.groupTop(0), "第 1 组从列顶开始");
        SimpleAssert.eq(90, NekoPocketLeftColumn.groupTop(1), "第 2 组 y = 72 + 18 = 90");
        SimpleAssert.eq(180, NekoPocketLeftColumn.groupTop(2), "第 3 组 y = 2×90 = 180");
        SimpleAssert.eq(
            NekoPocketLeftColumn.FLUID_AREA_HEIGHT - NekoPocketLeftColumn.GROUP_HEIGHT,
            NekoPocketLeftColumn.groupTop(2),
            "最后一组的底边正好贴到末行（不留缝、不重叠）");
        // ---- 右列（R78②：12 行盘 + 空 1 行 + 蒸馏 2 行 = 15 行 = 与中栏同高）----
        SimpleAssert.eq(216, NekoPocketEssenceColumn.ESSENCE_HEIGHT, "源质盘 12 行 × 18 = 216");
        SimpleAssert.eq(18, NekoPocketEssenceColumn.SEPARATOR_HEIGHT, "★空 1 行 = 18（零槽；进度条住这里）");
        SimpleAssert.eq(36, NekoPocketEssenceColumn.DISTILL_HEIGHT, "蒸馏 2 行 × 18 = 36");
        SimpleAssert.eq(
            NekoPocketStorageColumn.HEIGHT,
            NekoPocketEssenceColumn.ESSENCE_HEIGHT + NekoPocketEssenceColumn.SEPARATOR_HEIGHT
                + NekoPocketEssenceColumn.DISTILL_HEIGHT,
            "右列三段高度之和 = 列高（216 + 18 + 36 = 270，★与中栏同高 = R78 的 整体规整）");
        SimpleAssert.eq(
            NekoPocketEssenceColumn.ESSENCE_HEIGHT + NekoPocketEssenceColumn.SEPARATOR_HEIGHT,
            NekoPocketEssenceColumn.DISTILL_Y,
            "蒸馏盘起点 = 盘面 + 空行（★蒸馏 2 行下移，R78②）");
        SimpleAssert.eq(
            NekoPocketEssenceColumn.ESSENCE_ROWS + 1 + NekoPocketEssenceColumn.DISTILL_ROWS,
            15,
            "12 + 1 + 2 = 15 行 ⇒ 与中栏行数相同（规整由行数对齐达成）");
        // ---- 底部带三段（R78①）----
        SimpleAssert.eq(282, NekoPocketBottomBand.Y, "底部带起点 = 6+270+6");
        SimpleAssert.eq(72, NekoPocketBottomBand.HEIGHT, "底部带高 72");
        SimpleAssert.eq(
            NekoPocketPanel.HEIGHT - NekoPocketPanel.MARGIN,
            NekoPocketBottomBand.Y + NekoPocketBottomBand.HEIGHT,
            "高度闭合：带底边 + 外边距 = 面板高（★再高就撞 360 天花板）");
        SimpleAssert.eq(6, NekoPocketBottomBand.COIN_X, "左段 x = 面板外边距");
        SimpleAssert.eq(112, NekoPocketBottomBand.COIN_WIDTH, "★R80①：左段 100 → 112（= 中栏左沿 118 - 外边距 6）");
        SimpleAssert.eq(118, NekoPocketBottomBand.BACKPACK_X, "★背包段 x = 6+112 = 118 = 中栏 x（R80① 同 x 判据）");
        SimpleAssert.eq(162, NekoPocketBottomBand.BACKPACK_WIDTH, "★背包段 9 列 × 18 = 162 = 中栏宽（R80① 同宽判据）");
        SimpleAssert.eq(72, NekoPocketBottomBand.BACKPACK_HEIGHT, "背包 4 行 × 18 = 72 = 带高（★所以中栏 15 行一行都不用删）");
        SimpleAssert
            .eq(NekoPocketBottomBand.BACKPACK_HEIGHT, NekoPocketBottomBand.HEIGHT, "背包高必须正好等于带高（否则要么撑破 360 要么留缝）");
        SimpleAssert.eq(112, NekoPocketBottomBand.BIND_WIDTH, "★R81④：右段 130 → 112 = 6×18+4（面板收到 398 后的唯一解）");
        SimpleAssert.eq(0, NekoPocketBottomBand.BIND_SLACK, "★R80①：三段之间不留间距 ⇒ 余量必须恰为 0（不是 0 = 出现没人认领的空白）");
        SimpleAssert.eq(280, NekoPocketBottomBand.BIND_X, "右段 x = 6+112+162 = 280（★R81④ 未动，动的只有右段宽）");
        SimpleAssert.eq(
            NekoPocketPanel.WIDTH - NekoPocketPanel.MARGIN,
            NekoPocketBottomBand.BIND_X + NekoPocketBottomBand.BIND_WIDTH,
            "★底部带横向闭合：右段右边 = 398-6 = 392（左右外边距仍各 6）");
        SimpleAssert.eq(
            6 + 112 + 162 + 112,
            NekoPocketPanel.WIDTH - NekoPocketPanel.MARGIN,
            "★R81④ 三段加总 112|162|112 = 386，加左右外边距正好铺满 398（= 主区实占）");
        SimpleAssert.eq(4, NekoPocketBottomBand.BACKPACK_ROWS, "背包 4 行（R78①）");
        SimpleAssert.eq(9, NekoPocketBottomBand.BACKPACK_COLUMNS, "背包 9 列（与框架那组的 rowSize 同值）");
    }

    // ================================================================== R78 新增三条交付判据
    //
    // 这三条都是"任务包 §1/§3 点名要机检"的形态：D-1 的内容层按库存、R78③ 的首入账格序
    // （含持久化与不回收）、R78① 的背包格序映射。

    /**
     * ★钉 D-1（上一片交付不实）：<b>库存 0 的源质格 ⇒ 内容层为空；库存 &gt; 0 ⇒ 内容层为该 tag</b>。
     * <p>
     * 旧实现是"无条件 {@code cell.overlay(icon)}"，而它自己的 javadoc 写着"无货时只撤掉图标与文本"
     * ⇒ 注释声明了代码没做的事。现在判据是 {@link NekoEssenceGhostCell#drawsContentLayer} 单点，
     * 落地序列是 {@link NekoEssenceGhostCell#applyContentLayer} 单点，本用例★驱动的是<b>生产那一条
     * 静态序列</b>（用记录型 sink），所以断言的是"生产代码在库存 0 时走的是 clearIcon + 空文本"，
     * 不是把判据抄第二遍。
     * <p>
     * ★为什么不在这里 {@code new NekoEssenceGhostCell()} 然后读 {@code getOverlay()}：
     * 实测本 JVM 构造任何 MUI2 widget 都抛
     * {@code NoClassDefFoundError: it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap}
     * （与 {@code PocketGuiTextures} 类注释同一条限制）⇒ "图标真的没画"这一半属<b>实机项</b>，
     * 这里如实记出而不假装验过。槽位底（background）由装配期设定、内容层从不撤它，
     * 这条也在实机项里（R73②"格子本身要显示"）。
     */
    private static void essenceCellContentLayerFollowsStock() {
        // ---- 判据本体（纯函数，装配、同步与生产落地序列读的都是它）----
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.drawsContentLayer("ignis", 0), "★库存 0 ⇒ 不画内容层");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.drawsContentLayer("ignis", -3), "负数同样不画");
        SimpleAssert.that(NekoEssenceGhostCell.drawsContentLayer("ignis", 1), "库存 1 ⇒ 画");
        SimpleAssert.that(NekoEssenceGhostCell.drawsContentLayer("ignis", 64), "库存满格也画");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.drawsContentLayer(null, 9), "该格无归属 tag ⇒ 不画");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.drawsContentLayer("", 9), "空 tag ⇒ 不画");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.drawsContentLayer(null, 0), "无主空格 ⇒ 不画");

        // ---- 生产落地序列：库存 0 ⇒ clearIcon + 空文本，且 showIcon 一次都不调 ----
        final Recorder empty = new Recorder();
        NekoEssenceGhostCell.applyContentLayer("ignis", 0, empty);
        SimpleAssert.eq(1, empty.clearCalls, "★库存 0 ⇒ 撤图标被调用一次");
        SimpleAssert.eq(0, empty.iconCalls, "★库存 0 ⇒ 画图标一次都没发生（旧实现就是这里恒为 1）");
        SimpleAssert.eq("", empty.amount, "★库存 0 ⇒ 数量文本是空串（不是留着旧数）");

        final Recorder full = new Recorder();
        NekoEssenceGhostCell.applyContentLayer("ignis", 12, full);
        SimpleAssert.eq(1, full.iconCalls, "库存 > 0 ⇒ 画图标一次");
        SimpleAssert.eq("ignis", full.lastIcon, "★内容层就是这个 tag 的图标");
        SimpleAssert.eq("12", full.amount, "数量文本按点数出");
        SimpleAssert.eq(0, full.clearCalls, "有货时不得去撤图标");

        // 从有货回到无货（玩家点取晶体的那条路）⇒ 两条动作都要重新发生
        final Recorder drained = new Recorder();
        NekoEssenceGhostCell.applyContentLayer("ignis", 5, drained);
        NekoEssenceGhostCell.applyContentLayer("ignis", 0, drained);
        SimpleAssert.eq(1, drained.clearCalls, "★扣光之后要撤一次图标");
        SimpleAssert.eq(1, drained.iconCalls, "同一序列被调两遍：只有有货那一次画图标（原位切换不重复画）");
        SimpleAssert.eq("", drained.amount, "扣光后文本也清空");

        // 没归属的格：即使档里有点数也不画（不会有这种档，但开关必须两维都判）
        final Recorder orphan = new Recorder();
        NekoEssenceGhostCell.applyContentLayer(null, 5, orphan);
        SimpleAssert.eq(0, orphan.iconCalls, "无主格不画图标");
        SimpleAssert.eq(1, orphan.clearCalls, "无主格走撤图标");
        SimpleAssert.eq("", orphan.amount, "无主格没有数量文本");
        // sink 为 null 不得炸（面板不得因一个空目标崩溃）
        NekoEssenceGhostCell.applyContentLayer("ignis", 3, null);
    }

    /** {@link NekoEssenceGhostCell.ContentSink} 的记录型实现（★只为把"生产走了哪条分支"变成读数）。 */
    private static final class Recorder implements NekoEssenceGhostCell.ContentSink {

        int iconCalls;
        int clearCalls;
        String lastIcon;
        String amount;

        @Override
        public void showIcon(String aspectTag) {
            iconCalls++;
            lastIcon = aspectTag;
        }

        @Override
        public void clearIcon() {
            clearCalls++;
        }

        @Override
        public void showAmount(String text) {
            amount = text;
        }
    }

    /**
     * ★R78③：源质格的<b>格序 = 首次入账顺序</b>，且①持久化、②重开不变、③<b>撤空不回收格位</b>。
     * <p>
     * 为什么这三条必须一起钉：只验"按入账序"的话，把归属表做成<b>每次现算</b>（例如
     * {@code aspectOrder()} 派生序，或按 tag 字典序）的实现同样能过；那玩家重开一次面板就
     * 全部重排（找不到东西）而 ghost 声明还会指向别的 tag（声明按<b>格号</b>索引）。
     */
    private static void essenceCellOrderIsFirstCreditAndPersists() {
        final int grid = PocketConstants.ESSENCE_DISPLAY_GRID;
        final PocketEssenceStore store = new PocketEssenceStore();
        // ---- ① 首次入账顺序 = 格序（后入账的不能挤掉先入账的格）----
        store.add("ignis", 10);
        store.add("aer", 10);
        store.add("ignis", 5);
        store.add("terra", 1);
        SimpleAssert.eq(0, store.cellOf("ignis"), "第一个入账的落 0 格");
        SimpleAssert.eq(1, store.cellOf("aer"), "第二个落 1 格");
        SimpleAssert.eq(2, store.cellOf("terra"), "第三个落 2 格");
        SimpleAssert.eq(-1, store.cellOf("ordo"), "★还没入过账的 tag 不占格（格位由入账触发，不由注册表预铺；-1 = 无格位）");
        SimpleAssert.eq(0, store.cellOf("ignis"), "★同一 tag 重复入账不搬家（第二次 add 仍落 0 格；assignCell 走幂等分支）");
        SimpleAssert.eq("ignis", store.tagAtCell(0), "格号反查 tag 与 cellOf 对称");
        SimpleAssert.eq("aer", store.tagAtCell(1), "同上");
        SimpleAssert.eq("terra", store.tagAtCell(2), "同上");
        SimpleAssert.eq(null, store.tagAtCell(3), "未占的格反查为 null（显示侧据此不画内容）");
        SimpleAssert.eq(null, store.tagAtCell(-1), "越界格反查为 null");
        SimpleAssert.eq(null, store.tagAtCell(grid), "上界外一格同样 null（不抛）");
        SimpleAssert.eq(3, store.assignedCellCount(), "已占格数 = 见过的 tag 数");
        SimpleAssert.eq(0, store.unplacedTagCount(), "3 个 tag 都有格位 ⇒ 无溢出");

        // ---- ③ ★R86 改判（作废 R78③「撤空不回收」）：扣到 0 当场腾格 ----
        store.extract("aer", 10);
        SimpleAssert.eq(0, store.get("aer"), "点数确实扣光了");
        SimpleAssert.eq(-1, store.cellOf("aer"), "★扣到 0 ⇒ 那一格当场腾出来（用户原话「清空的源质对应位置就空出来，不要 0/256」）");
        SimpleAssert.eq(null, store.tagAtCell(1), "格位归属表里那一行已置空（显示侧据此整格不画）");
        SimpleAssert.eq(2, store.assignedCellCount(), "占格数随腾格减一，不再「含曾经占过的格」");
        // 腾出来的格会被<b>新</b> tag 抢走 ⇒ 同 tag 再入账时落点<b>不必</b>是原来那一格（旧裁定「再灌回来
        // 还是原来那一格」随 R78③ 一起作废；这里显式钉住新形状，免得有人把它当回归改回去）。
        store.add("aqua", 3);
        SimpleAssert.eq(1, store.cellOf("aqua"), "新 tag 占最小空位 = 刚腾出来的 1 格");
        store.add("aer", 7);
        SimpleAssert.eq(3, store.cellOf("aer"), "★aer 回来时只能占下一个空位（3 格），不回原来的 1 格");
        SimpleAssert.eq(4, store.assignedCellCount(), "ignis / aqua / aer / terra 各占一格");

        // ---- ② 持久化：写出 → 读回 → 格序逐字不变（重开面板/重进世界不重排）----
        final NBTTagCompound root = new NBTTagCompound();
        store.writeTo(root);
        SimpleAssert.that(root.hasKey(PocketConstants.ESSENCE_CELL_ORDER), "★格位归属必须真的落 NBT（不落 = 重开就重排）");
        final PocketEssenceStore again = PocketEssenceStore.readFrom(root);
        SimpleAssert.eq(0, again.cellOf("ignis"), "重开后 ignis 仍在 0 格");
        SimpleAssert.eq(
            3,
            again.cellOf("aer"),
            "★重开后 aer 在它<b>腾过格之后重新占的</b>那一格（R86 起格位不再长期绑定；这一条钉的是「同一份档两次读出的格序一致」，不是「一辈子不回原格」）");
        SimpleAssert.eq(1, again.cellOf("aqua"), "重开后 aqua 抢到的那格（= aer 腾出来的）照回来");
        SimpleAssert.eq(2, again.cellOf("terra"), "重开后 terra 仍在 2 格");
        SimpleAssert.eq(7, again.get("aer"), "点数也回来了");
        SimpleAssert.eq(store.assignedCellCount(), again.assignedCellCount(), "占格数量逐字往返（R86 起只数有货的格）");
        for (int cell = 0; cell < grid; cell++) {
            final String before = store.tagAtCell(cell);
            final String after = again.tagAtCell(cell);
            SimpleAssert.eq(before == null ? "" : before, after == null ? "" : after, "第 " + cell + " 格归属不变");
        }

        // ---- 溢出兜底：>72 个 tag 时多出来的只存不显（★代码路径必须保留，R78 明文）----
        final PocketEssenceStore flood = new PocketEssenceStore();
        for (int index = 0; index < grid; index++) {
            SimpleAssert.eq(index, flood.assignCell("tag" + index), "前 72 个 tag 依次占满 0…71");
        }
        SimpleAssert.eq(-1, flood.assignCell("extra"), "★第 73 个 tag 没有格位 ⇒ assignCell 返回 -1");
        flood.add("extra", 5);
        SimpleAssert.eq(5, flood.get("extra"), "无格位的 tag 照样入账（存储与显示解耦，R26）");
        SimpleAssert.eq(-1, flood.cellOf("extra"), "它没有格号");
        SimpleAssert.eq(1, flood.unplacedTagCount(), "★溢出读数 = 有货但无格位的 tag 数（驱动 overflow_note）");
        SimpleAssert.eq(grid, flood.assignedCellCount(), "格位表长度恒等于格数（不随内容伸缩 ⇒ R32 前提不破）");

        // ---- 旧档兼容：没有归属表时按 Aspects 的入账顺序补占（不重排、不丢格）----
        final NBTTagCompound legacy = new NBTTagCompound();
        final NBTTagList aspects = new NBTTagList();
        final String[] legacyOrder = { "potentia", "bestia", "ignis" };
        for (int index = 0; index < legacyOrder.length; index++) {
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setString(PocketConstants.ASPECT_KEY, legacyOrder[index]);
            entry.setShort(PocketConstants.ASPECT_AMOUNT, (short) (index + 1));
            aspects.appendTag(entry);
        }
        final NBTTagCompound ess = new NBTTagCompound();
        ess.setTag(PocketConstants.ASPECTS, aspects);
        legacy.setTag(PocketConstants.ESSENCE, ess);
        SimpleAssert.eq(Boolean.FALSE, legacy.hasKey(PocketConstants.ESSENCE_CELL_ORDER), "★这是一份 R78 之前的旧档");
        final PocketEssenceStore migrated = PocketEssenceStore.readFrom(legacy);
        SimpleAssert.eq(0, migrated.cellOf("potentia"), "旧档第 1 条落 0 格（插入序即首次入账序）");
        SimpleAssert.eq(1, migrated.cellOf("bestia"), "旧档第 2 条落 1 格");
        SimpleAssert.eq(2, migrated.cellOf("ignis"), "旧档第 3 条落 2 格");
        final NBTTagCompound migratedRoot = new NBTTagCompound();
        migrated.writeTo(migratedRoot);
        SimpleAssert.that(migratedRoot.hasKey(PocketConstants.ESSENCE_CELL_ORDER), "旧档一次读写即升到新形状（★只升不降）");
        final PocketEssenceStore twice = PocketEssenceStore.readFrom(migratedRoot);
        for (int index = 0; index < legacyOrder.length; index++) {
            SimpleAssert.eq(index, twice.cellOf(legacyOrder[index]), "旧档迁移后第二次读也不重排（幂等，★否则每次读写都会漂一次）");
        }
    }

    /**
     * ★R78③：格序映射必须<b>服务端算 + 随现有 blob 同步</b>（两端各算各的就是 R32 的头号风险）。
     * <p>
     * 这里钉的是那根通道的编解码：三段式（点数 / 每格 tag / 溢出数），★下标一律是<b>格号</b>；
     * 无主格写空串、外来/畸形 blob 一律回落成"该格为 0 / 无主"而不是抛。
     */
    private static void essenceBlobCarriesCellOrderRoundTrip() {
        final int grid = PocketConstants.ESSENCE_DISPLAY_GRID;
        final int[] points = new int[grid];
        final String[] tags = new String[grid];
        tags[0] = "ignis";
        points[0] = 12;
        tags[2] = "aer";
        points[2] = 0; // ★有主但空着：格位保留、点数为 0（D-1 的空态就长这样）
        final String blob = NekoPocketPanel.encodeEssenceBlob(points, tags, 3);
        final NekoPocketPanel.EssenceView view = NekoPocketPanel.decodeEssenceBlob(blob, grid);
        SimpleAssert.eq(12, view.points[0], "点数按格号回来");
        SimpleAssert.eq("ignis", view.tags[0], "tag 按格号回来");
        SimpleAssert.eq("aer", view.tags[2], "第 2 格仍归 aer（★中间的空格不能把后面的格整体前移）");
        SimpleAssert.eq(0, view.points[2], "有主空格的点数是 0 ⇒ 显示侧据此撤内容层");
        SimpleAssert.eq(null, view.tags[1], "无主格解出 null");
        SimpleAssert.eq(3, view.unplaced, "溢出数原样回来");
        // 畸形输入一律回落，不得抛（面板不得因一条坏 blob 崩）
        final NekoPocketPanel.EssenceView broken = NekoPocketPanel.decodeEssenceBlob("abc;def", grid);
        SimpleAssert.eq(0, broken.points[0], "非数字 ⇒ 0");
        SimpleAssert.eq(0, broken.unplaced, "缺第三段 ⇒ 0");
        final NekoPocketPanel.EssenceView empty = NekoPocketPanel.decodeEssenceBlob(null, grid);
        SimpleAssert.eq(grid, empty.points.length, "★解出来的数组长度恒等于格数（不随 blob 条数伸缩）");
        SimpleAssert.eq(grid, empty.tags.length, "同上");
        final NekoPocketPanel.EssenceView shortBlob = NekoPocketPanel.decodeEssenceBlob("1,2;ignis,abc", grid);
        SimpleAssert.eq(1, shortBlob.points[0], "短 blob 前两段照读");
        SimpleAssert.eq(2, shortBlob.points[1], "同上");
        SimpleAssert.eq(null, shortBlob.tags[2], "缺的格回落无主（不抛、不读脏）");
        SimpleAssert.eq(0, shortBlob.unplaced, "缺第三段 ⇒ 0");
    }

    /**
     * ★R78①：背包的"布局序号 → 框架槽号"映射必须是 0…35 的双射，且<b>快捷栏画在最下行</b>
     * （与原版容器一致，也与库内 {@code SlotGroupWidget#playerInventory} 的读法一致）。
     */
    private static void backpackSlotMappingIsBijection() {
        final int total = PocketConstants.PLAYER_BACKPACK_SLOTS;
        final boolean[] seen = new boolean[total];
        for (int layout = 0; layout < total; layout++) {
            final int player = NekoPocketBottomBand.backpackPlayerSlotAt(layout);
            SimpleAssert.that(player >= 0 && player < total, "布局格 " + layout + " 必须落在合法背包槽内");
            seen[player] = true;
        }
        for (int player = 0; player < total; player++) {
            SimpleAssert.that(seen[player], "★背包槽 " + player + " 必须被某一格画出来（否则就是隐形槽）");
        }
        SimpleAssert.eq(9, NekoPocketBottomBand.backpackPlayerSlotAt(0), "视觉首格 = 主背包第 9 格（不是快捷栏）");
        SimpleAssert.eq(35, NekoPocketBottomBand.backpackPlayerSlotAt(26), "视觉第三行末格 = 主背包末格 35");
        SimpleAssert.eq(0, NekoPocketBottomBand.backpackPlayerSlotAt(27), "★视觉最下行首格 = 快捷栏 0");
        SimpleAssert.eq(8, NekoPocketBottomBand.backpackPlayerSlotAt(35), "视觉最下行末格 = 快捷栏 8");
        SimpleAssert.eq(
            total,
            NekoPocketBottomBand.backpackLayoutSlotCount(),
            "矩阵格数 = 框架注册的 36（不等就会有格子没有 handler 或 handler 没有格子）");
    }

    // ================================================================== R86（实机 6 条）两条新判据

    /**
     * ★R86（缺陷 5，改判 R78③「撤空不回收」）：出账清零 ⇒ 当场腾格，并且<b>旧档</b>里
     * "有格位、无库存"的历史空洞在读档时被折叠掉——否则玩家手上那份 R78③ 期间写出的档会永远
     * 顶着几格 {@code 0/256}，正是他报的那句话。
     */
    private static void essenceCellReleasedAndLegacyHoleFolds() {
        final PocketEssenceStore store = new PocketEssenceStore();
        store.add("ignis", 12);
        store.add("aer", 30);
        SimpleAssert.eq(0, store.cellOf("ignis"), "先占两格：ignis = 0");
        SimpleAssert.eq(1, store.cellOf("aer"), "aer = 1");
        SimpleAssert.eq(30, store.extract("aer", 999), "请求超过存量 ⇒ 按存量给，不报负数");
        SimpleAssert.eq(-1, store.cellOf("aer"), "★清零即腾格（用户原话「清空的源质对应位置就空出来」）");
        SimpleAssert.eq(null, store.tagAtCell(1), "归属表里那一行已置空 ⇒ 显示侧整格不画");
        // 部分出账<b>不</b>腾格：还剩点数就必須留在原格，否则"点掉一半格子全跑"
        store.add("terra", 9);
        store.extract("terra", 4);
        SimpleAssert.eq(1, store.cellOf("terra"), "★terra 拿到的正是 aer 腾出来的 1 格；还剩 5 点 ⇒ 格位不动");
        SimpleAssert.eq(-1, store.cellOf("aer"), "腾出去的格被新 tag 占走后 aer 仍是无格位（不会自己「回来」）");
        // ---- 旧档折叠：手搭一份"格位表里有 aer、库存里没有"的档（R78③ 期间的合法形状）----
        final NBTTagCompound root = new NBTTagCompound();
        store.writeTo(root);
        final NBTTagList holes = new NBTTagList();
        holes.appendTag(new NBTTagString("ignis"));
        holes.appendTag(new NBTTagString("aer"));
        holes.appendTag(new NBTTagString("terra"));
        root.setTag(PocketConstants.ESSENCE_CELL_ORDER, holes);
        final PocketEssenceStore again = PocketEssenceStore.readFrom(root);
        SimpleAssert.eq(0, again.cellOf("ignis"), "有货的归属照回来");
        SimpleAssert.eq(-1, again.cellOf("aer"), "★读档时把「有格位无库存」那一行折叠掉（不折叠则旧档里的 0/256 占位永远清不掉）");
        SimpleAssert.eq(2, again.assignedCellCount(), "折叠后占格数只数真的有货的那两个");
        // 折叠不能把中间的空位挤成重排：terra 必须还在它自己那一格
        SimpleAssert.eq(2, again.cellOf("terra"), "★折叠只撤那一行，不做整体前移（前移＝换一套格序）");
    }

    /**
     * ★R86（缺陷 2）：晶化源质放进 12 格 ⇒ <b>整叠消耗</b>入账，且不走 {@code drainContainer}、不退空壳。
     * <p>
     * ★如实标注本用例<b>测不到</b>什么（档案 r86-ret-capgap §E 的假绿警示）：真缺陷住在
     * {@code TaumBridge#drainAll} 对晶的那道早退里，而桩 gate 没有那条早退。这里钉的是<b>上层消耗支</b>
     * 的契约形状：点数 = 单件 amount × {@code stackSize}、结论是 {@code CONSUMED} 而非 {@code DRAINED}、
     * 预检失败分毫不动、以及晶这一支一次都不该去调 {@code drainContainer}（白调，生产侧恒返 EMPTY）。
     */
    private static void injectCrystalConsumesWholeStack() {
        final StubGate gate = new StubGate();
        final ItemStack crystals = stack(64);
        gate.putCrystal(crystals, TaumAspectAmounts.of(new String[] { "ignis" }, new int[] { 1 }));
        final PocketEssenceStore store = new PocketEssenceStore();

        final PocketSlots.IntakeResult result = PocketSlots.injectContainer(crystals, store, gate);
        SimpleAssert.that(result.consumed(), "★晶走整叠消耗支，不是瓶那条抽干支 ⇒ " + result.kind);
        SimpleAssert.that(!result.drained(), "消耗支不得同时报 drained（调用方靠这两个比特分清「退回 / 销毁」）");
        SimpleAssert.eq(64, result.points, "★整叠 64 枚各计 1 点 = 64 点（漏乘 stackSize 只会进 1 点）");
        SimpleAssert.eq(64, store.get("ignis"), "点数按整叠入账");
        SimpleAssert.eq(0, gate.drainCalls, "★晶一支都不碰 drainContainer（生产实现对它恒返 EMPTY）");
        // 全有全无：单格上限只剩 3 点，10 枚晶进不去 ⇒ 一格都不动
        final StubGate tight = new StubGate();
        final ItemStack more = stack(10);
        tight.putCrystal(more, TaumAspectAmounts.of(new String[] { "ignis" }, new int[] { 1 }));
        final PocketEssenceStore full = new PocketEssenceStore();
        full.add("ignis", PocketConstants.ESSENCE_CAP_PER_TAG - 3);
        final PocketSlots.IntakeResult rejected = PocketSlots.injectContainer(more, full, tight);
        SimpleAssert.eq(PocketSlots.Intake.STORE_FULL, rejected.kind, "装不下 ⇒ STORE_FULL（与瓶支同一条 R29 出口）");
        SimpleAssert
            .eq(PocketConstants.ESSENCE_CAP_PER_TAG - 3, full.get("ignis"), "★预检失败时源质表分毫未动（只够 3 点也不截断消耗，R45c/FIX-6）");
        // 换算本体单独钉：多 tag 按各自 amount × 叠数
        final Map<String, Integer> single = new LinkedHashMap<>();
        single.put("aer", 8);
        single.put("ignis", 3);
        final Map<String, Integer> scaled = PocketSlots.scaledByStackSize(single, 5);
        SimpleAssert.eq(40, scaled.get("aer"), "8 × 5");
        SimpleAssert.eq(15, scaled.get("ignis"), "3 × 5");
        SimpleAssert.eq(
            40,
            PocketSlots.scaledByStackSize(scaled, 0)
                .get("aer"),
            "★叠数非正按 ×1 计：既不抹零也不二次放大");
        SimpleAssert.eq(
            40,
            PocketSlots.scaledByStackSize(scaled, -7)
                .get("aer"),
            "负叠数同样按 ×1（外来入参不得造出负点数）");
        SimpleAssert.eq(
            2,
            PocketSlots.scaledByStackSize(scaled, 1)
                .size(),
            "放大只动值、不动条目数");
    }

    // ================================================================== R87 批（B 切片）：源质 GUI 面修复
    //
    // carriesTag 晶族特判（R87-e）/ 声明 tag 保格（R87-f）/ 结晶点击入槽判定流四态（R87-d）。
    // 共同点：这三件事都在纯 JVM 可达的判定面上（晶识别走 StubGate.capacityOf、保格走谓词注入、
    // intake 走纯静态判定），实机面（点击派发、聊天播报、遮罩渲染）不在此声称已验证。

    /**
     * ★R87-e（缺陷 4a）：{@code carriesTag} 对<b>晶族</b>（{@code capacityOf == CRYSTAL_CAPACITY}）
     * 恒视为含本格 tag——NEI 面板给的是无 NBT 裸晶 + TC 把晶的蒸馏注册成空表 ⇒ 两条探针结构性恒空，
     * 不特判就是"拖晶恒拒且拒收被静默吞"。★空格（cellTag null/空）的拒收原样保留（R86 裁定不放开）。
     */
    private static void essenceCarriesTagCrystalFamilyPassesOnlyStockedCells() {
        final ItemStack bareCrystal = stack(1);
        final ItemStack vessel = stack(1);
        final StubGate gate = new StubGate()
            // 晶：内容只走 readContainer（NEI 裸晶 ⇒ EMPTY，模拟"无 NBT"）；绝不登记进 distill 表
            .putCrystal(bareCrystal, TaumAspectAmounts.EMPTY)
            .putContainer(vessel, TaumAspectAmounts.of(new String[] { "aer" }, new int[] { 7 }));
        SimpleAssert.eq(TaumDistillRules.CRYSTAL_CAPACITY, gate.capacityOf(bareCrystal), "前置：这一栈确实被判成晶族（否则本用例在钉一条假分支）");
        SimpleAssert
            .that(NekoEssenceGhostCell.carriesTag(bareCrystal, "ignis", gate), "★晶 × 有货格（ignis）⇒ 收（按物品形状放行，不再看 NBT）");
        SimpleAssert.that(
            NekoEssenceGhostCell.carriesTag(bareCrystal, "aer", gate),
            "★晶 × 另一种有货格（aer）⇒ 同样收：目标 tag = 声明格自己的 tag，晶不自带归属");
        SimpleAssert.eq(
            Boolean.FALSE,
            NekoEssenceGhostCell.carriesTag(bareCrystal, null, gate),
            "★空格（cellTag null）仍拒：R86 裁定不放开");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.carriesTag(bareCrystal, "", gate), "★空格（cellTag 空串）仍拒");
        // 非晶路径零变化：罐子仍按容器内容判，不含本格 tag 的仍拒（负控）
        SimpleAssert.eq(
            Boolean.FALSE,
            NekoEssenceGhostCell.carriesTag(vessel, "ignis", gate),
            "★非晶物品仍按两条探针判：只有 aer 的罐子声明不了 ignis 格");
    }

    /**
     * ★R87-f（缺陷 4b）：声明保格谓词注入后——清零支：声明中的 tag 保留格位（空置+遮罩锚点不漂），
     * 未声明 tag 照旧 R86 腾格；再入账幂等落回<b>原格</b>。折叠支：{@code readFrom} 的旧档空洞折叠
     * <b>只对无声明的空洞生效</b>（"声明中清零但格位保留"从历史残留变成合法现役状态）。
     */
    private static void essenceDeclaredTagKeepsCellOnDrainAndFold() {
        final PocketEssenceStore store = new PocketEssenceStore();
        store.add("ignis", 12);
        store.add("aer", 30);
        store.setDeclaredTagProbe(tag -> "ignis".equals(tag));
        SimpleAssert.eq(0, store.cellOf("ignis"), "前置：ignis 占 0 格");
        SimpleAssert.eq(12, store.extract("ignis", 999), "声明格清零照常给点数");
        SimpleAssert.eq(0, store.get("ignis"), "amounts 摘除照旧");
        SimpleAssert.eq(0, store.cellOf("ignis"), "★声明中的 tag 保留格位（不再 R86 腾格）");
        SimpleAssert.eq("ignis", store.tagAtCell(0), "归属表那一行还在 ⇒ 该格空置 + 遮罩锚点不漂");
        SimpleAssert.eq(2, store.assignedCellCount(), "占格计数含空置的声明格");
        // 未声明 tag 照旧腾格（R86 逐字保留）
        SimpleAssert.eq(30, store.extract("aer", 999), "未声明格清零照常给点数");
        SimpleAssert.eq(-1, store.cellOf("aer"), "★未声明 tag 清零仍腾格");
        // ---- 折叠支：先落一份「声明中清零、格位保留」的档（此刻 ignis = 0 点且仍占 0 格）----
        final NBTTagCompound root = new NBTTagCompound();
        store.writeTo(root);
        final PocketEssenceStore kept = PocketEssenceStore.readFrom(root, tag -> "ignis".equals(tag));
        SimpleAssert.eq(0, kept.cellOf("ignis"), "★readFrom 折叠 × 声明 ⇒ 保留（合法现役状态不被吃掉）");
        final PocketEssenceStore folded = PocketEssenceStore.readFrom(root);
        SimpleAssert.eq(-1, folded.cellOf("ignis"), "readFrom 折叠 × 无声明（null 谓词）⇒ 照折（R86 行为逐字一致）");
        // 再入账幂等落回原格（遮罩归位的主路径从此恒命中）
        store.add("ignis", 5);
        SimpleAssert.eq(0, store.cellOf("ignis"), "★同 tag 再入账经 assignCell 幂等支落回原格");
        // 无声明谓词在场时，未声明 tag 的历史空洞同样照折（只对声明开例外，不对折叠整体放水）
        final NBTTagCompound legacy = new NBTTagCompound();
        final PocketEssenceStore seeded = new PocketEssenceStore();
        seeded.add("ignis", 12);
        seeded.writeTo(legacy);
        final NBTTagList holes = new NBTTagList();
        holes.appendTag(new NBTTagString("ignis"));
        holes.appendTag(new NBTTagString("terra"));
        legacy.setTag(PocketConstants.ESSENCE_CELL_ORDER, holes);
        final PocketEssenceStore mixed = PocketEssenceStore.readFrom(legacy, tag -> "ignis".equals(tag));
        SimpleAssert.eq(0, mixed.cellOf("ignis"), "有货的归属照回来");
        SimpleAssert.eq(-1, mixed.cellOf("terra"), "★无声明的历史空洞（terra）折叠不因谓词在场而放水");
    }

    /**
     * ★R87-d（缺陷 1）：点击入槽的服务端判定流<b>四态</b>（carried 桩件 + StubGate）：
     * 非晶 / 无归属 / 余量不足（晶分毫未动）/ 成功（整叠换点数、tag 取晶自带）。
     * 各态的 lang 键一并对账（回执文案键与判定态不得错位）。
     */
    private static void essenceIntakeCrystalFromCursorFourStates() {
        // ---- 态 1：NOT_CRYSTAL（空手 / 非晶）----
        final PocketEssenceStore store = new PocketEssenceStore();
        SimpleAssert.eq(
            PocketEssenceIntake.Outcome.NOT_CRYSTAL,
            PocketEssenceIntake.intake(null, store, new StubGate()).outcome,
            "空游标 ⇒ 不是晶");
        final ItemStack plain = stack(4);
        SimpleAssert.eq(
            PocketEssenceIntake.Outcome.NOT_CRYSTAL,
            PocketEssenceIntake.intake(plain, store, new StubGate()).outcome,
            "非晶物品 ⇒ 不是晶");
        SimpleAssert.eq(0, store.totalPoints(), "态 1 分毫不动");
        // ---- 态 2：NO_OWNER（晶存在但内容空 = 无源质归属）----
        final StubGate emptyGate = new StubGate();
        final ItemStack blank = stack(3);
        emptyGate.putCrystal(blank, TaumAspectAmounts.EMPTY);
        final PocketEssenceIntake.Result noOwner = PocketEssenceIntake.intake(blank, store, emptyGate);
        SimpleAssert.eq(PocketEssenceIntake.Outcome.NO_OWNER, noOwner.outcome, "无 NBT 裸晶 ⇒ 没有归属");
        SimpleAssert.eq("gtit.pocket.essence.intake.no_owner", noOwner.langKey(), "态 2 回执键对账");
        SimpleAssert.eq(3, blank.stackSize, "态 2 晶分毫未动（本路径不替 TC 随机赋型）");
        // ---- 态 3：NO_ROOM（全有全无失败，源质表与晶都不动）----
        final StubGate gate = new StubGate();
        final ItemStack crystals = stack(10);
        gate.putCrystal(crystals, TaumAspectAmounts.of(new String[] { "ignis" }, new int[] { 1 }));
        final PocketEssenceStore full = new PocketEssenceStore();
        full.add("ignis", PocketConstants.ESSENCE_CAP_PER_TAG - 4);
        final PocketEssenceIntake.Result noRoom = PocketEssenceIntake.intake(crystals, full, gate);
        SimpleAssert.eq(PocketEssenceIntake.Outcome.NO_ROOM, noRoom.outcome, "10 点 > 余 4 点 ⇒ 余量不足");
        SimpleAssert.eq("gtit.pocket.essence.intake.no_room", noRoom.langKey(), "态 3 回执键对账");
        SimpleAssert.eq(PocketConstants.ESSENCE_CAP_PER_TAG - 4, full.get("ignis"), "★态 3 源质表分毫未动");
        SimpleAssert.eq(10, crystals.stackSize, "★态 3 晶分毫未动（不截断消耗，R45c/FIX-6 同源纪律）");
        // ---- 态 4：ACCEPTED（整叠入账，tag = 晶自带）----
        final PocketEssenceIntake.Result ok = PocketEssenceIntake.intake(crystals, store, gate);
        SimpleAssert.eq(PocketEssenceIntake.Outcome.ACCEPTED, ok.outcome, "64 格空余装得下 10 点 ⇒ 入账");
        SimpleAssert.that(ok.accepted(), "accepted() 是调用方清游标的唯一判据");
        SimpleAssert.eq("ignis", ok.tag, "★目标 tag = 晶自带的 tag");
        SimpleAssert.eq(10, ok.points, "点数 = 单件 amount × 叠数");
        SimpleAssert.eq("gtit.pocket.essence.intake.ok", ok.langKey(), "态 4 回执键对账（成功条含 tag 与点数）");
        SimpleAssert.eq(10, store.get("ignis"), "源质表真的进了点数");
        // 失败三键（态 1）最后对账，凑齐四态
        SimpleAssert.eq(
            "gtit.pocket.essence.intake.not_crystal",
            PocketEssenceIntake.intake(plain, store, gate)
                .langKey(),
            "态 1 回执键对账");
    }

    // ------------------------------------------------------------------ 桩件与工具

    /**
     * 源质探针桩件（D 批）：生产实现 {@code EssenceGate.TAUM} 在 Thaumcraft 缺席的测试 JVM 里
     * <b>恒</b>返回"非容器 / 空 / 无产物"，拿它跑蒸馏与注入的用例会得到一片假绿
     * （与 R59b 偏离① 里 {@code PocketCellProbe} 的情况同形）。因此分流与两条支路都收这个入参。
     * <p>
     * 额外记录两件事，专门用来把"改述后的禁令"变成可执行断言：
     * <ul>
     * <li>{@link #aspectQueries}：谁被问过产物（容器出现在这里就是违规）；</li>
     * <li>{@link #drainCalls}：抽干被调了几次（全有全无预检失败时必须为 0）。</li>
     * </ul>
     */
    private static final class StubGate implements EssenceGate {

        private final Map<ItemStack, TaumAspectAmounts> containers = new IdentityHashMap<>();
        private final Map<ItemStack, TaumAspectAmounts> distillTable = new IdentityHashMap<>();
        final List<ItemStack> aspectQueries = new ArrayList<>();
        final List<ItemStack> drainQueries = new ArrayList<>();
        int drainCalls;
        /** 被问产物的<b>容器</b>次数 —— 恒应为 0（R44c/R63b）。 */
        int containerQueries;

        StubGate putContainer(ItemStack stack, TaumAspectAmounts content) {
            containers.put(stack, content);
            return this;
        }

        StubGate putDistill(ItemStack stack, TaumAspectAmounts aspects) {
            distillTable.put(stack, aspects);
            return this;
        }

        @Override
        public boolean isContainer(ItemStack stack) {
            return stack != null && containers.containsKey(stack);
        }

        @Override
        public TaumAspectAmounts readContainer(ItemStack stack) {
            final TaumAspectAmounts content = stack == null ? null : containers.get(stack);
            return content == null ? TaumAspectAmounts.EMPTY : content;
        }

        @Override
        public TaumAspectAmounts drainContainer(ItemStack stack) {
            drainCalls++;
            drainQueries.add(stack);
            final TaumAspectAmounts content = readContainer(stack);
            if (!content.isEmpty()) {
                // 抽干后容器变空（真实现是就地改写 stack 的 NBT/meta）
                containers.put(stack, TaumAspectAmounts.EMPTY);
            }
            return content;
        }

        @Override
        public TaumAspectAmounts aspectsOf(ItemStack stack) {
            aspectQueries.add(stack);
            if (isContainer(stack)) {
                containerQueries++;
            }
            final TaumAspectAmounts aspects = stack == null ? null : distillTable.get(stack);
            return aspects == null ? TaumAspectAmounts.EMPTY : aspects;
        }

        /**
         * ★R86（缺陷 2）：这一栈是不是"晶化源质"（整叠消耗那一支的入口判据）。
         * 用身份集而不是 {@code instanceof} 某个假物品类：桩件里同一假物品既要能扮晶也要能扮瓶。
         */
        private final java.util.Set<ItemStack> crystals = java.util.Collections
            .newSetFromMap(new IdentityHashMap<ItemStack, Boolean>());

        /** ★R86：把某栈登记成晶化源质（内容 = <b>单件</b>那份 aspect，整叠共享一份）。 */
        StubGate putCrystal(ItemStack stack, TaumAspectAmounts single) {
            crystals.add(stack);
            return putContainer(stack, single);
        }

        @Override
        public int capacityOf(ItemStack stack) {
            if (stack == null) {
                return TaumDistillRules.CAPACITY_NOT_A_CONTAINER;
            }
            if (crystals.contains(stack)) {
                return TaumDistillRules.CRYSTAL_CAPACITY;
            }
            return containers.containsKey(stack) ? TaumDistillRules.PHIAL_CAPACITY
                : TaumDistillRules.CAPACITY_NOT_A_CONTAINER;
        }
    }

    /** 普通物品栈（每枚都是<b>不同对象</b>，供桩件按身份查表；damage 固定 0）。 */
    private static ItemStack stack(int size) {
        return new ItemStack(FakePlainItem.INSTANCE, Math.max(1, size), 0);
    }

    /**
     * 指定 damage 的普通物品栈（R83 A2 起蒸馏的<b>组身份 = item + damage</b> ⇒
     * 一个用例里要造"两组"必须换 damage，只换对象会得到一组）。
     */
    private static ItemStack stackDistill(int size, int damage) {
        return new ItemStack(FakePlainItem.INSTANCE, Math.max(1, size), damage);
    }

    /**
     * 未定位形态的绑定（右下绑定格的正常入口）。
     */
    private static boolean bind(PocketCellBindings bindings, String uuid) {
        return bindings.bind(uuid, PocketConstants.MODE_DISK_UUID);
    }

    private static PocketCellBindings bindingsOf(String... uuids) {
        final PocketCellBindings bindings = new PocketCellBindings();
        for (String uuid : uuids) {
            bind(bindings, uuid);
        }
        return bindings;
    }

    private static String uuidOf(int i) {
        return new UUID(0L, i).toString();
    }

    private static NBTTagCompound bindingEntry(String uuid) {
        final NBTTagCompound entry = new NBTTagCompound();
        entry.setString(PocketConstants.ENTRY_ID, uuid);
        entry.setByte(PocketConstants.ENTRY_MODE, PocketConstants.MODE_DISK_UUID);
        return entry;
    }

    private static NBTTagList listOf(NBTTagCompound... entries) {
        final NBTTagList list = new NBTTagList();
        for (NBTTagCompound entry : entries) {
            list.appendTag(entry);
        }
        return list;
    }

    private static ItemStack cellStack(String diskuuid) {
        final ItemStack stack = new ItemStack(FakeCellItem.INSTANCE, 1, 0);
        stack.stackTagCompound = new NBTTagCompound();
        stack.stackTagCompound.setString(InfinityCellConstants.DISKUUID, diskuuid);
        return stack;
    }

    /** 非元件栈（探针必须认不出来）。 */
    private static ItemStack plainStack() {
        return new ItemStack(FakePlainItem.INSTANCE, 1, 0);
    }

    /** 只满足 {@code IInfinityCellItem} 身份判定，不触碰任何元件内容逻辑。 */
    private static final class FakeCellItem extends Item implements IInfinityCellItem {

        static final FakeCellItem INSTANCE = new FakeCellItem();

        @Override
        public IMEInventoryHandler<?> getInventoryHandler(ItemStack o, ISaveProvider container, EntityPlayer player) {
            return null;
        }

        @Override
        public StorageChannel getChannel() {
            return StorageChannel.ITEMS;
        }
    }

    /** 普通物品：探针必须跳过它。 */
    private static final class FakePlainItem extends Item {

        static final FakePlainItem INSTANCE = new FakePlainItem();
    }

    /**
     * 假宿主：既是 {@code TileEntity}（探针生产路径的硬门禁），又是 {@code IInventory}（扫槽与复验的对象），
     * 同时充当 {@link PocketCellProbe.HostResolver} 的测试实现——把"坐标 ↔ 世界"这一段唯一需要真
     * {@code World} 的触点顶掉，其余判定（扫槽得 slot、比身份、三态收敛）走的仍是生产代码。
     */
    private static final class FakeHost extends TileEntity implements IInventory, PocketCellProbe.HostResolver {

        static final int DIM = 0;
        static final int AT_X = 64;
        static final int AT_Y = 70;
        static final int AT_Z = -32;

        final ItemStack[] stacks = new ItemStack[8];
        boolean chunkLoaded = true;
        /** 置 false 模拟"坐标处已不是容器"（换了 tile）。 */
        boolean vanished = false;

        @Override
        public PocketCellProbe.Located locate(Object container) {
            // 结构上照抄生产门禁：只有 TileEntity 本体可定位，其余（null、IO 端口、tooltip 宿主）跳过
            return container instanceof TileEntity && container == this
                ? new PocketCellProbe.Located(DIM, AT_X, AT_Y, AT_Z, PocketCellProbe.SLOT_UNKNOWN)
                : null;
        }

        @Override
        public boolean chunkLoaded(PocketCellProbe.Located at) {
            return chunkLoaded;
        }

        @Override
        public Object hostAt(PocketCellProbe.Located at) {
            return vanished ? null : this;
        }

        @Override
        public int getSizeInventory() {
            return stacks.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stacks[slot];
        }

        @Override
        public ItemStack decrStackSize(int slot, int count) {
            return null;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            return null;
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            stacks[slot] = stack;
        }

        @Override
        public String getInventoryName() {
            return "fakeDrive";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return true;
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return true;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return true;
        }
    }

    /**
     * 纯 JVM 桩件：由容量/拒收集合驱动注入结论，并记录 {@code announce} 调用与本轮服务过的通道。
     * <p>
     * 与真实实现同构的一点很关键：{@code inject} <b>每次回读槽位的活数量</b>（真实实现是回读
     * {@code player.inventory}），因此一批里第二枚元件轮到同一槽时只会拿到 0，不会重复搬运。
     * 真实实现见 {@link PocketAeChannelOps}（触达 AE2，本套件不覆盖）。
     */
    private static final class StubOps implements PocketChannelOps {

        long nowMs = BASE_MS;
        long tick = 100L;
        /** (元件#通道) → 剩余可收点数；未登记的桶视为无限容量。 */
        final Map<String, Integer> capacity = new LinkedHashMap<>();
        /** 被分区 WHITELIST 挡在外面（或 BLACKLIST 命中）的内容标识。 */
        final Set<String> rejected = new LinkedHashSet<>();
        /** 失联元件集合。 */
        final Set<String> lost = new LinkedHashSet<>();
        final List<SourceSlot> sources = new ArrayList<>();
        final List<List<Delta>> announcements = new ArrayList<>();
        final List<String> servedChannels = new ArrayList<>();
        /** ★R87：每次注入被叫到的来源载荷键（反成环铁律的"从未被投递"证据面）。 */
        final List<String> servedKeys = new ArrayList<>();
        int injectCalls;
        /** 抽取方向的调用记录（D 批拉取模式用）：载荷键 + 该声明占用的槽号。 */
        final List<String> extractCalls = new ArrayList<>();
        final List<Integer> extractSlots = new ArrayList<>();
        /** 元件侧各载荷键还有多少可抽（拉取模式的"源"）。 */
        final Map<String, Integer> extractable = new LinkedHashMap<>();
        /**
         * ★R85：按载荷键<b>钉死</b>抽取回执（0 件成交），只为让 {@code runRefillBatch} 的停批面可在纯
         * JVM 里被驱动（真实实现的回执来自 AE2 handler，套件拿不到）。空表 = 本字段完全不影响旧行为。
         */
        final Map<String, PocketReceipt> forcedExtract = new LinkedHashMap<>();
        /** ★R87：按载荷键钉死注入回执（0 件成交），驱动「LOST 仍停批 / FULL 不停批」的判别面。 */
        final Map<String, PocketReceipt> forcedInject = new LinkedHashMap<>();

        @Override
        public Outcome extract(PocketFilterConfig.Filter filter, String diskuuid, int count) {
            extractCalls.add(filter.key());
            extractSlots.add(filter.slotIndex());
            final PocketReceipt forced = forcedExtract.get(filter.key());
            if (forced != null) {
                return new Outcome(forced, 0);
            }
            final Integer avail = extractable.get(filter.key());
            if (avail == null || avail <= 0) {
                return new Outcome(PocketReceipt.OK, 0);
            }
            final int moved = Math.min(avail, count <= 0 ? avail : count);
            extractable.put(filter.key(), avail - moved);
            return new Outcome(PocketReceipt.OK, moved);
        }

        void fillSources(int... counts) {
            sources.clear();
            for (int i = 0; i < counts.length; i++) {
                sources.add(new SourceSlot(i, "i:1:0:", counts[i]));
            }
        }

        void fillSources(String contentKey, int count) {
            sources.clear();
            sources.add(new SourceSlot(0, contentKey, count));
        }

        /** ★R87：按来源类别构造一条来源（反成环铁律的驱动面：FLUID/ESSENCE 来源按语义载荷匹配）。 */
        void fillSource(int slot, String contentKey, int count, SourceKind kind) {
            sources.clear();
            sources.add(new SourceSlot(slot, contentKey, count, kind));
        }

        void addSource(int slot, String contentKey, int count, SourceKind kind) {
            sources.add(new SourceSlot(slot, contentKey, count, kind));
        }

        /** 槽位活数量：真实实现读的是背包，桩件读的是本表。 */
        private int liveCount(int slot) {
            for (SourceSlot source : sources) {
                if (source.slot == slot) {
                    return source.count;
                }
            }
            return 0;
        }

        private void settle(int slot, int left) {
            for (int i = 0; i < sources.size(); i++) {
                if (sources.get(i).slot == slot) {
                    sources.set(i, new SourceSlot(slot, sources.get(i).contentKey, left, sources.get(i).kind));
                }
            }
        }

        @Override
        public long nowMs() {
            return nowMs;
        }

        @Override
        public long currentTick() {
            return tick;
        }

        @Override
        public boolean isCellLost(String diskuuid) {
            return lost.contains(diskuuid);
        }

        @Override
        public List<String> channelIdsOf(String diskuuid) {
            return lost.contains(diskuuid) ? Collections.<String>emptyList() : CHANNELS;
        }

        @Override
        public List<SourceSlot> snapshotSources() {
            final List<SourceSlot> copy = new ArrayList<>(sources.size());
            for (SourceSlot source : sources) {
                // ★R87：kind 必须原样带回——反成环铁律的匹配面在 runner 侧读它
                copy.add(new SourceSlot(source.slot, source.contentKey, source.count, source.kind));
            }
            return copy;
        }

        @Override
        public Outcome inject(SourceSlot source, String diskuuid, String typeId) {
            injectCalls++;
            servedChannels.add(typeId);
            servedKeys.add(source.contentKey);
            // ★R87：镜像真实实现的 typeId 门（PocketAeChannelOps 的 R84 成对早退 + R86 源质回落）——
            // 物品/源质来源只走物品通道、流体来源只走流体通道，其余按"无事可做"返回 OK；
            // 否则 R87-b 的全通道轮转会把来源灌进不相干的通道，桩件就与真实行为分叉了。
            final String gateChannel = source.contentKey.startsWith("i:") ? "item"
                : source.contentKey.startsWith("f:") ? "fluid" : source.contentKey.startsWith("e:") ? "item" : null;
            if (gateChannel != null && !gateChannel.equals(typeId)) {
                return new Outcome(PocketReceipt.OK, 0);
            }
            final PocketReceipt forced = forcedInject.get(source.contentKey);
            if (forced != null) {
                return new Outcome(forced, 0);
            }
            final int live = liveCount(source.slot);
            if (live <= 0) {
                // 槽位已被本轮前一次搬运掏空（或玩家已取走）：不改写任何状态
                return new Outcome(PocketReceipt.OK, 0);
            }
            if (rejected.contains(source.contentKey)) {
                return new Outcome(PocketReceipt.FILTER_REJECTED, 0);
            }
            final String bucket = diskuuid + "#" + typeId;
            final Integer free = capacity.get(bucket);
            if (free != null && free <= 0) {
                return new Outcome(PocketReceipt.FULL, 0);
            }
            final int requested = free == null ? live : Math.min(free, live);
            if (free != null) {
                capacity.put(bucket, free - requested);
            }
            settle(source.slot, live - requested);
            return new Outcome(requested >= live ? PocketReceipt.OK : PocketReceipt.PARTIAL, requested);
        }

        @Override
        public void announce(List<Delta> deltas) {
            announcements.add(new ArrayList<>(deltas));
        }
    }

    // ================================================================== S-E 批 · ghost 请求文法 + 流体条/源质格两个拖入入口

    /**
     * ghost 请求文法与区域字母（SET/CLR 两条式样、letterOf/kindOf 互逆、字母取自 PocketConstants）。
     * <p>
     * ★这条同时钉住「文法字母不分散在两端写死」（R58b 的纪律从成本常量扩到文法字母）。
     */
    private static void ghostRequestGrammarAndLetters() {
        SimpleAssert.eq(
            "SET|7|i:2621:7:AAA",
            PocketGhostRequest.setRequest(7, PocketFilterConfig.itemKey(2621, 7, "AAA")),
            "SET 式样 = SET|slot|载荷键");
        SimpleAssert.eq("CLR|0|F", PocketGhostRequest.clearRequest(Kind.FLUID, 0), "CLR 式样 = CLR|slot|区域字母（流体条 = F）");
        SimpleAssert.eq("CLR|127|I", PocketGhostRequest.clearRequest(Kind.ITEM, 127), "中栏解绑的字母是 I");
        SimpleAssert.eq("CLR|47|E", PocketGhostRequest.clearRequest(Kind.ESSENCE, 47), "源质格解绑的字母是 E");
        for (Kind kind : Kind.values()) {
            final String letter = PocketGhostRequest.letterOf(kind);
            SimpleAssert.that(letter.length() == 1, "区域字母必须恰好一个字符：" + kind);
            SimpleAssert.eq(kind, PocketGhostRequest.kindOf(letter), "letterOf 与 kindOf 互逆：" + kind);
        }
        SimpleAssert.eq(null, PocketGhostRequest.kindOf("X"), "不认识的字母 ⇒ null（上层据此拒收）");
        SimpleAssert.eq(null, PocketGhostRequest.kindOf(null), "null 字母 ⇒ null");
        SimpleAssert.eq("", PocketGhostRequest.letterOf(null), "null 区域 ⇒ 空字母");
        SimpleAssert.eq(PocketConstants.GHOST_KIND_ITEM, PocketGhostRequest.letterOf(Kind.ITEM), "I 只来自常量");
        SimpleAssert.eq(PocketConstants.GHOST_KIND_FLUID, PocketGhostRequest.letterOf(Kind.FLUID), "F 只来自常量");
        SimpleAssert.eq(PocketConstants.GHOST_KIND_ESSENCE, PocketGhostRequest.letterOf(Kind.ESSENCE), "E 只来自常量");
    }

    /**
     * ★CLR 带 kind 后三类互不覆盖：三个区域各自的第 0 格都有声明时，解绑只会掉自己那一条。
     * <p>
     * 这条就是 R70 给 CLR 加第三段的理由本身（裸 CLR 分不清中栏第 0 格与流体条第 0 格，
     * 是 R59b 偏离④复合键在请求文法上的对偶面）。
     */
    private static void ghostClearCarriesKindNoCrossClobber() {
        final PocketFilterConfig config = new PocketFilterConfig();
        SimpleAssert.that(set(config, 0, PocketFilterConfig.itemKey(2621, 7, "")).changed(), "中栏第 0 格 SET 应生效");
        SimpleAssert
            .that(set(config, 0, PocketFilterConfig.fluidKey("water")).changed(), "流体条第 0 格 SET 应生效（与中栏同索引、不同区域）");
        SimpleAssert
            .that(set(config, 0, PocketFilterConfig.essenceKey("essentia", "aer")).changed(), "源质格第 0 格 SET 应生效（同上）");
        SimpleAssert.eq(3, config.size(), "三条各占各的区域，互不覆盖");

        for (Kind kind : Kind.values()) {
            final PocketGhostRequest.Decision cleared = PocketGhostRequest
                .apply(PocketGhostRequest.clearRequest(kind, 0), config);
            SimpleAssert.eq(PocketGhostRequest.Outcome.APPLIED, cleared.outcome, "解绑应生效：" + kind);
            SimpleAssert.eq(kind, cleared.kind, "解绑命中的区域 = 请求里那个字母：" + kind);
            SimpleAssert.eq(0, cleared.slotIndex, "解绑命中的槽号");
            SimpleAssert.eq(null, config.at(kind, 0), "该区域第 0 格应已无声明：" + kind);
            SimpleAssert.eq(2, config.size(), "★另两个区域的第 0 格一条都没被误清：" + kind);
            // 把刚清掉的那条补回去，让下一轮仍在"三条齐"的状态下检验
            set(config, 0, payloadOf(kind));
            SimpleAssert.eq(3, config.size(), "回填后仍是三条（本轮结论不受回填影响）");
        }

        // 逐区域清干净，验证每条声明都只能由自己区域的字母解掉
        for (Kind kind : Kind.values()) {
            SimpleAssert.that(
                PocketGhostRequest.apply(PocketGhostRequest.clearRequest(kind, 0), config)
                    .changed(),
                "按区域逐个解绑：" + kind);
        }
        SimpleAssert.eq(0, config.size(), "三条都解绑后表为空");
    }

    /** 旧文法（CLR 不带区域字母）必须整条拒收，而不是"默认清中栏"。 */
    private static void ghostClearWithoutKindIsRejected() {
        final PocketFilterConfig config = new PocketFilterConfig();
        SimpleAssert.that(set(config, 0, PocketFilterConfig.fluidKey("lava")).changed(), "先声明流体条");
        for (String bare : new String[] { "CLR|0", "CLR|0|", "CLR|0|?", "CLR|0|item", "CLR|0|II" }) {
            final PocketGhostRequest.Decision decision = PocketGhostRequest.apply(bare, config);
            SimpleAssert.eq(PocketGhostRequest.Outcome.REJECTED, decision.outcome, "缺或错区域字母必须拒收：" + bare);
            SimpleAssert.that(config.at(Kind.FLUID, 0) != null, "★拒收不得动到已存在的声明：" + bare);
            SimpleAssert.that(config.at(Kind.ITEM, 0) == null, "★也不得顺手往中栏写或清任何东西：" + bare);
        }
        SimpleAssert.eq(1, config.size(), "五轮拒收后表里仍是那一条流体声明");
    }

    /** ★SET 的 kind 由载荷键前缀决定：流体落 FLUID 空间、源质落 ESSENCE 空间、物品落 ITEM 空间。 */
    private static void ghostSetDispatchesKindFromPayload() {
        final PocketFilterConfig config = new PocketFilterConfig();
        final PocketGhostRequest.Decision fluid = set(config, 0, PocketFilterConfig.fluidKey("lava"));
        SimpleAssert.eq(PocketGhostRequest.Outcome.APPLIED, fluid.outcome, "流体声明应生效");
        SimpleAssert.eq(Kind.FLUID, fluid.kind, "f 前缀 ⇒ Kind.FLUID（不再按「只有物品」一刀切拒收）");
        SimpleAssert.eq(0, fluid.slotIndex, "流体条槽号原样回报");
        SimpleAssert.eq(null, config.at(Kind.ITEM, 0), "★没有把流体声明错挂到中栏第 0 格");

        final PocketGhostRequest.Decision essence = set(config, 7, PocketFilterConfig.essenceKey("essentia", "ignis"));
        SimpleAssert.eq(PocketGhostRequest.Outcome.APPLIED, essence.outcome, "源质声明应生效");
        SimpleAssert.eq(Kind.ESSENCE, essence.kind, "e 前缀 ⇒ Kind.ESSENCE");
        SimpleAssert.eq(7, essence.slotIndex, "源质格格号原样回报");
        SimpleAssert.eq(null, config.at(Kind.ITEM, 7), "★没有把源质声明错挂到中栏第 7 格");

        final PocketGhostRequest.Decision item = set(config, 7, PocketFilterConfig.itemKey(2621, 7, "AAA"));
        SimpleAssert.eq(PocketGhostRequest.Outcome.APPLIED, item.outcome, "物品声明应生效");
        SimpleAssert.eq(Kind.ITEM, item.kind, "i 前缀 ⇒ Kind.ITEM");
        assertFilterEquals(7, Kind.ITEM, "i:2621:7:AAA", config.at(Kind.ITEM, 7));
        assertFilterEquals(7, Kind.ESSENCE, "e:essentia:ignis", config.at(Kind.ESSENCE, 7));
        assertFilterEquals(0, Kind.FLUID, "f:lava", config.at(Kind.FLUID, 0));
        SimpleAssert.eq(3, config.size(), "三个区域各一条（同为槽号 7 的 ITEM 与 ESSENCE 互不覆盖）");
    }

    /**
     * ★分区域越界拒收：三个区域各一个上界负例 + 各一个边界正例（★当前上界 135 / 18 / 72 = R80① 口径，旧口径 128 / 1 / 48 已被覆盖）。
     * <p>
     * 这正是被本批改掉的旧判定（越界只按中栏 {@code storage().getSlots()} 算 ⇒ 流体条与源质格的
     * 合法索引被当成越界拒收，而 5…47 这类"中栏内合法"的索引会被误收进错误区域）。
     */
    private static void ghostSetRegionBoundsRejected() {
        final PocketFilterConfig config = new PocketFilterConfig();
        // 正例：各区域最后一个合法索引
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            set(config, PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1, PocketFilterConfig.itemKey(1, 0, "")).outcome,
            "中栏最后一格（R80① 后是第 134 格）合法");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            set(config, PocketConstants.GHOST_FLUID_SLOT_LIMIT - 1, PocketFilterConfig.fluidKey("water")).outcome,
            "流体槽最后一格合法（R78② 后上界是 18 ⇒ 第 17 格）");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            set(config, PocketConstants.GHOST_FLUID_SLOT_LIMIT - 6, PocketFilterConfig.fluidKey("lava")).outcome,
            "流体槽第 0 格仍合法 ⇒ <b>旧档的 FLUID:0 键读回来还在自己的索引里</b>");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            set(
                config,
                PocketConstants.GHOST_ESSENCE_SLOT_LIMIT - 1,
                PocketFilterConfig.essenceKey("essentia", "aer")).outcome,
            "源质格上界前一格合法（R78② 后是 71）");
        SimpleAssert.eq(4, config.size(), "四个边界正例各占一格（流体侧多了「第 0 格仍合法」这一条）");

        // 负例：各区域第一个越界索引（★一律取常量上界本身，R80① 后是 135 / 18 / 72）
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            set(config, PocketConstants.GHOST_ITEM_SLOT_LIMIT, PocketFilterConfig.itemKey(1, 0, "")).outcome,
            "★中栏第 135 格（= 上界本身）越界 ⇒ 拒收（R80①：旧口径合法到 149，本轮收到 134）");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            set(config, PocketConstants.GHOST_FLUID_SLOT_LIMIT, PocketFilterConfig.fluidKey("water")).outcome,
            "★流体槽上界那一格越界 ⇒ 拒收（R78② 后是第 18 格；旧口径合法到 5、新口径合法到 17）");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            set(
                config,
                PocketConstants.GHOST_ESSENCE_SLOT_LIMIT,
                PocketFilterConfig.essenceKey("essentia", "aer")).outcome,
            "★源质格上界那一格越界 ⇒ 拒收（R78② 后是第 72 格）");
        SimpleAssert.eq(4, config.size(), "越界的三条一个字节都没写进表（仍是那四条正例）");

        // 旧判定的另一半危害：某个索引"对本区域非法、对中栏合法"时不得退化成写到中栏去。
        // ★中栏末格（R80① 后是 134）对流体区域是越界：合法索引各区域独立
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            set(config, PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1, PocketFilterConfig.fluidKey("water")).outcome,
            "流体槽没有中栏末格那一个号 ⇒ 拒收（不得退化成往中栏末格写）");
        SimpleAssert.eq(
            "i:1:0:",
            config.at(Kind.ITEM, PocketConstants.GHOST_ITEM_SLOT_LIMIT - 1)
                .key(),
            "★中栏末格上那条**物品**声明没被这条流体请求改写");
        SimpleAssert.eq(4, config.size(), "无副作用");

        // 负索引与"未设置"哨兵
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            set(config, PocketConstants.FILTER_SLOT_UNSET, PocketFilterConfig.itemKey(1, 0, "")).outcome,
            "槽号 -1（未设置哨兵）⇒ 拒收");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            set(config, -5, PocketFilterConfig.fluidKey("water")).outcome,
            "负槽号 ⇒ 拒收");
    }

    /** 载荷侧的拒收面：空流体名、空 typeId、解不出的串、非载荷段的键。 */
    private static void ghostSetEmptyOrForeignPayloadRejected() {
        final PocketFilterConfig config = new PocketFilterConfig();
        for (String payload : new String[] { "f:", "e::aer", "e:essentia:", "i:abc", "i:1", "f:water|extra",
            "z:whatever", "" }) {
            final PocketGhostRequest.Decision decision = set(config, 0, payload);
            SimpleAssert.eq(PocketGhostRequest.Outcome.REJECTED, decision.outcome, "载荷解不出或缺必要字段 ⇒ 拒收：" + payload);
        }
        SimpleAssert.eq(0, config.size(), "八次拒收后表仍为空（不留下任何永远抽不出东西的死声明）");
        // 空流体名与空 typeId 之所以必须拒：读档侧本来就是"缺键即丢条目"（PocketFilterConfig#readFrom），
        // 写入口若收下，ghost 表里就存着一条能显示、能同步、但抽取端 byId 恒 null 的不一致项
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            set(config, 0, PocketFilterConfig.fluidKey("water")).outcome,
            "补一条合法声明作为对照");
        SimpleAssert.eq(1, config.size(), "对照组生效");
    }

    /** 幂等面：重复 SET 同一载荷、重复 CLR 同一格都是 UNCHANGED（不写档、不刷虚化）。 */
    private static void ghostRequestRepeatIsUnchanged() {
        final PocketFilterConfig config = new PocketFilterConfig();
        final String key = PocketFilterConfig.essenceKey("essentia", "aer");
        SimpleAssert.eq(PocketGhostRequest.Outcome.APPLIED, set(config, 3, key).outcome, "首次 SET ⇒ APPLIED");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.UNCHANGED,
            set(config, 3, key).outcome,
            "同格重复拖入同一载荷 ⇒ UNCHANGED（同槽覆盖是合法语义，但不算状态改变了）");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            set(config, 3, PocketFilterConfig.essenceKey("essentia", "ignis")).outcome,
            "同格换成别的载荷 ⇒ APPLIED（覆盖确实改变了状态）");
        SimpleAssert.eq(1, config.size(), "同格始终只有一条声明");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            PocketGhostRequest.apply(PocketGhostRequest.clearRequest(Kind.ESSENCE, 3), config).outcome,
            "解绑生效一次");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.UNCHANGED,
            PocketGhostRequest.apply(PocketGhostRequest.clearRequest(Kind.ESSENCE, 3), config).outcome,
            "重复解绑 ⇒ UNCHANGED（无副作用）");
        SimpleAssert.eq(0, config.size(), "表回到空");
    }

    /** 文法侧的拒收面：空串、缺段、非数字槽号、未知操作码、大小写、null 表。 */
    private static void ghostRequestMalformedRejected() {
        final PocketFilterConfig config = new PocketFilterConfig();
        for (String request : new String[] { null, "", "SET", "SET|", "SET|0", "SET|x|f:water", "CLR", "CLR|",
            "RUN|0|i:1:0:", "|0|f:water", "clr|0|F", "set|0|f:water" }) {
            SimpleAssert.eq(
                PocketGhostRequest.Outcome.REJECTED,
                PocketGhostRequest.apply(request, config).outcome,
                "不合法请求必须拒收：<" + request + ">");
        }
        SimpleAssert.eq(0, config.size(), "拒收不留痕");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            PocketGhostRequest.apply("SET|0|f:water|extra", config).outcome,
            "载荷段带分隔符的假键 ⇒ 拒收（切分 limit=3 会让尾段整个落进流体名，故由「载荷键不含竖线」的式样不变量拦下）");
        // 表为 null（外来调用）不得炸
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            PocketGhostRequest.apply("SET|0|f:water", null).outcome,
            "filters 为 null ⇒ 拒收而不是 NPE");
    }

    /**
     * ★apply 之后的表过一遍 S2C blob 往返与落档往返（§2.3 的实测结论：编解码本来就覆盖三类，本批不动它）。
     * <p>
     * 覆盖点是"rebuild 单点"：服务端写入口与 blob 解码共用 {@code PocketGhostRequest.rebuildAt}，
     * 任何一侧再自己写一份 switch 就会在这里露出来。
     */
    private static void ghostAppliedSurvivesBlobRoundTrip() {
        final PocketFilterConfig config = new PocketFilterConfig();
        set(config, 127, PocketFilterConfig.itemKey(2621, 7, "AAA"));
        set(config, 0, PocketFilterConfig.fluidKey("water"));
        set(config, 47, PocketFilterConfig.essenceKey("essentia", "ignis"));
        SimpleAssert.eq(3, config.size(), "三个区域各一条");

        final PocketFilterConfig back = NekoPocketPanel.parseGhostBlob(NekoPocketPanel.ghostBlobOf(config));
        SimpleAssert.eq(3, back.size(), "blob 往返三类齐备");
        assertFilterEquals(127, Kind.ITEM, "i:2621:7:AAA", back.at(Kind.ITEM, 127));
        assertFilterEquals(0, Kind.FLUID, "f:water", back.at(Kind.FLUID, 0));
        assertFilterEquals(47, Kind.ESSENCE, "e:essentia:ignis", back.at(Kind.ESSENCE, 47));
        SimpleAssert.eq(
            NekoPocketPanel.ghostBlobOf(config),
            NekoPocketPanel.ghostBlobOf(back),
            "blob 文本二次生成逐字相同（顺序 = 插入序 = 补满序）");

        final NBTTagCompound root = new NBTTagCompound();
        config.writeTo(root);
        final PocketFilterConfig reloaded = PocketFilterConfig.readFrom(root);
        SimpleAssert.eq(3, reloaded.size(), "NBT 往返后三条都在");
        assertFilterEquals(0, Kind.FLUID, "f:water", reloaded.at(Kind.FLUID, 0));
        assertFilterEquals(47, Kind.ESSENCE, "e:essentia:ignis", reloaded.at(Kind.ESSENCE, 47));
        // 客户端那份镜像（blob 解码）过一遍 CLR：区域字母与 kind 必须一路上得来
        final PocketGhostRequest.Decision cleared = PocketGhostRequest
            .apply(PocketGhostRequest.clearRequest(Kind.FLUID, 0), back);
        SimpleAssert.eq(PocketGhostRequest.Outcome.APPLIED, cleared.outcome, "镜像表也能按 F 解绑流体条");
        SimpleAssert.eq(2, back.size(), "★镜像表的另外两条（同槽号 0 的东西都没有）不受影响");
    }

    /**
     * ★流体条拖入的"桶类容器反解出水 / 岩浆"与按序兜底、以及解不出即不收。
     * <p>
     * 边界如实声明（与 {@code extract_fluid_branch_moves_fluid} 同一理由）：真探针要碰
     * {@code FluidContainerRegistry} 与 {@code FluidRegistry}，后者在纯 JVM 里连类初始化都过不去
     * （本机实测 {@code FluidRegistry.<clinit>} 抛 NPE ⇒ 后续触点 {@code NoClassDefFoundError}）。
     * 故本用例分两半：①—④ 用桩件钉住探针链的<b>顺序、兜底、吞 Throwable 与拒收口径</b>（纯逻辑，
     * 也是唯一会被整合包 mod 差异影响的那一半）；⑤ 拿<b>真探针</b>钉"整条链跑完既不抛也不改栈"——
     * 这一条只有真探针测得到（桩件抛的异常是自己造的）。真水桶到底解不解得出水属<b>实机项</b>。
     */
    private static void fluidBarProbeChainOrderAndFallback() {
        final ItemStack waterBucket = new ItemStack(Items.water_bucket, 1);
        final ItemStack lavaBucket = new ItemStack(Items.lava_bucket, 1);
        final ItemStack plainBucket = new ItemStack(Items.bucket, 1);
        final NamedProbe firstLevel = new NamedProbe().put(waterBucket, "water")
            .put(lavaBucket, "lava");
        final NamedProbe secondLevel = new NamedProbe().put(plainBucket, "water");

        // ① 第一级命中即返回（注册过的满容器：水桶 ⇒ water、岩浆桶 ⇒ lava）
        SimpleAssert
            .eq("water", NekoPocketFluidSlot.firstFluidName(waterBucket, firstLevel, secondLevel), "满水桶 ⇒ water");
        SimpleAssert.eq(
            "lava",
            NekoPocketFluidSlot.firstFluidName(lavaBucket, firstLevel, secondLevel),
            "满岩浆桶 ⇒ lava（判据要求的「桶类容器反解出水与岩浆」）");
        SimpleAssert.eq(0, secondLevel.queries, "★第一级命中后不得再问第二级（顺序即优先级）");

        // ② 第一级解不出 ⇒ 第二级（流体方块）兜住
        SimpleAssert.eq(
            "water",
            NekoPocketFluidSlot.firstFluidName(plainBucket, firstLevel, secondLevel),
            "空桶在第一级解不出 ⇒ 由第二级兜住");
        SimpleAssert.eq(1, secondLevel.queries, "第二级被问了一次");

        // ③ 两级都解不出 ⇒ 空串（不收，NEI 继续拖着、不吃栈）
        SimpleAssert
            .eq("", NekoPocketFluidSlot.firstFluidName(plainBucket, new NamedProbe(), new NamedProbe()), "两级都空 ⇒ 空串");

        // ④ 探针抛 Throwable ⇒ 继续兜下一级，绝不冒到点击分发
        final NamedProbe hostile = new NamedProbe().hostile(waterBucket);
        SimpleAssert.eq(
            "lava",
            NekoPocketFluidSlot.firstFluidName(waterBucket, hostile, new NamedProbe().put(waterBucket, "lava")),
            "第一级抛 ⇒ 吞掉并继续问第二级（第三方容器实现不得成为 GUI 崩溃源）");
        SimpleAssert
            .eq("", NekoPocketFluidSlot.firstFluidName(waterBucket, hostile, new NamedProbe()), "全都抛或全解不出 ⇒ 空串（不向上抛）");

        // ⑤ 真探针链：纯 JVM 里必然解不出，但必须"跑完不抛、返回空串"
        SimpleAssert.eq(
            "",
            NekoPocketFluidSlot
                .firstFluidName(new ItemStack(Items.water_bucket, 1), NekoPocketFluidSlot.productionProbes()),
            "★真探针在纯 JVM 里全部解不出 ⇒ 空串（异常被吞、不冒到分发）");
        SimpleAssert.eq("", NekoPocketFluidSlot.firstFluidName(null, firstLevel), "null 栈 ⇒ 空串");
        SimpleAssert.eq("", NekoPocketFluidSlot.firstFluidName(waterBucket), "空探针链 ⇒ 空串");
    }

    /** 拖入 → 载荷键的接受面与拒收面（左键 / 灰显 / 右键 / 解不出名字），并接上服务端那半段。 */
    private static void fluidBarDragKeyAcceptAndReject() {
        SimpleAssert
            .eq("f:water", NekoPocketFluidSlot.ghostKeyFor(0, true, "water"), "左键 + 可用 + 解出水 ⇒ 载荷键 f:water（不自造第四种键格式）");
        SimpleAssert.eq("f:lava", NekoPocketFluidSlot.ghostKeyFor(0, true, "lava"), "岩浆同理");
        SimpleAssert.eq("", NekoPocketFluidSlot.ghostKeyFor(1, true, "water"), "★右键不设声明（解绑走 CLR）");
        SimpleAssert.eq("", NekoPocketFluidSlot.ghostKeyFor(2, true, "water"), "中键同理 ⇒ 不收");
        SimpleAssert.eq("", NekoPocketFluidSlot.ghostKeyFor(0, false, "water"), "★整栏灰显一律不收（R31 口径）");
        SimpleAssert.eq("", NekoPocketFluidSlot.ghostKeyFor(0, true, ""), "解不出流体名 ⇒ 不收");
        SimpleAssert.eq("", NekoPocketFluidSlot.ghostKeyFor(0, true, null), "null 名 ⇒ 不收");
        // 生成的键必须能被服务端那侧收下并落到 FLUID 空间（客户端半段 + 服务端半段接起来才是完整通路）
        final PocketFilterConfig config = new PocketFilterConfig();
        final String key = NekoPocketFluidSlot.ghostKeyFor(0, true, "water");
        final PocketGhostRequest.Decision applied = PocketGhostRequest
            .apply(PocketGhostRequest.setRequest(NekoPocketFluidSlot.FIRST_SLOT_INDEX, key), config);
        SimpleAssert.eq(PocketGhostRequest.Outcome.APPLIED, applied.outcome, "流体条的键可被服务端收下");
        SimpleAssert.eq(Kind.FLUID, applied.kind, "落在 FLUID 空间（不是中栏）");
        SimpleAssert.eq(0, NekoPocketFluidSlot.FIRST_SLOT_INDEX, "流体条唯一槽号 = 0（上界 1）");
        assertFilterEquals(0, Kind.FLUID, "f:water", config.at(Kind.FLUID, NekoPocketFluidSlot.FIRST_SLOT_INDEX));
        SimpleAssert.eq(null, config.at(Kind.ITEM, 0), "★中栏第 0 格没被流体拖入写上");
    }

    /** ★源质格：拖入物必须含本格 tag 才算声明；不含 ⇒ 不收（不吃栈）。 */
    private static void essenceCellTagMatchRequired() {
        final ItemStack firelog = stack(1);
        final ItemStack vessel = stack(1);
        final ItemStack junk = stack(1);
        final StubGate gate = new StubGate()
            .putDistill(firelog, TaumAspectAmounts.of(new String[] { "ignis" }, new int[] { 2 }))
            .putContainer(vessel, TaumAspectAmounts.of(new String[] { "aer" }, new int[] { 7 }));

        // 蒸馏产出命中（普通物品）与容器内容命中（罐/瓶）两条路都算"含该 tag"
        SimpleAssert.that(NekoEssenceGhostCell.carriesTag(firelog, "ignis", gate), "可蒸馏物含本格 ignis ⇒ 含");
        SimpleAssert.that(NekoEssenceGhostCell.carriesTag(vessel, "aer", gate), "源质容器含本格 aer ⇒ 含（R31 接口探测口径）");
        SimpleAssert.eq(
            Boolean.FALSE,
            NekoEssenceGhostCell.carriesTag(firelog, "aer", gate),
            "★拖来的东西不含本格 tag ⇒ 不含（不许把任意东西当源质声明）");
        SimpleAssert
            .eq(Boolean.FALSE, NekoEssenceGhostCell.carriesTag(vessel, "ignis", gate), "★容器侧同理：只有 aer 的罐子声明不了 ignis 格");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.carriesTag(junk, "ignis", gate), "无源质物品 ⇒ 不含");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.carriesTag(firelog, null, gate), "本格无 tag ⇒ 不含");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.carriesTag(firelog, "", gate), "空 tag ⇒ 不含");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.carriesTag(null, "ignis", gate), "null 栈 ⇒ 不含");
        SimpleAssert.eq(Boolean.FALSE, NekoEssenceGhostCell.carriesTag(firelog, "ignis", null), "null 探针 ⇒ 不含");
    }

    /** ★源质格匹配则落 ESSENCE 空间；不匹配 / 无通道 / 灰显 / 右键一律不收。 */
    private static void essenceGhostDragLandsEssenceSpace() {
        final String typeId = "essentia";
        SimpleAssert.eq(
            "e:essentia:ignis",
            NekoEssenceGhostCell.ghostKeyFor(0, true, true, "ignis", typeId),
            "匹配 ⇒ e:typeId:tag（键格式仍是 PocketFilterConfig 那三种之一）");
        SimpleAssert.eq("", NekoEssenceGhostCell.ghostKeyFor(0, true, false, "ignis", typeId), "★tag 不匹配 ⇒ 不收");
        SimpleAssert.eq("", NekoEssenceGhostCell.ghostKeyFor(1, true, true, "ignis", typeId), "右键 ⇒ 不收（解绑走 CLR|格号|E）");
        SimpleAssert.eq("", NekoEssenceGhostCell.ghostKeyFor(0, false, true, "ignis", typeId), "TC 不在场灰显 ⇒ 不收");
        SimpleAssert.eq(
            "",
            NekoEssenceGhostCell.ghostKeyFor(0, true, true, "ignis", ""),
            "没有任何已注册通道能吃下这一 tag ⇒ 不收（不写死声明再静默空转）");
        SimpleAssert.eq("", NekoEssenceGhostCell.ghostKeyFor(0, true, true, null, typeId), "本格无 tag ⇒ 不收");

        final PocketFilterConfig config = new PocketFilterConfig();
        final String key = NekoEssenceGhostCell.ghostKeyFor(0, true, true, "ignis", typeId);
        final PocketGhostRequest.Decision applied = PocketGhostRequest
            .apply(PocketGhostRequest.setRequest(13, key), config);
        SimpleAssert.eq(PocketGhostRequest.Outcome.APPLIED, applied.outcome, "服务端收下源质声明");
        SimpleAssert.eq(Kind.ESSENCE, applied.kind, "★落在 ESSENCE 空间");
        assertFilterEquals(13, Kind.ESSENCE, "e:essentia:ignis", config.at(Kind.ESSENCE, 13));
        SimpleAssert.eq(null, config.at(Kind.ITEM, 13), "★中栏第 13 格没被顺手写上");
        SimpleAssert.that(
            PocketGhostRequest.apply(PocketGhostRequest.clearRequest(Kind.ESSENCE, 13), config)
                .changed(),
            "E 字母只解源质格那一格");
        SimpleAssert.eq(0, config.size(), "整表清空");
    }

    /**
     * R71 附带修正：AE2 第三方通道的 id 是<b>该通道自取</b>的字符串，带命名空间冒号完全合法
     * （内置两枚是 item/fluid 无冒号，故这一形状只有 addon 侧会踩）。
     * 切分点取<b>最后</b>一个分隔符 ⇒ typeId 不被从中间切断、tag 不混入残段。
     */
    private static void essenceKeySurvivesNamespacedTypeId() {
        final String typeId = "thaumicessence:channel";
        final String key = PocketFilterConfig.essenceKey(typeId, "ignis");
        final PocketFilterConfig.Filter parsed = PocketFilterConfig.parseKey(key);
        SimpleAssert.that(parsed instanceof PocketFilterConfig.EssenceFilter, "带冒号的 typeId 仍可反解");
        final PocketFilterConfig.EssenceFilter essence = (PocketFilterConfig.EssenceFilter) parsed;
        SimpleAssert.eq(typeId, essence.typeId, "typeId 完整（indexOf 切法会只剩前段）");
        SimpleAssert.eq("ignis", essence.tag, "tag 不混入 typeId 残段");
        SimpleAssert.eq(key, essence.key(), "键逐字往返");
    }

    /** 一次 SET 请求（载荷键已备好）。 */
    private static PocketGhostRequest.Decision set(PocketFilterConfig config, int slot, String payloadKey) {
        return PocketGhostRequest.apply(PocketGhostRequest.setRequest(slot, payloadKey), config);
    }

    // ================================================================== R83 B1（缺陷 2 的执法点 + 需求 5 的"绑定格除外"）

    /**
     * ★服务端<b>开屏即</b>拒绝往绑定格里放件（R83 计划 §3 B1 的那条"必须排在翻 allowShiftTransfer 之前"的判据）。
     * <p>
     * 判据点是 {@code PocketInventory#newStorageGroup} 里 handler 自己的 {@code isItemValid}：
     * {@code ModularSlot.isItemValid} 的最后一环 → {@code SlotItemHandler.isItemValid}
     * （MUI2 {@code utils/item/SlotItemHandler.java:27-42}）→ {@code itemHandler.isItemValid(index, stack)}，
     * 而原版 {@code net/minecraft/inventory/Container.java} 的六条放入/搬运分支（:180、:197、:314、:349、
     * :410、:424、:441）全都只看 {@code Slot.isItemValid} ⇒ 这条判据<b>不依赖</b>面板那侧有没有把 ghost
     * 应用到 {@code ModularSlot.canPut}（现状是只有客户端应用 ⇒ 存量档的绑定格在服务端原本 canPut=true）。
     * <p>
     * 刻意走"写档 → 重新读档"而不是本会话刚拖出来的格：那才是"重开 GUI"的真实形状。
     */
    private static void ghostSlotsRejectPlacementAtOpen() {
        final PocketInventory seed = PocketInventory.readFrom(null);
        final PocketFilterConfig config = new PocketFilterConfig();
        config.add(0, item(0, 2621, 7, ""));
        config.add(5, item(5, 2621, 7, ""));
        config.add(3, fluid(3, "water"));
        seed.replaceFilters(config);
        final NBTTagCompound root = new NBTTagCompound();
        seed.writeTo(root);

        final PocketInventory reopened = PocketInventory.readFrom(root);
        SimpleAssert.that(reopened.isGhostItemSlot(0), "重开后台账里第 0 格仍是绑定格");
        SimpleAssert.that(reopened.isGhostItemSlot(5), "重开后台账里第 5 格仍是绑定格");
        SimpleAssert.eq(Boolean.FALSE, reopened.isGhostItemSlot(1), "没声明过的第 1 格仍是真实格");

        final ItemStack probe = stack(1);
        SimpleAssert.eq(
            Boolean.FALSE,
            reopened.storage()
                .isItemValid(0, probe),
            "★绑定格不接受玩家放入（点击/Shift 落点/拖拽分堆共用这一道判据）");
        SimpleAssert.eq(
            Boolean.FALSE,
            reopened.storage()
                .isItemValid(5, probe),
            "第二个绑定格同样拒收");
        SimpleAssert.that(
            reopened.storage()
                .isItemValid(1, probe),
            "非绑定格仍可放（不得把整栏刷成禁放）");
        SimpleAssert.that(
            reopened.storage()
                .isItemValid(3, probe),
            "★流体侧的声明不得串到中栏第 3 格（复合键含 kind，R59b 口径）");
        SimpleAssert.that(
            reopened.fluidInteraction()
                .isItemValid(0, probe),
            "流体交互格不受本判据误伤");
        SimpleAssert.that(
            reopened.distillInput()
                .isItemValid(0, probe),
            "蒸馏输入格不受本判据误伤");
        SimpleAssert.that(
            reopened.bindSlot()
                .isItemValid(0, probe),
            "绑定格不受本判据误伤");

        // 程序化写入不经过 isItemValid：ghost 搬空与通道回写用的就是这条路（否则关屏前搬不走存量件）
        reopened.storage()
            .setStackInSlot(0, probe.copy());
        SimpleAssert.eq(1, reopened.storageStack(0).stackSize, "服务端自己写格仍可行（判据只拦玩家输入）");
        // 但落点侧仍按同一份判据跳过绑定格：新来的一件必须落到别的格，而不是并进上面那次人工写入
        SimpleAssert.eq(1, reopened.depositIntoStorage(probe.copy()), "非绑定格承接落位");
        SimpleAssert.eq(1, reopened.storageStack(0).stackSize, "★绑定格一格都没多（depositIntoStorage 的显式跳过）");
    }

    /**
     * 需求 5「适配中键/R 键整理（但绑定物品的格子除外）」的游标算式：
     * 被测 {@code PocketInventory#nextSortableStorageSlot}（服务端唯一一份"跳过绑定格"的游标，
     * {@code depositIntoStorage} 与面板的整理落位都读它）。
     */
    private static void sortTargetsSkipBoundSlots() {
        final PocketInventory inventory = PocketInventory.readFrom(null);
        final PocketFilterConfig config = new PocketFilterConfig();
        for (int slot : new int[] { 0, 1, 4 }) {
            config.add(slot, item(slot, 2621, 7, ""));
        }
        inventory.replaceFilters(config);

        SimpleAssert.eq(2, inventory.nextSortableStorageSlot(0), "游标跳过开头两条连续声明");
        SimpleAssert.eq(2, inventory.nextSortableStorageSlot(2), "落在非绑定格上就地返回（不自增）");
        SimpleAssert.eq(3, inventory.nextSortableStorageSlot(3), "下一个可用格是 3");
        SimpleAssert.eq(5, inventory.nextSortableStorageSlot(4), "跳过单条声明");
        SimpleAssert.eq(2, inventory.nextSortableStorageSlot(-9), "负起点按 0 处理（不越界、不静默返回 -1）");
        SimpleAssert.eq(
            PocketInventory.STORAGE_SLOTS,
            inventory.nextSortableStorageSlot(PocketInventory.STORAGE_SLOTS),
            "★整段越界 ⇒ 返回格数本身（调用方据此判「没有可用格」）");

        // 一格真实格都不剩 ⇒ 游标恒为格数（整理与落点都必须"无处可放"而不是往 ghost 格里塞）
        final PocketInventory allBound = PocketInventory.readFrom(null);
        final PocketFilterConfig everywhere = new PocketFilterConfig();
        for (int index = 0; index < PocketInventory.STORAGE_SLOTS; index++) {
            everywhere.add(index, item(index, 1, 0, ""));
        }
        allBound.replaceFilters(everywhere);
        SimpleAssert.eq(PocketInventory.STORAGE_SLOTS, allBound.nextSortableStorageSlot(0), "全栏绑定 ⇒ 游标直接到底");
        SimpleAssert.eq(0, allBound.depositIntoStorage(stack(8)), "全栏绑定 ⇒ 落点回报 0（不吃件也不塞进绑定格）");
    }

    /**
     * ★中栏那块满覆盖件的 hover 链放行（缺陷 2 唯一可证的破坏点）。
     * <p>
     * {@code IWidget.canHoverThrough()} 的接口默认值是 <b>false</b>（MUI2
     * {@code api/widget/IWidget.java:179-181}），而 {@code ModularGuiContext.getHoveredWidgets}
     * 在第一个不穿透的件上就 {@code break}（{@code screen/viewport/ModularGuiContext.java:428}）⇒
     * 覆盖整块中栏的"一键取出"件会把底下 135 格的 hover 整段截掉（无高亮、无 tooltip、
     * {@code setHoveredSlot(null)} 让 NEI 悬槽与原版连点收集一起失效）。
     * 本用例只钉"两个覆写在不在"（回归护栏：谁把覆盖件换回裸 {@code ButtonWidget} 就会红），
     * 不实例化 widget —— 本套件的纪律是不 new GUI 件（见 {@code NekoEssenceGhostCell} 那条用例的注释）。
     */
    private static void takeOutOverlayDeclaresHoverPassThrough() {
        Class<?> overlay = null;
        try {
            overlay = Class.forName(
                NekoPocketStorageColumn.class.getName() + "$HoverThroughOverlayButton",
                false,
                NekoPocketStorageColumn.class.getClassLoader());
        } catch (Throwable ignored) {
            // 下面用 assert 报红，这里不吞异常就等于把断言信息换成栈
        }
        SimpleAssert.that(overlay != null, "中栏覆盖件类必须存在（改名/撤掉都要同步本用例）");
        String missing = null;
        try {
            overlay.getDeclaredMethod("canHover");
            overlay.getDeclaredMethod("canHoverThrough");
        } catch (Throwable e) {
            missing = e.getClass()
                .getSimpleName() + ": "
                + e.getMessage();
        }
        SimpleAssert.eq(null, missing, "覆盖件必须自己声明 canHover() 与 canHoverThrough() 两个覆写");
    }

    // ================================================================== R83 C2（缺陷 6：alt 绑定 + 每条声明的组上限）
    //
    // ★这一批用例由"实现收口片"补（r83-plan §3 C2 判据、r83-panel-todo §1 第 14 项末行的点名）：
    // C2 那片被打断在生产代码写完、用例为零的状态 ⇒ 下面每条都对应一条"没有机检就会静默退化"的判据。
    // 可测边界（如实记，不假装验过）：三类格件的 {@code onMouseScroll} / {@code setDeclaredCap} /
    // {@code drawCapReadout} 都是<b>实例</b>方法，本 JVM 构造任何 MUI2 widget 都抛
    // {@code NoClassDefFoundError: it/unimi/dsi/fastutil/.../Object2ObjectOpenHashMap}（与
    // {@code essence_cell_content_layer_follows_stock} 同一条限制）⇒ 滚轮手势、右上角橙色像素、
    // tooltip 那一行、AE2 {@code handler.extractItems(MODULATE)} 的真实抽到量<b>全属只能实机项</b>；
    // 本批钉的是它们共同的<b>纯函数</b>（{@code nextCap}/{@code resolveCap}/{@code capDirective}）
    // 与"这些纯函数确实被站点接上了"的源码半边（{@link #capConsumersActuallyCallResolveCap}）。

    /**
     * 判据 1（步进表逐类 + 首步语义）。
     * <p>
     * ★这条钉的是「物品 1 件／流体 1%／源质 1 点」这三个数字<em>只有 PocketConstants 一份</em>，
     * 以及 D-6 裁定的流体分母口径：<b>单 tank 16,000,000 mB 的 1% = 160,000</b>，
     * <em>不是</em> 18 个 tank 合计 288,000,000 的 1%（那是 2,880,000，一档就顶到 18% 的量级）。
     * 还钉住"未设 ⇒ 第一下从现全局量起走"（否则显示 16M、滚一下变 160,000 = 把默认值当 0 读）。
     */
    private static void capStepTablePerKind() {
        // ---- ① 三个步进数字只允许活在常量里，stepOf 只做分派 ----
        SimpleAssert.eq(1, PocketConstants.FILTER_CAP_STEP_ITEM, "物品一档 = 1 件（用户原话「物品每次1个」）");
        SimpleAssert.eq(1, PocketConstants.FILTER_CAP_STEP_ESSENCE, "源质一档 = 1 点（= 1 晶）");
        SimpleAssert.eq(100, PocketConstants.FILTER_CAP_PERCENT_STEPS, "流体「1%」的档数分母 = 100 ⇒ 一格就是一档");
        SimpleAssert.eq(
            PocketConstants.FLUID_BAR_CAPACITY_ML / PocketConstants.FILTER_CAP_PERCENT_STEPS,
            PocketConstants.FILTER_CAP_STEP_FLUID,
            "★流体步长必须由单 tank 容量派生（不留字面量，改 tank 容量时步进自动跟着走）");
        SimpleAssert.eq(160_000, PocketConstants.FILTER_CAP_STEP_FLUID, "★流体一档 = 160,000 mB（交付口径要写进玩家说明的那个读数）");
        SimpleAssert.that(
            PocketConstants.FILTER_CAP_STEP_FLUID
                != PocketConstants.FLUID_TOTAL_CAPACITY_ML / PocketConstants.FILTER_CAP_PERCENT_STEPS,
            "★分母不得是 18 tank 合计（288M/100 = 2,880,000 —— 用合计就把「1%」放大了 18 倍）");
        SimpleAssert.eq(1, PocketGhostRequest.stepOf(Kind.ITEM), "stepOf(ITEM) 只转发 FILTER_CAP_STEP_ITEM");
        SimpleAssert.eq(1, PocketGhostRequest.stepOf(Kind.ESSENCE), "stepOf(ESSENCE) 只转发 FILTER_CAP_STEP_ESSENCE");
        SimpleAssert.eq(160_000, PocketGhostRequest.stepOf(Kind.FLUID), "stepOf(FLUID) 只转发 FILTER_CAP_STEP_FLUID");
        SimpleAssert.eq(PocketConstants.FILTER_CAP_MIN, PocketGhostRequest.stepOf(null), "null 区域 ⇒ 下界，不抛");

        // ---- ② 首步语义：未设 ⇒ 从"这一类的现全局量"起步，再走一档 ----
        SimpleAssert.eq(
            63,
            PocketGhostRequest.nextCap(Kind.ITEM, PocketConstants.FILTER_CAP_UNSET, 64, UpOrDown.DOWN, false),
            "★物品：没滚过轮的 64 叠样本往下滚一格 = 63（不是 0、不是 1、不是 -1）");
        SimpleAssert.eq(
            64,
            PocketGhostRequest.nextCap(Kind.ITEM, PocketConstants.FILTER_CAP_UNSET, 64, UpOrDown.UP, false),
            "物品：未设往上滚仍停在该物品的 maxStackSize（上界收口，不回绕成 1）");
        SimpleAssert.eq(
            15_840_000,
            PocketGhostRequest.nextCap(Kind.FLUID, PocketConstants.FILTER_CAP_UNSET, 0, UpOrDown.DOWN, false),
            "★流体：未设往下滚一格 = 16,000,000 − 160,000（与右上角那一刻的「16M」读数连续）");
        SimpleAssert.eq(
            PocketConstants.FLUID_BAR_CAPACITY_ML,
            PocketGhostRequest.nextCap(Kind.FLUID, PocketConstants.FILTER_CAP_UNSET, 0, UpOrDown.UP, false),
            "流体：未设往上滚 = 天花板本身");
        SimpleAssert.eq(
            63,
            PocketGhostRequest.nextCap(Kind.ESSENCE, PocketConstants.FILTER_CAP_UNSET, 0, UpOrDown.DOWN, false),
            "源质：未设往下滚一格 = 64 − 1");

        // ---- ③ 已设过 ⇒ 在原值上加减一档（不再从天花板起） ----
        SimpleAssert.eq(21, PocketGhostRequest.nextCap(Kind.ITEM, 20, 64, UpOrDown.UP, false), "物品已设 20 ⇒ 上滚 = 21");
        SimpleAssert.eq(19, PocketGhostRequest.nextCap(Kind.ITEM, 20, 64, UpOrDown.DOWN, false), "物品已设 20 ⇒ 下滚 = 19");
        SimpleAssert.eq(
            8_160_000,
            PocketGhostRequest.nextCap(Kind.FLUID, 8_000_000, 0, UpOrDown.UP, false),
            "流体已设 8M ⇒ 上滚 = 8,160,000");
        SimpleAssert.eq(
            7_840_000,
            PocketGhostRequest.nextCap(Kind.FLUID, 8_000_000, 0, UpOrDown.DOWN, false),
            "流体已设 8M ⇒ 下滚 = 7,840,000");
        SimpleAssert.eq(31, PocketGhostRequest.nextCap(Kind.ESSENCE, 30, 0, UpOrDown.UP, false), "源质已设 30 ⇒ 上滚 = 31");

        // ---- ④ 方向与倍率的来源：UpOrDown.modifier 的库口径（改了库就红，不靠记忆） ----
        SimpleAssert.eq(1, UpOrDown.UP.modifier, "★库事实：UP.modifier = +1（nextCap 的方向唯一来源）");
        SimpleAssert.eq(-1, UpOrDown.DOWN.modifier, "★库事实：DOWN.modifier = −1");
        SimpleAssert.eq(1, PocketGhostRequest.capSteps(UpOrDown.UP, false), "alt 上滚 = 1 格");
        SimpleAssert.eq(-1, PocketGhostRequest.capSteps(UpOrDown.DOWN, false), "alt 下滚 = −1 格");
        SimpleAssert.eq(0, PocketGhostRequest.capSteps(null, true), "方向缺失 ⇒ 0 格（倍率乘不上去）");
        SimpleAssert.eq(
            PocketConstants.FILTER_CAP_CEILING_ESSENCE,
            PocketGhostRequest.nextCap(Kind.ESSENCE, PocketConstants.FILTER_CAP_UNSET, 0, null, false),
            "无方向 + 未设 ⇒ 现全局量（★不是 0，也不抛）");

        // ---- ⑤ 下界是硬门：滚到 1 就停，绝不滚出 0 或负数 ----
        SimpleAssert.eq(1, PocketGhostRequest.nextCap(Kind.ITEM, 1, 64, UpOrDown.DOWN, false), "物品已到 1 再下滚 = 1");
        SimpleAssert
            .eq(1, PocketGhostRequest.nextCap(Kind.ESSENCE, 1, 0, UpOrDown.DOWN, true), "源质 1 点 + fast 下滚仍 = 1");
        SimpleAssert.eq(
            PocketConstants.FILTER_CAP_MIN,
            PocketGhostRequest.nextCap(Kind.FLUID, 160_000, 0, UpOrDown.DOWN, true),
            "★流体 fast 下滚跨过 0 ⇒ 停在 1，不越界成负数");

        // ---- ⑥ 物品支的上界是"该物品自己的 maxStackSize"，不是常数 64（16 格药材不能被判成 64） ----
        SimpleAssert.eq(
            15,
            PocketGhostRequest.nextCap(Kind.ITEM, PocketConstants.FILTER_CAP_UNSET, 16, UpOrDown.DOWN, false),
            "16 叠物品：未设下滚 = 15");
        int overflow = PocketConstants.FILTER_CAP_UNSET;
        for (int i = 0; i < 40; i++) {
            overflow = PocketGhostRequest.nextCap(Kind.ITEM, overflow, 16, UpOrDown.UP, true);
        }
        SimpleAssert.eq(16, overflow, "★连按 40 次 fast 上滚也顶不过该物品的 16 叠 ⇒ 手势造不出「超一叠」的物品 cap");
        SimpleAssert.eq(
            998,
            PocketGhostRequest.nextCap(Kind.ITEM, PocketConstants.FILTER_CAP_UNSET, 999, UpOrDown.DOWN, false),
            "★999 叠（GT5U 缆线类）⇒ 天花板与该上滚一档都跟着该物品自身走，本层不硬编码 64");
    }

    /**
     * 判据 2（D-6：alt+ctrl 的 ×10 作用在<em>步进</em>上）。
     * <p>
     * ★这条钉的两件事：① 同一入口下 {@code fast=true} 的位移恰为 {@code fast=false} 的 10 倍；
     * ② ×10 这个数在 pocket 两个源码目录（含 {@code channel/**}）里<em>只有两处</em>——定义 +
     * {@code capSteps} 的唯一消费 ⇒ 没有人拿它去乘通道节拍。再加 {@code config/Config.java} 里
     * 不得出现任何 {@code FILTER_CAP} 字样（那是"第二处速率真值"最容易长出来的地方）。
     * 真正"×10 之后每拍流量没变大"仍属实机项（要跑驱动器）。
     */
    private static void capFastMultiplierOnlyScalesTheStep() {
        SimpleAssert.eq(10, PocketConstants.FILTER_CAP_FAST_MULTIPLIER, "×10 的字面值（用户原话）");
        SimpleAssert.eq(10, PocketGhostRequest.capSteps(UpOrDown.UP, true), "fast 上滚 = 10 格");
        SimpleAssert.eq(-10, PocketGhostRequest.capSteps(UpOrDown.DOWN, true), "fast 下滚 = −10 格");
        SimpleAssert.eq(
            PocketConstants.FILTER_CAP_FAST_MULTIPLIER,
            PocketGhostRequest.capSteps(UpOrDown.UP, true) / PocketGhostRequest.capSteps(UpOrDown.UP, false),
            "★格数比值恰为倍率（×10 作用在步进，不是别处）");

        // 未收口区间内逐类验位移比 = 10（收口点上比值会失真，故各取一个中间值）
        for (Kind kind : Kind.values()) {
            final int maxStack = kind == Kind.ITEM ? 64 : 0;
            final int start = kind == Kind.ITEM ? 20 : kind == Kind.FLUID ? 8_000_000 : 30;
            final int slow = PocketGhostRequest.nextCap(kind, start, maxStack, UpOrDown.UP, false);
            final int fast = PocketGhostRequest.nextCap(kind, start, maxStack, UpOrDown.UP, true);
            final int slowDelta = slow - start;
            final int fastDelta = fast - start;
            SimpleAssert.that(slowDelta > 0, "慢档必须真的走开一格：" + kind);
            SimpleAssert
                .that(fast < PocketGhostRequest.ceilingOf(kind) || kind == Kind.ITEM, "★取样点必须落在未收口区，否则比值无意义：" + kind);
            SimpleAssert.eq(
                slowDelta * PocketConstants.FILTER_CAP_FAST_MULTIPLIER,
                fastDelta,
                "★" + kind + "：fast 位移 = slow 位移 ×10（同一入口、只差一个布尔）");
            SimpleAssert.eq(
                start - slowDelta * PocketConstants.FILTER_CAP_FAST_MULTIPLIER,
                PocketGhostRequest.nextCap(kind, start, maxStack, UpOrDown.DOWN, true),
                "★" + kind + "：下滚方向同样只翻符号，不另立倍率");
        }

        // ---- 源码半边：×10 只有一处消费，且没有渗进通道速率配置 ----
        final int multiplierHits = countPocketSourceLinesMatching("FILTER_CAP_FAST_MULTIPLIER", true);
        if (multiplierHits < 0) {
            System.out.println("[NOTE] 找不到仓库根 ⇒ 「×10 只有一处消费」的源码半边未验（★不是通过）");
        } else {
            SimpleAssert.eq(
                2,
                multiplierHits,
                "★FILTER_CAP_FAST_MULTIPLIER 全 pocket 面只允许两处（PocketConstants 定义 + capSteps 消费）；多一处就是造了第二处速率真值");
        }
        final java.util.List<String> configLines = sourceLinesOrNull(
            "src/main/java/com/miaokatze/gtit/config/Config.java");
        if (configLines == null) {
            System.out.println("[NOTE] 读不到 Config.java ⇒ 「未碰 config 上界」半边未验（★不是通过）");
        } else {
            int leaks = 0;
            for (String line : configLines) {
                if (!isCommentLine(line) && line.contains("FILTER_CAP")) {
                    leaks++;
                }
            }
            SimpleAssert.eq(0, leaks, "★Config.java 里不得出现任何 FILTER_CAP 字样（D-6：组上限不进 config 的第二处真值）");
        }
    }

    /**
     * 判据 3（★"未设 ⇒ 回落现全局量 ⇒ 旧档零行为变更"的唯一机检）。
     * <p>
     * 这条是整个 C2 的兼容面承诺：引入可调上限<em>不得</em>改变任何一个没滚过轮的玩家的现有填充行为。
     * 三类各自等于今天的现全局量（物品 = 该件自己的 maxStackSize、流体 = FLUID_BAR_CAPACITY_ML、
     * 源质 = ESSENCE_CAP_PER_TAG），并且 NBT 侧"没调过就不占键"⇒ 旧档读写形状逐字节不变。
     * 若这条红了，等于"新功能顺手改了老档行为"，是本批最不可让步的判据。
     */
    private static void unsetCapFallsBackToTodayGlobalAmount() {
        final int unset = PocketConstants.FILTER_CAP_UNSET;

        // ---- ① 三类未设 ⇒ 各自等于今日全局量（★不是 0、不是 1、不是猜的 64） ----
        SimpleAssert.eq(
            64,
            PocketFilterConfig.resolveCap(new PocketFilterConfig.ItemFilter(0, 2621, 7, "", unset), 64),
            "★物品未设 ⇒ 该物品的 maxStackSize（64 叠样本 = 64）");
        SimpleAssert.eq(
            16,
            PocketFilterConfig.resolveCap(new PocketFilterConfig.ItemFilter(0, 2621, 7, "", unset), 16),
            "★物品回落值随物品自身变化（不是常数 64）");
        SimpleAssert.eq(
            960,
            PocketFilterConfig.resolveCap(new PocketFilterConfig.ItemFilter(0, 2621, 7, "", unset), 960),
            "GT5U 缆线类 960 叠也照自身回落");
        SimpleAssert.eq(
            PocketConstants.FLUID_BAR_CAPACITY_ML,
            PocketFilterConfig.resolveCap(new PocketFilterConfig.FluidFilter(0, "water", unset), 0),
            "★流体未设 ⇒ FLUID_BAR_CAPACITY_ML（16M/tank，与今日的填tank行为同值）");
        SimpleAssert.eq(
            PocketConstants.FILTER_CAP_CEILING_FLUID,
            PocketFilterConfig.resolveCap(new PocketFilterConfig.FluidFilter(0, "water", unset), 0),
            "流体回落值与上界同一个常量（★刻意同源：现行为就是填到自然满量）");
        SimpleAssert.eq(
            PocketConstants.FILTER_CAP_CEILING_ESSENCE,
            PocketFilterConfig.resolveCap(new PocketFilterConfig.EssenceFilter(0, "essentia", "aer", unset), 0),
            "★R84：源质未设 ⇒ <b>FILTER_CAP_CEILING_ESSENCE</b>（一批仍至多 64 点 = 一整堆晶）——"
                + "它与每格上限 ESSENCE_CAP_PER_TAG(256) 已<b>解耦</b>：晶化源质单堆就是 64，"
                + "若让批上限跟着 256 走，抽取支会抽 256 点却只物化得回 64 晶、注回也只还 64 ⇒ 净吞 192 点");

        // ---- ② 三参形态与两参形态必须同判据（格件读的是 resolveRawCap） ----
        for (Kind kind : Kind.values()) {
            SimpleAssert.eq(
                PocketFilterConfig.defaultCap(kind, 64),
                PocketFilterConfig.resolveRawCap(kind, unset, 64),
                "★resolveRawCap(未设) 必须逐字等于 defaultCap：" + kind);
            SimpleAssert.eq(
                PocketFilterConfig.resolveCap(newEssenceLike(kind, 0, unset), 64),
                PocketFilterConfig.resolveRawCap(kind, unset, 64),
                "两条入口在" + kind + "上不得分叉（否则显示与消费两处真相）");
            SimpleAssert.eq(7, PocketFilterConfig.resolveRawCap(kind, 7, 64), "已设 ⇒ 原值透出，不做任何换算：" + kind);
        }

        // ---- ③ 兜底形状：拿不到样本栈 / 没有声明 ----
        SimpleAssert.eq(unset, PocketFilterConfig.resolveCap(null, 64), "★没有声明 ⇒ 没有上限可读（返回 UNSET，不是 64）");
        SimpleAssert.eq(unset, PocketFilterConfig.resolveRawCap(null, 64, 64), "null 区域 ⇒ UNSET");
        SimpleAssert.eq(
            PocketConstants.FILTER_CAP_MIN,
            PocketFilterConfig.defaultCap(Kind.ITEM, 0),
            "物品拿不到 maxStackSize ⇒ 下界 1（一次一件，比「回落到 0 ⇒ 这一条永远不拉」诚实）");
        SimpleAssert
            .eq(PocketConstants.FILTER_CAP_MIN, PocketFilterConfig.defaultCap(Kind.ITEM, -8), "负 maxStackSize ⇒ 下界 1");
        SimpleAssert.eq(PocketConstants.FILTER_CAP_MIN, PocketFilterConfig.defaultCap(null, 64), "null 区域 ⇒ 下界 1");

        // ---- ④ NBT 半边：没调过轮的旧档形状必须一字不变（不占 cap 键） ----
        final PocketFilterConfig legacy = new PocketFilterConfig();
        SimpleAssert.that(set(legacy, 5, PocketFilterConfig.itemKey(2621, 7, "")).changed(), "旧档：中栏第 5 格一条声明");
        SimpleAssert.that(set(legacy, 2, PocketFilterConfig.fluidKey("water")).changed(), "旧档：流体槽第 2 格");
        SimpleAssert.that(set(legacy, 9, PocketFilterConfig.essenceKey("essentia", "aer")).changed(), "旧档：源质格第 9 格");
        final NBTTagCompound legacyRoot = new NBTTagCompound();
        legacy.writeTo(legacyRoot);
        SimpleAssert.eq(
            0,
            countNbtKeysInFilterBlob(legacyRoot, PocketConstants.FILTER_CAP),
            "★没人滚过轮 ⇒ 一条 cap 键都不许落档（旧档字节形状不变 ⇒ 新键只增不改）");
        final PocketFilterConfig legacyBack = PocketFilterConfig.readFrom(legacyRoot);
        SimpleAssert.eq(3, legacyBack.size(), "往返后仍三条");
        for (Kind kind : Kind.values()) {
            final PocketFilterConfig.Filter back = legacyBack
                .at(kind, kind == Kind.ITEM ? 5 : kind == Kind.FLUID ? 2 : 9);
            SimpleAssert.that(back != null, "往返后该区域的声明还在：" + kind);
            SimpleAssert.eq(unset, back.cap(), "★缺 cap 键读回来必须是 UNSET 而不是 0：" + kind);
            SimpleAssert.eq(payloadOf(kind), back.key(), "往返后载荷键逐字不变（cap 不进身份）：" + kind);
        }
        SimpleAssert.eq(64, PocketFilterConfig.resolveCap(legacyBack.at(Kind.ITEM, 5), 64), "旧档读回来再走消费 ⇒ 与今日同值");
    }

    /**
     * 判据 4（区间收口 + "UNSET 绝不被当成 0 参与步进"）。
     * <p>
     * ★这条同时钉住服务端拒收线的三条腿：越界值收口、0 与负数<em>整条拒收</em>（不是"悄悄抬到 1"，
     * 因为 0 的语义是"这一条不拉了"，而本仓口径是"不想拉就右键解绑"）、以及"那一格根本没有声明"时
     * 绝不顺手建一条空声明。另钉段数判据（SET/CLR 恰 3 段、CAP 恰 4 段）。
     */
    private static void capClampedAndUnsetNeverReadAsZero() {
        // ---- ① clampCap 纯收口：下界硬、上界照入参、不四舍五入不取模 ----
        SimpleAssert.eq(1, PocketGhostRequest.clampCap(0, 64), "0 ⇒ 1");
        SimpleAssert.eq(1, PocketGhostRequest.clampCap(-999, 64), "负数 ⇒ 1");
        SimpleAssert.eq(64, PocketGhostRequest.clampCap(1000, 64), "越上界 ⇒ 天花板");
        SimpleAssert.eq(1, PocketGhostRequest.clampCap(5, 0), "★天花板本身畸形(0) ⇒ 仍不低于下界（不返回 0）");
        SimpleAssert.eq(64, PocketGhostRequest.clampCap(64, 64), "边界值原样通过");
        SimpleAssert.eq(
            PocketConstants.FLUID_BAR_CAPACITY_ML,
            PocketGhostRequest.ceilingOf(Kind.FLUID),
            "★流体上界 = 单 tank 16M（不是 FLUID_TOTAL_CAPACITY_ML 288M）");
        SimpleAssert.that(
            PocketConstants.FLUID_TOTAL_CAPACITY_ML != PocketGhostRequest.ceilingOf(Kind.FLUID),
            "流体上界不得等于 18 tank 合计");
        SimpleAssert.eq(64, PocketGhostRequest.ceilingOf(Kind.ESSENCE), "源质上界 = 64 点");
        SimpleAssert.eq(
            Integer.MAX_VALUE,
            PocketGhostRequest.ceilingOf(Kind.ITEM),
            "★物品请求侧不设界（自然满量只有拿着 ItemStack 的一端解得，见 O-1）");

        // ---- ② UNSET 不得被当成 0 参与步进（否则第一次下滚就"从 0 往上加"） ----
        final PocketFilterConfig config = new PocketFilterConfig();
        SimpleAssert.that(set(config, 5, PocketFilterConfig.itemKey(2621, 7, "")).changed(), "先建一条未设的声明");
        SimpleAssert.eq(
            PocketConstants.FILTER_CAP_UNSET,
            config.at(Kind.ITEM, 5)
                .cap(),
            "新声明的原始值是 UNSET（不是 0）");
        SimpleAssert.eq(
            63,
            PocketGhostRequest.nextCap(
                Kind.ITEM,
                config.at(Kind.ITEM, 5)
                    .cap(),
                64,
                UpOrDown.DOWN,
                false),
            "★拿原始 UNSET 去步进 ⇒ 63（若被当成 0 就是 0−1 → 收口成 1）");

        // ---- ③ 服务端 CAP 的三条拒收线 ----
        final PocketFilterConfig fluid = new PocketFilterConfig();
        SimpleAssert.that(set(fluid, 1, PocketFilterConfig.fluidKey("lava")).changed(), "流体槽第 1 格建声明");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.APPLIED,
            PocketGhostRequest.apply(PocketGhostRequest.capRequest(1, Kind.FLUID, 320_000), fluid).outcome,
            "合法绝对值 ⇒ 生效");
        SimpleAssert.eq(
            320_000,
            fluid.at(Kind.FLUID, 1)
                .cap(),
            "落档读的是绝对值本身（服务端不重算步进）");
        SimpleAssert.eq(
            PocketConstants.FLUID_BAR_CAPACITY_ML,
            PocketGhostRequest.apply(PocketGhostRequest.capRequest(1, Kind.FLUID, 999_999_999), fluid).outcome
                == PocketGhostRequest.Outcome.APPLIED
                    ? fluid.at(Kind.FLUID, 1)
                        .cap()
                    : -1,
            "★越界值 ⇒ 收口到 16M 后落档（不是拒收、也不是原样存 999,999,999）");
        for (String bad : new String[] { "0", "-1", "-999999" }) {
            final PocketFilterConfig probe = new PocketFilterConfig();
            set(probe, 1, PocketFilterConfig.fluidKey("lava"));
            SimpleAssert.eq(
                PocketGhostRequest.Outcome.REJECTED,
                PocketGhostRequest.apply("CAP|1|F|" + bad, probe).outcome,
                "★0 与负数整条拒收（不是悄悄抬到 1）：" + bad);
            SimpleAssert.eq(
                PocketConstants.FILTER_CAP_UNSET,
                probe.at(Kind.FLUID, 1)
                    .cap(),
                "拒收不得动到原值（UNSET 保持 UNSET）：" + bad);
        }
        final PocketFilterConfig empty = new PocketFilterConfig();
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            PocketGhostRequest.apply(PocketGhostRequest.capRequest(3, Kind.ITEM, 12), empty).outcome,
            "★那一格根本没有声明 ⇒ 拒收（绝不顺手建空声明）");
        SimpleAssert.eq(0, empty.size(), "拒收后一条声明都没有被建出来");
        final PocketFilterConfig outOfRange = new PocketFilterConfig();
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            PocketGhostRequest.apply("CAP|" + PocketConstants.GHOST_ITEM_SLOT_LIMIT + "|I|10", outOfRange).outcome,
            "★槽号越出该区域白名单 ⇒ 拒收（中栏上界 = GHOST_ITEM_SLOT_LIMIT）");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            PocketGhostRequest.apply("CAP|1|F|abc", outOfRange).outcome,
            "值解不出整数 ⇒ 拒收");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            PocketGhostRequest.apply("CAP|1|X|10", outOfRange).outcome,
            "区域字母不认识 ⇒ 拒收");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            PocketGhostRequest.apply("SET|1|i:2621:7:extra|tail", outOfRange).outcome,
            "★段数即判据：SET 只接受恰 3 段（载荷里塞出第 4 段就整条拒收）");
        SimpleAssert.eq(
            PocketGhostRequest.Outcome.REJECTED,
            PocketGhostRequest.apply("CLR|1|I|9", outOfRange).outcome,
            "★CLR 只接受恰 3 段");
    }

    /**
     * 判据 5（指令串文法 + 与真载荷键互斥可判 + 往返闭合）。
     * <p>
     * ★用例名 {@code capDirectiveRoundTrip} 被生产代码 {@code PocketGhostRequest:95} 的注释点名引用，
     * 改名就等于再留一条悬空引用。这条钉的是 C2 的"借道 requestGhost"过渡形状之所以安全的全部理由：
     * 真载荷键只用 ':' 分段、永不含 '|'，于是带 "CAP|" 前缀的第二段<em>只可能</em>是调上限指令；
     * 而 {@code SET|<slot>|<CAP 指令>} 拼出来的串必须与 {@code capRequest} 逐字符相同（否则客户端
     * 发的东西服务端解不回来 = 静默失效）。
     */
    private static void capDirectiveRoundTrip() {
        // ---- ① 指令形状 ----
        SimpleAssert.eq("CAP|F|160000", PocketGhostRequest.capDirective(Kind.FLUID, 160_000), "指令 = CAP|<区域字母>|<绝对值>");
        SimpleAssert.eq("CAP|I|10", PocketGhostRequest.capDirective(Kind.ITEM, 10), "物品支指令");
        SimpleAssert.eq("CAP|E|64", PocketGhostRequest.capDirective(Kind.ESSENCE, 64), "源质支指令");
        SimpleAssert.that(PocketGhostRequest.isCapDirective("CAP|F|160000"), "capDirective 造的串必须被 isCapDirective 认出来");
        SimpleAssert.that(!PocketGhostRequest.isCapDirective("CAPF160000"), "少了分隔符就不算指令");
        SimpleAssert.that(!PocketGhostRequest.isCapDirective("cap|F|1"), "★大小写敏感（不能把玩家物品名里的 'cap|' 当指令）");
        SimpleAssert.that(!PocketGhostRequest.isCapDirective(null), "null ⇒ false，不抛");
        SimpleAssert.that(!PocketGhostRequest.isCapDirective(""), "空串 ⇒ false");
        SimpleAssert.that(!PocketGhostRequest.isCapDirective("SET|1|F|1"), "另一个操作码不算指令");

        // ---- ② 与真载荷键互斥可判：三类真键（含最刁钻的形状）永不含 '|' ----
        for (Kind kind : Kind.values()) {
            SimpleAssert.that(!payloadOf(kind).contains("|"), "★真载荷键不得含 '|'：" + kind);
            SimpleAssert.that(!PocketGhostRequest.isCapDirective(payloadOf(kind)), "真载荷键不算指令：" + kind);
        }
        final String[] nastyKeys = { PocketFilterConfig.itemKey(2621, 7, "{display:{Name:\"CAP|F|1\"}}"),
            PocketFilterConfig.itemKey(2621, 7, "SET|0|I|9"), PocketFilterConfig.fluidKey("CAP|F|1"),
            PocketFilterConfig.fluidKey("neko:cap|molten"), PocketFilterConfig.essenceKey("CAP|F", "ignis"),
            PocketFilterConfig.essenceKey("thaumcraft:essentia", "CAP|E|9"), PocketFilterConfig.essenceKey("", "") };
        for (String key : nastyKeys) {
            SimpleAssert.that(!key.startsWith("CAP|"), "★NBT 里带 CAP| 字面串的物品名也只是载荷键内容，不是指令：" + key);
            SimpleAssert.that(!PocketGhostRequest.isCapDirective(key), "isCapDirective 只认前缀，不做子串匹配：" + key);
        }

        // ---- ③ 往返闭合：格件发出的 SET|slot|(CAP 指令) == capRequest，且服务端解回同一个绝对值 ----
        for (Kind kind : Kind.values()) {
            final int slot = kind == Kind.ITEM ? 42 : kind == Kind.FLUID ? 7 : 3;
            final int value = kind == Kind.ITEM ? 33 : kind == Kind.FLUID ? 2_560_000 : 21;
            final String viaRequestGhostEntry = PocketGhostRequest
                .setRequest(slot, PocketGhostRequest.capDirective(kind, value));
            final String canonical = PocketGhostRequest.capRequest(slot, kind, value);
            SimpleAssert.eq(viaRequestGhostEntry, canonical, "★借道 requestGhost 的产物与 capRequest 逐字符相同：" + kind);
            SimpleAssert.eq(
                "CAP|" + slot + "|" + PocketGhostRequest.letterOf(kind) + "|" + value,
                canonical,
                "完整式样 CAP|<槽号>|<区域字母>|<绝对值>：" + kind);

            final PocketFilterConfig config = new PocketFilterConfig();
            SimpleAssert.that(set(config, slot, payloadOf(kind)).changed(), "先建该区域的声明：" + kind);
            final PocketGhostRequest.Decision applied = PocketGhostRequest.apply(canonical, config);
            SimpleAssert.eq(PocketGhostRequest.Outcome.APPLIED, applied.outcome, "★往返必须能被服务端解回并生效：" + kind);
            SimpleAssert.eq(kind, applied.kind, "命中的区域来自指令自带的字母：" + kind);
            SimpleAssert.eq(slot, applied.slotIndex, "命中的槽号来自 setRequest 插进去的那一段：" + kind);
            SimpleAssert.eq(
                value,
                config.at(kind, slot)
                    .cap(),
                "读回的绝对值 == 发出的绝对值（不被服务端重算）：" + kind);
            SimpleAssert.eq(
                payloadOf(kind),
                config.at(kind, slot)
                    .key(),
                "★调上限不得动到载荷键（身份不变）：" + kind);
            SimpleAssert.eq(1, config.size(), "调上限不得凭空多出一条声明：" + kind);
            // 再走一遍同一条指令 ⇒ UNCHANGED（幂等，blob 不因为"又滚了一下"就重复落盘）
            SimpleAssert.eq(
                PocketGhostRequest.Outcome.UNCHANGED,
                PocketGhostRequest.apply(canonical, config).outcome,
                "同一绝对值重复下发 ⇒ UNCHANGED：" + kind);
            // 载荷键走 setRequest 时仍是 SET 文法（证明分流没有误伤正常绑定）
            final PocketFilterConfig fresh = new PocketFilterConfig();
            SimpleAssert.eq(
                "SET|" + slot + "|" + payloadOf(kind),
                PocketGhostRequest.setRequest(slot, payloadOf(kind)),
                "真载荷键的 setRequest 仍是 SET 文法：" + kind);
            SimpleAssert.eq(
                PocketGhostRequest.Outcome.APPLIED,
                PocketGhostRequest.apply(PocketGhostRequest.setRequest(slot, payloadOf(kind)), fresh).outcome,
                "SET 半边没坏：" + kind);
            SimpleAssert.eq(
                PocketConstants.FILTER_CAP_UNSET,
                fresh.at(kind, slot)
                    .cap(),
                "新建声明的原始上限是 UNSET：" + kind);
        }
    }

    /**
     * 判据 7（cap 是数量、不是身份）。
     * <p>
     * ★这条钉的是"同键不同 cap 必须仍是同一条声明"：{@code contains()} 与 {@code parseKey} 两侧都只按
     * 载荷键比对，一旦谁把数量拼进键里，"同一种物品两个格"就会分裂成两条身份、NEI 重复提示与
     * {@code PocketAeChannelOps#contentKey} 的比对同时出事。另钉 {@code withCap} 的不可变性
     * （返回新实例，正在跑的批次读到的旧实例一字不改）与 blob 读写成对（P-C2-3 的机检半边）。
     */
    private static void capNotPartOfFilterIdentity() {
        final String itemKey = PocketFilterConfig.itemKey(2621, 7, "");
        final PocketFilterConfig config = new PocketFilterConfig();
        SimpleAssert.that(config.add(3, new PocketFilterConfig.ItemFilter(3, 2621, 7, "", 5)), "第 3 格：cap=5 的物品声明应落地");
        SimpleAssert.that(
            config.add(40, new PocketFilterConfig.ItemFilter(40, 2621, 7, "", 60)),
            "★第 40 格：同载荷键、不同 cap 的声明必须也能落地（两格要同一种东西是合法读法）");
        SimpleAssert.eq(2, config.size(), "两条声明各自占一格，没有因 cap 不同而分裂、也没因键相同而互相覆盖");
        SimpleAssert.that(config.contains(itemKey), "contains 只按载荷键判定（不看数量）");
        SimpleAssert
            .that(config.contains(PocketFilterConfig.itemKey(2621, 7, "{a:1}")) == false, "★没声明过的键不得因数量不同被误判为已存在");
        SimpleAssert.eq(
            5,
            config.at(Kind.ITEM, 3)
                .cap(),
            "第一条的数量");
        SimpleAssert.eq(
            60,
            config.at(Kind.ITEM, 40)
                .cap(),
            "第二条的数量");
        SimpleAssert.eq(
            config.at(Kind.ITEM, 3)
                .key(),
            config.at(Kind.ITEM, 40)
                .key(),
            "★两条的身份串逐字相同（数量不进键）");
        SimpleAssert.eq(
            itemKey,
            PocketFilterConfig.parseKey(itemKey)
                .key(),
            "parseKey 解出的载荷串与入参逐字相同");
        SimpleAssert.eq(
            PocketConstants.FILTER_CAP_UNSET,
            PocketFilterConfig.parseKey(itemKey)
                .cap(),
            "★parseKey 解出来的是纯载荷，cap 一律 UNSET（数量不从键里来）");
        for (Kind kind : Kind.values()) {
            SimpleAssert.eq(
                PocketConstants.FILTER_CAP_UNSET,
                PocketFilterConfig.parseKey(payloadOf(kind))
                    .cap(),
                "三类都同判据：" + kind);
            SimpleAssert.that(
                !payloadOf(kind).contains(String.valueOf(PocketConstants.FILTER_CAP)),
                "★载荷键串里不得出现 cap 键名（数量没被拼进身份）：" + kind);
        }

        // ---- withCap 返回新实例：旧实例一字不改（正在跑的批次不能被滚轮事件改写） ----
        final PocketFilterConfig.Filter original = new PocketFilterConfig.EssenceFilter(
            11,
            "essentia",
            "ignis",
            PocketConstants.FILTER_CAP_UNSET);
        final PocketFilterConfig.Filter nudged = original.withCap(9);
        SimpleAssert.that(original != nudged, "★withCap 必须返回新实例（原地改会让正在跑的批次读到半新半旧）");
        SimpleAssert.eq(PocketConstants.FILTER_CAP_UNSET, original.cap(), "★原实例的 cap 一字未动");
        SimpleAssert.eq(9, nudged.cap(), "新实例带上新值");
        SimpleAssert.eq(original.key(), nudged.key(), "★身份串不变");
        SimpleAssert.eq(original.slotIndex(), nudged.slotIndex(), "槽号不变");
        SimpleAssert.eq(original.kind(), nudged.kind(), "区域不变");
        SimpleAssert.eq(false, config.add(3, itemFilterAt(3, 41)), "同槽二次写入 = 覆盖（add 必须返回 false）");
        SimpleAssert.eq(
            41,
            config.at(Kind.ITEM, 3)
                .cap(),
            "★覆盖后该格读到的就是新上限");
        SimpleAssert.eq(2, config.size(), "★覆盖不增条数");

        // ---- blob 读写成对（P-C2-3 的机检半边：只写不读 = 静默丢档） ----
        final PocketFilterConfig withCaps = new PocketFilterConfig();
        withCaps.add(7, new PocketFilterConfig.ItemFilter(7, 2621, 7, "", 41));
        withCaps.add(3, new PocketFilterConfig.FluidFilter(3, "water", 1_120_000));
        withCaps.add(19, new PocketFilterConfig.EssenceFilter(19, "thaumcraft:essentia", "aer", 12));
        final NBTTagCompound root = new NBTTagCompound();
        withCaps.writeTo(root);
        SimpleAssert.eq(3, countNbtKeysInFilterBlob(root, PocketConstants.FILTER_CAP), "三条已设 ⇒ 恰好落三个 cap 键");
        final PocketFilterConfig back = PocketFilterConfig.readFrom(root);
        SimpleAssert.eq(
            41,
            back.at(Kind.ITEM, 7)
                .cap(),
            "★物品支 cap 落档后读回（写读必须成对）");
        SimpleAssert.eq(
            1_120_000,
            back.at(Kind.FLUID, 3)
                .cap(),
            "★流体支 cap 落档后读回");
        SimpleAssert.eq(
            12,
            back.at(Kind.ESSENCE, 19)
                .cap(),
            "★源质支 cap 落档后读回（含带命名空间的 typeId）");
        SimpleAssert.eq(
            withCaps.at(Kind.ITEM, 7)
                .key(),
            back.at(Kind.ITEM, 7)
                .key(),
            "往返后身份串不变");
        SimpleAssert.eq(3, back.size(), "往返条数不变");
    }

    /**
     * 判据 6（★消费点真的有调用方 —— 防"写入侧零调用方"型假绿）。
     * <p>
     * C2 被打断时的形状正是"字段、NBT、文法、步进全齐，但没有任何人读它"。这条分两半边：
     * <ul>
     * <li><b>接线半边（机检）</b>：直接读生产源码，逐支确认 {@code extractItem}/{@code extractFluid}/
     * {@code extractEssence} 三个方法体内各有一行真在调 {@code PocketFilterConfig.resolveCap(}，
     * 并确认三类格件各调一次 {@code PocketGhostRequest.nextCap(}（步进表不是死常量）。删掉任一调用点
     * 本条即红 ⇒ 这条半边是有机检的，不是"靠注释声称"。</li>
     * <li><b>算式半边（机检）</b>：把三站点共同的可测部件按站点原样组一次（{@code fluidRequestFor} 等），
     * 证 {@code resolveCap} 的取值真的能改<em>拉多少</em>。</li>
     * </ul>
     * ★仍属<em>只能实机</em>的那一半（本条不覆盖，如实标出）：AE2 {@code handler.extractItems(…,
     * Actionable.MODULATE)} 的返回量、玩家背包/中栏真实落库、以及下一拍是否按新上限续拉 ——
     * 纯 JVM 拿不到真 {@code IMEInventoryHandler}（见类 javadoc 的可测边界）。
     */
    private static void capConsumersActuallyCallResolveCap() {
        // ================= 半边 A：站点接线（源码机检） =================
        final java.util.List<String> ops = sourceLinesOrNull(
            "src/main/java/com/miaokatze/gtit/common/items/pocket/PocketAeChannelOps.java");
        // ★R87-A：源质支纯搬去了 PocketEssenceChannelOps ⇒ 源质支的消费点改在那边扫描
        final java.util.List<String> essenceOps = sourceLinesOrNull(
            "src/main/java/com/miaokatze/gtit/common/items/pocket/PocketEssenceChannelOps.java");
        if (ops == null || essenceOps == null) {
            System.out.println(
                "[NOTE] 找不到仓库根或读不到 PocketAeChannelOps.java / PocketEssenceChannelOps.java ⇒ 「三支消费都接上 resolveCap」的接线半边【未验】（★不是通过；下面的算式半边照验）");
        } else {
            final int itemStart = methodStart(ops, "Outcome extractItem(");
            final int fluidStart = methodStart(ops, "Outcome extractFluid(");
            final int essenceStart = methodStart(essenceOps, "Outcome extractEssence(");
            SimpleAssert.that(
                itemStart >= 0 && fluidStart > itemStart && essenceStart >= 0,
                "★三支抽取方法的定位必须成功（物品/流体在 PocketAeChannelOps、源质在其专属类，改名/合并即红）");
            assertResolveCapInside(ops, itemStart, fluidStart, "extractItem(物品支)");
            assertResolveCapInside(ops, fluidStart, ops.size(), "extractFluid(流体支)");
            assertResolveCapInside(essenceOps, essenceStart, essenceOps.size(), "extractEssence(源质支)");
            // 物品支必须把"该物品自己的 maxStackSize"喂给 resolveCap（否则未设回落就是猜的 64）
            SimpleAssert.that(
                regionContainsCode(ops, itemStart, fluidStart, "wanted.getMaxStackSize()"),
                "★物品支消费点必须传 wanted.getMaxStackSize()（未设时回落 = 该件自身堆叠上限 = 今日行为）");
            SimpleAssert.eq(
                3,
                countCodeLinesIn(ops, "PocketFilterConfig.resolveCap(")
                    + countCodeLinesIn(essenceOps, "PocketFilterConfig.resolveCap("),
                "★全仓 resolveCap 消费点恰 3 处（物品/流体/源质各一；★R87-A 后跨两个文件计）；多一处就是第二处真相");
            // ★扫描器自身的两个负控：证明上面那三条不是"恒真式"读数（C1 的教训：静态块全绿不等于判据成立）
            SimpleAssert.that(
                !regionContainsCode(ops, itemStart, fluidStart, "resolveCapButThisTokenDoesNotExist"),
                "★负控：区间扫描必须对不存在的片段报 false（否则上面的接线判据是恒真式）");
            SimpleAssert.eq(-1, methodStart(ops, "Outcome extractNothingAtAll("), "★负控：行定位必须对不存在的方法报 -1（否则区间边界是假的）");
        }
        // ★判据形态：spotless 会把长调用在 `PocketGhostRequest` 之后折行，把 `.nextCap(` 单独放到下一行
        // ⇒ 认 "PocketGhostRequest\.nextCap\(" 会随机漏掉被折的两支（本轮实测只报 1/3）。
        // 点号始终粘在方法名上，故认 "\.nextCap\(" 既跨得过折行、又不会撞上定义行（`int nextCap(`）
        // 与 javadoc 引用（`#nextCap}`）。
        final int nextCapHits = countPocketSourceLinesMatching("\\.nextCap\\(", true);
        if (nextCapHits < 0) {
            System.out.println("[NOTE] 找不到仓库根 ⇒ 「三类格件都走同一条 nextCap」的接线半边未验（★不是通过）");
        } else {
            SimpleAssert.eq(
                3,
                nextCapHits,
                "★nextCap 的三个调用方 = NekoFilterSlot / NekoPocketFluidSlot / NekoEssenceGhostCell 各一处（少一处即某一支滚轮是死码）");
        }
        final int directiveHits = countPocketSourceLinesMatching("PocketGhostRequest\\.capDirective\\(", true);
        if (directiveHits < 0) {
            System.out.println("[NOTE] 找不到仓库根 ⇒ capDirective 调用方计数未验");
        } else {
            SimpleAssert.that(
                directiveHits >= 3,
                "★三类格件各自发 capDirective（与 nextCap 同数 ⇒ 没有哪一支只算不发）；读到 " + directiveHits
                    + "。★刻意用 ≥ 而不是 ==：Panel 待办 P-C2-1 允许面板自己造指令（requestGhostCap 转发），那时恰好三处不是稳定判据");
        }

        // ================= 半边 B：算式真的能改"拉多少" =================
        final int unset = PocketConstants.FILTER_CAP_UNSET;

        // 物品支（站点算式 min(simulated, resolveCap(filter, wanted.getMaxStackSize())) 的可测部分）
        final PocketFilterConfig.Filter itemUnset = new PocketFilterConfig.ItemFilter(0, 2621, 7, "", unset);
        final PocketFilterConfig.Filter itemCapped = new PocketFilterConfig.ItemFilter(0, 2621, 7, "", 30);
        SimpleAssert.eq(
            Math.min(100, PocketFilterConfig.resolveCap(itemUnset, 64)),
            64,
            "★旧档（未设）：模拟可得 100 ⇒ 一次仍拉 64 = 该件一叠（今日行为，未被新功能改动）");
        SimpleAssert.eq(
            Math.min(100, PocketFilterConfig.resolveCap(itemCapped, 64)),
            30,
            "★玩家调到 30 ⇒ 一次只拉 30（cap 真的在改变拉取量，不是摆设）");

        // 流体支（站点算式 fluidRequestFor(available, min(room, resolveCap(filter, 0)))，用的是生产的 fluidRequestFor）
        final PocketFilterConfig.Filter fluidUnset = new PocketFilterConfig.FluidFilter(0, "water", unset);
        final PocketFilterConfig.Filter fluidCapped = new PocketFilterConfig.FluidFilter(0, "water", 320_000);
        SimpleAssert.eq(
            PocketAeChannelOps.fluidRequestFor(
                PocketConstants.FLUID_BAR_CAPACITY_ML,
                Math.min(PocketConstants.FLUID_BAR_CAPACITY_ML, PocketFilterConfig.resolveCap(fluidUnset, 0))),
            PocketConstants.FLUID_BAR_CAPACITY_ML,
            "★旧档流体：一次仍要满整 tank（16M）= 今日行为");
        SimpleAssert.eq(
            PocketAeChannelOps.fluidRequestFor(
                PocketConstants.FLUID_BAR_CAPACITY_ML,
                Math.min(PocketConstants.FLUID_BAR_CAPACITY_ML, PocketFilterConfig.resolveCap(fluidCapped, 0))),
            320_000,
            "★玩家调到两档（320,000 mB）⇒ 请求量就是 320,000（两档 × 160,000）");
        SimpleAssert
            .eq(PocketAeChannelOps.fluidRequestFor(7_000, 320_000), 7_000, "落点空间更小时仍按落点收口（cap 不得越过 tank 剩余空间超发）");

        // 源质支（站点算式 min(count, resolveCap(filter, 0))）
        final PocketFilterConfig.Filter essenceUnset = new PocketFilterConfig.EssenceFilter(
            0,
            "essentia",
            "aer",
            unset);
        final PocketFilterConfig.Filter essenceCapped = new PocketFilterConfig.EssenceFilter(0, "essentia", "aer", 7);
        // ★R85 小项 6：旧写法是 min(ESSENCE_CAP_PER_TAG, resolveCap(...)) —— 256/64 解耦（R84）之后
        // 那层 Math.min 恒等于右边一项，是一层<b>永真包装</b>：它既给后来人"这条按每格上限钉住了"的错觉，
        // 又让"把天花板抬到 128/256"这种改法在本条上<b>永远绿</b>（假绿）。现在直接钉符号：
        // 未设的源质声明回落 = FILTER_CAP_CEILING_ESSENCE（★不是 ESSENCE_CAP_PER_TAG），
        // 并把"两者不等"这条 R84 裁定一起钉住 ⇒ 谁把天花板抬到 256 让两者重新相等，本条立刻红。
        SimpleAssert.that(
            PocketConstants.ESSENCE_CAP_PER_TAG != PocketConstants.FILTER_CAP_CEILING_ESSENCE,
            "★R84 裁定：每格上限(256)与一次取出的天花板必须<b>不是</b>同一个数（两者一旦相等，下面那条读数就退回假绿）");
        SimpleAssert.eq(
            PocketConstants.FILTER_CAP_CEILING_ESSENCE,
            PocketFilterConfig.resolveCap(essenceUnset, 0),
            "★旧档源质：一批至多 FILTER_CAP_CEILING_ESSENCE 点（回落值是天花板，★不是每格上限 256；"
                + "把 ESSENCE_OUT_MAX_POINTS_PER_ACTION 改歪会立刻红在这里）");
        SimpleAssert
            .eq(64, PocketFilterConfig.resolveCap(essenceUnset, 0), "★同一读数的字面锚：抬天花板是<b>裁定</b>，必须让这条红，不许悄悄跟着符号走");
        SimpleAssert.eq(
            Math.min(7, PocketConstants.FILTER_CAP_CEILING_ESSENCE),
            PocketFilterConfig.resolveCap(essenceCapped, 0),
            "★玩家调到 7 ⇒ 一批只 7 点（不会撞全有全无 ⇒ 也就不会永久 needsRoom 卡死）");
    }

    /** 造一条"区域对、载荷随意"的声明，只为拿 {@code resolveCap} 的两条入口做同判据对表。 */
    private static PocketFilterConfig.Filter newEssenceLike(Kind kind, int slot, int cap) {
        switch (kind) {
            case ITEM:
                return new PocketFilterConfig.ItemFilter(slot, 2621, 7, "", cap);
            case FLUID:
                return new PocketFilterConfig.FluidFilter(slot, "water", cap);
            case ESSENCE:
            default:
                return new PocketFilterConfig.EssenceFilter(slot, "essentia", "aer", cap);
        }
    }

    /** 同一格换 cap（覆盖形态），只为证"同槽 withCap 不增条数"。 */
    private static PocketFilterConfig.Filter itemFilterAt(int slot, int cap) {
        return new PocketFilterConfig.ItemFilter(slot, 2621, 7, "", cap);
    }

    /** 数 ghost blob 里出现了多少个某个键（★只数条目级键，用来判"cap 有没有落档/有没有多落"）。 */
    private static int countNbtKeysInFilterBlob(NBTTagCompound root, String key) {
        final NBTTagCompound domain = root.getCompoundTag(PocketConstants.FILTERS);
        int hits = 0;
        for (String listKey : new String[] { PocketConstants.FILTER_ITEMS, PocketConstants.FILTER_FLUIDS,
            PocketConstants.FILTER_ESSENTIA }) {
            final NBTTagList list = domain.getTagList(listKey, 10);
            for (int i = 0; i < list.tagCount(); i++) {
                if (list.getCompoundTagAt(i)
                    .hasKey(key)) {
                    hits++;
                }
            }
        }
        return hits;
    }

    /** 读仓内某个源码文件的行；找不到仓库根或读失败返回 {@code null}（调用方必须打 NOTE，不得当空用）。 */
    private static java.util.List<String> sourceLinesOrNull(String relativePath) {
        final java.nio.file.Path root = repoRootOrNull();
        if (root == null) {
            return null;
        }
        final java.nio.file.Path file = root.resolve(relativePath);
        if (!java.nio.file.Files.isReadable(file)) {
            return null;
        }
        try {
            return java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ignored) {
            return null;
        }
    }

    /** 某一行的代码正文（★不是注释）里是否含给定片段。 */
    private static boolean isCommentLine(String line) {
        final String trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    /** 第一个包含 {@code needle} 的代码行的下标；找不到返回 -1。 */
    private static int methodStart(java.util.List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (!isCommentLine(lines.get(i)) && lines.get(i)
                .contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    /** 区间内（★注释行不算）是否有代码行含给定片段。 */
    private static boolean regionContainsCode(java.util.List<String> lines, int from, int to, String needle) {
        for (int i = Math.max(0, from); i < Math.min(lines.size(), to); i++) {
            if (!isCommentLine(lines.get(i)) && lines.get(i)
                .contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** 整份文件里含给定片段的代码行数（注释不算）。 */
    private static int countCodeLinesIn(java.util.List<String> lines, String needle) {
        int hits = 0;
        for (String line : lines) {
            if (!isCommentLine(line) && line.contains(needle)) {
                hits++;
            }
        }
        return hits;
    }

    /** ★判据 6 的"接线"断言：某一支抽取方法的方法体里必须真有一行在调 resolveCap。 */
    private static void assertResolveCapInside(java.util.List<String> lines, int from, int to, String label) {
        final int hits = countCodeLinesIn(
            lines.subList(Math.max(0, from), Math.min(lines.size(), to)),
            "PocketFilterConfig.resolveCap(");
        SimpleAssert.eq(
            1,
            hits,
            "★" + label + " 方法体内必须恰有一处消费 PocketFilterConfig.resolveCap(（读到 " + hits + " ⇒ 该支要么没接上、要么接了两遍）");
    }

    /** 各区域的一条代表性载荷键（多处复用，免得同一份字面量在断言里写三遍）。 */
    private static String payloadOf(Kind kind) {
        switch (kind) {
            case ITEM:
                return PocketFilterConfig.itemKey(2621, 7, "");
            case FLUID:
                return PocketFilterConfig.fluidKey("water");
            case ESSENCE:
            default:
                return PocketFilterConfig.essenceKey("essentia", "aer");
        }
    }

    /** 流体名探针桩件：按栈<b>身份</b>给名，并可指定"问到就抛"的条目。 */
    private static final class NamedProbe implements NekoPocketFluidSlot.FluidNameProbe {

        private final Map<ItemStack, String> names = new IdentityHashMap<>();
        private final Set<ItemStack> hostile = Collections.newSetFromMap(new IdentityHashMap<ItemStack, Boolean>());
        int queries;

        NamedProbe put(ItemStack stack, String fluidName) {
            names.put(stack, fluidName);
            return this;
        }

        NamedProbe hostile(ItemStack stack) {
            hostile.add(stack);
            return this;
        }

        @Override
        public String fluidName(ItemStack draggedStack) {
            queries++;
            if (draggedStack != null && hostile.contains(draggedStack)) {
                throw new IllegalStateException("模拟第三方容器实现抛异常");
            }
            return draggedStack == null ? null : names.get(draggedStack);
        }
    }
}
