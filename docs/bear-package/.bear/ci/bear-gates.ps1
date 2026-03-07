$ErrorActionPreference = 'Stop'
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$mode = 'enforce'
$baseShaOverride = $null
$blocksPath = 'bear.blocks.yaml'
$scriptArgs = @($args)
if ($scriptArgs.Count -gt 0 -and $scriptArgs[0] -eq '--') {
    $scriptArgs = if ($scriptArgs.Count -gt 1) { @($scriptArgs[1..($scriptArgs.Count - 1)]) } else { @() }
}
for ($i = 0; $i -lt $scriptArgs.Count; $i++) {
    switch ($scriptArgs[$i]) {
        '--mode' {
            if ($i + 1 -ge $scriptArgs.Count) {
                throw 'missing value for --mode'
            }
            $mode = $scriptArgs[$i + 1]
            $i++
        }
        '--base-sha' {
            if ($i + 1 -ge $scriptArgs.Count) {
                throw 'missing value for --base-sha'
            }
            $baseShaOverride = $scriptArgs[$i + 1]
            $i++
        }
        '--blocks' {
            if ($i + 1 -ge $scriptArgs.Count) {
                throw 'missing value for --blocks'
            }
            $blocksPath = $scriptArgs[$i + 1]
            $i++
        }
        default {
            throw ('unsupported argument: ' + $scriptArgs[$i])
        }
    }
}
if ($mode -ne 'enforce' -and $mode -ne 'observe') {
    throw ('unsupported value for --mode: ' + $mode)
}

function New-OrderedArray($value) {
    if ($null -eq $value) {
        return @()
    }
    return @($value)
}

