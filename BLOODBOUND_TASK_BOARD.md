# BloodboundSMP Task Board

This file records active work in a simple visible format. New requests should be added under `Te doen`; finished items move to `Afgerond`.

## Te Doen

- Manual in-game validation of the EndBoss physical ritual with real Boss Shards and Corrupted Hearts.
- Manual in-game validation that sandbox AuctionHouse listings are invisible from the SMP AuctionHouse.
- Manual in-game validation that sandbox QuickSell uses sandbox balance and does not affect EconomyWatch.
- Run launch smoke QA after the maintenance/adminmode fixes and record the final READY verdict.
- Verify the Ritual Chest accepts the live Corrupted Heart item stack requirement.
- Verify performance alerts stay quiet during normal solo mining/building.
- Make a pre-launch host backup of `plugins/`, worlds, configs, and plugin data before `/maintenance off`.

## Bezig

- Add restart-safe GitHub UpdateOrchestrator and prepare GitHub release assets.

## Afgerond

- Added visible startup/storage backend reporting for SQLite vs fallback properties.
- Added SQLite-first storage with fallback for legacy `PropertiesFile` users.
- Added maintenance mode and guided smoke QA.
- Added staff alerts for captured severe command/server errors.
- Fixed EndBoss ritual item detection to accept Boss Shards and Corrupted Hearts by item model and legacy CustomModelData.
- Changed the EndBoss ritual sacrifice to use real lightning and lethal mob removal before the Ritual Chest appears.
- Added better Ritual Chest missing-item feedback when the chest is broken incomplete.
- Separated sandbox AuctionHouse data from SMP AuctionHouse data.
- Separated sandbox QuickSell wallet behavior from the SMP wallet.
- Made `mitchtest_*` sandbox worlds full-permission test spaces for Admin/Owner/OP.
- Reworked the repository README into a professional GitHub front page.
- Fixed maintenance/QA access so staff/op can run `/qa start smoke` while maintenance is active.
- Added automatic `/qa start smoke` preflight checks for plugin set, key services, and SQLite backend.
- Fixed Ritual Chest Corrupted Heart recognition by using the registered CorruptedHeartService before model fallbacks.
- Allowed adminmode item drops inside `mitchtest_*` test worlds while keeping SMP/admin item cleanup strict elsewhere.
- Hardened economy amounts against `NaN`, `Infinity`, `1e309`, negative values, and over-limit transactions.
- Hardened `PropertiesFile.getDouble()` so corrupt non-finite stored values fall back safely.
- Added AuctionHouse buyer inventory precheck before money withdrawal.
- Kept AuctionHouse queued refunds in storage when player inventory is full instead of dropping them on the ground.
- Reduced noisy performance alerts by requiring sustained lag samples and raising launch alert cooldown/default threshold.
- Rebuilt and staged all 33 Bloodbound plugin jars as `1.0.0-rc.5`.
- Promoted BloodboundSMP to `1.0.0` first public release.
- Reworked the GitHub README with server-owner quick start, system overview, command tables, storage, feature flags and production safety notes.
- Added local `release-assets/1.0.0` with plugin jars, release manifest, checksums and release notes for GitHub upload.
- Added `MitchSMP-UpdateOrchestrator` for GitHub release check/stage/verify/approve/history/rollback scheduling.
- Added offline `scripts/apply-pending-update.ps1` helper for applying staged updates only while Paper is stopped.
- Verified command permission filtering covers all 184 declared commands and aliases.

## Niet Gedaan / Onzeker

- RC1 tag was not recreated because the exact RC1 commit is not known with certainty.
- Runtime folders are kept on disk because this workspace is also the local server. They remain ignored by git.
- Local clean boot is blocked before plugin load by Paperclip `AccessDeniedException` on `_clean-server-test/cache/mojang_26.1.2.jar`; no plugin startup stacktrace was reached in that test.
