[CmdletBinding()]
param(
    [string]$ExternalDestination,
    [string]$GitBashPath,
    [ValidateRange(30, 1800)]
    [int]$DockerReadyTimeoutSeconds = 300
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$BackupNamePattern = '^lavanda-flow-(?<Timestamp>[0-9]{8}T[0-9]{6}Z)\.dump$'
$DiagnosticLogNamePattern = '^scheduled-backup-[0-9]{8}T[0-9]{9}Z-[0-9]+\.log$'
$MaxDiagnosticLogs = 30
$TaskMutexName = 'Local\LavandaFlowPostgresqlBackup'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$backupDirectory = Join-Path $repositoryRoot 'backups'
$logDirectory = Join-Path $backupDirectory 'logs'
$logPath = Join-Path $logDirectory ('scheduled-backup-{0}-{1}.log' -f [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'), $PID)
$script:ExitCode = 1

function Write-RunLog {
    param([Parameter(Mandatory)][string]$Message)

    $line = '{0} {1}' -f [DateTime]::UtcNow.ToString('o'), $Message
    Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
    Write-Output $line
}

function Invoke-DiagnosticLogRetention {
    $logs = @(
        Get-ChildItem -LiteralPath $logDirectory -File -ErrorAction Stop |
            Where-Object { $_.Name -match $DiagnosticLogNamePattern } |
            Sort-Object -Property Name -Descending
    )

    foreach ($oldLog in @($logs | Select-Object -Skip $MaxDiagnosticLogs)) {
        Remove-Item -LiteralPath $oldLog.FullName -Force
    }
}

function Resolve-GitBash {
    param([string]$ExplicitPath)

    if ($ExplicitPath) {
        if (-not (Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) {
            throw "Git Bash was not found at the configured path: $ExplicitPath"
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).ProviderPath
    }

    $candidates = @()
    if ($env:ProgramFiles) {
        $candidates += Join-Path $env:ProgramFiles 'Git\bin\bash.exe'
    }
    if (${env:ProgramFiles(x86)}) {
        $candidates += Join-Path ${env:ProgramFiles(x86)} 'Git\bin\bash.exe'
    }
    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA 'Programs\Git\bin\bash.exe'
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).ProviderPath
        }
    }

    throw 'Git Bash was not found. Reinstall the task with -GitBashPath pointing to bash.exe.'
}

function Resolve-Cygpath {
    param([Parameter(Mandatory)][string]$BashPath)

    $bashDirectory = Split-Path -Parent $BashPath
    $gitRoot = Split-Path -Parent $bashDirectory
    $candidates = @(
        (Join-Path $bashDirectory 'cygpath.exe'),
        (Join-Path $gitRoot 'usr\bin\cygpath.exe')
    )

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).ProviderPath
        }
    }

    throw "cygpath.exe was not found beside the configured Git Bash installation: $BashPath"
}

function Convert-ToBashPath {
    param(
        [Parameter(Mandatory)][string]$CygpathPath,
        [Parameter(Mandatory)][string]$WindowsPath
    )

    $result = @(& $CygpathPath '-u' '--' $WindowsPath 2>$null)
    if ($LASTEXITCODE -ne 0 -or $result.Count -ne 1 -or [string]::IsNullOrWhiteSpace($result[0])) {
        throw "Could not convert a Windows path for Git Bash: $WindowsPath"
    }
    return $result[0]
}

function Convert-ToWindowsPath {
    param(
        [Parameter(Mandatory)][string]$CygpathPath,
        [Parameter(Mandatory)][string]$BashPath
    )

    $result = @(& $CygpathPath '-w' '--' $BashPath 2>$null)
    if ($LASTEXITCODE -ne 0 -or $result.Count -ne 1 -or [string]::IsNullOrWhiteSpace($result[0])) {
        throw "Could not convert a Git Bash artifact path: $BashPath"
    }
    return [System.IO.Path]::GetFullPath($result[0])
}

