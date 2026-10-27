# Native vending shell

The fullscreen shell remains a GT5U/MUI2 machine container. No browser, independent transaction engine or additional inventory is introduced.

- `NekoVendingLayout` owns panel-local geometry; `NekoTradeMainPanel` applies one transform for drawing and hit testing. Minimum logical canvas is 768 × 432. GUI-scale changes resize this graph, not its synced slot identities.
- Header, tools, 36 player slots, sixteen input handler slots, trade details, and the 100 output handler slots are shared across business pages. The input count comes from the machine constant; no display-only slots are manufactured.
- The original sign-in, lottery and mail widgets/callbacks remain intact inside a fitted workspace. Lottery timing/result selection is not recreated by the shell.
- DEFAULT/PRESET/CUSTOM read `NekoPageEntry.getSource()`. `defaultEntry`, source-preset metadata and positive/negative IDs are not used as substitutes. The small plus acts on the current real page and requires `canCreateTrade()`.
- The right-side editor and native NEI catalog share the same layout as player mode. The editor replaces the input/scene area, never the trade list or player bag. NEI receives a transformed absolute logical rectangle; the main panel deliberately does not exclude the whole screen from NEI.
- NEI is optional: its client adapter is only attached when `Loader.isModLoaded("NotEnoughItems")`; without it the reserved region explains the missing catalog and the original cursor-driven 32-choice editing remains usable. No replacement catalog is constructed.
- NEI lifetime is exact-screen scoped: activate on open, deactivate on temporary close, dispose at final disposal. Confirmations and ore dialogs suppress the catalog input path.
- `IoColumnPanel` exposes scene/statistics mounts and read-only references to its 100 real `ItemSlot` widgets. The scene bridge resolves the same `outputItems`; offscreen synchronized slots do not represent another inventory. The named scene bridge is mounted after all inventory/editor registrations on both sides.
- A→B rows use real ItemStack/meta/NBT icons, original tooltips, Shift trade and Ctrl favourite actions. Plain click selects a detail card. “已售罄” only follows the existing authoritative one-time-redemption state, LOCKED remains gold and cooldown remains cyan.
- Gift and preset controls open their existing independent server-authorized pages. They do not become vending commodities.

## Artwork

Ten kraft assets are byte-identical copies from GTMB `7be4aee`; `textures/gui/vending/kraft/provenance.json` records their source paths and SHA-256 values. This adds no GTMB runtime dependency. Native item rendering retains real item glint rather than drawing a glow over an icon rectangle.

## Verification boundary

`NekoVendingLayoutTest` covers 16 resolution/GUI-scale pairs (including 1280×720 and 1920×1080), non-overlapping regions, all 32 editor positions, inventory fit and stable resize. `TradePageRefreshTest` covers three-source visibility, real-page plus eligibility, selection/pagination and native status labels. Compile checks use the pinned MUI2 2.3.92 and NEI 2.8.98 classpaths. These contracts do not constitute Minecraft GL, hardware-input or multiplayer visual acceptance.
