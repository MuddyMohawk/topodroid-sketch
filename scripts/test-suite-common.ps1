Set-StrictMode -Version Latest

$script:QaFeatureNames=@('symbols','drawing','data','exchange','devices','runner','vanilla')

function Assert-QaRequestedScope {
  param([string]$Suite,[string[]]$Features=@(),[string[]]$Selector=@())
  foreach($feature in $Features) {
    if($feature -notin $script:QaFeatureNames){throw "Unknown test feature: $feature"}
  }
  if($Suite -eq 'all' -and ($Features.Count -or $Selector.Count)) {
    throw 'all always requests every maintained test. Use full or fast with -Feature/-Selector for a subset.'
  }
  if($Suite -in @('physical','refresh') -and $Features.Count){throw "$Suite uses its dedicated selection; -Feature applies to fast, smoke, full and diagnostics."}
}

function Assert-QaEmptyOutputDirectory {
  param([string]$Path)
  if((Test-Path -LiteralPath $Path) -and (!(Test-Path -LiteralPath $Path -PathType Container) -or @(Get-ChildItem -LiteralPath $Path -Force).Count)) {
    throw "Refusing to overwrite existing evidence: $Path. Select a new empty output directory."
  }
}

function Get-QaFreshJunitResults {
  param([object[]]$Files,[datetime]$StartedUtc)
  return @($Files | Where-Object {$_.LastWriteTimeUtc -ge $StartedUtc})
}

function Write-QaCheckpoint {
  param([string]$Directory,[object]$State)
  # Same-volume replacement leaves either the previous complete checkpoint or
  # the new one if the host is interrupted while writing.
  $path=Join-Path $Directory 'checkpoint.json'
  $temporary=$path+'.tmp'
  ConvertTo-Json -InputObject $State -Depth 10 | Set-Content -LiteralPath $temporary -Encoding UTF8
  Move-Item -LiteralPath $temporary -Destination $path -Force
}

function Assert-QaResume {
  param([object]$Checkpoint,[object]$Request,[object[]]$Apks)
  if($Checkpoint.version -ne 3){throw 'This checkpoint predates the current API-based export inventory. Preserve its report and start a new run with the rebuilt test APK.'}
  if($Checkpoint.request.repeat -ne 1 -or $Request.repeat -ne 1){throw 'Resume currently requires Repeat=1; repeated attempts must remain separate.'}
  foreach($key in @('suite','serial','physicalSerial','reverse')) {
    if([string]$Checkpoint.request.$key -cne [string]$Request.$key){throw "Resume request differs: $key"}
  }
  if(($Checkpoint.request.selectors -join ',') -cne ($Request.selectors -join ',')){throw 'Resume selectors differ.'}
  if(($Checkpoint.request.features -join ',') -cne ($Request.features -join ',')){throw 'Resume features differ.'}
  foreach($apk in $Apks) {
    $previous=@($Checkpoint.apks | Where-Object role -eq $apk.role)
    if($previous.Count -ne 1 -or $previous[0].sha256 -ne $apk.sha256){throw "Resume APK hash differs: $($apk.role). Start a separate run for changed APKs."}
  }
  if($Apks.Count -ne 2){throw 'Resume requires both exact APKs.'}
}

function Restore-QaCompletedResults {
  param([object[]]$Inventory,[object[]]$Previous)
  $byKey=@{}
  foreach($row in $Inventory){$byKey["$($row.device)|$($row.id)|$($row.iteration)"]=$row}
  foreach($row in $Previous) {
    $key="$($row.device)|$($row.id)|$($row.iteration)"
    if(!$byKey.ContainsKey($key)){throw "Checkpoint method no longer discovered: $key"}
    if($row.status -in @('PASSED','FAILED','MEASURED')) {
      $current=$byKey[$key]
      foreach($field in @('status','seconds','note')){$current.$field=$row.$field}
    }
  }
}

function Set-QaInfrastructureResults {
  param([object[]]$Tests,[string]$Reason)
  foreach($test in $Tests) {
    # A completed assertion remains a failure; apparent passes in an interrupted
    # process are not valid terminal results for this group.
    if($test.status -ne 'FAILED'){$test.status='BLOCKED';$test.note="Infrastructure: $Reason"}
  }
}

function Assert-SafeQaTarget {
  param([string]$Adb, [string]$Serial, [switch]$Physical)
  if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'An explicit device serial is required.' }
  Assert-AdbTarget -Adb $Adb -Serial $Serial
  $qemu = Get-AdbShellText -Adb $Adb -Serial $Serial -Command 'getprop ro.kernel.qemu' -Description 'Establish device safety'
  if ($Physical) {
    if ($qemu -eq '1') { throw "Physical checks require an explicitly selected physical tablet: $Serial is an emulator." }
  } elseif ($qemu -ne '1') { throw "BLOCKED: ordinary tests refuse physical device $Serial before installation or reset." }
}

