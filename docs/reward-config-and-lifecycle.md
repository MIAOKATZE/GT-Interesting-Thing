# Reward configuration and lifecycle safeguards

Lottery payment configuration now fails closed. Missing registry items, invalid nonempty
NBT, null cost rows, and nonpositive, fractional, or overflowing quantities no longer turn a
paid pool into a free/cheaper pool. The configuration loader and in-game pool editor use the
same payment decoder. Intentionally empty `costItems: []` remains supported; valid legacy
`nekoCurrencyId` / `costPerDraw` payments still migrate.

If loading fails, the server logs the invalid payment and exposes no lottery pools. It keeps
the existing JSON bytes; the editor cannot overwrite an unreadable file with a new empty
configuration. Back up `config/gtit/lottery/lottery.json`, restore the missing item/mod or fix
the reported payment/NBT, then reload. Remove a requirement or clear costs only when an
administrator deliberately intends to make the pool cheaper/free. Legacy migration retains
its `.bak` source. Already purchased prizes do not depend on a subsequent configuration
reload: the machine owns materialized stack copies in its persistent output queue.

Mail and lottery managers revoke access before their shutdown flush. A successful flush
clears the old world binding; a failed flush retains the old directory/cache for retry rather
than serving it as another world. Sign-in, mail, and lottery state is unavailable before world
initialization. A startup failure earlier in the common initialization chain therefore cannot
create claims that have no save directory.

Delayed lottery refreshes and mail reminders are bound to the original player entity and are
cleared on server lifecycle boundaries. They never own purchased items. Disconnected sign-in
items remain in the player's pending ledger, full mailboxes preserve existing attachments and
unconsumed template eligibility, and lottery prizes remain in the machine queue independently
of these UI notifications.

The headless regression entry points `LotteryCostValidationTest` and
`RewardSessionLifecycleTest` require an isolated working directory and
`-Dgtit.rewards.testScratch=true`. They are focused tests, not evidence of a complete Forge /
GregTech launch or arbitrary process-crash atomicity across player, wallet, and chunk files.

## Sign-in, online-time and blessing configuration recovery

The same preservation policy applies to:

- `config/gtit/signin/daily_signin.json`
- `config/gtit/signin/online_time_config.json`
- `config/gtit/mail/blessing_config.json`

An unreadable existing file (including a JSON `null` root) is **not replaced by defaults**.
The affected configuration becomes unavailable: new claims that require it, configuration
queries and edits fail explicitly until a successful reload. Failed saves are reported too;
they do not announce success or leave an affected reward configuration active with unsaved
changes. Replacement uses a temporary file and an atomic move, preserving the previous
file when replacement fails. Defaults are generated only when neither the current file nor
its legacy source exists.

Back up the existing file before repair. Fix the logged JSON/schema error or restore the last
known-good file, resolve any filesystem permission/storage problem, and use the corresponding
administrative reload (or restart the server). Do not delete the file merely to suppress the
error: that requests first-use defaults and may change the intended reward policy. A successful
reload explicitly restores access. Existing mailed attachments, queued sign-in items and
purchased machine prizes retain their already-recorded contents.

An explicit empty `tiers: []` / `cumulative_tiers: []` is a deliberate disablement and remains
empty after save/reload. Empty reward item arrays remain valid; omitted optional editor fields
still mean no change. Present malformed reward objects, non-array `items`, malformed item
entries and fractional/negative/overflowing quantities are rejected rather than silently
becoming smaller or empty rewards. Missing configured blessing items/currency items prevent
the mail from being constructed, so its once-only delivery receipt is not consumed.

## Persisted obligations and team pity

Existing malformed NBT fields are distinguished from absent legacy fields. Wrong list element
types, claim-receipt types, attachment remainders and pending reward fields now stop decoding
instead of falling back to empty lists or fresh claims. Valid empty lists and absent optional
legacy fields remain readable. Preserve and repair the affected player/team file or restore a
backup; do not replace it with an empty record to bypass the failure.

Lottery pity is loaded when a team key is first accessed, including after a membership change.
Subsequent teammate logins cannot overwrite live counters with stale disk data after a failed
save. Saving an unseen team cannot erase its existing file; failed decoding never publishes a
partial map. Nonnegative pity counters saturate rather than wrapping negative.

`RewardPersistenceBoundaryTest` checks these native-NBT and team-cache boundaries.
`RewardConfigPersistenceTest` checks malformed-file preservation, explicit unavailable states,
repair/reload, empty configurations, replacement failures and strict reward decoding. Both
require the same isolated working-directory opt-in as the earlier reward tests. Their headless
fixtures do not establish full Forge/GTNH team-event integration, a game-client run, or
cross-file process-crash atomicity.
