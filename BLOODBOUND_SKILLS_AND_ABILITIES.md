# BloodboundSMP Skills and Item Abilities

## Skilltree

Use `/skills` to open the Bloodbound progression tree. It is now arranged as visual lanes with connected nodes instead of a flat category list. Nodes show four states: locked, available, unlocked and maxed.

The six visible lanes are:

- Rookie Path: onboarding, goals, skirmish participation and early recovery flow.
- Bloodbound Path: lifesteal risk, bounties, corrupted hearts and comeback progression.
- Combat Path: CombatTag awareness, assists, skirmish and high-risk PvP identity.
- Economy Path: EconomyWatch, QuickSell, orders, contracts and AuctionHouse guidance.
- Relic & Legacy Path: collections, relics, seasonal progress and long-term prestige.
- Event & EndBoss Path: events, artifacts, boss shards, rituals and Archfiend preparation.

Skill XP still comes from real gameplay tracks:

- Mining XP: blocks and ores.
- Farming XP: crops, logs and breeding.
- Combat XP: valid PvP/PvE combat hooks.
- Alchemy XP: potions, apples and rare consumables.
- Enchanting XP: enchanting and item-ability progression.
- Economy XP: selling, orders, contracts and market actions.

Important rules:

- No skill grants free hearts.
- Item-bound tool and weapon abilities remain separate under `/abilities`.
- Some paths are exclusive, so players are pushed toward a build identity instead of maxing every branch quickly.
- Node settings live in `plugins/MitchSMP-Skills/skilltree.properties` and can be reloaded with `/skills reload`.
- `node.<id>.requires` controls previous-node requirements as a comma-separated list of node ids.

## Item Abilities

Use `/abilities` to inspect the held item. Use `/abilities toggle`, the GUI toggle, or sneak + swap-hands to enable or disable the held item's ability.

The right-side HUD automatically shows the relevant ability state while holding an ability item:

- `READY` - unlocked, enabled and available.
- `COOLDOWN Ns` - temporarily unavailable; the remaining seconds update automatically.
- `OFF` - unlocked but disabled on that item.
- `LOCKED` - the item challenge is not complete yet.

PvP ability cooldowns are server-configurable. Use `/abilities config` for the current overview or `/abilities config <ability> cooldown <seconds>` to tune one cooldown.

Abilities only appear after a rare Bloodbound enchant is applied to that item, through enchanting/loot/admin tools. The challenge belongs to the item, not the player.

Admin testing:

- `/abilities grant <ability>` applies a Bloodbound ability enchant to the matching held item.
- `/abilities complete` completes the held item's challenge.
- `/abilities testkit` gives staff a full unlocked test set.
- `/abilities clean` removes old duplicate ability lore.

Current abilities:

- God's Drill: pickaxe ability. Unlocks 3x3 oriented mining.
- Ancient Timber: axe ability. Breaks connected logs.
- Earthshaper: shovel ability. Digs a 3x3 area of shovel blocks.
- Harvest Lord: hoe ability. Harvests crops in an area.
- Blood-Forged Edge: sword ability. Deals x2 damage while the attacker is below 33% of their actual maximum health. This scales naturally with Lifesteal hearts.
- Echo Quiver: bow/crossbow ability. Calls lightning onto the entity hit by the projectile.
- Storm Bind: trident ability. Creates an impact explosion when the thrown trident hits.
- Aegis Guard: progresses only from attacks blocked with a shield. Once unlocked and enabled, manually activate it by sneak-right-clicking its item or using `/abilities activate`. It absorbs 100% of damage for its configured duration, then enters cooldown. Void damage is never absorbed.

Default PvP cooldowns:

- Blood-Forged Edge: 2 seconds.
- Echo Quiver: 10 seconds.
- Storm Bind: 15 seconds.
- Aegis Guard: 12 seconds active, then 1800 seconds cooldown by default.

Note on middle mouse:

Paper does not reliably expose normal survival middle-mouse/pick-block as a standalone server-side click. The reliable toggle shortcut is sneak + swap-hands; `/abilities toggle` and the `/abilities` GUI remain available.
