$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $root "resourcepacks\BloodboundSMP"
$destination = Join-Path $root "BloodboundSMP-resourcepack.zip"
$temporary = Join-Path $root "resourcepacks\BloodboundSMP-resourcepack.building.zip"

if (!(Test-Path -LiteralPath $source)) {
    throw "Resourcepack source not found: $source"
}
if (Test-Path -LiteralPath $temporary) {
    Remove-Item -LiteralPath $temporary -Force
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sourcePrefix = (Resolve-Path -LiteralPath $source).Path.TrimEnd('\') + '\'
$archive = [IO.Compression.ZipFile]::Open($temporary, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $source -Recurse -File) {
        if (!$file.FullName.StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to package a file outside the resourcepack source: $($file.FullName)"
        }
        $entryName = $file.FullName.Substring($sourcePrefix.Length).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive,
            $file.FullName,
            $entryName,
            [IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
} finally {
    $archive.Dispose()
}

Move-Item -LiteralPath $temporary -Destination $destination -Force

$verify = [IO.Compression.ZipFile]::OpenRead($destination)
try {
    $badEntries = @($verify.Entries | Where-Object { $_.FullName.Contains('\') })
    if ($badEntries.Count -gt 0) {
        throw "Resourcepack contains Windows-style zip paths."
    }
    if ($null -eq $verify.GetEntry("pack.mcmeta")) {
        throw "Resourcepack is missing pack.mcmeta at the zip root."
    }
} finally {
    $verify.Dispose()
}

$hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA1).Hash.ToLowerInvariant()
Write-Host "Built BloodboundSMP-resourcepack.zip"
Write-Host "SHA1: $hash"
