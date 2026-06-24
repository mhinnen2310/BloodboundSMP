# BloodboundSMP Development Log

## 2026-06-24 - P0 Launch Hardening

### Done

- Added Git/SemVer release foundations, Java 25 bytecode builds, guarded deploy metadata and startup diagnostics.
- Added central feature flags, command error persistence, Recovery rollback/inventory snapshots and stronger adminmode isolation.
- Rebalanced EconomyWatch/QuickSell crafting chains and added large/suspicious transaction alerts.
- Secured AuctionHouse listing lifecycle with validation, atomic purchase claims, refunds, expiry and admin audit commands.
- Removed automatic anti-cheat freezing; added warnings-only 24-hour review tooling.
- Added bounty source/increase/offline/login notifications and an active-bounty HUD warning.
- Added configurable entity-per-chunk enforcement and own-task performance profiling.
- Reworked ability toggling, Blood-Forged Edge, Aegis Guard, Harvest Lord and model cleanup.
- Full build passed for 31 Java 25-targeted plugins; all 169 commands/aliases are permission-filtered.

### Not Done

- A clean Java 25 runtime boot is not proven: only Java 26 is installed locally.
- The isolated clean boot could not run inside the filesystem sandbox because Paperclip could not access its copied cache; elevated execution was unavailable.
- Auction, rollback, bounty, abilities and adminmode still require the documented multiplayer/in-client rehearsal before launch.
- Cross-plugin listener/task profiling and migration of every synchronous properties save are not complete.
- The reported generic item-stacking bug needs a reproducible item/action sequence before it can be verified fixed.

### Unclear

- "Item stacking bug" does not identify which item types or inventory action trigger it; no speculative inventory rewrite was made.
- Paper 26.1.2 was previously observed on Java 26; host compatibility with the requested Java 25 runtime must be confirmed by a clean boot.

## 2026-06-18 - Post-Rebrand Gameplay/Systems Pass

### Done

- Started this persistent log at the user's request.
- Confirmed that work continues in `C:\Users\Mitchel\Desktop\BloodboundSMP-Test`.
- Began code inventory for admin mode, minigames, skills/abilities, tab completion and remaining Dutch strings.

### Not Done Yet

- No gameplay fixes have been shipped in this pass yet.
- No build/start verification has been run for this pass yet.

### Unclear / Needs Care

- The requested scope is very large. I will prioritize security/gameplay bugs first, then minigame polish, then larger new systems like Skyblock/hub expansion.
- "Universal minigame lobbies" can mean shared code architecture or consistent player-facing design. I will start with consistent protected Bloodbound-themed lobby layouts and command behavior.
- "Moving MOTD symbols" in a vanilla/Paper server MOTD can only be pseudo-animated by changing MOTD over time or using cycling text through a plugin/ping listener; server.properties itself is static.

### Batch 1 - Stability and Safety Fixes

Done:
- Fixed repeated Bloodbound ability lore by recognizing both old MitchSMP and new BloodboundSMP ability lines.
- Added `/abilities grant <ability>` and `/abilities complete` for staff testing custom tool/weapon abilities.
- Made `/skills` and `/abilities` argument tab completion hide admin-only options from non-admins.
- Added root command tab filtering for many player/staff commands based on rank permissions.
- Changed Spleef to use a custom Snowball Clapper tool with unlimited 125% speed snowballs.
- Changed TNT Run floor removal to sample multiple blocks under the player instead of one exact block.
- Blocked normal adminmode users from placing/using destructive items and from editing real containers outside test worlds.
- Made owner adminmode override skip admin rollback and exit cleanly when toggled off.
- Made `/balance` and key `/eco` admin balance commands resolve offline/cached/known players.
- Added central English replacements for several remaining Dutch admin/teleport/gamemode messages.

Not done yet:
- No hub or Skyblock plugin/system has been added in this batch.
- No new minigame maps/themes have been generated yet.
- Boss shard gameplay has not yet been split into a separate plugin.
- Server icon/logo has not yet been replaced.
- No live server files were changed.

