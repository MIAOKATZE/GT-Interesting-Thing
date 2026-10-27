# Native vending shell

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
