# Exercise the real instrumentation subprocess and checkpoint callback without
# touching adb, an emulator, installed packages, or public survey data.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -gt 5) {
  & "$env:SystemRoot/System32/WindowsPowerShell/v1.0/powershell.exe" -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath
  if($LASTEXITCODE -ne 0){throw 'Recovery contracts failed.'}
  return
}
. "$PSScriptRoot/android-test-common.ps1"
$repo=Split-Path -Parent $PSScriptRoot
$root=Join-Path $repo ('workbench/artifacts/runner-recovery-contracts-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $root | Out-Null
$fake=Join-Path $root 'fake-adb.exe'
Add-Type -OutputAssembly $fake -OutputType ConsoleApplication -TypeDefinition @'
using System;
using System.IO;
using System.Threading;
public class FakeAdb {
  public static int Main(string[] args) {
    string root=Path.GetDirectoryName(typeof(FakeAdb).Assembly.Location);
    string mode=File.ReadAllText(Path.Combine(root,"mode.txt")).Trim();
    string command=String.Join(" ",args);
    if(command.Contains("boot_id")) {
      string counter=Path.Combine(root,"health-count.txt");
      int n=File.Exists(counter)?Int32.Parse(File.ReadAllText(counter)):0;
      File.WriteAllText(counter,(n+1).ToString());
      if(n>0 && mode=="offline") {Console.Error.WriteLine("device offline");return 1;}
      Console.WriteLine(n>0 && mode=="reboot"?"22222222-2222-2222-2222-222222222222":"11111111-1111-1111-1111-111111111111");
      Console.WriteLine("1");return 0;
    }
    if(command.Contains("instrument")) {
      File.WriteAllText(Path.Combine(root,"client-pid.txt"),System.Diagnostics.Process.GetCurrentProcess().Id.ToString());
      Console.WriteLine("INSTRUMENTATION_STATUS: class=example.Test");
      Console.WriteLine("INSTRUMENTATION_STATUS: test=one");
      Console.WriteLine("INSTRUMENTATION_STATUS_CODE: 1");
      if(mode=="hang") {Thread.Sleep(30000);return 0;}
      if(mode=="anr") {Console.WriteLine("INSTRUMENTATION_RESULT: shortMsg=keyDispatchingTimedOut");Console.WriteLine("INSTRUMENTATION_CODE: 0");return 0;}
      Console.WriteLine("INSTRUMENTATION_STATUS: class=example.Test");
      Console.WriteLine("INSTRUMENTATION_STATUS: test=one");
      Console.WriteLine("INSTRUMENTATION_STATUS_CODE: "+(mode=="assertion"?"-2":"0"));
      Console.WriteLine(mode=="assertion"?"FAILURES!!!":"OK (1 test)");
      Console.WriteLine("INSTRUMENTATION_CODE: -1");return 0;
    }
    // Diagnostics must only start after local raw output and checkpoint save.
    if(!File.Exists(Path.Combine(root,"saved.txt"))) {Console.Error.WriteLine("checkpoint missing before diagnostics");return 1;}
    if(command.Contains("am force-stop")) {File.WriteAllText(Path.Combine(root,"device-stop.txt"),command);return 0;}
    File.AppendAllText(Path.Combine(root,"diagnostics.txt"),"saved first\n");
    return 0;
  }
}
'@
$checks=0
foreach($mode in @('healthy','assertion','anr','reboot','offline','hang')) {
  Set-Content "$root/mode.txt" $mode
  Remove-Item -LiteralPath "$root/health-count.txt","$root/saved.txt","$root/diagnostics.txt","$root/device-stop.txt" -Force -ErrorAction SilentlyContinue
  $script:reason='';$script:callback=$false
  $save={param($Reason)
    if(!(Test-Path "$root/$mode.instrumentation.log")){throw 'Raw evidence was not saved before callback'}
    $script:reason=$Reason;$script:callback=$true
    Set-Content "$root/saved.txt" 'checkpoint saved'
  }
  $clock=[Diagnostics.Stopwatch]::StartNew();$threw=$false;$message='';$ok=$false
  try {$ok=Invoke-InstrumentationTimed -Adb $fake -Serial 'fake' -Arguments @('shell','am','instrument') -Name $mode -TimeoutSeconds 2 -ArtifactsLocal $root -OnOutputSaved $save -AppPackage 'com.topodroid.TDX.sketch' -TestPackage 'com.topodroid.TDX.sketch.test'}
  catch {$threw=$true;$message=[string]$_}
  if(!$script:callback){throw "$mode did not checkpoint its raw evidence"};$checks++
  if($mode -in @('healthy','assertion')) {
    if($threw -or $script:reason -or ($ok -ne ($mode -eq 'healthy'))){throw "$mode classified incorrectly: $message"}
  } else {
    if(!$threw -or !$script:reason -or $message -notmatch 'QA_INFRA:'){throw "$mode escaped infrastructure accounting: $message"}
    if($mode -eq 'reboot' -and $message -notmatch 'rebooted'){throw 'Changed boot identity was not detected'}
    if($mode -eq 'offline' -and $message -notmatch 'health unavailable'){throw 'Disconnected emulator was not detected'}
    if($clock.Elapsed.TotalSeconds -gt 8){throw "$mode diagnostics exceeded the bounded recovery budget"}
  }
  $clientId=[int](Get-Content "$root/client-pid.txt")
  # Windows process-tree termination is asynchronous. Observe exit within a
  # bounded second, rather than mistaking an in-progress termination for a leak.
  $exitDeadline=[DateTime]::UtcNow.AddSeconds(1)
  while((Get-Process -Id $clientId -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $exitDeadline){Start-Sleep -Milliseconds 20}
  if(Get-Process -Id $clientId -ErrorAction SilentlyContinue){throw "$mode left a native test client running"}
  $checks++
  $deviceStopped=Test-Path "$root/device-stop.txt"
  if($deviceStopped -ne ($mode -notin @('healthy','assertion'))){throw "$mode stopped/skipped the device-side instrumentation incorrectly"}
  $checks++
  Write-Host "Recovery contract ${mode}: passed ($([Math]::Round($clock.Elapsed.TotalSeconds,2))s)"
}
"Recovery contracts: $checks passed. No real device accessed." | Tee-Object -FilePath "$root/result.txt" | Write-Host