Unclear / Needs care:
- Full command visibility depends on Bukkit's PlayerCommandSendEvent behavior at runtime; build confirms it compiles, startup/runtime still needs console verification.
- Adminmode container blocking intentionally targets placed-world containers only, so custom GUIs should remain usable.

### Batch 2 - Hub, Skyblock, Branding and Startup Verification

Done:
- Added a protected Bloodbound-themed `/hub` world with a navigator GUI for Wilderness, BedWars, TNT Run, Spleef and Skyblock.
- Added a first `/skyblock` plugin with one isolated island per player, separate Skyblock inventory storage, protected island radius, `/skyblock create/home/leave/reset/info`, and command restrictions while in Skyblock.
- Added default permissions for Hub and Skyblock to Core and added admin permissions for build/reset actions.
- Added Hub/Skyblock jars to the build pipeline and rebuilt all plugins into the testserver `plugins` folder.
- Added a dynamic BloodboundSMP MOTD ping listener with rotating symbols and game mode text.
- Updated `server.properties` fallback MOTD in the testserver.
- Replaced the testserver `server-icon.png` with a sharper Bloodbound dark-fantasy emblem and kept `server-icon.before-bloodbound.png` as rollback.
- Fixed the Hub builder to place only valid block materials in the world.
- Translated the visible `/mechanics` guide pages around economy, orders, contracts, seasons, HUD, skills, abilities, events, BedWars and fair-play into English.
- Added more central English replacements for common remaining Dutch command responses such as rank, hearts, admin balance, time and shop reset messages.
- Startup-verified the testserver: Paper reached `Done` and all MitchSMP/Bloodbound plugin jars loaded.
- Fixed the generated server icon to the exact Minecraft-required `64x64` dimensions after Paper rejected a larger generated image.
- Re-ran startup verification after the icon fix: Paper reached `Done`, there were no plugin-load errors, no server-icon errors and no testserver Java process was left running.

Not done yet:
- I did not touch the live/original `C:\Users\Mitchel\Desktop\SMP` server.
- I did not fully redesign all minigame maps into multiple themed variants yet.
- I did not split boss shard gameplay into a standalone boss-mechanics plugin yet.
- I did not fully audit every single command response in-game with a player account; this batch focused on code/build/startup and high-visibility strings.
- I did not add the final adminmode visual armor/nameplate polish in this batch.

Unclear / Needs care:
- An earlier piped `/stop` startup method triggered a Paper-side console exception, so final verification was done by waiting for `Done` and stopping the test process afterwards.
- Some old strings such as `MitchSMP Enchant:` remain intentionally in source so old items can be cleaned/migrated; they should not be written as new visible lore.
- The new Hub and Skyblock are functional first versions; map aesthetics can be expanded further without changing player data.

## 2026-06-19 - Menu, Command Visibility, Textures and Endboss Ritual

### Done

- Worked only in the Bloodbound testserver copy at `C:\Users\Mitchel\Desktop\BloodboundSMP-Test`.
- Fixed the tablist rank/prefix leak where staff/op players could visually appear as `Legend`; permissions can still downgrade outside adminmode, but the visible tab prefix now uses the real rank.
- Added `/menu` and `/m` to MitchSMP-Essentials with a sectioned Bloodbound player menu for survival, economy, progression, minigames and endgame UIs.
- Added `/help` and `/?` handling through the filtered Bloodbound command list, preventing normal players from seeing Bukkit plugin command categories through help.
- Tightened command visibility for namespaced Bukkit/Minecraft command roots when they are not explicitly mapped as safe.
- Changed `/commands` output to English and filtered it per sender permissions.
- Added `BLOODBOUND_TEST_COMMANDS.md` with tester instructions and grouped command lists.
- Added a 1500x1500 worldborder to the `bloodbound_skyblock` world.
- Created a Bloodbound resourcepack folder and zip:
  - `C:\Users\Mitchel\Desktop\BloodboundSMP-Test\resourcepacks\BloodboundSMP`
  - `C:\Users\Mitchel\Desktop\BloodboundSMP-Test\BloodboundSMP-resourcepack.zip`
