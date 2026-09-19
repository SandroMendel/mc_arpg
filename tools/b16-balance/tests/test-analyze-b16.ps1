[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "assertion failed: $Message" }
}

function Assert-Throws([scriptblock] $Action, [string] $ExpectedPart) {
    try { & $Action } catch {
        Assert-True ($_.Exception.Message -like "*$ExpectedPart*") "expected '$ExpectedPart', got '$($_.Exception.Message)'"
        return
    }
    throw "assertion failed: expected an error containing '$ExpectedPart'"
}

function Copy-Config([string] $Source, [string] $Target) {
    [System.IO.Directory]::CreateDirectory($Target) | Out-Null
    Get-ChildItem -LiteralPath $Source -Filter '*.yml' | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $Target $_.Name) }
}

function Assert-ReportEncoding([string] $Path) {
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    Assert-True (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)) "$Path has no UTF-8 BOM"
    Assert-True (-not ($bytes -contains [byte]0x0D)) "$Path contains LF-only line endings"
}

$root = [System.IO.Directory]::CreateTempSubdirectory('b16-balance-test-')
try {
    $repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
    $script = Join-Path $PSScriptRoot '..\scripts\analyze-b16.ps1'
    $fixture = Join-Path $PSScriptRoot '..\fixtures\current-baseline-v1.yml'
    $configSource = Join-Path $repo 'rpg-content\src\main\resources'
    $config = Join-Path $root.FullName 'config'
    Copy-Config $configSource $config

    $out1 = Join-Path $root.FullName 'report-1'
    $out2 = Join-Path $root.FullName 'report-2'
    & $script -ConfigRoot $config -ScenarioPath $fixture -OutputDirectory $out1 | Out-Null
    & $script -ConfigRoot $config -ScenarioPath $fixture -OutputDirectory $out2 | Out-Null
    foreach ($name in @('b16-balance.csv', 'b16-balance.json', 'b16-balance.md')) {
        $a = [System.IO.File]::ReadAllBytes((Join-Path $out1 $name))
        $b = [System.IO.File]::ReadAllBytes((Join-Path $out2 $name))
        Assert-True ([System.Linq.Enumerable]::SequenceEqual($a, $b)) "$name is byte-identical across repeated runs"
        Assert-ReportEncoding (Join-Path $out1 $name)
    }
    Assert-Throws { & $script -ConfigRoot $config -ScenarioPath $fixture -OutputDirectory $out1 } 'refusing to reuse an existing output path'
    $existingOutput = Join-Path $root.FullName 'existing-output'
    [System.IO.Directory]::CreateDirectory($existingOutput) | Out-Null
    Assert-Throws { & $script -ConfigRoot $config -ScenarioPath $fixture -OutputDirectory $existingOutput } 'refusing to reuse an existing output path'
    Assert-True (@(Get-ChildItem -LiteralPath $existingOutput -Force).Count -eq 0) 'existing output directory remains untouched'
    foreach ($protectedOutput in @($config, (Join-Path $repo 'rpg-plugin'), (Join-Path $repo 'rpg-content\src\main\resources'))) {
        Assert-Throws { & $script -ConfigRoot $config -ScenarioPath $fixture -OutputDirectory $protectedOutput } 'output directory is protected'
    }
    $json = Get-Content -LiteralPath (Join-Path $out1 'b16-balance.json') -Raw | ConvertFrom-Json
    $json2 = Get-Content -LiteralPath (Join-Path $out2 'b16-balance.json') -Raw | ConvertFrom-Json
    Assert-True ($json.status -eq 'VALID') 'valid report is explicitly marked VALID'
    Assert-True ($json.schemaVersion -eq 1) 'report schema version is present'
    Assert-True ($json.fixtureVersion -eq 1) 'fixture version is present'
    Assert-True ($json.baselineVersion -eq 1) 'baseline version is present'
    Assert-True ($json.scenarioId -eq 'b16-current-baseline-v1') 'stable scenario ID is present'
    Assert-True ($json.scenarioName -eq 'Current B16 runtime baseline') 'scenario name is present'
    Assert-True ($json.baselineId -match '^b16-baseline-v1-[0-9a-f]{16}$') 'deterministic baseline ID is present'
    Assert-True ($json.sourceHash -match '^sha256:[0-9a-f]{64}$') 'source hash is present'
    $expectedSourceFiles = @('classes.yml', 'abilities.yml', 'progression.yml', 'combat.yml', 'zones.yml', 'mobs.yml', 'items.yml', 'currency.yml', 'stats.yml')
    Assert-True ((@($json.sourceFiles) -join '|') -eq ($expectedSourceFiles -join '|')) 'report lists exactly the nine ordered source files'
    Assert-True ($json.baselineId -eq $json2.baselineId -and $json.sourceHash -eq $json2.sourceHash) 'metadata is stable across repeated runs'
    Assert-True ($json.usedIds.classes -contains 'WARRIOR') 'used class IDs are present'
    Assert-True ($json.usedIds.mobs -contains 'greenfields.rotling') 'used mob IDs are present'
    Assert-True ($json.baselineValues.Count -eq 5) 'baseline values include units and current values'
    Assert-True ((Get-Content -LiteralPath (Join-Path $out1 'b16-balance.md') -Raw) -match 'CSV/Spreadsheet is output only') 'markdown states output-only boundary'

    $invalidYaml = Join-Path $root.FullName 'invalid-yaml.yml'
    [System.IO.File]::WriteAllText($invalidYaml, "schemaVersion: 1`nscenario:`n  id: [broken`n", (New-Object System.Text.UTF8Encoding($false)))
    Assert-Throws { & $script -ConfigRoot $config -ScenarioPath $invalidYaml -OutputDirectory (Join-Path $root.FullName 'invalid-yaml-out') } 'invalid-yaml.yml'
    Assert-True (-not [System.IO.Directory]::Exists((Join-Path $root.FullName 'invalid-yaml-out'))) 'invalid YAML creates no output directory'

    $wrongVersion = Join-Path $root.FullName 'wrong-version.yml'
    (Get-Content -LiteralPath $fixture -Raw).Replace('schemaVersion: 1', 'schemaVersion: 2') | Set-Content -LiteralPath $wrongVersion -NoNewline
    Assert-Throws { & $script -ConfigRoot $config -ScenarioPath $wrongVersion -OutputDirectory (Join-Path $root.FullName 'wrong-version-out') } 'expected exact schemaVersion 1'
    Assert-True (-not [System.IO.Directory]::Exists((Join-Path $root.FullName 'wrong-version-out'))) 'wrong schema creates no output directory'

    $wrongFixtureVersion = Join-Path $root.FullName 'wrong-fixture-version.yml'
    (Get-Content -LiteralPath $fixture -Raw).Replace('fixtureVersion: 1', 'fixtureVersion: 2') | Set-Content -LiteralPath $wrongFixtureVersion -NoNewline
    $wrongFixtureVersionOut = Join-Path $root.FullName 'wrong-fixture-version-out'
    Assert-Throws { & $script -ConfigRoot $config -ScenarioPath $wrongFixtureVersion -OutputDirectory $wrongFixtureVersionOut } 'expected exact fixtureVersion 1'
    Assert-True (-not [System.IO.Directory]::Exists($wrongFixtureVersionOut)) 'wrong fixture version creates no output directory'

    $missingScenario = Join-Path $root.FullName 'missing-scenario.yml'
    (Get-Content -LiteralPath $fixture -Raw).Replace('  id: b16-current-baseline-v1', '  title-only: no-id') | Set-Content -LiteralPath $missingScenario -NoNewline
    $missingScenarioOut = Join-Path $root.FullName 'missing-scenario-out'
    Assert-Throws { & $script -ConfigRoot $config -ScenarioPath $missingScenario -OutputDirectory $missingScenarioOut } 'missing required key ''id'''
    Assert-True (-not [System.IO.Directory]::Exists($missingScenarioOut)) 'missing scenario ID creates no output directory'

    $unknownId = Join-Path $root.FullName 'unknown-id.yml'
    (Get-Content -LiteralPath $fixture -Raw).Replace('greenfields.rotling', 'unknown.mob') | Set-Content -LiteralPath $unknownId -NoNewline
    $unknownIdOut = Join-Path $root.FullName 'unknown-id-out'
    Assert-Throws { & $script -ConfigRoot $config -ScenarioPath $unknownId -OutputDirectory $unknownIdOut } "unknown mob ID 'unknown.mob'"
    Assert-True (-not [System.IO.Directory]::Exists($unknownIdOut)) 'unknown mob ID creates no output directory'

    $nonFiniteConfig = Join-Path $root.FullName 'non-finite-config'
    Copy-Config $configSource $nonFiniteConfig
    $statsPath = Join-Path $nonFiniteConfig 'stats.yml'
    $statsText = [System.IO.File]::ReadAllText($statsPath)
    $statsText = [regex]::Replace($statsText, '(?m)(^  physicalDamage:\r?\n\s+base: )[^\r\n]+', '${1}NaN', 1)
    [System.IO.File]::WriteAllText($statsPath, $statsText, (New-Object System.Text.UTF8Encoding($false)))
    $nonFiniteOut = Join-Path $root.FullName 'non-finite-out'
    Assert-Throws { & $script -ConfigRoot $nonFiniteConfig -ScenarioPath $fixture -OutputDirectory $nonFiniteOut } 'stats.yml:attributes.physicalDamage.base'
    Assert-Throws { & $script -ConfigRoot $nonFiniteConfig -ScenarioPath $fixture -OutputDirectory $nonFiniteOut } 'NaN and +/-Infinity are not allowed'
    Assert-True (-not [System.IO.Directory]::Exists($nonFiniteOut)) 'non-finite input creates no output directory'

    $scriptText = (Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '..\scripts') -Filter '*.ps1' -File -Recurse | ForEach-Object { [System.IO.File]::ReadAllText($_.FullName) }) -join "`n"
    Assert-True ($scriptText -notmatch '(?i)(Invoke-WebRequest|Invoke-RestMethod|Start-BitsTransfer|WebClient|HttpClient|Bukkit|Paper|ClassLoader)') 'analysis scripts have no network/server/classloader dependency'
    Assert-True ($scriptText -notmatch '(?i)(Import-Csv|ConvertFrom-Csv|Import-Excel|Import-PowerShellDataFile|Start-Process|server\.jar|plugins\\)') 'analysis scripts have no spreadsheet or runtime re-import path'
    Assert-True ($scriptText -notmatch '(?im)^.*rpg-plugin.*(WriteAllText|Set-Content|Out-File|Copy-Item)') 'analysis scripts have no plugin runtime write path'
    $readme = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\README.md') -Raw
    Assert-True ($readme -match 'kein.*Runtime-Rückimport') 'README documents no runtime re-import'
    Assert-True ($readme -match 'automatischen Config-Schreibpfad') 'README documents no automatic config writes'

    Write-Output 'B16 balance analysis tests: PASS'
} finally {
    if ($null -ne $root -and [System.IO.Directory]::Exists($root.FullName)) { Remove-Item -LiteralPath $root.FullName -Recurse -Force }
}
