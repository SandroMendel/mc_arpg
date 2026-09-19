[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ConfigRoot,

    [Parameter(Mandatory = $true)]
    [string] $ScenarioPath,

    [Parameter(Mandatory = $true)]
    [string] $OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExpectedSchemaVersion = 1
$ExpectedFixtureVersion = 1
$ExpectedBaselineVersion = 1
$ExpectedResultVersion = 1
$RequiredFiles = @(
    'classes.yml',
    'abilities.yml',
    'progression.yml',
    'combat.yml',
    'zones.yml',
    'mobs.yml',
    'items.yml',
    'currency.yml',
    'stats.yml'
)

function Fail-Analysis([string] $Path, [string] $Reason) {
    throw "B16 balance analysis failed: $($Path): $Reason"
}

function Get-MapValue([object] $Map, [string] $Key, [string] $Path) {
    if (-not ($Map -is [System.Collections.IDictionary])) {
        Fail-Analysis $Path "expected a map while reading '$Key'"
    }
    if (-not $Map.Contains($Key)) {
        Fail-Analysis $Path "missing required key '$Key'"
    }
    return $Map[$Key]
}

function Test-MapKey([object] $Map, [string] $Key) {
    return ($Map -is [System.Collections.IDictionary] -and $Map.Contains($Key))
}

function Get-PathValue([object] $Root, [string] $DottedPath, [string] $SourcePath) {
    $current = $Root
    $parts = $DottedPath -split '\.'
    $walked = [System.Collections.Generic.List[string]]::new()
    foreach ($part in $parts) {
        $walked.Add($part) | Out-Null
        if (-not ($current -is [System.Collections.IDictionary]) -or -not $current.Contains($part)) {
            Fail-Analysis $SourcePath "unknown document path '$DottedPath' at '$($walked -join '.')'"
        }
        $current = $current[$part]
    }
    return $current
}

function Remove-YamlComment([string] $Text) {
    $single = $false
    $double = $false
    $flowDepth = 0
    for ($i = 0; $i -lt $Text.Length; $i++) {
        $char = $Text[$i]
        if ($char -eq "'" -and -not $double) {
            if ($single -and $i + 1 -lt $Text.Length -and $Text[$i + 1] -eq "'") {
                $i++
                continue
            }
            $single = -not $single
            continue
        }
        if ($char -eq '"' -and -not $single) {
            $escaped = $i -gt 0 -and $Text[$i - 1] -eq '\'
            if (-not $escaped) { $double = -not $double }
            continue
        }
        if (-not $single -and -not $double) {
            if ($char -eq '{' -or $char -eq '[') { $flowDepth++ }
            if ($char -eq '}' -or $char -eq ']') { $flowDepth-- }
            if ($char -eq '#' -and $flowDepth -eq 0) {
                return $Text.Substring(0, $i).TrimEnd()
            }
        }
    }
    return $Text.TrimEnd()
}

function Split-FlowItems([string] $Text, [string] $Path) {
    $items = [System.Collections.Generic.List[string]]::new()
    $start = 0
    $single = $false
    $double = $false
    $depth = 0
    for ($i = 0; $i -lt $Text.Length; $i++) {
        $char = $Text[$i]
        if ($char -eq "'" -and -not $double) { $single = -not $single; continue }
        if ($char -eq '"' -and -not $single) {
            $escaped = $i -gt 0 -and $Text[$i - 1] -eq '\'
            if (-not $escaped) { $double = -not $double }
            continue
        }
        if ($single -or $double) { continue }
        if ($char -eq '{' -or $char -eq '[') { $depth++ }
        elseif ($char -eq '}' -or $char -eq ']') { $depth-- }
        elseif ($char -eq ',' -and $depth -eq 0) {
            $items.Add($Text.Substring($start, $i - $start).Trim()) | Out-Null
            $start = $i + 1
        }
    }
    if ($depth -ne 0 -or $single -or $double) { Fail-Analysis $Path 'unbalanced flow YAML value' }
    $tail = $Text.Substring($start).Trim()
    if ($tail.Length -gt 0) { $items.Add($tail) | Out-Null }
    return $items.ToArray()
}

function Find-YamlColon([string] $Text) {
    $single = $false
    $double = $false
    $depth = 0
    for ($i = 0; $i -lt $Text.Length; $i++) {
        $char = $Text[$i]
        if ($char -eq "'" -and -not $double) { $single = -not $single; continue }
        if ($char -eq '"' -and -not $single) {
            $escaped = $i -gt 0 -and $Text[$i - 1] -eq '\'
            if (-not $escaped) { $double = -not $double }
            continue
        }
        if ($single -or $double) { continue }
        if ($char -eq '{' -or $char -eq '[') { $depth++ }
        elseif ($char -eq '}' -or $char -eq ']') { $depth-- }
        elseif ($char -eq ':' -and $depth -eq 0) { return $i }
    }
    return -1
}

function Convert-YamlScalar([string] $Text, [string] $Path) {
    $value = Remove-YamlComment $Text
    $value = $value.Trim()
    if ($value.Length -eq 0 -or $value -eq '~' -or $value -eq 'null') { return $null }
    if ($value.StartsWith('{') -or $value.StartsWith('[')) {
        if ($value.StartsWith('{') -and -not $value.EndsWith('}')) { Fail-Analysis $Path 'unterminated flow map' }
        if ($value.StartsWith('[') -and -not $value.EndsWith(']')) { Fail-Analysis $Path 'unterminated flow list' }
        $inner = $value.Substring(1, $value.Length - 2)
        if ($value.StartsWith('{')) {
            $map = [ordered]@{}
            foreach ($item in (Split-FlowItems $inner $Path)) {
                $colon = Find-YamlColon $item
                if ($colon -lt 1) { Fail-Analysis $Path "invalid flow-map item '$item'" }
                $key = $item.Substring(0, $colon).Trim().Trim("'", '"')
                if ($map.Contains($key)) { Fail-Analysis $Path "duplicate key '$key'" }
                $map[$key] = Convert-YamlScalar $item.Substring($colon + 1) $Path
            }
            return $map
        }
        $list = [System.Collections.Generic.List[object]]::new()
        foreach ($item in (Split-FlowItems $inner $Path)) { $list.Add((Convert-YamlScalar $item $Path)) | Out-Null }
        return $list.ToArray()
    }
    if (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"'))) {
        if ($value.StartsWith("'")) { return $value.Substring(1, $value.Length - 2).Replace("''", "'") }
        try { return ($value | ConvertFrom-Json -ErrorAction Stop) } catch { Fail-Analysis $Path 'invalid quoted scalar' }
    }
    if ($value -match '^(?i:true|false)$') { return [bool]::Parse($value) }
    if ($value -match '^-?[0-9]+$') { try { return [long]::Parse($value, [Globalization.CultureInfo]::InvariantCulture) } catch { } }
    if ($value -match '^-?(?:[0-9]+\.[0-9]*|\.[0-9]+)(?:[eE][+-]?[0-9]+)?$') {
        try { return [double]::Parse($value, [Globalization.CultureInfo]::InvariantCulture) } catch { Fail-Analysis $Path 'invalid numeric scalar' }
    }
    return $value
}

function Parse-YamlBlock([object[]] $Tokens, [ref] $Index, [int] $Indent, [string] $SourcePath) {
    if ($Index.Value -ge $Tokens.Count) { Fail-Analysis $SourcePath 'unexpected end of document' }
    $isList = $Tokens[$Index.Value].Indent -eq $Indent -and ($Tokens[$Index.Value].Text -eq '-' -or $Tokens[$Index.Value].Text.StartsWith('- '))
    if ($isList) {
        $list = [System.Collections.Generic.List[object]]::new()
        while ($Index.Value -lt $Tokens.Count -and $Tokens[$Index.Value].Indent -eq $Indent -and ($Tokens[$Index.Value].Text -eq '-' -or $Tokens[$Index.Value].Text.StartsWith('- '))) {
            $token = $Tokens[$Index.Value]
            $rest = if ($token.Text -eq '-') { '' } else { $token.Text.Substring(1).TrimStart() }
            $Index.Value++
            if ($rest.Length -eq 0) {
                if ($Index.Value -ge $Tokens.Count -or $Tokens[$Index.Value].Indent -le $Indent) { Fail-Analysis "$($SourcePath):$($token.Line)" 'list item is missing a value' }
                $list.Add((Parse-YamlBlock $Tokens $Index $Tokens[$Index.Value].Indent $SourcePath)) | Out-Null
                continue
            }
            $colon = Find-YamlColon $rest
            if ($colon -gt 0 -and -not $rest.StartsWith('{')) {
                $map = [ordered]@{}
                $key = $rest.Substring(0, $colon).Trim()
                if ($key -notmatch '^[A-Za-z0-9_.-]+$') { Fail-Analysis "$($SourcePath):$($token.Line)" "invalid map key '$key'" }
                $inline = $rest.Substring($colon + 1).Trim()
                if ($inline.Length -gt 0) { $map[$key] = Convert-YamlScalar $inline "$($SourcePath):$($token.Line)" }
                elseif ($Index.Value -lt $Tokens.Count -and $Tokens[$Index.Value].Indent -gt $Indent) { $map[$key] = Parse-YamlBlock $Tokens $Index $Tokens[$Index.Value].Indent $SourcePath }
                else { $map[$key] = $null }
                if ($Index.Value -lt $Tokens.Count -and $Tokens[$Index.Value].Indent -gt $Indent) {
                    $childIndent = $Tokens[$Index.Value].Indent
                    while ($Index.Value -lt $Tokens.Count -and $Tokens[$Index.Value].Indent -eq $childIndent -and -not ($Tokens[$Index.Value].Text -eq '-' -or $Tokens[$Index.Value].Text.StartsWith('- '))) {
                        $entry = $Tokens[$Index.Value]
                        $entryColon = Find-YamlColon $entry.Text
                        if ($entryColon -le 0) { Fail-Analysis "$($SourcePath):$($entry.Line)" 'expected map entry' }
                        $entryKey = $entry.Text.Substring(0, $entryColon).Trim()
                        if ($entryKey -notmatch '^[A-Za-z0-9_.-]+$') { Fail-Analysis "$($SourcePath):$($entry.Line)" "invalid map key '$entryKey'" }
                        if ($map.Contains($entryKey)) { Fail-Analysis "$($SourcePath):$($entry.Line)" "duplicate key '$entryKey'" }
                        $entryValue = $entry.Text.Substring($entryColon + 1).Trim()
                        $Index.Value++
                        if ($entryValue.Length -gt 0) { $map[$entryKey] = Convert-YamlScalar $entryValue "$($SourcePath):$($entry.Line)" }
                        elseif ($Index.Value -lt $Tokens.Count -and $Tokens[$Index.Value].Indent -gt $childIndent) { $map[$entryKey] = Parse-YamlBlock $Tokens $Index $Tokens[$Index.Value].Indent $SourcePath }
                        else { $map[$entryKey] = $null }
                    }
                }
                $list.Add($map) | Out-Null
            } else { $list.Add((Convert-YamlScalar $rest "$($SourcePath):$($token.Line)")) | Out-Null }
        }
        return $list.ToArray()
    }
    $map = [ordered]@{}
    while ($Index.Value -lt $Tokens.Count -and $Tokens[$Index.Value].Indent -eq $Indent) {
        $token = $Tokens[$Index.Value]
        if ($token.Text -eq '-' -or $token.Text.StartsWith('- ')) { break }
        $colon = Find-YamlColon $token.Text
        if ($colon -le 0) { Fail-Analysis "$($SourcePath):$($token.Line)" 'expected map entry with a colon' }
        $key = $token.Text.Substring(0, $colon).Trim()
        if ($key -notmatch '^[A-Za-z0-9_.-]+$') { Fail-Analysis "$($SourcePath):$($token.Line)" "invalid map key '$key'" }
        if ($map.Contains($key)) { Fail-Analysis "$($SourcePath):$($token.Line)" "duplicate key '$key'" }
        $valueText = $token.Text.Substring($colon + 1).Trim()
        $Index.Value++
        if ($valueText.Length -gt 0) { $map[$key] = Convert-YamlScalar $valueText "$($SourcePath):$($token.Line)" }
        elseif ($Index.Value -lt $Tokens.Count -and $Tokens[$Index.Value].Indent -gt $Indent) { $map[$key] = Parse-YamlBlock $Tokens $Index $Tokens[$Index.Value].Indent $SourcePath }
        else { $map[$key] = $null }
    }
    return $map
}

function Read-YamlFile([string] $Path) {
    if (-not [System.IO.File]::Exists($Path)) { Fail-Analysis $Path 'file does not exist' }
    $lines = [System.IO.File]::ReadAllText($Path) -split "`r?`n"
    $tokens = [System.Collections.Generic.List[object]]::new()
    $documentMarker = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $raw = $lines[$i]
        if ($raw.Contains("`t")) { Fail-Analysis "$($Path):$($i + 1)" 'tabs are not supported for indentation' }
        $clean = Remove-YamlComment $raw
        if ($clean.Trim().Length -eq 0) { continue }
        $trimmed = $clean.Trim()
        if ($trimmed -eq '---') {
            if ($documentMarker) { Fail-Analysis "$($Path):$($i + 1)" 'multiple YAML documents are not supported' }
            $documentMarker = $true
            continue
        }
        if ($trimmed -eq '...') { continue }
        if ($trimmed.StartsWith('%') -or $trimmed.StartsWith('&') -or $trimmed.StartsWith('*') -or $trimmed.StartsWith('!')) { Fail-Analysis "$($Path):$($i + 1)" 'YAML directives, anchors, aliases and tags are not supported' }
        $indent = $clean.Length - $clean.TrimStart(' ').Length
        $tokens.Add([pscustomobject]@{ Indent = $indent; Text = $clean.Trim(); Line = $i + 1 }) | Out-Null
    }
    if ($tokens.Count -eq 0) { Fail-Analysis $Path 'empty YAML document' }
    $index = 0
    $root = Parse-YamlBlock $tokens ([ref]$index) $tokens[0].Indent $Path
    if ($index -ne $tokens.Count) { Fail-Analysis $Path "unexpected indentation near line $($tokens[$index].Line)" }
    return $root
}

