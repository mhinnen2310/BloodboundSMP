# BloodboundSMP

**Steal Hearts. Build Legacy.**
**No Claims. No Mercy.**

BloodboundSMP is a custom Paper 26.1.2 PvP Lifesteal SMP plugin suite. The project is built as a complete no-external-Minecraft-plugin ecosystem: economy, auction house, lifesteal, ranks, skills, minigames, staff tools, boss systems, resource pack support and launch QA are all maintained inside this repository.

Internal plugin IDs still use `MitchSMP-*` for Paper compatibility. Public-facing branding is BloodboundSMP/Bloodbound.

## Runtime Requirements

- Paper `26.1.2`
- Java `25` for production
- Java `25` for staging
- Java `25` build target

Do not deploy these jars on Java 21. They are built for Java 25 and will not load correctly on older runtimes.

## Main Systems

- Lifesteal hearts, bounties and combat-tag protection
- Internal economy, EconomyWatch, QuickSell and Auction House
- Skills, item-bound abilities and mechanics guide
- Endboss ritual, per-player bosses and Boss Shard progression
- BedWars, TNT Run, Spleef, Skirmish, Skyblock and Hub worlds
- Seasons, legacy rankings and Hall of Fame
- Staff mode, vanish, model tools, jail, audit, anti-cheat and recovery tooling
- Maintenance mode, guided smoke QA and bounded error tracking

## Build

From the repository/server root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\mitchsmp-src\build.ps1
```

The build output is staged in:

```text
mitchsmp-src/build/jars/
```

Deployment is intentionally separate and offline-only:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\mitchsmp-src\build.ps1 -Deploy
```

The deploy command refuses to run while the configured Minecraft port is listening.

## Release Artifacts

Versioned local release folders are created under:

```text
C:\Users\Mitchel\Desktop\BloodboundSMP-Jars\<version>
```

Each release folder should contain:

- all 33 plugin jars
- `BloodboundSMP-resourcepack.zip`
- `VERSION`
- `build-info.json`
- `SHA256SUMS.txt`
- tester/deploy documentation

## Storage

Core data storage is SQLite-first when Paper/Xerial JDBC is visible to plugins. If SQLite is not available on a host, the system falls back to crash-safe `.properties` files with `.tmp` writes and `.bak` backups.

At every server startup Core logs the active backend:

```text
Storage backend: SQLite (Paper/Xerial JDBC detected)
```

or:

```text
Storage backend: crash-safe properties fallback
```

Staff also receive a short storage-backend message when joining.

## Test And Maintenance Flow

Use maintenance mode before guided launch QA:

```text
/maintenance on
/qa start smoke
/qa pass
/qa warn <note>
/qa fail <note>
/qa recent
/maintenance off
```

Maintenance mode removes normal players while staff test. QA logs are bounded so they do not grow forever.

## Sandbox Worlds

Test worlds use the `mitchtest_*` prefix. Admin/Owner/OP accounts have full testing freedom there. AuctionHouse and QuickSell use separated sandbox data/wallets so test listings and test sales do not affect the real SMP economy.

## Git Workflow

Primary release branch:

```text
release/1.0
```

Release candidates are tagged as:

```text
v1.0.0-rc.X
```

Do not force-push release tags. Create a new RC tag for each approved release build.

## Documentation

- [Build and Deploy](BUILD_AND_DEPLOY.md)
- [Changelog](CHANGELOG.md)
- [Release Checklist](RELEASE_CHECKLIST.md)
- [Launch Test Commands](BLOODBOUND_TEST_COMMANDS.md)
- [Hosted Server Upgrade](docs/HOSTED_SERVER_UPGRADE.md)
- [Player Guide](docs/PLAYER_GUIDE.md)
- [Staff Guide](docs/STAFF_GUIDE.md)
- [Technical Reference](docs/TECHNICAL_REFERENCE.md)

## Safety Notes

- Never hot-replace plugin jars while Paper is running.
- Never delete plugin data folders during an ordinary upgrade.
- Keep runtime folders such as `world/`, `plugins/`, `logs/`, `cache/`, `libraries/` and `versions/` out of git.
- Public release jars must be built from a committed release state and verified on a clean boot test.
