# Trade-history persistence and recovery

Trade histories enforce one-time redemptions, lifetime limits and cooldowns. Losing a record is not equivalent to a new player.

## Files and compatible interpretation

- Personal records remain `<world>/gtit_neko_histories/<player UUID>.dat`, using the existing uncompressed Minecraft NBT encoding.
- New records have integer `historyFormatVersion = 1`. Their `histories` list always contains **current personal fallback activity**, including when `legacyHistoryConsumed = true`.
- An old, unversioned record with `legacyHistoryConsumed = true` is an archive of the already-migrated source. It is not imported again and is not treated as new personal activity. Historical files cannot distinguish activity that the old implementation incorrectly mixed into that archive; this change does not invent a recovery for those ambiguous records.
- Old unconsumed records remain importable. Unsupported versions, malformed groups/counters/list element types and duplicate group IDs fail closed. Existing bytes are left untouched; no zero-history replacement is created.
- Lifetime and cooldown counters are nonnegative `long` values and saturate at `Long.MAX_VALUE`. Repeated team-transfer copies must not wrap negative and reopen one-time rewards.

## One-time legacy migration protocol

`legacy-migrations.dat` is a narrow, version-1 migration-intent journal, **not** a general transaction log for purchases, wallets or machines. It contains one immutable entry per migrated player: original team UUID and a detached snapshot of that player's legacy histories.

1. Read and validate every unconsumed member source before changing either ledger.
2. Atomically replace the journal with all staged intents. A failed journal write aborts before any merge or consumed marker.
3. Apply each intent to team history and record its player-UUID receipt in the same `NekoTeamData` NBT as the history. Live personal references are consumed/forwarded to the team, so late completions use the surviving owner.
4. Replace the personal file with a versioned consumed marker and its now-current fallback records. A failed marker replacement retains the journal and the in-memory retry state.

On restart, the journal supersedes an old unmarked legacy source. A team with no receipt replays the snapshot; a team with the receipt does not add it again. Thus a consumed personal marker cannot erase restrictions merely because the GTNHLib team save lagged behind it. Replay applies to the original target team and to a current team containing that source player. Receipts follow team merges and member transfers alongside their histories. Explicit history resets retain receipts, so replay cannot undo an administrator reset.

The journal retains these snapshots indefinitely; growth is proportional to the number of migrated players and their legacy groups, not to subsequent purchases. The old team-wide `legacyHistoryMigrated` bit remains readable but no longer suppresses a newly joined member's migration or a pending replay. There is no automatic pruning: safely deleting an intent would require proof that every relevant team receipt is durable. Back up the personal files, journal and GTNHLib team data together. Do not delete the journal or strip receipts as a recovery shortcut.

## Failure and world lifecycle

- Replacement uses `AtomicNbtFile`: a same-directory temporary file followed by atomic replacement, never delete-old-then-rename. If the filesystem does not support the atomic move, the save fails and the previous file remains.
- Logout retains the canonical in-memory history object. Periodic, logout, stopping and stopped hooks retry saves.
- `unloadAll` fences new history lookups. Failed writes retain the old world's directory and cached records. A new world cannot bind until those pending old-world writes succeed; old data is never saved under the new world's path.
- A corrupt migration journal rejects world binding. Restore a coherent backup or repair the offending file after inspecting logs; do not substitute an empty ledger just to enable trading.
- These guarantees cover reported I/O failures, atomic file replacement, and replay after independently lagging team saves. They are **not** an fsync/power-loss guarantee or an atomic transaction spanning physical output, wallet, player inventory, world chunks and team storage. Ordinary post-migration purchase durability still depends on their respective save schedules.

## Locking and verified boundaries

Manager cache/lifecycle/migration operations take the manager monitor before history snapshots. The executor resolves history first, performs payment/delivery under that history's monitor, then releases it **before** calling the manager to save completion. It does not hold a global manager monitor across ME/inventory/output callbacks. The resolved-history preflight avoids resolving the team again while the history monitor is held. Team receipt/merge operations take the team-data monitor before the history merge locks.

Production GUI admission, membership events and shutdown remain server-thread operations; these changes do not claim an arbitrary multithread transaction across external mod callbacks. Regression coverage uses actual production manager/team-data/history code and real Minecraft NBT/file I/O for corruption, write failures, cross-world fencing, migration journal/marker ordering, lagging team saves, receipt propagation, live-reference forwarding, counter saturation, and a save-versus-completion latch race. Cached test runs use narrow external Teams/FML facades where native dependencies are unavailable. Full transformed Forge/GTNHLib/AE/MUI server runtime and abrupt power-loss testing remain separate integration requirements.
