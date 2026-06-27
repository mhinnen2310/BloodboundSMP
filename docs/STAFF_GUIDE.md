# BloodboundSMP Staff Guide

## Staff Principles

Staff members play as normal players outside staff mode. Administrative powers, inventories and balances remain isolated. Use the least invasive action that protects players and evidence.

## Daily Commands

- `/adminmode`: enter or leave isolated staff mode.
- `/staffprofile <player>`: offline-capable overview of rank, hearts, economy, bounty, K/D, assists, homes, snapshots, notes and reports.
- `/staffnote add <player> <note>` and `/staffnote view <player>`: append-only moderation notes.
- `/reports`: open reports.
- `/report view <id>` and `/report close <id>`: inspect and resolve reports.
- `/staffchat`: private staff coordination.
- `/perf`: current server performance summary.
- `/staffaudit`: owner-bound audit tools.

Player chat is not stored by the report or staff-audit implementation. Commands and relevant administrative actions are audit targets.

## Skilltree Admin

- `/skillsadmin reload`: reload `plugins/MitchSMP-Skills/skilltree.properties`.
- `/skillsadmin addxp <player> <track> <amount>`: grant XP to a gameplay track such as `mining`, `combat` or `economy`.
- `/skillsadmin points <player> <amount>`: set available skillpoints.
- `/skillsadmin reset <player>`: reset that player's Bloodbound skilltree choices and skill XP profile.

Use this tooling mainly in test/sandbox while tuning progression lanes. Skilltree nodes do not grant free hearts, and item-bound weapon/tool abilities stay under `/abilities`.

## Incident Response

1. Preserve context: inspect `/staffprofile`, reports and snapshots before changing inventory or location.
2. Contain immediate harm with freeze, jail or vanish tools as appropriate.
3. Record a factual `/staffnote`; do not write assumptions as facts.
4. Use rollback only for a bounded player/action/time scope.
5. Verify the result and record the action category in staff audit.
6. Escalate economy-wide incidents to Owner before resets or broad balance changes.

## Recovery And Rollback

- Never edit player data while the server is running.
- Use inventory snapshots and supported restore commands first.
- Stop the server before copying plugin data or worlds.
- Keep the pre-change server backup until the next release is accepted.
- Test broad rollback or economy actions in the test world before production use.

## Economy Abuse

Check the player's balance, inventory snapshots, Auction House activity, QuickSell activity and related accounts. Do not punish solely on a price anomaly: EconomyWatch prices are dynamic. Preserve evidence before removing duplicated value.

## Anti-Cheat Review

Anti-cheat signals are evidence, not an automatic conviction. Review movement conditions, ping, world, staff/test state and repeated patterns. Autoclicker enforcement is disabled; flight and severe movement checks prioritize freeze/review behavior.

## Rank Policy

Paid ranks provide convenience, storage, choices and cosmetics. They must not grant hearts, stronger combat gear, direct damage bonuses or reduced incoming damage. Staff permissions require staff mode; Owner override is for controlled testing and remains auditable.