- Added custom item models/textures:
  - Boss Shard: Nether Star, CustomModelData `910001`, model `bloodbound:item/boss_shard`.
  - Corrupted Heart: Echo Shard, CustomModelData `910002`, model `bloodbound:item/corrupted_heart`.
- Updated Boss Shard and Corrupted Heart item lore/name repair so recognized items get the current Bloodbound text and CustomModelData.
- Added a physical Endboss ritual path:
  - hostile mob enclosed by four Nether Brick Walls,
  - Redstone Torch on each wall,
  - lightning/particles/fire,
  - mob removal,
  - temporary Ritual Chest,
  - CustomModelData-based requirement validation,
  - no fake renamed Boss Shards/Corrupted Hearts,
  - hopper/piston/explosion protection,
  - 15-minute Ritual Chest expiry,
  - boss fight starts when the completed chest is broken.
- Restricted `/endboss start` to admins/testers; regular players are directed to the physical ritual.
- Added `/model`, `/disguise`, `/morph`, `/modelchanger` for staff in adminmode with mob-based visible model disguises.
- Staff model disguises create a tracked, harmless entity through Paper reflection where available: invulnerable, silent, no gravity, no AI, not persistent.
- Full build passed after changes.
- Startup verification passed:
  - Log: `C:\Users\Mitchel\Desktop\BloodboundSMP-Test\bloodbound-startup-check-20260619-014511.out.log`
  - Paper reached `Done (5.701s)`.
  - No plugin load errors, invalid plugin errors or stack traces were found in the checked startup output.

### Not Done Yet

- I did not modify the live/original `C:\Users\Mitchel\Desktop\SMP` server.
- I did not host/configure the resourcepack URL in `server.properties`; the zip is generated and ready to host or load manually.
- I did not implement true player-skin disguises, because that needs packet/client model support or an external dependency. Mob disguises are implemented.
- I did not implement the in-game Endboss drops editor GUI in this pass; drops still use the existing EndBoss reward logic.
- I did not complete a full manual in-client walkthrough for every menu button; build and startup were verified.

### Unclear / Needs Care

- The physical ritual assumes Redstone Torch and Redstone Wall Torch block types from Paper. Both are supported in the local compile stubs now, but the exact wall-torch placement should still be tested in-game.
- The Ritual Chest map is runtime memory only. If the server restarts while a Ritual Chest exists, that chest will not stay registered as a ritual chest after restart.
- The resourcepack uses `pack_format` 64 for the current Bloodbound test environment. If a client rejects the pack, adjust only `pack.mcmeta` after confirming the exact client version.
- Two older Java server processes were already running before/after this check; I only stopped the short-lived startup-check process I created.

## 2026-06-19 - Resourcepack 26.1.2, Ritual, Abilities, Hub/Admin Follow-up

### Done

- Worked only in the active Bloodbound testserver copy at `C:\Users\Mitchel\Desktop\BloodboundSMP-Test - kopie (2)`.
- Did not modify the live/original `C:\Users\Mitchel\Desktop\SMP` server.
- Updated the Bloodbound resourcepack for newer client item model handling:
  - added `assets/minecraft/items/nether_star.json`;
  - added `assets/minecraft/items/echo_shard.json`;
  - kept CustomModelData `910001` for Boss Shard and `910002` for Corrupted Heart;
  - rebuilt `BloodboundSMP-resourcepack.zip`;
  - verified SHA1 `4c6c39700410f28a16a12234becc193ba762338c`.
- Added `BLOODBOUND_RESOURCEPACK_SETUP.md` explaining why `server.properties` still needs a hosted HTTP(S) URL and how to test/host the pack safely.
- Made the physical Endboss ritual detection more forgiving around the final Redstone Torch:
  - scans nearby possible ritual centers;
  - validates four Nether Brick Walls and four unique Redstone Torches;
  - searches for the enclosed hostile mob at the ritual center;
  - forces a Ritual Chest at/above the center if the spot is not cleanly reported as air.
