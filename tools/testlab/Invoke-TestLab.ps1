param(
    [Parameter(Mandatory=$true)][ValidateSet('run','start','status','command','probe','stop','recover-lock')][string]$Action,
    [ValidateSet('GTIT','GTSR','GTMB','GTSWN','OUTPOST')][string]$Instance = 'GTIT',
    [string]$Java,
    [string]$Registry = (Join-Path (Split-Path (Split-Path (Split-Path $PSScriptRoot))) 'Testlab\instances.json'),
    [string]$Command,
    [int]$StartupTimeout = 900,
    [int]$MaxRuntime = 3600,
    [int]$Timeout = 900,
    [string[]]$RequireScenario = @(),
    [string[]]$RequireCheck = @()
)
$ErrorActionPreference = 'Stop'
$sharedTools = Join-Path (Split-Path (Split-Path (Split-Path $PSScriptRoot))) 'tools/testlab'
$resolved = & python -B -c 'import json,sys;sys.path.insert(0,sys.argv[1]);import host;i=host.registered(sys.argv[2],sys.argv[3]);print(json.dumps(dict(server=str(i["_path"]),java=i["server_java"])))' $sharedTools $Registry $Instance
if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve the shared Testlab registry' }
$entries = $resolved | ConvertFrom-Json
$target = $entries.server
if (-not $Java) { $Java = $entries.java }
$arguments = @((Join-Path $PSScriptRoot 'runner.py'), $Action, '--instance', $target, '--registry', $Registry, '--java', $Java, '--startup-timeout', $StartupTimeout, '--max-runtime', $MaxRuntime, '--timeout', $Timeout)
if ($Command) { $arguments += @('--command', $Command) }
foreach ($scenario in $RequireScenario) { $arguments += @('--require-scenario', $scenario) }
foreach ($check in $RequireCheck) { $arguments += @('--require-check', $check) }
& python @arguments
exit $LASTEXITCODE
