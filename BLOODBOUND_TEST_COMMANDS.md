# BloodboundSMP Test Commands

Use this file for testing the BloodboundSMP testserver copy. Do not run these tests on the official/live server until approved.

## Player Menu

- `/menu` or `/m` - opens the main Bloodbound menu.
- `/commands [page]` - shows only commands the player is allowed to use.
- `/help [page]` or `/? [page]` - same filtered command list, replaces Bukkit help for players.

Test as Default:
1. Type `/` and confirm admin-only roots are not suggested.
2. Run `/?` and confirm it shows Bloodbound commands, not Bukkit plugin categories.
3. Open `/menu` and confirm admin-only buttons are hidden.

## Survival

- `/spawn` - teleport to spawn.
- `/rtp [radius]` or `/wild` - random teleport into the SMP world.
- `/starterkit` or `/kit` - claim starter kit.
- `/sethome [name]`, `/home [name]`, `/homes`, `/delhome <name>` - homes.
- `/tpa <player>`, `/tpaccept`, `/tpdeny` - teleport requests.
- `/hud` - configure HUD.
- `/hud mode seasonal` and `/hud mode overall` - switch HUD stat mode.

## Economy

- `/balance [player]`, `/bal`, `/money` - view money.
- `/pay <player> <amount>` - pay another player.
- `/moneytop` - economy leaderboard.
- `/shop` - configurable base shop.
- `/sell`, `/quicksell`, `/qs` - quick sell UI.
- `/ah` or `/auctionhouse` - Auction House.
- `/ah sell <price>` - list the held item.

## Progression

- `/skills` or `/skilltree` - skilltree UI.
- `/abilities` - tool-bound abilities UI.
- `/mechanics` - written mechanics guide.
- `/collection` or `/clog` - collection log.
- `/contracts` - contracts.
- `/orders` or `/resourceorders` - resource orders.
- `/login` or `/daily` - daily rewards.
- `/explorer` - explorer ranking.
- `/legacy` - legacy/Hall of Fame progress.

## PvP And Endgame

- `/hearts [player]` - Lifesteal hearts.
- `/bounties` or `/bounty [player]` - bounty info.
- `/bounty place <player> <amount>` - place a funded bounty, including on cached offline players.
- `/opshop`, `/bossshop`, `/shardshop` - Boss Shard shop, if the player has access.
- `/endboss ritual` - physical endboss ritual instructions.

### Physical Endboss Ritual Test

1. Trap one hostile mob on one block.
2. Place Nether Brick Walls north, south, east and west of the mob.
3. Place one Redstone Torch on each wall.
4. Confirm lightning/particles/sound, mob removal and Ritual Chest creation.
5. Put the required configured items in the Ritual Chest:
   - Boss Shards, CustomModelData `910001`.
   - Corrupted Hearts, CustomModelData `910002`.
   - Dragon Egg.
   - Plain Nether Star.
   - Enchanted Golden Apples.
6. Close the chest and confirm the ready message: `Bloodbound » The ritual is ready. Break the chest to confirm.`
7. Break the chest and confirm the boss arena starts.
8. Try renamed fake Nether Stars/Echo Shards; they must not count.
9. Try hopper/piston/explosion interactions; they should not move or duplicate the Ritual Chest.

## Minigames

- `/hub` or `/navigator` - server hub.
- `/bw join [arena]`, `/bw leave`, `/bw start` - BedWars.
- `/tntrun join [arena]`, `/tntrun leave`, `/tntrun start` - TNT Run.
- `/spleef join [arena]`, `/spleef leave`, `/spleef start` - Spleef.
- `/skyblock create`, `/skyblock home`, `/skyblock leave`, `/skyblock reset confirm`, `/skyblock info` - Skyblock.

Skyblock test:
- Confirm the `bloodbound_skyblock` world border is 1500x1500 around 0,0.

## Staff/Admin

Staff commands should only appear in tabcomplete for players with the right permissions.

- `/adminmode on|off|override` - staff mode.
- `/staffmode pvp <player>`, `/staffmode pvpaccept`, `/staffmode pvpdeny`, `/staffmode pvpstop` - consent-based staffmode PvP.
- `/admin` - admin UI.
- `/model <mob>` - change visible staff model while in admin mode.
- `/model off` - clear staff model.
- `/model player <name>` - currently explains why player-skin disguises need packet/client model support.
- `/v` or `/vanish` - vanish.
- `/noclip` - no-clip and entity possession.
- `/fakeores` - fake ore bait UI.
- `/invsee <player>`, `/enderchest [player]` - inspection.
- `/jail <player> [minutes]`, `/jail visit <player>`, `/unjail <player>` - jail.
- `/freeze <player>`, `/lockdown <player>`, `/release <player>` - moderation locks.
- `/tp <player|x y z>`, `/tphere <player>` - admin teleport.
- `/heal`, `/feed`, `/fly`, `/gm`, `/speed`, `/day`, `/night`, `/sun`, `/rain` - admin utility.
- `/testworld create|join|leave|reset|list` - isolated staff test worlds.
- `/smpworld load <name>` - Owner-only SMP world load; requires admin mode and two confirmation steps.
- `/smpworld active` - show the active RTP wilderness.
- `/lagclear` - cleanup dropped items.
- `/perf` - performance monitor.
- `/econwatch` - economy watch tools.
- `/rankperms`, `/cmdperms`, `/rank`, `/setrank` - rank/permission tools.