function Assert-Integer([object] $Value, [string] $Path) {
    if ($Value -is [bool] -or $Value -isnot [byte] -and $Value -isnot [int16] -and $Value -isnot [int32] -and $Value -isnot [int64]) { Fail-Analysis $Path 'expected an integer' }
    return [int64]$Value
}

function Assert-String([object] $Value, [string] $Path) {
    if ($null -eq $Value -or $Value -isnot [string] -or [string]::IsNullOrWhiteSpace($Value)) { Fail-Analysis $Path 'expected a non-empty string' }
    return [string]$Value
}

function Assert-List([object] $Value, [string] $Path) {
    if ($Value -isnot [System.Collections.IList] -or $Value -is [string]) { Fail-Analysis $Path 'expected a list' }
    return $Value
}

function To-Double([object] $Value, [string] $Path) {
    if ($Value -is [bool] -or $Value -isnot [System.IConvertible]) { Fail-Analysis $Path 'expected a numeric value' }
    try { $number = [double]::Parse($Value.ToString(), [Globalization.CultureInfo]::InvariantCulture) } catch { Fail-Analysis $Path 'expected a finite numeric value' }
    if (-not [double]::IsFinite($number)) { Fail-Analysis $Path 'expected a finite numeric value; NaN and +/-Infinity are not allowed' }
    return $number
}

