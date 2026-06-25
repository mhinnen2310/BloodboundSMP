param(
    [switch]$Deploy,
    [switch]$AllowDirty
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ServerRoot = Split-Path -Parent $ProjectRoot
$BuildRoot = Join-Path $ProjectRoot "build"
$PluginsDir = Join-Path $ServerRoot "plugins"
$ApiJar = Join-Path $ServerRoot "libraries\io\papermc\paper\paper-api\26.1.2.build.70-stable\paper-api-26.1.2.build.70-stable.jar"
$VersionFile = Join-Path $ServerRoot "VERSION"
$Version = if (Test-Path -LiteralPath $VersionFile) { (Get-Content -LiteralPath $VersionFile -Raw).Trim() } else { "0.0.0-dev" }
$JavaRelease = "25"

if ($Version -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$') {
    throw "VERSION is not valid SemVer: $Version"
}

function Get-ServerPort {
    $properties = Join-Path $ServerRoot "server.properties"
    if (Test-Path $properties) {
        $line = Get-Content $properties | Where-Object { $_ -match '^server-port=' } | Select-Object -First 1
        if ($line -and $line.Split('=', 2)[1] -match '^\d+$') {
            return [int]$line.Split('=', 2)[1]
        }
    }
    return 25565
}

function Test-ServerRunning {
    $port = Get-ServerPort
    $pattern = ":$port\s+.*LISTENING"
    return $null -ne (netstat -ano -p TCP | Select-String $pattern | Select-Object -First 1)
}

if ($Deploy -and (Test-ServerRunning)) {
    throw "Deployment refused: the Minecraft server is still listening on port $(Get-ServerPort). Stop it fully before using -Deploy."
}

function Get-GitCommit {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $inside = & git -C $ServerRoot rev-parse --is-inside-work-tree 2>$null
        $insideCode = $LASTEXITCODE
        $commit = & git -C $ServerRoot rev-parse --short=12 --verify HEAD 2>$null
        $commitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldPreference
    }
    if ($insideCode -ne 0 -or $inside -ne "true" -or $commitCode -ne 0) {
        return "unversioned"
    }
    return ($commit | Out-String).Trim()
}

function Test-GitDirty {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $inside = & git -C $ServerRoot rev-parse --is-inside-work-tree 2>$null
        $insideCode = $LASTEXITCODE
        $status = & git -C $ServerRoot status --porcelain 2>$null
        $statusCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldPreference
    }
    if ($insideCode -ne 0 -or $inside -ne "true" -or $statusCode -ne 0) {
        return $true
    }
    return -not [string]::IsNullOrWhiteSpace($status -join "`n")
}

if ($Deploy -and !$AllowDirty -and (Test-GitDirty)) {
    throw "Deployment refused: commit all release files first, or use -AllowDirty only for a documented local test."
}

$DeployBackup = $null
if ($Deploy) {
    $DeployBackup = Join-Path $BuildRoot ("deploy-backups\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
    New-Item -ItemType Directory -Force $DeployBackup | Out-Null
    Get-ChildItem -LiteralPath $PluginsDir -Filter "MitchSMP-*.jar" -ErrorAction SilentlyContinue |
        Copy-Item -Destination $DeployBackup -Force
}

function Find-JdkTool($toolName) {
    $fromPath = Get-Command $toolName -ErrorAction SilentlyContinue
    if ($fromPath) {
        return $fromPath.Source
    }

    $javaDirs = @(
        $env:JAVA_HOME,
        "C:\Program Files\Java\jdk-26.0.1",
        "C:\Program Files\Java\latest"
    ) | Where-Object { $_ -and (Test-Path $_) }

    foreach ($dir in $javaDirs) {
        $candidate = Join-Path $dir "bin\$toolName"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    $found = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName "bin\$toolName" } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1

    if ($found) {
        return $found
    }

    throw "Could not find $toolName. Install a JDK or set JAVA_HOME."
}

function Quote-JavacArg($value) {
    return '"' + ($value -replace "\\", "/") + '"'
}

function Invoke-Javac($argFile, $name) {
    $oldErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $script:Javac "@$argFile" 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldErrorActionPreference
    }
    if ($exitCode -ne 0) {
        $output | ForEach-Object { Write-Host $_ }
        throw "javac failed for $name"
    }
}

