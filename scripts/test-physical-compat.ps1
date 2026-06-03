param(
  [string]$Serial = "R32X200DN0T",
  [string]$VanillaApk = "",
  [string]$SketchApk = "",
  [string]$TestApk = "",
  [string]$ExpectedModel = "SM-T577U",
  [switch]$SkipBuild,
  [switch]$AllowDowngrade,
  [switch]$DestructiveClean
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "android-test-common.ps1")
$RepoRoot = Split-Path -Parent $ScriptDir
$Gradle = Join-Path $RepoRoot "gradlew.bat"
$GradleUserHome = Resolve-GradleUserHome -RepoRoot $RepoRoot
$LocalProperties = Join-Path $RepoRoot "local.properties"
$VanillaPackage = "com.topodroid.TDX"
$SketchPackage = "com.topodroid.TDX.sketch"
$TestPackage = "com.topodroid.TDX.sketch.test"
$Runner = "androidx.test.runner.AndroidJUnitRunner"
$Instrumentation = "$TestPackage/$Runner"
$VanillaActivity = "$VanillaPackage/.MainWindow"
$SketchActivity = "$SketchPackage/com.topodroid.TDX.MainWindow"
$VanillaFileProvider = "com.topodroid.fileprovider"
$SketchFileProvider = "com.topodroid.TDX.sketch.fileprovider"
$RunStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$SurveyName = "compat_$RunStamp"
$RunDir = Join-Path $RepoRoot "tmp-physical-compat-runs\$RunStamp"
$ArtifactsLocal = Join-Path $RunDir "sketch-test-artifacts"
$ArtifactsRemote = "/sdcard/Android/data/$SketchPackage/files/test-artifacts"
$CaseArtifactsRemote = "$ArtifactsRemote/physical_compat_$SurveyName"
$DiagnosticsDir = Join-Path $RunDir "diagnostics"
$Permissions = @(
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.ACCESS_COARSE_LOCATION",
  "android.permission.CAMERA",
  "android.permission.RECORD_AUDIO",
  "android.permission.BLUETOOTH_SCAN",
  "android.permission.BLUETOOTH_CONNECT"
)

if ([string]::IsNullOrWhiteSpace($VanillaApk)) {
  $VanillaApk = Join-Path $RepoRoot "test-fixtures\TopoDroidX-6.4.53-36.apk"
}
if ([string]::IsNullOrWhiteSpace($SketchApk)) {
  $SketchApk = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
}
if ([string]::IsNullOrWhiteSpace($TestApk)) {
  $TestApk = Join-Path $RepoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
}

function Convert-SdkPath {
  param([string]$Path)
  return $Path.Replace('\:', ':')
}

