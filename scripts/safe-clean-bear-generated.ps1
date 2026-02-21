param(
    [switch]$WhatIf,
    [switch]$IncludeGreenfieldReset,
    [switch]$Yes
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    $root = (Resolve-Path ".").Path
    if (-not (Test-Path (Join-Path $root ".git"))) {
        throw "Not at repository root. Run from repo root where .git exists."
    }
    return $root
}

function Assert-InRepo([string]$repoRoot, [string]$targetPath) {
    $full = [System.IO.Path]::GetFullPath($targetPath)
    if (-not $full.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to delete outside repo root: $full"
    }
    return $full
}

function Add-IfExists([System.Collections.Generic.List[string]]$targets, [string]$path) {
    if (Test-Path -LiteralPath $path) {
        $targets.Add((Resolve-Path -LiteralPath $path).Path)
    }
}

function Confirm-Deletion([string[]]$targets, [switch]$Yes, [switch]$WhatIf) {
    if ($targets.Count -eq 0 -or $Yes -or $WhatIf) {
        return
    }
    Write-Output ""
    $answer = Read-Host "Type CLEAN to delete these paths"
    if ($answer -ne "CLEAN") {
        throw "Cleanup aborted. Confirmation token did not match."
    }
}

$repoRoot = Resolve-RepoRoot

# Paths that are BEAR-generated or deterministic temporary outputs.
$generatedRelativeTargets = @(
    ".bear-gradle-user-home",
    ".gradle-user",
    ".bear-test-results",
    ".tmp-compile-work",
    ".tmp-golden-*",
    ".tmp-staging-*",
    ".tmp-wrapper-*",
    ".tmp-manifest-*",
    "build",
    "bin/main",
    "bin/test",
    ".data",
    "src/main/java/com/bear/generated",
    "src/test/java/com/bear/generated"
)

# Optional reset paths for fresh greenfield re-runs.
$greenfieldResetTargets = @(
    "bear.blocks.yaml",
    "spec",
    "src/main/java/blocks",
    "src/test/java/blocks"
)

$targets = New-Object System.Collections.Generic.List[string]

foreach ($relative in $generatedRelativeTargets) {
    $candidate = Join-Path $repoRoot $relative
    if ($relative.Contains("*")) {
        Get-ChildItem -Path $candidate -Force -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $targets.Add($_.FullName) }
        continue
    }
    Add-IfExists $targets $candidate
}

if ($IncludeGreenfieldReset) {
    foreach ($relative in $greenfieldResetTargets) {
        Add-IfExists $targets (Join-Path $repoRoot $relative)
    }
}

$targets = @($targets | Sort-Object -Unique)

if ($targets.Count -eq 0) {
    Write-Output "No BEAR cleanup targets found."
    exit 0
}

Write-Output "BEAR cleanup targets:"
foreach ($t in $targets) {
    $full = Assert-InRepo $repoRoot $t
    Write-Output " - $full"
}

if ($WhatIf) {
    Write-Output "WhatIf mode: no files were deleted."
    exit 0
}

Confirm-Deletion -targets $targets -Yes:$Yes -WhatIf:$WhatIf

foreach ($t in $targets) {
    $full = Assert-InRepo $repoRoot $t
    Remove-Item -Recurse -Force -LiteralPath $full
    Write-Output "Removed: $full"
}

Write-Output "BEAR cleanup complete."
