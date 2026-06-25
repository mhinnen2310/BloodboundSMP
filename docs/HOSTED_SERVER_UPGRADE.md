# Hosted Server Upgrade

Use this procedure when upgrading from an earlier BloodboundSMP release candidate.

Production runtime, staging runtime and build target are Java 25. Do not deploy these jars on Java 21.

1. Download a full host backup containing worlds, plugin data, configs and the current jars.
2. Stop Paper from the hosting panel and wait until the process is fully offline.
3. Keep every `plugins/MitchSMP-*` data folder. These contain persistent player and server data.
4. Remove the old `MitchSMP-*.jar` files only.
5. Upload all jars from one release folder together. Never mix release versions intentionally.
6. Upload the matching resource-pack zip and update its public direct-download URL and SHA-1 when the pack changed.
7. Start Paper and verify that all MitchSMP plugins enable without errors.
8. Run the smoke tests in `BLOODBOUND_TEST_COMMANDS.md` before reopening the server.

Do not replace jars while Paper is running. Do not delete plugin data folders during an ordinary upgrade.

The plugin API uses additive default methods where possible, but mixed-version installs are not supported as a deployment strategy. Upgrade the complete suite together.