## Resource Pack

Generated pack:
- `C:\Users\Mitchel\Desktop\SMP\BloodboundSMP-resourcepack.zip`

CustomModelData:
- Boss Shard: `Material.NETHER_STAR`, `910001`, model `bloodbound:item/boss_shard`.
- Corrupted Heart: `Material.ECHO_SHARD`, `910002`, model `bloodbound:item/corrupted_heart`.

To test textures locally, run `start-resourcepack-host.bat`, restart the server, join from the same PC and accept the pack.

## Regression Tests Added 2026-06-19

1. BedWars: join/start a solo staff test and place blocks around every intact bed. Confirm the server does not freeze and each bed remains one complete two-block bed.
2. TNT Run: confirm five layers, no head collision, no hunger/damage, and that standing halfway over two blocks removes both supporting blocks.
3. Spleef: confirm four layers, 140% Snowball Clapper speed, projectile removal on impact, and no damage to the floor below the impacted layer.
4. Abilities: use `/abilities testkit`; verify 3x3 God's Drill, full-tree Ancient Timber, one lightning strike per Echo Quiver hit, trident impact explosion, and x2 Blood-Forged damage below 33% health.
5. Contracts: open `/contracts`, confirm 3-6 rank-based rolls and a live countdown. Complete only a displayed contract and verify future rotations do not receive progress.
6. Orders: open `/orders`, confirm a live countdown and automatic reroll. Change the timer with `/progression config orderDurationMinutes 5` on a test setup.
7. Offline bounty: have a known player log out, run `/bounty place <name> 100`, check the balance deduction and claim protection.
8. SMP world: in Owner admin mode run `/smpworld load test`, then the first and second confirmation commands. Confirm no old world/player data is deleted and `/rtp` uses the new wilderness.

## Runtime Hotfix Tests Added 2026-06-19

Before testing, fully stop Paper and deploy the staged jars:

```powershell
.\mitchsmp-src\build.ps1 -Deploy
```

1. Run `/menu`; the Bloodbound player menu must open without a `Material.OAK_BED` error.
2. Type `/skyblock` and press Tab; the console must not report a `PlayerCommandSendEvent.getCommands()` linkage error.
3. Run `/skyblock reset`, then `/skyblock reset confirm`; the starter chest must generate without an index error.
4. As the exact Owner rank, run `/ownerconfirm confirm` once. Then open `/staffaudit`. A second account, even if renamed, must not gain access.
5. Run `/boss status`, then in admin mode `/boss spawn <player>`. Kill the marked boss and verify a guaranteed Boss Shard drop.
6. Kill the Endboss and verify its Boss Shard reward is issued through the same shard service.
7. Open `/skills`, navigate categories/pages and buy a perk. Level a skill and verify the achievement sound plus level-up title.
8. Join each minigame and start a staff solo test. Verify the five-second themed title/sound countdown.
9. Check generated arenas with `/bw arenas`, `/tntrun arenas` and `/spleef arenas`.
10. Generate custom themed arenas with `/bw premade <name> <crimson|ember|relic>`, `/tntrun premade <name> <crimson|ember|relic>` and `/spleef premade <name> <frost|bloodice|relic>`.

## Ability, Structure And Pack Tests Added 2026-06-19

Configuration overview:

- `/serverconfig` - lists the main in-chat configuration entry points.
- `/abilities config` - shows all ability requirements, enabled states and PvP cooldowns.
- `/abilities config blood_forged_edge cooldown <seconds>`
- `/abilities config echo_quiver cooldown <seconds>`
- `/abilities config storm_bind cooldown <seconds>`
- `/abilities config aegis_guard cooldown <seconds>`
- `/event config lootStructureMinBedrockOffset <3-10>`
- `/event config lootStructureMaxBedrockOffset <3-10>`
- `/corruptedheart chance ancient_loot_structure <percent>`

Regression checklist:

1. Throw an unlocked Storm Bind trident. Its first valid impact must explode, the HUD must show `COOLDOWN`, and a later impact must work after it reaches `READY`.
2. Hit the same armored target above and below 33% of the attacker's real Lifesteal maximum health. Blood-Forged Edge must only apply x2 damage below the threshold.
3. As Default, open `/opshop`; the Boss Shard shop must open without granting admin tools.
4. Complete the four-torch Endboss ritual. It must create the Ritual Chest without a `DRAGON_BREATH` Float-data exception.
5. As Default, type `/` and verify Bukkit internals, admin roots and commands without an explicit Bloodbound permission are absent.
6. Generate a new Ancient Loot Structure and verify its base is 6-10 blocks above the world's minimum height, according to config.
7. Inspect Ancient Loot Structure chests. Corrupted Hearts must use the `ancient_loot_structure` chance and still validate as genuine custom items.
8. Start the server through `start.bat`, accept the pack, and compare a Boss Shard and Corrupted Heart with plain Nether Stars and Echo Shards. Only the custom items may use Bloodbound models.
