[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet('Install', 'Status', 'Remove')]
    [string]$Mode,
    [ValidatePattern('^(?:[01][0-9]|2[0-3]):[0-5][0-9]$')]
    [string]$At = '20:00',
    [string]$ExternalDestination,
    [string]$GitBashPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$TaskName = 'Lavanda Flow - PostgreSQL Backup'
$runnerPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'run-scheduled-backup.ps1'))

function Assert-ScheduledTasksAvailable {
    if (-not (Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue)) {
        throw 'Windows Scheduled Tasks cmdlets are required. Run this script in Windows PowerShell on the operator workstation.'
    }
}

function Resolve-ExistingAbsolutePath {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Label,
        [Parameter(Mandatory)][ValidateSet('Leaf', 'Container')][string]$PathType
    )

    if (-not [System.IO.Path]::IsPathRooted($Path)) {
        throw "$Label must be an absolute Windows path."
    }
    if (-not (Test-Path -LiteralPath $Path -PathType $PathType)) {
        throw "$Label does not exist: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).ProviderPath
}

function Quote-NativeArgument {
    param([Parameter(Mandatory)][string]$Value)

    if ($Value.Contains('"')) {
        throw 'Task arguments cannot contain a double quote.'
    }
    $escaped = [regex]::Replace($Value, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

Assert-ScheduledTasksAvailable

switch ($Mode) {
    'Install' {
        if (-not (Test-Path -LiteralPath $runnerPath -PathType Leaf)) {
            throw "Scheduled backup runner not found: $runnerPath"
        }

        $argumentParts = @(
            '-NoProfile',
            '-NonInteractive',
            '-ExecutionPolicy',
            'Bypass',
            '-File',
            (Quote-NativeArgument -Value $runnerPath)
        )

        if ($ExternalDestination) {
            $resolvedDestination = Resolve-ExistingAbsolutePath -Path $ExternalDestination -Label 'External destination' -PathType Container
            $argumentParts += '-ExternalDestination'
            $argumentParts += Quote-NativeArgument -Value $resolvedDestination
        }
        if ($GitBashPath) {
            $resolvedGitBash = Resolve-ExistingAbsolutePath -Path $GitBashPath -Label 'Git Bash path' -PathType Leaf
            $argumentParts += '-GitBashPath'
            $argumentParts += Quote-NativeArgument -Value $resolvedGitBash
        }

        $powerShellExecutable = if ($PSVersionTable.PSEdition -eq 'Core') {
            Join-Path $PSHOME 'pwsh.exe'
        }
        else {
            Join-Path $PSHOME 'powershell.exe'
        }
        $taskAction = New-ScheduledTaskAction -Execute $powerShellExecutable -Argument ($argumentParts -join ' ')
        $scheduleTime = [DateTime]::ParseExact($At, 'HH:mm', [Globalization.CultureInfo]::InvariantCulture)
        $trigger = New-ScheduledTaskTrigger -Daily -At $scheduleTime
        $settings = New-ScheduledTaskSettingsSet `
            -StartWhenAvailable `
            -AllowStartIfOnBatteries `
            -DontStopIfGoingOnBatteries `
            -MultipleInstances IgnoreNew `
            -ExecutionTimeLimit (New-TimeSpan -Hours 2)
        $principal = New-ScheduledTaskPrincipal `
            -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) `
            -LogonType Interactive `
            -RunLevel Limited

        Register-ScheduledTask `
            -TaskName $TaskName `
            -Action $taskAction `
            -Trigger $trigger `
            -Settings $settings `
            -Principal $principal `
            -Description 'Creates, verifies, copies, and retains Lavanda Flow operational PostgreSQL backups.' `
            -Force | Out-Null

        Write-Output "Installed or updated '$TaskName' for $At local time."
        Write-Output 'The task runs only while the current Windows user is signed in, starts missed runs when available, never wakes the notebook, and ignores overlapping starts.'
    }

    'Status' {
        $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
        if (-not $task) {
            Write-Output "Scheduled task '$TaskName' is not installed."
            exit 1
        }
        $info = Get-ScheduledTaskInfo -TaskName $TaskName
        [pscustomobject]@{
            TaskName = $task.TaskName
            State = $task.State
            UserId = $task.Principal.UserId
            LogonType = $task.Principal.LogonType
            NextRunTime = $info.NextRunTime
            LastRunTime = $info.LastRunTime
            LastTaskResult = $info.LastTaskResult
            StartWhenAvailable = $task.Settings.StartWhenAvailable
            MultipleInstances = $task.Settings.MultipleInstances
            Action = $task.Actions.Execute
            Arguments = $task.Actions.Arguments
        }
    }

    'Remove' {
        $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
        if ($task) {
            Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
            Write-Output "Removed scheduled task '$TaskName'. Backup artifacts and configuration were not changed."
        }
        else {
            Write-Output "Scheduled task '$TaskName' was already absent."
        }
    }
}
