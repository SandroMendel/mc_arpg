[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ServerRoot,
    [string] $ScenarioPath = (Join-Path $PSScriptRoot '..\scenarios\b15-full.yaml'),
    [string] $OutputRoot = (Join-Path $PSScriptRoot '..\runs'),
    [string] $PlayerToolPath,
    [string] $ResultInputPath,
    [string] $MetricsPath,
    [int] $Port = 25565,
    [int] $ReadinessTimeoutSeconds = 120,
    [switch] $SkipPlayerTool
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$validation = & (Join-Path $PSScriptRoot 'validate-scenario.ps1') -ScenarioPath $ScenarioPath | ConvertFrom-Json
if (-not $validation.valid) { throw "Scenario validation failed: $($validation.errors -join '; ')" }

$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss-fff')
$runDirectory = Join-Path $OutputRoot $runId
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$manifestPath = Join-Path $runDirectory 'run-manifest.json'
Copy-Item -LiteralPath $ScenarioPath -Destination (Join-Path $runDirectory 'scenario.yaml')

function Capture-Command([string] $FilePath, [string[]] $Arguments) {
    try { return (& $FilePath @Arguments 2>&1 | Out-String).Trim() } catch { return "unavailable: $($_.Exception.Message)" }
}

$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
$computer = Get-CimInstance Win32_ComputerSystem
$os = Get-CimInstance Win32_OperatingSystem
$disk = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='$($env:SystemDrive)'"
$javaVersion = Capture-Command 'java' @('-version')
$jvmArguments = if ($env:JAVA_TOOL_OPTIONS) { $env:JAVA_TOOL_OPTIONS } else { '' }
$metricsSource = if ($MetricsPath) { $MetricsPath } else { Join-Path $ServerRoot 'plugins\rpg-plugin\performance\metrics.prom' }

$result = [ordered]@{
    meanTps = $null
    msptP95 = $null
    msptP99 = $null
    peakCustomMobs = $null
    measurementMinutes = $null
    activePlayersPeak = $null
    missingSources = @()
    metricsFresh = $false
    restartDetected = $false
    warmupCompleted = $false
    firstFailure = [ordered]@{ code = 'RESULT_DATA_MISSING'; message = 'The load-tool adapter did not provide result.json.' }
}

$manifest = [ordered]@{
    runId = $runId
    status = 'RUNNING'
    scenario = [ordered]@{
        id = $validation.id
        revision = $validation.revision
        players = $validation.players
        regions = $validation.regions
        customMobs = $validation.customMobs
        warmupMinutes = $validation.warmupMinutes
        measurementMinutes = $validation.measurementMinutes
        workloads = @('movement', 'combat', 'coin-drops', 'hordes')
    }
    environment = [ordered]@{
        minecraft = '26.2'
        paperBuild = '26.2.build.112-stable'
        java = $javaVersion
        tool = 'mc-pilot'
        toolVersion = 'pin-at-run'
    }
    hardware = [ordered]@{
        cpu = [string]$cpu.Name
        cores = "logical=$($computer.NumberOfLogicalProcessors);physical=$($cpu.NumberOfCores)"
        memoryBytes = [string]$computer.TotalPhysicalMemory
        storage = if ($disk) { [string]$disk.Size } else { 'unavailable' }
        os = [string]$os.Caption
        jvmArguments = $jvmArguments
    }
    artifacts = [ordered]@{
        metrics = 'metrics.prom'
        report = 'performance-report.log'
        logs = 'server.log'
    }
    result = $result
}

$manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8

try {
    $ready = $false
    $deadline = (Get-Date).AddSeconds($ReadinessTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $client.Connect('127.0.0.1', $Port)
            $ready = $true
            break
        } catch { Start-Sleep -Seconds 1 } finally { $client.Dispose() }
    }
    if (-not $ready) { throw "Paper readiness check failed on port $Port" }

    if (-not $SkipPlayerTool) {
        if ([string]::IsNullOrWhiteSpace($PlayerToolPath)) { throw 'PlayerToolPath is required unless -SkipPlayerTool is used.' }
        $arguments = @(
            '--scenario', (Resolve-Path -LiteralPath $ScenarioPath).Path,
            '--players', $validation.players,
            '--mobs', $validation.customMobs,
            '--regions', $validation.regions,
            '--warmup-minutes', $validation.warmupMinutes,
            '--measurement-minutes', $validation.measurementMinutes,
            '--output', $runDirectory)
        $toolProcess = Start-Process -FilePath $PlayerToolPath -ArgumentList $arguments -WorkingDirectory (Split-Path -Parent $PlayerToolPath) -PassThru -Wait
        if ($toolProcess.ExitCode -ne 0) { throw "mc-pilot exited with code $($toolProcess.ExitCode)" }
    }

    $candidateResult = if ($ResultInputPath) { $ResultInputPath } else { Join-Path $runDirectory 'result.json' }
    if (Test-Path -LiteralPath $candidateResult -PathType Leaf) {
        $manifest.result = Get-Content -LiteralPath $candidateResult -Raw | ConvertFrom-Json
        $manifest.status = 'PASSED'
    } else {
        $manifest.status = 'INVALID'
    }

    if (Test-Path -LiteralPath $metricsSource -PathType Leaf) { Copy-Item -LiteralPath $metricsSource -Destination (Join-Path $runDirectory 'metrics.prom') }
    $serverLog = Join-Path $ServerRoot 'logs\latest.log'
    if (Test-Path -LiteralPath $serverLog -PathType Leaf) { Copy-Item -LiteralPath $serverLog -Destination (Join-Path $runDirectory 'server.log') }
    $reportSource = Join-Path $ServerRoot 'plugins\rpg-plugin\performance\performance-report.log'
    if (Test-Path -LiteralPath $reportSource -PathType Leaf) { Copy-Item -LiteralPath $reportSource -Destination (Join-Path $runDirectory 'performance-report.log') }

    $manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    $checked = & (Join-Path $PSScriptRoot 'validate-manifest.ps1') -ManifestPath $manifestPath -CheckArtifacts | ConvertFrom-Json
    $manifest.status = $checked.computedStatus
    $manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    $checked | ConvertTo-Json -Depth 12
} catch {
    $manifest.status = 'ABORTED'
    $manifest.result.firstFailure = [ordered]@{ code = 'RUNNER_FAILURE'; message = $_.Exception.Message }
    $manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    throw
}