function Format-Number([object] $Value) {
    if ($Value -is [string]) { return $Value }
    if ($Value -is [bool]) { return $Value.ToString().ToLowerInvariant() }
    if ($Value -is [System.IConvertible]) { return ([double]$Value).ToString('0.###############', [Globalization.CultureInfo]::InvariantCulture) }
    return [string]$Value
}

function Get-ClassTier([object] $Class, [int64] $Level, [string] $Path) {
    $selected = $null
    foreach ($tier in (Assert-List (Get-MapValue $Class 'weapon-ladder' $Path) "$Path.weapon-ladder")) {
        $required = Assert-Integer (Get-MapValue $tier 'required-level' "$Path.weapon-ladder") "$Path.weapon-ladder.required-level"
        if ($required -le $Level -and ($null -eq $selected -or $required -gt $selected.Required)) { $selected = [pscustomobject]@{ Required = $required; Values = Get-MapValue $tier 'values' "$Path.weapon-ladder" } }
    }
    if ($null -eq $selected) { Fail-Analysis $Path "no weapon tier is available for level $Level" }
    return $selected
}

function Get-EffectiveAttacker([object] $Config, [string] $ClassId, [int64] $Level, [string] $Path) {
    $classes = Get-MapValue $Config['classes.yml'] 'classes' 'classes.yml'
    $class = Get-MapValue $classes $ClassId "classes.yml:classes.$ClassId"
    $stats = Get-MapValue $Config['stats.yml'] 'attributes' 'stats.yml:attributes'
    $baseAttributes = Get-MapValue $class 'base-stats' "classes.yml:classes.$ClassId.base-stats"
    $growth = Get-MapValue $class 'growth' "classes.yml:classes.$ClassId.growth"
    $physicalBase = To-Double (Get-MapValue (Get-MapValue $stats 'physicalDamage' 'stats.yml:attributes.physicalDamage') 'base' 'stats.yml:attributes.physicalDamage.base') 'stats.yml:attributes.physicalDamage.base'
    $attackBase = To-Double (Get-MapValue (Get-MapValue $stats 'attackSpeed' 'stats.yml:attributes.attackSpeed') 'base' 'stats.yml:attributes.attackSpeed.base') 'stats.yml:attributes.attackSpeed.base'
    $tier = Get-ClassTier $class $Level "classes.yml:classes.$ClassId"
    $classDamage = To-Double (Get-MapValue $baseAttributes 'physicalDamage' "classes.yml:classes.$ClassId.base-stats") "classes.yml:classes.$ClassId.base-stats.physicalDamage"
    $growthDamage = To-Double (Get-MapValue $growth 'physicalDamage' "classes.yml:classes.$ClassId.growth") "classes.yml:classes.$ClassId.growth.physicalDamage"
    $weaponDamage = To-Double (Get-MapValue $tier.Values 'physicalDamage' "classes.yml:classes.$ClassId.weapon-ladder.values") "classes.yml:classes.$ClassId.weapon-ladder.values.physicalDamage"
    $weaponSpeed = To-Double (Get-MapValue $tier.Values 'attackSpeed' "classes.yml:classes.$ClassId.weapon-ladder.values") "classes.yml:classes.$ClassId.weapon-ladder.values.attackSpeed"
    return [pscustomobject]@{
        Damage = $physicalBase + $classDamage + (($Level - 1) * $growthDamage) + $weaponDamage
        AttackSpeed = $attackBase + $weaponSpeed
        DamageSource = "classes.yml:classes.$ClassId"
        Level = $Level
    }
}

