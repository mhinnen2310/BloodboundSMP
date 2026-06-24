param(
    [string]$JavaPath = "java",
    [int]$TimeoutSeconds = 150
)

$ErrorActionPreference = "Stop"
$workspace = [System.IO.Path]::GetFullPath($PSScriptRoot)
$testRoot = [System.IO.Path]::GetFullPath((Join-Path $workspace "_clean-server-test"))
if (-not $testRoot.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean a path outside the workspace: $testRoot"
}

if (Test-Path -LiteralPath $testRoot) {
    Remove-Item -LiteralPath $testRoot -Recurse -Force
}
New-Item -ItemType Directory -Path (Join-Path $testRoot "plugins") -Force | Out-Null

$paper = Get-ChildItem -LiteralPath $workspace -File -Filter "paper-*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $paper) {
    throw "No Paper jar found in $workspace"
}
$pluginJars = Get-ChildItem -LiteralPath (Join-Path $workspace "mitchsmp-src\build\jars") -File -Filter "MitchSMP-*-1.0.0-rc.1.jar"
if ($pluginJars.Count -lt 31) {
    throw "Expected at least 31 RC plugin jars, found $($pluginJars.Count). Run mitchsmp-src/build.ps1 first."
}

Copy-Item -LiteralPath $paper.FullName -Destination (Join-Path $testRoot "paper.jar")
$pluginJars | Copy-Item -Destination (Join-Path $testRoot "plugins")
$paperCache = Join-Path $workspace "cache\mojang_26.1.2.jar"
if (Test-Path -LiteralPath $paperCache) {
    New-Item -ItemType Directory -Path (Join-Path $testRoot "cache") -Force | Out-Null
    Copy-Item -LiteralPath $paperCache -Destination (Join-Path $testRoot "cache\mojang_26.1.2.jar")
}
Set-Content -LiteralPath (Join-Path $testRoot "eula.txt") -Value "eula=true" -Encoding ascii
@(
    "server-port=25575"
    "online-mode=false"
    "motd=BloodboundSMP clean boot test"
    "level-name=clean_world"
    "spawn-protection=0"
    "view-distance=3"
    "simulation-distance=3"
    "max-players=2"
) | Set-Content -LiteralPath (Join-Path $testRoot "server.properties") -Encoding ascii

$logPath = Join-Path $testRoot "clean-boot.log"
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $JavaPath
$startInfo.Arguments = "-Xms512M -Xmx1G -jar paper.jar --nogui"
$startInfo.WorkingDirectory = $testRoot
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.CreateNoWindow = $true

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
if (-not $process.Start()) {
    throw "Could not start clean test server."
}

$lines = [System.Collections.Generic.List[string]]::new()
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$ready = $false
try {
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        while (-not $process.StandardOutput.EndOfStream) {
            $line = $process.StandardOutput.ReadLine()
            if ($null -eq $line) { break }
            $lines.Add($line)
            if ($line -match "Done \([0-9.]+s\)!") {
                $ready = $true
                break
            }
        }
        if ($ready) { break }
        Start-Sleep -Milliseconds 100
    }
    if ($ready -and -not $process.HasExited) {
        $process.StandardInput.WriteLine("stop")
        $process.StandardInput.Flush()
        $process.WaitForExit(30000) | Out-Null
    }
} finally {
    if (-not $process.HasExited) {
        $process.Kill($true)
        $process.WaitForExit()
    }
    while (-not $process.StandardOutput.EndOfStream) {
        $lines.Add($process.StandardOutput.ReadLine())
    }
    while (-not $process.StandardError.EndOfStream) {
        $lines.Add($process.StandardError.ReadLine())
    }
    $lines | Set-Content -LiteralPath $logPath -Encoding utf8
}

$bad = $lines | Where-Object { $_ -match "\bERROR\b|Unhandled exception|Could not load|Could not pass event|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException" }
if (-not $ready) {
    $reason = if ($process.ExitCode -ne 0) { "exited with code $($process.ExitCode)" } else { "did not reach Done within $TimeoutSeconds seconds" }
    throw "Clean server $reason. See $logPath"
}
if ($bad.Count -gt 0) {
    $bad | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    throw "Clean server reached Done but logged $($bad.Count) launch error(s). See $logPath"
}

Write-Host "PASS: clean server reached Done, stopped cleanly, and logged no launch exceptions." -ForegroundColor Green
Write-Host "Log: $logPath"
