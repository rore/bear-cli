param(
    [string]$DemoRepoPath = "..\bear-account-demo",
    [switch]$IncludeGreenfieldReset,
    [switch]$IncludeGradleCache,
    [switch]$WhatIf,
    [switch]$Yes
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-Directory([string]$path, [string]$message) {
    if (-not (Test-Path -LiteralPath $path -PathType Container)) {
        throw $message
    }
}

function Confirm-Cleanup([switch]$Yes, [switch]$WhatIf) {
    if ($Yes -or $WhatIf) {
        return
    }
    Write-Output ""
    $answer = Read-Host "Type CLEAN_DEMO to continue"
    if ($answer -ne "CLEAN_DEMO") {
        throw "Demo cleanup aborted. Confirmation token did not match."
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$bearCliRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir ".."))
Assert-Directory -path $bearCliRoot -message "Could not resolve bear-cli root."
if (-not (Test-Path -LiteralPath (Join-Path $bearCliRoot ".git"))) {
    throw "bear-cli root is not a git repository."
}

$demoRoot = [System.IO.Path]::GetFullPath((Join-Path $bearCliRoot $DemoRepoPath))
Assert-Directory -path $demoRoot -message "Demo repository not found: $demoRoot"
if (-not (Test-Path -LiteralPath (Join-Path $demoRoot ".git"))) {
    throw "Demo repository missing .git: $demoRoot"
}

$safeCleanScript = Join-Path $bearCliRoot "scripts\safe-clean-bear-generated.ps1"
if (-not (Test-Path -LiteralPath $safeCleanScript)) {
    throw "Missing cleanup script: $safeCleanScript"
}

Write-Output "Demo cleanup target repo: $demoRoot"
Write-Output "Step 1/3: remove generated/demo-run artifacts"
Write-Output "Step 2/3: reset tracked changes + remove untracked and ignored files"
Write-Output "Step 3/3: report git status + mandatory path checks"
if ($IncludeGreenfieldReset) {
    Write-Output "Mode: greenfield reset (also removes BEAR-authored IR/implementation paths before git restore)"
} else {
    Write-Output "Mode: branch reset only (preserves committed BEAR-authored IR/implementation paths)"
}

Confirm-Cleanup -Yes:$Yes -WhatIf:$WhatIf

Push-Location $demoRoot
try {
    if ($WhatIf) {
        & $safeCleanScript -IncludeGreenfieldReset:$IncludeGreenfieldReset -IncludeGradleCache:$IncludeGradleCache -WhatIf
        Write-Output "WhatIf mode: showing git reset/clean actions."
        Write-Output "Would run: git restore --worktree --staged ."
        if ($IncludeGradleCache) {
            Write-Output "Would run: git clean -ndx"
            & git clean -ndx
        } else {
            Write-Output "Would run: git clean -ndx -e .bear-gradle-user-home/"
            & git clean -ndx -e .bear-gradle-user-home/
        }
    } else {
        & $safeCleanScript -IncludeGreenfieldReset:$IncludeGreenfieldReset -IncludeGradleCache:$IncludeGradleCache -Yes
        & git restore --worktree --staged .
        if ($IncludeGradleCache) {
            & git clean -fdx
        } else {
            & git clean -fdx -e .bear-gradle-user-home/
        }
    }

    Write-Output ""
    Write-Output "git status --short:"
    $statusLines = @(& git status --short)
    if ($statusLines.Count -eq 0) {
        Write-Output "(clean)"
    } else {
        $statusLines | ForEach-Object { Write-Output $_ }
    }

    Write-Output ""
    Write-Output "Path checks:"
    $checks = @(
        "build",
        "build2",
        "build3",
        "build4",
        "bin/main",
        "bin/test",
        ".gradle",
        "bear.blocks.yaml",
        "spec",
        "src/main/java/blocks",
        "src/test/java/blocks",
        ".bear-gradle-user-home"
    )
    foreach ($path in $checks) {
        if (Test-Path -LiteralPath $path) {
            Write-Output "$path : EXISTS"
        } else {
            Write-Output "$path : MISSING"
        }
    }
} finally {
    Pop-Location
}
