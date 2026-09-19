[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path,
    [string] $Manifest = (Join-Path $PSScriptRoot '..\manifest\inventory-manifest-v1.json'),
    [string] $OutputDirectory = (Join-Path $PSScriptRoot '..\reports\current-v1')
)

$ErrorActionPreference = 'Stop'
$script:AstScannerDirectory = $null
$script:AstScannerJava = $null

function Resolve-RepoPath([string] $Root, [string] $Relative) {
    $resolved = if ([IO.Path]::IsPathRooted($Relative) ) {
        [IO.Path]::GetFullPath($Relative)
    } else {
        [IO.Path]::GetFullPath((Join-Path $Root $Relative))
    }
    Assert-NoExternalReparsePoint $resolved $Root 'path'
    return $resolved
}

function Has-JsonProperty([object] $Object, [string] $Name) {
    return $null -ne $Object.PSObject.Properties[$Name]
}

function Get-JsonProperty([object] $Object, [string] $Name) {
    if (-not (Has-JsonProperty $Object $Name)) { return $null }
    return $Object.PSObject.Properties[$Name].Value
}

function Assert-JsonObject([object] $Object, [string] $Context) {
    if ($null -eq $Object -or $Object -is [string] -or $Object -is [array] -or $Object -is [ValueType]) {
        throw ('{0} must be an object' -f $Context)
    }
}

function Assert-ExactFields([object] $Object, [string[]] $Allowed, [string] $Context) {
    Assert-JsonObject $Object $Context
    foreach ($property in $Object.PSObject.Properties) {
        if ($Allowed -notcontains $property.Name) {
            throw ('{0} contains unknown field: {1}' -f $Context, $property.Name)
        }
    }
}

function Require-JsonString([object] $Object, [string] $Name, [string] $Context) {
    if (-not (Has-JsonProperty $Object $Name)) { throw ('{0}.{1} is required' -f $Context, $Name) }
    $value = Get-JsonProperty $Object $Name
    if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) {
        throw ('{0}.{1} must be a non-empty string' -f $Context, $Name)
    }
    return $value
}

function Optional-JsonString([object] $Object, [string] $Name, [string] $Context) {
    if (-not (Has-JsonProperty $Object $Name)) { return $null }
    $value = Get-JsonProperty $Object $Name
    if ($null -eq $value) { return $null }
    if ($value -isnot [string] -or [string]::IsNullOrWhiteSpace($value)) {
        throw ('{0}.{1} must be null or a non-empty string' -f $Context, $Name)
    }
    return $value
}

function Require-JsonValue([object] $Object, [string] $Name, [string] $Context) {
    if (-not (Has-JsonProperty $Object $Name)) { throw ('{0}.{1} is required' -f $Context, $Name) }
    $value = Get-JsonProperty $Object $Name
    if ($null -eq $value) { throw ('{0}.{1} must not be null' -f $Context, $Name) }
    return $value
}

function Assert-JsonArray([object] $Value, [string] $Context) {
    if ($null -eq $Value -or $Value -isnot [array]) { throw ('{0} must be an array' -f $Context) }
}

function Assert-RegexPattern([string] $Pattern, [string] $Context) {
    try { [regex]::new($Pattern) | Out-Null } catch { throw ('{0} is not a valid regex: {1}' -f $Context, $_.Exception.Message) }
}

function Test-NumericScalar([object] $Value) {
    return $Value -is [byte] -or $Value -is [sbyte] -or $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or $Value -is [int64] -or $Value -is [uint64] -or
        $Value -is [single] -or $Value -is [double] -or $Value -is [decimal]
}

