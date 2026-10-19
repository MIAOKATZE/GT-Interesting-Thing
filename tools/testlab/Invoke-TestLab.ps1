param(
    [Parameter(Mandatory=$true)][ValidateSet('run','start','status','command','probe','stop','recover-lock')][string]$Action,
    [ValidateSet('GTIT','GTSR','GTMB','GTSWN','OUTPOST')][string]$Instance = 'GTIT',
    [string]$Java = 'java',
    [string]$Command,
    [int]$StartupTimeout = 900,
    [int]$MaxRuntime = 3600,
    [int]$Timeout = 900,
    [string[]]$RequireScenario = @(),
    [string[]]$RequireCheck = @()
)
$ErrorActionPreference = 'Stop'
$target = Join-Path 'E:\CodeGT' (([string][char]0x6d4b)+([char]0x8bd5)+([char]0x5b9e)+([char]0x4f8b)+'-'+$Instance)
$arguments = @((Join-Path $PSScriptRoot 'runner.py'), $Action, '--instance', $target, '--java', $Java, '--startup-timeout', $StartupTimeout, '--max-runtime', $MaxRuntime, '--timeout', $Timeout)
if ($Command) { $arguments += @('--command', $Command) }
foreach ($scenario in $RequireScenario) { $arguments += @('--require-scenario', $scenario) }
foreach ($check in $RequireCheck) { $arguments += @('--require-check', $check) }
& python @arguments
exit $LASTEXITCODE