function Add-Row([System.Collections.Generic.List[object]] $Rows, [string] $MetricId, [string] $MetricType, [string] $RowId, [string] $Source, [string] $Path, [string] $EntityId, [object] $Level, [object] $Value, [string] $Unit, [object] $SecondaryValue, [string] $SecondaryUnit) {
    $Rows.Add([ordered]@{
        metricId = $MetricId
        metricType = $MetricType
        rowId = $RowId
        source = $Source
        documentPath = $Path
        entityId = $EntityId
        level = if ($null -eq $Level) { $null } else { $Level }
        value = $Value
        unit = $Unit
        secondaryValue = $SecondaryValue
        secondaryUnit = $SecondaryUnit
    }) | Out-Null
}

function Escape-Csv([object] $Value) {
    if ($null -eq $Value) { return '' }
    $text = if ($Value -is [double] -or $Value -is [decimal] -or $Value -is [int] -or $Value -is [long]) { Format-Number $Value } else { [string]$Value }
    return '"' + $text.Replace('"', '""') + '"'
}

function Convert-RowsToCsv([object[]] $Rows) {
    $columns = @('scenarioId', 'metricId', 'metricType', 'rowId', 'source', 'documentPath', 'entityId', 'level', 'value', 'unit', 'secondaryValue', 'secondaryUnit')
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add(($columns | ForEach-Object { Escape-Csv $_ }) -join ',') | Out-Null
    foreach ($row in $Rows) {
        $values = @('scenarioId', 'metricId', 'metricType', 'rowId', 'source', 'documentPath', 'entityId', 'level', 'value', 'unit', 'secondaryValue', 'secondaryUnit')
        $fields = [System.Collections.Generic.List[string]]::new()
        foreach ($field in $values) {
            $value = if ($field -eq 'scenarioId') { $script:ScenarioId } else { $row[$field] }
            $fields.Add((Escape-Csv $value)) | Out-Null
        }
        $lines.Add(($fields -join ',')) | Out-Null
    }
    return ($lines -join "`n") + "`n"
}

