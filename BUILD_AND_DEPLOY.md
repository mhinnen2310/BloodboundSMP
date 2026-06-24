# Build And Deploy

## Requirements

- Java 25 runtime on production.
- JDK 25 or newer for compilation. Newer JDKs compile with `--release 25`.
- Paper 26.1.2 API already present under `libraries/`.

## Branches

- `main`: stable production.
- `develop`: active integration.
- `release/1.0`: 1.0 release candidate.
- `hotfix/<description>`: urgent fixes branched from `main`.

## Staged Build

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mitchsmp-src\build.ps1
```

Artifacts are written to `mitchsmp-src/build/jars`. `build-info.json` records the version, Java target, compiler and Git commit.

## Offline Deploy

1. Commit the approved changes.
2. Stop Paper cleanly and verify port 25565 is closed.
3. Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mitchsmp-src\build.ps1 -Deploy
```

Deploy refuses to overwrite live jars while Paper is listening. Existing jars are copied to a timestamped rollback directory before replacement. Use `-AllowDirty` only for an explicitly documented local test build.

## Rollback

1. Stop Paper.
2. Restore the complete matching jar set from `mitchsmp-src/build/deploy-backups/<timestamp>`.
3. Restore plugin data/world backups only when the incident requires data rollback.
4. Start Paper and retain `logs/startup-diagnostics.log` plus `logs/latest.log`.