- Added the Endboss drops GUI editor:
  - `/endboss drops` opens a 54-slot editor for admins;
  - slots 1-45 store custom drops;
  - closing the GUI saves drops;
  - if configured drops exist, the Endboss uses them instead of fallback drops.
- Improved `/model` stability:
  - active disguises are tick-synced to the staff player's location;
  - staff players are hidden from new joiners while their model disguise is active;
  - stale model entities are cleaned up.
- Reworked hub behavior:
  - `/hub` now gives a compass navigator instead of auto-opening the GUI;
  - hub inventory is separated from SMP inventory while inside the hub;
  - SMP inventory is restored before leaving through hub navigation/commands;
  - hub build protection uses `/protect build <player> <true|false>` and Owner-only direct control;
  - hub layout version was bumped with a larger decorated Bloodbound-themed hub shell, buildings/plants and block-art.
- Added `/abilities testkit` for staff/admin testing:
  - gives unlocked test items for God's Drill, Ancient Timber, Earthshaper, Harvest Lord, Blood-Forged Edge, Echo Quiver, Storm Bind and Aegis Guard.
- Made `/abilities toggle` actually toggle the held item ability.
- Removed the unsafe shift-right-click ability toggle path.
- Updated ability behavior:
  - Aegis Guard challenge is now based on attacks blocked while using the shield;
  - Blood-Forged Edge now multiplies outgoing damage by x1.5 while the attacker is below 33% health;
  - God's Drill tooltip now describes 3x3 oriented mining and command/GUI toggle.
- Added `BLOODBOUND_SKILLS_AND_ABILITIES.md` with player/staff-facing explanations for skills and item abilities.
- Improved several skilltree mechanics and tooltips:
  - farming bonus yield is stronger;
  - Replanter can replant crop blocks;
  - Harvest Flow can clear nearby crops with hoes;
  - economy UI now focuses on the working economy perks: QuickSell Efficiency, Order Runner and Contract Broker.
- Connected economy skills to actual rewards:
  - Order Runner increases resource order payout;
  - Contract Broker increases contract payout;
  - QuickSell Efficiency already increases `/sell` payout.
- Changed adminmode economy to a shared admin wallet (`admin.shared`) so staff in adminmode use the same test/admin economy account.
- Removed the unnecessary "view own inventory" button from Admin UI.
- Added fake ore cleanup command support through `/fakeores delete`.
- Translated several newly touched Skills, Artifacts and Endboss messages to English.
- Rebuilt all plugins successfully after the final code changes.

### Not Done Yet

- I did not start a new testserver process after the final build because Java/Paper processes were already running. The new jars require a server restart to load.
- I did not configure `resource-pack=` in `server.properties`, because Minecraft needs a direct hosted URL. The zip and SHA1 are ready; hosting is documented in `BLOODBOUND_RESOURCEPACK_SETUP.md`.
- I did not complete a full in-client walkthrough of the Endboss ritual, `/model` visibility, Hub inventory restore, or every GUI button after this build.
- I did not fully translate every remaining Essentials/shop/jail/admin response to English; this pass translated the newly touched and high-priority parts.
- I did not implement true shift-middle-click ability toggling because Paper does not reliably expose normal survival middle mouse/pick-block as a standalone server-side click.
- I did not implement the full deeper skilltree roadmap for brewing speed, longer potions, booksmith application, table attunement and grandmaster no-lapis/no-XP enchanting in this batch.

### Unclear / Needs Care

- The Endboss ritual had an old `Â»` encoding artifact in one message that did not match cleanly through patching. It is cosmetic, but should be cleaned in a dedicated encoding-safe pass.
- The resourcepack should be tested on the actual 26.1.2 client after hosting. If the client still rejects it, the next thing to adjust is only the pack metadata/item definition format, not gameplay code.
- `/model` can hide the staff player and show a synced entity disguise, but true player-skin/model replacement for all clients still needs packet/client-side support or an external dependency.
- Middle mouse ability toggling is probably not possible with a pure Paper server plugin for normal survival play. The reliable routes are `/abilities toggle` and the `/abilities` GUI.