function Write-Utf8Lf([string] $Path, [string] $Text) {
    $normalized = $Text -replace "`r`n", "`n" -replace "`r", "`n"
    [System.IO.File]::WriteAllText($Path, $normalized, (New-Object System.Text.UTF8Encoding($false)))
}

function Test-PathWithin([string] $Candidate, [string] $Root) {
    $relative = [System.IO.Path]::GetRelativePath($Root, $Candidate)
    return ($relative -eq '.' -or (-not [System.IO.Path]::IsPathRooted($relative) -and $relative -notmatch '^\.\.(?:[\\/]|$)'))
}

function Assert-SafeOutputPath([string] $OutputPath, [string] $ConfigPath) {
    $repoPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
    $protectedRoots = @(
        [pscustomobject]@{ Path = $ConfigPath; Label = 'ConfigRoot' }
        [pscustomobject]@{ Path = [System.IO.Path]::GetFullPath((Join-Path $repoPath 'rpg-plugin')); Label = 'rpg-plugin' }
        [pscustomobject]@{ Path = [System.IO.Path]::GetFullPath((Join-Path $repoPath 'rpg-content\src\main\resources')); Label = 'rpg-content runtime resources' }
    )
    foreach ($protected in $protectedRoots) {
        if (Test-PathWithin $OutputPath $protected.Path) {
            Fail-Analysis $OutputPath "output directory is protected because it targets $($protected.Label) or a child path"
        }
    }
    if ([System.IO.File]::Exists($OutputPath) -or [System.IO.Directory]::Exists($OutputPath)) {
        Fail-Analysis $OutputPath 'refusing to reuse an existing output path'
    }
}

$configFull = [System.IO.Path]::GetFullPath($ConfigRoot)
$scenarioFull = [System.IO.Path]::GetFullPath($ScenarioPath)
$outputFull = [System.IO.Path]::GetFullPath($OutputDirectory)
if (-not [System.IO.Directory]::Exists($configFull)) { Fail-Analysis $configFull 'config root does not exist' }
if (-not [System.IO.File]::Exists($scenarioFull)) { Fail-Analysis $scenarioFull 'scenario file does not exist' }
Assert-SafeOutputPath $outputFull $configFull

