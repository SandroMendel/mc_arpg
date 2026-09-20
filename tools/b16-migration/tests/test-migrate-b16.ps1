[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) {
        throw "assertion failed: $Message"
    }
}

function Assert-Throws([scriptblock] $Action, [string] $MessagePart) {
    try {
        & $Action
    } catch {
        Assert-True ($_.Exception.Message -like "*$MessagePart*") "expected '$MessagePart', got '$($_.Exception.Message)'"
        return
    }
    throw "assertion failed: expected an error containing '$MessagePart'"
}

$root = [System.IO.Directory]::CreateTempSubdirectory('b16-migration-test-')
try {
    $script = Join-Path $PSScriptRoot '..\scripts\migrate-b16.ps1'
    $legacy = Join-Path $root.FullName 'legacy\classes.yml'
    $output = Join-Path $root.FullName 'migrated\classes.yml'
    $backup = Join-Path $root.FullName 'backup\classes.yml'
    $second = Join-Path $root.FullName 'migrated-again\classes.yml'
    $legacyText = "classes:`n  warrior:`n    base-health: 100.0`n    level: 60`n"
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($legacy)) | Out-Null
    [System.IO.File]::WriteAllText($legacy, $legacyText, (New-Object System.Text.UTF8Encoding($false)))

    $first = & $script -InputPath $legacy -OutputPath $output -BackupPath $backup
    $migrated = [System.IO.File]::ReadAllText($output)
    Assert-True ($first.status -eq 'MIGRATED') 'legacy source is reported as migrated'
    Assert-True ($migrated.StartsWith("schemaVersion: 1`n")) 'schemaVersion is the new root envelope'
    Assert-True ($migrated.Contains('base-health: 100.0')) 'numeric baseline is retained'
    Assert-True ($migrated.Contains('level: 60')) 'level baseline is retained'
    Assert-True ([System.IO.File]::ReadAllText($legacy) -eq $legacyText) 'source remains byte-identical'
    Assert-True ([System.IO.File]::ReadAllText($backup) -eq $legacyText) 'backup is a source copy'
    Assert-True (@(Get-ChildItem -LiteralPath (Split-Path -Parent $output) -Filter '.b16-migration-*.tmp' -File).Count -eq 0) 'successful migration cleans its temporary output'

    $secondResult = & $script -InputPath $output -OutputPath $second
    Assert-True ($secondResult.status -eq 'UNCHANGED') 'v1 input is an idempotent no-op'
    Assert-True ([System.IO.File]::ReadAllText($second) -eq $migrated) 'repeat output is identical'

    $fixtureRoot = Join-Path $PSScriptRoot '..\fixtures'
    $fixtureOutputRoot = Join-Path $root.FullName 'fixture-output'
    $legacyFixtures = @(Get-ChildItem -LiteralPath (Join-Path $fixtureRoot 'legacy') -Filter '*.yml' -File | Sort-Object Name)
    Assert-True ($legacyFixtures.Count -eq 9) 'all nine canonical legacy fixtures are covered'
    foreach ($fixture in $legacyFixtures) {
        $fixtureOutput = Join-Path $fixtureOutputRoot $fixture.Name
        $sourceBefore = [System.IO.File]::ReadAllBytes($fixture.FullName)
        $fixtureResult = & $script -InputPath $fixture.FullName -OutputPath $fixtureOutput
        $expectedPath = Join-Path $fixtureRoot "expected\$($fixture.Name)"
        Assert-True ([System.IO.File]::Exists($expectedPath)) "$($fixture.Name) has a golden fixture"
        $expected = [System.IO.File]::ReadAllText($expectedPath)
        Assert-True ($fixtureResult.status -eq 'MIGRATED') "$($fixture.Name) fixture is migrated"
        Assert-True ([System.IO.File]::ReadAllText($fixtureOutput) -eq $expected) "$($fixture.Name) matches its golden output"
        Assert-True ([System.Linq.Enumerable]::SequenceEqual($sourceBefore, [System.IO.File]::ReadAllBytes($fixture.FullName))) "$($fixture.Name) source remains byte-identical"
    }

    $existingOutput = Join-Path $root.FullName 'existing.yml'
    [System.IO.File]::WriteAllText($existingOutput, 'do-not-overwrite')
    Assert-Throws { & $script -InputPath $legacy -OutputPath $existingOutput } 'refusing to overwrite existing output'
    Assert-True ([System.IO.File]::ReadAllText($existingOutput) -eq 'do-not-overwrite') 'existing output remains unchanged'

    $faultyOutputParent = Join-Path $root.FullName 'faulty-output-parent'
    [System.IO.File]::WriteAllText($faultyOutputParent, 'not-a-directory')
    $faultyOutput = Join-Path $faultyOutputParent 'classes.yml'
    $faultyOutputFailed = $false
    try {
        & $script -InputPath $legacy -OutputPath $faultyOutput
    } catch {
        $faultyOutputFailed = $true
    }
    Assert-True $faultyOutputFailed 'invalid output parent is rejected without server access'
    Assert-True (-not [System.IO.File]::Exists($faultyOutput)) 'invalid output parent leaves no output'

    $faultyBackupParent = Join-Path $root.FullName 'faulty-backup-parent'
    [System.IO.File]::WriteAllText($faultyBackupParent, 'not-a-directory')
    $faultyBackup = Join-Path $faultyBackupParent 'classes.yml'
    $faultyBackupOutput = Join-Path $root.FullName 'faulty-backup-output\classes.yml'
    $faultyBackupFailed = $false
    try {
        & $script -InputPath $legacy -OutputPath $faultyBackupOutput -BackupPath $faultyBackup
    } catch {
        $faultyBackupFailed = $true
    }
    Assert-True $faultyBackupFailed 'backup failure is rejected without server access'
    Assert-True (-not [System.IO.File]::Exists($faultyBackupOutput)) 'backup failure leaves no output'
    Assert-True (@(Get-ChildItem -LiteralPath (Split-Path -Parent $faultyBackupOutput) -Filter '.b16-migration-*.tmp' -File).Count -eq 0) 'backup failure cleans its temporary output'

    $ambiguous = Join-Path $root.FullName 'legacy\ambiguous\classes.yml'
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($ambiguous)) | Out-Null
    [System.IO.File]::WriteAllText($ambiguous, "classes:`nfuture-balance:`n")
    $ambiguousOutput = Join-Path $root.FullName 'ambiguous-out.yml'
    Assert-Throws { & $script -InputPath $ambiguous -OutputPath $ambiguousOutput } 'classes.yml at future-balance: unknown legacy root section'
    Assert-True (-not [System.IO.File]::Exists($ambiguousOutput)) 'ambiguous migration creates no output'

    $duplicate = Join-Path $root.FullName 'legacy\duplicate\classes.yml'
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($duplicate)) | Out-Null
    [System.IO.File]::WriteAllText($duplicate, "classes:`nclasses:`n")
    $duplicateOutput = Join-Path $root.FullName 'duplicate-out.yml'
    Assert-Throws { & $script -InputPath $duplicate -OutputPath $duplicateOutput } 'classes.yml at classes: duplicate root key'
    Assert-True (-not [System.IO.File]::Exists($duplicateOutput)) 'duplicate-root migration creates no output'

    $nestedDuplicate = Join-Path $root.FullName 'legacy\nested-duplicate\classes.yml'
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($nestedDuplicate)) | Out-Null
    $nestedDuplicateText = @'
