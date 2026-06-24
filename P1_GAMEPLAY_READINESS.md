# BloodboundSMP 1.0 P1 Gameplay Readiness

## Done

- Rookie detection by playtime, hearts or overall kills.
- Expanded one-time Starter Kit and cooldown Recovery Kit with sell/AH restrictions.
- Six GUI-driven Rookie Contracts and dynamic `/goals`.
- 15-second PvP assists with money, Combat XP and pair cooldown; no assist hearts.
- One-time First Blood with victim reuse protection.
- Isolated Skirmish arena, kit, inventory restore, no Lifesteal/legacy/bounty stats.
- Default HUD is balance, kills, deaths, rank and all relevant ability lines.
- Ability use/toggle audio reduced to at most a quiet pling.
- EconomyWatch-influenced, persistent contract rotations and expired-completed claim grace UI.
- Named seasons in commands, archive and Hall of Fame records.
- Farming XP includes breeding and farm-animal harvesting.
- Offline-capable staff profiles, append-only staff notes and auditable reports.
- Full source build succeeds for all 33 plugins.
- Recovery Kits now include two 30-minute account-bound ability trials.
- Reports have a paginated staff GUI and staff audit has category navigation.
- Season name is available as an optional HUD component.
- Mining, Farming, Combat, Alchemy and Enchanting received functional perk effects and clearer English lore.
- RC2 clean Paper boot succeeds with all 33 plugins.

## Not Done

- Season name is optional rather than forced into the default HUD because the requested default remains balance, kills, deaths, rank and abilities.
- Exact Java 25 runtime testing is unavailable locally; RC2 runtime testing passed on Java 26.

## Unclear Or Requires Runtime Testing

- The reported item-stacking bug has no reliable reproduction. It may involve a client view refresh, an economy UI or server inventory synchronization. No blind fix was applied.
- Multiplayer combat, GUI interaction and visual resource-pack behavior still require real-client acceptance testing.
- Skirmish spectator/respawn timing, assist edge cases and all GUI click paths need multiplayer acceptance testing.
- Rookie playtime cannot infer hours played before this module existed.
