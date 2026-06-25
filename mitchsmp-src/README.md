# BloodboundSMP Plugin Source

Custom BloodboundSMP Paper plugins with no external Minecraft plugin dependencies.

The public server branding is BloodboundSMP. Internal plugin IDs, jars and dependency names remain `MitchSMP-*` for safe Paper loading compatibility.

## Build

Production runtime, staging runtime and build target are all Java 25. Do not deploy these plugins on Java 21; they are built for Java 25 and will not load correctly on older runtimes.

Compile safely while the server is running (staging only):

```powershell
.\mitchsmp-src\build.ps1
```

After the server has fully stopped, compile and deploy:

```powershell
.\mitchsmp-src\build.ps1 -Deploy
```

Deployment is refused while the configured Minecraft port is listening. This prevents live jar replacement and lazy classloading failures.

## Modules

- `MitchSMP-Core`: shared API, ranks, permissions, file-backed storage, utilities
- `MitchSMP-Lifesteal`: 1-20 heart lifesteal system
- `MitchSMP-CorruptedHearts`: rare recovery item for players below 10 hearts
- `MitchSMP-Permissions`: rank and permission commands
- `MitchSMP-CombatTag`: combat tagging and combat logout death
- `MitchSMP-TPA`: teleport requests blocked during combat
- `MitchSMP-Homes`: rank-based homes blocked during combat
- `MitchSMP-Bounties`: bounty values from heart count
- `MitchSMP-Economy`: internal economy with pay and leaderboards
- `MitchSMP-AuctionHouse`: UI item market using MitchSMP economy
- `MitchSMP-Cosmetics`: tags, trails, join/kill effects, emotes
- `MitchSMP-Events`: automated event starter and admin event commands
- `MitchSMP-Chat`: prefixes, colors, private messages, staff chat, mutes
- `MitchSMP-AntiCheat`: early staff alerts for reach, CPS, fly, speed, scaffold patterns
- `MitchSMP-Seasons`: season timer, leaderboards and hall of fame
- `MitchSMP-BedWars`: separated void BedWars arena with saved survival inventories
- `MitchSMP-HUD`: right-side scoreboard HUD with configurable components
- `MitchSMP-RTP`: random teleport command
- `MitchSMP-Skills`: 100-level GUI skilltree, skillpoints, item-bound endgame abilities and mechanics guide
- `MitchSMP-EndBoss`: ritual-gated endgame bossfight in a temporary resettable hell world
- `MitchSMP-Essentials`: spawn, back, staff tools, jail, shop, starter kit and admin utilities
- `MitchSMP-Artifacts`: rare special items and OP shop currency/items
- `MitchSMP-Bosses`: per-player Bloodbound bosses and guaranteed Boss Shard drops

## BloodboundSMP Test Commands

### Player Basics

- `/commands [page]`: shows the in-game command list.
- `/spawn`: teleports to spawn.
- `/back`: adminmode-only teleport to the previous teleport/death location.
- `/starterkit`: one-time starter kit.
- `/shop`: basic shop UI with tools up to iron only.
- `/rtp [radius]`: random teleport.
- `/hud`: shows active HUD parts.
- `/hud on|off|reset`: toggles or resets the right-side HUD.
- `/hud mode <seasonal|overall>`: switches K/D/KDR between season stats and overall progression stats.
- `/hud add|remove|toggle <part>`: edits HUD parts. Parts include `balance`, `kills`, `deaths`, `kdr`, `biome`, `xyz`, `hearts`, `rank`, `world`, `online`, `ping`.
- `/abilities clean`: cleans old duplicated ability lore.
- Tool abilities rollen via enchanting table, ability books en loot. De challenge verschijnt pas nadat het item zo'n MitchSMP ability enchant heeft.

### Staff Safety

- `/adminmode [on|off|override]`: switches separated staff mode. Staff/admin/OP powers only work while this is on. `override` is Owner-only for testing.
- `/adminmode override`: Owner-only test override while admin mode stays active.
- `/staffmode [on|off]`: alias for admin mode.
- `/maintenance <on|off|status>`: Admin-only launch/test lock. Normal players are removed and cannot keep playing while guided QA is active.
- `/qa start smoke`: starts the guided smoke-test checklist while maintenance is on. Use `/qa pass`, `/qa warn <note>`, `/qa fail <note>`, `/qa next`, `/qa status`, `/qa stop`, and `/qa recent`.
- `/errors recent [count]`: staff-visible bounded error tracker. Severe command/server errors are also announced to staff with throttling.
- Core logs the active storage backend at every startup. Staff also see it on join: `SQLite` when Paper/Xerial JDBC is visible, otherwise `crash-safe properties fallback`.
- `/v` or `/vanish [player]`: vanish while in admin mode.
- Outside admin mode, staff and OPs are capped to normal Legend-style player permissions.
- Admin mode has its own stored inventory. Your normal SMP inventory is restored when you switch off.
- Lifesteal, combat tag, bounties, progression distance and season stats ignore players in admin mode.
- Staffchat and anticheat alerts are only visible to real staff ranks, not VIP/MVP/Legend.

### Seasons and Hall of Fame

