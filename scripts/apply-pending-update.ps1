param(
    [string]$ServerRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$root = [System.IO.Path]::GetFullPath($ServerRoot)
$plugins = Join-Path $root "plugins"
$updates = Join-Path $plugins ".updates"
$pendingPath = Join-Path $updates "pending-update.json"

if (-not (Test-Path -LiteralPath $pendingPath)) {
    Write-Host "No pending BloodboundSMP update."
    exit 0
}

$pending = Get-Content -LiteralPath $pendingPath -Raw | ConvertFrom-Json
if ($pending.rollbackBackup) {
    $source = Join-Path ([string]$pending.rollbackBackup) "plugins"
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Rollback backup plugin folder not found: $source"
    }
    Write-Host "Applying BloodboundSMP rollback from $source"
} else {
    $targetVersion = [string]$pending.targetVersion
    if ([string]::IsNullOrWhiteSpace($targetVersion)) {
        throw "Pending update has no targetVersion."
    }
    $source = Join-Path $updates ("staged\" + $targetVersion)
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Staged update not found: $source"
    }
    Write-Host "Applying BloodboundSMP update $targetVersion"
}

$backup = Join-Path $updates ("backups\pre-apply-" + (Get-Date -Format "yyyy-MM-dd_HH-mm-ss"))
New-Item -ItemType Directory -Force (Join-Path $backup "plugins") | Out-Null
Get-ChildItem -LiteralPath $plugins -Filter "MitchSMP-*.jar" -File -ErrorAction SilentlyContinue |
    Copy-Item -Destination (Join-Path $backup "plugins") -Force

Get-ChildItem -LiteralPath $plugins -Filter "MitchSMP-*.jar" -File -ErrorAction SilentlyContinue |
    Remove-Item -Force

Get-ChildItem -LiteralPath $source -Filter "MitchSMP-*.jar" -File |
    Copy-Item -Destination $plugins -Force

Remove-Item -LiteralPath $pendingPath -Force
Write-Host "BloodboundSMP pending update applied. Backup: $backup"
