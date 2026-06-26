# BloodboundSMP

**Steal Hearts. Build Legacy.**
**No Claims. No Mercy.**

BloodboundSMP is a complete custom **Paper 26.1.2 PvP Lifesteal SMP** plugin suite. It is built as a no-external-Minecraft-plugin ecosystem: ranks, permissions, economy, auction house, lifesteal, skills, minigames, staff tools, recovery, boss systems, launch QA and resource-pack item models all live in this repository.

Internal jar/plugin names still use `MitchSMP-*` for Paper compatibility. Player-facing branding is **BloodboundSMP** / **Bloodbound**.

## Release Status

- Current release: `1.0.12`
- Release branch: `release/1.0`
- Stable tag: `v1.0.12`
- Runtime: **Java 25**
- Server: **Paper 26.1.2**
- Build target: **Java 25**

Do not deploy these jars on Java 21. They are built for Java 25 and will not load correctly on older runtimes.

## What Is Included

BloodboundSMP ships as 35 custom plugins that are designed to run together.

| Area | Systems |
| --- | --- |
| Core SMP | Core API, ranks, permissions, chat, homes, TPA, RTP, HUD |
| PvP | Lifesteal hearts, bounties, combat tag, skirmish, anti-cheat alerts |
| Economy | Internal economy, EconomyWatch, QuickSell, AuctionHouse, shop, resource orders |
| Progression | Skills, item abilities, mechanics guide, contracts, collections, seasons, legacy |
| Endgame | Endboss ritual, Boss Shards, rare loot, custom item models |
| Minigames | BedWars, TNT Run, Spleef, Skirmish, Skyblock, Hub |
| Staff | Admin mode, vanish, jail, model tools, fake ores, recovery, rollback, audit |
| Launch Ops | Maintenance mode, guided QA smoke tests, error tracker, feature flags, GitHub update staging |

## Server Owner Quick Start

1. Install Paper `26.1.2`.
2. Run the server on Java `25`.
3. Stop the server fully before installing or upgrading plugins.
4. Copy **all 35** `MitchSMP-*-<version>.jar` files into `plugins/`.
5. Keep existing plugin data folders during upgrades.
6. Start the server.
7. Confirm console shows all Bloodbound plugins enabled and storage backend status.
8. Run maintenance QA before opening to players:

```text
/maintenance on
/qa start smoke
/errors recent 10
/features list
/maintenance off
```

Never hot-replace plugin jars while Paper is running.

## Building From Source

From the repository/server root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\mitchsmp-src\build.ps1
```

Build output is written to:

```text
mitchsmp-src/build/jars/
```

To deploy into the local `plugins/` folder, the server must be offline:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\mitchsmp-src\build.ps1 -Deploy
```

The deploy command refuses to run while the configured Minecraft port is listening.

## Release Artifacts

For a production release, package:

- all 35 `MitchSMP-*-<version>.jar` plugin jars
- `BloodboundSMP-resourcepack.zip`
- `VERSION`
- `mitchsmp-src/build/build-info.json`
- SHA256 checksums
- deployment notes

The expected build-info for a stable release should show:

```json
{
  "version": "1.0.0",
  "javaRelease": 25,
  "dirty": false
}
```

## Main Player Commands

| Command | Purpose |
| --- | --- |
| `/menu` | Main Bloodbound menu with links to core systems |
| `/spawn` | Return to spawn |
| `/rtp` | Random wilderness teleport |
| `/sethome`, `/home`, `/homes` | Home management |
| `/tpa`, `/tpaccept`, `/tpdeny` | Player teleport requests |
| `/balance`, `/pay` | Economy balance and payments |
| `/sell` | QuickSell UI |
| `/ah` | Auction House UI |
| `/shop` | Basic shop UI |
| `/contracts` | High-risk contracts |
| `/orders` | Server resource orders |
| `/skills` | Skilltree UI |
| `/abilities` | Item ability status/settings |
| `/mechanics` | Bloodbound mechanics guide |
| `/collection` | Collection log |
| `/bounty` / `/bounties` | Bounty system |
| `/hub` | Hub world |
| `/skyblock` | Skyblock mode |
| `/bw join` | BedWars |
| `/tntrun join` | TNT Run |
| `/spleef join` | Spleef |
| `/skirmish join` | Low-risk PvP practice |
| `/endboss ritual` | Endboss ritual instructions |

Commands are permission-filtered in tab completion, so normal players should only see what they are allowed to use.