function Get-PropertyValue($object, $name) {
    if ($null -eq $object) {
        return $null
    }
    $property = $object.PSObject.Properties[$name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Normalize-Lines($text) {
    if ($null -eq $text -or $text.Length -eq 0) {
        return @()
    }
    $lines = ($text -replace "`r`n", "`n" -replace "`r", "`n").Split("`n")
    while ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -eq '') {
        if ($lines.Count -eq 1) {
            return @()
        }
        $lines = $lines[0..($lines.Count - 2)]
    }
    return $lines
}

function New-InvalidFooter() {
    return [ordered]@{
        valid = $false
        code = 'WRAPPER_FOOTER_INVALID'
        path = 'stderr.footer'
        remediation = 'Inspect captured BEAR stderr and restore deterministic failure footer.'
    }
}

function Parse-FailureFooter($text, $exitCode) {
    if ($exitCode -eq 0) {
        return [ordered]@{
            valid = $true
            code = $null
            path = $null
            remediation = $null
        }
    }
    $lines = Normalize-Lines $text
    if ($lines.Count -lt 3) {
        return New-InvalidFooter
    }
    $tail = $lines[($lines.Count - 3)..($lines.Count - 1)]
    if ($tail[0] -notmatch '^CODE=(.+)$') {
        return New-InvalidFooter
    }
    $code = $Matches[1]
    if ($tail[1] -notmatch '^PATH=(.+)$') {
        return New-InvalidFooter
    }
    $path = $Matches[1]
    if ($tail[2] -notmatch '^REMEDIATION=(.+)$') {
        return New-InvalidFooter
    }
    $remediation = $Matches[1]
    return [ordered]@{
        valid = $true
        code = $code
        path = $path
        remediation = $remediation
    }
}

function Try-ParseAgentJson($text) {
    if ([string]::IsNullOrWhiteSpace($text)) {
        return [ordered]@{
            json = $null
            valid = $false
        }
    }
    try {
        return [ordered]@{
            json = ($text | ConvertFrom-Json)
            valid = $true
        }
    } catch {
        return [ordered]@{
            json = $null
            valid = $false
        }
    }
}

function Get-CheckClasses($exitCode, $footerValid) {
    if (-not $footerValid) {
        return @('CI_INTERNAL_ERROR')
    }
    switch ($exitCode) {
        70 { return @('CI_INTERNAL_ERROR') }
        64 { return @('CI_VALIDATION_OR_USAGE_ERROR') }
        2 { return @('CI_VALIDATION_OR_USAGE_ERROR') }
        74 { return @('CI_IO_GIT_ERROR') }
        7 { return @('CI_POLICY_BYPASS_ATTEMPT') }
        6 { return @('CI_GOVERNANCE_DRIFT') }
        4 { return @('CI_TEST_FAILURE') }
        3 { return @('CI_GOVERNANCE_DRIFT') }
        0 { return @('CI_NO_STRUCTURAL_CHANGE') }
        default { return @('CI_INTERNAL_ERROR') }
    }
}

function Get-PrClasses($exitCode, $footerValid, $telemetry) {
    if (-not $footerValid) {
        return @('CI_INTERNAL_ERROR')
    }
    $primary = 'CI_NO_STRUCTURAL_CHANGE'
    switch ($exitCode) {
        70 { $primary = 'CI_INTERNAL_ERROR' }
        64 { $primary = 'CI_VALIDATION_OR_USAGE_ERROR' }
        2 { $primary = 'CI_VALIDATION_OR_USAGE_ERROR' }
        74 { $primary = 'CI_IO_GIT_ERROR' }
        7 { $primary = 'CI_POLICY_BYPASS_ATTEMPT' }
        5 { $primary = 'CI_BOUNDARY_EXPANSION' }
        0 { $primary = 'CI_NO_STRUCTURAL_CHANGE' }
        default { $primary = 'CI_INTERNAL_ERROR' }
    }
    $classes = @($primary)
    if ($telemetry.available -and $telemetry.hasDependencyPowerExpansion) {
        $classes += 'CI_DEPENDENCY_POWER_EXPANSION'
    }
    return @($classes | Select-Object -Unique)
}

function New-DeltaView($delta) {
    return [pscustomobject][ordered]@{
        class = [string](Get-PropertyValue $delta 'class')
        category = [string](Get-PropertyValue $delta 'category')
        change = [string](Get-PropertyValue $delta 'change')
        key = [string](Get-PropertyValue $delta 'key')
        deltaId = [string](Get-PropertyValue $delta 'deltaId')
    }
}

function Get-PrTelemetry($agentJson) {
    $empty = [ordered]@{
        available = $false
        deltas = @()
        governanceSignals = @()
        combinedBoundaryDeltas = @()
        boundaryDeltaIds = @()
        hasDependencyPowerExpansion = $false
    }
    $extensions = Get-PropertyValue $agentJson 'extensions'
    $prGovernance = Get-PropertyValue $extensions 'prGovernance'
    if ($null -eq $prGovernance) {
        return $empty
    }
    $deltas = New-OrderedArray (Get-PropertyValue $prGovernance 'deltas')
    $signals = New-OrderedArray (Get-PropertyValue $prGovernance 'governanceSignals')
    $blocks = New-OrderedArray (Get-PropertyValue $prGovernance 'blocks')
    $hasDependencyPowerExpansion = $false
    $combinedBoundaryDeltas = @()
    foreach ($delta in $deltas) {
        $deltaView = New-DeltaView $delta
        if ($deltaView.class -eq 'BOUNDARY_EXPANDING') {
            if ([string]::IsNullOrWhiteSpace($deltaView.deltaId)) {
                return $empty
            }
            $combinedBoundaryDeltas += $deltaView
            if ($deltaView.category -eq 'ALLOWED_DEPS') {
                $hasDependencyPowerExpansion = $true
            }
        }
    }
    foreach ($block in $blocks) {
        $blockDeltas = New-OrderedArray (Get-PropertyValue $block 'deltas')
        foreach ($delta in $blockDeltas) {
            $deltaView = New-DeltaView $delta
            if ($deltaView.class -eq 'BOUNDARY_EXPANDING') {
                if ([string]::IsNullOrWhiteSpace($deltaView.deltaId)) {
                    return $empty
                }
                $combinedBoundaryDeltas += $deltaView
                if ($deltaView.category -eq 'ALLOWED_DEPS') {
                    $hasDependencyPowerExpansion = $true
                }
            }
        }
    }
    $sortedBoundaryDeltas = @($combinedBoundaryDeltas | Sort-Object class, category, change, key)
    $dedupedBoundaryDeltas = @()
    $seenDeltaIds = @{}
    foreach ($delta in $sortedBoundaryDeltas) {
        if (-not $seenDeltaIds.ContainsKey($delta.deltaId)) {
            $seenDeltaIds[$delta.deltaId] = $true
            $dedupedBoundaryDeltas += $delta
        }
    }
    return [ordered]@{
        available = $true
        deltas = @($deltas)
        governanceSignals = @($signals)
        combinedBoundaryDeltas = @($dedupedBoundaryDeltas)
        boundaryDeltaIds = @($dedupedBoundaryDeltas | ForEach-Object { $_.deltaId })
        hasDependencyPowerExpansion = $hasDependencyPowerExpansion
    }
}

function Read-AllowFile($path) {
    if (-not (Test-Path $path)) {
        return [ordered]@{
            valid = $true
            entries = @()
        }
    }
    try {
        $json = Get-Content $path -Raw | ConvertFrom-Json
        return [ordered]@{
            valid = $true
            entries = New-OrderedArray (Get-PropertyValue $json 'entries')
        }
    } catch {
        return [ordered]@{
            valid = $false
            entries = @()
        }
    }
}

function Compare-ExactSet($left, $right) {
    $a = @($left | Sort-Object)
    $b = @($right | Sort-Object)
    if ($a.Count -ne $b.Count) {
        return $false
    }
    for ($index = 0; $index -lt $a.Count; $index++) {
        if ($a[$index] -ne $b[$index]) {
            return $false
        }
    }
    return $true
}

function Evaluate-Allow($modeValue, $prResult, $telemetry, $resolvedBaseSha, $allowFilePath) {
    $observed = @($telemetry.boundaryDeltaIds)
    if ($modeValue -ne 'enforce' -or $null -eq $prResult -or $prResult.exitCode -ne 5) {
        return [ordered]@{
            status = 'not-needed'
            reason = $null
            observedDeltaIds = $observed
        }
    }
    if (-not $telemetry.available -or $observed.Count -eq 0) {
        return [ordered]@{
            status = 'unavailable'
            reason = 'PR_GOVERNANCE_UNAVAILABLE'
            observedDeltaIds = $observed
        }
    }
    $allowData = Read-AllowFile $allowFilePath
    if (-not $allowData.valid) {
        return [ordered]@{
            status = 'unavailable'
            reason = 'ALLOW_FILE_INVALID'
            observedDeltaIds = $observed
        }
    }
    if ($allowData.entries.Count -eq 0) {
        return [ordered]@{
            status = 'mismatch'
            reason = 'ALLOW_FILE_MISSING'
            observedDeltaIds = $observed
        }
    }
    foreach ($entry in $allowData.entries) {
        $entryBaseSha = [string](Get-PropertyValue $entry 'baseSha')
        $entryDeltaIds = New-OrderedArray (Get-PropertyValue $entry 'deltaIds')
        if ($entryBaseSha -eq $resolvedBaseSha -and (Compare-ExactSet $entryDeltaIds $observed)) {
            return [ordered]@{
                status = 'matched'
                reason = $null
                observedDeltaIds = $observed
            }
        }
    }
    return [ordered]@{
        status = 'mismatch'
        reason = 'ALLOW_FILE_MISMATCH'
        observedDeltaIds = $observed
    }
}

function Get-AllowEntryCandidate($modeValue, $prResult, $telemetry, $resolvedBaseSha) {
    if ($modeValue -ne 'enforce' -or $null -eq $prResult -or $prResult.exitCode -ne 5) {
        return [ordered]@{
            status = 'not-needed'
            value = $null
        }
    }
    if (-not $telemetry.available -or $telemetry.boundaryDeltaIds.Count -eq 0) {
        return [ordered]@{
            status = 'unavailable'
            value = $null
        }
    }
    return [ordered]@{
        status = 'available'
        value = [ordered]@{
            baseSha = $resolvedBaseSha
            deltaIds = @($telemetry.boundaryDeltaIds)
        }
    }
}

function Escape-JsonString($value) {
    if ($null -eq $value) {
        return ''
    }
    return ([string]$value).Replace('\', '\\').Replace('"', '\"').Replace("`r", '\r').Replace("`n", '\n').Replace("`t", '\t')
}

function Format-AllowEntryCandidateJson($candidate, $pretty) {
    if ($null -eq $candidate) {
        return 'null'
    }
    $baseSha = Escape-JsonString $candidate.baseSha
    $deltaIds = @($candidate.deltaIds)
    if (-not $pretty) {
        $encodedDeltaIds = @($deltaIds | ForEach-Object { '"' + (Escape-JsonString $_) + '"' })
        return '{"baseSha":"' + $baseSha + '","deltaIds":[' + ($encodedDeltaIds -join ',') + ']}'
    }
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('{')
    $lines.Add('  "baseSha": "' + $baseSha + '",')
    $lines.Add('  "deltaIds": [')
    for ($index = 0; $index -lt $deltaIds.Count; $index++) {
        $suffix = if ($index -lt $deltaIds.Count - 1) { ',' } else { '' }
        $lines.Add('    "' + (Escape-JsonString $deltaIds[$index]) + '"' + $suffix)
    }
    $lines.Add('  ]')
    $lines.Add('}')
    return [string]::Join("`n", $lines)
}

function Get-CodeDisplay($code) {
    if ($null -eq $code) {
        return '-'
    }
    return $code
}

function Get-ClassesDisplay($classes) {
    if ($null -eq $classes -or $classes.Count -eq 0) {
        return '-'
    }
    return ($classes -join ',')
}

function New-MarkdownSummary($modeValue, $decision, $baseResolution, $checkReport, $prReport, $combinedBoundaryDeltas, $allowEntryCandidate) {
    $baseDisplay = if ($baseResolution.resolved) { $baseResolution.value } else { 'unresolved' }
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('# BEAR CI Governance')
    $lines.Add('')
    $lines.Add('- Mode: ' + $modeValue)
    $lines.Add('- Decision: ' + $decision)
    $lines.Add('- Base SHA: ' + $baseDisplay)
    $lines.Add('- Report: build/bear/ci/bear-ci-report.json')
    $lines.Add('')
    $lines.Add('## Check')
    $lines.Add('- Exit: ' + $checkReport.exitCode)
    $lines.Add('- Code: ' + (Get-CodeDisplay $checkReport.code))
    $lines.Add('- Classes: ' + (Get-ClassesDisplay $checkReport.classes))
    $lines.Add('')
    $lines.Add('## PR Check')
    if ($prReport.status -eq 'ran') {
        $lines.Add('- Exit: ' + $prReport.exitCode)
        $lines.Add('- Code: ' + (Get-CodeDisplay $prReport.code))
        $lines.Add('- Classes: ' + (Get-ClassesDisplay $prReport.classes))
    } else {
        $lines.Add('- Status: NOT_RUN')
        $lines.Add('- Reason: ' + $prReport.reason)
    }
    if ($combinedBoundaryDeltas.Count -gt 0) {
        $lines.Add('')
        $lines.Add('## Boundary Deltas')
        foreach ($delta in $combinedBoundaryDeltas) {
            $lines.Add('- `' + $delta.class + ' | ' + $delta.category + ' | ' + $delta.change + ' | ' + $delta.key + '`')
        }
    }
    if ($allowEntryCandidate.status -eq 'available') {
        $lines.Add('')
        $lines.Add('## Allow Entry Candidate')
        $lines.Add('```json')
        foreach ($line in Normalize-Lines (Format-AllowEntryCandidateJson $allowEntryCandidate.value $true)) {
            $lines.Add($line)
        }
        $lines.Add('```')
    } elseif ($allowEntryCandidate.status -eq 'unavailable') {
        $lines.Add('')
        $lines.Add('## Allow Entry Candidate')
        $lines.Add('Unavailable: PR governance telemetry was unusable, so no exact allow entry could be generated.')
    }
    return [string]::Join("`n", $lines) + "`n"
}

function Write-MarkdownSummary($summaryPath, $summaryContent) {
    Set-Content -Path $summaryPath -Value $summaryContent
    if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_STEP_SUMMARY)) {
        Add-Content -Path $env:GITHUB_STEP_SUMMARY -Value $summaryContent
    }
}
function Resolve-BaseSha($overrideValue, $repoRootPath) {
    if (-not [string]::IsNullOrWhiteSpace($overrideValue)) {
        return [ordered]@{
            resolved = $true
            value = $overrideValue
        }
    }
    $eventPath = $env:GITHUB_EVENT_PATH
    if ([string]::IsNullOrWhiteSpace($eventPath) -or -not (Test-Path $eventPath)) {
        return [ordered]@{
            resolved = $false
            value = $null
        }
    }
    try {
        $eventJson = Get-Content $eventPath -Raw | ConvertFrom-Json
    } catch {
        return [ordered]@{
            resolved = $false
            value = $null
        }
    }
    $pullRequest = Get-PropertyValue $eventJson 'pull_request'
    if ($null -ne $pullRequest) {
        $base = Get-PropertyValue (Get-PropertyValue $pullRequest 'base') 'sha'
        if (-not [string]::IsNullOrWhiteSpace($base)) {
            return [ordered]@{
                resolved = $true
                value = [string]$base
            }
        }
        return [ordered]@{
            resolved = $false
            value = $null
        }
    }
    $before = [string](Get-PropertyValue $eventJson 'before')
    if (-not [string]::IsNullOrWhiteSpace($before) -and $before -ne '0000000000000000000000000000000000000000') {
        return [ordered]@{
            resolved = $true
            value = $before
        }
    }
    $headFallback = (& git -C $repoRootPath rev-parse 'HEAD~1' 2>$null)
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($headFallback)) {
        return [ordered]@{
            resolved = $true
            value = $headFallback.Trim()
        }
    }
    return [ordered]@{
        resolved = $false
        value = $null
    }
}

