[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [string] $BackupPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Stop-Migration([string] $Message) {
    throw "B16 migration failed: $Message"
}

function Resolve-RequiredPath([string] $Value, [string] $Name) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        Stop-Migration "$Name must not be blank"
    }
    return [System.IO.Path]::GetFullPath($Value)
}

function New-TemporaryOutputPath([string] $Directory) {
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        $candidate = Join-Path $Directory ".b16-migration-$([System.IO.Path]::GetRandomFileName()).tmp"
        try {
            $stream = [System.IO.File]::Open(
                $candidate,
                [System.IO.FileMode]::CreateNew,
                [System.IO.FileAccess]::Write,
                [System.IO.FileShare]::None)
            $stream.Dispose()
            return $candidate
        } catch [System.IO.IOException] {
            if ([System.IO.File]::Exists($candidate)) {
                continue
            }
            throw
        }
    }
    Stop-Migration 'could not allocate a unique temporary output file'
}

$inputFull = Resolve-RequiredPath $InputPath 'InputPath'
$outputFull = Resolve-RequiredPath $OutputPath 'OutputPath'
$backupFull = if ([string]::IsNullOrWhiteSpace($BackupPath)) {
    $null
} else {
    Resolve-RequiredPath $BackupPath 'BackupPath'
}

$pathComparer = [System.StringComparer]::OrdinalIgnoreCase
if ($pathComparer.Equals($inputFull, $outputFull)) {
    Stop-Migration 'InputPath and OutputPath must be different'
}
if ($null -ne $backupFull -and $pathComparer.Equals($inputFull, $backupFull)) {
    Stop-Migration 'InputPath and BackupPath must be different'
}
if ($null -ne $backupFull -and $pathComparer.Equals($outputFull, $backupFull)) {
    Stop-Migration 'OutputPath and BackupPath must be different'
}
if (-not [System.IO.File]::Exists($inputFull)) {
    Stop-Migration "source file does not exist: $inputFull"
}
if ([System.IO.File]::Exists($outputFull) -or [System.IO.Directory]::Exists($outputFull)) {
    Stop-Migration "refusing to overwrite existing output: $outputFull"
}
if ($null -ne $backupFull -and [System.IO.File]::Exists($backupFull)) {
    Stop-Migration "refusing to overwrite existing backup: $backupFull"
}

$fileName = [System.IO.Path]::GetFileName($inputFull)
$allowedRoots = @{
    'classes.yml' = @('classes')
    'abilities.yml' = @('runtime', 'abilities')
    'progression.yml' = @('xp-curve', 'level-growth', 'mob-xp', 'party', 'progress-event')
    'combat.yml' = @('combat', 'environment', 'mobs')
    'zones.yml' = @('provisional', 'fallback-point', 'warning-cooldown-seconds', 'combat-logout', 'zones')
    'mobs.yml' = @('admin-spawn-limit', 'budget', 'horde', 'kinds', 'hordes')
    'items.yml' = @('inventory', 'wear', 'repair', 'templates', 'loot', 'vendors')
    'currency.yml' = @('account', 'drops', 'ledger', 'history')
    'stats.yml' = @('attributes')
}
if (-not $allowedRoots.ContainsKey($fileName)) {
    Stop-Migration "unsupported B16 filename '$fileName'"
}

$raw = [System.IO.File]::ReadAllText($inputFull)
if ([string]::IsNullOrWhiteSpace($raw)) {
    Stop-Migration "empty source document at <document> in $fileName"
}

$lines = $raw -split '\r?\n'
$rootKeys = [System.Collections.Generic.List[string]]::new()
$seenRootKeys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$mapFrames = [System.Collections.Generic.List[object]]::new()
$mapFrames.Add(
    [pscustomobject]@{
        ScopeIndent = -1
        Path = ''
        Keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    })
$schemaVersion = $null
$firstMeaningfulLine = -1
$documentMarkerSeen = $false

