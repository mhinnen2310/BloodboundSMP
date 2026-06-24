# Changelog

All notable BloodboundSMP changes are documented here. Versions follow Semantic Versioning.

## [Unreleased]

### Added

- P0 launch-hardening program for recovery, admin isolation, economy integrity, security and diagnostics.

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
