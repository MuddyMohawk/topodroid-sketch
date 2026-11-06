function Invoke-QaBoundedProcess {
  param([string]$FilePath,[string[]]$Arguments,[int]$TimeoutSeconds=30,[string]$Description='native command')
  # Redirect asynchronously: a full stdout pipe must not defeat the wall deadline.
  $info=New-Object Diagnostics.ProcessStartInfo
  $info.FileName=$FilePath
  $info.Arguments=($Arguments | ForEach-Object {
    '"'+[regex]::Replace([regex]::Replace($_,'(\\*)"','$1$1\"'),'(\\+)$','$1$1')+'"'
  }) -join ' '
  $info.UseShellExecute=$false;$info.CreateNoWindow=$true
  $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
  $process=New-Object Diagnostics.Process
  $process.StartInfo=$info
  try {
    $null=$process.Start()
    $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
    if(!$process.WaitForExit($TimeoutSeconds*1000)) {
      try{$process.Kill()}catch{}
      throw "$Description timed out after ${TimeoutSeconds}s."
    }
    # The client owns both pipes; do not wait indefinitely for inherited handles.
    if(!$stdout.Wait(1000) -or !$stderr.Wait(1000)){throw "$Description did not close its output pipes."}
    $output=$stdout.Result+$stderr.Result
    if($process.ExitCode -ne 0){throw "$Description failed with exit code $($process.ExitCode): $output"}
    return $output.TrimEnd()
  } finally {$process.Dispose()}
}

function Invoke-AdbChecked {
  param(
    [string]$Adb,
    [string]$Serial,
    [string[]]$Arguments,
    [string]$Description = "adb command",
    [int]$TimeoutSeconds = 30
  )

  $output=Invoke-QaBoundedProcess -FilePath $Adb -Arguments (@('-s',$Serial)+$Arguments) -TimeoutSeconds $TimeoutSeconds -Description $Description
  return @($output -split "`r?`n")
}

function Get-QaDeviceBootId {
  param([string]$Adb,[string]$Serial)
  $raw=(Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @('shell','cat /proc/sys/kernel/random/boot_id; getprop sys.boot_completed') -Description "Device health $Serial" -TimeoutSeconds 5) -join "`n"
  $parts=@($raw.Trim() -split '\s+')
  if($parts.Count -ne 2 -or $parts[0] -notmatch '^[a-fA-F0-9-]{36}$' -or $parts[1] -ne '1'){throw "Device $Serial has not finished booting: $raw"}
  return $parts[0]
}

function Stop-QaProcessTree {
  param([Diagnostics.Process]$Process)
  try {
    if(!$Process.HasExited) {
      # Kill only this runner's wrapper and its adb client, never the shared adb server.
      $null=Invoke-QaBoundedProcess -FilePath "$env:SystemRoot/System32/taskkill.exe" -Arguments @('/PID',[string]$Process.Id,'/T','/F') -TimeoutSeconds 3 -Description 'Stop stalled instrumentation client'
    }
  } catch {try{$Process.Kill()}catch{}}
}

function Stop-QaInstrumentationPackages {
  param([string]$Adb,[string]$Serial,[string]$AppPackage,[string]$TestPackage)
  if(!$AppPackage -and !$TestPackage){return}
  $packages=@($TestPackage,$AppPackage | Where-Object {$_})
  foreach($name in $packages){if($name -notmatch '^com[.]topodroid[.]TDX[.]sketch(?:[.]test)?$'){throw "Refusing to stop a package outside Sketch QA: $name"}}
  $command=($packages | ForEach-Object {"am force-stop $_"}) -join '; '
  try {
    Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @('shell',$command) -Description 'Stop interrupted Sketch instrumentation on device' -TimeoutSeconds 5 | Out-Null
  } catch {Write-Host "Bounded instrumentation stop: $_"}
}

