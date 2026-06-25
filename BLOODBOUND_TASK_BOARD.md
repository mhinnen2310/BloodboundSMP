# BloodboundSMP Task Board

This file records active work in a simple visible format. New requests should be added under `Te doen`; finished items move to `Afgerond`.

## Te Doen

- Manual in-game validation of the Bloodbound Archfiend EndBoss visual with the rebuilt resourcepack enabled.
- Manually replace the hosted server's updater jar with `v1.0.8` once so future `/updates` commands can self-stage, self-apply and clean old versioned jars on restart.
- Manual in-game validation of the EndBoss physical ritual with real Boss Shards and Corrupted Hearts.
- Manual in-game validation that sandbox AuctionHouse listings are invisible from the SMP AuctionHouse.
- Manual in-game validation that sandbox QuickSell uses sandbox balance and does not affect EconomyWatch.
- Manual in-game validation of EndBoss spectator mode, four wave timing, arena fall safety and Hall of Fame victory section.
- Run the adminmode lag isolation test matrix: survival sand, adminmode sand, adminmode no vanish/model, adminmode with vanish/model, limiter off, HUD cached.
- Run launch smoke QA after the maintenance/adminmode fixes and record the final READY verdict.
- Verify the Ritual Chest accepts the live Corrupted Heart item stack requirement.
- Verify performance alerts stay quiet during normal solo mining/building.
- Make a pre-launch host backup of `plugins/`, worlds, configs, and plugin data before `/maintenance off`.

## Bezig

- Validate UpdateOrchestrator 1.0.8 on the hosted server after manually uploading the fixed updater jar once.
- Validate launch hotpath performance patch in-game: fast block break, inventory clicks, QuickSell, AH buy/list/cancel, contracts, adminmode block break/place.
- Validate adminmode/HUD performance fixes and EndBoss party spectator flow in-game.

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
- Added HUD stat read caching and sidebar change detection while preserving one-second ability cooldown updates.
- Reduced staff model/disguise tick overhead and stopped repeated per-viewer hide/show calls.
- Added FallingBlock bypass for the Performance entity-per-chunk limiter.
- Added EndBoss spectator respawn for fallen party members while teammates survive.
- Added EndBoss victory Hall of Fame group sections with clear time.
- Added EndBoss red aura/glow, four bounded mob waves and arena fall safety.
- Fixed GitHub UpdateOrchestrator release asset parsing so manifest detection works with GitHub API field order.
- Added `MitchSMP-CustomMobs` for Bloodbound Archfiend custom boss visuals.
- Converted the Archfiend Blockbench model into the Bloodbound resourcepack as `bloodbound:archfiend`.
- Added crisp-upscaled 512x512 Archfiend body/glow/wings/weapon textures and source model documentation.
- Added debounced storage writes for high-frequency PropertiesFile users.
- Batched economy balances, AuctionHouse saves, skill cooldown/progression saves and progression contract/stat saves.
- Removed normal player block break/place audit writes from the gameplay hot path while keeping adminmode tracing.
- Reduced performance sampling overhead from every second to every 5 seconds with corrected TPS math.
- Hardened UpdateOrchestrator release asset parsing and added direct manifest/jar download fallback.
- Added hosted-server pending update application during shutdown/startup for panels without an external update script.
- Added updater cleanup for old versioned `MitchSMP-*.jar` files before applying staged or rollback jars.

## Niet Gedaan / Onzeker

- RC1 tag was not recreated because the exact RC1 commit is not known with certainty.
- Runtime folders are kept on disk because this workspace is also the local server. They remain ignored by git.
- Local clean boot is blocked before plugin load by Paperclip `AccessDeniedException` on `_clean-server-test/cache/mojang_26.1.2.jar`; no plugin startup stacktrace was reached in that test.
- This patch reduces main hotpath write pressure, but real 1000+ player readiness still requires a hosted load test/profiler run.
- Because hosted servers may not run the external offline update script, manually upload `MitchSMP-UpdateOrchestrator-1.0.8.jar` once, then cancel any older pending update and stage `v1.0.8`.
