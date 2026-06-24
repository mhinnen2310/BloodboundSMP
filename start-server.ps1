$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$hostScript = Join-Path $root "host-resourcepack.ps1"
$errorLog = Join-Path $root "logs\start-server-error.log"
$hostProcess = $null

Set-Content -LiteralPath $errorLog -Value "" -Encoding UTF8

$javaCandidate = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    (Get-Command java.exe -ErrorAction Stop).Source
}
$javaVersionText = (& $javaCandidate -version 2>&1 | Out-String).Trim()
$javaMajor = if ($javaVersionText -match 'version "(\d+)') { [int]$Matches[1] } else { 0 }
if ($javaMajor -lt 25) {
    throw "BloodboundSMP requires Java 25 or newer. Detected: $javaVersionText"
}

$diagnostics = Join-Path $root "logs\startup-diagnostics.log"
@(
    "BloodboundSMP startup diagnostics",
    "Started: $([DateTime]::Now.ToString('o'))",
    "Java executable: $javaCandidate",
    "Java: $javaVersionText",
    "Runtime target: Java 25",
    "Server jar: paper-26.1.2-70.jar",
    "Plugin jars: $(@(Get-ChildItem -LiteralPath (Join-Path $root 'plugins') -Filter 'MitchSMP-*.jar').Count)",
    $(if ($javaMajor -eq 25) { "Runtime check: PASS" } else { "Runtime check: WARNING - production must use Java 25; current major is $javaMajor" })
) | Set-Content -LiteralPath $diagnostics -Encoding UTF8

try {
    if (Test-Path -LiteralPath $hostScript) {
        $hostInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $hostInfo.FileName = "powershell.exe"
        $hostInfo.Arguments = '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $hostScript + '"'
        $hostInfo.UseShellExecute = $true
        $hostInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
        $hostProcess = [System.Diagnostics.Process]::Start($hostInfo)
        Start-Sleep -Milliseconds 750
    }

    & $javaCandidate -Xms4G -Xmx10G -jar paper-26.1.2-70.jar nogui
    exit $LASTEXITCODE
} catch {
    ($_ | Out-String) | Set-Content -LiteralPath $errorLog -Encoding UTF8
    throw
} finally {
    if ($hostProcess -ne $null -and !$hostProcess.HasExited) {
        Stop-Process -Id $hostProcess.Id -Force
    }
}