function Format-CmdArgument($value) {
    if ($null -eq $value) {
        return '""'
    }
    return '"' + ([string]$value).Replace('"', '""') + '"'
}

function Format-ShArgument($value) {
    if ($null -eq $value) {
        return "''"
    }
    return "'" + ([string]$value).Replace("'", "'\''") + "'"
}

function Invoke-BearCommand($label, $commandText, $commandPath, $commandArgs) {
    $stdoutPath = Join-Path $script:tempDir ($label + '.stdout')
    $stderrPath = Join-Path $script:tempDir ($label + '.stderr')
    if ($env:OS -eq 'Windows_NT') {
        $quotedArgs = @($commandArgs | ForEach-Object { Format-CmdArgument $_ }) -join ' '
        $cmdLine = (Format-CmdArgument $commandPath) + $(if ($quotedArgs.Length -gt 0) { ' ' + $quotedArgs } else { '' })
        & cmd.exe /d /c ($cmdLine + ' 1> ' + (Format-CmdArgument $stdoutPath) + ' 2> ' + (Format-CmdArgument $stderrPath)) | Out-Null
        $exitCode = $LASTEXITCODE
    } else {
        $processInfo = New-Object System.Diagnostics.ProcessStartInfo
        $processInfo.FileName = $commandPath
        $processInfo.WorkingDirectory = (Get-Location).Path
        $processInfo.UseShellExecute = $false
        $processInfo.RedirectStandardOutput = $true
        $processInfo.RedirectStandardError = $true
        foreach ($arg in $commandArgs) {
            [void]$processInfo.ArgumentList.Add([string]$arg)
        }
        $process = [System.Diagnostics.Process]::Start($processInfo)
        $stdoutText = $process.StandardOutput.ReadToEnd()
        $stderrText = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $exitCode = $process.ExitCode
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($stdoutPath, $stdoutText, $utf8NoBom)
        [System.IO.File]::WriteAllText($stderrPath, $stderrText, $utf8NoBom)
    }
    $stdoutText = if (Test-Path $stdoutPath) { Get-Content $stdoutPath -Raw } else { '' }
    $stderrText = if (Test-Path $stderrPath) { Get-Content $stderrPath -Raw } else { '' }
    $stdoutHash = if (Test-Path $stdoutPath) { (Get-FileHash -Algorithm SHA256 $stdoutPath).Hash.ToLowerInvariant() } else { $null }
    $stderrHash = if (Test-Path $stderrPath) { (Get-FileHash -Algorithm SHA256 $stderrPath).Hash.ToLowerInvariant() } else { $null }
    $agent = Try-ParseAgentJson $stdoutText
    $footer = Parse-FailureFooter $stderrText $exitCode
    return [ordered]@{
        label = $label
        command = $commandText
        exitCode = $exitCode
        stdoutText = $stdoutText
        stderrText = $stderrText
        stdoutHash = $stdoutHash
        stderrHash = $stderrHash
        agentJson = $agent.json
        footer = $footer
    }
}
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir '..\..'))
$bearCommand = Join-Path $repoRoot ($(if ($env:OS -eq 'Windows_NT') { '.bear/tools/bear-cli/bin/bear.bat' } else { '.bear/tools/bear-cli/bin/bear' }))
$allowFilePath = Join-Path $repoRoot '.bear/ci/baseline-allow.json'
$reportPath = Join-Path $repoRoot 'build/bear/ci/bear-ci-report.json'
$summaryPath = Join-Path $repoRoot 'build/bear/ci/bear-ci-summary.md'
$null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $reportPath)
$script:tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ('bear-ci-' + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Force -Path $script:tempDir
$pushSucceeded = $false
try {
    Push-Location $repoRoot
    $pushSucceeded = $true
    $commandPrefix = $(if ($env:OS -eq 'Windows_NT') { '.bear/tools/bear-cli/bin/bear.bat' } else { '.bear/tools/bear-cli/bin/bear' })
    $checkCommandText = $commandPrefix + ' check --all --project . --blocks ' + $blocksPath + ' --collect=all --agent'
    $checkResult = Invoke-BearCommand 'check' $checkCommandText $bearCommand @('check', '--all', '--project', '.', '--blocks', $blocksPath, '--collect=all', '--agent')
    $checkClasses = Get-CheckClasses $checkResult.exitCode $checkResult.footer.valid

    $baseResolution = Resolve-BaseSha $baseShaOverride $repoRoot
    $prResult = $null
    $prTelemetry = [ordered]@{
        available = $false
        deltas = @()
        governanceSignals = @()
        combinedBoundaryDeltas = @()
        boundaryDeltaIds = @()
        hasDependencyPowerExpansion = $false
    }
    $prStatus = 'not-run'
    $prReason = $null
    $prClasses = @()
    $commands = @($checkCommandText)

    if ($checkResult.exitCode -eq 5) {
        $prReason = 'UNEXPECTED_CHECK_EXIT'
    } elseif ($checkResult.exitCode -in @(2, 64, 70, 74)) {
        $prReason = 'CHECK_PRECONDITION_FAILURE'
    } elseif (-not $baseResolution.resolved) {
        $prReason = 'BASE_UNRESOLVED'
    } else {
        $prCommandText = $commandPrefix + ' pr-check --all --project . --base ' + $baseResolution.value + ' --blocks ' + $blocksPath + ' --collect=all --agent'
        $commands += $prCommandText
        $prResult = Invoke-BearCommand 'pr-check' $prCommandText $bearCommand @('pr-check', '--all', '--project', '.', '--base', $baseResolution.value, '--blocks', $blocksPath, '--collect=all', '--agent')
        $prTelemetry = Get-PrTelemetry $prResult.agentJson
        $prClasses = Get-PrClasses $prResult.exitCode $prResult.footer.valid $prTelemetry
        $prStatus = 'ran'
    }

    $allowEvaluation = Evaluate-Allow $mode $prResult $prTelemetry $baseResolution.value $allowFilePath
    $allowEntryCandidate = Get-AllowEntryCandidate $mode $prResult $prTelemetry $baseResolution.value

    $decision = 'pass'
    if ($checkResult.exitCode -in @(2, 5, 64, 70, 74)) {
        $decision = 'fail'
    } elseif (-not $baseResolution.resolved) {
        $decision = 'fail'
    } elseif ($null -eq $prResult) {
        $decision = 'fail'
    } elseif ($mode -eq 'observe') {
        if ($prResult.exitCode -in @(2, 64, 70, 74)) {
            $decision = 'fail'
        }
    } elseif ($checkResult.exitCode -ne 0) {
        $decision = 'fail'
    } elseif ($prResult.exitCode -eq 0) {
        $decision = 'pass'
    } elseif ($prResult.exitCode -eq 5 -and $allowEvaluation.status -eq 'matched') {
        $decision = 'allowed-expansion'
    } else {
        $decision = 'fail'
    }

    $checkReport = [ordered]@{
        status = 'ran'
        exitCode = $checkResult.exitCode
        code = $checkResult.footer.code
        path = $checkResult.footer.path
        remediation = $checkResult.footer.remediation
        classes = @($checkClasses)
    }
    $prReport = if ($prStatus -eq 'ran') {
        [ordered]@{
            status = 'ran'
            reason = $null
            exitCode = $prResult.exitCode
            code = $prResult.footer.code
            path = $prResult.footer.path
            remediation = $prResult.footer.remediation
            classes = @($prClasses)
            allowEntryCandidate = $allowEntryCandidate.value
            deltas = @($prTelemetry.deltas)
            governanceSignals = @($prTelemetry.governanceSignals)
        }
    } else {
        [ordered]@{
            status = 'not-run'
            reason = $prReason
            exitCode = $null
            code = $null
            path = $null
            remediation = $null
            classes = @()
            allowEntryCandidate = $null
            deltas = @()
            governanceSignals = @()
        }
    }

    $report = [ordered]@{
        schemaVersion = 'bear.ci.governance.v1'
        mode = $mode
        resolvedBaseSha = $(if ($baseResolution.resolved) { $baseResolution.value } else { $null })
        commands = @($commands)
        bearRaw = [ordered]@{
            checkAgentJson = $checkResult.agentJson
            prCheckAgentJson = $(if ($null -ne $prResult) { $prResult.agentJson } else { $null })
            checkStdoutHash = $checkResult.stdoutHash
            checkStderrHash = $checkResult.stderrHash
            prCheckStdoutHash = $(if ($null -ne $prResult) { $prResult.stdoutHash } else { $null })
            prCheckStderrHash = $(if ($null -ne $prResult) { $prResult.stderrHash } else { $null })
        }
        check = $checkReport
        prCheck = $prReport
        allowEvaluation = [ordered]@{
            status = $allowEvaluation.status
            reason = $allowEvaluation.reason
            observedDeltaIds = @($allowEvaluation.observedDeltaIds)
        }
        decision = $decision
    }
    $reportJson = $report | ConvertTo-Json -Depth 12 -Compress
    Set-Content -Path $reportPath -Value $reportJson
    $summaryMarkdown = New-MarkdownSummary $mode $decision $baseResolution $checkReport $prReport $prTelemetry.combinedBoundaryDeltas $allowEntryCandidate
    Write-MarkdownSummary $summaryPath $summaryMarkdown

    $baseDisplay = if ($baseResolution.resolved) { $baseResolution.value } else { '<unresolved>' }
    $checkCodeDisplay = Get-CodeDisplay $checkResult.footer.code
    $checkClassesDisplay = Get-ClassesDisplay $checkClasses
    Write-Output ('MODE=' + $mode + ' DECISION=' + $decision + ' BASE=' + $baseDisplay)
    Write-Output ('CHECK exit=' + $checkResult.exitCode + ' code=' + $checkCodeDisplay + ' classes=' + $checkClassesDisplay)
    if ($prStatus -eq 'ran') {
        $prCodeDisplay = Get-CodeDisplay $prResult.footer.code
        $prClassesDisplay = Get-ClassesDisplay $prClasses
        Write-Output ('PR-CHECK exit=' + $prResult.exitCode + ' code=' + $prCodeDisplay + ' classes=' + $prClassesDisplay)
        if ($allowEntryCandidate.status -eq 'available') {
            Write-Output 'ALLOW_ENTRY_CANDIDATE:'
            Write-Output (Format-AllowEntryCandidateJson $allowEntryCandidate.value $false)
        } elseif ($allowEntryCandidate.status -eq 'unavailable') {
            Write-Output 'ALLOW_ENTRY_CANDIDATE: UNAVAILABLE'
        }
    } else {
        Write-Output ('PR-CHECK NOT_RUN: ' + $prReason)
    }

    if ($decision -eq 'fail') {
        exit 1
    }
    exit 0
} finally {
    if ($pushSucceeded) {
        Pop-Location
    }
    if (Test-Path $script:tempDir) {
        Remove-Item -Recurse -Force $script:tempDir
    }
}






