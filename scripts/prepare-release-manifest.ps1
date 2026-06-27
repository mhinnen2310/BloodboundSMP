param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$ReleaseRoot = "release-assets",
    [string]$Minecraft = "26.1.2",
    [int]$Java = 25
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$releaseDir = Join-Path $repoRoot (Join-Path $ReleaseRoot $Version)
if (-not (Test-Path -LiteralPath $releaseDir)) {
    throw "Release directory not found: $releaseDir"
}

$jars = Get-ChildItem -LiteralPath $releaseDir -Filter "MitchSMP-*.jar" -File | Sort-Object Name
if ($jars.Count -eq 0) {
    throw "No MitchSMP plugin jars found in $releaseDir"
}

$plugins = foreach ($jar in $jars) {
    $pluginName = $jar.BaseName -replace ("-" + [regex]::Escape($Version) + "$"), ""
    [ordered]@{
        name = $pluginName
        file = $jar.Name
        version = $Version
        sha256 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes = $jar.Length
    }
}

$files = foreach ($file in (Get-ChildItem -LiteralPath $releaseDir -File | Sort-Object Name)) {
    [ordered]@{
        name = $file.Name
        bytes = $file.Length
        sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$manifest = [ordered]@{
    generatedAt = [DateTime]::UtcNow.ToString("o")
    release = "v$Version"
    tag = "v$Version"
    version = $Version
    minecraft = $Minecraft
    java = $Java
    plugins = $plugins
    files = $files
}

$manifestPath = Join-Path $releaseDir "mitchsmp-release-manifest.json"
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

$raw = Get-Content -LiteralPath $manifestPath -Raw
$updaterPattern = [regex]'\{[^{}]*"name"\s*:\s*"([^"]+)"[^{}]*"file"\s*:\s*"([^"]+)"[^{}]*"version"\s*:\s*"([^"]+)"[^{}]*"sha256"\s*:\s*"([a-fA-F0-9]{64})"[^{}]*}'
$matches = $updaterPattern.Matches($raw).Count
if ($matches -ne $jars.Count) {
    throw "Manifest validation failed: updater regex sees $matches plugins, expected $($jars.Count)."
}

Write-Host "Wrote $manifestPath"
Write-Host "Updater-visible plugin assets: $matches"
