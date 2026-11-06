# Exercise the real refresh script against a fake SDK, never a connected device.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -gt 5) {
  & "$env:SystemRoot/System32/WindowsPowerShell/v1.0/powershell.exe" -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath
  if ($LASTEXITCODE -ne 0) { throw 'Tablet refresh contracts failed.' }
  return
}
$repo = Split-Path -Parent $PSScriptRoot
$root = [IO.Path]::GetFullPath((Join-Path $repo ('workbench/tmp/tablet-refresh-contract-'+[Guid]::NewGuid().ToString('N'))))
$workspace = [IO.Path]::GetFullPath($repo).TrimEnd('\')+'\'
if (!$root.StartsWith($workspace,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected fixture path' }
$sdk = Join-Path $root 'fake sdk/platform-tools'
New-Item -ItemType Directory -Force -Path $sdk,"$root/scripts","$root/app/build/outputs/apk/debug" | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot/tablet-refresh.ps1","$PSScriptRoot/android-test-common.ps1" -Destination "$root/scripts"
Set-Content "$root/app/build/outputs/apk/debug/app-debug.apk" 'fake APK'
$sdkProperty = (Split-Path -Parent $sdk).Replace('\','\\').Replace(':','\:').Replace(' ','\ ')
Set-Content "$root/local.properties" "sdk.dir=$sdkProperty"
Set-Content "$root/gradlew.bat" "@echo off`r`n> `"%~dp0build.txt`" echo built`r`nexit /b 0"
Add-Type -OutputAssembly "$sdk/adb.exe" -OutputType ConsoleApplication -TypeDefinition @'
using System;
using System.IO;
public class FakeTabletAdb {
  public static int Main(string[] args) {
    string root=AppDomain.CurrentDomain.BaseDirectory;
    if(args.Length<3 || args[0]!="-s" || args[1]!="R32X200DN0T") return 90;
    string mode=File.ReadAllText(Path.Combine(root,"mode.txt")).Trim();
    string command=String.Join(" ",args,2,args.Length-2);
    File.AppendAllText(Path.Combine(root,"calls.txt"),command+Environment.NewLine);
    if(command=="get-state") {
      if(mode=="offline") {Console.Error.WriteLine("device offline");return 1;}
      Console.WriteLine("device");
    } else if(command=="shell getprop ro.product.model") {
      Console.WriteLine(mode=="wrong-model"?"OTHER":"SM-T577U");
    } else if(command.StartsWith("install -r -t ")) {
      if(mode=="install-failure") return 1;
      Console.WriteLine("Success");
    } else if(command=="shell pm clear com.topodroid.TDX.sketch") {
      if(mode=="clear-failure") return 1;
      Console.WriteLine("Success");
    } else if(command=="shell run-as com.topodroid.TDX.sketch mkdir -p shared_prefs") {
    } else if(command=="shell run-as com.topodroid.TDX.sketch tee shared_prefs/com.topodroid.TDX.sketch_preferences.xml") {
      if(mode=="seed-failure") return 1;
      File.WriteAllText(Path.Combine(root,"prefs.xml"),Console.In.ReadToEnd());
    } else if(command.StartsWith("shell pm grant com.topodroid.TDX.sketch android.permission.")) {
      if(mode=="grant-failure") return 1;
    } else if(command=="shell appops set --uid com.topodroid.TDX.sketch MANAGE_EXTERNAL_STORAGE allow") {
    } else if(command=="shell am force-stop com.topodroid.TDX.sketch") {
    } else if(command=="shell am start -W -n com.topodroid.TDX.sketch/com.topodroid.TDX.MainWindow") {
      Console.WriteLine(mode=="launch-failure"?"Error: activity failed":"Status: ok");
    } else if(command=="shell run-as com.topodroid.TDX.sketch cat shared_prefs/com.topodroid.TDX.sketch_preferences.xml") {
      Console.WriteLine(mode=="profile-lost"?"<map/>":File.ReadAllText(Path.Combine(root,"prefs.xml")));
    } else {Console.Error.WriteLine("Unexpected adb command: "+command);return 91;}
    return 0;
  }
}
'@
. "$PSScriptRoot/android-test-common.ps1"
$checks = 0
function Check([bool]$Value,[string]$Message) { if (!$Value) { throw $Message }; $script:checks++ }
Check ((Resolve-AndroidSdkPath $root) -eq (Split-Path -Parent $sdk)) 'Escaped SDK path was corrupted'
$previousHome = $env:TOPO_TEST_GRADLE_HOME
try {
  $env:TOPO_TEST_GRADLE_HOME = ''
  Check ((Resolve-GradleUserHome $root) -eq (Join-Path $root '.gradle-test-home')) 'Refresh and tests use different caches'
  $env:TOPO_TEST_GRADLE_HOME = 'custom-cache'
  Check ((Resolve-GradleUserHome $root) -eq (Join-Path $root 'custom-cache')) 'Explicit cache override was lost'
} finally { $env:TOPO_TEST_GRADLE_HOME = $previousHome }
$shell = "$env:SystemRoot/System32/WindowsPowerShell/v1.0/powershell.exe"
foreach ($mode in @('offline','wrong-model','install-failure','clear-failure','seed-failure','grant-failure','launch-failure','profile-lost','healthy')) {
  Set-Content "$sdk/mode.txt" $mode
  Set-Content "$sdk/calls.txt" ''
  # Offline mode also proves the build completes before tablet detection.
  $parameters = @('-NoProfile','-ExecutionPolicy','Bypass','-File',"$root/scripts/tablet-refresh.ps1")
  if ($mode -ne 'offline') { $parameters += '-SkipBuild' }
  $succeeded = $false
  try { Invoke-QaBoundedProcess $shell $parameters 30 "Refresh contract: $mode" | Out-Null; $succeeded = $true } catch { $failure = $_ }
  Check ($succeeded -eq ($mode -eq 'healthy')) "Refresh accepted failure mode: $mode"
  $calls = Get-Content "$sdk/calls.txt" -Raw
  if ($mode -in @('offline','wrong-model','install-failure')) { Check ($calls -notmatch 'shell pm clear') "$mode reached package reset" }
  if ($mode -eq 'offline') { Check (Test-Path "$root/build.txt") 'Missing tablet prevented the required build' }
  if ($mode -in @('clear-failure','seed-failure','grant-failure')) { Check ($calls -notmatch 'shell am start') "$mode reached app launch" }
  if ($mode -eq 'healthy') {
    Check (([regex]::Matches($calls,'shell pm grant ')).Count -eq 6) 'A required runtime permission was omitted'
    Check ($calls.Contains('MANAGE_EXTERNAL_STORAGE allow')) 'All-files access was omitted'
    Check ($calls.IndexOf('install -r -t') -lt $calls.IndexOf('shell pm clear')) 'Reset ran before successful installation'
    Check ($calls.IndexOf('shell am start') -lt $calls.IndexOf('shell run-as com.topodroid.TDX.sketch cat')) 'Profile was not verified after launch'
  }
}
Write-Host "Tablet refresh contracts: $checks passed. No real device accessed. Fixture: $root"
