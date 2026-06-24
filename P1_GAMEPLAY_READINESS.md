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

## Not Done

- Recovery Kit does not grant sample unlocked endgame abilities; reward items remain intentionally low-power.
- Full skilltree rebalance of every existing perk and hand-based enchant/anvil mastery is not complete in this P1 pass.
- Staff audit's existing GUI has not been fully redesigned into every requested category; profile, notes and report command baselines are implemented.
- Reports are command-driven, not a full GUI.
- Season name is not forced into every player's HUD because HUD customization is preserved.
- No Git push occurred because this repository has no configured remote.
- The isolated Paper boot did not reach plugin loading: Paperclip was denied access to the copied `_clean-server-test/cache/mojang_26.1.2.jar` by the execution sandbox.

## Unclear Or Requires Runtime Testing

- The reported item-stacking bug has no reliable reproduction. It may involve a client view refresh, an economy UI or server inventory synchronization. No blind fix was applied.
- Java 25 clean-runtime startup and multiplayer behavior require an installed Java 25 runtime and real client test.
- Skirmish spectator/respawn timing, assist edge cases and all GUI click paths need multiplayer acceptance testing.
- Rookie playtime cannot infer hours played before this module existed.
