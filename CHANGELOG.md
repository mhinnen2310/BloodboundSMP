# Changelog

## 1.1.0 Shard Banking and Proximity Spawners

- Bumped the release to `1.1.0` so hosted servers can reliably update past the previously cached `1.0.22` build.
- Reworked `MitchSMP-Spawners` into proximity-based real mob spawners: placed spawners only run while their placer is online and nearby, while still keeping a GUI for info/upgrades.
- Added dynamic spawner support for all mob entity types, with common mobs preconfigured and every unconfigured mob falling back to global default timings, limits and upgrade costs.
- Simplified spawner item tooltips to only show mob, level and output per hour.
- Made spawner upgrade prices configurable per spawner type and per level, with global defaults in `plugins/MitchSMP-Spawners/spawners.properties`.
- Added physical Shard ATMs and Shard Vaults through `/shardatm` and `/shardvault`.
- Boss Shards can no longer be stored in normal containers, hoppers, barrels, shulkers or ender chests; players must use a Shard ATM, Shard Vault or carry them.
- Added configurable Shard Vault detection radius so nearby enemies can receive a one-time warning when they approach a hidden vault.
- Ensured `/skillsadmin editor` is included in the `1.1.0` build and command usage so admins can edit skill node layout/icon data from the GUI.

## 1.0.22 Spawners, OP Shop Editor and World Management

- Added `MitchSMP-Spawners`, an inventory-based AFK spawner system. Placed custom spawners no longer spawn mobs; right-click opens a GUI with stored output and upgrades.
- Added configurable spawner types in `plugins/MitchSMP-Spawners/spawners.properties`, including output material, interval, capacity, max level and per-level upgrade costs.
- Added `/spawner give <player> <type> [level]`, `/spawner types` and `/spawner reload` for staff-managed shop distribution.
- Made `/plugins`, `/pl`, Bukkit plugin aliases and version commands hidden/blocked from non-staff command visibility.
- Added `/opshopadmin` so staff can add/remove OP shop tabs, place the held item into a specific slot, set Boss Shard costs and reload the shop config.
- Converted `/opshop` to a tabbed, config-backed Boss Shard shop while preserving the existing Pro, Elite, God and Specials defaults on first boot.
- Added `/enchant <enchantment> <level>` for admins to apply vanilla enchantments, including above-normal levels, to the item in hand. The existing portable `/enchant` UI remains available when no enchant arguments are given.
- Added `/worldmanager` for non-test managed worlds, including normal/flat/void creation, teleporting, spawn setting and double-confirm deletion.
- Added `/skillsadmin editor`, a GUI-driven skill node editor for changing node positions, icons, enabled state, required level, max level, branch section and skillpoint cost without hand-editing `skilltree.properties`.

## 1.0.21 Skilltree Icons, MOTD Controls and EndBoss Config Polish

- Added chat-editable Bloodbound MOTD controls with `/motd show`, `/motd set 1`, `/motd set 2`, `/motd frames` and `/motd reload`.
- Added `plugins/MitchSMP-Core/motd.properties` with readable help keys, animation frames and two-line MOTD text.
- Made the EndBoss arena barrier configurable and set the default invisible barrier one block tighter than before.
- Reorganized EndBoss public config into clearer grouped keys such as `requirements.*`, `boss.*` and `arena.*`, while keeping legacy flat keys as fallback.
- Added skilltree tooltip section toggles through `ui.tooltip.show_*` keys in `skilltree.properties`.
- Added Bloodbound specialization/node custom item models and generated resourcepack textures for skilltree UI icons.
- Added Owner-only `//limit max <blocks>` so BBEdit's hard maximum can be tuned without code changes.
- Kept `/endboss config` safer by allowing text values for title/color/mobs and numeric validation for numeric keys only.

## 1.0.20 Safezones and Config-Driven Blood Skills

- Added `MitchSMP-Safezones`, a dedicated staff-managed safezone plugin with cuboid claims, wand/pos selection and configurable flags for PvP, mob spawning, hunger, build protection, explosions, fall damage and hostile damage.
- Rebuilt the visible skilltree into a tighter Bloodbound identity selection: fewer filler nodes, exclusive paths, `/skills reset confirm`, and a one-time wipe of test-launch skill progress.
- Reworked `/skills` again into a true specialization tree: root node, Frontier/Warpath direction choice, six previewable specializations and confirm-before-commit branch locking.
- Added specialization pages for Prospector, Cultivator, Runesmith, Bloodreaver, Bulwark and Marksman, with preview-only locked-out pages after a player commits.
- Added `/anvil` and `/enchant` as Runesmith Arcane Workstation unlocks, gated by node progress, permissions and CombatTag.
- Expanded `/skillsadmin` with `givepoint`, `unlock`, `setbranch`, `clearbranch`, `debug` and `testgui` for controlled testing.
- Upgraded `/skills` into a Bloodbound progression hub with visual lane menus for Rookie, Bloodbound, Combat, Economy, Relic/Legacy and Event/EndBoss progression.
- Added locked/available/unlocked/maxed node states, hover requirements, connected-system lore and config-driven previous-node requirements through `node.<id>.requires`.
- Made the skilltree config-driven through `plugins/MitchSMP-Skills/skilltree.properties`; node visibility, display names, icon material, slot, lane, required level, max level and lore can be edited and reloaded with `/skills reload`.
- Reworked the Archfiend fight into four health-based stages at 100/80/60/40 percent health, with config-driven stage mobs, damage scaling, speed scaling and special attack timing.
- Prepared the build script to select the newest locally available Paper API jar, making the codebase friendlier for Paper 26.1.2 build 72 while still falling back to the currently installed local API.

