# Native vending shell

## GTIT v2.0.17 顶栏、NEI编辑器与抽屉

本版从 NekoVMGuiV2.createHeader 移除顶栏预设按钮，编辑模式标签距右边缘14px。独立预设管理、命令和祝福编辑保留。

NEI编辑修复针对跨目录拖拽漏事件、drop后清空选择、隐藏数量控件和旧搜索框不响应MUI2 TextEvent(String)等问题。NekoEmbeddedNeiSession捕获目录按下、拖出与释放，sticky ghost跨目录连续配置；目标只收copy，不接管真实cursor/NEI发物；命中根据当前viewport重算，避免MUI2 2.3.92 getAllHovered耗尽迭代器或使用上一帧hover。NekoVMGuiV2和共享NekoWorkbenchPanel均有可见MUI2 TextField与数量控件。现代文本由MUI2处理，native NEI SearchField.setText过滤，close后恢复native布局、搜索、page与focus。

数量范围1..intmax，交易BigStack可用；原phantom上限及同物品左-1/右+1保留，超限拒绝并保留选择，普通投放不刷聊天且规则显示于tooltip。无新增debug、兼容层或同步协议。

spotlessApply、compileJava、compileTestJava、runNekoGhostSlotTest、runNekoEmbeddedNeiProbeTest、runTradeEditorDraftContractTest 在 --max-workers=2 下通过，用时14秒。NEI ABI probe 使用NEI 2.8.98 compile classpath；这不代表NEI 2.8.155 runtime、GL、物理输入或真实OS IME验收。FEED从卷帘门出货时先隐藏与WAIT同姿入口样本，到首个运动样本后连续显示；协议与服务端状态不变。

The fullscreen shell remains a GT5U/MUI2 machine container. No browser, independent transaction engine or additional inventory is introduced.

- `NekoVendingLayout` owns panel-local geometry; `NekoTradeMainPanel` applies one transform for drawing and hit testing. Minimum logical canvas is 768 × 432. GUI-scale changes resize this graph, not its synced slot identities.
- Header, tools, 36 player slots, sixteen input handler slots, trade details, and the 100 output handler slots are shared across business pages. The input count comes from the machine constant; no display-only slots are manufactured.
- Sign-in and mail retain their existing business callbacks. Lottery has a responsive native workspace; timing/result selection is not recreated by the shell. Preset/gift administration uses `NekoWorkbenchPanel` with the same transform for draw, native slot hit tests and optional NEI bounds.
- DEFAULT/PRESET/CUSTOM read `NekoPageEntry.getSource()`. `defaultEntry`, source-preset metadata and positive/negative IDs are not used as substitutes. The small plus acts on the current real page and requires `canCreateTrade()`.
- The right-side editor and native NEI catalog share the same layout as player mode. The editor replaces the input/scene area, never the trade list or player bag. NEI receives a transformed absolute logical rectangle; the main panel deliberately does not exclude the whole screen from NEI.
- NEI is optional: its client adapter is only attached when `Loader.isModLoaded("NotEnoughItems")`; without it the reserved region explains the missing catalog and the original cursor-driven 32-choice editing remains usable. No replacement catalog is constructed.
- NEI lifetime is exact-screen scoped: activate on open, deactivate on temporary close, dispose at final disposal. Confirmations and ore dialogs suppress the catalog input path.
- `IoColumnPanel` exposes scene/statistics mounts and read-only references to its 100 real `ItemSlot` widgets. The scene bridge resolves the same `outputItems`; offscreen synchronized slots do not represent another inventory. The named scene bridge is mounted after all inventory/editor registrations on both sides.
- Compact A→B rows directly show ordinary/shimmering coin costs alongside real ItemStack/meta/NBT requirements; wallet-display tokens do not become physical input requirements. Long text uses local truncation/tooltips. Shift trade, Ctrl favourite and plain-click detail selection retain original identities. “已售罄” follows authoritative one-time redemption; LOCKED, cooldown and unmet conditions are explicit. Cooldown is still seconds: 28800 = 8h 0m, never ticks.
- Gift and preset controls open independent server-authorized fullscreen paper workbenches, not vending commodities. Existing trade/ore/sign-in/online-tier/lottery editors share the local style. Ghost slots copy quantity/NBT without consuming the real source; NEI catalog input is copy-only, not cheat/spawn. Dragging uses the current parent inverse transform to keep the grab point aligned after resize.

## Artwork

Ten kraft assets are byte-identical copies from GTMB `7be4aee`; `textures/gui/vending/kraft/provenance.json` records their source paths and SHA-256 values. This adds no GTMB runtime dependency. Native item rendering retains real item glint rather than drawing a glow over an icon rectangle.

## Verification boundary

`NekoVendingLayoutTest` covers 16 resolution/GUI-scale pairs (including 1280×720 and 1920×1080), non-overlapping regions, all 32 editor positions, inventory fit and stable resize. `TradePageRefreshTest` covers three-source visibility, real-page plus eligibility, selection/pagination and native status labels. Compile checks use the pinned MUI2 2.3.92 and NEI 2.8.98 classpaths. These contracts do not constitute Minecraft GL, hardware-input or multiplayer visual acceptance.

The frozen R5 HTML is a visual reference, not replaced or embedded. MUI2 TestTile reference: 2.3.92-1.7.10 / `104df5f739b9d4b9fc7e3dcb0cfdf02c35d362ce`. New headless entries include `NekoWorkbenchLayoutTest` (24 viewport/scale combinations), `NekoTradePresentationTest`, `NekoTradeCardLayoutTest`, `NekoTextFitTest`, `NekoGhostSlotTest`, `NekoEditDragGeometryTest` and `LotteryWheelGeometryTest`. Exact final execution results belong to the refinement receipt. Native runtime scope is only main-menu startup/exit; world, server, gameplay and GUI interaction are out of scope, not pending gates.

## Round 2 native layout contract (2.0.11 candidate)

This section describes the 2.0.11 / Core 0.1.26 implementation. Compilation and the full headless check passed (72 tasks). Exact-artifact startup is recorded separately in the PR and delivery receipts. Earlier 2.0.10 / 0.1.25 evidence remains historical and is not transferred to these edits. Native runtime scope remains main-menu startup and normal Quit only; world/server/gameplay/GUI/ME interaction tests are outside scope, not pending delivery gates.

- Main business navigation moves to a horizontal top rail; category/card proportions and the bottom detail area are coordinated. The complete 36-slot player inventory is centered within its bottom region, not clipped to create apparent whitespace.
- The real machine input grows to **32 slots in two rows of 16**. External indices remain compatible: `0..7` are the original inputs; `8..107` are the unchanged 100 outputs; `108..131` map to inputs `8..31`. Existing 8/16-input saves retain their items. The UI must not invent display-only inputs or move old output indices.
- Bottom trade detail shows actual item/currency requirements and outputs rather than only an item-count summary. Existing quantity/NBT/candidate semantics, server request identity, Shift-click purchase and Ctrl-favourite behavior remain intact. Cooldown remains seconds; “已售罄” is retained.
- The trade editor reorganizes labels, quantities, draft slots and action buttons while keeping native NEI copy-only behavior and the shared viewport transform.
- **The Gift and trade Preset manager buttons are absent from the vending header.** Independent Gift (`O` by default) and Preset (`I` by default) keybindings, standalone pages, server permissions and commands remain available.

Focused layout, inventory-index/migration, native ghost-slot, editor, detail and input-transform regressions must bind to the final source before their outcome is recorded. These checks do not establish native visual acceptance.