function Wait-ForManagedPostgres {
    param([Parameter(Mandatory)][int]$TimeoutSeconds)

    $environmentFile = Join-Path $repositoryRoot '.env.operational'
    $backupComposeFile = Join-Path $repositoryRoot 'compose.backup.yaml'
    $dockerCommand = Get-Command 'docker.exe' -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $dockerCommand) {
        throw 'Docker CLI was not found on the Windows PATH.'
    }
    $dockerPath = $dockerCommand.Source
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        do {
            & $dockerPath 'info' 1>$null 2>$null
            if ($LASTEXITCODE -eq 0) {
                & $dockerPath 'compose' '-f' $backupComposeFile '--env-file' $environmentFile `
                    'run' '--rm' '-T' '--no-deps' 'postgres-tooling' `
                    'database_url="${SPRING_DATASOURCE_URL#jdbc:}"; exec psql --dbname="$database_url" --command="SELECT 1"' `
                    1>$null 2>$null
                if ($LASTEXITCODE -eq 0) {
                    return
                }
            }
            Start-Sleep -Seconds 5
        } while ([DateTime]::UtcNow -lt $deadline)
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    throw "Docker Desktop and managed PostgreSQL did not become reachable within $TimeoutSeconds seconds."
}

function Get-ChecksumRecord {
    param(
        [Parameter(Mandatory)][string]$ChecksumPath,
        [Parameter(Mandatory)][string]$ExpectedDumpName
    )

    if (-not (Test-Path -LiteralPath $ChecksumPath -PathType Leaf)) {
        return $null
    }

    $content = Get-Content -LiteralPath $ChecksumPath -Raw
    $match = [regex]::Match($content, '\A(?<Hash>[0-9A-Fa-f]{64})[ \t]+\*?(?<Name>[^\r\n]+)\r?\n?\z')
    if (-not $match.Success -or $match.Groups['Name'].Value -cne $ExpectedDumpName) {
        return $null
    }

    return $match.Groups['Hash'].Value.ToUpperInvariant()
}

function Get-ValidBackupPair {
    param([Parameter(Mandatory)][string]$DumpPath)

    if (-not (Test-Path -LiteralPath $DumpPath -PathType Leaf)) {
        return $null
    }

    $dump = Get-Item -LiteralPath $DumpPath
    $nameMatch = [regex]::Match($dump.Name, $BackupNamePattern)
    if (-not $nameMatch.Success) {
        return $null
    }

    try {
        $timestamp = [DateTime]::ParseExact(
            $nameMatch.Groups['Timestamp'].Value,
            'yyyyMMddTHHmmssZ',
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal
        )
    }
    catch {
        return $null
    }

    $checksumPath = $dump.FullName + '.sha256'
    $expectedHash = Get-ChecksumRecord -ChecksumPath $checksumPath -ExpectedDumpName $dump.Name
    if (-not $expectedHash) {
        return $null
    }

    $actualHash = (Get-FileHash -LiteralPath $dump.FullName -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($actualHash -cne $expectedHash) {
        return $null
    }

    return [pscustomobject]@{
        Dump = $dump.FullName
        Checksum = [System.IO.Path]::GetFullPath($checksumPath)
        Hash = $actualHash
        Timestamp = $timestamp
    }
}

function Get-SafeFailureSummary {
    param([Parameter(Mandatory)][string[]]$Lines)

    $safePrefixes = @(
        'Required command not found:',
        'Operational environment file not found:',
        'Operational PostgreSQL service is not running',
        'Operational PostgreSQL service is not reachable.',
        'Backup artifact already exists:',
        'Usage:'
    )

    foreach ($line in $Lines) {
        foreach ($prefix in $safePrefixes) {
            if ($line.StartsWith($prefix, [StringComparison]::Ordinal)) {
                return $line
            }
        }
    }
    return 'backup-postgres.sh failed; run the manual backup command for detailed console diagnostics.'
}

function Publish-ExternalBackup {
    param(
        [Parameter(Mandatory)]$Pair,
        [Parameter(Mandatory)][string]$Destination
    )

    if (-not [System.IO.Path]::IsPathRooted($Destination)) {
        throw 'The external backup destination must be an absolute Windows path.'
    }
    if (-not (Test-Path -LiteralPath $Destination -PathType Container)) {
        throw "The external backup destination is unavailable: $Destination"
    }

    $destinationPath = (Resolve-Path -LiteralPath $Destination).ProviderPath
    if ([string]::Equals(
        $destinationPath.TrimEnd('\', '/'),
        ([System.IO.Path]::GetFullPath($backupDirectory)).TrimEnd('\', '/'),
        [StringComparison]::OrdinalIgnoreCase
    )) {
        throw 'The external backup destination cannot be the local backups directory.'
    }

    $dumpName = [System.IO.Path]::GetFileName($Pair.Dump)
    $checksumName = [System.IO.Path]::GetFileName($Pair.Checksum)
    $stagingDirectory = Join-Path $destinationPath ('.lavanda-flow-copy-{0}.partial' -f [Guid]::NewGuid().ToString('N'))
    $stagedDump = Join-Path $stagingDirectory $dumpName
    $stagedChecksum = Join-Path $stagingDirectory $checksumName
    $finalDump = Join-Path $destinationPath $dumpName
    $finalChecksum = Join-Path $destinationPath $checksumName
    $publishedDump = $false
    $publishedChecksum = $false
    $publicationSucceeded = $false

    New-Item -ItemType Directory -Path $stagingDirectory | Out-Null
    try {
        Copy-Item -LiteralPath $Pair.Dump -Destination $stagedDump
        Copy-Item -LiteralPath $Pair.Checksum -Destination $stagedChecksum

        $stagedPair = Get-ValidBackupPair -DumpPath $stagedDump
        if (-not $stagedPair -or $stagedPair.Hash -cne $Pair.Hash) {
            throw 'The staged external backup failed checksum verification.'
        }

        $finalDumpExists = Test-Path -LiteralPath $finalDump
        $finalChecksumExists = Test-Path -LiteralPath $finalChecksum
        if ($finalDumpExists -or $finalChecksumExists) {
            throw "An external artifact already uses the backup name: $dumpName"
        }

        Move-Item -LiteralPath $stagedDump -Destination $finalDump
        $publishedDump = $true
        Move-Item -LiteralPath $stagedChecksum -Destination $finalChecksum
        $publishedChecksum = $true

        $publishedPair = Get-ValidBackupPair -DumpPath $finalDump
        if (-not $publishedPair -or $publishedPair.Hash -cne $Pair.Hash) {
            throw 'The published external backup failed checksum verification.'
        }
        $publicationSucceeded = $true
        Write-RunLog "External backup copied and verified: $finalDump"
    }
    finally {
        $rollbackFailure = $null
        try {
            if (-not $publicationSucceeded) {
                $publishedArtifacts = @()
                if ($publishedChecksum) {
                    $publishedArtifacts += $finalChecksum
                }
                if ($publishedDump) {
                    $publishedArtifacts += $finalDump
                }
                foreach ($artifact in $publishedArtifacts) {
                    if (Test-Path -LiteralPath $artifact) {
                        try {
                            Remove-Item -LiteralPath $artifact -Force -ErrorAction Stop
                        }
                        catch {
                            $rollbackFailure = $_.Exception
                        }
                    }
                }
            }
        }
        finally {
            if (Test-Path -LiteralPath $stagingDirectory) {
                Remove-Item -LiteralPath $stagingDirectory -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
        if ($rollbackFailure) {
            throw "External publication rollback failed: $($rollbackFailure.Message)"
        }
    }
}

function Invoke-LocalRetention {
    $validPairs = @(
        Get-ChildItem -LiteralPath $backupDirectory -File -ErrorAction Stop |
            Where-Object { $_.Name -match $BackupNamePattern } |
            ForEach-Object { Get-ValidBackupPair -DumpPath $_.FullName } |
            Where-Object { $null -ne $_ } |
            Sort-Object -Property Timestamp -Descending
    )

    if ($validPairs.Count -lt 8) {
        Write-RunLog "Retention skipped: $($validPairs.Count) valid routine backup pair(s)."
        return
    }

    foreach ($pair in @($validPairs | Select-Object -Skip 7)) {
        if (-not (Get-ValidBackupPair -DumpPath $pair.Dump)) {
            throw "Retention stopped because a candidate pair changed during validation: $($pair.Dump)"
        }
        Remove-Item -LiteralPath $pair.Dump -Force
        Remove-Item -LiteralPath $pair.Checksum -Force
        Write-RunLog "Retention removed old routine backup pair: $($pair.Dump)"
    }
}

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$mutex = New-Object System.Threading.Mutex($false, $TaskMutexName)
$mutexAcquired = $false

try {
    Write-RunLog 'Scheduled backup started.'
    Invoke-DiagnosticLogRetention
    try {
        $mutexAcquired = $mutex.WaitOne(0)
    }
    catch [System.Threading.AbandonedMutexException] {
        $mutexAcquired = $true
    }
    if (-not $mutexAcquired) {
        throw 'Another scheduled backup run is already active.'
    }

    $resolvedBash = Resolve-GitBash -ExplicitPath $GitBashPath
    $cygpath = Resolve-Cygpath -BashPath $resolvedBash
    $backupScript = Join-Path $repositoryRoot 'scripts\operations\backup-postgres.sh'
    $bashBackupScript = Convert-ToBashPath -CygpathPath $cygpath -WindowsPath $backupScript

    Write-RunLog "Waiting up to $DockerReadyTimeoutSeconds seconds for Docker Desktop and managed PostgreSQL reachability."
    Wait-ForManagedPostgres -TimeoutSeconds $DockerReadyTimeoutSeconds
    Write-RunLog 'Docker Desktop and managed PostgreSQL are reachable; invoking the authoritative backup script.'

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $backupOutput = @(& $resolvedBash '--noprofile' '--norc' $bashBackupScript 2>&1)
        $backupExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $backupLines = @($backupOutput | ForEach-Object { $_.ToString() })
    if ($backupExitCode -ne 0) {
        $script:ExitCode = if ($backupExitCode) { $backupExitCode } else { 1 }
        $summary = Get-SafeFailureSummary -Lines $backupLines
        throw "Backup creation failed with exit code $backupExitCode. $summary"
    }

    $backupRecords = @()
    $checksumRecords = @()
    foreach ($line in $backupLines) {
        $backupMatch = [regex]::Match($line, '^Backup created: (?<Path>.+)$')
        if ($backupMatch.Success) {
            $backupRecords += $backupMatch.Groups['Path'].Value
        }
        $checksumMatch = [regex]::Match($line, '^Checksum created: (?<Path>.+)$')
        if ($checksumMatch.Success) {
            $checksumRecords += $checksumMatch.Groups['Path'].Value
        }
    }
    if ($backupRecords.Count -ne 1 -or $checksumRecords.Count -ne 1) {
        throw 'backup-postgres.sh did not emit exactly one backup path and one checksum path.'
    }

    $dumpPath = Convert-ToWindowsPath -CygpathPath $cygpath -BashPath $backupRecords[0]
    $checksumPath = Convert-ToWindowsPath -CygpathPath $cygpath -BashPath $checksumRecords[0]
    if ($checksumPath -cne ($dumpPath + '.sha256')) {
        throw 'The emitted checksum path does not match the emitted backup path.'
    }

    $resolvedBackupDirectory = [System.IO.Path]::GetFullPath($backupDirectory).TrimEnd('\', '/')
    $emittedDirectory = [System.IO.Path]::GetDirectoryName($dumpPath).TrimEnd('\', '/')
    if (-not [string]::Equals($resolvedBackupDirectory, $emittedDirectory, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The scheduled backup was not created in the repository routine-backup directory.'
    }

    $pair = Get-ValidBackupPair -DumpPath $dumpPath
    if (-not $pair) {
        throw 'The backup script output did not identify a checksum-valid backup pair.'
    }
    Write-RunLog "Local backup created and verified: $($pair.Dump)"

    if ($ExternalDestination) {
        Publish-ExternalBackup -Pair $pair -Destination $ExternalDestination
    }
    else {
        Write-RunLog 'No external destination is configured; the run produced a local backup only.'
    }

    Invoke-LocalRetention
    Write-RunLog 'Scheduled backup completed successfully.'
    $script:ExitCode = 0
}
catch {
    Write-RunLog "Scheduled backup failed: $($_.Exception.Message)"
    [Console]::Error.WriteLine($_.Exception.Message)
}
finally {
    if ($mutexAcquired) {
        $mutex.ReleaseMutex()
    }
    $mutex.Dispose()
}

exit $script:ExitCode
