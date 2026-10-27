# Opt-in native vending client smoke fixture

This is an **interactive fixture, not a visual PASS generator**. It opens the real remote vending GUI through the registered terminal item. The controller and casings live in the owned TestLab dimension; the real player is never teleported, and no block is placed in the player's world. Normal installations register neither the scenario nor its listeners. The scenario is excluded from `suite`, `stress` and default `profile`.

## Isolated launch

Use a disposable copy of the full official RC2 client **including its config and saves**, with the newly built matching GTIT/Core jars replacing their old copies. Do not put duplicate versions in `mods`. Do not run this against a production profile: native trade editor/NEI actions can modify ordinary catalogs/configuration, which this fixture does not roll back. No dedicated server or EULA acceptance is needed for this singleplayer test.

Before startup, set `config/gtit/testlab.cfg`:

```text
testlab {
    B:enabled=true
    B:clientUiEnabled=true
    I:timeoutTicks=72000
}
```

Keep other generated settings unchanged. Create a new **Creative / Superflat / Allow Cheats ON** world, with an entirely empty player inventory and cursor. Close inventory and stand upright. No auto-open or auto-teleport occurs at startup.

## Exact commands

1. `/gtitlab status` — verify enabled and no active run.
2. `/gtitlab ui vending` — requires the local singleplayer creative OP and both switches above. It creates a real native controller plus a small symmetric owned casing fixture, then runs `checkStructure(true, base)` after 10 server ticks. A failure stops/cleans the fixture instead of forcing its formed state.
3. After **Vending ready**, keep the marked terminal selected and right-click air. The normal registered remote-terminal GUI opens. After 40 ticks spent in a remote vending GUI, three separate stacks of **8 emeralds** are enqueued through the real output API. All three share exact item/NBT identity (total **24**) but have separate output identities.
4. Close GUI, run `/gtitlab ui vending-batch`, right-click terminal again. After 40 GUI-open ticks and completion of the previous batch, the real API enqueues **17 stacks × 64 emeralds = 1,088**; the `>16 stacks` batch rule must use the rear outlet.
5. Close GUI, run `/gtitlab ui vending-legendary`, reopen. This enqueues **8 diamonds** with the public legendary flag, independently of batch size. It must use the rear outlet even though it is one stack.
6. `/gtitlab ui vending-ordinary` queues another ordinary sample in the same fashion. Take existing output before the 100-slot capacity is exhausted. Fixtures never intentionally exercise overflow/drop.
7. Close GUI and `/gtitlab ui vending-done`. Wait for the report/cleanup, then `/gtitlab status` must show `active=none` and `cleaning=false`. Terminal, paper and taken output bearing this run's marker are scrubbed from player inventory/cursor; only the recorded owned blocks are removed. `/gtitlab reset` is an emergency cancellation and intentionally yields an interrupted FAIL, not acceptance.

The adjacent hotbar slot has **64 marked paper** for ordinary input-slot click/drag/shift-click checks. No wallet credit, trade configuration, fake ME success, or automatic player action is introduced. Dropping a marked fixture item is suppressed and stops the session. Disconnect, permission/creative loss, changing worlds, shutdown and timeout also terminate/clean the session. A hard process kill cannot promise cleanup: discard the whole disposable profile rather than reusing it as gameplay state.

## Manual acceptance evidence

Record resolution, GUI scale, active renderer/shaders, screenshot names, actions and expected/actual inventory counts. Report each item separately as PASS/FAIL/NOT RUN.

- **Native opening and state:** room, floor/walls/shutter, black cat and real Minecraft item/block appearance render without GL corruption. Vanilla/NEI content outside the scene retains expected color/depth/scissor state. No second scene simulation or world camera is created.
- **Ordinary:** 3 × 8 emeralds are delivered through ordinary outlet metadata. Statistics show 24 for the exact group. Hover/selection highlights all three matching bodies. Half removes 12 total; custom 5 removes 5; all removes the remaining 7. Check real player inventory and machine stock after every operation, not just animation. Keep the marked terminal in the same selected hotbar slot while testing.
- **Large batch:** after the first group is cleared, 17 × 64 emeralds show the rear-batch routing and total 1,088. Take results are inventory-capacity limited with no lost/duplicated stock. Reopening obtains the current authoritative snapshot.
- **Legendary:** 8 diamonds route rear independently of batch size, remain selectable and can be taken through the same guarded native input path.
- **Controls and scale:** right-drag camera, overlap/occlusion picking, shift-left group take, pause and reduced-motion toggles; GUI scales 1/2/3/Auto; resize, close/reopen and resource reload. Take actions are still authoritative while visual pose is paused.
- **Editor and NEI:** on the disposable profile only, enter native edit mode, open input/output candidate editor, drag a known NEI item onto the intended target, edit quantity, save/reopen, and verify the item and quantity once. Check overlay occlusion, mouse focus, scrolling and no click-through. The fixture does **not** assert editor behavior or restore those edits.
- **ME:** **NOT RUN by this fixture.** A real AE uplink/network with inventory acceptance/rejection is required to prove wait/fade/success/failure. No phase is injected or inferred from a removed item ID.
- **Cleanup:** report JSON under `logs/gtit-testlab` must say `ownedBlocksRemaining=0` and `cleanupComplete=true`; no marked terminal/output remains in the player inventory/cursor. A report PASS only means its named native setup/enqueue checks passed. The explicit visual-input and live-ME checks remain SKIP unless independently observed and documented.

## Implementation boundaries

`TestLabVendingFixture` uses only normal registered `ItemMachines.placeBlockAt`, owned `ProbeContext` blocks, native structure checking, terminal NBT binding, and public `dispenseItemStacks` / `dispenseItemStack(stack, true)`. No machine/scene source or release gameplay defaults are changed by the fixture. It does not require a new network protocol or send an automatic open request. It must be launched with a rebuilt jar; an already-running older client will not discover the command until restarted.
