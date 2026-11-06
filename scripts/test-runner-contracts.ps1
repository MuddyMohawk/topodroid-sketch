# Hermetic tests of the actual host runner functions (no adb or package mutations).
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. "$PSScriptRoot/test-suite-common.ps1"
. "$PSScriptRoot/android-test-common.ps1"
$checks=0
function Check([bool]$Value,[string]$Message) {if(!$Value){throw $Message}; $script:checks++}
function Must-Throw([scriptblock]$Action,[string]$Message) { $threw=$false; try { & $Action | Out-Null }catch{$threw=$true}; Check $threw $Message }
$text="INSTRUMENTATION_STATUS: stream=QA_TEST|example.Test#one|FAST|false|symbols,exchange`nINSTRUMENTATION_STATUS: stream=QA_TEST|example.Test#two|UI|true|devices`nINSTRUMENTATION_RESULT: stream=QA_DISCOVERED|2`nINSTRUMENTATION_CODE: -1"
$rows=@(ConvertFrom-QaInventory $text)
Check ($rows.Count -eq 2 -and $rows[1].baseline) 'APK inventory/tier metadata was lost'
Check (($rows[0].features -join ',') -eq 'symbols,exchange') 'APK feature metadata was lost'
Must-Throw {ConvertFrom-QaInventory ($text.Replace('|symbols,exchange',''))} 'Missing feature classification succeeded'
Must-Throw {ConvertFrom-QaInventory ($text.Replace('symbols,exchange','symbols,symbols'))} 'Duplicate feature classification succeeded'
Must-Throw {ConvertFrom-QaInventory ($text.Replace('symbols,exchange','unknown'))} 'Unknown feature classification succeeded'
Must-Throw { ConvertFrom-QaInventory 'QA_DISCOVERED|0' } 'Zero-test discovery succeeded'
Must-Throw { ConvertFrom-QaInventory ($text.Replace('QA_DISCOVERED|2','QA_DISCOVERED|3')) } 'Incomplete discovery succeeded'
Must-Throw { ConvertFrom-QaInventory ($text.Replace('|FAST|','|UNKNOWN|')) } 'Unclassified test succeeded'
Must-Throw { ConvertFrom-QaInventory ($text.Replace('INSTRUMENTATION_CODE: -1','INSTRUMENTATION_CODE: 0')) } 'Unsuccessful inventory process accepted'
Check (@(Select-QaTests $rows @('FAST') @()).Count -eq 1) 'Tier selection lost fast coverage'
Must-Throw { Select-QaTests $rows @('FAST') @('missing.Test') } 'Unmatched selector succeeded'
Must-Throw { Select-QaTests $rows @('FAST') @('example.Test#two') } 'Out-of-tier selector succeeded'
Must-Throw { Select-QaTests $rows @('DIAGNOSTIC') @() } 'Zero-test selection succeeded'
Check (@(Select-QaTests $rows @('FAST','UI') @() @('symbols')).Count -eq 1) 'Symbol selection included device checks'
Check ((Select-QaTests $rows @('FAST','UI') @() @('devices')).id -eq 'example.Test#two') 'Device selection lost its method'
Check (@(Select-QaTests $rows @('FAST','UI') @() @('symbols','exchange')).Count -eq 1) 'Overlapping features duplicated a method'
Check (@(Select-QaTests $rows @('FAST','UI') @()).Count -eq $rows.Count) 'Unfiltered coverage omitted a discovered method'
$withVanilla=@(ConvertFrom-QaInventory ($text.Replace('|devices','|vanilla')))
Check (@(Select-QaTests $withVanilla @('FAST','UI') @() @('exchange')).Count -eq 1) 'Sketch exchange selected deferred Vanilla compatibility'
Check ((Select-QaTests $withVanilla @('FAST','UI') @() @('vanilla')).id -eq 'example.Test#two') 'Dedicated Vanilla selection lost its method'
Must-Throw {Select-QaTests $rows @('FAST','UI') @() @('unknown')} 'Unknown feature selector succeeded'
Must-Throw {Select-QaTests $rows @('FAST') @() @('devices')} 'Out-of-tier feature selection reported success'
Must-Throw {Select-QaTests $rows @('FAST','UI') @('example.Test#two') @('symbols')} 'Selector bypassed the feature boundary'
Must-Throw {Assert-QaRequestedScope 'all' @('symbols') @()} 'all silently accepted a feature subset'
Must-Throw {Assert-QaRequestedScope 'all' @() @('example.Test')} 'all silently accepted a method subset'
Assert-QaRequestedScope 'all' @() @();$checks++
Assert-QaRequestedScope 'full' @('symbols','exchange') @();$checks++
$terminal=@'
INSTRUMENTATION_STATUS: class=example.Test
INSTRUMENTATION_STATUS: test=one
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: stream=QA_TIMING|test|125
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=example.Test
INSTRUMENTATION_STATUS: test=one
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_CODE: -1
'@
Read-QaResults $terminal @($rows[0])
Check ($rows[0].status -eq 'PASSED' -and $rows[0].seconds -eq .125) 'Valid terminal/timing result was lost'
Read-QaResults '' @($rows[0])
Check ($rows[0].status -eq 'BLOCKED') 'Missing terminal result was accepted'
Read-QaResults ($terminal.Replace('INSTRUMENTATION_CODE: -1','')) @($rows[0])
Check ($rows[0].status -eq 'BLOCKED') 'Missing process completion was accepted'
Read-QaResults ($terminal.Replace('INSTRUMENTATION_CODE: -1','INSTRUMENTATION_CODE: 0')) @($rows[0])
Check ($rows[0].status -eq 'BLOCKED') 'Unsuccessful process code accepted apparent passing methods'
Read-QaResults ($terminal.Replace('INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: -4')) @($rows[0])
Check ($rows[0].status -eq 'BLOCKED') 'Assumption/blocked prerequisite was counted as a pass'
$rows[0].tier='DIAGNOSTIC';Read-QaResults $terminal @($rows[0])
Check ($rows[0].status -eq 'MEASURED') 'Diagnostic reported as a correctness pass'
$failed=$terminal.Replace("`r`n","`n").Replace("INSTRUMENTATION_STATUS: test=one`nINSTRUMENTATION_STATUS_CODE: 0","INSTRUMENTATION_STATUS: test=one`nINSTRUMENTATION_STATUS: stack=assertion`nINSTRUMENTATION_STATUS_CODE: -2")
Read-QaResults $failed @($rows[0]);Check ($rows[0].status -eq 'FAILED') 'Assertion did not fail accounting'
# The real safety guard must reject physical devices before any installation code.
function Assert-AdbTarget {param($Adb,$Serial)}
function Get-AdbShellText {param($Adb,$Serial,$Command,$Description);return $script:qemu}
$script:qemu='';Must-Throw {Assert-SafeQaTarget 'adb' 'field-tablet'} 'Physical target was accepted as ordinary'
Must-Throw {Assert-SafeQaTarget 'adb' '' -Physical} 'Implicit tablet target was accepted'
$script:qemu='1';Assert-SafeQaTarget 'adb' 'emulator-5554';$checks++
Must-Throw {Assert-SafeQaTarget 'adb' 'emulator-5554' -Physical} 'Emulator accepted as physical target'
$stamp=[DateTime]::UtcNow
$oldXml=[pscustomobject]@{LastWriteTimeUtc=$stamp.AddSeconds(-1)}
$newXml=[pscustomobject]@{LastWriteTimeUtc=$stamp.AddSeconds(1)}
Check (@(Get-QaFreshJunitResults @($oldXml) $stamp).Count -eq 0) 'Stale JVM XML was accepted after a failed invocation'
Check (@(Get-QaFreshJunitResults @($oldXml,$newXml) $stamp).Count -eq 1) 'Fresh JVM terminal result was dropped'
$contractRoot=[IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $PSScriptRoot) ('workbench/tmp/runner-contract-'+[Guid]::NewGuid().ToString('N'))))
$workspaceRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot)).TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
if(!$contractRoot.StartsWith($workspaceRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected contract fixture path'}
New-Item -ItemType Directory -Path $contractRoot -Force | Out-Null
$sentinel=Join-Path $contractRoot 'sentinel.txt'
try {
  Assert-QaEmptyOutputDirectory $contractRoot;$checks++
  Set-Content -LiteralPath $sentinel -Value 'preserve'
  Must-Throw {Assert-QaEmptyOutputDirectory $contractRoot} 'Existing run evidence could be overwritten'
  Check ((Get-Content -LiteralPath $sentinel -Raw).Trim() -eq 'preserve') 'Existing evidence sentinel was changed'
} finally {if(Test-Path -LiteralPath $sentinel){Remove-Item -LiteralPath $sentinel};Remove-Item -LiteralPath $contractRoot}
# Exercise the actual subprocess deadline, not a mock timeout return value.
$shell=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$clock=[Diagnostics.Stopwatch]::StartNew()
Must-Throw {Invoke-QaBoundedProcess $shell @('-NoProfile','-Command','Start-Sleep -Seconds 30') 1 'hung adb stand-in'} 'Hung native client escaped its deadline'
Check ($clock.Elapsed.TotalSeconds -lt 4) 'Bounded client cleanup hung after timeout'
$quoted=Invoke-QaBoundedProcess $shell @('-NoProfile','-Command','[Console]::Write(''a "quoted" value'')') 5 'argument quoting'
Check ($quoted -ceq 'a "quoted" value') 'Bounded client corrupted quoted shell arguments'
# Checkpoint replacement and resume preserve assertions, but rerun interrupted
# methods only. Changed binaries must never inherit old green results.
New-Item -ItemType Directory -Path $contractRoot -Force | Out-Null
try {
  $request=[pscustomobject]@{suite='full';features=@('symbols');serial='emulator-test';physicalSerial='';repeat=1;reverse=$false;selectors=@()}
  $apks=@([pscustomobject]@{role='app';sha256='app-hash'},[pscustomobject]@{role='test';sha256='test-hash'})
  $state=[pscustomobject]@{version=3;request=$request;apks=$apks;results=@()}
  Write-QaCheckpoint $contractRoot $state
  $state.results=@('replacement');Write-QaCheckpoint $contractRoot $state
  $saved=Get-Content (Join-Path $contractRoot 'checkpoint.json') -Raw | ConvertFrom-Json
  Check ($saved.results[0] -eq 'replacement' -and !(Test-Path (Join-Path $contractRoot 'checkpoint.json.tmp'))) 'Checkpoint replacement lost durable state'
  Assert-QaResume $saved $request $apks;$checks++
  $differentScope=[pscustomobject]@{suite='full';features=@('devices');serial='emulator-test';physicalSerial='';repeat=1;reverse=$false;selectors=@()}
  Must-Throw {Assert-QaResume $saved $differentScope $apks} 'Resume changed feature coverage silently'
  $changed=@($apks[0],[pscustomobject]@{role='test';sha256='new-test-hash'})
  Must-Throw {Assert-QaResume $saved $request $changed} 'Changed test APK inherited prior results'
  $pending=@('passed','failed','interrupted' | ForEach-Object {[pscustomobject]@{device='emulator-test';id=$_;iteration=1;status='BLOCKED';seconds=0.0;note='pending'}})
  $previous=@([pscustomobject]@{device='emulator-test';id='passed';iteration=1;status='PASSED';seconds=1.0;note=''},[pscustomobject]@{device='emulator-test';id='failed';iteration=1;status='FAILED';seconds=2.0;note='original assertion'},[pscustomobject]@{device='emulator-test';id='interrupted';iteration=1;status='BLOCKED';seconds=0.0;note='device lost'})
  Restore-QaCompletedResults $pending $previous
  Check ($pending[0].status -eq 'PASSED' -and $pending[1].status -eq 'FAILED' -and $pending[1].note -eq 'original assertion') 'Resume dropped completed results or hid an assertion'
  Check ($pending[2].status -eq 'BLOCKED' -and $pending[2].note -eq 'pending') 'Interrupted method was silently accepted as complete'
  Set-QaInfrastructureResults $pending 'device rebooted'
  Check ($pending[0].status -eq 'BLOCKED' -and $pending[1].status -eq 'FAILED' -and $pending[2].status -eq 'BLOCKED') 'Device reboot accepted apparent passes or hid an assertion'
} finally {
  Remove-Item -LiteralPath (Join-Path $contractRoot 'checkpoint.json') -Force
  Remove-Item -LiteralPath $contractRoot
}
$script:bootResponse='11111111-1111-1111-1111-111111111111`n1'.Replace('`n',"`n")
function Invoke-AdbChecked {param($Adb,$Serial,$Arguments,$Description,$TimeoutSeconds);return $script:bootResponse}
Check ((Get-QaDeviceBootId 'adb' 'emulator-test') -eq '11111111-1111-1111-1111-111111111111') 'Healthy boot identity was rejected'
$script:bootResponse='device offline'
Must-Throw {Get-QaDeviceBootId 'adb' 'emulator-test'} 'Offline device accepted as healthy'
$script:bootResponse="11111111-1111-1111-1111-111111111111`n0"
Must-Throw {Get-QaDeviceBootId 'adb' 'emulator-test'} 'Incomplete reboot accepted as healthy'
$script:diagnosticCalls=0
function Invoke-AdbChecked {param($Adb,$Serial,$Arguments,$Description,$TimeoutSeconds);if($TimeoutSeconds -gt 5){throw 'Unbounded diagnostics'};$script:diagnosticCalls++;throw 'offline'}
Write-InstrumentationDiagnostics -Adb 'adb' -Serial 'emulator-test'
Check ($script:diagnosticCalls -eq 1) 'Dead device triggered cascaded diagnostics'
Write-Host "Runner contracts: $checks passed."
