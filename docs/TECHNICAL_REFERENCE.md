# BloodboundSMP Technical Reference

## Build And Deploy

Build from the server root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mitchsmp-src\build.ps1
```

The build stages versioned jars. Deploy only while Paper is fully stopped; follow [BUILD_AND_DEPLOY.md](../BUILD_AND_DEPLOY.md).

## Permissions And Commands

- Player and tester command catalog: [BLOODBOUND_TEST_COMMANDS.md](../BLOODBOUND_TEST_COMMANDS.md)
- Permission-filtered in game: `/commands`
- Primary rank definitions and inheritance: `plugins/MitchSMP-Permissions/`
- Command declarations: each module's `mitchsmp-src/<module>/resources/plugin.yml`
- Build verification rejects declared command aliases without a permission mapping.

Important P1 nodes:

- `mitchsmp.gameplay.use`: `/goals`, `/rookie`, `/recoverykit`, `/report`
- `mitchsmp.reports.staff`: reports, staff profiles and staff notes
- `mitchsmp.skirmish.play`: `/skirmish`

## Configuration

- Core feature flags: `plugins/MitchSMP-Core/`
- Progression tuning: `/progression config list` and `plugins/MitchSMP-Progression/`
- Contract grace: `contractClaimGraceMinutes`
- Contract rotation: `contractDurationMinutes`
- Season name/duration: `/season` administration and `plugins/MitchSMP-Seasons/`
- EconomyWatch tuning and snapshots: `plugins/MitchSMP-EconomyWatch/`

Use supported commands for live tuning. Stop Paper before manual property-file edits.

## Data Storage

Plugins use local YAML/properties/SQLite-style internal storage under `plugins/MitchSMP-*`. Gameplay P1 data is in `plugins/MitchSMP-Gameplay/gameplay.properties`; Skirmish state is in `plugins/MitchSMP-Skirmish/skirmish.properties`. Player UUIDs are authoritative. Runtime inventory snapshots are owned by the Recovery service.

P1 persistent records include rookie progress, playtime, recovery cooldowns, assist anti-farm pairs, First Blood locks, reports, staff notes, Skirmish inventory returns, training stats, contract rotations and named seasons.

## Migration Notes

- Internal plugin names remain `MitchSMP-*` for dependency compatibility.
- New modules require `MitchSMP-Gameplay` and `MitchSMP-Skirmish` jars to be deployed with the full staged set.
- Existing HUD customization is preserved. `/hud reset` applies the new default: balance, kills, deaths and rank; active abilities are appended automatically.
- Rookie playtime begins accumulating when the Gameplay module is first installed; historical playtime is not reconstructed.
- Contract rotation order is persisted per player and cycle after first generation.

## Test Plan

1. Start a clean copy using Java 25 and Paper 26.1.2.
2. Confirm all 33 plugins enable without stack traces.
3. Test as Default, VIP, Moderator and Owner with `/commands` and tab completion.
4. Complete and claim each Rookie Contract; verify no-sell/no-AH restrictions.
5. Kill with a third assisting player; verify one heart for the killer, none for the assistant.
6. Complete one Skirmish with two players and a staff solo test; verify inventory/location restore and no legacy stats.
7. Rotate contracts; claim a completed previous-cycle contract during grace.
8. Restart Paper and verify all P1 state persists.
9. Follow the wider [RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md).
