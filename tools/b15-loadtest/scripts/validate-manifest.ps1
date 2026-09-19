[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ManifestPath,
    [switch] $CheckArtifacts
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$invalid = [System.Collections.Generic.List[string]]::new()
$failed = [System.Collections.Generic.List[string]]::new()

function Add-Invalid([string] $Message) { $invalid.Add($Message) }
function Add-Failed([string] $Message) { $failed.Add($Message) }
function Has-Property($Object, [string] $Name) {
    return $null -ne $Object -and $null -ne $Object.PSObject.Properties[$Name]
}

if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    [ordered]@{ computedStatus = 'INVALID'; invalid = @("manifest does not exist: $ManifestPath"); failures = @() } | ConvertTo-Json -Compress
    exit 1
}

try {
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
} catch {
    [ordered]@{ computedStatus = 'INVALID'; invalid = @("manifest is not valid JSON: $($_.Exception.Message)"); failures = @() } | ConvertTo-Json -Compress
    exit 1
}

foreach ($required in @('runId', 'status', 'scenario', 'environment', 'hardware', 'artifacts', 'result')) {
    if (-not (Has-Property $manifest $required)) { Add-Invalid "missing top-level field: $required" }
}

if ($invalid.Count -eq 0) {
    if ([string]::IsNullOrWhiteSpace([string]$manifest.runId)) { Add-Invalid 'runId is blank' }
    if (@('RUNNING', 'PASSED', 'FAILED', 'INVALID', 'ABORTED') -notcontains [string]$manifest.status) {
        Add-Invalid "unsupported status: $($manifest.status)"
    }
}

$scenario = $manifest.scenario
foreach ($required in @('id', 'revision', 'players', 'regions', 'customMobs', 'warmupMinutes', 'measurementMinutes', 'workloads')) {
    if (-not (Has-Property $scenario $required)) { Add-Invalid "missing scenario field: $required" }
}
if ((Has-Property $scenario 'id') -and $scenario.id -ne 'b15-full') { Add-Invalid 'scenario.id must be b15-full' }
if ((Has-Property $scenario 'players') -and $scenario.players -lt 150) { Add-Invalid 'scenario.players is below 150' }
if ((Has-Property $scenario 'regions') -and $scenario.regions -lt 6) { Add-Invalid 'scenario.regions is below 6' }
if ((Has-Property $scenario 'customMobs') -and $scenario.customMobs -lt 800) { Add-Invalid 'scenario.customMobs is below 800' }
if ((Has-Property $scenario 'warmupMinutes') -and $scenario.warmupMinutes -lt 15) { Add-Invalid 'scenario.warmupMinutes is below 15' }
if ((Has-Property $scenario 'measurementMinutes') -and $scenario.measurementMinutes -lt 30) { Add-Invalid 'scenario.measurementMinutes is below 30' }
foreach ($workload in @('movement', 'combat', 'coin-drops', 'hordes')) {
    if (-not (@($scenario.workloads) -contains $workload)) { Add-Invalid "scenario is missing workload: $workload" }
}

$result = $manifest.result
foreach ($required in @('meanTps', 'msptP95', 'msptP99', 'peakCustomMobs', 'measurementMinutes', 'activePlayersPeak', 'missingSources', 'metricsFresh', 'restartDetected', 'warmupCompleted', 'firstFailure')) {
    if (-not (Has-Property $result $required)) { Add-Invalid "missing result field: $required" }
}

if ($CheckArtifacts -and (Has-Property $manifest 'artifacts')) {
    $manifestDirectory = Split-Path -Parent (Resolve-Path -LiteralPath $ManifestPath)
    foreach ($artifact in @('metrics', 'report', 'logs')) {
        if (-not (Has-Property $manifest.artifacts $artifact) -or [string]::IsNullOrWhiteSpace([string]$manifest.artifacts.$artifact)) {
            Add-Invalid "missing artifact path: $artifact"
        } else {
            $artifactPath = Join-Path $manifestDirectory ([string]$manifest.artifacts.$artifact)
            if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) { Add-Invalid "artifact does not exist: $artifact" }
        }
    }
}

$terminal = @('PASSED', 'FAILED', 'INVALID', 'ABORTED') -contains [string]$manifest.status
if ($terminal) {
    if ($null -eq $result.meanTps -or $null -eq $result.msptP95 -or $null -eq $result.msptP99 -or $null -eq $result.peakCustomMobs) {
        Add-Invalid 'terminal result is missing TPS/MSPT/mob data'
    }
    if ($null -eq $result.measurementMinutes -or $result.measurementMinutes -lt 30) { Add-Failed 'measurement duration is below 30 minutes' }
    if ($null -eq $result.activePlayersPeak -or $result.activePlayersPeak -lt 150) { Add-Failed 'active player peak is below 150' }
    if ($null -eq $result.missingSources -or @($result.missingSources).Count -gt 0) { Add-Failed 'one or more expected sources are missing' }
    if ($result.metricsFresh -ne $true) { Add-Invalid 'metrics are stale or freshness was not proven' }
    if ($result.restartDetected -ne $false) { Add-Failed 'server restart detected during measurement' }
    if ($result.warmupCompleted -ne $true) { Add-Failed 'warm-up phase was not completed' }
    if ($null -ne $result.meanTps -and $result.meanTps -lt 19.5) { Add-Failed 'mean TPS is below 19.5' }
    if ($null -ne $result.msptP95 -and $result.msptP95 -ge 40.0) { Add-Failed 'MSPT p95 is not below 40 ms' }
    if ($null -ne $result.msptP99 -and $result.msptP99 -ge 50.0) { Add-Failed 'MSPT p99 is not below 50 ms' }
    if ($null -ne $result.peakCustomMobs -and $result.peakCustomMobs -lt 800) { Add-Failed 'peak Custom-Mobs is below 800' }
}

if ([string]$manifest.status -eq 'PASSED') {
    if ($null -ne $result.firstFailure) { Add-Failed 'PASSED manifest contains a first failure' }
}

if ([string]$manifest.status -eq 'FAILED' -and $null -eq $result.firstFailure) {
    Add-Failed 'FAILED manifest must record firstFailure'
}

$computed = if ($invalid.Count -gt 0) { 'INVALID' } elseif ($failed.Count -gt 0) { 'FAILED' } elseif ([string]$manifest.status -eq 'RUNNING') { 'RUNNING' } else { [string]$manifest.status }
[ordered]@{
    computedStatus = $computed
    invalid = @($invalid)
    failures = @($failed)
} | ConvertTo-Json -Depth 8 -Compress
if ($computed -eq 'INVALID' -or $computed -eq 'FAILED') { exit 1 }
