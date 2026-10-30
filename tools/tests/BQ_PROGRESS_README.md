# GTIT BetterQuesting recovery regression

These runners compile the **complete production injector**, not extracted methods. They never download
or invoke Gradle. Caller-supplied local MCP-mapped Minecraft, Forge and BetterQuesting jars are required.
Only run when the controller has released its exclusive game/build gate.

```sh
python tools/tests/run_bq_progress_regressions.py \
  --classpath-file /path/to/native-mcp-classpath.txt \
  --java-home /path/to/jdk \
  --work-dir /path/to/new-native-test-output
python tools/tests/run_bq_stamp_regressions.py \
  --classpath-file /path/to/native-mcp-classpath.txt \
  --java-home /path/to/jdk \
  --work-dir /path/to/new-stamp-test-output
```

`--source-root` chooses the source to compile, including an unchanged baseline; the regression itself
always comes from the runner's sibling Java file. Native source/classpath SHA256 lists, compile output,
test output and newly generated local synthetic worlds are retained in `--work-dir`. The stamp runner
sets its Java temporary directory to `--work-dir`, keeping fixture writes isolated there.

## Native boundary

The database, QuestInstance, TaskCheckbox/TaskRetrieval/TaskCrafting, task registries, NBT conversion,
SaveLoadHandler dirty-player set, native progress load/save and asynchronous file writes are **real BQ**.
Seams are limited to mod logger/version, Loader presence, BQ logger/world-directory holder, network sends
and an empty OreDictionary needed by untransformed Minecraft. No SaveLoadHandler or quest/task logic is
mocked. Test reflection invokes private native load/save boundaries and inspects native dirty-player
state; the production recovery does not reflect.

The defaults window invokes the same native snapshot/reload/restore database operations as
QuestCommandDefaults, not the full command, event bus or dedicated-server boot. Fixture manifests are
small synthetic GTIT/MIAO packs, not the complete shipped 35-quest GTIT pack. Current shipped MIAO is
empty; nonempty MIAO fixtures test future dual-pack isolation without changing shipped content.
This is not a real-player login, gameplay completion or reward-grant test.

## Coverage

- Genuine missing definitions after native initial aggregate/per-player load and defaults-style reload;
  recover exact completion, serialized claimed bit and checkbox state.
- Aggregate merge=false then per-player merge=true, including both claim precedence directions.
- Retained newer claims and resets, both same version and definition refresh; additionally retain one
  listed owned quest while a different listed quest in the **same pack** is actually recreated.
- Foreign quests retained unchanged; unlisted owned quests still pruned per GTIT policy and never
  resurrected from disk. Unknown roots keep get-or-create but have no recovery fallback.
- GTIT and nonempty MIAO passes in both orders; each second pass preserves the first pack's newer live
  state. Dual stamp keys and unknown nested metadata survive. Empty MIAO does not replay GTIT/foreign
  progress and still prunes its own namespace.
- Repeated injection, missing/corrupt stamps, truly absent fresh-world BQ directory, nonfatal failed
  stamp publication, malformed input sibling and exact input-byte preservation.
- Native dirty-user set union. Offline completion users and partial-only users are written by completed
  native save futures then recovered by a subsequent native load. Partial-only cases are genuine
  incomplete states: one of two checkbox tasks, 1-of-2 retrieval, and 1-of-2 crafting.
- Filesystem stamp tests: UTF-8, both key orders/metadata, missing nested parent, regular-file parent,
  deterministic read failure through the production strict update path (no empty-object fallback, no
  temp/metadata loss), explicit preservation of malformed/non-object stamps, deterministic unsupported
  atomic move (old regular-file bytes preserved; no fallback), native move
  failure, temp cleanup, and real unwritable-directory rejection. Permission bypass is explicitly
  reported as a skip rather than a pass if run by a privileged user.

Production dirty-user discovery uses the native completion API plus only the exact native task classes
currently bundled by GTIT. It reads their top-level `completeUsers` strings and `userProgress[].uuid`
envelopes, never task `data`, protected maps, custom/subclass task serializers or recursive NBT.
**Custom task-only recovery is not guaranteed.** Native saving writes all quest states for each queued
user; this patch only unions the native dirty-user set and does not schedule a separate global save.
Existing task-index/factory-ID matching on definition changes is not a general task migration scheme.
Atomic stamp replacement guarantees complete-file visibility, not fsync/power-loss durability.

BQ 3.8.89 may log a move-then-delete `FileNotFoundException` during its own legacy progress migration:
it already moved the aggregate to QuestProgress.backup.json before attempting to delete the old path.
The tests await native save futures and reload the new per-player files; they do not use the warning
text as success/failure evidence.

Strict writer-side reading refuses unreadable, malformed or non-object existing stamp files rather than
conflating them with missing files. This is nonfatal to injection but will repeat definition refresh until
the stamp issue is repaired. The deterministic reader seam tests this without changing OS permissions.