function Get-SdkPath {
  if ($env:ANDROID_SDK_ROOT) { return Convert-SdkPath $env:ANDROID_SDK_ROOT }
  if ($env:ANDROID_HOME) { return Convert-SdkPath $env:ANDROID_HOME }
  if (Test-Path $LocalProperties) {
    $sdkLine = Get-Content $LocalProperties | Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1
    if ($sdkLine) {
      return Convert-SdkPath ($sdkLine -replace '^sdk.dir=', '')
    }
  }
  return Convert-SdkPath (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
}

function Get-JavaHome {
  $candidates = @()
  if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
  $candidates += @(
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\Java\jdk-21",
    "C:\Program Files\Java\jdk-21.0.2",
    "C:\Program Files\Java\jdk-21.0.1"
  )

  foreach ($candidate in $candidates) {
    if (Test-Path (Join-Path $candidate 'bin\java.exe')) {
      return $candidate
    }
  }

  throw "Could not find a local JDK 21 installation."
}

function Get-AaptPath {
  param([string]$SdkPath)

  $buildTools = Join-Path $SdkPath "build-tools"
  $aapt = Get-ChildItem -Path $buildTools -Filter aapt.exe -Recurse -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
  if ([string]::IsNullOrWhiteSpace($aapt)) {
    throw "Could not find aapt.exe under $buildTools."
  }
  return $aapt
}

function Invoke-Adb {
  param([string[]]$Arguments, [string]$Description = "")
  if ([string]::IsNullOrWhiteSpace($Description)) {
    $Description = "adb $($Arguments -join ' ')"
  }
  Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments $Arguments -Description $Description
}

function Save-AdbOutput {
  param(
    [string[]]$Arguments,
    [string]$FileName,
    [string]$Description
  )

  New-Item -ItemType Directory -Force -Path $DiagnosticsDir | Out-Null
  $path = Join-Path $DiagnosticsDir $FileName
  try {
    $output = & $Adb -s $Serial @Arguments 2>&1
    $output | Set-Content -LiteralPath $path
    Write-Host "Diagnostic: $Description -> $path"
  } catch {
    "Failed to collect ${Description}: $_" | Set-Content -LiteralPath $path
    Write-Host "WARN diagnostic failed: $Description"
  }
}

function Write-FailureDiagnostics {
  param([string]$Reason)

  Write-Host "Collecting physical compatibility diagnostics: $Reason"
  New-Item -ItemType Directory -Force -Path $DiagnosticsDir | Out-Null
  Save-AdbOutput -Arguments @("get-state") -FileName "adb-state.txt" -Description "adb state"
  Save-AdbOutput -Arguments @("shell", "getprop", "ro.product.model") -FileName "device-model.txt" -Description "device model"
  Save-AdbOutput -Arguments @("shell", "getprop", "ro.product.cpu.abilist") -FileName "device-abis.txt" -Description "device ABIs"
  Save-AdbOutput -Arguments @("shell", "dumpsys", "window") -FileName "window.txt" -Description "focused window"
  Save-AdbOutput -Arguments @("shell", "dumpsys", "activity", "top") -FileName "activity-top.txt" -Description "top activity"
  Save-AdbOutput -Arguments @("shell", "dumpsys", "package", $VanillaPackage) -FileName "package-vanilla.txt" -Description "vanilla package"
  Save-AdbOutput -Arguments @("shell", "dumpsys", "package", $SketchPackage) -FileName "package-sketch.txt" -Description "Sketch package"
  Save-AdbOutput -Arguments @("shell", "appops", "get", "--uid", $VanillaPackage) -FileName "appops-vanilla.txt" -Description "vanilla appops"
  Save-AdbOutput -Arguments @("shell", "appops", "get", "--uid", $SketchPackage) -FileName "appops-sketch.txt" -Description "Sketch appops"
  Save-AdbOutput -Arguments @("shell", "logcat", "-t", "1000") -FileName "logcat-tail.txt" -Description "logcat tail"

  try {
    $remotePng = "/sdcard/Download/topodroid-physical-compat-$RunStamp.png"
    $localPng = Join-Path $DiagnosticsDir "screen.png"
    Invoke-Adb @("shell", "screencap", "-p", $remotePng) -Description "Capture failure screenshot" | Out-Null
    Invoke-Adb @("pull", $remotePng, $localPng) -Description "Pull failure screenshot" | Out-Null
    Write-Host "Diagnostic: screenshot -> $localPng"
  } catch {
    Write-Host "WARN screenshot diagnostic failed: $_"
  }

  try {
    $remoteXml = "/sdcard/Download/topodroid-physical-compat-$RunStamp.xml"
    $localXml = Join-Path $DiagnosticsDir "window.xml"
    Invoke-Adb @("shell", "uiautomator", "dump", $remoteXml) -Description "Dump failure UI hierarchy" | Out-Null
    Invoke-Adb @("pull", $remoteXml, $localXml) -Description "Pull failure UI hierarchy" | Out-Null
    Write-Host "Diagnostic: UI hierarchy -> $localXml"
  } catch {
    Write-Host "WARN UI hierarchy diagnostic failed: $_"
  }

  try {
    Pull-IfExists -RemotePath $CaseArtifactsRemote -LocalPath $ArtifactsLocal
    Pull-IfExists -RemotePath "/sdcard/Download/$SurveyName.zip" -LocalPath (Join-Path $RunDir "download-$SurveyName.zip")
    Pull-IfExists -RemotePath "/sdcard/Documents/TopoDroid Sketch/zip/$SurveyName.zip" -LocalPath (Join-Path $RunDir "sketch-$SurveyName.zip")
    Pull-IfExists -RemotePath "/sdcard/Documents/TDX/TopoDroid/zip/$SurveyName.zip" -LocalPath (Join-Path $RunDir "vanilla-$SurveyName.zip")
  } catch {
    Write-Host "WARN artifact diagnostic pull failed: $_"
  }
}

function Invoke-Step {
  param(
    [string]$Name,
    [scriptblock]$Action,
    [string]$Package = "",
    [string]$Survey = $SurveyName
  )

  $timer = [System.Diagnostics.Stopwatch]::StartNew()
  $context = "survey=$Survey"
  if (-not [string]::IsNullOrWhiteSpace($Package)) { $context = "package=$Package $context" }
  Write-Host ("[{0}] START {1} ({2})" -f (Get-Date -Format "HH:mm:ss"), $Name, $context)
  try {
    & $Action
    $timer.Stop()
    Write-Host ("[{0}] PASS {1} in {2:n1}s ({3})" -f (Get-Date -Format "HH:mm:ss"), $Name, $timer.Elapsed.TotalSeconds, $context)
  } catch {
    $timer.Stop()
    Write-Host ("[{0}] FAIL {1} after {2:n1}s ({3})" -f (Get-Date -Format "HH:mm:ss"), $Name, $timer.Elapsed.TotalSeconds, $context)
    Write-FailureDiagnostics -Reason $Name
    throw
  }
}

function Wait-Until {
  param(
    [string]$Description,
    [scriptblock]$Condition,
    [int]$TimeoutSeconds = 120,
    [int]$HeartbeatSeconds = 30
  )

  $timer = [System.Diagnostics.Stopwatch]::StartNew()
  $lastHeartbeat = 0.0
  while ($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
    if (& $Condition) {
      $timer.Stop()
      return
    }
    if (($timer.Elapsed.TotalSeconds - $lastHeartbeat) -ge $HeartbeatSeconds) {
      $lastHeartbeat = $timer.Elapsed.TotalSeconds
      $remaining = [Math]::Max(0, $TimeoutSeconds - $timer.Elapsed.TotalSeconds)
      Write-Host ("[{0}] WAIT {1}: elapsed {2:n1}s, remaining {3:n0}s" -f (Get-Date -Format "HH:mm:ss"), $Description, $timer.Elapsed.TotalSeconds, $remaining)
    }
    Start-Sleep -Seconds 2
  }
  $timer.Stop()
  throw "$Description timed out after $TimeoutSeconds seconds."
}

function Get-ApkBadging {
  param([string]$ApkPath)

  if (-not (Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath"
  }

  $output = & $Aapt dump badging $ApkPath 2>&1
  if ($LASTEXITCODE -ne 0) {
    $output | ForEach-Object { Write-Host $_ }
    throw "aapt dump badging failed for $ApkPath."
  }
  return @($output)
}

function Get-BadgingPackageName {
  param([string[]]$Badging)
  $line = $Badging | Where-Object { $_ -like "package:*" } | Select-Object -First 1
  if ($line -notmatch "name='([^']+)'") {
    throw "Could not parse APK package name from badging."
  }
  return $Matches[1]
}

function Get-BadgingVersionLabel {
  param([string[]]$Badging)
  $line = $Badging | Where-Object { $_ -like "package:*" } | Select-Object -First 1
  $versionName = if ($line -match "versionName='([^']+)'") { $Matches[1] } else { "unknown" }
  $versionCode = if ($line -match "versionCode='([^']+)'") { $Matches[1] } else { "unknown" }
  return "$versionName ($versionCode)"
}

function Get-BadgingNativeAbis {
  param([string[]]$Badging)
  $line = $Badging | Where-Object { $_ -like "native-code:*" } | Select-Object -First 1
  if ([string]::IsNullOrWhiteSpace($line)) {
    return @()
  }

  $abis = @()
  foreach ($match in [regex]::Matches($line, "'([^']+)'")) {
    $abis += $match.Groups[1].Value
  }
  return $abis
}

function Assert-ApkPackage {
  param(
    [string]$Actual,
    [string]$Expected,
    [string]$Description
  )

  if ($Actual -ne $Expected) {
    throw "$Description package mismatch. Expected $Expected, got $Actual."
  }
}

function Assert-ApkAbiCompatible {
  param(
    [string[]]$ApkAbis,
    [string[]]$DeviceAbis,
    [string]$Description
  )

  $apkAbiList = @($ApkAbis | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
  $deviceAbiList = @($DeviceAbis | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

  if ($apkAbiList.Count -eq 0) {
    return
  }

  $matches = @($apkAbiList | Where-Object { $deviceAbiList -contains $_ })
  if ($matches.Count -eq 0) {
    throw "$Description APK native ABIs [$($apkAbiList -join ', ')] do not match device ABIs [$($deviceAbiList -join ', ')]."
  }
}

function Install-ApkPhysical {
  param(
    [string]$ApkPath,
    [string]$Description
  )

  $args = @("install", "-r", "-t")
  if ($AllowDowngrade) {
    $args += "-d"
  }
  $args += $ApkPath
  $output = Invoke-Adb $args -Description "Install $Description APK"
  $text = $output -join "`n"
  if ($text -notmatch "Success") {
    $output | ForEach-Object { Write-Host $_ }
    throw "Install $Description APK did not report Success."
  }
}

function Assert-ResolveActivity {
  param(
    [string]$PackageName,
    [string]$ExpectedComponent
  )

  $output = Invoke-Adb @("shell", "cmd", "package", "resolve-activity", "--brief", $PackageName) -Description "Resolve $PackageName"
  $text = ($output -join "`n")
  if ($text -notmatch [regex]::Escape($ExpectedComponent)) {
    $output | ForEach-Object { Write-Host $_ }
    throw "Expected $PackageName to resolve to $ExpectedComponent."
  }
}

function Assert-ProviderAuthority {
  param(
    [string]$PackageName,
    [string]$Authority
  )

  $output = Invoke-Adb @("shell", "dumpsys", "package", $PackageName) -Description "Check provider authority for $PackageName"
  $text = ($output -join "`n")
  if ($text -notmatch [regex]::Escape("[$Authority]")) {
    throw "Expected $PackageName to register provider authority $Authority."
  }
}

function Start-AppChecked {
  param(
    [string]$PackageName,
    [string]$Component
  )

  $output = Invoke-Adb @("shell", "am", "start", "-W", "-n", $Component) -Description "Launch $Component"
  $text = ($output -join "`n")
  if ($text -notmatch "Status: ok") {
    $output | ForEach-Object { Write-Host $_ }
    throw "Launch $Component did not report Status: ok."
  }
  Dismiss-PermissionDialogs -PackageName $PackageName -MaxClicks 8
}

function Assert-RemoteDirectory {
  param([string]$Path)
  $quotedPath = "'" + ($Path -replace "'", "'\''") + "'"
  $result = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "if [ -d $quotedPath ]; then echo exists; else echo missing; fi" -Description "Check $Path"
  if ($result -ne "exists") {
    throw "Expected remote directory to exist: $Path"
  }
}

function Get-UiXml {
  param([string]$Label)

  New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
  $safe = ($Label -replace '[^A-Za-z0-9_.-]', '_')
  $remote = "/sdcard/Download/topodroid-$RunStamp-$safe.xml"
  $local = Join-Path $RunDir "$safe.xml"
  Invoke-Adb @("shell", "uiautomator", "dump", $remote) -Description "Dump UI $Label" | Out-Null
  Invoke-Adb @("pull", $remote, $local) -Description "Pull UI $Label" | Out-Null
  return [xml](Get-Content -LiteralPath $local -Raw)
}

function Get-NodeCenter {
  param($Node)
  $bounds = $Node.GetAttribute("bounds")
  if ($bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
    return $null
  }
  $x1 = [int]$Matches[1]
  $y1 = [int]$Matches[2]
  $x2 = [int]$Matches[3]
  $y2 = [int]$Matches[4]
  return @{
    X = [int](($x1 + $x2) / 2)
    Y = [int](($y1 + $y2) / 2)
  }
}

function Find-PositiveDialogNode {
  param(
    [xml]$Xml,
    [string]$PackageName
  )

  $positiveResourceIds = @(
    "com.android.permissioncontroller:id/permission_allow_button",
    "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
    "android:id/button1",
    "${PackageName}:id/btn_ok",
    "${PackageName}:id/button_ok",
    "${PackageName}:id/btn_skip",
    "${PackageName}:id/btn_next"
  )
  $positiveTexts = @(
    "While using the app",
    "Only this time",
    "Allow",
    "OK",
    "Continue",
    "Got it",
    "Skip"
  )

  foreach ($node in $Xml.SelectNodes("//node")) {
    $resourceId = $node.GetAttribute("resource-id")
    $text = $node.GetAttribute("text")
    $desc = $node.GetAttribute("content-desc")
    if ($positiveResourceIds -contains $resourceId) {
      return $node
    }
    foreach ($positiveText in $positiveTexts) {
      if ($text -eq $positiveText -or $desc -eq $positiveText) {
        return $node
      }
    }
  }
  return $null
}

function Dismiss-PermissionDialogs {
  param(
    [string]$PackageName,
    [int]$MaxClicks = 6
  )

  $clicks = 0
  while ($clicks -lt $MaxClicks) {
    $xml = Get-UiXml -Label "prompt-$PackageName-$clicks"
    $node = Find-PositiveDialogNode -Xml $xml -PackageName $PackageName
    if ($null -eq $node) {
      return
    }
    $center = Get-NodeCenter -Node $node
    if ($null -eq $center) {
      throw "Found prompt button but could not parse bounds."
    }
    $label = $node.GetAttribute("text")
    if ([string]::IsNullOrWhiteSpace($label)) { $label = $node.GetAttribute("resource-id") }
    Write-Host ("[{0}] PROMPT {1}: tapping '{2}' at {3},{4}" -f (Get-Date -Format "HH:mm:ss"), $PackageName, $label, $center.X, $center.Y)
    Invoke-Adb @("shell", "input", "tap", "$($center.X)", "$($center.Y)") -Description "Tap prompt for $PackageName" | Out-Null
    Start-Sleep -Seconds 1
    $clicks += 1
  }

  $finalXml = Get-UiXml -Label "prompt-$PackageName-final"
  $remaining = Find-PositiveDialogNode -Xml $finalXml -PackageName $PackageName
  if ($null -ne $remaining) {
    throw "Prompt remains after $MaxClicks clicks for $PackageName."
  }
}

function Grant-PackagePermissions {
  param([string]$PackageName)

  foreach ($permission in $Permissions) {
    Invoke-Adb @("shell", "pm", "grant", $PackageName, $permission) -Description "Grant $permission to $PackageName" | Out-Null
  }
  Invoke-Adb @("shell", "appops", "set", "--uid", $PackageName, "MANAGE_EXTERNAL_STORAGE", "allow") -Description "Allow MANAGE_EXTERNAL_STORAGE for $PackageName" | Out-Null
  Save-AdbOutput -Arguments @("shell", "dumpsys", "package", $PackageName) -FileName "permissions-$PackageName.txt" -Description "permissions for $PackageName"
  Save-AdbOutput -Arguments @("shell", "appops", "get", "--uid", $PackageName) -FileName "appops-$PackageName.txt" -Description "appops for $PackageName"
}

function Invoke-DestructiveClean {
  if (-not $DestructiveClean) {
    return
  }

  $model = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "getprop ro.product.model" -Description "Confirm destructive model"
  if (-not [string]::IsNullOrWhiteSpace($ExpectedModel) -and $model -ne $ExpectedModel) {
    throw "Refusing -DestructiveClean: expected model $ExpectedModel, got $model."
  }
  Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $VanillaPackage
  Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $SketchPackage

  Invoke-Adb @("shell", "pm", "clear", $VanillaPackage) -Description "Destructive clear vanilla package" | Out-Null
  Invoke-Adb @("shell", "pm", "clear", $SketchPackage) -Description "Destructive clear Sketch package" | Out-Null
  Invoke-Adb @("shell", "pm", "clear", $TestPackage) -Description "Destructive clear Sketch test package" | Out-Null
  Invoke-Adb @("shell", "sh", "-c", "rm -rf '/sdcard/Android/data/$SketchPackage/files/test-artifacts' '/sdcard/Android/data/$TestPackage/files'") -Description "Remove known test artifact dirs" | Out-Null
  Invoke-Adb @("shell", "sh", "-c", "find '/sdcard/Documents/TDX' '/sdcard/Documents/TopoDroid Sketch' '/sdcard/Download' -maxdepth 2 \( -name 'compat_*' -o -name 'physical_compat_*' \) -exec rm -rf {} + 2>/dev/null || true") -Description "Remove old compat artifacts" | Out-Null
}

function Pull-IfExists {
  param(
    [string]$RemotePath,
    [string]$LocalPath
  )

  $quotedRemote = "'" + ($RemotePath -replace "'", "'\''") + "'"
  $exists = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "if [ -e $quotedRemote ]; then echo exists; else echo missing; fi" -Description "Check $RemotePath"
  if ($exists -eq "exists") {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LocalPath) | Out-Null
    Invoke-Adb @("pull", $RemotePath, $LocalPath) -Description "Pull $RemotePath" | Out-Null
  } else {
    Write-Host "Artifact not present on device: $RemotePath"
  }
}

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
$TranscriptPath = Join-Path $RunDir "physical-compat.log"
$SdkPath = Get-SdkPath
$JavaHome = Get-JavaHome
$Adb = Join-Path $SdkPath "platform-tools\adb.exe"
$Aapt = Get-AaptPath -SdkPath $SdkPath
$TranscriptStarted = $false

Push-Location $RepoRoot
try {
  Start-Transcript -Path $TranscriptPath -Force | Out-Null
  $TranscriptStarted = $true
  Write-Host "Physical compatibility run: $RunStamp"
  Write-Host "Artifacts: $RunDir"
  Write-Host "Survey: $SurveyName"

  $env:JAVA_HOME = $JavaHome
  $env:PATH = "$JavaHome\bin;$env:PATH"

  Invoke-Step -Name "Build Sketch/test APKs" -Action {
    if ($SkipBuild) {
      Write-Host ("[{0}] SKIP Gradle build; using existing APKs" -f (Get-Date -Format "HH:mm:ss"))
    } else {
      Invoke-NativeChecked -FilePath $Gradle -Arguments @("-g", $GradleUserHome, "--no-daemon", "--console=plain", "--no-problems-report", ":app:assembleDebug", ":app:assembleDebugAndroidTest") -Description "Gradle physical compat build" -TimeoutSeconds 1200 -IdleTimeoutSeconds 300
    }
  } -Package $SketchPackage

  $deviceAbis = @()
  Invoke-Step -Name "Physical device preflight" -Action {
    Assert-AdbTarget -Adb $Adb -Serial $Serial
    $model = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "getprop ro.product.model" -Description "Get device model"
    $release = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "getprop ro.build.version.release" -Description "Get Android release"
    $abisText = Get-AdbShellText -Adb $Adb -Serial $Serial -Command "getprop ro.product.cpu.abilist" -Description "Get device ABIs"
    $script:deviceAbis = @($abisText -split "," | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if (($script:deviceAbis -notcontains "arm64-v8a") -and ($script:deviceAbis -notcontains "armeabi-v7a")) {
      throw "Expected an ARM device ABI, got [$($script:deviceAbis -join ', ')]."
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedModel) -and $model -ne $ExpectedModel) {
      throw "Expected model $ExpectedModel, got $model."
    }
    Write-Host "Device $Serial model=$model Android=$release ABIs=$($script:deviceAbis -join ', ')"
  }

  Invoke-Step -Name "Validate APK metadata" -Action {
    $vanillaBadging = Get-ApkBadging -ApkPath $VanillaApk
    $sketchBadging = Get-ApkBadging -ApkPath $SketchApk
    $testBadging = Get-ApkBadging -ApkPath $TestApk

    $vanillaPackageName = Get-BadgingPackageName -Badging $vanillaBadging
    $sketchPackageName = Get-BadgingPackageName -Badging $sketchBadging
    $testPackageName = Get-BadgingPackageName -Badging $testBadging
    Assert-ApkPackage -Actual $vanillaPackageName -Expected $VanillaPackage -Description "Vanilla"
    Assert-ApkPackage -Actual $sketchPackageName -Expected $SketchPackage -Description "Sketch"
    Assert-ApkPackage -Actual $testPackageName -Expected $TestPackage -Description "Sketch test"

    Assert-ApkAbiCompatible -ApkAbis (Get-BadgingNativeAbis -Badging $vanillaBadging) -DeviceAbis $script:deviceAbis -Description "Vanilla"
    Assert-ApkAbiCompatible -ApkAbis (Get-BadgingNativeAbis -Badging $sketchBadging) -DeviceAbis $script:deviceAbis -Description "Sketch"
    Write-Host "Vanilla APK: $(Get-BadgingVersionLabel -Badging $vanillaBadging) $VanillaApk"
    Write-Host "Sketch APK: $(Get-BadgingVersionLabel -Badging $sketchBadging) $SketchApk"
    Write-Host "Sketch test APK: $(Get-BadgingVersionLabel -Badging $testBadging) $TestApk"
  }

  Invoke-Step -Name "Install packages" -Action {
    Install-ApkPhysical -ApkPath $VanillaApk -Description "vanilla"
    Install-ApkPhysical -ApkPath $SketchApk -Description "Sketch"
    Install-ApkPhysical -ApkPath $TestApk -Description "Sketch test"
  }

  Invoke-Step -Name "Package coexistence preflight" -Action {
    Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $VanillaPackage
    Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $SketchPackage
    Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $TestPackage
    Assert-InstrumentationRunner -Adb $Adb -Serial $Serial -TestPackage $TestPackage -Runner $Runner -AppPackage $SketchPackage
    Assert-ResolveActivity -PackageName $VanillaPackage -ExpectedComponent $VanillaActivity
    Assert-ResolveActivity -PackageName $SketchPackage -ExpectedComponent $SketchActivity
    Assert-ProviderAuthority -PackageName $VanillaPackage -Authority $VanillaFileProvider
    Assert-ProviderAuthority -PackageName $SketchPackage -Authority $SketchFileProvider
    if ($VanillaFileProvider -eq $SketchFileProvider) {
      throw "FileProvider authorities must be distinct."
    }
    Save-AdbOutput -Arguments @("shell", "dumpsys", "package", $VanillaPackage) -FileName "package-vanilla-installed.txt" -Description "vanilla installed package"
    Save-AdbOutput -Arguments @("shell", "dumpsys", "package", $SketchPackage) -FileName "package-sketch-installed.txt" -Description "Sketch installed package"
  }

  Invoke-Step -Name "Optional destructive clean" -Action {
    Invoke-DestructiveClean
  }

  Invoke-Step -Name "Permission bootstrap vanilla" -Package $VanillaPackage -Action {
    Grant-PackagePermissions -PackageName $VanillaPackage
  }
  Invoke-Step -Name "Permission bootstrap Sketch" -Package $SketchPackage -Action {
    Grant-PackagePermissions -PackageName $SketchPackage
  }

  Invoke-Step -Name "Launch vanilla and clear prompts" -Package $VanillaPackage -Action {
    Start-AppChecked -PackageName $VanillaPackage -Component $VanillaActivity
  }
  Invoke-Step -Name "Launch Sketch and clear prompts" -Package $SketchPackage -Action {
    Start-AppChecked -PackageName $SketchPackage -Component $SketchActivity
  }

  Invoke-Step -Name "Storage roots smoke" -Action {
    Assert-RemoteDirectory -Path "/sdcard/Documents/TDX"
    Assert-RemoteDirectory -Path "/sdcard/Documents/TopoDroid Sketch"
  }

  Invoke-Step -Name "Physical vanilla/Sketch ZIP round-trip" -Package $SketchPackage -Action {
    $ok = Invoke-InstrumentationTimed -Adb $Adb -Serial $Serial `
      -Arguments @(
        "shell", "am", "instrument", "-w", "-r",
        "-e", "class", "com.topodroid.TDX.PhysicalCompatInstrumentedTest#sketchVanillaZipRoundTrip_onPhysicalTablet",
        "-e", "physical_compat_survey", $SurveyName,
        "-e", "physical_compat_vanilla_package", $VanillaPackage,
        "-e", "physical_compat_sketch_package", $SketchPackage,
        $Instrumentation
      ) `
      -Name "Physical vanilla/Sketch ZIP round-trip" `
      -EstimateSeconds 720 -TimeoutSeconds 1500 -IdleTimeoutSeconds 300 -HeartbeatSeconds 30 `
      -AppPackage $SketchPackage -TestPackage $TestPackage -ArtifactsLocal $ArtifactsLocal
    if (-not $ok) {
      throw "Physical compatibility instrumentation reported failures."
    }
  }

  Invoke-Step -Name "Pull run artifacts" -Action {
    Clear-LocalArtifactDirectory -Path $ArtifactsLocal -Description "physical compatibility artifacts"
    Pull-IfExists -RemotePath $CaseArtifactsRemote -LocalPath $ArtifactsLocal
    Pull-IfExists -RemotePath "/sdcard/Documents/TDX/TopoDroid/zip/$SurveyName.zip" -LocalPath (Join-Path $RunDir "vanilla-$SurveyName.zip")
  }

  Write-Host "Physical compatibility PASS for $SurveyName"
  Write-Host "Artifacts: $RunDir"
} finally {
  if ($TranscriptStarted) {
    Stop-Transcript | Out-Null
  }
  Pop-Location
}
