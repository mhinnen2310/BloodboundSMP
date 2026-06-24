# Changelog

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
