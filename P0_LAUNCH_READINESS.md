# BloodboundSMP 1.0 P0 Launch Readiness

Public launch is blocked until every P0 row is `PASS` or the affected feature is disabled through a tested feature flag.

| Gate | Status | Evidence required |
| --- | --- | --- |
| P0.1 Git/release | CODE COMPLETE | `release/1.0`, SemVer RC artifacts, guarded deploy, changelog/checklists; final RC commit/tag still required |
| P0.2 Java 25 | PARTIAL | All 31 jars compile with class major 69 (`--release 25`); local runtime is Java 26, so Java 25 boot remains required |
| P0.3 Clean server | BLOCKED | `clean-boot-test.ps1` exists; sandboxed Paperclip cannot access its cache and elevated test was unavailable |
| P0.4 Adminmode isolation | CODE COMPLETE | Separate inventory/balance/skills, death keep, AH/sell/container/drop/minigame guards; multiplayer runtime rehearsal required |
| P0.5 Rollback/snapshots | CODE COMPLETE | Recovery plugin, cuboid confirmation, persistent inventory snapshots including jail/admin/world/death/shutdown |
| P0.6 Economy integrity | CODE COMPLETE | Recipe sell caps, dynamic supply/loot inputs, rare-item overrides, negative-balance guard and large-transfer alerts |
| P0.7 AuctionHouse security | CODE COMPLETE | Atomic claims, validators, refund queue, expiry, laundering/pair alerts and `/ahadmin`; runtime lifecycle test required |
| P0.8 Anti-cheat warnings-only | CODE COMPLETE | Automatic freeze removed; player warning, staff alerts, persistent detections, `/ac recent` and `/ac review` |
| P0.9 Command/error tracking | CODE COMPLETE | 169 roots/aliases permission-covered; global command error persistence and `/errors` added |
| P0.10 Core gameplay bugs | PARTIAL | Ability shortcut/HUD/Bloodforged/Aegis/Harvest/model/setheart fixed; item-stacking and full combat regression still need in-client proof |
| P0.11 Feature flags | CODE COMPLETE | Central persistent flags, startup report, command blocking and staff override |
| P0.12 Bounty notifications | CODE COMPLETE | Online/offline/source/increase/login notifications plus automatic HUD warning |
| P0.13 Performance | PARTIAL | Per-chunk entity cap and own-task profiler added; cross-plugin listener profiling and full hot-path I/O migration remain open |

## Release Rule

Do not tag `v1.0.0`, advertise a public launch, or call the server production-ready while any row is `OPEN`, `IN PROGRESS`, or untested.

## Evidence Locations

- Build metadata: `mitchsmp-src/build/build-info.json`
- Startup diagnostics: `logs/startup-diagnostics.log`
- Test instructions: `BLOODBOUND_TEST_COMMANDS.md`
- Change history: `CHANGELOG.md` and `BLOODBOUND_DEV_LOG.md`
- Release procedure: `BUILD_AND_DEPLOY.md` and `RELEASE_CHECKLIST.md`
- Clean first-boot test: `powershell -ExecutionPolicy Bypass -File .\clean-boot-test.ps1`

## Current Verification

- Full build: PASS, 31 versioned jars, Java 25 bytecode.
- Command permission map: PASS, 169 commands and aliases.
- `git diff --check`: PASS (line-ending notices only).
- Clean Paper boot: NOT PROVEN in this pass. Paperclip cache access was denied inside the sandbox; Java 25 is not installed locally.
- Public launch verdict: **BLOCKED** until Java 25 clean boot and the runtime rehearsals above pass.
