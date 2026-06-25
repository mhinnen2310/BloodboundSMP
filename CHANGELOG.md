# Changelog

## 1.0.0 First Public Release

- Promoted the launch-hardened RC6 build to the first public BloodboundSMP release.
- Finalized Java 25 release packaging, maintenance QA guidance and GitHub documentation.
- Kept all gameplay/data behavior from RC6 intact.

## 1.0.0-rc.6 Launch Stability Hardening

- Fixed maintenance/QA access so staff/op can run guided smoke QA while players remain locked out.
- Added automatic smoke QA preflight checks for plugin set, key services and SQLite backend.
- Fixed Ritual Chest Corrupted Heart detection by using the registered CorruptedHeartService before item-model fallbacks.
- Allowed adminmode item drops inside `mitchtest_*` sandbox worlds while preserving strict SMP cleanup.
- Hardened economy amount handling against `NaN`, `Infinity`, `1e309`, negative values and over-limit transactions.
- Hardened stored double reads so non-finite persisted values fall back safely.
- Added AuctionHouse full-inventory checks before purchase withdrawal and kept full-inventory refunds queued.
- Reduced noisy performance alerts by requiring sustained lag samples and increasing launch alert defaults.
- Reworked command-send filtering to avoid Paper API return-type mismatch crashes.

## 1.0.0-rc.5 EndBoss Ritual And Sandbox Isolation

- Fixed physical EndBoss Ritual Chest validation for Boss Shards and Corrupted Hearts using `item_model` as well as legacy CustomModelData.
- Made the ritual sacrifice mob die through real lightning/lethal removal before the Ritual Chest appears.
- Added clearer missing-item feedback when breaking an incomplete Ritual Chest.
- Separated sandbox AuctionHouse listings from normal SMP AuctionHouse listings.
- Separated sandbox QuickSell balance behavior from normal SMP balances and EconomyWatch.
- Reworked the repository README into a clearer GitHub front page.
- Added `BLOODBOUND_TASK_BOARD.md` for visible Te Doen/Afgerond tracking.

## 1.0.0-rc.4 Maintenance QA And Error Alerts

- Added `/maintenance <on|off|status>` for staff-controlled launch/test lockdown.
- Added `/qa start smoke` guided smoke-test flow with pass/warn/fail verdict logging.
- Added staff notifications for severe command/server errors captured by the error tracker.
- Added bounded `.db` record stores for new error/QA records to avoid unbounded RAM/disk growth.
- Added SQLite-first storage for legacy `PropertiesFile` data when Paper/Xerial JDBC is visible, with automatic fallback to files.
- Added console and staff join status for the active storage backend.
- Let Admin/Owner/OP use full permissions inside `mitchtest_*` sandbox worlds, including AuctionHouse and QuickSell testing.
- Made legacy `PropertiesFile` saves crash-safer with `.tmp` writes and `.bak` backups.
- Documented Java 25 as production runtime, staging runtime and build target.

## 1.0.0-rc.3 Rookie HUD And Skirmish Ability Cache

- Added an automatic `ROOKIE: ACTIVE` HUD line while Rookie protection is active.
- Added an unlocked Aegis Guard shield to every Skirmish kit alongside the Sharpness I iron sword.
- Added one rotating Skirmish ability cache per minute with direct inventory claiming and automatic chest cleanup.
- Added Bloodcourt variants of Blood-Forged Edge, Echo Quiver and Storm Bind as cache rewards.
- Documented the safe all-jars-together upgrade procedure for hosted servers.
- Recorded Blood Oaths, tickets, timelines, migration tooling and other P2/P3 systems as post-launch scope.

## 1.0.0-rc.2 Final Gameplay Hardening

- Made Aegis Guard manually activated with configurable timed 100% absorption and visible HUD state.
- Protected the Skirmish arena from building and breaking.
- Fixed Boss Shard and Corrupted Heart `item_model` assignment for the working 26.1.2 resource pack.
- Made inactive-season HUD stats fall back to overall deaths/kills and added an optional season HUD component.
- Added Recovery ability trials, practical skilltree effects, portable mastery stations and buffered skill persistence.
- Added report GUI, categorized staff audit and cross-plugin registration profiling.
- Added delayed economy/Auction House inventory synchronization for suspected client ghost stacks.

## 1.0.0-rc.1 P1 Gameplay and Polish

- Added Rookie status, restricted Starter/Recovery kits, Rookie Contracts and dynamic goals.
- Added assist rewards, protected First Blood and isolated low-stakes Skirmish PvP.
- Added multi-ability HUD lines and quiet ability interaction audio.
- Added EconomyWatch-aware persistent contract rotations and expired claim grace.
- Added named seasons, farming-animal XP, staff profiles, staff notes and reports.
- Added player, staff and technical launch documentation.

All notable BloodboundSMP changes are documented here. Versions follow Semantic Versioning.

## [Unreleased]

### Added

- P0 launch-hardening program for recovery, admin isolation, economy integrity, security and diagnostics.
- Central feature flags and persistent command-error tracking.
- Cuboid rollback and inventory snapshot recovery plugin.
- AuctionHouse audit/refund/expiry tooling and `/ahadmin` inspection commands.
- Warnings-only anti-cheat review flow and bounty HUD/offline notifications.
- Configurable per-chunk entity caps and performance task profiling.

### Changed

- Adminmode data is isolated from survival economy, inventories, skills, containers, drops and minigames.
- QuickSell values now respect ingredient/output ratios, active supply and loot availability.
- Ability toggling uses sneak + swap-hand instead of sneak + drop.
- Blood-Forged Edge and Aegis Guard now expose configurable active phases and cooldown states.

### Security

- Removed automatic anti-cheat freezing; detections now warn and require staff review.
- Auction purchases now atomically claim listings before money/item transfer.
- Added suspicious economy and repeated-player AuctionHouse transaction alerts.

## [1.0.0-rc.1] - 2026-06-24

### Added

- Complete custom BloodboundSMP plugin suite covering Lifesteal, economy, progression, events, minigames and staff tooling.
- Bloodbound branding and custom Boss Shard/Corrupted Heart resource pack.
- Offline-safe staged build and guarded deployment workflow.

### Changed

- Build output now targets Java 25 bytecode and records version/commit diagnostics.
- Release artifacts use the version from `VERSION`.

### Security

- Public launch remains blocked until every item in `P0_LAUNCH_READINESS.md` is verified or feature-flagged off.
