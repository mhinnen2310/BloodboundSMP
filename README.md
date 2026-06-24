# BloodboundSMP Server

BloodboundSMP is the English public-facing rebrand of the current custom PvP Lifesteal SMP plugin suite.

Tagline: **Steal Hearts. Build Legacy.**

Secondary tagline: **No Claims. No Mercy.**

## Active Server

The approved Bloodbound test build has been merged into this single active server folder:

`C:\Users\Mitchel\Desktop\SMP`

Pre-merge rollback backup:

`C:\Users\Mitchel\Desktop\SMP_BACKUPS\SMP-before-bloodbound-merge-20260619`

- Paper jar: `paper-26.1.2-70.jar`
- Server port: `25565`
- MOTD: `BloodboundSMP - Steal Hearts. Build Legacy. / No Claims. No Mercy.`
- Server icon: `server-icon.png`

## Build

From this server folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\mitchsmp-src\build.ps1
```

The build script creates staged Java 25 artifacts. Live deployment is a separate offline-only action documented in [BUILD_AND_DEPLOY.md](BUILD_AND_DEPLOY.md).

Current release candidate: `1.0.0-rc.2`.

Release records:

- `CHANGELOG.md`
- `RELEASE_CHECKLIST.md`
- `P0_LAUNCH_READINESS.md`
- `P1_GAMEPLAY_READINESS.md`

Launch documentation:

- `docs/PLAYER_GUIDE.md`
- `docs/STAFF_GUIDE.md`
- `docs/TECHNICAL_REFERENCE.md`

## Public Branding

Visible player-facing text should use:

- BloodboundSMP for the full server name
- Bloodbound for compact HUD/menu/prefix use
- Dark red, crimson, ancient gold, black, gray and bone white colors
- Professional English PvP Lifesteal language

## Internal Plugin IDs

Internal plugin names and jars intentionally still use `MitchSMP-*`.
These are technical IDs used by Paper dependency loading and should not be renamed during this safe rebrand pass.

## Player-Facing Command Areas

- Lifesteal and hearts
- Auction House
- Economy and QuickSell
- BedWars, TNT Run and Spleef
- Homes, TPA and RTP
- Skills, item abilities and mechanics guide
- Seasons, legacy rankings and Hall of Fame
- Staff mode, audit tools and anti-cheat alerts

## Player Entry Points

- `/menu` opens the sectioned player GUI.
- `/commands` shows the permission-filtered command list.
- `/hub` opens the protected Bloodbound hub flow.
- `/rtp` uses the active SMP wilderness.

Recent administration additions:

- `/bounty place <player> <amount>` supports cached offline targets.
- `/progression config orderDurationMinutes <minutes>` controls resource-order rotation.
- `/smpworld load <name>` safely loads a new Owner-only SMP wilderness after two confirmations.
- `/smpworld active` shows the wilderness used by `/rtp` and the hub Wilderness button.

## Rollback

Stop the server and restore the pre-merge backup above. Never overwrite current worlds or plugin data while the server is running.