function Build-Module($name, $folder, $jarName, $extraClassPath) {
    $moduleRoot = Join-Path $ProjectRoot $folder
    $classes = Join-Path $BuildRoot "classes\$name"
    $artifactPrefix = $jarName.Substring(0, $jarName.Length - "-0.1.0.jar".Length)
    $artifactName = "$artifactPrefix-$Version.jar"
    $jarPath = Join-Path $BuildRoot "jars\$artifactName"
    $argFile = Join-Path $BuildRoot "$name-javac.args"

    if (Test-Path $classes) {
        Remove-Item $classes -Recurse -Force
    }
    New-Item -ItemType Directory -Force $classes | Out-Null

    $sources = Get-ChildItem (Join-Path $moduleRoot "src") -Recurse -Filter *.java
    if ($sources.Count -eq 0) {
        throw "No Java sources found for $name"
    }
    $classpathParts = @($script:CompileLibraries) + @($script:StubsClasses) + @($extraClassPath)
    $classpath = [string]::Join([IO.Path]::PathSeparator, $classpathParts)

    $argsFileContent = @(
        "-encoding",
        "UTF-8",
        "--release",
        $JavaRelease,
        "-cp",
        (Quote-JavacArg $classpath),
        "-d",
        (Quote-JavacArg $classes)
    ) + @($sources | ForEach-Object { Quote-JavacArg $_.FullName })
    $argsFileContent | Set-Content -Encoding ASCII $argFile

    Write-Host "Compiling $name..."
    Invoke-Javac $argFile $name

    $resources = Join-Path $moduleRoot "resources"
    if (Test-Path $resources) {
        Copy-Item (Join-Path $resources "*") $classes -Recurse -Force
    }
    $descriptor = Join-Path $classes "plugin.yml"
    if (Test-Path -LiteralPath $descriptor) {
        $descriptorText = Get-Content -LiteralPath $descriptor -Raw
        $descriptorText = $descriptorText -replace '(?m)^version:\s*.*$', "version: $Version"
        Set-Content -LiteralPath $descriptor -Value $descriptorText -Encoding UTF8
    }

    New-Item -ItemType Directory -Force (Split-Path -Parent $jarPath) | Out-Null
    if (Test-Path $jarPath) {
        Remove-Item $jarPath -Force
    }
    & $script:Jar --create --file $jarPath -C $classes .
    if ($LASTEXITCODE -ne 0) {
        throw "jar failed for $name"
    }

    if ($Deploy) {
        Get-ChildItem -LiteralPath $PluginsDir -Filter "$artifactPrefix-*.jar" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -ne $artifactName } |
            Remove-Item -Force
        Copy-Item $jarPath (Join-Path $PluginsDir $artifactName) -Force
        Write-Host "Built and deployed $artifactName"
    } else {
        Write-Host "Built and staged $artifactName"
    }
}

