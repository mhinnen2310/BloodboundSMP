# BloodboundSMP Player Guide

## Getting Started

1. Run `/menu` for the main player interface.
2. Claim `/starterkit` once, then use `/rtp` to enter the wilderness.
3. Open `/goals` and `/rookie` for personal objectives and beginner contracts.
4. Use `/recoverykit` when you are a rookie or have 10 hearts or fewer. It has a 24-hour cooldown.
5. Practice through `/skirmish` without heart loss or gear drops.

Starter and Rookie reward items are account-bound: they cannot be sold through QuickSell or the Auction House.

## Lifesteal, Hearts And Death

- Players start with 10 hearts.
- A real SMP player kill awards the killer 1 heart and removes 1 heart from the victim.
- Minimum: 1 heart. Maximum: 20 hearts.
- Minigames, Skirmish, staff mode and protected utility worlds do not affect Lifesteal.
- Assists in the final 15 seconds award money and Combat XP, but never hearts.
- A player's first eligible Lifesteal kill grants the one-time First Blood milestone.
- Corrupted Hearts recover players below 10 hearts and can be found or crafted with expensive materials.

## Bounties

Use `/bounties` to view targets. High-heart and long-standing 20-heart players naturally become more valuable. Player-funded bounties can also target previously known offline players. Repeated kills between the same players are restricted to prevent bounty farming.

## Contracts And Orders

- `/rookie`: one-time beginner objectives.
- `/contracts`: rotating high-risk objectives.
- `/contracts expired`: completed contracts from the previous rotation that are still inside the claim grace period.
- `/orders`: rotating server resource demand.

Contract selection uses EconomyWatch signals plus randomized variety. This helps demand scarce resources without making rotations predictable or easy to manipulate.

## Skills And Abilities

Use `/skills` for Mining, Farming, Combat, Alchemy, Enchanting and Economy progression. Skill XP and perks are player-bound. Item abilities are separate and item-bound; their challenge and unlocked state stay on that exact tool or weapon.

Ability status appears in the HUD. If both a held ability and Aegis Guard are relevant, both appear as separate lines. Ability interaction uses only a quiet pling at most; it does not spam activation audio.

See [BLOODBOUND_SKILLS_AND_ABILITIES.md](../BLOODBOUND_SKILLS_AND_ABILITIES.md) for detailed effects.

## Events, Collections And Endgame

- `/collection` tracks relics and collectibles.
- `/legacy` shows lasting achievements.
- `/season` shows the numbered and named season.
- `/endboss ritual` explains the physical Endboss ritual.
- Boss Shards are endgame currency and are separate from ordinary Nether Stars or Echo Shards.

## Practice And Minigames

- `/skirmish`: fixed-kit PvP practice, no heart loss, no legacy stats.
- `/bw join`: BedWars.
- `/tntrun join`: TNT Run.
- `/spleef join`: snowball Spleef.
- `/skyblock`: isolated Skyblock progression.

## Known Limitations

- A suspected item-stacking issue has not been reproduced. No speculative data mutation has been added. Report the exact items, UI, clicks and before/after stack sizes with `/report` or to staff.
- The server targets Paper 26.1.2 and Java 25 runtime compatibility. Cross-version client support is not guaranteed without protocol translation software, which is intentionally not an external dependency here.
- Use `/commands` for the permission-filtered command list; inaccessible plugin commands should not be suggested to normal players.
