param(
    [string]$DemoRepoPath = "..\bear-account-demo",
    [string]$BaseRef = "HEAD",
    [switch]$IncludeGreenfieldReset,
    [switch]$SkipClean,
    [switch]$SkipSync,
    [switch]$RunGates,
    [switch]$SkipBuild,
    [switch]$IncludeGradleCache,
    [switch]$Yes
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    $root = (Resolve-Path ".").Path
    if (-not (Test-Path (Join-Path $root ".git"))) {
        throw "Run this script from bear-cli repository root."
    }
    return $root
}

function Resolve-DemoRoot([string]$repoRoot, [string]$demoRepoPath) {
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $demoRepoPath))
    if (-not (Test-Path (Join-Path $candidate ".git"))) {
        throw "Demo repository not found or missing .git: $candidate"
    }
    return $candidate
}

function Invoke-Step([string]$name, [scriptblock]$action) {
    Write-Output ""
    Write-Output ("== {0} ==" -f $name)
    & $action
}

$repoRoot = Resolve-RepoRoot
$demoRoot = Resolve-DemoRoot -repoRoot $repoRoot -demoRepoPath $DemoRepoPath

Write-Output "Simulated demo run preparation"
Write-Output (" - bear-cli root: {0}" -f $repoRoot)
Write-Output (" - demo root:     {0}" -f $demoRoot)
Write-Output " - isolation note: this is a clean-room simulation, not true memory isolation."

if (-not $SkipClean) {
    Invoke-Step "Clean demo workspace" {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\clean-demo-branch.ps1") `
            -DemoRepoPath $DemoRepoPath `
            -IncludeGreenfieldReset:$IncludeGreenfieldReset `
            -IncludeGradleCache:$IncludeGradleCache `
            -Yes:$Yes
        if ($LASTEXITCODE -ne 0) {
            throw "clean-demo-branch failed with exit code $LASTEXITCODE"
        }
    }
} else {
    Write-Output "Skipping clean step."
}

if (-not $SkipSync) {
    Invoke-Step "Sync demo runtime + agent package" {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\sync-bear-demo.ps1") `
            -DemoRepoPath $DemoRepoPath `
            -SkipBuild:$SkipBuild `
            -Yes:$Yes
        if ($LASTEXITCODE -ne 0) {
            throw "sync-bear-demo failed with exit code $LASTEXITCODE"
        }
    }
} else {
    Write-Output "Skipping sync step."
}

if (-not $RunGates) {
    Write-Output ""
    Write-Output "Preparation complete. Run with -RunGates to execute compile/check/pr-check smoke."
    exit 0
}

$bearCli = Join-Path $demoRoot ".bear\tools\bear-cli\bin\bear.bat"
if (-not (Test-Path $bearCli)) {
    throw "Missing demo bear runtime: $bearCli"
}

$results = @()

function Invoke-BearCommand([string]$stepName, [string[]]$args) {
    Write-Output ""
    Write-Output ("== {0} ==" -f $stepName)
    Push-Location $demoRoot
    try {
        & $bearCli @args
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $results += [pscustomobject]@{
        step = $stepName
        exit = $exitCode
    }
}

Invoke-BearCommand -stepName "bear compile --all" -args @("compile", "--all", "--project", ".")
Invoke-BearCommand -stepName "bear check --all" -args @("check", "--all", "--project", ".")
Invoke-BearCommand -stepName "bear pr-check --all" -args @("pr-check", "--all", "--project", ".", "--base", $BaseRef)

Write-Output ""
Write-Output "Simulated gate summary:"
foreach ($result in $results) {
    Write-Output ("SIM_RUN|step={0}|exit={1}" -f $result.step, $result.exit)
}

$firstFailure = $results | Where-Object { $_.exit -ne 0 } | Select-Object -First 1
if ($null -ne $firstFailure) {
    exit $firstFailure.exit
}
exit 0
