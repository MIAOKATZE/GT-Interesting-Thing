# Static review, 2026-10-29

## Scope

Three review rounds were performed against the changes based on `8d0e1bdd9ab2b77460b257e036ce2366096765b3`: two multi-angle review/repair passes and a fresh independent third pass. The final integration review checks the combined candidate, not only individual patches.

The repairs concentrate on value conservation and failure boundaries:

- Exact wallet balances, team/history merge ownership, persistent redemption restrictions, bounded physical withdrawals and honest ME refund receipts.
- Mail/sign-in/lottery entitlements, partial delivery, strict existing-data decoding, unavailable configuration handling, and world-bound cache ownership.
- Reincarnation migration receipts, accepted inventory callbacks, failed checkpoints, and cancellation of destructive single-player behavior when a session becomes public.
- Pocket carrier identity across native item copies, fluid NBT at AE boundaries, and nonempty multichannel-cell nesting.
- Client connection ownership, bounded admission for uncommitted requests, native packet limits, and editor compare-before-write checks.
- BQ pack validation before mutation and cooperating GTIT flight sources.

No shared Core defect was established. No workflow or automatic merging is added.

## Verification limits

**A complete Gradle build and a real GTNH client/server session were not completed.** Dependency resolution reached a network-policy block at Maven Central. This review does not certify Java/Jabel integration, transformed mixins, the whole UI graph, live Teams/ME/BQ behavior, or pack startup.

Focused regression checks compile selected production code against available native Minecraft/Forge/AE/Core classes where feasible. Unavailable collaborators use explicitly scoped fixtures; some GUI/callback probes execute extracted production methods. These checks are evidence for the tested boundaries, not a replacement for a complete mod build. Before/after failures were reproduced for the repaired defects. The repository also retains executable regression mains and wires the added suites into `check`.

The added reward-configuration regression tasks use isolated build directories and require the `gtit.rewards.testScratch` opt-in. Do not invoke those mains against a real game directory.

Single-file atomic replacement is not a transaction spanning player data, wallets, mailboxes, team state and world files. No universal crash-exactly-once or power-loss guarantee is claimed. No automatic reconciliation deletes ambiguous migration records.

## Behavior and recovery notes

- [Wallet conservation and compatibility](wallet-conservation.md)
- [Trade history migration and recovery](history-persistence.md)
- [Reward configuration and lifecycle](reward-config-and-lifecycle.md)
- [Reincarnation persistence and recovery](reincarnation-persistence.md)

Malformed existing reward configurations now remain intact and become unavailable until repaired and successfully reloaded. Explicit valid empty configurations remain intentional. Preserve backups before upgrading or downgrading persisted formats.

Remaining UI limitation: a stale trade-editor submission is rejected before modification, but general rejection does not yet provide correlated acknowledgement and complete draft recovery. Saturated request admission leaves business state unchanged; universal overload feedback/fairness is not claimed.
