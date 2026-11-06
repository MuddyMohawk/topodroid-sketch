param(
  [ValidateSet("fast", "smoke", "full", "diagnostics", "all", "refresh", "physical")]
  [string]$Suite = "fast",
  [string]$Serial = "emulator-5554",
  [string]$LogDir = "",
  [string]$PhysicalSerial = "",
  [ValidateSet('symbols','drawing','data','exchange','devices','runner','vanilla')][string[]]$Feature = @(),
  [switch]$Apply,
  [string]$CandidateDirectory = "",
  [string]$GradleUserHome = "",
  [string]$VanillaApk = "",
  [string]$SketchApk = "",
  [string]$TestApk = "",
  [string]$ExpectedModel = "",
  [switch]$AllowDowngrade,
  [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if ($Suite -in @('all','physical') -and !$PhysicalSerial) {
  throw "$Suite requires an explicit -PhysicalSerial; no field tablet is selected automatically."
}
if ($Apply -and $Suite -ne 'refresh') {throw '-Apply is only valid with refresh.'}
if($Suite -in @('all','physical','refresh') -and $Feature.Count){throw "$Suite does not accept a feature subset."}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
if ([string]::IsNullOrWhiteSpace($LogDir)) {
  $LogDir = Join-Path $RepoRoot "workbench/artifacts/launcher-logs"
}
if (-not [string]::IsNullOrWhiteSpace($GradleUserHome)) {
  if (-not [System.IO.Path]::IsPathRooted($GradleUserHome)) {
    $GradleUserHome = Join-Path $RepoRoot $GradleUserHome
  }
  $GradleUserHome = [System.IO.Path]::GetFullPath($GradleUserHome)
}

$targetScript = if ($Suite -eq 'refresh') { Join-Path $ScriptDir 'refresh-visual-baselines.ps1' } else { Join-Path $ScriptDir 'test-suite.ps1' }

if (-not (Test-Path $targetScript)) {
  throw "Test script not found: $targetScript"
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$baseName = "topodroid-test-$Suite-$stamp"
$log = Join-Path $LogDir "$baseName.log"
$pidFile = Join-Path $LogDir "$baseName.pid"
$powershell = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"

function Quote-PowerShellString {
  param([string]$Value)
  return "'" + ($Value -replace "'", "''") + "'"
}

$gradleEnvLine = ""
if (-not [string]::IsNullOrWhiteSpace($GradleUserHome)) {
  $gradleEnvLine = "`$env:TOPO_TEST_GRADLE_HOME = $(Quote-PowerShellString $GradleUserHome)"
}
$scriptParamsLine = "`$scriptParams = @{ Serial = $(Quote-PowerShellString $Serial) }"
$skipBuildParamLine = if ($SkipBuild) {
  "`$scriptParams['SkipBuild'] = `$true"
} else {
  ""
}
$physicalParamLines = @()
if ($Suite -ne 'refresh') { $physicalParamLines += "`$scriptParams['Suite'] = $(Quote-PowerShellString $Suite)" }
if ($PhysicalSerial) { $physicalParamLines += "`$scriptParams['PhysicalSerial'] = $(Quote-PowerShellString $PhysicalSerial)" }
if ($Feature.Count) { $physicalParamLines += "`$scriptParams['Feature'] = @(" + (($Feature | ForEach-Object {Quote-PowerShellString $_}) -join ',') + ')' }
if ($Apply) { $physicalParamLines += "`$scriptParams['Apply'] = `$true" }
if ($CandidateDirectory) {
  if ($Suite -ne 'refresh') {throw '-CandidateDirectory is only valid with refresh.'}
  $physicalParamLines += "`$scriptParams['CandidateDirectory'] = $(Quote-PowerShellString $CandidateDirectory)"
}
$physicalParamsLine = $physicalParamLines -join "`n"
$skipBuildLogLine = if ($SkipBuild) {
  '"SkipBuild=true" | Out-File -LiteralPath ' + (Quote-PowerShellString $log) + ' -Append'
} else {
  ""
}

$command = @"
Set-Content -LiteralPath $(Quote-PowerShellString $pidFile) -Value `$PID
`$code = 0
"[$(Get-Date -Format "yyyy-MM-dd HH:mm:ss")] Launching $Suite test on $Serial with PID `$PID" | Out-File -LiteralPath $(Quote-PowerShellString $log)
$gradleEnvLine
if (`$env:TOPO_TEST_GRADLE_HOME) {
  "TOPO_TEST_GRADLE_HOME=`$env:TOPO_TEST_GRADLE_HOME" | Out-File -LiteralPath $(Quote-PowerShellString $log) -Append
}
$skipBuildLogLine
try {
$scriptParamsLine
$skipBuildParamLine
$physicalParamsLine
  "Params=Serial:`$(`$scriptParams.Serial) SkipBuild:`$(`$scriptParams.ContainsKey('SkipBuild'))" | Out-File -LiteralPath $(Quote-PowerShellString $log) -Append
  & $(Quote-PowerShellString $targetScript) @scriptParams *>&1 | Tee-Object -FilePath $(Quote-PowerShellString $log) -Append
  if (`$LASTEXITCODE -is [int]) { `$code = `$LASTEXITCODE }
} catch {
  `$code = 1
  "[$(Get-Date -Format "yyyy-MM-dd HH:mm:ss")] Test run threw:" | Out-File -LiteralPath $(Quote-PowerShellString $log) -Append
  (`$_ | Out-String) | Out-File -LiteralPath $(Quote-PowerShellString $log) -Append
}
exit `$code
"@
$encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
$processInfo = New-Object System.Diagnostics.ProcessStartInfo
$processInfo.FileName = $powershell
$processInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encodedCommand"
$processInfo.UseShellExecute = $true
$processInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
$process = [System.Diagnostics.Process]::Start($processInfo)

Write-Host "Started $Suite test run"
Write-Host "PID: $($process.Id)"
Write-Host "pidFile: $pidFile"
Write-Host "log: $log"
if (-not [string]::IsNullOrWhiteSpace($GradleUserHome)) {
  Write-Host "GradleUserHome: $GradleUserHome"
}
if ($SkipBuild) {
  Write-Host "SkipBuild: true"
}
Write-Host "Poll: Get-Content -Tail 80 -Path '$log'"
Write-Host "Watch: .\scripts\watch-test-run.ps1 -Log '$log' -PidFile '$pidFile'"