## 1.0.19 Archfiend Hitbox and Persistent Model Hotfix

- Fixed `/testmob endboss` on newer Paper 26.1.2 builds by clamping test boss health to the server-supported max.
- Added the first central `ServerRuntime` API helper for safer main-thread scheduling and capped entity health writes.
- Added Owner-gated BloodboundEdit `//` building commands with persistent owner grants, wand selection, batched edits and undo.
- Kept the Bloodbound Archfiend fight controller on a normal zombie-sized hitbox instead of a huge boss hitbox.
- Strengthened hidden-controller setup for the real EndBoss and sandbox test EndBoss so the visual model is the player-facing boss.
- Hardened CustomMobs persistent visual cleanup so saved models and clones do not leave stale runtime armorstands behind.
- Added persistent visual runtime tracking for saved clones so follow/path/action behavior is managed consistently.

## 1.0.18 Staff Clones and EndBoss Test Route Fix

- Expanded CustomMobs staff tooling with clone display names, nearest visual info, rename and remove commands for Hall of Fame/NPC decoration work.
- Added CustomMobs clone support suitable for staff-created Hall of Fame displays and static NPC-like decorations.
- Fixed `/spawnmob endboss` and `/testmob endboss` so sandbox testing no longer spawns a visible Warden.
- Made sandbox test EndBoss use a hidden controller with the Bloodbound Archfiend visual attached.
- Improved `/endboss force` so staff can force-start the full bossfight directly without first opening a ritual party.

## 1.0.17 Skill Lanes, Ability Books and Archfiend Boss Controller

- Reworked the skilltree into path-based identity lanes with active Bloodbound abilities like `/scout`, `/markvein`, `/bloodrush`, `/brewboost` and `/contractboost`.
- Added a one-time skill progression wipe for the test-launch data so the redesigned lanes start clean.
- Added anvil support for Bloodbound ability books so books can transfer their matching ability onto the correct weapon/tool type.
- Fixed God's Drill classification so mixed stone variants such as granite, diorite, andesite and deepslate-like blocks are included in the 3x3 drill pass.
- Connected the Bloodbound Archfiend visual to the EndBoss controller with movement, melee attacks, special attacks and animation pulses.
- Routed damage dealt to the Archfiend visual into the real boss health so players can fight the custom visual instead of a visible Warden.
- Added CustomMobs player clone support for Hall of Fame use and EndBoss victory group displays.
- Suppressed the enchant glint override on custom model carrier items where the Paper item meta supports it.

## 1.0.16 Resourcepack Repair, Updater Checks and Skill Tuning

- Repaired legacy Boss Shard and Corrupted Heart detection so older/generated items regain their hidden marker and correct Bloodbound item model.
- Rebuilt and verified the 26.1.2 resourcepack entries for Boss Shards, Corrupted Hearts and the Archfiend item model.
- Added automatic startup release checks in the updater and staff join update-status notices.
- Allowed Bloodbound ability enchants to roll on enchanted books.
- Updated EconomyWatch so rare Bloodbound ability books/items are valued as endgame rarity, not plain gear.
- Tuned Mining skill perks so Vein Discipline improves ore-yield identity instead of duplicating Haste duration.

## 1.0.14 Archfiend HD Texture Pass

- Reworked the Archfiend texture atlas to reduce repeated neon surfaces and improve the dark fantasy boss look.
- Added atlas-aware obsidian armor, ancient gold trim, tattered wing membranes and controlled crimson cracks.
- Changed the faceplate to recessed red eye slits with glow-layer support instead of protruding bright eyes.

## 1.0.13 Archfiend Texture Path Compatibility

- Replaced numeric Archfiend texture keys with named texture keys for better Minecraft 26.x item model compatibility.
- Added item-scoped Archfiend texture aliases under `assets/bloodbound/textures/item/archfiend/`.
- Rebuilt the resourcepack so `/custommob test` can resolve the model and its textures from the same namespace.

## 1.0.12 Archfiend CustomModelData Fallback

- Added Archfiend `custom_model_data` fallback mapping to `nether_star` item definitions.
- This makes the Archfiend visual render even if the `item_model` component is not applied reliably on armorstand helmet items.
- Kept the broad stable Archfiend model from 1.0.11.

## 1.0.11 Stable Broad Archfiend Model

