param(
    [Parameter(Mandatory=$true)][ValidateSet('run','start','status','command','probe','stop','recover-lock')][string]$Action,
    [ValidateSet('GTIT','GTSR','GTMB','GTSWN','OUTPOST')][string]$Instance = 'GTIT',
    [string]$Java,
    [string]$Registry = (Join-Path (Split-Path (Split-Path (Split-Path $PSScriptRoot))) 'plan\testlab-instances.json'),
    [string]$Command,
    [int]$StartupTimeout = 900,
    [int]$MaxRuntime = 3600,
    [int]$Timeout = 900,
    [string[]]$RequireScenario = @(),
    [string[]]$RequireCheck = @()
)
$ErrorActionPreference = 'Stop'
$entries = (Get-Content -LiteralPath $Registry -Raw -Encoding UTF8 | ConvertFrom-Json).instances | Where-Object { $_.id -eq $Instance }
if (@($entries).Count -ne 1) { throw "Unknown or duplicate registered instance: $Instance" }
$target = [System.IO.Path]::GetFullPath($entries.server)
if (-not $Java) { $Java = $entries.server_java }
$arguments = @((Join-Path $PSScriptRoot 'runner.py'), $Action, '--instance', $target, '--registry', $Registry, '--java', $Java, '--startup-timeout', $StartupTimeout, '--max-runtime', $MaxRuntime, '--timeout', $Timeout)
if ($Command) { $arguments += @('--command', $Command) }
foreach ($scenario in $RequireScenario) { $arguments += @('--require-scenario', $scenario) }
foreach ($check in $RequireCheck) { $arguments += @('--require-check', $check) }
& python @arguments
exit $LASTEXITCODE