for ($index = 0; $index -lt $lines.Count; $index++) {
    $line = $lines[$index]
    $trimmed = $line.Trim()
    if ($index -eq 0) {
        $trimmed = $trimmed.TrimStart([char]0xFEFF)
    }
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
        continue
    }
    if ($firstMeaningfulLine -lt 0) {
        $firstMeaningfulLine = $index
    }
    if ($trimmed -eq '---') {
        if ($documentMarkerSeen) {
            Stop-Migration "$fileName at <document>: multiple YAML documents are not supported"
        }
        $documentMarkerSeen = $true
        continue
    }
    if ($trimmed.StartsWith('%')) {
        Stop-Migration "$fileName at <document>: YAML directives are not part of the known legacy structure"
    }

    if ($line -match '^(?<indent>[ \t]*)(?<key>[A-Za-z0-9_-]+)\s*:(?<value>.*)$') {
        $indent = $matches['indent'].Length
        while ($mapFrames.Count -gt 1 -and $mapFrames[$mapFrames.Count - 1].ScopeIndent -ge $indent) {
            $mapFrames.RemoveAt($mapFrames.Count - 1)
        }

        $parentFrame = $mapFrames[$mapFrames.Count - 1]
        $key = $matches['key']
        $path = if ([string]::IsNullOrEmpty($parentFrame.Path)) {
            $key
        } else {
            "$($parentFrame.Path).$key"
        }
        if (-not $parentFrame.Keys.Add($key)) {
            if ($indent -eq 0) {
                Stop-Migration "$fileName at ${key}: duplicate root key"
            }
            Stop-Migration "$fileName at ${path}: duplicate key"
        }
        $value = $matches['value']

        if ($indent -eq 0) {
            if (-not $seenRootKeys.Add($key)) {
                Stop-Migration "$fileName at ${key}: duplicate root key"
            }
            if ($key -eq 'schemaVersion') {
                if ($value -notmatch '^\s*(?<version>[^\s#]+)\s*(?:#.*)?$') {
                    Stop-Migration "$fileName at schemaVersion: an explicit integer version is required"
                }
                $schemaVersion = $matches['version']
            } else {
                $rootKeys.Add($key) | Out-Null
            }
        }

        $inlineValue = $value.Trim()
        if ($inlineValue.Length -eq 0 -or $inlineValue.StartsWith('#')) {
            $mapFrames.Add(
                [pscustomobject]@{
                    ScopeIndent = $indent
                    Path = $path
                    Keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
                })
        }
        continue
    }

    if (-not $line.StartsWith(' ') -and -not $line.StartsWith("`t")) {
        Stop-Migration "$fileName at <document>: unsupported root YAML syntax"
    }
}

if ($rootKeys.Count -eq 0) {
    Stop-Migration "$fileName at <document>: a non-empty known legacy structure is required"
}
foreach ($key in $rootKeys) {
    if (-not ($allowedRoots[$fileName] -contains $key)) {
        Stop-Migration "$fileName at ${key}: unknown legacy root section"
    }
}

$changed = $true
$status = 'MIGRATED'
$outputText = $raw
if ($null -ne $schemaVersion) {
    if ($schemaVersion -ne '1') {
        Stop-Migration "$fileName at schemaVersion: expected exact integer 1, was $schemaVersion"
    }
    $changed = $false
    $status = 'UNCHANGED'
} else {
    $newline = if ($raw.Contains("`r`n")) { "`r`n" } else { "`n" }
    $outputLines = [System.Collections.Generic.List[string]]::new()
    $inserted = $false
    for ($index = 0; $index -lt $lines.Count; $index++) {
        $outputLines.Add($lines[$index]) | Out-Null
        if ($index -eq $firstMeaningfulLine -and $lines[$index].Trim() -eq '---') {
            $outputLines.Add('schemaVersion: 1') | Out-Null
            $inserted = $true
        }
    }
    if (-not $inserted) {
        $outputLines.Insert(0, 'schemaVersion: 1') | Out-Null
    }
    $outputText = [string]::Join($newline, $outputLines)
}

$outputParent = [System.IO.Directory]::GetParent($outputFull)
if ($null -ne $outputParent -and -not $outputParent.Exists) {
    [System.IO.Directory]::CreateDirectory($outputParent.FullName) | Out-Null
}

$tempOutputFull = $null
try {
    $tempOutputFull = New-TemporaryOutputPath $outputParent.FullName
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tempOutputFull, $outputText, $utf8)

    if ($null -ne $backupFull) {
        $backupParent = [System.IO.Directory]::GetParent($backupFull)
        if ($null -ne $backupParent -and -not $backupParent.Exists) {
            [System.IO.Directory]::CreateDirectory($backupParent.FullName) | Out-Null
        }
        [System.IO.File]::Copy($inputFull, $backupFull)
    }

    # The temporary file is in the destination directory, so File.Move is an
    # atomic rename on the same volume and cannot overwrite a newly-created output.
    [System.IO.File]::Move($tempOutputFull, $outputFull)
    $tempOutputFull = $null
} finally {
    if ($null -ne $tempOutputFull -and [System.IO.File]::Exists($tempOutputFull)) {
        [System.IO.File]::Delete($tempOutputFull)
    }
}

[pscustomobject]@{
    status = $status
    changed = $changed
    input = $inputFull
    output = $outputFull
    backup = $backupFull
}