- Rebuilt the Archfiend as a broad, heavy vanilla cuboid model with no fragile rotated parts.
- Increased boss presence with wider torso, thick shoulders, heavy arms, large wings, horns, crown, claws, back spines, scythe and heart core.
- Updated resourcepack metadata so the client clearly shows the BloodboundSMP Archfiend pack version.

## 1.0.10 Archfiend Visual Quality Pass

- Reworked Archfiend textures toward a darker Bloodbound endgame style: obsidian black, crimson cracks, bone accents, gold highlights and hotter wing/glow details.
- Added extra model silhouette details including infernal horns, crimson horn tips, back spines, bone ribs and a crown aura.
- Rebuilt `BloodboundSMP-resourcepack.zip` with the scarier Archfiend visual.

## 1.0.9 Archfiend Resourcepack Model Fix

- Fixed the Bloodbound Archfiend item model so Minecraft no longer rejects it as an invalid model.
- Scaled model elements into vanilla item-model bounds.
- Normalized custom model rotation angles to values accepted by Minecraft item models.
- Rebuilt `BloodboundSMP-resourcepack.zip` with the corrected Archfiend visual.

## 1.0.8 Update Jar Cleanup

- Update application now removes old versioned `MitchSMP-*.jar` files for each plugin before copying the newly staged jar.
- This prevents Paper from continuing to load older plugin versions such as `1.0.3` when `1.0.8` jars are staged.
- Rollback application also clears matching newer/older versioned jars before restoring the backup jars.

## 1.0.7 Hosted Updater Apply Fix

- Added built-in pending-update application for hosts that do not run the external offline update script.
- Pending updates are now copied during plugin shutdown/restart where possible.
- If a pending update is still present at startup, the updater applies it and logs that one extra restart is required so Paper loads the new jars.
- Added protection against applying an older pending release over a newer manually uploaded updater jar.

## 1.0.6 Update Staging Manifest Fallback

- Hardened `MitchSMP-UpdateOrchestrator` asset parsing against nested GitHub release JSON.
- Added direct GitHub release download fallback for `mitchsmp-release-manifest.json` and plugin jars.
- This fixes `/updates stage latest` reporting `Release has no mitchsmp-release-manifest.json` even when the release contains the manifest asset.

## 1.0.5 Launch Performance Hotpath Patch

- Added debounced `PropertiesFile.saveSoon(...)` storage writes to reduce repeated full-store saves during high-frequency gameplay events.
- Batched economy balance writes for QuickSell, AuctionHouse and normal transactions.
- Batched AuctionHouse listing/refund/audit store saves while preserving hard flush on plugin shutdown.
- Removed normal player block break/place staff-audit writes from the hot path; adminmode block tracing remains active but debounced.
- Coarsened staff audit stats so unique block locations/items no longer create unbounded auditstat keys.
- Buffered contract progression/stat updates from block break, block place, movement and combat into the existing progression flush loop.
- Reduced PerformancePlugin world/entity sampling from every second to every 5 seconds and fixed TPS math for the longer sample window.

## 1.0.4 Bloodbound Archfiend Custom Boss Model

- Added `MitchSMP-CustomMobs`, a vanilla-resourcepack custom mob visual controller.
- Added Bloodbound Archfiend Blockbench source model metadata loading and runtime visual attachment.
- Converted the Archfiend `.bbmodel` into a `bloodbound:archfiend` item model for the Bloodbound resourcepack.
- Added 512x512 crisp-upscaled Archfiend body, glow, wings and weapon textures.
- Integrated the Archfiend visual with the EndBoss while keeping the real boss hitbox/server AI intact.
- Added staff-only `/custommob` diagnostics and test commands.

## 1.0.3 Update Manifest Parser Fix

- Fixed the GitHub release asset parser in `MitchSMP-UpdateOrchestrator`.
- Release assets are now detected regardless of GitHub API field order, including `browser_download_url` before `name`.
- This fixes `/updates stage <version>` incorrectly reporting that `mitchsmp-release-manifest.json` is missing.

## 1.0.2 Adminmode Performance And EndBoss Flow

- Cached HUD season/progression storage reads so the one-second HUD loop no longer reloads stat files every tick.
- Skipped sidebar rewrites when HUD lines are unchanged, while keeping ability cooldown HUD updates responsive.
- Reduced staff model/disguise overhead by removing repeated per-viewer hide/show calls from the model tick and lowering the model sync rate.
- Excluded FallingBlock entities from the performance entity-per-chunk limiter to protect sand/gravel physics.
- Reworked EndBoss deaths so fallen party members spectate while teammates are still alive.
- Added EndBoss victory Hall of Fame group sections with party stands and clear time.
- Added EndBoss arena fall safety for players and boss, red boss aura/glow, and four bounded mob waves.

## 1.0.1 Update Orchestrator

- Added `MitchSMP-UpdateOrchestrator` for restart-safe GitHub release checks, staging, verification, approval, backups and rollback scheduling.
- Added `/updates` commands for release status, check, stage, verify, approve, cancel, history and rollback.
- Added release manifest and SHA-256 verification support.
- Added updater permissions and command tab filtering integration.
- Kept update application restart-only; no hot-reload behavior is performed.

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
