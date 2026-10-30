# Reincarnation persistence and recovery

Reincarnation is supported only in an integrated server that has **not** been
published to LAN. Back up both the global record and the relevant world before
upgrading or performing manual recovery. Do not edit either while Minecraft is
running.

## Records and ownership

- `config/gtit/reincarnation.dat` is the encrypted global license: player UUID,
  permanent timeline fingerprints, and the cross-world reward mailbox. Its v2
  mailbox includes `grantInFlight` and the `delivered` contiguous prefix. Encryption
  is obfuscation, not a security boundary.
- `<world>/data/gtit_reincarnation_world.dat` uses Minecraft's native compressed
  NBT envelope, `{data: ...}`. It holds hull/column/row progress, deposited item
  references, `Migrated`, `MigrationId`, `GrantStarted`, and `GrantClaimed`.
- Ordinary GUI deposits, withdrawals, hull consumption, and row unlocks update
  **only the world-local receipt**. They do not rewrite the global license.
  Confirming reincarnation and completing a claim are Handler-owned global
  transitions. A failure to write an unrelated global file must not lose a GUI
  movement already accepted by vanilla.
- Accepted slot callbacks are obligations, not cancelable button requests. Close,
  replacement, and orderly stop drain their local queue exactly once. The shared
  WorldData instance retains the resulting receipt through close/reopen.

## Legacy v1 migration

The legacy encrypted v1 record contains both global and world-local fields. It
must not be replaced by v2 until its world-local fields have a durable receiver.
The protocol is:

1. Checkpoint a random `MigrationId` in the receiving world's WorldData.
2. Atomically add `migrationOwner = MigrationId` to the **complete v1 source**.
3. Copy legacy progress/deposits to the receiver; checkpoint them together with
   `Migrated = true`.
4. Only that matching owner may replace v1 with the v2 license/mailbox.

The owner is a persisted identity, not a directory name; renaming the receiving
world does not invalidate it. An interruption after reservation leaves the full
source available to the same owner. An interruption after the receiver receipt
retries source consumption without importing twice. Login performs migration too,
so grant startup cannot overwrite legacy progress before a GUI was opened.

| Persisted receiver | Source | Recovery |
| --- | --- | --- |
| No identity | Complete unowned v1 | Retry normally; no receiver was acknowledged |
| Identity, not migrated | Complete v1 reserved to that identity | The same receiver may retry import |
| Identity and migrated receipt | Complete v1 reserved to that identity | Retry source consumption, not import |
| Migrated receipt | v2 | Migration is complete |

### Reserved owner is unavailable

Restore the same receiver from a backup retaining its `MigrationId`, then reopen
it to resume. Keep a backup of the full reserved global source. If no such backup
exists, stop and explicitly reconcile the intended receiver and any previous
imports/backups. **Do not remove `migrationOwner` or automatically reassign it to
another world**: an unavailable owner is indistinguishable from one that already
imported the items and may later return.

### Old ambiguous receipt

Older versions could leave `Migrated = true` without `MigrationId` while v1 still
existed. There is no reliable ownership evidence. This version refuses to consume
that source based on the old flag alone. Back up the global file and all candidate
worlds; identify the original receiver from its history and contents before an
explicit reconciliation. Do not clear `Migrated`, delete the source, or open a new
world to force a fresh import. Already-existing unowned pre-protocol sources cannot
be retroactively proved unused; downgrades also do not enforce the new ownership
field. No destructive automatic reconciliation is provided.

## Checkpoints and grant retries

Immediate checkpoints serialize the native NBT envelope, force a same-directory
temporary file, and replace the destination with `ATOMIC_MOVE`. Unsupported atomic
moves fail rather than degrade to a non-atomic replacement. Temporary symlinks are
rejected. A reported WorldData checkpoint failure throws and leaves that receipt
dirty/in memory; only successful replacement clears its dirty bit. Correct the
filesystem problem and retry before exiting. Ordinary world-local updates remain
subject to Minecraft's normal save timing; a dirty bit alone is not proof of a
durable save.

First eligible login checkpoints `GrantStarted` **before** the animation delay.
Logout or death during the delay therefore retains eligibility, and respawn can
resume an existing commitment. A delivery checkpoint requires a readable active
mailbox: missing/unreadable data is not a successful acknowledgment. A failed item
handoff cannot be skipped by later scheduled items. A failed post-handoff write
keeps the in-session prefix advanced, so that same context retries its checkpoint
before handing out the next item. Unresolved I/O failures may require restoring
access and logging in again; preserve backups before manual reconciliation.

## LAN publication during ascension

Publishing the integrated server to LAN revokes pending reincarnation damage,
ascension, hardcore enforcement, and its pending deletion countdown/target. A
carrier rider returns to the recorded finite, bounded origin only if still in the
exact same world; fall distance is reset after that return. A different-world
entity or invalid origin is not teleported. Cancellation restores only the
original hardcore/game mode values that still match this enforcer's overrides;
an originally hardcore world remains hardcore. Repeated cancellation is harmless.
The global fingerprints and mailbox are not cleared, so escrow remains available
and a later valid single-player login can apply the ordinary rules.

## Exact limits

This is **not a transaction spanning player inventory, chunk drops, world data,
and the global mailbox**. A process/power failure between their independent saves
can still duplicate or lose a reward. A failed post-handoff checkpoint followed
by discarding that in-memory context also requires reconciliation; the prefix is
not a replacement for a cross-file transaction. Do not claim crash-proof
exactly-once delivery.

There is no parent-directory fsync guarantee, concurrent-process writer lock,
backup rollback reconciliation, or safe downgrade guarantee. Atomic replacement
protects an individual file, not all related files together. Failed final saves
can still lose dirty state when the process exits. World deletion does not follow
symbolic links; it remains an intentional destructive action only for the selected
single-player timeline. The item-reference format intentionally keeps registry ID
and metadata rather than full item NBT; invalid registry entries retain the
existing audited-skip policy during grant completion.
