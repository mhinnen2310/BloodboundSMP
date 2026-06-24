# BloodboundSMP 1.0 P0 Launch Readiness

Public launch is blocked until every P0 row is `PASS` or the affected feature is disabled through a tested feature flag.

| Gate | Status | Evidence required |
| --- | --- | --- |
| P0.1 Git/release | IN PROGRESS | Branches, clean release commit, changelog and tagged RC |
| P0.2 Java 25 | IN PROGRESS | `--release 25` build plus clean Java 25 runtime boot |
| P0.3 Clean server | OPEN | Empty-server first boot and generated config audit |
| P0.4 Adminmode isolation | OPEN | Death/container/AH/pay/minigame leak tests |
| P0.5 Rollback/snapshots | OPEN | Region rollback and inventory restore rehearsal |
| P0.6 Economy integrity | OPEN | Craft/smelt price invariants and spike alerts |
| P0.7 AuctionHouse security | OPEN | Full lifecycle, validation, laundering and dupe tests |
| P0.8 Anti-cheat warnings-only | OPEN | No automatic punishment; 24-hour review flow |
| P0.9 Command/error tracking | OPEN | Permission/usage matrix and captured command failures |
| P0.10 Core gameplay bugs | OPEN | Ability/item/combat regression suite |
| P0.11 Feature flags | OPEN | Disable/enable/override and startup state tests |
| P0.12 Bounty notifications | OPEN | Online/offline/source/HUD notification tests |
| P0.13 Performance | OPEN | Hot-path audit, entity limits and profiler evidence |

## Release Rule

Do not tag `v1.0.0`, advertise a public launch, or call the server production-ready while any row is `OPEN`, `IN PROGRESS`, or untested.

## Evidence Locations

- Build metadata: `mitchsmp-src/build/build-info.json`
- Startup diagnostics: `logs/startup-diagnostics.log`
- Test instructions: `BLOODBOUND_TEST_COMMANDS.md`
- Change history: `CHANGELOG.md` and `BLOODBOUND_DEV_LOG.md`
- Release procedure: `BUILD_AND_DEPLOY.md` and `RELEASE_CHECKLIST.md`
