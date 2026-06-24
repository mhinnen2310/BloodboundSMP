# BloodboundSMP Skills and Item Abilities

## Skilltree

Use `/skills` to open the skilltree. Every category levels to 100. Each level grants 1 skillpoint. Perks are bought inside category pages.

Mining:

- Mining Speed: grants frequent Haste while mining.
- Mining Yield: ores can drop extra ore blocks.
- Deep Miner, Ore Surveyor, Vein Discipline, Mining Mastery: progression/prestige anchors for deeper mining expansion.

Farming:

- Farming Yield: crops and logs can drop extra block loot.
- Replanter: crop blocks can auto-replant after breaking.
- Harvest Flow: hoes can harvest nearby crops in one action.
- Forester, Supply Gardener, Farming Mastery: progression/prestige anchors for farming and order gameplay.

Combat:

- Combat Sustain: heals slightly after real kills.
- Bounty Focus: bounty-hunter progression identity.
- Duelist: PvP progression anchor for future tuning.
- Escape Discipline: reserved for the timed escape utility.
- Kingslayer Focus: reserved for high-heart/high-bounty target pressure.
- Combat Mastery: combat prestige.

Alchemy:

- Alchemy perks currently focus on progression identity and future potion/consumable upgrades.
- Golden apples, enchanted golden apples and XP bottles grant alchemy XP.

Enchanting:

- Enchanting Mastery: speeds item-ability challenge progress.
- Rune Sense, Table Attunement, Booksmith, Anvil Care, Enchanting Grandmaster: progression anchors for deeper enchanting systems.

Economy:

- QuickSell Efficiency: increases `/sell` payout by up to +15%.
- Order Runner: increases resource order payout by up to +15%.
- Contract Broker: increases contract payout by up to +20%.

## Item Abilities

Use `/abilities` to inspect the held item. Use `/abilities toggle`, the GUI toggle, or sneak + drop to enable or disable the held item's ability.

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
- Aegis Guard: passive shield ability. Progresses from attacks blocked, absorbs configured damage, shows shield health, and recharges after its cooldown.

Default PvP cooldowns:

- Blood-Forged Edge: 2 seconds.
- Echo Quiver: 10 seconds.
- Storm Bind: 15 seconds.
- Aegis Guard recharge: 1800 seconds.

Note on middle mouse:

Paper does not reliably expose normal survival middle-mouse/pick-block as a standalone server-side click. The reliable no-command shortcut is sneak + drop; `/abilities toggle` and the `/abilities` GUI remain available.
