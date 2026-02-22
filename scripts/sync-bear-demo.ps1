param(
    [string]$DemoRepoPath = "..\bear-account-demo",
    [switch]$SkipBuild,
    [string]$CliInstallPath,
    [switch]$WhatIf,
    [switch]$Yes
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    $root = (Resolve-Path ".").Path
    if (-not (Test-Path (Join-Path $root ".git"))) {
        throw "Not at bear-cli repository root. Run from repo root where .git exists."
    }
    return $root
}

function Resolve-Absolute([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Path does not exist: $path"
    }
    return (Resolve-Path -LiteralPath $path).Path
}

function Assert-Inside([string]$root, [string]$candidate) {
    $fullRoot = [System.IO.Path]::GetFullPath($root)
    $fullCandidate = [System.IO.Path]::GetFullPath($candidate)
    if (-not $fullCandidate.StartsWith($fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing operation outside root. Root=$fullRoot Candidate=$fullCandidate"
    }
    return $fullCandidate
}

function Test-CliInstallRoot([string]$root) {
    return (Test-Path (Join-Path $root "bin\bear.bat")) -and
        (Test-Path (Join-Path $root "lib\app-0.1.0-SNAPSHOT.jar")) -and
        (Test-Path (Join-Path $root "lib\kernel-0.1.0-SNAPSHOT.jar"))
}

function Find-PackagedCli([string]$repoRoot) {
    $packaged = Join-Path $repoRoot "docs\bear-package\.bear\tools\bear-cli"
    if (Test-CliInstallRoot $packaged) {
        return (Resolve-Path $packaged).Path
    }
    return $null
}

function Resolve-PackagedAgentRoot([string]$repoRoot) {
    $agentRoot = Join-Path $repoRoot "docs\bear-package\.bear\agent"
    if (-not (Test-Path (Join-Path $agentRoot "BEAR_AGENT.md"))) {
        throw "Missing packaged agent bundle at $agentRoot. Expected docs/bear-package/.bear/agent/*."
    }
    return (Resolve-Path $agentRoot).Path
}

function Find-InstalledCli([string]$repoRoot, [string]$explicitPath) {
    if ($explicitPath) {
        $resolved = Resolve-Absolute $explicitPath
        if (-not (Test-CliInstallRoot $resolved)) {
            throw "Invalid CliInstallPath (missing expected bear runtime files): $resolved"
        }
        return $resolved
    }

    $localInstall = Join-Path $repoRoot "app\build\install\bear"
    if (Test-Path (Join-Path $localInstall "bin\bear.bat")) {
        return (Resolve-Path $localInstall).Path
    }

    $tempRoot = Join-Path $env:TEMP "bear-cli-build"
    if (-not (Test-Path $tempRoot)) {
        throw "Could not locate installDist output. Expected either $localInstall or $tempRoot."
    }

    $candidates = Get-ChildItem -Path $tempRoot -Directory |
        Sort-Object LastWriteTime -Descending

    foreach ($dir in $candidates) {
        $candidate = Join-Path $dir.FullName "app\install\bear"
        if (Test-CliInstallRoot $candidate) {
            return $candidate
        }
    }

    throw "Could not locate installDist output under $tempRoot. Run :app:installDist first."
}

function Confirm-OrThrow([switch]$WhatIf, [switch]$Yes) {
    if ($WhatIf -or $Yes) {
        return
    }
    $answer = Read-Host "Type SYNC to continue"
    if ($answer -ne "SYNC") {
        throw "Sync aborted. Confirmation token did not match."
    }
}

function Ensure-GitIgnoreEntry([string]$gitIgnorePath, [string]$entry, [switch]$WhatIf) {
    $normalizedEntry = $entry.Trim()
    if ($normalizedEntry.Length -eq 0) {
        return $false
    }

    if (-not (Test-Path -LiteralPath $gitIgnorePath)) {
        if ($WhatIf) {
            Write-Output ("WhatIf: would create {0} with entry: {1}" -f $gitIgnorePath, $normalizedEntry)
            return $true
        }
        Set-Content -LiteralPath $gitIgnorePath -Value ($normalizedEntry + [Environment]::NewLine)
        return $true
    }

    $existingRaw = Get-Content -LiteralPath $gitIgnorePath -Raw
    $normalizedRaw = $existingRaw -replace "`r`n", "`n"
    $lines = $normalizedRaw -split "`n"
    foreach ($line in $lines) {
        if ($line.Trim() -eq $normalizedEntry) {
            return $false
        }
    }

    if ($WhatIf) {
        Write-Output ("WhatIf: would append {0} to {1}" -f $normalizedEntry, $gitIgnorePath)
        return $true
    }

    if (($normalizedRaw.Length -gt 0) -and (-not $normalizedRaw.EndsWith("`n"))) {
        $normalizedRaw += "`n"
    }
    $normalizedRaw += $normalizedEntry + "`n"
    $finalRaw = $normalizedRaw -replace "`n", [Environment]::NewLine
    Set-Content -LiteralPath $gitIgnorePath -Value $finalRaw
    return $true
}

$repoRoot = Resolve-RepoRoot
$demoRoot = Resolve-Absolute $DemoRepoPath
if (-not (Test-Path (Join-Path $demoRoot ".git"))) {
    throw "Demo repo root must contain .git: $demoRoot"
}

$packagedAgentRoot = Resolve-PackagedAgentRoot $repoRoot
$packagedCli = Find-PackagedCli $repoRoot

if (-not $SkipBuild -and -not $CliInstallPath) {
    $gradleWrapper = Join-Path $repoRoot "gradlew.bat"
    if (-not (Test-Path $gradleWrapper)) {
        throw "Missing gradle wrapper: $gradleWrapper"
    }
    Write-Output "Building bear-cli distribution..."
    if (-not $WhatIf) {
        Push-Location $repoRoot
        try {
            & $gradleWrapper --no-daemon :app:installDist
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle build failed with exit code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
    } else {
        Write-Output "WhatIf mode: build skipped."
    }
}

if ($SkipBuild -and $packagedCli -and -not $CliInstallPath) {
    Write-Output ("SkipBuild enabled; using packaged CLI runtime: {0}" -f $packagedCli)
}

$installRoot = if ($CliInstallPath) {
    Find-InstalledCli $repoRoot $CliInstallPath
} else {
    try {
        Find-InstalledCli $repoRoot $null
    } catch {
        if ($packagedCli) {
            Write-Output ("Falling back to packaged CLI runtime: {0}" -f $packagedCli)
            $packagedCli
        } else {
            throw
        }
    }
}
$dstCliRoot = Join-Path $demoRoot ".bear\tools\bear-cli"
$dstCliBin = Join-Path $dstCliRoot "bin"
$dstCliLib = Join-Path $dstCliRoot "lib"

$runtimeOperations = @(
    @{ type = "replaceDir"; source = (Join-Path $installRoot "bin"); destination = $dstCliBin },
    @{ type = "replaceDir"; source = (Join-Path $installRoot "lib"); destination = $dstCliLib }
)

$docMappings = @(
    @{ source = (Join-Path $packagedAgentRoot "BEAR_AGENT.md"); destination = (Join-Path $demoRoot ".bear\agent\BEAR_AGENT.md") },
    @{ source = (Join-Path $packagedAgentRoot "WORKFLOW.md"); destination = (Join-Path $demoRoot ".bear\agent\WORKFLOW.md") },
    @{ source = (Join-Path $packagedAgentRoot "doc\BEAR_PRIMER.md"); destination = (Join-Path $demoRoot ".bear\agent\doc\BEAR_PRIMER.md") },
    @{ source = (Join-Path $packagedAgentRoot "doc\IR_EXAMPLES.md"); destination = (Join-Path $demoRoot ".bear\agent\doc\IR_EXAMPLES.md") },
    @{ source = (Join-Path $packagedAgentRoot "doc\IR_QUICKREF.md"); destination = (Join-Path $demoRoot ".bear\agent\doc\IR_QUICKREF.md") },
    @{ source = (Join-Path $packagedAgentRoot "doc\BLOCK_INDEX_QUICKREF.md"); destination = (Join-Path $demoRoot ".bear\agent\doc\BLOCK_INDEX_QUICKREF.md") }
)

Write-Output "Sync plan:"
Write-Output (" - Source CLI install: {0}" -f $installRoot)
Write-Output (" - Target demo repo:   {0}" -f $demoRoot)
Write-Output " - Replace runtime directories:"
foreach ($op in $runtimeOperations) {
    Write-Output ("   - {0}" -f (Assert-Inside $demoRoot $op.destination))
}
Write-Output " - Copy agent package files:"
foreach ($map in $docMappings) {
    Write-Output ("   - {0}" -f (Assert-Inside $demoRoot $map.destination))
}

if ($WhatIf) {
    Write-Output "WhatIf mode: no files were modified."
    exit 0
}

Confirm-OrThrow -WhatIf:$WhatIf -Yes:$Yes

foreach ($op in $runtimeOperations) {
    $src = Resolve-Absolute $op.source
    $dst = Assert-Inside $demoRoot $op.destination
    $parent = Split-Path -Parent $dst
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    if (Test-Path $dst) {
        Remove-Item -LiteralPath $dst -Recurse -Force
    }
    Copy-Item -LiteralPath $src -Destination $dst -Recurse -Force
    Write-Output ("Synced runtime dir: {0}" -f $dst)
}

foreach ($map in $docMappings) {
    $src = Resolve-Absolute $map.source
    $dst = Assert-Inside $demoRoot $map.destination
    $parent = Split-Path -Parent $dst
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    Copy-Item -LiteralPath $src -Destination $dst -Force
    Write-Output ("Synced package file: {0}" -f $dst)
}

$demoGitIgnore = Assert-Inside $demoRoot (Join-Path $demoRoot ".gitignore")
if (Ensure-GitIgnoreEntry -gitIgnorePath $demoGitIgnore -entry ".bear-gradle-user-home/" -WhatIf:$WhatIf) {
    if ($WhatIf) {
        Write-Output "WhatIf: demo .gitignore would be updated with .bear-gradle-user-home/"
    } else {
        Write-Output "Ensured demo .gitignore contains .bear-gradle-user-home/"
    }
}

$srcAppJar = Join-Path $installRoot "lib\app-0.1.0-SNAPSHOT.jar"
$srcKernelJar = Join-Path $installRoot "lib\kernel-0.1.0-SNAPSHOT.jar"
$dstAppJar = Join-Path $dstCliRoot "lib\app-0.1.0-SNAPSHOT.jar"
$dstKernelJar = Join-Path $dstCliRoot "lib\kernel-0.1.0-SNAPSHOT.jar"

$srcAppHash = (Get-FileHash $srcAppJar -Algorithm SHA256).Hash
$dstAppHash = (Get-FileHash $dstAppJar -Algorithm SHA256).Hash
$srcKernelHash = (Get-FileHash $srcKernelJar -Algorithm SHA256).Hash
$dstKernelHash = (Get-FileHash $dstKernelJar -Algorithm SHA256).Hash

if ($srcAppHash -ne $dstAppHash -or $srcKernelHash -ne $dstKernelHash) {
    throw "Post-sync hash mismatch detected for demo CLI jars."
}

$mismatch = @()
foreach ($map in $docMappings) {
    $srcHash = (Get-FileHash $map.source -Algorithm SHA256).Hash
    $dstHash = (Get-FileHash $map.destination -Algorithm SHA256).Hash
    if ($srcHash -ne $dstHash) {
        $mismatch += $map.destination
    }
}
if ($mismatch.Count -gt 0) {
    throw ("Post-sync hash mismatch detected for package files: " + ($mismatch -join ", "))
}

Write-Output "Sync complete."
Write-Output "CLI JAR hashes match source runtime output."
Write-Output "Agent package file hashes match docs/bear-package/.bear source."