$configs = [ordered]@{}
$sourceBytes = [System.Collections.Generic.List[byte]]::new()
foreach ($name in $RequiredFiles) {
    $path = Join-Path $configFull $name
    $configs[$name] = Read-YamlFile $path
    $version = Assert-Integer (Get-MapValue $configs[$name] 'schemaVersion' "$($name):schemaVersion") "$($name):schemaVersion"
    if ($version -ne $ExpectedSchemaVersion) { Fail-Analysis "$($path):schemaVersion" "expected exact schemaVersion $ExpectedSchemaVersion, was $version" }
    foreach ($byte in [System.Text.Encoding]::UTF8.GetBytes("$name`n")) { $sourceBytes.Add($byte) | Out-Null }
    foreach ($byte in [System.IO.File]::ReadAllBytes($path)) { $sourceBytes.Add($byte) | Out-Null }
}
$fixture = Read-YamlFile $scenarioFull
$fixtureSchema = Assert-Integer (Get-MapValue $fixture 'schemaVersion' "$($scenarioFull):schemaVersion") "$($scenarioFull):schemaVersion"
if ($fixtureSchema -ne $ExpectedSchemaVersion) { Fail-Analysis "$($scenarioFull):schemaVersion" "expected exact schemaVersion $ExpectedSchemaVersion, was $fixtureSchema" }
$fixtureVersion = Assert-Integer (Get-MapValue $fixture 'fixtureVersion' "$($scenarioFull):fixtureVersion") "$($scenarioFull):fixtureVersion"
if ($fixtureVersion -ne $ExpectedFixtureVersion) { Fail-Analysis "$($scenarioFull):fixtureVersion" "expected exact fixtureVersion $ExpectedFixtureVersion, was $fixtureVersion" }
$baselineVersion = Assert-Integer (Get-MapValue $fixture 'baselineVersion' "$($scenarioFull):baselineVersion") "$($scenarioFull):baselineVersion"
if ($baselineVersion -ne $ExpectedBaselineVersion) { Fail-Analysis "$($scenarioFull):baselineVersion" "expected exact baselineVersion $ExpectedBaselineVersion, was $baselineVersion" }
foreach ($byte in [System.Text.Encoding]::UTF8.GetBytes("fixture`n")) { $sourceBytes.Add($byte) | Out-Null }
foreach ($byte in [System.IO.File]::ReadAllBytes($scenarioFull)) { $sourceBytes.Add($byte) | Out-Null }
$sourceHash = ([System.Security.Cryptography.SHA256]::Create().ComputeHash($sourceBytes.ToArray()) | ForEach-Object { $_.ToString('x2') }) -join ''

$scenario = Get-MapValue $fixture 'scenario' "$($scenarioFull):scenario"
$script:ScenarioId = Assert-String (Get-MapValue $scenario 'id' "$($scenarioFull):scenario") "$($scenarioFull):scenario.id"
$scenarioName = Assert-String (Get-MapValue $scenario 'name' "$($scenarioFull):scenario") "$($scenarioFull):scenario.name"
$sourceFiles = Assert-List (Get-MapValue $scenario 'source-files' "$($scenarioFull):scenario") "$($scenarioFull):scenario.source-files"
$actualSourceFiles = (($sourceFiles | ForEach-Object { [string]$_ } | Sort-Object) -join ',')
$expectedSourceFiles = (($RequiredFiles | Sort-Object) -join ',')
if ($actualSourceFiles -ne $expectedSourceFiles) { Fail-Analysis "$($scenarioFull):scenario.source-files" 'must list exactly the nine B16 source files' }
$metrics = Assert-List (Get-MapValue $scenario 'metrics' "$($scenarioFull):scenario") "$($scenarioFull):scenario.metrics"
$rows = [System.Collections.Generic.List[object]]::new()
$usedIds = [ordered]@{ classes = [System.Collections.Generic.List[string]]::new(); abilities = [System.Collections.Generic.List[string]]::new(); mobs = [System.Collections.Generic.List[string]]::new(); zones = [System.Collections.Generic.List[string]]::new(); items = [System.Collections.Generic.List[string]]::new() }
$baselineValues = [System.Collections.Generic.List[object]]::new()
$metricIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

