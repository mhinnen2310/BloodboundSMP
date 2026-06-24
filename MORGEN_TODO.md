# MitchSMP Morgen TODO

## Testwereld / Sandbox

- Command om een losse testwereld te maken, bijvoorbeeld `/testworld create <naam>`.
- Command om naar de testwereld te gaan, bijvoorbeeld `/testworld join <naam>`.
- Command om de testwereld volledig te resetten, bijvoorbeeld `/testworld reset <naam> confirm`.
- Testwereld moet los staan van de SMP:
  - aparte world folder
  - geen lifesteal/progression impact op main SMP
  - geen economy/stat/leaderboard schade tenzij expliciet test-only
  - snelle reset zonder main world/data aan te raken
- Mogelijke extra commands:
  - `/testworld leave`
  - `/testworld list`
  - `/testworld clonefrom smp <naam>` voor latere debug-scenario's

## Staffmode PvP Veiligheid

- Staff in staffmode mag standaard geen PvP doen en geen PvP ontvangen.
- Staff kan alleen PvP testen via:
  - `/staffmode pvp <playername>`
  - target krijgt accept-verzoek
  - pas na accept ontstaat tijdelijke PvP-toestemming tussen alleen die twee spelers
- Mogelijke accept/deny commands:
  - `/staffmode pvpaccept`
  - `/staffmode pvpdeny`
- PvP-sessie moet automatisch stoppen bij:
  - staffmode uit
  - vanish/noclip aan
  - speler disconnect
  - staff disconnect
  - timeout
  - `/staffmode pvpstop`
- Alle staffmode PvP requests en kills loggen in staff audit.
