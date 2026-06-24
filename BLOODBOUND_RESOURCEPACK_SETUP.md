# BloodboundSMP Resource Pack Setup

## Current Pack

- Zip: `BloodboundSMP-resourcepack.zip`
- Source: `resourcepacks/BloodboundSMP`
- Client pack format for Paper/Minecraft 26.1.2: `84`
- SHA1: `b4bada05354852aa8e6e1e4efbc991b88f3a4f20`
- Resource pack ID: `6f302d82-5e7a-4f4a-9cc9-26f1118b1dbd`

CustomModelData:

- Boss Shard: `NETHER_STAR`, CustomModelData `910001`
- Corrupted Heart: `ECHO_SHARD`, CustomModelData `910002`

The zip contains direct `bloodbound:boss_shard` and `bloodbound:corrupted_heart` item-model definitions plus CustomModelData fallbacks. RC2 writes both `item_model` and CustomModelData directly. Existing tagged items are migrated when their owner joins. Normal Nether Stars and Echo Shards retain their vanilla model.

## Local Test

1. Start `start-resourcepack-host.bat` and leave it running.
2. Keep these values in `server.properties`:
   - `resource-pack=http://127.0.0.1:8123/BloodboundSMP-resourcepack.zip`
   - `resource-pack-sha1=b4bada05354852aa8e6e1e4efbc991b88f3a4f20`
   - `resource-pack-id=6f302d82-5e7a-4f4a-9cc9-26f1118b1dbd`
   - `require-resource-pack=false`
3. Restart the Minecraft server.
4. Join from a client on the same PC and accept the server pack.

The included host is a dependency-free PowerShell TCP server bound only to `127.0.0.1`. Its script syntax has been verified, but its live HTTP download test was blocked by the Codex process-execution limit and still needs one manual test.

## Public Server

`127.0.0.1` only works for a client on the server PC. For other players:

1. Upload the zip to a stable direct-download HTTPS host or expose it through a controlled web server/reverse proxy.
2. Replace `resource-pack=` with that direct URL.
3. Keep the SHA1 above unchanged unless the zip changes.
4. Test with `require-resource-pack=false` first.
5. Set `require-resource-pack=true` only after multiple external clients load it successfully.

Every zip rebuild changes its SHA1. Recalculate with:

```powershell
(Get-FileHash .\BloodboundSMP-resourcepack.zip -Algorithm SHA1).Hash.ToLowerInvariant()
```

## Rollback

1. Clear `resource-pack=` and `resource-pack-sha1=`.
2. Keep `require-resource-pack=false`.
3. Restart the server.