foreach ($metric in $metrics) {
    $metricId = Assert-String (Get-MapValue $metric 'id' "$($scenarioFull):scenario.metrics") "$($scenarioFull):scenario.metrics.id"
    if (-not $metricIds.Add($metricId)) { Fail-Analysis "$($scenarioFull):scenario.metrics.$metricId" 'duplicate metric id' }
    $metricType = Assert-String (Get-MapValue $metric 'type' "$($scenarioFull):scenario.metrics.$metricId") "$($scenarioFull):scenario.metrics.$metricId.type"
    if ($metricType -eq 'damage-curve') {
        $classIds = Assert-List (Get-MapValue $metric 'class-ids' "$($scenarioFull):scenario.metrics.$metricId") "$($scenarioFull):scenario.metrics.$metricId.class-ids"
        $levels = Assert-List (Get-MapValue $metric 'levels' "$($scenarioFull):scenario.metrics.$metricId") "$($scenarioFull):scenario.metrics.$metricId.levels"
        foreach ($classIdValue in $classIds) {
            $classId = Assert-String $classIdValue "$($scenarioFull):scenario.metrics.$metricId.class-ids"
            if (-not (Test-MapKey (Get-MapValue $configs['classes.yml'] 'classes' 'classes.yml') $classId)) { Fail-Analysis "$($scenarioFull):scenario.metrics.$metricId.class-ids" "unknown class ID '$classId'" }
            $usedIds.classes.Add($classId) | Out-Null
            foreach ($levelValue in $levels) {
                $level = Assert-Integer $levelValue "$($scenarioFull):scenario.metrics.$metricId.levels"
                if ($level -lt 1 -or $level -gt 60) { Fail-Analysis "$($scenarioFull):scenario.metrics.$metricId.levels" "level $level is outside the current 1..60 range" }
                $attacker = Get-EffectiveAttacker $configs $classId $level $metricId
                Add-Row $rows $metricId $metricType "$classId-level-$level" 'classes.yml' "classes.$classId" $classId $level $attacker.Damage 'damage-units' $attacker.AttackSpeed 'attacks-per-second'
            }
        }
    } elseif ($metricType -eq 'ttk') {
        $classId = Assert-String (Get-MapValue $metric 'class-id' "$($scenarioFull):scenario.metrics.$metricId") "$($scenarioFull):scenario.metrics.$metricId.class-id"
        $level = Assert-Integer (Get-MapValue $metric 'level' "$($scenarioFull):scenario.metrics.$metricId") "$($scenarioFull):scenario.metrics.$metricId.level"
        $mobIds = Assert-List (Get-MapValue $metric 'mob-ids' "$($scenarioFull):scenario.metrics.$metricId") "$($scenarioFull):scenario.metrics.$metricId.mob-ids"
        if (-not (Test-MapKey (Get-MapValue $configs['classes.yml'] 'classes' 'classes.yml') $classId)) { Fail-Analysis "$($scenarioFull):scenario.metrics.$metricId.class-id" "unknown class ID '$classId'" }
        $attacker = Get-EffectiveAttacker $configs $classId $level $metricId
        $usedIds.classes.Add($classId) | Out-Null
        foreach ($mobIdValue in $mobIds) {
            $mobId = Assert-String $mobIdValue "$($scenarioFull):scenario.metrics.$metricId.mob-ids"
            $kinds = Get-MapValue $configs['mobs.yml'] 'kinds' 'mobs.yml:kinds'
            if (-not (Test-MapKey $kinds $mobId)) { Fail-Analysis "$($scenarioFull):scenario.metrics.$metricId.mob-ids" "unknown mob ID '$mobId'" }
            $usedIds.mobs.Add($mobId) | Out-Null
            $attributes = Get-MapValue (Get-MapValue $kinds $mobId "mobs.yml:kinds.$mobId") 'attributes' "mobs.yml:kinds.$mobId"
            $health = To-Double (Get-MapValue $attributes 'health' "mobs.yml:kinds.$mobId.attributes") "mobs.yml:kinds.$mobId.attributes.health"
            $defense = To-Double (Get-MapValue $attributes 'defense' "mobs.yml:kinds.$mobId.attributes") "mobs.yml:kinds.$mobId.attributes.defense"
            $afterDefense = $attacker.Damage * 100.0 / (100.0 + $defense)
            $hits = [math]::Ceiling($health / $afterDefense)
            $ttk = if ($hits -le 1) { 0.0 } else { (($hits - 1) / $attacker.AttackSpeed) * 1000.0 }
            Add-Row $rows $metricId $metricType "$classId-vs-$mobId" 'mobs.yml' "kinds.$mobId.attributes" $mobId $level $ttk 'milliseconds' $hits 'hits'
        }
    } elseif ($metricType -eq 'baseline-values') {
        $values = Assert-List (Get-MapValue $metric 'values' "$($scenarioFull):scenario.metrics.$metricId") "$($scenarioFull):scenario.metrics.$metricId.values"
        foreach ($entry in $values) {
            $entryId = Assert-String (Get-MapValue $entry 'id' "$($scenarioFull):scenario.metrics.$metricId.values") "$($scenarioFull):scenario.metrics.$metricId.values.id"
            $source = Assert-String (Get-MapValue $entry 'source' "$($scenarioFull):scenario.metrics.$metricId.values.$entryId") "$($scenarioFull):scenario.metrics.$metricId.values.$entryId.source"
            if (-not $configs.Contains($source)) { Fail-Analysis "$($scenarioFull):scenario.metrics.$metricId.values.$entryId.source" "unknown source file '$source'" }
            $docPath = Assert-String (Get-MapValue $entry 'path' "$($scenarioFull):scenario.metrics.$metricId.values.$entryId") "$($scenarioFull):scenario.metrics.$metricId.values.$entryId.path"
            $unit = Assert-String (Get-MapValue $entry 'unit' "$($scenarioFull):scenario.metrics.$metricId.values.$entryId") "$($scenarioFull):scenario.metrics.$metricId.values.$entryId.unit"
            $value = Get-PathValue $configs[$source] $docPath $source
            if ($value -is [System.Collections.IDictionary] -or $value -is [System.Collections.IList]) { Fail-Analysis "$($scenarioFull):scenario.metrics.$metricId.values.$entryId.path" 'baseline path must resolve to a scalar' }
            $baseline = [ordered]@{ id = $entryId; source = $source; path = $docPath; value = $value; unit = $unit }
            $baselineValues.Add($baseline) | Out-Null
            Add-Row $rows $metricId $metricType $entryId $source $docPath $entryId $null $value $unit $null $null
        }
    } else { Fail-Analysis "$($scenarioFull):scenario.metrics.$metricId.type" "unsupported metric type '$metricType'" }
}