function Invoke-NativeChecked {
  param(
    [string]$FilePath,
    [string[]]$Arguments,
    [string]$Description,
    [int]$TimeoutSeconds = 0,
    [int]$IdleTimeoutSeconds = 0,
    [int]$HeartbeatSeconds = 30,
    [string]$WorkingDirectory = ""
  )

  $effectiveWorkingDirectory = if ([string]::IsNullOrWhiteSpace($WorkingDirectory)) {
    (Get-Location).Path
  } else {
    $WorkingDirectory
  }

  if ($TimeoutSeconds -gt 0 -or $IdleTimeoutSeconds -gt 0) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
    $safeName = ($Description -replace '[^A-Za-z0-9_.-]', '_')
    $stdout = Join-Path $env:TEMP "topodroid-$stamp-$safeName.out"
    $exitCodeFile = Join-Path $env:TEMP "topodroid-$stamp-$safeName.exit"
    $timeoutLabel = if ($TimeoutSeconds -gt 0) { "timeout ${TimeoutSeconds}s" } else { "no total timeout" }
    $idleLabel = if ($IdleTimeoutSeconds -gt 0) { ", idle ${IdleTimeoutSeconds}s" } else { "" }
    $powershell = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"

    function Quote-NativePowerShellString {
      param([string]$Value)
      return "'" + ($Value -replace "'", "''") + "'"
    }

    Write-Host ("[{0}] START {1} ({2}{3})" -f (Get-Date -Format "HH:mm:ss"), $Description, $timeoutLabel, $idleLabel)
    $argumentLiterals = ($Arguments | ForEach-Object { Quote-NativePowerShellString $_ }) -join ", "
    $command = @"
`$ErrorActionPreference = "Continue"
`$code = 0
`$nativeArgs = @($argumentLiterals)
try {
  Set-Location -LiteralPath $(Quote-NativePowerShellString $effectiveWorkingDirectory)
  & $(Quote-NativePowerShellString $FilePath) @nativeArgs 2>&1 | ForEach-Object {
    if (`$_ -is [System.Management.Automation.ErrorRecord]) { `$_.Exception.Message } else { `$_ }
  } | Tee-Object -FilePath $(Quote-NativePowerShellString $stdout)
  if (`$LASTEXITCODE -is [int]) { `$code = `$LASTEXITCODE }
} catch {
  `$code = 1
  (`$_ | Out-String) | Tee-Object -FilePath $(Quote-NativePowerShellString $stdout) -Append
}
Set-Content -LiteralPath $(Quote-NativePowerShellString $exitCodeFile) -Value `$code
exit `$code
"@
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $powershell
    $processInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encodedCommand"
    $processInfo.UseShellExecute = $true
    $processInfo.WorkingDirectory = $effectiveWorkingDirectory
    $processInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $process = [System.Diagnostics.Process]::Start($processInfo)
    $stdoutLines = 0
    $lastOutputAt = 0.0
    $lastHeartbeatAt = 0.0
    $completed = $true
    $timedOut = $false
    $idleTimedOut = $false

    while ($true) {
      $completed = $process.WaitForExit(2000)
      $newLines = 0
      $newLines += Write-NewProcessOutput -Path $stdout -LineOffset ([ref]$stdoutLines)
      if ($newLines -gt 0) {
        $lastOutputAt = $timer.Elapsed.TotalSeconds
      }
      if ($completed) {
        break
      }
      if ($HeartbeatSeconds -gt 0 -and ($timer.Elapsed.TotalSeconds - $lastHeartbeatAt) -ge $HeartbeatSeconds) {
        $lastHeartbeatAt = $timer.Elapsed.TotalSeconds
        $idleFor = $timer.Elapsed.TotalSeconds - $lastOutputAt
        $remaining = if ($TimeoutSeconds -gt 0) { [Math]::Max(0, $TimeoutSeconds - $timer.Elapsed.TotalSeconds) } else { -1 }
        $remainingText = if ($remaining -ge 0) { ", remaining before timeout {0:n0}s" -f $remaining } else { "" }
        Write-Host ("[{0}] WAIT {1}: elapsed {2:n1}s, no output for {3:n1}s{4}" -f (Get-Date -Format "HH:mm:ss"), $Description, $timer.Elapsed.TotalSeconds, $idleFor, $remainingText)
      }
      if ($TimeoutSeconds -gt 0 -and $timer.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
        $timedOut = $true
        $completed = $false
        break
      }
      if ($IdleTimeoutSeconds -gt 0 -and ($timer.Elapsed.TotalSeconds - $lastOutputAt) -ge $IdleTimeoutSeconds) {
        $idleTimedOut = $true
        $completed = $false
        break
      }
    }
    $timer.Stop()

    if (-not $completed) {
      $why = if ($idleTimedOut) { "IDLE TIMEOUT" } elseif ($timedOut) { "TIMEOUT" } else { "STOPPED" }
      Write-Host ("[{0}] {1} {2} after {3:n1}s" -f (Get-Date -Format "HH:mm:ss"), $why, $Description, $timer.Elapsed.TotalSeconds)
      try { $process.Kill() } catch { }
      $null = Write-NewProcessOutput -Path $stdout -LineOffset ([ref]$stdoutLines)
      Remove-Item -LiteralPath $stdout, $exitCodeFile -Force -ErrorAction SilentlyContinue
      if ($idleTimedOut) {
        throw "$Description made no progress for $IdleTimeoutSeconds seconds."
      }
      throw "$Description timed out after $TimeoutSeconds seconds."
    }

    $process.WaitForExit()
    $process.Refresh()
    $null = Write-NewProcessOutput -Path $stdout -LineOffset ([ref]$stdoutLines)
    $exitCode = $process.ExitCode
    if (Test-Path $exitCodeFile) {
      $exitCodeText = (Get-Content -LiteralPath $exitCodeFile -ErrorAction SilentlyContinue | Select-Object -First 1)
      $parsedExitCode = 0
      if ([int]::TryParse($exitCodeText, [ref]$parsedExitCode)) {
        $exitCode = $parsedExitCode
      }
    }
    Remove-Item -LiteralPath $stdout, $exitCodeFile -Force -ErrorAction SilentlyContinue
    if ($exitCode -ne 0) {
      throw "$Description failed with exit code $exitCode."
    }
    Write-Host ("[{0}] PASS {1} in {2:n1}s" -f (Get-Date -Format "HH:mm:ss"), $Description, $timer.Elapsed.TotalSeconds)
    return
  }

  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    Push-Location $effectiveWorkingDirectory
    & $FilePath @Arguments
    $exitCode = $LASTEXITCODE
  } finally {
    Pop-Location
    $ErrorActionPreference = $previousPreference
  }

  if ($exitCode -ne 0) {
    throw "$Description failed with exit code $exitCode."
  }
}

function Clear-LocalArtifactDirectory {
  param(
    [string]$Path,
    [string]$Description = "local artifacts",
    [switch]$Fatal
  )

  if (-not (Test-Path $Path)) {
    return
  }

  $resolved = [IO.Path]::GetFullPath($Path)
  $workspace = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot)).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
  if (!$resolved.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase)) { throw "Refusing recursive deletion outside workspace: $resolved" }
  $lastError = $null
  for ($attempt = 1; $attempt -le 3; $attempt++) {
    try {
      Remove-Item -LiteralPath $Path -Recurse -Force
      return
    } catch {
      $lastError = $_
      Start-Sleep -Seconds $attempt
    }
  }

  $message = "Could not clear $Description at $Path after 3 attempts: $lastError"
  if ($Fatal) {
    throw $message
  }
  Write-Host "WARN $message"
}

function Resolve-AndroidSdkPath {
  param([string]$RepoRoot)

  $localProperties = Join-Path $RepoRoot 'local.properties'
  $sdk = ''
  if (Test-Path -LiteralPath $localProperties) {
    $line = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^\s*sdk[.]dir\s*[=:]' } | Select-Object -First 1
    if ($line) {
      # local.properties escapes Windows backslashes, colons and spaces.
      $sdk = [regex]::Replace(($line -replace '^\s*sdk[.]dir\s*[=:]\s*', '').Trim(), '\\([\\:= #!])', '$1')
    }
  }
  if (!$sdk) { $sdk = $env:ANDROID_HOME }
  if (!$sdk) { $sdk = $env:ANDROID_SDK_ROOT }
  if (!$sdk -and $env:LOCALAPPDATA) { $sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
  if (!$sdk) { throw 'Android SDK not configured. Set sdk.dir in local.properties or ANDROID_HOME.' }
  if (![IO.Path]::IsPathRooted($sdk)) { $sdk = Join-Path $RepoRoot $sdk }
  return [IO.Path]::GetFullPath($sdk)
}

function Resolve-GradleUserHome {
  param([string]$RepoRoot)

  $candidate = if (-not [string]::IsNullOrWhiteSpace($env:TOPO_TEST_GRADLE_HOME)) {
    $env:TOPO_TEST_GRADLE_HOME
  } else {
    Join-Path $RepoRoot ".gradle-test-home"
  }
  $expanded = [Environment]::ExpandEnvironmentVariables($candidate)
  if ([System.IO.Path]::IsPathRooted($expanded)) {
    return [System.IO.Path]::GetFullPath($expanded)
  }
  return [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $expanded))
}

function Resolve-GradleApkOutput {
  param(
    [string]$OutputDirectory,
    [string]$Description
  )

  $metadataPath = Join-Path $OutputDirectory "output-metadata.json"
  if (Test-Path $metadataPath) {
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    if ($metadata.elements -and $metadata.elements.Count -gt 0) {
      $outputFile = $metadata.elements[0].outputFile
      if (-not [string]::IsNullOrWhiteSpace($outputFile)) {
        $candidate = Join-Path $OutputDirectory $outputFile
        if (Test-Path $candidate) {
          return $candidate
        }
        throw "$Description APK listed in metadata does not exist: $candidate"
      }
    }
  }

  if (Test-Path $OutputDirectory) {
    $apk = Get-ChildItem -LiteralPath $OutputDirectory -Filter "*.apk" -File |
      Sort-Object LastWriteTime -Descending |
      Select-Object -First 1
    if ($apk) {
      return $apk.FullName
    }
  }

  throw "Could not locate $Description APK in $OutputDirectory"
}

function Get-AdbShellText {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$Command,
    [string]$Description
  )

  $output = Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @("shell", $Command) -Description $Description
  return (($output -join "`n").Trim())
}

function Assert-AdbTarget {
  param(
    [string]$Adb,
    [string]$Serial
  )

  if (-not (Test-Path $Adb)) {
    throw "adb not found at $Adb"
  }

  $state = (Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @("get-state") -Description "Check adb target $Serial" | Select-Object -First 1).ToString().Trim()
  if ($state -ne "device") {
    throw "Target serial $Serial is not ready. adb get-state returned '$state'."
  }
}

function Assert-EmulatorProfile {
  param(
    [string]$Adb,
    [string]$Serial
  )

  $qemu = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "getprop ro.kernel.qemu" -Description "Verify emulator safety"
  if ($qemu -ne '1') { throw "Refusing ordinary tests on physical target $Serial before installation or package reset." }
  $expectedSize = "2560x1600"
  $expectedDensity = "320"
  $expectedFont = "1.0"

  $size = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "wm size" -Description "Check emulator size"
  if ($size -notmatch [regex]::Escape($expectedSize)) {
    throw "Unexpected emulator size for $Serial. Expected $expectedSize, got '$size'."
  }

  $density = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "wm density" -Description "Check emulator density"
  if ($density -notmatch "\b$expectedDensity\b") {
    throw "Unexpected emulator density for $Serial. Expected $expectedDensity dpi, got '$density'."
  }

  $fontScale = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "settings get system font_scale" -Description "Check emulator font scale"
  if (-not $fontScale.StartsWith($expectedFont)) {
    throw "Unexpected emulator font scale for $Serial. Expected $expectedFont, got '$fontScale'."
  }

  $locale = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "getprop persist.sys.locale" -Description "Check emulator locale"
  if ([string]::IsNullOrWhiteSpace($locale)) {
    $locale = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "getprop ro.product.locale" -Description "Check emulator product locale"
  }
  if ([string]::IsNullOrWhiteSpace($locale)) {
    $locale = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "getprop persist.sys.language" -Description "Check emulator language"
  }
  if ($locale -notmatch "^en([-_]|$)") {
    throw "Unexpected emulator locale for $Serial. Expected English, got '$locale'."
  }
}

function Install-ApkChecked {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$ApkPath,
    [string]$Description
  )

  if (-not (Test-Path $ApkPath)) {
    throw "$Description APK does not exist: $ApkPath"
  }

  $output = Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @("install", "-r", "-t", $ApkPath) -Description $Description -TimeoutSeconds 120
  $text = $output -join "`n"
  if ($text -notmatch "Success") {
    $output | ForEach-Object { Write-Host $_ }
    throw "$Description did not report a successful install."
  }
}

