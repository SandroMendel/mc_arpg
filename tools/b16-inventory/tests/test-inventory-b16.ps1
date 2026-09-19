[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$tool = Join-Path $repo 'tools/b16-inventory/scripts/inventory-b16.ps1'
$fixtureValid = Join-Path $repo 'tools/b16-inventory/fixtures/manifest-fixture-valid-v1.json'
$fixtureGap = Join-Path $repo 'tools/b16-inventory/fixtures/manifest-fixture-gap-v1.json'
$fixtureUncovered = Join-Path $repo 'tools/b16-inventory/fixtures/manifest-fixture-uncovered-numeric-v1.json'
$fixtureStatementWindow = Join-Path $repo 'tools/b16-inventory/fixtures/manifest-fixture-statement-window-v1.json'
$fixtureInvalidSemantics = Join-Path $repo 'tools/b16-inventory/fixtures/manifest-fixture-invalid-semantics-v1.json'
$fixtureInvalidFields = Join-Path $repo 'tools/b16-inventory/fixtures/manifest-fixture-invalid-fields-v1.json'
$fixtureInvalidSourcePath = Join-Path $repo 'tools/b16-inventory/fixtures/manifest-fixture-invalid-source-path-v1.json'
$fixtureInvalidYamlPath = Join-Path $repo 'tools/b16-inventory/fixtures/manifest-fixture-invalid-yaml-path-v1.json'
$fixtureDuplicateYaml = Join-Path $repo 'tools/b16-inventory/fixtures/manifest-fixture-duplicate-yaml-v1.json'
$manifest = Join-Path $repo 'tools/b16-inventory/manifest/inventory-manifest-v1.json'
$tempRoot = [System.IO.Directory]::CreateTempSubdirectory('b16-inventory-test-').FullName
$astTempRoot = [System.IO.Path]::GetTempPath()
$astTempBefore = @(Get-ChildItem -LiteralPath $astTempRoot -Directory -Filter 'b16-inventory-ast-*' -Force -ErrorAction SilentlyContinue | ForEach-Object FullName)

function Assert([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw "ASSERTION FAILED: $Message" }
}

function Try-NewExternalReparsePoint([string] $Path, [string] $Target) {
    foreach ($kind in @('SymbolicLink', 'Junction')) {
        try {
            New-Item -ItemType $kind -Path $Path -Target $Target -ErrorAction Stop | Out-Null
            return $kind
        } catch {
            if (Test-Path -LiteralPath $Path) {
                Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
            }
        }
    }
    return $null
}

try {
    $syntax = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($tool, [ref]$syntax, [ref]$errors) | Out-Null
    Assert ($errors.Count -eq 0) 'PowerShell syntax must parse'

    $valid1 = Join-Path $tempRoot 'valid-1'
    & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $fixtureValid -OutputDirectory $valid1 | Out-Null
    Assert ($LASTEXITCODE -eq 0) 'positive fixture must pass'
    $validReport = Get-Content -Raw (Join-Path $valid1 'b16-inventory.json') | ConvertFrom-Json
    Assert ($validReport.status -eq 'VALID') 'positive fixture status must be VALID'
    Assert ($validReport.coverage.gaps -eq 0) 'positive fixture must have no GAP'

    $valid2 = Join-Path $tempRoot 'valid-2'
    & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $fixtureValid -OutputDirectory $valid2 | Out-Null
    Assert ($LASTEXITCODE -eq 0) 'second positive fixture must pass'
    foreach ($name in @('b16-inventory.json','b16-inventory.csv','b16-inventory.md')) {
        $left = [IO.File]::ReadAllBytes((Join-Path $valid1 $name))
        $right = [IO.File]::ReadAllBytes((Join-Path $valid2 $name))
        Assert ([Linq.Enumerable]::SequenceEqual($left, $right)) "deterministic output for $name"
        Assert ($left[0] -ne 0xEF -or $left[1] -ne 0xBB -or $left[2] -ne 0xBF) "UTF-8 without BOM for $name"
        Assert (-not ([Text.Encoding]::UTF8.GetString($left) -match "`r`n")) "LF-only output for $name"
    }

    $gap = Join-Path $tempRoot 'gap'
    & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $fixtureGap -OutputDirectory $gap | Out-Null
    Assert ($LASTEXITCODE -eq 1) 'changed fixture must fail with exit 1'
    $gapReport = Get-Content -Raw (Join-Path $gap 'b16-inventory.json') | ConvertFrom-Json
    Assert ($gapReport.status -eq 'GAP') 'changed fixture must be GAP'
    Assert (@($gapReport.candidates | Where-Object scanStatus -eq 'GAP').Count -ge 1) 'changed fixture must expose GAP row'

    $uncovered = Join-Path $tempRoot 'uncovered-numeric'
    & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $fixtureUncovered -OutputDirectory $uncovered 2>$null | Out-Null
    Assert ($LASTEXITCODE -eq 1) 'uncovered numeric literal must fail with exit 1'
    $uncoveredReport = Get-Content -Raw (Join-Path $uncovered 'b16-inventory.json') | ConvertFrom-Json
    $uncoveredGaps = @($uncoveredReport.candidates | Where-Object {
        $_.id -like 'unmapped:sample-defaults:*' -and $_.semanticCheck -eq 'javac-tree-ast'
    })
    Assert ($uncoveredGaps.Count -eq 2) 'uncovered decimal and hexadecimal literals must be explicit GAPs'
    Assert (@($uncoveredGaps | Where-Object value -eq '0x10').Count -eq 1) 'hexadecimal literal must be preserved in the GAP value'
    Assert (@($uncoveredReport.candidates | Where-Object { $_.value -eq '99.0' -or $_.value -eq '88.0' }).Count -eq 0) 'strings and comments must not become AST numeric literals'

    $statementWindow = Join-Path $tempRoot 'statement-window'
    & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $fixtureStatementWindow -OutputDirectory $statementWindow 2>$null | Out-Null
    Assert ($LASTEXITCODE -eq 1) 'earlier literal in an unbounded statement must fail as an explicit GAP'
    $statementReport = Get-Content -Raw (Join-Path $statementWindow 'b16-inventory.json') | ConvertFrom-Json
    Assert (@($statementReport.candidates | Where-Object { $_.id -like 'unmapped:sample-defaults:*' -and $_.value -eq '7.0' }).Count -eq 1) 'earlier literal in the same statement must be discovered'

    foreach ($invalid in @(
        @{ Path = $fixtureInvalidSemantics; Name = 'invalid semantic status' },
        @{ Path = $fixtureInvalidFields; Name = 'unknown or missing schema fields' },
        @{ Path = $fixtureInvalidSourcePath; Name = 'source path traversal' },
        @{ Path = $fixtureInvalidYamlPath; Name = 'absolute YAML path' }
    )) {
        $invalidOutput = Join-Path $tempRoot (($invalid.Name -replace '[^A-Za-z0-9-]', '-'))
        & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $invalid.Path -OutputDirectory $invalidOutput 2>$null | Out-Null
        Assert ($LASTEXITCODE -eq 2) "$($invalid.Name) must fail fast with exit 2"
    }

    $duplicateYamlOutput = Join-Path $tempRoot 'duplicate-yaml'
    & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $fixtureDuplicateYaml -OutputDirectory $duplicateYamlOutput 2>$null | Out-Null
    Assert ($LASTEXITCODE -eq 2) 'duplicate YAML paths must fail fast with exit 2'
    Assert (-not [System.IO.Directory]::Exists($duplicateYamlOutput)) 'duplicate YAML paths create no report'

    $reparseRoot = Join-Path $repo ('.b16-inventory-reparse-test-' + [guid]::NewGuid().ToString('N'))
    $reparseTarget = [System.IO.Directory]::CreateTempSubdirectory('b16-inventory-reparse-target-').FullName
    $reparseLink = Join-Path $reparseRoot 'source'
    try {
        New-Item -ItemType Directory -Path $reparseRoot | Out-Null
        [IO.File]::WriteAllText((Join-Path $reparseTarget 'Sample.java'), 'final class Sample { static int VALUE = 7; }')
        $reparseKind = Try-NewExternalReparsePoint $reparseLink $reparseTarget
        if ($null -eq $reparseKind) {
            Write-Output 'B16 inventory reparse-point test: SKIP (junction/symlink creation is not permitted)'
        } else {
            $reparseManifest = Join-Path $tempRoot 'manifest-reparse-point.json'
            $reparseRelativeRoot = (Join-Path (Split-Path -Leaf $reparseRoot) 'source') -replace '\\', '/'
            $reparseDefinition = [ordered]@{
                inventoryVersion = 'b16-inventory-v1'
                schemaVersion = 1
                generatedFrom = 'server-free reparse-point negative test'
                semanticMethod = 'test fixture'
                sourceRoots = @($reparseRelativeRoot)
                scanRegions = @()
                candidates = @([ordered]@{
                    id = 'reparse-point-test'
                    sourceFile = "$reparseRelativeRoot/Sample.java"
                    sourcePattern = 'static int VALUE = (?<value>\d+)'
                    valueGroup = 'value'
                    expectedValue = 7
                    unit = 'count'
                    category = 'ALGORITHM_CONSTANT'
                    scanStatus = 'EXCEPTION'
                    semanticCheck = 'exact-numeric'
                    reason = 'negative path-security fixture'
                })
            }
            [IO.File]::WriteAllText($reparseManifest, ($reparseDefinition | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
            $reparseOutput = Join-Path $tempRoot 'reparse-point'
            & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $reparseManifest -OutputDirectory $reparseOutput 2>$null | Out-Null
            Assert ($LASTEXITCODE -eq 2) 'external reparse point must fail fast with exit 2'
            Assert (-not [System.IO.Directory]::Exists($reparseOutput)) 'external reparse point must create no report'
            Write-Output ('B16 inventory reparse-point test: PASS ({0})' -f $reparseKind)
        }
    } finally {
        if (Test-Path -LiteralPath $reparseLink) { Remove-Item -LiteralPath $reparseLink -Force -ErrorAction SilentlyContinue }
        if (Test-Path -LiteralPath $reparseRoot) { Remove-Item -LiteralPath $reparseRoot -Recurse -Force -ErrorAction SilentlyContinue }
        if (Test-Path -LiteralPath $reparseTarget) { Remove-Item -LiteralPath $reparseTarget -Recurse -Force -ErrorAction SilentlyContinue }
    }

    $current = Join-Path $tempRoot 'current'
    & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $manifest -OutputDirectory $current | Out-Null
    Assert ($LASTEXITCODE -eq 0) 'current manifest must pass with resolved T002 candidates'
    $currentReport = Get-Content -Raw (Join-Path $current 'b16-inventory.json') | ConvertFrom-Json
    Assert ($currentReport.status -eq 'VALID') 'current manifest status must be VALID'
    Assert (@($currentReport.candidates | Where-Object scanStatus -eq 'MIGRATABLE').Count -gt 0) 'current report must contain migratable rows'
    Assert (@($currentReport.candidates | Where-Object scanStatus -eq 'EXCEPTION').Count -gt 0) 'current report must contain justified exceptions'
    $currentGaps = @($currentReport.candidates | Where-Object scanStatus -eq 'GAP')
    Assert ($currentGaps.Count -eq 0) 'current report must contain zero GAPs'
    foreach ($expectedException in @(
        @{ Id = 'mobs.admin-spawn-limit'; Category = 'PROTECTION_BOUNDARY'; Value = '20'; Unit = 'entities-simultaneously-registered-server-wide'; YamlFile = 'rpg-content/src/main/resources/mobs.yml'; YamlPath = 'admin-spawn-limit' },
        @{ Id = 'exception.ability.behind-angle'; Category = 'ALGORITHM_CONSTANT'; Value = '90.0'; Unit = 'degrees'; YamlFile = $null; YamlPath = $null },
        @{ Id = 'exception.ability.projectile-speed'; Category = 'PLATFORM_PHYSICS'; Value = '1.6'; Unit = 'blocks-per-tick'; YamlFile = $null; YamlPath = $null }
    )) {
        $matches = @($currentReport.candidates | Where-Object { $_.id -eq $expectedException.Id })
        Assert ($matches.Count -eq 1) "$($expectedException.Id) must be present exactly once"
        $candidate = $matches[0]
        Assert ($candidate.scanStatus -eq 'EXCEPTION') "$($expectedException.Id) must be EXCEPTION"
        Assert ($candidate.category -eq $expectedException.Category) "$($expectedException.Id) category must be $($expectedException.Category)"
        Assert ($candidate.value -eq $expectedException.Value) "$($expectedException.Id) value must be $($expectedException.Value)"
        Assert ($candidate.unit -eq $expectedException.Unit) "$($expectedException.Id) unit must be $($expectedException.Unit)"
        Assert ($candidate.yamlFile -eq $expectedException.YamlFile) "$($expectedException.Id) YAML file ownership must match"
        Assert ($candidate.yamlPath -eq $expectedException.YamlPath) "$($expectedException.Id) YAML path ownership must match"
    }
    Assert (@($currentReport.candidates | Where-Object { $_.id -like 'region:*' -or $_.id -like 'unmapped:*' }).Count -eq 0) 'technical region/unmapped GAPs must be absent'
    Assert (@($currentReport.candidates | Where-Object { $_.scanStatus -eq 'GAP' -and $_.category -eq 'PROTECTION_BOUNDARY' }).Count -eq 0) 'known EXCEPTION comparisons must not become GAP'
    foreach ($wearId in @(
        'items.wear.threshold',
        'items.wear.floor',
        'items.wear.per-damage-taken',
        'items.wear.per-damage-dealt',
        'items.wear.per-death',
        'items.wear.death-factor-min',
        'items.wear.warn-at-50',
        'items.wear.warn-at-25',
        'items.wear.warn-at-10'
    )) {
        Assert (@($currentReport.candidates | Where-Object { $_.id -eq $wearId -and $_.scanStatus -eq 'MIGRATABLE' }).Count -eq 1) "$wearId must be mapped as MIGRATABLE"
    }

    & pwsh -NoProfile -File $tool -RepoRoot $repo -Manifest $fixtureValid -OutputDirectory $valid1 | Out-Null
    Assert ($LASTEXITCODE -ne 0) 'existing output directory must be rejected'
    $astTempAfter = @(Get-ChildItem -LiteralPath $astTempRoot -Directory -Filter 'b16-inventory-ast-*' -Force -ErrorAction SilentlyContinue | ForEach-Object FullName)
    Assert (@($astTempAfter | Where-Object { $astTempBefore -notcontains $_ }).Count -eq 0) 'AST temp directories must be cleaned up'

    Write-Output 'B16 inventory tests: PASS'
} finally {
    if ([System.IO.Directory]::Exists($tempRoot)) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}