- `/season`: shows active season. If none is active, it tells admins to start one manually.
- `/season list`: lists current and archived seasons.
- `/season reset seasonal`: resets current seasonal kills/deaths/streak/king.
- `/season reset overall`: resets season stats and asks Progression to reset legacy/explorer stats.
- `/season start <days>`: starts a new season.
- `/season end`: ends the current season and does not auto-start a new one.
- `/season delete <number>`: removes a test season and asks the visual HOF to remove that season too.
- `/season purge`: clears all season test data and purges the visual HOF.
- `/legacy hof`: teleports to the protected void Hall of Fame.
- `/legacy snapshot [season]`: stores the current legacy leaders as a HOF snapshot.
- `/legacy delete <season>`: removes one visual HOF season.
- `/legacy purgehof`: clears all HOF snapshots and rebuilds the hall cleanly.
- `/legacy rebuild`: rebuilds the HOF world from saved snapshots.
- `/progression reset overall`: clears legacy kills/deaths/bounty claims and explorer distance.

### Economy and Auction House

- `/balance [player]`, `/pay <player> <amount>`, `/moneytop`: economy basics. Adminmode uses a separate admin balance.
- `/eco stats`: shows total money, average balance, richest balance and QuickSell multiplier.
- `/eco adminbal [player] [amount]`: shows or sets the separate adminmode balance.
- `/eco clear <player> confirm`: clears one normal player balance.
- `/eco resetall confirm`: resets all normal balances and admin balances with confirmation.
- `/econwatch active <days>`: sets the active-player window for dynamic economy calculations.
- `/event config economyScaleMax 0`: leaves event money scaling uncapped; use a positive number only when you want a hard cap.
- `/sell`: opens the QuickSell basket UI. Drag items from your inventory into the sale slots and confirm. `/quicksell` and `/qs` still work.
- `/sell all`: opens the same safe basket UI with an instruction message.
- `/sell hand`: opens the same safe basket UI with an instruction message.
- `/sell prices`: lists dynamic economy-aware sell prices.
- `/ah`: opens the Auction House UI.
- `/ah sell <price>`: lists the item in your hand.
- AH UI buttons: search via sign, sort, reset filters, back, My Listings, New Listing.
- AH slots: Default 5, VIP 10, MVP 20, Legend 40. Staff are capped at Legend-style player convenience outside admin mode.

### Minigames

- `/bw join [arena]`, `/bw leave`: BedWars lobby/game flow.
- `/bw paid <arena> <price>`: admin sets a BedWars entry fee; pot pays to winners.
- `/tntrun join [arena]`, `/tntrun leave`: TNT Run lobby/game flow.
- `/tntrun paid <arena> <price>`: admin sets a TNT Run entry fee; the winner receives the full pot.

### Endgame Boss

- `/endboss ritual`: shows the ritual requirements.
- `/endboss start`: opens the confirmation for the initiator.
- `/endboss confirm`: confirms start or join.
- `/endboss join`: joins the open ritual party.
- `/endboss status`: shows the active ritual/fight.
- `/endboss force`: admin force-start for testing.
- `/endboss end`: admin cleanup/reset.
- `/endboss config <key> <value>`: tunes `min_players`, `boss_health`, `required_hearts`, `required_boss_shards`, `required_corrupted_hearts`, `required_enchanted_apples`.

Each fight creates a fresh temporary hell world, blocks escape commands while inside, rewards Infernal Ward/hell loot on victory, and resets/deletes the world after win or wipe.

### Admin Tools

- `/fakeores [player|all|nearby|cancel] [radius] [seconds]`: opens an ore selector, puts staff into noclip and lets them right-click blocks to place exposed 2x2x2 bait veins for xray checks.
- `/jail <player> [minutes]`: places a player in an auto-generated void jail cell.
- `/jail visit <player>`: teleports staff outside the jailed player's glass cell.
- `/unjail <player>`: releases a jailed player.
- `/freeze <player>`, `/lockdown <player>`, `/release <player>`: staff intervention commands.
- `/godtools [player]`: gives high-end staff intervention tools.
- `/invsee <player>`, `/enderchest [player]`, `/tp <player|x y z>`, `/tphere <player>`, `/clearinventory [player]`: investigation/admin tools.
- `/lagclear`: removes dropped items from worlds.

### World Safety

- BedWars, Hall of Fame and Jail worlds block mob spawning.
- BedWars stores survival inventories before join and restores them after leave or after a server restart.
- BedWars paid matches deny lobby entry if the player cannot afford the entry fee.
- Jailed players cannot run commands.
- Jail and Hall of Fame block breaking/placing is blocked for everyone, including admins.
- Auction listings are stored in `plugins/MitchSMP-AuctionHouse/listings.properties`.
- New launch QA and error records are stored in bounded `.db` record stores under `plugins/MitchSMP-Core/`.
- Legacy `PropertiesFile` data uses SQLite automatically when `org.sqlite.JDBC` is visible from Paper's bundled libraries. On hosts where that driver is not exposed to plugins, it falls back to crash-safe `.tmp`/`.bak` properties files.
- Admin/Owner/OP have full testing freedom inside `mitchtest_*` sandbox worlds. This is intentionally not applied to the normal SMP, hub, minigames, jail or Hall of Fame worlds.

## Rank Benefits

- Default: 3 homes, 5 AH listings, basic player commands.
- VIP: 5 homes, 10 AH listings, VIP cosmetics.
- MVP: 8 homes, 20 AH listings, MVP cosmetics.
- Legend: 12 homes, 40 AH listings, Legend cosmetics.
- Paid ranks must never grant hearts, combat buffs, better damage, less damage or exclusive combat gear.