function Assert-InstalledPackage {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$PackageName
  )

  $path = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "pm path $PackageName" -Description "Check installed package $PackageName"
  if ($path -notmatch "^package:") {
    throw "Expected package $PackageName is not installed on $Serial."
  }
}

function Assert-InstrumentationRunner {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$TestPackage,
    [string]$Runner,
    [string]$AppPackage
  )

  $instrumentation = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "pm list instrumentation" -Description "Check instrumentation runner"
  $expectedPrefix = "instrumentation:$TestPackage/$Runner"
  $runnerLine = ($instrumentation -split "`n" | Where-Object { $_.Trim().StartsWith($expectedPrefix) } | Select-Object -First 1)
  if ([string]::IsNullOrWhiteSpace($runnerLine)) {
    throw "Expected instrumentation runner $expectedPrefix is not installed on $Serial."
  }
  if ($runnerLine -notmatch [regex]::Escape("(target=$AppPackage)")) {
    throw "Instrumentation runner $expectedPrefix does not target $AppPackage."
  }
}

function Install-TestApksAndPreflight {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$AppApk,
    [string]$TestApk,
    [string]$AppPackage,
    [string]$TestPackage,
    [string]$Runner
  )

  Assert-AdbTarget -Adb $Adb -Serial $Serial
  Assert-EmulatorProfile -Adb $Adb -Serial $Serial
  Install-ApkChecked -Adb $Adb -Serial $Serial -ApkPath $AppApk -Description "Install app APK"
  Install-ApkChecked -Adb $Adb -Serial $Serial -ApkPath $TestApk -Description "Install test APK"
  Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $AppPackage
  Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $TestPackage
  Assert-InstrumentationRunner -Adb $Adb -Serial $Serial -TestPackage $TestPackage -Runner $Runner -AppPackage $AppPackage
  Write-Host "Preflight OK for $Serial ($AppPackage / $TestPackage)."
}

