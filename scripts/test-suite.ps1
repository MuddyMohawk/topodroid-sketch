param(
  [ValidateSet('fast','smoke','full','diagnostics','physical','all','refresh')][string]$Suite='fast',
  [string]$Serial='emulator-5554',
  [string]$PhysicalSerial='',
  [Alias('Group')][ValidateSet('symbols','drawing','data','exchange','devices','runner','vanilla')][string[]]$Feature=@(),
  [switch]$SkipBuild,
  [string[]]$Selector=@(),
  [ValidateRange(1,5)][int]$Repeat=1,
  [switch]$Reverse,
  [string]$OutputDirectory='',
  [switch]$Apply,
  [string]$CandidateDirectory='',
  [switch]$Authoring,
  [string]$ResumeFrom=''
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($Suite -eq 'refresh' -and !$Authoring) {
  if($Feature.Count){throw 'refresh uses baseline selectors; -Feature applies to validation suites.'}
  if(!$CandidateDirectory -and $OutputDirectory){$CandidateDirectory=$OutputDirectory}
  & "$PSScriptRoot/refresh-visual-baselines.ps1" -Serial $Serial -SkipBuild:$SkipBuild -Selector $Selector -CandidateDirectory $CandidateDirectory -Apply:$Apply
  return
}
if($Authoring -and $Suite -ne 'refresh'){throw 'Authoring is only valid for the refresh suite.'}
$repo=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/android-test-common.ps1"
. "$PSScriptRoot/test-suite-common.ps1"
$Feature=@($Feature | ForEach-Object {$_.ToLowerInvariant()} | Sort-Object -Unique)
Assert-QaRequestedScope -Suite $Suite -Features $Feature -Selector $Selector
$commandStarted=Get-Date
$commandClock=[Diagnostics.Stopwatch]::StartNew()
$sdk=if ($env:ANDROID_SDK_ROOT) {$env:ANDROID_SDK_ROOT} else {Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
$adb=Join-Path $sdk 'platform-tools/adb.exe'
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$gradle=Join-Path $repo 'gradlew.bat'
$gradleCache=Resolve-GradleUserHome -RepoRoot $repo
$gradleArgs=@('-g',$gradleCache,'--offline','--console=plain','--max-workers=2')
$package='com.topodroid.TDX.sketch'
$testPackage="$package.test"
$runner='com.topodroid.TDX.qa.SketchTestRunner'
$instrumentation="$testPackage/$runner"
if (!$OutputDirectory) { $OutputDirectory=Join-Path $repo ('workbench/artifacts/test-repair-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')+'-'+$Suite) }
Assert-QaEmptyOutputDirectory $OutputDirectory
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
$timings=[ordered]@{build=0.0;install=0.0;jvm=0.0;fixture=0.0;test=0.0;cleanup=0.0;discovery=0.0;validation=0.0}
$inventory=@(); $targets=@(); $issues=New-Object 'System.Collections.Generic.List[string]'
$animations=@{}
$pythonReady=$false
$stoppedTargets=@{}
$request=[pscustomobject]@{suite=$Suite;features=$Feature;serial=$Serial;physicalSerial=$PhysicalSerial;selectors=$Selector;repeat=$Repeat;reverse=[bool]$Reverse;timestamp=$commandStarted.ToString('o');resumeFrom=$ResumeFrom}
$apkRecords=@();$checkpoint=$null;$activeGroup='';$runState='running'
if($ResumeFrom) {
  if(!$SkipBuild -or $Suite -eq 'refresh'){throw 'Resume requires -SkipBuild and a validation suite; authoring starts a separate run.'}
  $ResumeFrom=[IO.Path]::GetFullPath($ResumeFrom)
  $checkpoint=Get-Content -LiteralPath (Join-Path $ResumeFrom 'checkpoint.json') -Raw | ConvertFrom-Json
}

function Save-QaRun {
  if(Get-Variable validationClock -ErrorAction SilentlyContinue){$timings.validation=$validationClock.Elapsed.TotalSeconds}
  Write-QaCheckpoint $OutputDirectory ([pscustomobject]@{version=3;state=$runState;updated=(Get-Date -Format o);activeGroup=$activeGroup;request=$request;apks=$apkRecords;results=@($inventory);timings=$timings;issues=@($issues);stoppedTargets=$stoppedTargets;animations=$animations})
  ConvertTo-Json -InputObject @($inventory) -Depth 6 | Set-Content "$OutputDirectory/results.json"
  $inventory | Export-Csv -NoTypeInformation "$OutputDirectory/results.csv"
  $timings | ConvertTo-Json | Set-Content "$OutputDirectory/timings.json"
  $issues | Set-Content "$OutputDirectory/issues.txt"
  $request | ConvertTo-Json | Set-Content "$OutputDirectory/request.json"
}

function Adb-Text([string]$Target, [string[]]$NativeArgs) {
  return ((Invoke-AdbChecked -Adb $adb -Serial $Target -Arguments $NativeArgs -Description ($NativeArgs -join ' ')) -join "`n")
}
function Grant-QaPermissions([string]$Target) {
  foreach ($p in @('ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','CAMERA','RECORD_AUDIO','BLUETOOTH_SCAN','BLUETOOTH_CONNECT')) {
    Adb-Text $Target @('shell','pm','grant',$package,"android.permission.$p") | Out-Null
  }
  Adb-Text $Target @('shell','appops','set','--uid',$package,'MANAGE_EXTERNAL_STORAGE','allow') | Out-Null
}
function Wake-QaPhysicalTarget([string]$Target) {
  Adb-Text $Target @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
  $dismiss=Adb-Text $Target @('shell','wm','dismiss-keyguard')
  if($dismiss -match 'Error|Exception'){throw "Unlock the explicitly selected tablet before compatibility checks: $Target; $dismiss"}
  $policy=Adb-Text $Target @('shell','dumpsys','window','policy')
  if($policy -match '(?m)^\s*(showing|inputRestricted)=true\s*$'){throw "Unlock the explicitly selected tablet before compatibility checks: $Target"}
}
function Invoke-QaPhysicalCwd([string]$Mode,[string]$Target,[string]$Journal='') {
  $native=@("$PSScriptRoot/qa_physical_cwd.py",$Mode,'--adb',$adb,'--serial',$Target)
  if($Journal){$native+=@('--journal',$Journal)}
  Invoke-NativeChecked -FilePath 'python' -Arguments $native -Description "Physical working-directory safeguard: $Mode" -TimeoutSeconds 45 -IdleTimeoutSeconds 30
}
function Run-QaGroup([object[]]$Tests,[string]$Target,[string]$Name,[switch]$Physical,[switch]$Record) {
  if($ResumeFrom){$Tests=@($Tests | Where-Object {$_.status -notin @('PASSED','FAILED','MEASURED')})}
  if(!$Tests.Count){return}
  if($stoppedTargets.ContainsKey($Target)) {
    foreach($t in $Tests){$t.status='BLOCKED';$t.note="Infrastructure: $($stoppedTargets[$Target]); not attempted after target stopped."}
    Save-QaRun
    return
  }
  $script:activeGroup=$Name
  Save-QaRun
  $log=Join-Path $OutputDirectory ($Name+'.log')
  $args=@('shell','am','instrument','-w','-r','-e','class',($Tests.id -join ','))
  if ($Physical) { $args+=@('-e','qa_physical','true') }
  if ($Record) { $args+=@('-e','visual_baseline_mode','record','-e','qa_authoring','true') }
  $args+=$instrumentation
  $timer=[Diagnostics.Stopwatch]::StartNew()
  $isUiGroup=@($Tests | Where-Object {$_.tier -in @('UI','SMOKE')}).Count -gt 0
  $timeout=if($Name -eq 'fast'){150}elseif(@($Tests | Where-Object tier -eq 'COMPAT').Count){600}elseif($isUiGroup){[Math]::Max(180,$Tests.Count*180)}else{[Math]::Max(180,$Tests.Count*120)}
  if($Record){$timeout=[Math]::Max(360,$timeout)} # Three independent exports; each file retains its 45s deadline.
  $physicalJournal=if($Physical){Join-Path $OutputDirectory ($Name+'-cwd.json')}else{''}
  try {
    if($Physical) {
      $wakeClock=[Diagnostics.Stopwatch]::StartNew()
      try {Wake-QaPhysicalTarget $Target;Invoke-QaPhysicalCwd 'capture' $Target $physicalJournal}
      finally {$timings.fixture+=$wakeClock.Elapsed.TotalSeconds}
    }
    # The shared bounded subprocess runner preserves raw output and captures hangs.
    $saveBeforeDiagnostics={
      param($Reason)
      $partial=Join-Path $OutputDirectory ($Name+'.instrumentation.log')
      if(Test-Path $partial){Read-QaResults (Get-Content $partial -Raw) $Tests}
      if($Reason) {
        $stoppedTargets[$Target]=$Reason
        Set-QaInfrastructureResults $Tests "$Reason; see $Name.instrumentation.log"
      }
      Save-QaRun
    }
    # A UI method can perform two separately bounded 45s exports plus dialog
    # interaction between progress events. Silence alone must not truncate
    # that valid work. Device-health probes and the total group cap remain.
    $idleTimeout=if($isUiGroup){180}else{90}
    $null=Invoke-InstrumentationTimed -Adb $adb -Serial $Target -Arguments $args -Name $Name -TimeoutSeconds $timeout -IdleTimeoutSeconds $idleTimeout -AppPackage $package -TestPackage $testPackage -ArtifactsLocal $OutputDirectory -OnOutputSaved $saveBeforeDiagnostics
    $raw=Get-Content -LiteralPath (Join-Path $OutputDirectory ($Name+'.instrumentation.log')) -Raw
    $raw | Set-Content -LiteralPath $log
    Read-QaResults -Text $raw -Expected $Tests
  } catch {
    $failure=[string]$_
    if($failure -match 'QA_INFRA:'){$stoppedTargets[$Target]=$failure}
    $partial=Join-Path $OutputDirectory ($Name+'.instrumentation.log')
    if(Test-Path $partial){try{Read-QaResults (Get-Content $partial -Raw) $Tests}catch{Write-Host "Partial result accounting: $_"}}
    if($stoppedTargets.ContainsKey($Target)){Set-QaInfrastructureResults $Tests $failure}
    foreach ($t in $Tests) { if ($t.status -in @('NOT_REQUESTED','BLOCKED')) { $t.status='BLOCKED'; $t.note="Runner: $failure" } }
    $issues.Add("${Name}: $failure")
  } finally {
    if($physicalJournal -and (Test-Path -LiteralPath $physicalJournal)) {
      $recoveryClock=[Diagnostics.Stopwatch]::StartNew()
      try {Invoke-QaPhysicalCwd 'restore' $Target $physicalJournal}
      catch {$issues.Add("PHYSICAL WORKING DIRECTORY RECOVERY PENDING on ${Target}: $_; record=$physicalJournal")}
      finally {$timings.cleanup+=$recoveryClock.Elapsed.TotalSeconds}
    }
  }
  $rawPath=Join-Path $OutputDirectory ($Name+'.instrumentation.log')
  if(Test-Path $rawPath) {
    $raw=Get-Content $rawPath -Raw
    foreach($kind in @('fixture','test','cleanup')) {
      foreach($m in [regex]::Matches($raw,"QA_TIMING\|$kind\|(\d+)")){$timings[$kind]+=[double]$m.Groups[1].Value/1000}
    }
  }
  $timer.Stop()
  $script:activeGroup=''
  Save-QaRun
  Write-Host ("{0}: {1:n2}s; {2}" -f $Name,$timer.Elapsed.TotalSeconds,(($Tests | Group-Object status | ForEach-Object {"$($_.Name)=$($_.Count)"}) -join ', '))
}

Push-Location $repo
try {
  # All prerequisites must be checked before builds, installs or tests.
  if ($Suite -in @('all','physical') -and !$PhysicalSerial) {throw "$Suite requires an explicit -PhysicalSerial; no field tablet is selected automatically."}
  if ($Suite -eq 'physical') {
    if (!$PhysicalSerial) { throw 'physical requires -PhysicalSerial; a tablet is never selected automatically.' }
  } else { Assert-SafeQaTarget -Adb $adb -Serial $Serial }
  if ($Suite -in @('all','refresh')) {Assert-EmulatorProfile -Adb $adb -Serial $Serial}
  if ($Suite -in @('all','physical')) {
    if (!$PhysicalSerial) { throw 'all requires an explicit -PhysicalSerial and installed Vanilla on both devices.' }
    Assert-SafeQaTarget -Adb $adb -Serial $PhysicalSerial -Physical
    Assert-InstalledPackage -Adb $adb -Serial $PhysicalSerial -PackageName 'com.topodroid.TDX'
    Wake-QaPhysicalTarget $PhysicalSerial
    if ($Suite -eq 'all') { Assert-InstalledPackage -Adb $adb -Serial $Serial -PackageName 'com.topodroid.TDX' }
  }
  if ($Apply -or $CandidateDirectory) { throw 'Use the refresh authoring entry point to apply or select candidates.' }
  if(!(Get-Command python -ErrorAction SilentlyContinue)){throw 'Python 3 is required for suite reporting before tests can start.'}
  Invoke-NativeChecked -FilePath 'python' -Arguments @('-c','import json,sys; assert sys.version_info.major == 3') -Description 'Check reporting runtime' -TimeoutSeconds 30 -IdleTimeoutSeconds 30
  $pythonReady=$true
  if($Suite -in @('all','physical')){Invoke-QaPhysicalCwd 'check' $PhysicalSerial}
  & "$PSScriptRoot/test-runner-contracts.ps1"
  $clock=[Diagnostics.Stopwatch]::StartNew()
  if (!$SkipBuild) {
    Invoke-NativeChecked -FilePath $gradle -Arguments ($gradleArgs+@(':app:assembleDebug',':app:assembleDebugAndroidTest',':app:compileDebugUnitTestJavaWithJavac')) -Description 'Compile app and test APKs' -TimeoutSeconds 1200 -IdleTimeoutSeconds 300
  }
  $timings.build=$clock.Elapsed.TotalSeconds
  if($checkpoint) {
    $appApk=Join-Path $ResumeFrom 'used-app.apk';$testApk=Join-Path $ResumeFrom 'used-test.apk'
  } else {
    $appApk=Resolve-GradleApkOutput -OutputDirectory "$repo/app/build/outputs/apk/debug" -Description 'app'
    $testApk=Resolve-GradleApkOutput -OutputDirectory "$repo/app/build/outputs/apk/androidTest/debug" -Description 'test'
  }
  $apkRecords=@([pscustomobject]@{role='app';sha256=(Get-FileHash $appApk -Algorithm SHA256).Hash},[pscustomobject]@{role='test';sha256=(Get-FileHash $testApk -Algorithm SHA256).Hash})
  if($checkpoint){Assert-QaResume $checkpoint $request $apkRecords}
  Copy-Item -LiteralPath $appApk -Destination "$OutputDirectory/used-app.apk"
  Copy-Item -LiteralPath $testApk -Destination "$OutputDirectory/used-test.apk"
  @($appApk,$testApk) | ForEach-Object { $f=Get-Item $_; "APK $($f.FullName) bytes=$($f.Length) SHA256=$((Get-FileHash $_ -Algorithm SHA256).Hash)" } | Tee-Object -FilePath "$OutputDirectory/apks.txt" | Write-Host
  $clock.Restart()
  $targets=if ($Suite -eq 'physical') {@($PhysicalSerial)} elseif ($Suite -eq 'all') {@($Serial,$PhysicalSerial)} else {@($Serial)}
  foreach ($target in $targets) {
    Install-ApkChecked -Adb $adb -Serial $target -ApkPath $appApk -Description 'Install QA app'
    Install-ApkChecked -Adb $adb -Serial $target -ApkPath $testApk -Description 'Install QA tests'
    Grant-QaPermissions $target
  }
  $timings.install=$clock.Elapsed.TotalSeconds
  $validationClock=[Diagnostics.Stopwatch]::StartNew()
  if ($Suite -ne 'physical') {
    $clock.Restart()
    $raw=Adb-Text $Serial @('shell','am','instrument','-w','-r','-e','qa_discover','true',$instrumentation)
    $raw | Set-Content "$OutputDirectory/discovery.log"
    $inventory=@(ConvertFrom-QaInventory $raw)
    $timings.discovery=$clock.Elapsed.TotalSeconds
  } else {
    $raw=Adb-Text $PhysicalSerial @('shell','am','instrument','-w','-r','-e','class','com.topodroid.TDX.PhysicalCompatInstrumentedTest','-e','qa_physical','true','-e','qa_discover','true',$instrumentation)
    $raw | Set-Content "$OutputDirectory/discovery.log"
    $inventory=@(ConvertFrom-QaInventory $raw)
  }
  $tiers=switch ($Suite) {
    fast {@('FAST')}; smoke {@('SMOKE')}; full {@('FAST','UI','SMOKE','COMPAT')}
    diagnostics {@('DIAGNOSTIC')}; all {@('FAST','UI','SMOKE','DIAGNOSTIC','COMPAT')}
    physical {@('COMPAT')}; refresh {@('UI','SMOKE')}
  }
  foreach($row in $inventory){$row.device=if($Suite -eq 'physical'){$PhysicalSerial}else{$Serial}}
  # A feature can have only JVM methods in the quick tier. The composed
  # selection must still contain actual requested methods after JVM discovery.
  $selected=@(Select-QaTests -Inventory $inventory -Tiers $tiers -Selector $Selector -Features $Feature -AllowEmpty:($Suite -in @('fast','full') -and !$Selector.Count))
  if($Suite -eq 'full' -and @($selected | Where-Object baseline).Count) {
    Assert-EmulatorProfile -Adb $adb -Serial $Serial
  }
  if($Suite -eq 'full' -and @($selected | Where-Object tier -eq 'COMPAT').Count) {
    Assert-InstalledPackage -Adb $adb -Serial $Serial -PackageName 'com.topodroid.TDX'
  }
  if ($Suite -eq 'refresh') {
    $selected=@($selected | Where-Object baseline)
    if (!$selected.Count) { throw 'No baseline-authoring methods selected.' }
  }
  foreach($row in $selected){$row.status='BLOCKED';$row.note='Pending execution; no terminal result yet.'}
  $physical=@()
  if($Suite -eq 'all') {
    $physical=@($selected | Where-Object tier -eq 'COMPAT' | ForEach-Object {[pscustomobject]@{id=$_.id;device=$PhysicalSerial;iteration=1;tier='COMPAT';features=$_.features;baseline=$false;status='BLOCKED';seconds=0.0;note='Pending execution; no terminal result yet.'}})
    $inventory+=$physical
  }
  if($checkpoint) {
    $inventory+=@($checkpoint.results | Where-Object device -eq 'host')
    Restore-QaCompletedResults $inventory $checkpoint.results
    if(Test-Path "$ResumeFrom/jvm"){Copy-Item -LiteralPath "$ResumeFrom/jvm" -Destination "$OutputDirectory/jvm" -Recurse}
  }
  Save-QaRun
  if ($Suite -in @('fast','full','all')) {
    if ($Selector.Count -eq 0 -and !$checkpoint) {
    $clock.Restart()
    $jvmStartedUtc=[DateTime]::UtcNow
    $jvmArgs=$gradleArgs+@(':app:testDebugUnitTest')
    if ($Suite -eq 'fast') { $jvmArgs+= '-PqaFast=true' }
    if ($Feature.Count) { $jvmArgs+=('-PqaFeatures='+($Feature -join ',')) }
    try { Invoke-NativeChecked -FilePath $gradle -Arguments $jvmArgs -Description 'JVM correctness' -TimeoutSeconds 600 -IdleTimeoutSeconds 120 } catch { $issues.Add("JVM: $_") }
    $timings.jvm=$clock.Elapsed.TotalSeconds
    New-Item -ItemType Directory -Force "$OutputDirectory/jvm" | Out-Null
    $freshXml=@(Get-QaFreshJunitResults @(Get-ChildItem "$repo/app/build/test-results/testDebugUnitTest/*.xml" -ErrorAction SilentlyContinue) $jvmStartedUtc)
    foreach($xmlFile in $freshXml){Copy-Item -LiteralPath $xmlFile.FullName -Destination "$OutputDirectory/jvm"}
    $jvm=@(Get-Content "$repo/app/build/qa/jvm-inventory.json" -Raw | ConvertFrom-Json)
    foreach($test in $jvm){$test | Add-Member -NotePropertyName device -NotePropertyValue 'host';$test | Add-Member -NotePropertyName iteration -NotePropertyValue 1}
    $unitById=@{}; foreach($test in $jvm){$unitById[$test.id]=$test}
    foreach($file in Get-ChildItem "$OutputDirectory/jvm/*.xml") {
      [xml]$xml=Get-Content $file.FullName -Raw
      foreach($case in $xml.testsuite.testcase) {
        $id='jvm:'+$case.classname+'#'+$case.name
        if(!$unitById.ContainsKey($id) -or !$unitById[$id].requested){throw "Unrequested or unclassified JVM result: $id"}
        $row=$unitById[$id];$row.seconds=[double]$case.time
        if($case.HasChildNodes){$row.status='FAILED';$row.note=$case.InnerXml}else{$row.status='PASSED'}
      }
    }
    foreach($test in $jvm) {
      if($test.status -eq 'NOT_REQUESTED' -and $test.requested){$test.status='BLOCKED';$test.note='Missing JVM terminal result'}
    }
    $inventory+=$jvm
    Save-QaRun
    }
  }
  if(!@($inventory | Where-Object {$_.status -ne 'NOT_REQUESTED'}).Count){throw 'The composed JVM/Android selection contains zero requested tests.'}
  $fast=@($selected | Where-Object tier -eq 'FAST')
  if($Reverse){[array]::Reverse($fast)}
  if ($fast.Count) { Run-QaGroup $fast $Serial 'fast' }
  $ui=@($selected | Where-Object { $_.tier -in @('UI','SMOKE') })
  if ($ui.Count -and !$stoppedTargets.ContainsKey($Serial)) {
    $environmentClock=[Diagnostics.Stopwatch]::StartNew()
    foreach($key in @('window_animation_scale','transition_animation_scale','animator_duration_scale')) {
      $animations[$key]=(Adb-Text $Serial @('shell','settings','get','global',$key)).Trim()
      if($checkpoint -and $checkpoint.PSObject.Properties.Name -contains 'animations' -and $checkpoint.animations.PSObject.Properties.Name -contains $key) {
        $animations[$key]=[string]$checkpoint.animations.$key
      }
      Save-QaRun # Preserve original values before changing even the first setting.
      Adb-Text $Serial @('shell','settings','put','global',$key,'0') | Out-Null
    }
    $timings.fixture+=$environmentClock.Elapsed.TotalSeconds
    if($ui.Count) {
      for($iteration=1;$iteration -le $Repeat;$iteration++) {
        $attempt=if($iteration -eq 1){@($ui)}else{@($ui | ForEach-Object {[pscustomobject]@{id=$_.id;device=$Serial;iteration=$iteration;tier=$_.tier;features=$_.features;baseline=$_.baseline;status='NOT_REQUESTED';seconds=0.0;note=''}})}
        if($iteration -gt 1){$inventory+=$attempt}
        $groups=@($attempt | Group-Object {($_.id -split '#')[0]})
        if($Reverse -xor ($iteration%2 -eq 0)){[array]::Reverse($groups)}
        foreach ($group in $groups) {
          Run-QaGroup @($group.Group) $Serial ($group.Name+"-attempt-$iteration") -Record:($Suite -eq 'refresh')
        }
      }
    }
  } elseif($ui.Count) {
    Set-QaInfrastructureResults $ui "$($stoppedTargets[$Serial]); UI checks not attempted after target stopped."
    Save-QaRun
  }
  foreach ($group in @($selected | Where-Object tier -eq 'DIAGNOSTIC' | Group-Object {($_.id -split '#')[0]})) { Run-QaGroup @($group.Group) $Serial $group.Name }
  $compat=@($selected | Where-Object tier -eq 'COMPAT')
  if ($compat.Count) {
    if ($Suite -in @('all','full')) { Run-QaGroup $compat $Serial 'emulator-compat' }
    # Account for each device independently.
    if($Suite -eq 'physical'){$physical=$compat}
    Run-QaGroup $physical $PhysicalSerial 'physical-compat' -Physical
  }
  $timings.validation=$validationClock.Elapsed.TotalSeconds
  if ($Suite -eq 'fast' -and $timings.validation -ge 120) { $issues.Add(('Fast runtime exceeded 120s: {0:n2}s; coverage was retained.' -f $timings.validation)) }
} catch { $issues.Add([string]$_) }
finally {
  $runState='finished'
  Save-QaRun
  if($animations.Count) {
    $environmentClock=[Diagnostics.Stopwatch]::StartNew()
    foreach($key in $animations.Keys) {
      if($stoppedTargets.ContainsKey($Serial)){$issues.Add("Animation restore deferred for unavailable target ${Serial}: $key=$($animations[$key])");continue}
      try {
        $restoreArgs=if($animations[$key] -eq 'null'){@('shell','settings','delete','global',$key)}else{@('shell','settings','put','global',$key,$animations[$key])}
        Invoke-AdbChecked -Adb $adb -Serial $Serial -Arguments $restoreArgs -Description "Restore animation $key" -TimeoutSeconds 5 | Out-Null
      } catch {$issues.Add("Restore emulator animation setting ${key}: $_")}
    }
    $timings.cleanup+=$environmentClock.Elapsed.TotalSeconds
    if(Get-Variable validationClock -ErrorAction SilentlyContinue){$timings.validation=$validationClock.Elapsed.TotalSeconds}
  }
  Save-QaRun
  foreach ($target in $targets) {
    if($stoppedTargets.ContainsKey($target)){Write-Host "Evidence remains on stopped target $target; local logs/checkpoint are preserved.";continue}
    try { Invoke-AdbChecked -Adb $adb -Serial $target -Arguments @('pull',"/sdcard/Android/data/$package/files/test-artifacts","$OutputDirectory/artifacts-$target") -Description 'Preserve test evidence' -TimeoutSeconds 30 | Out-Null } catch { Write-Host "Evidence pull: $_" }
  }
  $timings | Format-Table | Out-String | Write-Host
  Write-Host "Report: $OutputDirectory"
  if($pythonReady) {
    & python "$PSScriptRoot/report-test-suite.py" $OutputDirectory
    if($LASTEXITCODE -ne 0){$issues.Add('Report generation failed; JSON/CSV/raw evidence remain available.');$issues | Set-Content "$OutputDirectory/issues.txt"}
  }
  [ordered]@{started=$commandStarted.ToString('o');ended=(Get-Date).ToString('o');totalSeconds=$commandClock.Elapsed.TotalSeconds;note='Whole command including preflight, build/install, discovery, tests, cleanup, evidence transfer and report generation.'} | ConvertTo-Json | Set-Content "$OutputDirectory/command.json"
  Write-Host ('Whole command: {0:n2}s (including build/install, cleanup and reporting)' -f $commandClock.Elapsed.TotalSeconds)
  Pop-Location
}
$bad=@($inventory | Where-Object {$_.status -in @('FAILED','BLOCKED')})
if ($issues.Count -or $bad.Count) { throw ("Suite failed: {0} failed/blocked; {1}" -f $bad.Count,($issues -join '; ')) }
if ($inventory.Count -eq 0) { throw 'No tests were accounted for.' }