classes:
  warrior:
    base-stats:
      health: 100.0
      health: 125.0
'@
    [System.IO.File]::WriteAllText($nestedDuplicate, $nestedDuplicateText, (New-Object System.Text.UTF8Encoding($false)))
    $nestedDuplicateOutput = Join-Path $root.FullName 'nested-duplicate-out.yml'
    Assert-Throws { & $script -InputPath $nestedDuplicate -OutputPath $nestedDuplicateOutput } 'classes.yml at classes.warrior.base-stats.health: duplicate key'
    Assert-True (-not [System.IO.File]::Exists($nestedDuplicateOutput)) 'nested-duplicate migration creates no output'

    $multiDocument = Join-Path $root.FullName 'legacy\multi-document\classes.yml'
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($multiDocument)) | Out-Null
    [System.IO.File]::WriteAllText($multiDocument, "---`nclasses:`n---`nclasses:`n")
    $multiDocumentOutput = Join-Path $root.FullName 'multi-document-out.yml'
    Assert-Throws { & $script -InputPath $multiDocument -OutputPath $multiDocumentOutput } 'classes.yml at <document>: multiple YAML documents are not supported'
    Assert-True (-not [System.IO.File]::Exists($multiDocumentOutput)) 'multi-document migration creates no output'

    $nonInteger = Join-Path $root.FullName 'legacy\non-integer-version\classes.yml'
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($nonInteger)) | Out-Null
    [System.IO.File]::WriteAllText($nonInteger, "schemaVersion: one`nclasses:`n")
    $nonIntegerOutput = Join-Path $root.FullName 'non-integer-version-out.yml'
    Assert-Throws { & $script -InputPath $nonInteger -OutputPath $nonIntegerOutput } 'classes.yml at schemaVersion: expected exact integer 1, was one'
    Assert-True (-not [System.IO.File]::Exists($nonIntegerOutput)) 'non-integer schema migration creates no output'

    $unsupported = Join-Path $root.FullName 'legacy\unsupported\classes.yml'
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($unsupported)) | Out-Null
    [System.IO.File]::WriteAllText($unsupported, "schemaVersion: 2`nclasses:`n")
    Assert-Throws { & $script -InputPath $unsupported -OutputPath (Join-Path $root.FullName 'unsupported-out.yml') } 'expected exact integer 1'

    Write-Output 'B16 migration script tests: PASS'
} finally {
    if ($null -ne $root -and [System.IO.Directory]::Exists($root.FullName)) {
        Remove-Item -LiteralPath $root.FullName -Recurse -Force
    }
}