## Staff And Launch Commands

| Command | Purpose |
| --- | --- |
| `/maintenance on/off/status` | Lock or open the server |
| `/qa start smoke` | Guided launch smoke test with automatic preflight |
| `/features list` | View feature flags |
| `/feature disable <feature>` | Emergency-disable risky systems |
| `/errors recent [count]` | Recent captured server/command errors |
| `/adminmode` | Separated staff mode |
| `/v` | Vanish |
| `/freeze`, `/jail`, `/invsee` | Moderation tools |
| `/rollback` / recovery commands | Restore snapshots when required |
| `/perf` | Performance summary and diagnostics |
| `/updates status` | GitHub release/update status |
| `/updates check` | Check GitHub releases |
| `/updates stage <version/latest>` | Download release assets into staging |
| `/updates verify` | Verify manifest and SHA-256 checksums |
| `/updates approve` | Mark verified update for next restart |
| `/updates rollback` | Schedule rollback to a previous backed-up jar set |

Staff should use maintenance mode before launch checks:

```text
/maintenance on
/qa start smoke
```

Only open the server when QA returns `READY` or an acceptable `READY_WITH_WARNINGS`.

## Storage

BloodboundSMP uses SQLite-first storage when Paper/Xerial JDBC is visible to plugins. If SQLite is unavailable on a host, plugins fall back to crash-safe `.properties` files with `.tmp` writes and `.bak` backups.

At startup, Core logs one of:

```text
Storage backend: SQLite (Paper/Xerial JDBC detected)
Storage backend: crash-safe properties fallback
```

Staff also receive a short storage-backend message when joining.

## Sandbox And Testing

Test worlds use the `mitchtest_*` prefix. Admin/Owner/OP accounts have full testing freedom there. AuctionHouse and QuickSell use separated sandbox data/wallets so test listings and test sales do not affect the real SMP economy.

Use this for risky validation before touching the live SMP worlds.

## Feature Flags

Feature flags are the launch safety brake. Examples:

```text
/feature disable auctionhouse
/feature disable quicksell
/feature disable pay
/feature disable skirmish
/feature disable bedwars
/feature disable tntrun
/feature disable spleef
/feature disable skyblock
```

If a single system misbehaves during an event, disable that system instead of taking the whole server offline.

## GitHub Update Orchestrator

`MitchSMP-UpdateOrchestrator` checks GitHub Releases and stages updates safely. It never hot-reloads plugins.

Safe update flow:

```text
/updates check
/updates stage latest
/updates verify
/updates approve
```

Approval writes `plugins/.updates/pending-update.json`. Apply the staged jars only while the server is stopped, preferably through a startup/deploy script. The updater also creates backups under `plugins/.updates/backups/` and records actions in `plugins/.updates/history/update-history.log`.

Offline apply helper:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\apply-pending-update.ps1
```

Run that script only while Paper is stopped, before starting the server again.

Default source:

```text
github.owner=mhinnen2310
github.repo=BloodboundSMP
github.releaseChannel=stable
github.tokenEnv=MITCHSMP_GITHUB_TOKEN
```

## Resource Pack

BloodboundSMP uses a custom resource pack for Boss Shards, Corrupted Hearts and Bloodbound-themed items. Players should have the resource pack enabled for the intended visuals. The server can be configured to serve the pack through `server.properties` once the final hosted URL and SHA1 are known.

## Documentation

- [Build and Deploy](BUILD_AND_DEPLOY.md)
- [Changelog](CHANGELOG.md)
- [Release Checklist](RELEASE_CHECKLIST.md)
- [Launch Test Commands](BLOODBOUND_TEST_COMMANDS.md)
- [Hosted Server Upgrade](docs/HOSTED_SERVER_UPGRADE.md)
- [Player Guide](docs/PLAYER_GUIDE.md)
- [Staff Guide](docs/STAFF_GUIDE.md)
- [Technical Reference](docs/TECHNICAL_REFERENCE.md)

## Production Safety Notes

- Stop Paper before replacing jars.
- Deploy all Bloodbound jars together.
- Keep plugin data folders, worlds, configs and SQLite/properties data.
- Make a backup before every public event or release upgrade.
- Use `/maintenance on` before running guided QA.
- Keep feature flags ready for AuctionHouse, QuickSell, payments and minigames.
- Runtime folders such as `world/`, `plugins/`, `logs/`, `cache/`, `libraries/` and `versions/` must stay out of git.