function ConvertFrom-QaInventory {
  param([string]$Text)
  $rows = @([regex]::Matches($Text, '(?m)^(?:INSTRUMENTATION_STATUS: stream=)?QA_TEST\|([^|\r\n]+)\|(FAST|UI|SMOKE|DIAGNOSTIC|COMPAT)\|(true|false)\|([a-z,]+)[ \t]*\r?$') | ForEach-Object {
    $features=@($_.Groups[4].Value -split ',')
    if(@($features | Where-Object {$_ -notin $script:QaFeatureNames}).Count -or @($features | Sort-Object -Unique).Count -ne $features.Count){throw 'Invalid or duplicate APK feature classification.'}
    [pscustomobject]@{id=$_.Groups[1].Value; device=''; iteration=1; tier=$_.Groups[2].Value; features=$features; baseline=($_.Groups[3].Value -eq 'true'); status='NOT_REQUESTED'; seconds=0.0; note=''}
  })
  $terminal = [regex]::Match($Text, 'QA_DISCOVERED\|(\d+)')
  if (!$terminal.Success -or $rows.Count -eq 0 -or $rows.Count -ne [int]$terminal.Groups[1].Value -or
      $Text -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$' -or $Text -match 'INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg=') { throw 'APK discovery was incomplete, unclassified, unsuccessful, or returned zero tests. Rebuild APKs when adding feature metadata.' }
  if (@($rows.id | Sort-Object -Unique).Count -ne $rows.Count) { throw 'Duplicate APK test methods.' }
  return $rows
}

function Read-QaResults {
  param([string]$Text, [object[]]$Expected)
  if ($Expected.Count -eq 0) { throw 'A zero-test selection cannot succeed.' }
  $byId = @{}; foreach ($test in $Expected) { $byId[$test.id]=$test; $test.status='BLOCKED'; $test.note='Missing terminal test result' }
  $block=@{}; $stack=''; $started=@{}; $completed=@{}
  $captureUnavailable=@{}
  $current=''
  foreach ($line in ($Text -split "`r?`n")) {
    if ($line -match 'QA_TIMING\|test\|(\d+)' -and $byId.ContainsKey($current)) { $byId[$current].seconds=[double]$Matches[1]/1000 }
    if($line -match '(stable-frame capture unavailable|live frame capture unavailable)' -and $byId.ContainsKey($current)) {
      if(!$captureUnavailable.ContainsKey($current)){$captureUnavailable[$current]=0};$captureUnavailable[$current]++
    }
    if ($line -match '^INSTRUMENTATION_STATUS: (class|test|stack)=(.*)$') { $block[$Matches[1]]=$Matches[2]; if ($Matches[1] -eq 'stack') { $stack=$Matches[2] }; continue }
    if ($line -match '^INSTRUMENTATION_STATUS_CODE: (-?\d+)') {
      $code=[int]$Matches[1]
      if ($block.ContainsKey('class') -and $block.ContainsKey('test')) {
        $id=$block['class']+'#'+$block['test']
        if (!$byId.ContainsKey($id)) { throw "Unrequested result: $id" }
        if ($code -eq 1) { $started[$id]=$true; $current=$id }
        else {
          if ($completed.ContainsKey($id)) { throw "Duplicate terminal result: $id" }
          $completed[$id]=$true
          $test=$byId[$id]
          if ($code -eq 0 -and $started.ContainsKey($id)) {
            $test.status=if ($test.tier -eq 'DIAGNOSTIC') {'MEASURED'} else {'PASSED'}
            $test.note=if($captureUnavailable.ContainsKey($id)){"Frame readiness observed $($captureUnavailable[$id]) unavailable capture(s) within its deadline; the test was not restarted."}else{''}
          }
          elseif ($code -in @(-1,-2)) { $test.status='FAILED'; $test.note=$stack }
          else { $test.status='BLOCKED'; $test.note="Non-success instrumentation result $code" }
        }
      }
      $block=@{}; $stack=''
    } elseif ($block.ContainsKey('stack') -and $line -notmatch '^INSTRUMENTATION_') { $stack+="`n$line" }
  }
  # A process that dies after its final method still has not completed a valid run.
  $processResults=[regex]::Matches($Text,'(?m)^INSTRUMENTATION_CODE: (-?\d+)\s*$')
  if ($processResults.Count -ne 1 -or $processResults[0].Groups[1].Value -ne '-1' -or $Text -match 'INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg=') {
    foreach ($test in $Expected) { if ($test.status -in @('PASSED','MEASURED')) { $test.status='BLOCKED'; $test.note='Missing or unsuccessful instrumentation completion' } }
  }
}

function Select-QaTests {
  param([object[]]$Inventory, [string[]]$Tiers, [string[]]$Selector, [string[]]$Features=@(), [switch]$AllowEmpty)
  foreach($feature in $Features){if($feature -notin $script:QaFeatureNames){throw "Unknown test feature: $feature"}}
  $selected=@($Inventory | Where-Object { $_.tier -in $Tiers })
  if($Features.Count){$selected=@($selected | Where-Object { @($_.features | Where-Object {$_ -in $Features}).Count -gt 0 })}
  if ($Selector.Count -gt 0) {
    foreach ($s in $Selector) {
      if (@($selected | Where-Object { $_.id -eq $s -or ($_.id -split '#')[0] -eq $s }).Count -eq 0) { throw "Unmatched or out-of-scope selector: $s" }
    }
    $selected=@($selected | Where-Object { $_.id -in $Selector -or ($_.id -split '#')[0] -in $Selector })
  }
  if ($selected.Count -eq 0 -and !$AllowEmpty) { throw 'Selection contains zero tests.' }
  return $selected
}

