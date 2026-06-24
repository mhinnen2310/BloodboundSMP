# BloodboundSMP Skills and Item Abilities

## Skilltree

Use `/skills` to open the skilltree. Every category levels to 100. Each level grants 1 skillpoint. Perks are bought inside category pages.

Mining:

- Mining Speed: grants frequent Haste while mining.
- Mining Yield: ores can drop extra ore blocks.
- Deep Miner: bonus Mining XP at Y 0 and below.
- Ore Surveyor: additional bonus-ore chance.
- Vein Discipline: longer Haste after mining.
- Mining Mastery: improves both Haste and bonus ore yield.

Farming:

- Farming Yield: crops and logs can drop extra block loot.
- Replanter: crop blocks can auto-replant after breaking.
- Harvest Flow: hoes can harvest nearby crops in one action.
- Forester: more Farming XP and bonus-log chance.
- Supply Gardener: more XP from crop harvesting.
- Farming Mastery: further crop/log yield and animal-farming progression.

Combat:

- Combat Sustain: heals slightly after real kills.
- Bounty Focus: extra Combat XP when fighting bounty targets, rate limited.
- Duelist: up to +5% direct PvP damage.
- Escape Discipline: prevents a critical hit and teleports up to 50 blocks away, with a 60-minute cooldown.
- Kingslayer Focus: highlights nearby 20-heart or bounty targets up to 128 blocks and warns them.
- Combat Mastery: combat prestige.

Alchemy:

- Consumed potions, golden apples and honey grant Alchemy XP.
- Brewing Focus shortens an active brewing timer by up to 50% once per 30 seconds.
- Infernal Resolve maintains Fire Resistance in the Nether.
- Alchemy Grandmaster extends consumed potion durations by up to 25%.

Enchanting:

- Enchanting Mastery: speeds item-ability challenge progress.
- Rune Sense and Table Attunement improve Bloodbound ability-enchant odds.
- Anvil Care accelerates item-bound ability challenges.
- Max Booksmith unlocks `/skills anvil`; max Enchanting Grandmaster unlocks `/skills enchant`.

Economy:

- QuickSell Efficiency: increases `/sell` payout by up to +15%.
- Order Runner: increases resource order payout by up to +15%.
- Contract Broker: increases contract payout by up to +20%.

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