function Test-PathWithinRoot([string] $Path, [string] $Root) {
    $pathFull = [IO.Path]::GetFullPath($Path).TrimEnd([char[]]@('\', '/'))
    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd([char[]]@('\', '/'))
    $rootPrefix = $rootFull + [IO.Path]::DirectorySeparatorChar
    return $pathFull.Equals($rootFull, [StringComparison]::OrdinalIgnoreCase) -or
        $pathFull.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-NoExternalReparsePoint([string] $Path, [string] $Root, [string] $Context) {
    $pathFull = [IO.Path]::GetFullPath($Path)
    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd([char[]]@('\', '/'))
    if (-not (Test-PathWithinRoot $pathFull $rootFull)) { return }

    $relative = [IO.Path]::GetRelativePath($rootFull, $pathFull)
    $pathsToCheck = [Collections.Generic.List[string]]::new()
    [void]$pathsToCheck.Add($rootFull)
    $current = $rootFull
    if ($relative -ne '.') {
        foreach ($segment in ($relative -split '[\\/]')) {
            if ([string]::IsNullOrWhiteSpace($segment) -or $segment -eq '.') { continue }
            $current = [IO.Path]::GetFullPath((Join-Path $current $segment))
            [void]$pathsToCheck.Add($current)
        }
    }
    foreach ($current in $pathsToCheck) {
        if (-not (Test-Path -LiteralPath $current)) { break }
        $item = Get-Item -LiteralPath $current -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) { continue }

        try {
            $target = $item.ResolveLinkTarget($true)
        } catch {
            throw ('{0} cannot resolve reparse point: {1} ({2})' -f $Context, $current, $_.Exception.Message)
        }
        if ($null -eq $target) {
            throw ('{0} contains an unresolved reparse point: {1}' -f $Context, $current)
        }
        $targetFull = [IO.Path]::GetFullPath($target.FullName)
        if (-not (Test-PathWithinRoot $targetFull $rootFull)) {
            throw ('{0} points outside RepoRoot through reparse point: {1} -> {2}' -f $Context, $current, $targetFull)
        }
    }
}

function Resolve-RepoRelativePath([string] $Root, [string] $Relative, [string] $Context) {
    if ([string]::IsNullOrWhiteSpace($Relative)) { throw ('{0} must be a non-empty repo-relative path' -f $Context) }
    if ([IO.Path]::IsPathRooted($Relative) -or $Relative -match '^[A-Za-z]:' -or $Relative -match '^[\\/]') {
        throw ('{0} must not be absolute: {1}' -f $Context, $Relative)
    }
    $segments = $Relative -split '[\\/]'
    if (@($segments | Where-Object { $_ -eq '..' }).Count -gt 0) {
        throw ('{0} must not contain path traversal: {1}' -f $Context, $Relative)
    }
    try {
        $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd([char[]]@('\', '/'))
        $resolved = [IO.Path]::GetFullPath([IO.Path]::Combine($rootFull, $Relative))
    } catch {
        throw ('{0} is not a valid repo-relative path: {1}' -f $Context, $Relative)
    }
    $rootPrefix = $rootFull + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.Equals($rootFull, [StringComparison]::OrdinalIgnoreCase) -and
        -not $resolved.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw ('{0} resolves outside RepoRoot: {1}' -f $Context, $Relative)
    }
    Assert-NoExternalReparsePoint $resolved $rootFull $Context
    return $resolved
}

function Validate-Manifest([object] $Definition, [string] $Repo) {
    $topFields = @('inventoryVersion', 'schemaVersion', 'generatedFrom', 'semanticMethod', 'sourceRoots', 'scanRegions', 'candidates')
    Assert-ExactFields $Definition $topFields 'manifest'
    if ((Require-JsonString $Definition 'inventoryVersion' 'manifest') -ne 'b16-inventory-v1') {
        throw ('unsupported inventory version: {0}' -f (Get-JsonProperty $Definition 'inventoryVersion'))
    }
    $schemaVersion = Require-JsonValue $Definition 'schemaVersion' 'manifest'
    if (-not (Test-NumericScalar $schemaVersion) -or [int]$schemaVersion -ne 1) {
        throw ('unsupported manifest schema version: {0}' -f $schemaVersion)
    }
    [void](Require-JsonString $Definition 'generatedFrom' 'manifest')
    [void](Require-JsonString $Definition 'semanticMethod' 'manifest')

    $sourceRoots = Get-JsonProperty $Definition 'sourceRoots'
    Assert-JsonArray (,$sourceRoots) 'manifest.sourceRoots'
    foreach ($root in @($sourceRoots)) {
        if ($root -isnot [string]) { throw 'manifest.sourceRoots entries must be strings' }
        [void](Resolve-RepoRelativePath $Repo $root 'manifest.sourceRoots entry')
    }

    $regions = Get-JsonProperty $Definition 'scanRegions'
    Assert-JsonArray (,$regions) 'manifest.scanRegions'
    $regionIds = @{}
    $regionFields = @('id', 'sourceFile', 'startPattern', 'endPattern', 'candidatePattern')
    foreach ($region in @($regions)) {
        $context = 'manifest.scanRegions entry'
        Assert-ExactFields $region $regionFields $context
        $id = Require-JsonString $region 'id' $context
        if ($regionIds.ContainsKey($id)) { throw ('duplicate scan region id: {0}' -f $id) }
        $regionIds[$id] = $true
        $sourceFile = Require-JsonString $region 'sourceFile' $context
        [void](Resolve-RepoRelativePath $Repo $sourceFile ('{0}.sourceFile' -f $context))
        [void](Require-JsonString $region 'candidatePattern' $context)
        Assert-RegexPattern (Get-JsonProperty $region 'candidatePattern') ('{0}.candidatePattern' -f $context)
        foreach ($optionalPattern in @('startPattern', 'endPattern')) {
            if (Has-JsonProperty $region $optionalPattern) {
                $pattern = Require-JsonString $region $optionalPattern $context
                Assert-RegexPattern $pattern ('{0}.{1}' -f $context, $optionalPattern)
            }
        }
    }

    $candidates = Get-JsonProperty $Definition 'candidates'
    Assert-JsonArray (,$candidates) 'manifest.candidates'
    $candidateIds = @{}
    $candidateFields = @('id', 'sourceFile', 'sourcePattern', 'valueGroup', 'expectedValue', 'yamlExpectedValue', 'yamlFile', 'yamlPath', 'unit', 'category', 'scanStatus', 'semanticCheck', 'reason')
    $allowedStatuses = @('MIGRATABLE', 'EXCEPTION', 'GAP')
    $allowedCategories = @('CONTENT', 'PROTECTION_BOUNDARY', 'UNKNOWN_CANDIDATE', 'PLATFORM_PHYSICS', 'ALGORITHM_CONSTANT')
    $allowedChecks = @('exact-numeric', 'seconds-to-millis', 'delegate-reference', 'constant-boundary', 'formula-constant')
    $numericChecks = @('exact-numeric', 'seconds-to-millis', 'constant-boundary', 'formula-constant')
    foreach ($candidate in @($candidates)) {
        $context = 'manifest.candidates entry'
        Assert-ExactFields $candidate $candidateFields $context
        $id = Require-JsonString $candidate 'id' $context
        if ($candidateIds.ContainsKey($id)) { throw ('duplicate candidate id: {0}' -f $id) }
        $candidateIds[$id] = $true
        $sourceFile = Require-JsonString $candidate 'sourceFile' $context
        [void](Resolve-RepoRelativePath $Repo $sourceFile ('{0}.sourceFile' -f $context))
        $sourcePattern = Require-JsonString $candidate 'sourcePattern' $context
        Assert-RegexPattern $sourcePattern ('{0}.sourcePattern' -f $context)
        $valueGroup = Optional-JsonString $candidate 'valueGroup' $context
        if ($null -ne $valueGroup) {
            $regex = [regex]::new($sourcePattern)
            if ($regex.GetGroupNames() -notcontains $valueGroup) {
                throw ('{0}.valueGroup is not a named group in sourcePattern: {1}' -f $context, $valueGroup)
            }
        }
        $expectedValue = Require-JsonValue $candidate 'expectedValue' $context
        [void](Require-JsonString $candidate 'unit' $context)
        $category = Require-JsonString $candidate 'category' $context
        if ($allowedCategories -notcontains $category) { throw ('{0}.category is not allowed: {1}' -f $context, $category) }
        $scanStatus = Require-JsonString $candidate 'scanStatus' $context
        if ($allowedStatuses -notcontains $scanStatus) { throw ('{0}.scanStatus is not allowed: {1}' -f $context, $scanStatus) }
        $semanticCheck = Require-JsonString $candidate 'semanticCheck' $context
        if ($allowedChecks -notcontains $semanticCheck) { throw ('{0}.semanticCheck is not allowed: {1}' -f $context, $semanticCheck) }
        [void](Require-JsonString $candidate 'reason' $context)
        if ($semanticCheck -eq 'delegate-reference') {
            if ($expectedValue -isnot [string]) { throw ('{0}.expectedValue must be a string for delegate-reference' -f $context) }
            if ($null -ne $valueGroup) { throw ('{0}.valueGroup is not allowed for delegate-reference' -f $context) }
        } else {
            if (-not (Test-NumericScalar $expectedValue)) { throw ('{0}.expectedValue must be numeric for {1}' -f $context, $semanticCheck) }
            if ($null -eq $valueGroup) { throw ('{0}.valueGroup is required for {1}' -f $context, $semanticCheck) }
        }
        if (Has-JsonProperty $candidate 'yamlExpectedValue') {
            $yamlExpectedValue = Get-JsonProperty $candidate 'yamlExpectedValue'
            if ($null -eq $yamlExpectedValue -or ($semanticCheck -ne 'delegate-reference' -and -not (Test-NumericScalar $yamlExpectedValue))) {
                throw ('{0}.yamlExpectedValue has an invalid type' -f $context)
            }
        }
        $yamlFile = Optional-JsonString $candidate 'yamlFile' $context
        $yamlPath = Optional-JsonString $candidate 'yamlPath' $context
        if (($null -eq $yamlFile) -xor ($null -eq $yamlPath)) {
            throw ('{0}.yamlFile and yamlPath must be provided together' -f $context)
        }
        if ($null -ne $yamlFile) { [void](Resolve-RepoRelativePath $Repo $yamlFile ('{0}.yamlFile' -f $context)) }
        if ($scanStatus -eq 'MIGRATABLE' -and ($null -eq $yamlFile -or $null -eq $yamlPath)) {
            throw ('{0}.MIGRATABLE candidates require yamlFile and yamlPath' -f $context)
        }
    }
}

function Mask-JavaNonCode([string] $Text) {
    $builder = [Text.StringBuilder]::new($Text.Length)
    $state = 'code'
    $index = 0
    while ($index -lt $Text.Length) {
            $char = $Text[$index]
        if ($state -eq 'code') {
            if ($char -eq '/' -and $index + 1 -lt $Text.Length -and $Text[$index + 1] -eq '/') {
                [void]$builder.Append('  '); $state = 'line-comment'; $index += 2; continue
            }
            if ($char -eq '/' -and $index + 1 -lt $Text.Length -and $Text[$index + 1] -eq '*') {
                [void]$builder.Append('  '); $state = 'block-comment'; $index += 2; continue
            }
            if ($char -eq '"' -and $index + 2 -lt $Text.Length -and $Text.Substring($index, 3) -eq '"""') {
                [void]$builder.Append('   '); $state = 'text-block'; $index += 3; continue
            }
            if ($char -eq '"') { [void]$builder.Append(' '); $state = 'string'; $index++; continue }
            if ($char -eq "'") { [void]$builder.Append(' '); $state = 'char'; $index++; continue }
            [void]$builder.Append($char); $index++; continue
        }
        if ($state -eq 'line-comment') {
            if ($char -eq "`r" -or $char -eq "`n") { [void]$builder.Append($char); $state = 'code' } else { [void]$builder.Append(' ') }
            $index++; continue
        }
        if ($state -eq 'block-comment') {
            if ($char -eq '*' -and $index + 1 -lt $Text.Length -and $Text[$index + 1] -eq '/') {
                [void]$builder.Append('  '); $state = 'code'; $index += 2; continue
            }
            if ($char -eq "`r" -or $char -eq "`n") { [void]$builder.Append($char) } else { [void]$builder.Append(' ') }
            $index++; continue
        }
        if ($state -eq 'text-block') {
            if ($char -eq '"' -and $index + 2 -lt $Text.Length -and $Text.Substring($index, 3) -eq '"""') {
                [void]$builder.Append('   '); $state = 'code'; $index += 3; continue
            }
            if ($char -eq "`r" -or $char -eq "`n") { [void]$builder.Append($char) } else { [void]$builder.Append(' ') }
            $index++; continue
        }
        if ($char -eq '\\' -and $index + 1 -lt $Text.Length) {
            [void]$builder.Append('  '); $index += 2; continue
        }
        if (($state -eq 'string' -and $char -eq '"') -or ($state -eq 'char' -and $char -eq "'")) {
            [void]$builder.Append(' '); $state = 'code'; $index++; continue
        }
        if ($char -eq "`r" -or $char -eq "`n") { [void]$builder.Append($char) } else { [void]$builder.Append(' ') }
        $index++
    }
    return $builder.ToString()
}

function Get-JavaStatementWindow([string] $Text, [int] $Start, [int] $Length) {
    # For unbounded regions, inspect the complete Java statement containing the
    # semantic anchor. Use masked source so semicolons/braces inside comments or
    # string/char literals cannot truncate the window.
    $masked = Mask-JavaNonCode $Text
    $left = $Start
    while ($left -gt 0 -and $masked[$left - 1] -notmatch '[;{}]') { $left-- }
    $right = $Start + $Length
    while ($right -lt $Text.Length -and $masked[$right] -notmatch '[;{}]') { [void]($right++) }
    if ($right -lt $Text.Length) { $right++ }
    return [pscustomobject]@{ Start = $left; End = $right }
}

function Get-JavaNumericLiteralSpans([string] $Text) {
    $masked = Mask-JavaNonCode $Text
    $pattern = '(?<![A-Za-z0-9_$])(?:0[xX][0-9A-Fa-f](?:_?[0-9A-Fa-f])*(?:\.[0-9A-Fa-f](?:_?[0-9A-Fa-f])*)?[pP][+-]?[0-9](?:_?[0-9])*[fFdD]?|0[xX][0-9A-Fa-f](?:_?[0-9A-Fa-f])*[lL]?|0[bB][01](?:_?[01])*[lL]?|0[0-7](?:_?[0-7])*[lL]?|[0-9](?:_?[0-9])*\.(?:[0-9](?:_?[0-9])*)?(?:[eE][+-]?[0-9](?:_?[0-9])*)?[fFdD]?|\.[0-9](?:_?[0-9])*(?:[eE][+-]?[0-9](?:_?[0-9])*)?[fFdD]?|[0-9](?:_?[0-9])*(?:[eE][+-]?[0-9](?:_?[0-9])*)?[lLfFdD]?)(?![A-Za-z0-9_$])'
    foreach ($match in [regex]::Matches($masked, $pattern)) {
        [pscustomobject]@{ Start = $match.Index; End = $match.Index + $match.Length; Value = $Text.Substring($match.Index, $match.Length) }
    }
}

function Initialize-AstScanner {
    if ($script:AstScannerDirectory -and $script:AstScannerJava) { return }
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $javac -or $null -eq $java) {
        throw 'JDK AST scanner unavailable: both java and javac must be on PATH'
    }
    $source = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\java\InventoryAstScanner.java'))
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw ('JDK AST scanner source is missing: {0}' -f $source)
    }
    $temp = [IO.Directory]::CreateTempSubdirectory('b16-inventory-ast-').FullName
    try {
        $compileOutput = @(& $javac.Source -encoding UTF-8 -d $temp $source 2>&1)
        if ($LASTEXITCODE -ne 0) {
            throw ('javac failed: {0}' -f (($compileOutput | Out-String).Trim()))
        }
        $script:AstScannerDirectory = $temp
        $script:AstScannerJava = $java.Source
    } catch {
        Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
        throw ('JDK AST scanner unavailable: {0}' -f $_.Exception.Message)
    }
}

function Invoke-AstNumericLiteralSpans([string] $Path) {
    Initialize-AstScanner
    $output = @(& $script:AstScannerJava -cp $script:AstScannerDirectory InventoryAstScanner $Path 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw ('JDK AST scanner failed for {0}: {1}' -f $Path, (($output | Out-String).Trim()))
    }
    foreach ($line in $output) {
        if ([string]::IsNullOrWhiteSpace([string]$line)) { continue }
        $parts = ([string]$line) -split "`t", 5
        if ($parts.Count -ne 5 -or $parts[0] -ne 'L') {
            throw ('JDK AST scanner emitted an invalid span for {0}: {1}' -f $Path, $line)
        }
        [int]$start = 0; [int]$end = 0
        if (-not [int]::TryParse($parts[1], [Globalization.NumberStyles]::Integer, [Globalization.CultureInfo]::InvariantCulture, [ref]$start) -or
            -not [int]::TryParse($parts[2], [Globalization.NumberStyles]::Integer, [Globalization.CultureInfo]::InvariantCulture, [ref]$end) -or
            $start -lt 0 -or $end -le $start) {
            throw ('JDK AST scanner emitted an invalid span for {0}: {1}' -f $Path, $line)
        }
        [pscustomobject]@{ Start = $start; End = $end; Kind = $parts[3]; Value = $parts[4] }
    }
}

function Assert-AstMatchesLexicalFallback([string] $Text, [int] $WindowStart, [object[]] $AstSpans) {
    $astKeys = @($AstSpans | ForEach-Object { '{0}:{1}:{2}' -f $_.Start, $_.End, $_.Value } | Sort-Object)
    $lexicalKeys = @(Get-JavaNumericLiteralSpans $Text | ForEach-Object {
        '{0}:{1}:{2}' -f ($WindowStart + $_.Start), ($WindowStart + $_.End), $_.Value
    } | Sort-Object)
    if (($astKeys -join '|') -ne ($lexicalKeys -join '|')) {
        throw ('JDK AST and masked lexical fallback disagree in semantic window starting at {0}' -f $WindowStart)
    }
}

function Read-Text([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "missing file: $Path"
    }
    return [IO.File]::ReadAllText($Path)
}

function Get-LineNumber([string] $Text, [int] $Offset) {
    if ($Offset -le 0) { return 1 }
    return (($Text.Substring(0, $Offset) -split "`r?`n").Count)
}

function Parse-Scalar([string] $Raw) {
    $value = $Raw.Trim()
    if ($value -match '^#') { return $null }
    if ($value -match "^(['`"])(.*)\1$") { return $Matches[2] }
    if ($value -match '^(true|false)$') { return [bool]::Parse($value) }
    if ($value -match '^-?\d+$') { return [long]::Parse($value, [Globalization.CultureInfo]::InvariantCulture) }
    if ($value -match '^-?(?:\d+\.\d*|\d*\.\d+)(?:[eE][+-]?\d+)?$') {
        return [double]::Parse($value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture)
    }
    return $value
}

function Parse-InlineSequence([string] $Raw) {
    if ($Raw -notmatch '^\[(?<items>.*)\]$') { return $null }
    $inner = $Matches['items'].Trim()
    if ($inner.Length -eq 0) { return @() }
    return @($inner -split '\s*,\s*' | ForEach-Object { Parse-Scalar $_ })
}

function Remove-InlineComment([string] $Value) {
    $single = $false
    $double = $false
    for ($i = 0; $i -lt $Value.Length; $i++) {
        $char = $Value[$i]
        if ($char -eq "'" -and -not $double) { $single = -not $single }
        elseif ($char -eq '"' -and -not $single) { $double = -not $double }
        elseif ($char -eq '#' -and -not $single -and -not $double -and ($i -eq 0 -or [char]::IsWhiteSpace($Value[$i - 1]))) {
            return $Value.Substring(0, $i).TrimEnd()
        }
    }
    return $Value.TrimEnd()
}

function Read-ScalarYaml([string] $Path) {
    $result = [ordered]@{}
    $seenPaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $stack = [Collections.Generic.List[object]]::new()
    $lines = (Read-Text $Path) -split "`r?`n"
    for ($lineIndex = 0; $lineIndex -lt $lines.Count; $lineIndex++) {
        $line = $lines[$lineIndex]
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#') -or $line.TrimStart().StartsWith('- ')) { continue }
        if ($line -notmatch '^(?<indent>\s*)(?<key>[^:#][^:]*):(?:\s*(?<value>.*))?$') { continue }
        $indent = $Matches.indent.Length
        $key = $Matches.key.Trim().Trim("'").Trim('"')
        $rawValue = if ($null -eq $Matches.value) { '' } else { $Matches.value }
        $raw = Remove-InlineComment $rawValue
        while ($stack.Count -gt 0 -and $stack[$stack.Count - 1].Indent -ge $indent) { $stack.RemoveAt($stack.Count - 1) }
        $parts = @($stack | ForEach-Object Key) + $key
        $pathKey = $parts -join '.'
        if (-not $seenPaths.Add($pathKey)) {
            throw ('duplicate YAML path in {0}: {1} (line {2})' -f $Path, $pathKey, ($lineIndex + 1))
        }
        if (-not [string]::IsNullOrWhiteSpace($raw)) {
            $sequence = Parse-InlineSequence $raw
            if ($null -ne $sequence) {
                $result[$pathKey] = [pscustomobject]@{ Value = $sequence; Line = $lineIndex + 1 }
                for ($itemIndex = 0; $itemIndex -lt $sequence.Count; $itemIndex++) {
                    $result[('{0}[{1}]' -f $pathKey, $itemIndex)] = [pscustomobject]@{ Value = $sequence[$itemIndex]; Line = $lineIndex + 1 }
                }
            } else {
                $result[$pathKey] = [pscustomobject]@{ Value = Parse-Scalar $raw; Line = $lineIndex + 1 }
            }
        } else {
            $stack.Add([pscustomobject]@{ Indent = $indent; Key = $key })
        }
    }
    return $result
}

function Convert-Numeric([object] $Value) {
    if ($Value -is [int] -or $Value -is [long] -or $Value -is [double] -or $Value -is [decimal]) { return [double]$Value }
    $parsed = 0.0
    if ([double]::TryParse([string]$Value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$parsed)) { return $parsed }
    return $null
}

function Values-Equal([object] $Actual, [object] $Expected) {
    $actualNumber = Convert-Numeric $Actual
    $expectedNumber = Convert-Numeric $Expected
    if ($null -ne $actualNumber -and $null -ne $expectedNumber) { return [Math]::Abs($actualNumber - $expectedNumber) -lt 0.0000001 }
    return ([string]$Actual -ceq [string]$Expected)
}

function New-Row([object] $Candidate) {
    return [ordered]@{
        id = $Candidate.id
        sourceFile = $Candidate.sourceFile
        sourceLocation = $null
        yamlFile = $Candidate.yamlFile
        yamlPath = $Candidate.yamlPath
        value = $Candidate.expectedValue
        yamlValue = $null
        unit = $Candidate.unit
        category = $Candidate.category
        scanStatus = $Candidate.scanStatus
        semanticCheck = $Candidate.semanticCheck
        reason = $Candidate.reason
    }
}

function Add-Gap([Collections.Generic.List[object]] $Rows, [string] $Id, [string] $SourceFile, [int] $Line, [string] $Reason, [string] $Value = $null, [string] $SemanticCheck = 'lexical-numeric-fallback') {
    $location = if ($Line -gt 0) { '{0}:{1}' -f $SourceFile, $Line } else { '{0}:unknown' -f $SourceFile }
    $Rows.Add([ordered]@{
        id = $Id
        sourceFile = $SourceFile
        sourceLocation = $location
        yamlFile = $null
        yamlPath = $null
        value = $Value
        yamlValue = $null
        unit = $null
        category = 'UNKNOWN_CANDIDATE'
        scanStatus = 'GAP'
        semanticCheck = $SemanticCheck
        reason = $Reason
    })
}

try {
    $repo = [IO.Path]::GetFullPath($RepoRoot)
    $manifestPath = Resolve-RepoPath $repo $Manifest
    $output = [IO.Path]::GetFullPath($OutputDirectory)
    Assert-NoExternalReparsePoint $output $repo 'OutputDirectory'
    if (Test-Path -LiteralPath $output) { throw ('output directory already exists: {0}' -f $output) }
    if (Test-Path -LiteralPath ($output + '.staging')) { throw ('staging directory already exists: {0}.staging' -f $output) }

    $definition = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    Validate-Manifest $definition $repo

    $sources = @{}
    $sourcePaths = @{}
    $yaml = @{}
    foreach ($candidate in $definition.candidates) {
        if (-not $sources.ContainsKey($candidate.sourceFile)) {
            $sourcePath = Resolve-RepoRelativePath $repo $candidate.sourceFile 'candidate.sourceFile'
            $sources[$candidate.sourceFile] = Read-Text $sourcePath
            $sourcePaths[$candidate.sourceFile] = $sourcePath
        }
        if ($candidate.yamlFile -and -not $yaml.ContainsKey($candidate.yamlFile)) {
            $yamlPath = Resolve-RepoRelativePath $repo $candidate.yamlFile 'candidate.yamlFile'
            $yaml[$candidate.yamlFile] = Read-ScalarYaml $yamlPath
        }
    }
    foreach ($region in $definition.scanRegions) {
        if (-not $sources.ContainsKey($region.sourceFile)) {
            $sourcePath = Resolve-RepoRelativePath $repo $region.sourceFile 'scanRegion.sourceFile'
            $sources[$region.sourceFile] = Read-Text $sourcePath
            $sourcePaths[$region.sourceFile] = $sourcePath
        }
    }

    $astSpansBySource = @{}
    foreach ($region in $definition.scanRegions) {
        if (-not $astSpansBySource.ContainsKey($region.sourceFile)) {
            $astSpansBySource[$region.sourceFile] = @(Invoke-AstNumericLiteralSpans $sourcePaths[$region.sourceFile])
        }
    }

    $rows = [Collections.Generic.List[object]]::new()
    $coveredSpans = @{}
    foreach ($candidate in $definition.candidates) {
        $row = New-Row $candidate
        $text = $sources[$candidate.sourceFile]
        $matches = [regex]::Matches($text, $candidate.sourcePattern, [Text.RegularExpressions.RegexOptions]::Multiline)
        if ($matches.Count -ne 1) {
            $row.scanStatus = 'GAP'
            $row.reason = 'source semantic pattern matched {0} times; expected exactly one' -f $matches.Count
            $rows.Add($row)
            continue
        }
        $match = $matches[0]
        $line = Get-LineNumber $text $match.Index
        $row.sourceLocation = '{0}:{1}' -f $candidate.sourceFile, $line
        if ($candidate.valueGroup) {
            $valueGroup = $match.Groups[$candidate.valueGroup]
            if ($null -eq $valueGroup -or -not $valueGroup.Success) {
                $row.scanStatus = 'GAP'
                $row.reason = 'manifest value group was not captured: {0}' -f $candidate.valueGroup
            } else {
                $row.value = $valueGroup.Value
            }
        }
        if ($candidate.yamlFile -and $candidate.yamlPath) {
            $yamlValues = $yaml[$candidate.yamlFile]
            if ($null -eq $yamlValues) {
                if ($candidate.scanStatus -eq 'EXCEPTION') {
                    $row.reason = '{0}; YAML file was not loaded because this is an EXCEPTION' -f $candidate.reason
                } else {
                    $row.scanStatus = 'GAP'
                    $row.reason = 'YAML file was not loaded: {0}' -f $candidate.yamlFile
                }
            } elseif (-not $yamlValues.Contains($candidate.yamlPath)) {
                if ($candidate.scanStatus -eq 'EXCEPTION') {
                    $row.reason = '{0}; YAML comparison skipped because this is an EXCEPTION' -f $candidate.reason
                } else {
                    $row.scanStatus = 'GAP'
                    $row.reason = 'YAML target path not found: {0}' -f $candidate.yamlPath
                }
            } else {
                $row.yamlValue = $yamlValues[$candidate.yamlPath].Value
                $expectedYaml = if ($null -ne $candidate.yamlExpectedValue) { $candidate.yamlExpectedValue } else { $candidate.expectedValue }
                if ($candidate.semanticCheck -eq 'seconds-to-millis') { $expectedYaml = [double]$candidate.expectedValue * 1000 }
                if ($candidate.semanticCheck -ne 'delegate-reference' -and -not (Values-Equal $row.yamlValue $expectedYaml)) {
                    if ($candidate.scanStatus -eq 'EXCEPTION') {
                        $row.reason = '{0}; YAML differs intentionally or is a related runtime value: expected {1}, actual {2}' -f $candidate.reason, $expectedYaml, $row.yamlValue
                    } else {
                        $row.scanStatus = 'GAP'
                        $row.reason = 'YAML value mismatch at {0}: expected {1}, actual {2}' -f $candidate.yamlPath, $expectedYaml, $row.yamlValue
                    }
                }
            }
        }
        if (-not $coveredSpans.ContainsKey($candidate.sourceFile)) { $coveredSpans[$candidate.sourceFile] = [Collections.Generic.List[object]]::new() }
        $coveredSpans[$candidate.sourceFile].Add([pscustomobject]@{ Start = $match.Index; End = $match.Index + $match.Length })
        $rows.Add($row)
    }

    foreach ($region in $definition.scanRegions) {
        $text = $sources[$region.sourceFile]
        if ($null -eq $text) {
            Add-Gap $rows ('region:{0}' -f $region.id) $region.sourceFile 0 'scan region source was not loaded'
            continue
        }
        $start = 0
        if ($region.startPattern) {
            $startMatch = [regex]::Match($text, $region.startPattern, [Text.RegularExpressions.RegexOptions]::Multiline)
            if (-not $startMatch.Success) { Add-Gap $rows ('region:{0}' -f $region.id) $region.sourceFile 0 'scan region start marker not found'; continue }
            $start = $startMatch.Index + $startMatch.Length
        }
        $end = $text.Length
        if ($region.endPattern) {
            $tail = $text.Substring($start)
            $endMatch = [regex]::Match($tail, $region.endPattern, [Text.RegularExpressions.RegexOptions]::Multiline)
            if (-not $endMatch.Success) { Add-Gap $rows ('region:{0}' -f $region.id) $region.sourceFile 0 'scan region end marker not found'; continue }
            $end = $start + $endMatch.Index
        }
        $segment = $text.Substring($start, $end - $start)
        $uncoveredSpans = [Collections.Generic.List[object]]::new()
        $semanticWindows = [Collections.Generic.List[object]]::new()
        $hasSemanticBounds = (-not [string]::IsNullOrWhiteSpace([string](Get-JsonProperty $region 'startPattern'))) -or
            (-not [string]::IsNullOrWhiteSpace([string](Get-JsonProperty $region 'endPattern')))
        if ($hasSemanticBounds) {
            $semanticWindows.Add([pscustomobject]@{ Start = $start; End = $end })
        }
        foreach ($hit in [regex]::Matches($segment, $region.candidatePattern, [Text.RegularExpressions.RegexOptions]::Multiline)) {
            $absolute = $start + $hit.Index
            if (-not $hasSemanticBounds) {
                $window = Get-JavaStatementWindow $text $absolute $hit.Length
                $semanticWindows.Add($window)
            }
            $covered = $false
            $spans = if ($coveredSpans.ContainsKey($region.sourceFile)) { $coveredSpans[$region.sourceFile] } else { @() }
            foreach ($span in $spans) {
                if ($absolute -ge $span.Start -and $absolute -lt $span.End) { $covered = $true; break }
            }
            if (-not $covered) {
                $line = Get-LineNumber $text $absolute
                $valueGroup = $hit.Groups['value']
                $value = if ($null -ne $valueGroup -and $valueGroup.Success) { $valueGroup.Value } else { $null }
                Add-Gap $rows ('unmapped:{0}:{1}' -f $region.id, $line) $region.sourceFile $line 'targeted Java candidate is not covered by a manifest semantic probe' $value
                $uncoveredSpans.Add([pscustomobject]@{ Start = $absolute; End = $absolute + $hit.Length })
            }
        }
        foreach ($window in $semanticWindows) {
            $astWindowSpans = @($astSpansBySource[$region.sourceFile] | Where-Object {
                $_.Start -ge $window.Start -and $_.End -le $window.End
            })
            $windowText = $text.Substring($window.Start, $window.End - $window.Start)
            Assert-AstMatchesLexicalFallback $windowText $window.Start $astWindowSpans
            foreach ($literal in $astWindowSpans) {
                $absolute = $literal.Start
                $literalEnd = $literal.End
                $covered = $false
                $spans = if ($coveredSpans.ContainsKey($region.sourceFile)) { $coveredSpans[$region.sourceFile] } else { @() }
                foreach ($span in $spans) {
                    if ($absolute -ge $span.Start -and $literalEnd -le $span.End) { $covered = $true; break }
                }
                if (-not $covered) {
                    foreach ($span in $uncoveredSpans) {
                        if ($absolute -ge $span.Start -and $literalEnd -le $span.End) { $covered = $true; break }
                    }
                }
                if (-not $covered) {
                    $line = Get-LineNumber $text $absolute
                    Add-Gap $rows ('unmapped:{0}:{1}' -f $region.id, $line) $region.sourceFile $line 'numeric Java literal is not covered by a manifest semantic probe' $literal.Value 'javac-tree-ast'
                    $uncoveredSpans.Add([pscustomobject]@{ Start = $absolute; End = $literalEnd })
                }
            }
        }
    }

    $status = if (@($rows | Where-Object scanStatus -eq 'GAP').Count -gt 0) { 'GAP' } else { 'VALID' }
    $report = [ordered]@{
        inventoryVersion = $definition.inventoryVersion
        schemaVersion = $definition.schemaVersion
        status = $status
        generatedFrom = $definition.generatedFrom
        semanticMethod = 'JDK JavacTree numeric literal spans with masked lexical fallback cross-check; no whole-language completeness claim'
        coverage = [ordered]@{
            sourceRoots = $definition.sourceRoots
            scanRegions = @($definition.scanRegions).Count
            candidates = @($definition.candidates).Count
            migratable = @($rows | Where-Object scanStatus -eq 'MIGRATABLE').Count
            exceptions = @($rows | Where-Object scanStatus -eq 'EXCEPTION').Count
            gaps = @($rows | Where-Object scanStatus -eq 'GAP').Count
        }
        candidates = @($rows | Sort-Object id)
    }
    $lf = [string][char]10
    $crlf = ([string][char]13) + $lf
    $json = ($report | ConvertTo-Json -Depth 12).Replace($crlf, $lf)
    $csv = ($rows | Sort-Object id | Select-Object id,sourceFile,sourceLocation,yamlFile,yamlPath,value,yamlValue,unit,category,scanStatus,semanticCheck,reason | ConvertTo-Csv -NoTypeInformation) -join $lf
    $markdown = @(
        '# B16 Java-/YAML-Inventar v1',
        '',
        ('- Status: **{0}**' -f $status),
        ('- Kandidaten: {0}' -f $report.coverage.candidates),
        ('- MIGRATABLE: {0}' -f $report.coverage.migratable),
        ('- EXCEPTION: {0}' -f $report.coverage.exceptions),
        ('- GAP: {0}' -f $report.coverage.gaps),
        '',
        '| ID | Fundstelle | YAML-Ziel | Wert | Einheit | Kategorie | Status |',
        '|---|---|---|---:|---|---|---|'
    )
    foreach ($row in ($rows | Sort-Object id)) {
        $markdown += ('| {0} | {1} | {2}:{3} | {4} | {5} | {6} | {7} |' -f $row.id, $row.sourceLocation, $row.yamlFile, $row.yamlPath, $row.value, $row.unit, $row.category, $row.scanStatus)
    }

    $parent = Split-Path -Parent $output
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $staging = $output + '.staging'
    New-Item -ItemType Directory -Path $staging | Out-Null
    try {
        [IO.File]::WriteAllText((Join-Path $staging 'b16-inventory.json'), $json, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $staging 'b16-inventory.csv'), $csv + $lf, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $staging 'b16-inventory.md'), ($markdown -join $lf) + $lf, [Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $staging -Destination $output
    } catch {
        Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
        throw
    }
    Write-Output ('B16 inventory status={0} candidates={1} migratable={2} exceptions={3} gaps={4}' -f $status, $report.coverage.candidates, $report.coverage.migratable, $report.coverage.exceptions, $report.coverage.gaps)
    if ($status -eq 'GAP') { exit 1 }
    exit 0
} catch {
    $errorMessage = $_.Exception.Message
    [Console]::Error.WriteLine(('B16 inventory failed: {0} ({1})' -f $errorMessage, $_.InvocationInfo.PositionMessage))
    exit 2
} finally {
    if ($script:AstScannerDirectory -and (Test-Path -LiteralPath $script:AstScannerDirectory)) {
        Remove-Item -LiteralPath $script:AstScannerDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
    $script:AstScannerDirectory = $null
    $script:AstScannerJava = $null
}