## 2026-06-19 - Crash Recovery Continuation In Unified SMP Server

### Done

- Worked only inside `C:\Users\Mitchel\Desktop\SMP`; no Windows, driver, personal, or other server files were edited.
- Confirmed the pre-merge backup remains at `C:\Users\Mitchel\Desktop\SMP_BACKUPS\SMP-before-bloodbound-merge-20260619`.
- Scanned 5,259 project files and found no empty Java, YAML, JSON, properties, or documentation files after the PC crash.
- Rebuilt all 29 plugins successfully multiple times after recovery.
- Started Paper far enough to reach `Done (6.661s)` and confirmed all 29 plugins enabled.
- Found the actual BedWars watchdog freeze in `onBedPhysics -> repairUnbrokenBeds -> placeBed -> onBedPhysics` and kept the repair guard active for the complete repair operation.
- TNT Run now rebuilds to layout version 5 with five floors, larger headroom, faster floor removal, and full player-footprint support detection.
- Spleef now rebuilds to layout version 2 with four floors, 140% Snowball Clapper speed, projectile cleanup, and same-layer-only snow removal.
- Fixed ability regressions: x2 Blood-Forged Edge damage below 33%, one Echo Quiver lightning impact, Storm Bind impact explosion, original block types for Ancient Timber/Harvest Lord, a bounded tree crown, and sneak + drop ability toggling.
- Fixed `/menu` permission routing and public command filtering.
- Improved Endboss ritual mob detection and delayed Ritual Chest placement.
- Added Skyblock respawn-to-island, guarded `/sb leave`, and a proper L-shaped starter island with renewable starter resources.
- Separated normal `/eco give|take|set` player balances from the shared admin wallet.
- Expanded contracts to exactly 100 definitions: 20 tracked categories with five tiers each.
- Added live contract/order GUI countdown refreshes.
- Added configurable order rotation through `orderDurationMinutes`, default 360 minutes.
- Resource orders now combine random selection with EconomyWatch stock, scarcity, and dynamic quicksell values.
- Cached contract rotations and buffered movement progress, replacing per-movement disk writes with five-second flushes.
- Added funded offline-capable bounties through `/bounty place <player> <amount>` while retaining pair/victim/day anti-farm limits.
- Added Owner-only `/smpworld load <name>` with admin-mode requirement and two ordered confirmations. It never deletes existing worlds or player data.
- `/rtp` and the hub Wilderness route now read the active SMP wilderness configured by `/smpworld`.
- Rebuilt the resource pack with Minecraft 26.1.2 pack format 84, modern item definitions, forward-slash zip paths, and matching server SHA1 `51e99d71d1f708db0feb1338334fde9bf1b4964c`.
- Added a dependency-free local resourcepack host script and updated setup/test documentation.

### Not Done Yet

- Did not modify or inspect important files outside the SMP workspace.
- Did not perform a multiplayer in-client BedWars, TNT Run, Spleef, bounty, order, or contract walkthrough. Code build and startup checks are complete; gameplay checks are listed in `BLOODBOUND_TEST_COMMANDS.md`.
- Did not run a second Paper startup after the final progression/bounty/world-loader edits because new process execution was blocked by the Codex usage limit; the final all-plugin compile is green.
- Did not complete a live HTTP resourcepack download test because Codex process execution was rejected by the current usage limit. Script syntax, zip structure, pack format, and SHA1 were verified.
- Did not set a public resourcepack URL. `127.0.0.1` is local-only and external players need a stable direct HTTPS URL.
- Did not finish every previously requested English translation, full hub architectural rebuild, true packet-level player disguise, all skilltree roadmap perks, or the larger per-player boss/event split in this continuation.
- Did not claim the server is production-ready; multiplayer exploit and load tests are still required.

### Unclear / Needs Care

