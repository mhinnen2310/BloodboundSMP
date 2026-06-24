# BloodboundSMP Rebrand Worklog

## Safety Scope

- Live server source: `C:\Users\Mitchel\Desktop\SMP`
- Full backup: `C:\Users\Mitchel\Desktop\SMP_BACKUPS\pre-bloodbound-rebrand-20260618-154739`
- Test server copy: `C:\Users\Mitchel\Desktop\BloodboundSMP-Test`
- Rule followed: all edits are made only inside the test server copy.
- Live integrity check: key live files still match the backup by SHA-256 after the driver crashes.

## Branding

- Public name: BloodboundSMP
- Short name: Bloodbound
- Main tagline: Steal Hearts. Build Legacy.
- Secondary tagline: No Claims. No Mercy.
- Style: dark fantasy PvP Lifesteal SMP
- Palette: dark red, crimson, ancient gold, black, gray, bone white

## Modified Files In The Test Copy

- `server.properties`
  - Set the MOTD to BloodboundSMP branding.
  - Changed the test server port and query port to `25566`.
- `server-icon.png`
  - Added a Bloodbound-style 64x64 server icon.
- `mitchsmp-src/core/src/nl/mitchsmp/core/util/Text.java`
  - Changed the global visible prefix to Bloodbound.
  - Added centralized English/Bloodbound visible text replacements for server messages, menus and lore.
  - Added `rawColor(...)` so player-written chat is not translated by the server text filter.
- `mitchsmp-src/core/src/nl/mitchsmp/core/api/MitchRank.java`
  - Updated visible rank prefix colors to the Bloodbound palette.
- `mitchsmp-src/hud/src/nl/mitchsmp/hud/HudPlugin.java`
  - Changed the scoreboard title from MitchSMP to Bloodbound.
- `mitchsmp-src/chat/src/nl/mitchsmp/chat/ChatPlugin.java`
  - Updated staffchat colors to the Bloodbound style.
  - Ensured player chat/private messages use raw color handling.
- `mitchsmp-src/**/*.java`
  - Converted remaining visible Dutch command/menu/lore/book strings to English where they are not covered by the central text filter.
  - Re-saved Java sources as UTF-8 without BOM after PowerShell added a BOM during a mechanical text pass.
- `plugins/MitchSMP-*.jar`
  - Rebuilt all custom plugin jars from the test source copy.
- `README.md`
  - Updated the test copy README to BloodboundSMP English rebrand documentation.
- `mitchsmp-src/README.md`
  - Updated source documentation header to clarify public Bloodbound branding while internal module IDs remain unchanged.
- `bloodbound-startup-console.log`, `bloodbound-startup-stdout.log`, `bloodbound-startup-stderr.log`
  - Startup verification logs generated from the test server.
- `BLOODBOUND_CHANGED_FILES.txt`
  - Exact hash-based changed-file list compared to the backup.

## Intentionally Not Renamed

The internal plugin IDs, jar names, package names, folders and `plugin.yml` dependency names still use `MitchSMP-*`.
This is intentional for the test rebrand because changing those names would affect plugin loading order and dependency contracts.
Visible player-facing branding is handled through MOTD, prefixes, scoreboard titles, messages, menus and docs.

## Rollback Plan

1. Stop the test server if it is running.
2. Delete `C:\Users\Mitchel\Desktop\BloodboundSMP-Test` if you want to discard the test.
3. The official server can be restored from `C:\Users\Mitchel\Desktop\SMP_BACKUPS\pre-bloodbound-rebrand-20260618-154739`.
4. The live server at `C:\Users\Mitchel\Desktop\SMP` was not edited during this rebrand pass.

## Startup Verification

- Build result: all custom plugin jars compiled successfully.
- Test server port: `25566`.
- Startup result: reached `Done`.
- Error scan: `0` lines matching `ERROR`, `Exception`, `Caused by`, `Could not load`, `failed to enable` or `SEVERE`.
- Console log: `C:\Users\Mitchel\Desktop\BloodboundSMP-Test\bloodbound-startup-console.log`
- Notes: Paper/Java emitted normal warnings about terminal features, spark async-profiler support on Windows and a deprecated `Unsafe` call in JOML. These were not plugin load errors.
- Data safety follow-up: Paper startup touched copied runtime world/config files during verification. Those runtime files were restored from the backup afterward. The final changed-file list reports `0` world file changes.

## Crash/Event Log Note

Windows event logs around the PC crashes show repeated AMD-related events:

- `cncmd.exe` application crashes.
- AMD `atio6axx.dll` fault module reports.
- `LiveKernelEvent 141` watchdog/TDR reports involving `amdkmdag.sys`.
- One reboot initiated by `Radeonsoftware.exe`.

This points to the AMD driver/Radeon stack rather than the server rebrand file edits. One ChatGPT Desktop hang was also logged later, but the hard driver resets were AMD-related.

## Approval Migration Plan

After the test is approved:

1. Stop the official server.
2. Make a fresh backup of the official server.
3. Copy only the approved branding files from the test copy:
   - rebuilt plugin jars from `plugins\`
   - `server.properties` MOTD change only, not the test port unless desired
   - `server-icon.png`
   - approved README/docs
4. Keep existing official worlds, player data, economy, stats, hearts, inventories and permissions.
5. Start the official server and check the console before opening it to players.

## Text Examples

- MOTD: `BloodboundSMP - Steal Hearts. Build Legacy.` / `No Claims. No Mercy.`
- Chat prefix: `[Bloodbound]`
- Scoreboard title: `Bloodbound`
- Staffchat prefix: `[Staff]` in dark red/crimson style.
- Example command denial: `You do not have permission.`
- Example BedWars message: `Your bed is still alive. Respawning at your island.`
- Example Auction House labels: `Search`, `Sort`, `My listings`, `New listing`, `Back to AH`.
