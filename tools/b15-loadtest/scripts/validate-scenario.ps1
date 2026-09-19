[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ScenarioPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ScenarioPath -PathType Leaf)) {
    throw "Scenario file does not exist: $ScenarioPath"
}

$text = Get-Content -LiteralPath $ScenarioPath -Raw
$errors = [System.Collections.Generic.List[string]]::new()

function Get-Scalar([string] $Pattern, [string] $Name) {
    $match = [regex]::Match($text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if (-not $match.Success) {
        $errors.Add("missing $Name")
        return $null
    }
    return $match.Groups[1].Value.Trim().Trim('"')
}

function Require-Text([string] $Pattern, [string] $Name) {
    if ($text -notmatch $Pattern) { $errors.Add("missing or invalid $Name") }
}

$id = Get-Scalar '^id:\s*(.+)$' 'id'
$revisionText = Get-Scalar '^revision:\s*(\d+)$' 'revision'
$playersText = Get-Scalar '^\s*simulated-players:\s*(\d+)$' 'simulated-players'
$mobsText = Get-Scalar '^\s*custom-mobs:\s*(\d+)$' 'custom-mobs'
$warmupText = Get-Scalar '^\s*warmup-minutes:\s*(\d+)$' 'warmup-minutes'
$measurementText = Get-Scalar '^\s*measurement-minutes:\s*(\d+)$' 'measurement-minutes'
$tpsText = Get-Scalar '^\s*mean-tps-min:\s*([0-9.]+)$' 'mean-tps-min'
$p95Text = Get-Scalar '^\s*mspt-p95-max:\s*([0-9.]+)$' 'mspt-p95-max'
$p99Text = Get-Scalar '^\s*mspt-p99-max:\s*([0-9.]+)$' 'mspt-p99-max'

if ($id -and $id -ne 'b15-full') { $errors.Add('id must be b15-full') }
if ($revisionText -and [int]$revisionText -lt 1) { $errors.Add('revision must be positive') }
if ($playersText -and [int]$playersText -lt 150) { $errors.Add('simulated-players must be at least 150') }
if ($mobsText -and [int]$mobsText -lt 800) { $errors.Add('custom-mobs must be at least 800') }
if ($warmupText -and [int]$warmupText -lt 15) { $errors.Add('warmup-minutes must be at least 15') }
if ($measurementText -and [int]$measurementText -lt 30) { $errors.Add('measurement-minutes must be at least 30') }
if ($tpsText -and [double]$tpsText -lt 19.5) { $errors.Add('mean-tps-min must be at least 19.5') }
if ($p95Text -and [double]$p95Text -gt 40.0) { $errors.Add('mspt-p95-max must not exceed 40.0') }
if ($p99Text -and [double]$p99Text -gt 50.0) { $errors.Add('mspt-p99-max must not exceed 50.0') }

$regionCount = ([regex]::Matches($text, '(?m)^\s*-\s+region-\d+\s*$')).Count
if ($regionCount -lt 6) { $errors.Add("regions must contain at least six entries (found $regionCount)") }
foreach ($workload in @('movement', 'combat', 'coin-drops', 'hordes')) {
    Require-Text "(?m)^\s*-\s+$([regex]::Escape($workload))\s*$" "workload $workload"
}
Require-Text '(?m)^\s*region-distribution:\s*even\s*$' 'even region distribution'
foreach ($action in @('movement', 'combat', 'coin-drops', 'hordes')) {
    Require-Text "(?m)^\s*$([regex]::Escape($action)):\s*\S+" "action $action"
}

if ($errors.Count -gt 0) {
    [ordered]@{ valid = $false; errors = @($errors) } | ConvertTo-Json -Compress
    exit 1
}

[ordered]@{
    valid = $true
    id = $id
    revision = [int]$revisionText
    players = [int]$playersText
    customMobs = [int]$mobsText
    regions = $regionCount
    warmupMinutes = [int]$warmupText
    measurementMinutes = [int]$measurementText
    thresholds = [ordered]@{
        meanTps = [double]$tpsText
        msptP95 = [double]$p95Text
        msptP99 = [double]$p99Text
    }
} | ConvertTo-Json -Compress
