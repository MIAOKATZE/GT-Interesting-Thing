# Dedicated test instances

All five directories are independent physical extractions, bind only `127.0.0.1`, and use separate `world`, `config`, `logs`, libraries, and mod JARs. The shared `E:\CodeGT\测试实例` is untouched. EULA was set to true only after the user's explicit authorization on 2026-10-16.

| Instance | Directory | Port | Base pack |
|---|---|---|---|
| GTIT | `E:\CodeGT\测试实例-GTIT` | 25671 | Official 2.9.0-RC-2, matching GT5U 5.09.54.205 |
| GTSR | `E:\CodeGT\测试实例-GTSR` | 25672 | Official 2.9.0-RC-2, matching GT5U 5.09.54.205 |
| GTMB | `E:\CodeGT\测试实例-GTMB` | 25673 | Official 2.9.0-RC-2, matching GT5U 5.09.54.205 |
| GTSWN | `E:\CodeGT\测试实例-GTSWN` | 25674 | Official 2.9.0-RC-2, matching GT5U 5.09.54.205 |
| OUTPOST | `E:\CodeGT\测试实例-OUTPOST` | 25675 | Official 2.9.0-RC-2, matching GT5U 5.09.54.205 |

All five instances use `C:\Users\60158\Downloads\GT_New_Horizons_2.9.0-RC-2_Server_Java_17-26.zip`, SHA256 `e005a0d050ec06073b3b0d11b149b5ab9355cacf4b8c4b837c50b8a89946dbf9`, the complete [official RC2 server pack](https://downloads.gtnewhorizons.com/ServerPacks/betas/GT_New_Horizons_2.9.0-RC-2_Server_Java_17-26.zip). The user corrected the original 2.8.0 archive selection on 2026-10-16. Old baselines are retained under each instance's `.testlab/` and are outside Forge's mod search paths. Each instance's `testlab-instance.json` records the current provenance and archive checksum. Preserve each project's installed mods, worlds, test settings, and ownership locks when maintaining its instance.

Use a Java 17–26 executable for all five instances. From the GTIT repository root:

```powershell
# Starts a hidden supervisor, waits for Minecraft's Done marker, then returns.
.\tools\testlab\Invoke-TestLab.ps1 start -Instance GTIT -Java 'D:\Program\Java\bin\java.exe'
.\tools\testlab\Invoke-TestLab.ps1 status -Instance GTIT
.\tools\testlab\Invoke-TestLab.ps1 command -Instance GTIT -Command 'gtitlab status'

# Waits for COMPLETE and validates its new, matching JSON report; FAIL/SKIP exit nonzero.
.\tools\testlab\Invoke-TestLab.ps1 probe -Instance GTIT -Command 'gtitlab suite' -Timeout 900
.\tools\testlab\Invoke-TestLab.ps1 probe -Instance GTIT -Command 'gtitlab suite' -RequireScenario native-pocket,native-machine-trade,native-team-wallet,native-business-events -RequireCheck native-pocket/special-nbt-roundtrip -Timeout 900
.\tools\testlab\Invoke-TestLab.ps1 probe -Instance GTIT -Command 'gtitlab stress 3' -Timeout 900
.\tools\testlab\Invoke-TestLab.ps1 stop -Instance GTIT
```

`python tools/testlab/runner.py run --instance 'E:\CodeGT\测试实例-GTIT' --java '<java.exe>'` runs the supervisor in the current terminal. `start` detaches it without a GUI. Python options `--xms` and `--xmx` default to `1G` and `6G`; `--startup-timeout` defaults to 900 seconds, `--max-runtime` to 3600, and `--stop-timeout` to 120. Do not run five 6-GiB servers simultaneously unless memory permits. The configured maximum runtime ends with normal `stop`; forced termination is limited to the supervisor's own child after checking PID and kernel creation time.

GTIT config is `config/gtit/testlab.cfg`: `enabled=true`, `clientUiEnabled=false`, `dimensionId=1048580`. Install only the built GTIT runtime JAR into this instance's `mods/` before launching. The other four instances contain the corrected official pack; installation and compatibility checks for their respective development mods belong to those projects' tasks.

Before any world existed, GTIT alone was set to `level-type=FLAT`, `generate-structures=false`, and `view-distance=3` to reduce initial spawn generation during repeated debugging. Its manifest records this testlab optimization; the four other packs retain their original world settings.

The Mod defaults to `enabled=false`; the prepared GTIT instance explicitly enables it. Default `suite` runs the harness, pocket, machine/trade, team-wallet and business-event scenarios. `stress` uses the distant canvas and bounded native data operations; `profile native-machine-trade 5` repeats a selected scenario with timing. `refresh` clears owned fixtures and starts a fresh suite. An active run cancelled with `reset` is reported as FAIL, then cleaned.

The explicit persistence pair validates native pocket, wallet, sign-in and mail codecs across two real JVMs. It stores a checksummed fixture only under `logs/gtit-testlab/persistence/`; this does not claim a full multiplayer save/load test. A pending fixture is preserved until successfully read:

```powershell
.\tools\testlab\Invoke-TestLab.ps1 probe -Instance GTIT -Command 'gtitlab run native-persistence-write'
.\tools\testlab\Invoke-TestLab.ps1 stop -Instance GTIT
.\tools\testlab\Invoke-TestLab.ps1 start -Instance GTIT -Java 'D:\Program\Java\bin\java.exe'
.\tools\testlab\Invoke-TestLab.ps1 probe -Instance GTIT -Command 'gtitlab run native-persistence-read' -RequireCheck native-persistence-read/different-jvm-required
```

For the optional physical-client probe, enable both `enabled` and `clientUiEnabled` in that dedicated client's `config/gtit/testlab.cfg` and restart it. With the server lab enabled, a connected OP with an empty inventory and cursor can execute `/gtitlab ui pocket` from any dimension. The client invokes the real pocket item UI and acknowledges the expected MUI2 panel. Reports distinguish opening, refusal, and timeout; this does not verify rendered pixels or input behavior. No player is teleported. Temporary marked pocket items, cursor contents and drops are cleaned after the probe.

New native probes implement `ProbeScenario` and return a cooperative `ProbeTask`; register them with `TestLabRuntime.register`. Use `ProbeContext` for bounded coordinates, test identities, marked items, checks and cleanup callbacks. Default and stress scenarios are selected separately; persistence and client probes opt out of default suites. Register cleanup before publishing a business fixture, and use multiple ticks for sustained work. Raw JSON and the runner both refuse to treat an entirely skipped selected scenario as PASS.

Each run writes `.testlab/runs/<timestamp>-<id>/console.log` and `report.json`. `start` also writes `.testlab/supervisor.log`. `last-run.json` points to the latest completed run. GTIT scenario reports stay in `logs/gtit-testlab/<runId>.json`; probe summaries are copied to the supervisor run directory. A successful `command` only acknowledges stdin delivery. `probe` appends a fresh `--request <UUID>` to its command, then requires the START marker and report to echo that request ID and the COMPLETE marker to carry that START's run ID. This prevents an earlier queued task from supplying the probe result. It also checks schema, timestamps, requested mode/scenario, consistent case counts, complete fixture cleanup, and at least one concrete PASS/FAIL case for every selected scenario; PASS with an entirely skipped selected scenario is rejected. `--require-scenario` and `--require-check scenario/check` can enforce integration coverage without duplicating native scenario lists in the runner. Reports must be new and confined to this instance's report directory. Lab-changing commands are serialized while a probe owns the channel. Normal server logs and worlds persist across runs; `gtitlab reset`/`refresh` control only the lab's own region.

An exclusive `.testlab/owner.json` lock contains supervisor and Java PID plus creation identity. Independent instances can run concurrently; one instance permits one supervisor. Never delete a live owner's lock, never kill every `java.exe`, and never force-reset a world directory. `recover-lock` archives stale lock metadata only when its recorded processes are gone or their PID creation identities differ. It can also recover an abandoned probe lock; it refuses one whose client process remains alive.

Commands and probes pin the owner token captured for the operation. If the instance restarts before the command is queued, the request fails instead of sending an old operation to the new server. The supervisor also rejects queued requests carrying another generation's owner token.

Exit codes: 0 success, 2 configuration/request/report validation error, 3 occupied instance/channel, 4 startup/server failure, 5 timeout, 6 lab FAIL or SKIP, 130 keyboard interruption. `stop` waits for supervisor shutdown and returns the matching run's final exit code; inspect `last-run.json` for details and any forced termination.

```powershell
python -m unittest discover -s tools/testlab -p test_runner.py -v
```

These tests use disposable Python child processes to verify the runner's locks, PID identity, stdin/stop, timeouts, instance protection, and report correlation. They do not validate Minecraft or GTIT gameplay. `prepare_instances.py` is a fail-on-existing extraction utility, not an upgrade/reset command, and preserves the archive's EULA state.
