# MitchSMP Launch Checklist

## Before Public Launch

- Restart the server after every build so the latest jars load.
- Run `/plugins` and verify all `MitchSMP-*` plugins are green.
- Run `/rankperms commands` as Owner/Admin and spot-check command visibility.
- Turn off owner testing mode unless actively testing:
  - `/adminmode override off`
  - `/adminmode off`
- Clear old anti-cheat test data if needed:
  - `/ac clear <player>`

## Permission Smoke Test

Test with one Default/VIP and one staff account:

- Default/VIP cannot use `/admin`, `/gm`, `/tp`, `/eco`, `/sethearts`, `/event start`, `/season reset`, `/bw start`.
- Default/VIP can use `/spawn`, `/tpa`, `/home`, `/balance`, `/pay`, `/ah`, `/hud`, `/rtp`, `/bw join`.
- Staff outside adminmode behaves like Legend convenience, not staff power.
- Staff inside adminmode can use staff tools.
- Owner-only `/adminmode override` only works for rank Owner.

## Gameplay Smoke Test

- Lifesteal outside BedWars: kill gives +1 heart, death removes -1 heart.
- BedWars: `/bw join`, no `/home` or `/tpa` inside match, keep inventory on death, leave returns cleanly.
- AuctionHouse: buy, sell, cancel, search sign, my listings.
- Economy: `/quicksell` drag items into the QS sale inventory, hover values, confirm/cancel, `/eco clear <player> confirm`, `/eco resetall confirm`.
- HUD: `/hud mode seasonal`, `/hud mode overall`, reset season/overall and verify K/D updates.
- Jail: `/jail <player>`, `/jail visit <player>`, no commands from jail, no mobs.
- Hall of Fame: protected from block break/place, no mobs.
- Fake ores: staff gets XRay alert, player does not get staff alert.

## Known Limits

- Anti-cheat is alert/intervention based, not a full enterprise-grade cheat prevention system.
- BedWars should be tested with at least 2-4 real players before public launch.
- Economy balancing needs live data; use `/econwatch active <days>` and `/econwatch stats` after testing.