- Paper is currently launched by Java 26.0.1 although the original target mentioned Java 21. Plugin compilation succeeds, but production should standardize on the Paper-supported Java version before launch.
- Paper 26.1.2 threw an internal NPE when `/stop` was piped into console with no command world. The test instance had no players and its exact Java PIDs were stopped afterward; no Java processes remained.
- The BedWars infinite physics loop is fixed at its logged source, but a real join/start/bed-build regression test is still needed.
- The resourcepack now matches locally detected client resource format `84.0`; acceptance still needs verification in the actual 26.1.2 client after the local host is started.
- Pure Paper does not expose normal middle-mouse pick-block reliably as a survival interaction. Sneak + drop is the implemented no-command shortcut.

## 2026-06-19 - Runtime Compatibility, Owner Audit and Boss Split

### Done

- Worked only inside `C:\Users\Mitchel\Desktop\SMP`.
- Fixed `/menu` on Paper 26.1.2 by replacing the invalid `Material.OAK_BED` reference with `Material.RED_BED`.
- Fixed command-list filtering against the runtime Paper signature by using `Collection<String>` for `PlayerCommandSendEvent.getCommands()`.
- Fixed Skyblock reset starter chest generation by moving the invalid slot 31 entry into the 27-slot chest range.
- Added the standalone `MitchSMP-Bosses` module with per-player spawn rolls, custom attacks, sunlight protection, guaranteed Boss Shards, configurable range/rates and admin test controls.
- Removed personal boss scheduling/drop ownership from Events. `/event start boss` now delegates to the Bosses module.
- Added `BossShardService` to Core and connected Artifacts, Bosses and Endboss to one validated shard implementation.
- Replaced the hardcoded staffaudit username with `/ownerconfirm confirm`: exact Owner rank, one-time UUID binding, immutable after confirmation.
- Removed the remaining username exception from AntiCheat alerts. Alert access now follows staff rank permissions.
- Confirmed ordinary chat is not written to staffaudit. Reduced inventory-click logging to adminmode activity only; command audit remains active.
- Added Skilltree navigation sounds and achievement sound/title feedback for skill level-ups.
- Added five-second themed title/sound countdowns to BedWars, TNT Run and Spleef.
- Added persistent minigame themes and automatic arena variants:
  - BedWars: crimson, ember, relic;
  - TNT Run: crimson, ember, relic;
  - Spleef: frost, bloodice, relic.
- Compiled all 30 plugins successfully after the changes.
- Made `build.ps1` staging-only by default. `-Deploy` now refuses to overwrite jars while the Minecraft port is listening.
- Verified the deploy guard refused deployment while Paper was active on port 25565.

### Not Done Yet

- Did not stop or kill the active Paper process. An unsafe forced process termination could corrupt currently saved worlds.
- Did not deploy the final staged AntiCheat jar because the server is still running. Stop Paper cleanly, then run `.\mitchsmp-src\build.ps1 -Deploy`.
- Did not perform a post-restart in-client walkthrough of `/menu`, Skyblock reset, boss drops, owner confirmation or the newly generated themed arenas.
- Did not remove historical audit records containing player names; they are existing audit data, not access-control secrets.

### Unclear / Needs Care

- `latest.log` showed an Artifacts `GearType` classloading failure while the old server process was running. The final Artifacts jar contains `ArtifactsPlugin$GearType.class`; the error is consistent with the previous live jar overwrite and requires a clean restart to verify.
- Creating the missing themed arena worlds is intentionally done once after restart and may briefly use extra tick time while their block layouts are generated.
- Full multiplayer balance and exploit testing of all three map themes remains necessary before public launch.

## 2026-06-19 - Ability Cooldowns, Underground Vaults And Resource Pack 85

### Done