function Write-InstrumentationDiagnostics {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$AppPackage,
    [string]$TestPackage,
    [string]$ArtifactsLocal
  )

  # Three bounded calls, at most 15s total. Do not ask a frozen Android UI to
  # construct another hierarchy, or collect a bugreport during validation.
  $clock=[Diagnostics.Stopwatch]::StartNew()
  $messages=New-Object 'System.Collections.Generic.List[string]'
  foreach($probe in @(
    @{name='health';args=@('shell','cat /proc/sys/kernel/random/boot_id; getprop sys.boot_completed')},
    @{name='window';args=@('shell','dumpsys window windows | head -100')},
    @{name='crash-log';args=@('logcat','-b','crash','-d','-t','80')}
  )) {
    try {
      $budget=[Math]::Min(5,[Math]::Floor(15-$clock.Elapsed.TotalSeconds))
      if($budget -lt 1){break}
      $value=Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments $probe.args -Description $probe.name -TimeoutSeconds $budget
      $messages.Add("$($probe.name):`n$($value -join "`n")")
    } catch {
      $messages.Add("$($probe.name): $_")
      if($probe.name -eq 'health'){break}
    }
  }
  $text=$messages -join "`n"
  Write-Host "Bounded instrumentation diagnostics ($Serial, $([Math]::Round($clock.Elapsed.TotalSeconds,1))s)"
  if($ArtifactsLocal){
    New-Item -ItemType Directory -Force -Path $ArtifactsLocal | Out-Null
    $text | Set-Content -LiteralPath (Join-Path $ArtifactsLocal ('infrastructure-'+$Serial+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')+'.txt'))
  }
}

function Write-NewProcessOutput {
  param(
    [string]$Path,
    [ref]$LineOffset
  )

  if (-not (Test-Path $Path)) {
    return 0
  }

  try {
    $lines = @(Get-Content -LiteralPath $Path)
  } catch {
    return 0
  }

  $count = $lines.Count
  if ($count -le $LineOffset.Value) {
    return 0
  }

  for ($i = $LineOffset.Value; $i -lt $count; $i++) {
    Write-Host $lines[$i]
  }
  $written = $count - $LineOffset.Value
  $LineOffset.Value = $count
  return $written
}

function Invoke-InstrumentationTimed {
  param(
    [string]$Adb,
    [string]$Serial,
    [string[]]$Arguments,
    [string]$Name = "Instrumentation",
    [int]$EstimateSeconds = 0,
    [int]$TimeoutSeconds = 420,
    [int]$IdleTimeoutSeconds = 0,
    [int]$HeartbeatSeconds = 30,
    [string]$AppPackage = "",
    [string]$TestPackage = "",
    [string]$ArtifactsLocal = "",
    [scriptblock]$OnOutputSaved
  )

  try {$bootId=Get-QaDeviceBootId -Adb $Adb -Serial $Serial}
  catch {throw "QA_INFRA: Device unavailable before instrumentation: $_"}
  $stamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
  $safeName = ($Name -replace '[^A-Za-z0-9_.-]', '_')
  $stdout = Join-Path $env:TEMP "topodroid-$stamp-$safeName.out"
  $exitCodeFile = Join-Path $env:TEMP "topodroid-$stamp-$safeName.exit"
  $allArguments = @("-s", $Serial) + $Arguments
  if ($TimeoutSeconds -le 0 -and $EstimateSeconds -gt 0) {
    $TimeoutSeconds = [Math]::Max($EstimateSeconds * 2, $EstimateSeconds + 60)
  }
  $timeoutLabel = if ($TimeoutSeconds -gt 0) { "timeout ${TimeoutSeconds}s" } else { "no total timeout" }
  $idleLabel = if ($IdleTimeoutSeconds -gt 0) { ", idle ${IdleTimeoutSeconds}s" } else { "" }
  $estimateLabel = if ($EstimateSeconds -gt 0) { ", estimate ${EstimateSeconds}s" } else { "" }
  $displayName = "$Name ($timeoutLabel$idleLabel$estimateLabel)"

  Write-Host ("[{0}] START {1}" -f (Get-Date -Format "HH:mm:ss"), $displayName)
  function Quote-InstrumentationPowerShellString {
    param([string]$Value)
    return "'" + ($Value -replace "'", "''") + "'"
  }
  $argumentLiterals = ($allArguments | ForEach-Object { Quote-InstrumentationPowerShellString $_ }) -join ", "
  $powershell = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
  $command = @"
`$ErrorActionPreference = "Continue"
`$code = 0
`$nativeArgs = @($argumentLiterals)
try {
  & $(Quote-InstrumentationPowerShellString $Adb) @nativeArgs 2>&1 | ForEach-Object {
    if (`$_ -is [System.Management.Automation.ErrorRecord]) { `$_.Exception.Message } else { `$_ }
  } | Tee-Object -FilePath $(Quote-InstrumentationPowerShellString $stdout)
  if (`$LASTEXITCODE -is [int]) { `$code = `$LASTEXITCODE }
} catch {
  `$code = 1
  (`$_ | Out-String) | Tee-Object -FilePath $(Quote-InstrumentationPowerShellString $stdout) -Append
}
Set-Content -LiteralPath $(Quote-InstrumentationPowerShellString $exitCodeFile) -Value `$code
exit `$code
"@
  $commandFile = Join-Path $env:TEMP "topodroid-$stamp-$safeName.ps1"
  Set-Content -LiteralPath $commandFile -Value $command
  $processInfo = New-Object System.Diagnostics.ProcessStartInfo
  $processInfo.FileName = $powershell
  $processInfo.Arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $commandFile + '"'
  $processInfo.UseShellExecute = $true
  $processInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
  $timer = [System.Diagnostics.Stopwatch]::StartNew()
  $process = [System.Diagnostics.Process]::Start($processInfo)
  $completed = $true
  $timedOut = $false
  $idleTimedOut = $false
  $stdoutLines = 0
  $lastOutputAt = 0.0
  $lastHeartbeatAt = 0.0
  $estimateWarned = $false
  $lastHealthAt=0.0
  $infrastructureReason=''

  while ($true) {
    $completed = $process.WaitForExit(2000)
    $newLines = 0
    $newLines += Write-NewProcessOutput -Path $stdout -LineOffset ([ref]$stdoutLines)
    if ($newLines -gt 0) {
      $lastOutputAt = $timer.Elapsed.TotalSeconds
    }
    if ($completed) {
      break
    }
    if(($timer.Elapsed.TotalSeconds-$lastHealthAt) -ge 15) {
      $lastHealthAt=$timer.Elapsed.TotalSeconds
      try {
        $currentBoot=Get-QaDeviceBootId -Adb $Adb -Serial $Serial
        if($currentBoot -ne $bootId){$infrastructureReason='Device rebooted during instrumentation';break}
      } catch {$infrastructureReason="Device health unavailable: $_";break}
    }
    if ($EstimateSeconds -gt 0 -and -not $estimateWarned -and $timer.Elapsed.TotalSeconds -ge $EstimateSeconds) {
      $estimateWarned = $true
      Write-Host ("[{0}] WARN {1} exceeded estimate {2}s; elapsed {3:n1}s" -f (Get-Date -Format "HH:mm:ss"), $Name, $EstimateSeconds, $timer.Elapsed.TotalSeconds)
    }
    if ($HeartbeatSeconds -gt 0 -and ($timer.Elapsed.TotalSeconds - $lastHeartbeatAt) -ge $HeartbeatSeconds) {
      $lastHeartbeatAt = $timer.Elapsed.TotalSeconds
      $idleFor = $timer.Elapsed.TotalSeconds - $lastOutputAt
      $remaining = if ($TimeoutSeconds -gt 0) { [Math]::Max(0, $TimeoutSeconds - $timer.Elapsed.TotalSeconds) } else { -1 }
      $remainingText = if ($remaining -ge 0) { ", remaining before timeout {0:n0}s" -f $remaining } else { "" }
      Write-Host ("[{0}] WAIT {1}: elapsed {2:n1}s, no instrumentation output for {3:n1}s{4}" -f (Get-Date -Format "HH:mm:ss"), $Name, $timer.Elapsed.TotalSeconds, $idleFor, $remainingText)
    }
    if ($TimeoutSeconds -gt 0 -and $timer.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
      $timedOut = $true
      $completed = $false
      break
    }
    if ($IdleTimeoutSeconds -gt 0 -and ($timer.Elapsed.TotalSeconds - $lastOutputAt) -ge $IdleTimeoutSeconds) {
      $idleTimedOut = $true
      $completed = $false
      break
    }
  }
  $timer.Stop()

  if (-not $completed) {
    $why = if ($idleTimedOut) { "IDLE TIMEOUT" } elseif ($timedOut) { "TIMEOUT" } else { "STOPPED" }
    Write-Host ("[{0}] {1} {2} after {3:n1}s" -f (Get-Date -Format "HH:mm:ss"), $why, $Name, $timer.Elapsed.TotalSeconds)
    Stop-QaProcessTree $process
    $null = Write-NewProcessOutput -Path $stdout -LineOffset ([ref]$stdoutLines)
    if ($ArtifactsLocal -and (Test-Path $stdout)) {
      Copy-Item -LiteralPath $stdout -Destination (Join-Path $ArtifactsLocal ($Name+'.instrumentation.log')) -Force
    }
    if(!$infrastructureReason){$infrastructureReason=if($idleTimedOut){"No instrumentation progress for ${IdleTimeoutSeconds}s"}else{"Instrumentation exceeded ${TimeoutSeconds}s"}}
    if($OnOutputSaved){& $OnOutputSaved $infrastructureReason}
    Write-InstrumentationDiagnostics -Adb $Adb -Serial $Serial -AppPackage $AppPackage -TestPackage $TestPackage -ArtifactsLocal $ArtifactsLocal
    Stop-QaInstrumentationPackages -Adb $Adb -Serial $Serial -AppPackage $AppPackage -TestPackage $TestPackage
    Remove-Item -LiteralPath $stdout,$exitCodeFile,$commandFile -Force -ErrorAction SilentlyContinue
    throw "QA_INFRA: $infrastructureReason"
  }

  $process.WaitForExit()
  $process.Refresh()
  Remove-Item -LiteralPath $commandFile -Force -ErrorAction SilentlyContinue
  $null = Write-NewProcessOutput -Path $stdout -LineOffset ([ref]$stdoutLines)
  $output = @()
  if (Test-Path $stdout) { $output += Get-Content -LiteralPath $stdout }
  $exitCode = $process.ExitCode
  if (Test-Path $exitCodeFile) {
    $exitCodeText = (Get-Content -LiteralPath $exitCodeFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    $parsedExitCode = 0
    if ([int]::TryParse($exitCodeText, [ref]$parsedExitCode)) {
      $exitCode = $parsedExitCode
    }
  }
  if ($ArtifactsLocal) {
    New-Item -ItemType Directory -Force -Path $ArtifactsLocal | Out-Null
    $output | Set-Content -LiteralPath (Join-Path $ArtifactsLocal ($Name + '.instrumentation.log'))
  }
  Remove-Item -LiteralPath $stdout, $exitCodeFile -Force -ErrorAction SilentlyContinue

  $text = $output -join "`n"
  try {
    if((Get-QaDeviceBootId -Adb $Adb -Serial $Serial) -ne $bootId){$infrastructureReason='Device rebooted during instrumentation'}
  } catch {$infrastructureReason="Device health unavailable: $_"}
  if(!$infrastructureReason -and ($text -notmatch '(?m)^INSTRUMENTATION_CODE:' -or $text -match 'INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg=')) {
    $infrastructureReason='Instrumentation aborted without normal completion; see raw log (ANR/process crash is distinct from an assertion failure)'
  }
  if($OnOutputSaved){& $OnOutputSaved $infrastructureReason}
  if($infrastructureReason){
    Write-InstrumentationDiagnostics -Adb $Adb -Serial $Serial -AppPackage $AppPackage -TestPackage $TestPackage -ArtifactsLocal $ArtifactsLocal
    Stop-QaInstrumentationPackages -Adb $Adb -Serial $Serial -AppPackage $AppPackage -TestPackage $TestPackage
    throw "QA_INFRA: $infrastructureReason"
  }
  $hasInstrumentationResult = ($text -match "INSTRUMENTATION_CODE:") -or ($text -match "INSTRUMENTATION_RESULT:")
  if ($exitCode -ne 0 -and -not $hasInstrumentationResult) {
    throw "$Name instrumentation command failed with exit code $exitCode."
  }

  $ok = $true
  if ($text -match "FAILURES!!!" -or $text -match "INSTRUMENTATION_STATUS: Error" -or $text -match "INSTRUMENTATION_FAILED") {
    $ok = $false
  }
  $anrFailure = ($text -match "(?m)^INSTRUMENTATION_RESULT: shortMsg=keyDispatchingTimedOut") -or
                ($text -match "(?m)^INSTRUMENTATION_RESULT: longMsg=.*(Input dispatching timed out|is not responding|Not Responding|ANR)")
  if ($anrFailure) {
    $ok = $false
    Write-InstrumentationDiagnostics -Adb $Adb -Serial $Serial -AppPackage $AppPackage -TestPackage $TestPackage -ArtifactsLocal $ArtifactsLocal
  }
  if ($text -notmatch "OK \([1-9]\d* tests?\)") {
    $ok = $false
  }

  $status = if ($ok) { "PASS" } else { "FAIL" }
  Write-Host ("[{0}] {1} {2} in {3:n1}s" -f (Get-Date -Format "HH:mm:ss"), $status, $Name, $timer.Elapsed.TotalSeconds)
  if ($ok -and $EstimateSeconds -gt 0 -and $timer.Elapsed.TotalSeconds -gt $EstimateSeconds) {
    Write-Host ("[{0}] WARN {1} finished {2:n1}s over estimate" -f (Get-Date -Format "HH:mm:ss"), $Name, ($timer.Elapsed.TotalSeconds - $EstimateSeconds))
  }
  return $ok
}
