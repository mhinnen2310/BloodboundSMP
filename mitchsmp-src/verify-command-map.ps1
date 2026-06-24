$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$essentials = Join-Path $projectRoot "essentials\src\nl\mitchsmp\essentials\EssentialsPlugin.java"
$declared = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)

foreach ($pluginYml in Get-ChildItem -Path $projectRoot -Recurse -Filter plugin.yml) {
    foreach ($line in Get-Content -LiteralPath $pluginYml.FullName) {
        if ($line -match '^  ([A-Za-z0-9_-]+):$') {
            $declared.Add($Matches[1]) | Out-Null
            continue
        }
        if ($line -match '^    aliases:\s*\[(.*)\]') {
            foreach ($alias in $Matches[1].Split(',')) {
                $declared.Add($alias.Trim().Trim("'", '"').ToLowerInvariant()) | Out-Null
            }
        }
    }
}

$mapped = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$source = Get-Content -LiteralPath $essentials -Raw
foreach ($match in [regex]::Matches($source, 'Map\.entry\("([^"]+)"')) {
    $mapped.Add($match.Groups[1].Value) | Out-Null
}

$missing = @($declared | Where-Object { !$mapped.Contains($_) } | Sort-Object)
if ($missing.Count -gt 0) {
    throw "Commands missing from the permission filter: $($missing -join ', ')"
}

Write-Host "Command permission filter covers all $($declared.Count) declared commands and aliases."