function Prepare-CompileLibraries {
    return @()

    $compileClasses = Join-Path $BuildRoot "compile-api-classes"
    if (Test-Path $compileClasses) {
        Remove-Item $compileClasses -Recurse -Force
    }
    New-Item -ItemType Directory -Force $compileClasses | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $libraryRoot = Join-Path $ServerRoot "libraries"
    $jars = @(Get-Item $ApiJar) + @(
        Get-ChildItem $libraryRoot -Recurse -Filter *.jar |
            Where-Object {
                $path = $_.FullName
                $path -like "*\net\kyori\adventure-api\*" -or
                $path -like "*\net\kyori\adventure-key\*" -or
                $path -like "*\net\kyori\examination-api\*" -or
                $path -like "*\net\kyori\examination-string\*" -or
                $path -like "*\net\kyori\option\*" -or
                $path -like "*\net\md-5\bungeecord-chat\*" -or
                $path -like "*\com\google\guava\*" -or
                $path -like "*\org\jspecify\*" -or
                $path -like "*\org\slf4j\slf4j-api\*" -or
                $path -like "*\io\papermc\paper\paper-api\*"
            }
    )
    foreach ($jar in $jars | Sort-Object FullName -Unique) {
        $zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
        try {
            foreach ($entry in $zip.Entries) {
                if (!$entry.Name -or !$entry.FullName.EndsWith(".class")) {
                    continue
                }
                $relative = $entry.FullName.Replace("/", [IO.Path]::DirectorySeparatorChar)
                $destination = Join-Path $compileClasses $relative
                $parent = Split-Path -Parent $destination
                if (!(Test-Path $parent)) {
                    New-Item -ItemType Directory -Force $parent | Out-Null
                }
                if (Test-Path $destination) {
                    continue
                }
                $inputStream = $entry.Open()
                try {
                    $outputStream = [IO.File]::Open($destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
                    try {
                        $inputStream.CopyTo($outputStream)
                    } finally {
                        $outputStream.Dispose()
                    }
                } finally {
                    $inputStream.Dispose()
                }
            }
        } finally {
            $zip.Dispose()
        }
    }

    return @($compileClasses)
}

function Build-Stubs {
    $classes = Join-Path $BuildRoot "classes\stubs"
    $jarPath = Join-Path $BuildRoot "stubs.jar"
    if (Test-Path $classes) {
        Remove-Item $classes -Recurse -Force
    }
    New-Item -ItemType Directory -Force $classes | Out-Null

    $argFile = Join-Path $BuildRoot "stubs-javac.args"
    $sources = Get-ChildItem (Join-Path $ProjectRoot "stubs\src") -Recurse -Filter *.java
    $argsFileContent = @(
        "-encoding",
        "UTF-8",
        "--release",
        $JavaRelease,
        "-d",
        (Quote-JavacArg $classes)
    ) + @($sources | ForEach-Object { Quote-JavacArg $_.FullName })
    $argsFileContent | Set-Content -Encoding ASCII $argFile

    Invoke-Javac $argFile "stubs"
    if (Test-Path $jarPath) {
        Remove-Item $jarPath -Force
    }
    & $script:Jar --create --file $jarPath -C $classes .
    if ($LASTEXITCODE -ne 0) {
        throw "jar failed for stubs"
    }
    return $jarPath
}

if (!(Test-Path $ApiJar)) {
    throw "Paper API jar not found: $ApiJar"
}

$script:Javac = Find-JdkTool "javac.exe"
$script:Jar = Find-JdkTool "jar.exe"

New-Item -ItemType Directory -Force $BuildRoot, (Join-Path $BuildRoot "jars") | Out-Null
Get-ChildItem -LiteralPath (Join-Path $BuildRoot "jars") -Filter "MitchSMP-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force

$script:CompileLibraries = Prepare-CompileLibraries
$script:StubsClasses = Build-Stubs

$coreClasses = Join-Path $BuildRoot "classes\core"
$coreJar = Join-Path $BuildRoot "jars\MitchSMP-Core-$Version.jar"

Build-Module "core" "core" "MitchSMP-Core-0.1.0.jar" @()

$coreClassPath = @($coreJar)
Build-Module "recovery" "recovery" "MitchSMP-Recovery-0.1.0.jar" $coreClassPath
Build-Module "lifesteal" "lifesteal" "MitchSMP-Lifesteal-0.1.0.jar" $coreClassPath
Build-Module "corrupted-hearts" "corrupted-hearts" "MitchSMP-CorruptedHearts-0.1.0.jar" $coreClassPath
Build-Module "permissions" "permissions" "MitchSMP-Permissions-0.1.0.jar" $coreClassPath
Build-Module "combattag" "combattag" "MitchSMP-CombatTag-0.1.0.jar" $coreClassPath
Build-Module "tpa" "tpa" "MitchSMP-TPA-0.1.0.jar" $coreClassPath
Build-Module "homes" "homes" "MitchSMP-Homes-0.1.0.jar" $coreClassPath
Build-Module "economy" "economy" "MitchSMP-Economy-0.1.0.jar" $coreClassPath
Build-Module "economywatch" "economywatch" "MitchSMP-EconomyWatch-0.1.0.jar" $coreClassPath
Build-Module "bounties" "bounties" "MitchSMP-Bounties-0.1.0.jar" $coreClassPath
Build-Module "auctionhouse" "auctionhouse" "MitchSMP-AuctionHouse-0.1.0.jar" $coreClassPath
Build-Module "hud" "hud" "MitchSMP-HUD-0.1.0.jar" $coreClassPath
Build-Module "rtp" "rtp" "MitchSMP-RTP-0.1.0.jar" $coreClassPath
Build-Module "essentials" "essentials" "MitchSMP-Essentials-0.1.0.jar" $coreClassPath
Build-Module "hub" "hub" "MitchSMP-Hub-0.1.0.jar" $coreClassPath
Build-Module "skyblock" "skyblock" "MitchSMP-Skyblock-0.1.0.jar" $coreClassPath
Build-Module "performance" "performance" "MitchSMP-Performance-0.1.0.jar" $coreClassPath
Build-Module "update-orchestrator" "update-orchestrator" "MitchSMP-UpdateOrchestrator-0.1.0.jar" $coreClassPath
Build-Module "custommobs" "custommobs" "MitchSMP-CustomMobs-0.1.0.jar" $coreClassPath
Build-Module "artifacts" "artifacts" "MitchSMP-Artifacts-0.1.0.jar" $coreClassPath
Build-Module "bosses" "bosses" "MitchSMP-Bosses-0.1.0.jar" $coreClassPath
Build-Module "bedwars" "bedwars" "MitchSMP-BedWars-0.1.0.jar" $coreClassPath
Build-Module "tntrun" "tntrun" "MitchSMP-TNTRun-0.1.0.jar" $coreClassPath
Build-Module "spleef" "spleef" "MitchSMP-Spleef-0.1.0.jar" $coreClassPath
Build-Module "skirmish" "skirmish" "MitchSMP-Skirmish-0.1.0.jar" $coreClassPath
Build-Module "cosmetics" "cosmetics" "MitchSMP-Cosmetics-0.1.0.jar" $coreClassPath
Build-Module "events" "events" "MitchSMP-Events-0.1.0.jar" $coreClassPath
Build-Module "progression" "progression" "MitchSMP-Progression-0.1.0.jar" $coreClassPath
Build-Module "gameplay" "gameplay" "MitchSMP-Gameplay-0.1.0.jar" $coreClassPath
Build-Module "skills" "skills" "MitchSMP-Skills-0.1.0.jar" $coreClassPath
Build-Module "endboss" "endboss" "MitchSMP-EndBoss-0.1.0.jar" $coreClassPath
Build-Module "chat" "chat" "MitchSMP-Chat-0.1.0.jar" $coreClassPath
Build-Module "anticheat" "anticheat" "MitchSMP-AntiCheat-0.1.0.jar" $coreClassPath
Build-Module "seasons" "seasons" "MitchSMP-Seasons-0.1.0.jar" $coreClassPath

$compilerVersion = (& $script:Javac -version 2>&1 | Out-String).Trim()
$buildInfo = [ordered]@{
    version = $Version
    javaRelease = [int]$JavaRelease
    compiler = $compilerVersion
    gitCommit = Get-GitCommit
    dirty = Test-GitDirty
    builtAtUtc = [DateTime]::UtcNow.ToString("o")
    deployed = [bool]$Deploy
    deployBackup = $DeployBackup
}
$buildInfo | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $BuildRoot "build-info.json") -Encoding UTF8

Write-Host ""
if ($Deploy) {
    Write-Host "Done. BloodboundSMP $Version deployed while the server was offline. Commit: $($buildInfo.gitCommit)"
} else {
    Write-Host "Done. BloodboundSMP $Version staged for Java $JavaRelease. Commit: $($buildInfo.gitCommit)"
}