- Worked only inside `C:\Users\Mitchel\Desktop\SMP`; the supplied reference resource pack was read-only.
- Fixed Storm Bind losing its ability after the first test by reading the custom item from the thrown trident projectile instead of requiring the trident to remain in the player's hand.
- Added persistent, configurable cooldowns for Blood-Forged Edge, Echo Quiver, Storm Bind and Aegis Guard.
- Added right-side HUD states for held abilities: `READY`, `COOLDOWN`, `OFF` and `LOCKED`, without chat spam.
- Kept Blood-Forged Edge at x2 damage below 33% of the player's actual maximum health, so Lifesteal hearts scale the threshold correctly.
- Restored player access to `/opshop` through `mitchsmp.artifacts.use` without exposing the Artifacts admin permission.
- Fixed both Endboss `DRAGON_BREATH` particle calls for Paper 26.1.2 by supplying the required Float particle data.
- Replaced permissive unknown-command visibility with an explicit permission map. `verify-command-map.ps1` covers all 162 declared command roots and aliases.
- Added `/serverconfig` (`/confighelp`) as an admin-only overview of in-chat tuning commands.
- Moved newly generated Ancient Loot Structures to a configurable 6-10 blocks above the world's minimum height.
- Added `ancient_loot_structure` to Corrupted Heart chance configuration and integrated genuine Corrupted Hearts into structure chests.
- Updated the Bloodbound pack from exact format 84 to supported formats 85-99 after comparing the supplied 26.x reference pack.
- Added modern direct item-model definitions while retaining CustomModelData `910001` and `910002` as validation/legacy metadata.
- Rebuilt the zip with forward-slash paths, validated every JSON file, and updated `server.properties` to SHA1 `cadbbc3b0ab5d6b17512f6897c2929be2aadbecd`.
- Added automatic local resource-pack hosting to `start.bat` through `start-server.ps1`.
- Compiled all 30 plugins successfully after the changes.
- Confirmed Paper was fully stopped, backed up all 30 previous live jars to `mitchsmp-src/build/deploy-backups/20260619-214302`, and deployed the new jars.
- Validated every deployed build jar as a readable archive containing `plugin.yml` and compiled classes.
- Confirmed the local resource-pack host can bind to `127.0.0.1:8123` and Java itself starts correctly.

### Not Done Yet

- Did not complete an in-client multiplayer walkthrough of cooldown timing, damage balance, the ritual, OP Shop, structure generation or command visibility.
- Did not complete a live localhost HTTP download test because the execution environment rejected that background process test. Pack contents, paths, JSON and SHA1 were validated statically.
- Did not configure a public resource-pack host. The current `127.0.0.1` URL works only for clients on the server PC; external players require a stable direct HTTPS URL.
- Did not relocate Ancient Loot Structures that were already generated before this change.
- Could not complete the post-deploy Paper startup check inside Codex: sandboxed Java received `AccessDenied` on Paper's generated `cache/mojang_26.1.2.jar`, while the unsandboxed start request was rejected because the execution-approval usage limit was reached. The server remains stopped and should be started normally with `start.bat`.

### Unclear / Needs Care

- Final resource-pack acceptance and custom-item rendering still require testing in the actual 26.1.2 client.
- PvP cooldown defaults are conservative starting values and need multiplayer balancing before launch.
- Newly generated underground structures need an in-world test to confirm the 6-10 block bedrock offset feels fair in every dimension used for SMP generation.
- It is not yet confirmed whether Paper's cache access error exists outside the Codex sandbox. The cache file is readable, not read-only, and owned by the correct Windows account; no cache file was deleted or moved.
# 2026-06-24 - P1 Gameplay and Polish

## Done

- Implemented Rookie onboarding, assists, First Blood, Skirmish, goals, contract grace, season names, staff profiles/notes and reports.
- Updated default HUD and simultaneous ability display; reduced ability audio to a quiet pling at most.
- Built all 33 plugins successfully.

## Not Done

- No remote Git push: this repository has no configured remote.
- Full skilltree and staff-audit GUI redesign remain beyond this pass.
- Clean Paper runtime boot was attempted, but the sandbox denied Paperclip access to its copied Mojang cache before plugins loaded.

## Unclear

- Item stacking issue remains unreproduced; no speculative inventory rewrite was made.