$rows = @($rows | Sort-Object metricId, rowId)
foreach ($property in @('classes', 'abilities', 'mobs', 'zones', 'items')) { $usedIds[$property] = @($usedIds[$property] | Sort-Object -Unique) }
$baselineId = "b16-baseline-v$ExpectedBaselineVersion-$($sourceHash.Substring(0, 16))"
$jsonReport = [ordered]@{
    resultVersion = $ExpectedResultVersion
    status = 'VALID'
    schemaVersion = $ExpectedSchemaVersion
    fixtureVersion = $ExpectedFixtureVersion
    baselineVersion = $ExpectedBaselineVersion
    scenarioId = $script:ScenarioId
    scenarioName = $scenarioName
    baselineId = $baselineId
    sourceHash = "sha256:$sourceHash"
    sourceFiles = @($RequiredFiles)
    usedIds = $usedIds
    baselineValues = @($baselineValues)
    results = $rows
}
$jsonText = ($jsonReport | ConvertTo-Json -Depth 20)
$csvText = Convert-RowsToCsv $rows
$md = [System.Collections.Generic.List[string]]::new()
$md.Add('# B16 Balance Analysis') | Out-Null
$md.Add('') | Out-Null
$md.Add('- status: `VALID`') | Out-Null
$md.Add(('- resultVersion: `{0}`' -f $ExpectedResultVersion)) | Out-Null
$md.Add(('- scenarioId: `{0}`' -f $script:ScenarioId)) | Out-Null
$md.Add(('- baselineId: `{0}`' -f $baselineId)) | Out-Null
$md.Add(('- sourceHash: `sha256:{0}`' -f $sourceHash)) | Out-Null
$md.Add('') | Out-Null
$md.Add('This report is a deterministic baseline observation. It is not a balance target.') | Out-Null
$md.Add('') | Out-Null
$md.Add('| Metric | Row | Value | Unit | Secondary |') | Out-Null
$md.Add('| --- | --- | ---: | --- | ---: |') | Out-Null
foreach ($row in $rows) { $secondary = if ($null -eq $row.secondaryValue) { '' } else { "$(Format-Number $row.secondaryValue) $($row.secondaryUnit)" }; $md.Add("| $($row.metricId) | $($row.rowId) | $(Format-Number $row.value) | $($row.unit) | $secondary |") | Out-Null }
$md.Add('') | Out-Null
$md.Add('CSV/Spreadsheet is output only. There is no runtime re-import and no automatic config write path.') | Out-Null

$stagingFull = $null
try {
    $outputParent = [System.IO.Path]::GetDirectoryName($outputFull)
    if ([string]::IsNullOrWhiteSpace($outputParent)) { Fail-Analysis $outputFull 'output directory has no parent path' }
    if (-not [System.IO.Directory]::Exists($outputParent)) { [System.IO.Directory]::CreateDirectory($outputParent) | Out-Null }
    $stagingFull = Join-Path $outputParent ('.b16-balance-staging-' + [System.IO.Path]::GetRandomFileName())
    [System.IO.Directory]::CreateDirectory($stagingFull) | Out-Null
    Write-Utf8Lf (Join-Path $stagingFull 'b16-balance.csv') $csvText
    Write-Utf8Lf (Join-Path $stagingFull 'b16-balance.md') (($md -join "`n") + "`n")
    Write-Utf8Lf (Join-Path $stagingFull 'b16-balance.json') $jsonText
    foreach ($name in @('b16-balance.csv', 'b16-balance.md', 'b16-balance.json')) {
        if (-not [System.IO.File]::Exists((Join-Path $stagingFull $name))) { Fail-Analysis $stagingFull "staged report '$name' is missing" }
    }
    if ([System.IO.File]::Exists($outputFull) -or [System.IO.Directory]::Exists($outputFull)) { Fail-Analysis $outputFull 'refusing to overwrite output path during publication' }
    [System.IO.Directory]::Move($stagingFull, $outputFull)
    $stagingFull = $null
} catch {
    if ($null -ne $stagingFull -and [System.IO.Directory]::Exists($stagingFull)) {
        Remove-Item -LiteralPath $stagingFull -Recurse -Force -ErrorAction SilentlyContinue
    }
    throw
}
Write-Output "B16 balance analysis: VALID scenario=$script:ScenarioId baseline=$baselineId sourceHash=sha256:$sourceHash"
