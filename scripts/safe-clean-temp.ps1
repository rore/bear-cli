param(
    [switch]$WhatIf
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

$repoRoot = Resolve-RepoRoot

$allowedRelativeTargets = @(
    ".tmp-golden-*",
    ".tmp-staging-*",
    ".tmp-wrapper-*",
    ".tmp-manifest-*",
    "spec\golden\compile\withdraw\.staging",
    "spec\golden\compile\withdraw\bear\.staging"
)

$targets = New-Object System.Collections.Generic.List[string]
foreach ($pattern in $allowedRelativeTargets) {
    $candidate = Join-Path $repoRoot $pattern
    if ($pattern.Contains("*")) {
        Get-ChildItem -Path $candidate -Force -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $targets.Add($_.FullName) }
    } else {
        if (Test-Path $candidate) {
            $targets.Add((Resolve-Path $candidate).Path)
        }
    }
}

$targets = @($targets | Sort-Object -Unique)

if ($targets.Count -eq 0) {
    Write-Output "No temp targets found."
    exit 0
}

Write-Output "Temp targets to remove:"
foreach ($t in $targets) {
    $full = Assert-InRepo $repoRoot $t
    Write-Output " - $full"
}

if ($WhatIf) {
    Write-Output "WhatIf mode: no files were deleted."
    exit 0
}

foreach ($t in $targets) {
    $full = Assert-InRepo $repoRoot $t
    Remove-Item -Recurse -Force -LiteralPath $full
    Write-Output "Removed: $full"
}

Write-Output "Cleanup complete."
