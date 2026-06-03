param(
  [string]$Serial = "emulator-5554",
  [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "android-test-common.ps1")
$RepoRoot = Split-Path -Parent $ScriptDir
$Gradle = Join-Path $RepoRoot "gradlew.bat"
$GradleUserHome = Resolve-GradleUserHome -RepoRoot $RepoRoot
$LocalProperties = Join-Path $RepoRoot "local.properties"
$ArtifactsLocal = Join-Path $RepoRoot "tmp-recorded-latest"
$ArtifactsRemote = "/sdcard/Android/data/com.topodroid.TDX.sketch/files/test-artifacts"
$GoldenSource = Join-Path $ArtifactsLocal "recorded-goldens\emulator_2560x1600_320dpi_font1.0"
$GoldenTarget = Join-Path $RepoRoot "app\src\androidTest\assets\goldens\emulator_2560x1600_320dpi_font1.0"
$AppPackage = "com.topodroid.TDX.sketch"
$TestPackage = "com.topodroid.TDX.sketch.test"
$Runner = "androidx.test.runner.AndroidJUnitRunner"
$Instrumentation = "$TestPackage/$Runner"
$FullClass = "com.topodroid.TDX.VisualGoldenInstrumentedTest"

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

function Convert-SdkPath {
  param([string]$Path)
  return $Path.Replace('\:', ':')
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

function Invoke-Adb {
  param([string[]]$Arguments)
  Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments $Arguments -Description "adb $($Arguments -join ' ')"
}

function Invoke-Instrumentation {
  param([string[]]$Arguments)

  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = & $Adb -s $Serial @Arguments 2>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
  }

  $output | ForEach-Object { Write-Host $_ }

  if ($exitCode -ne 0) {
    throw "Instrumentation command failed with exit code $exitCode."
  }

  $text = $output -join "`n"
  if ($text -match "FAILURES!!!" -or $text -match "INSTRUMENTATION_STATUS: Error" -or $text -match "INSTRUMENTATION_FAILED") {
    return $false
  }

  if ($text -notmatch "OK \(\d+ tests?\)") {
    return $false
  }

  return $true
}

function Grant-Permissions {
  # Only runtime-grantable permissions. BLUETOOTH / BLUETOOTH_ADMIN are
  # install-time on modern Android and POST_NOTIFICATIONS isn't declared by
  # the manifest; attempting to grant them just produces SecurityException
  # noise from the platform and nothing else.
  $permissions = @(
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.BLUETOOTH_CONNECT"
  )

  # $ErrorActionPreference assignment is function-local in PowerShell, so this
  # reverts automatically when the function returns. Needed because under the
  # script-wide "Stop" preference, any stderr from a native command raises a
  # terminating NativeCommandError even when stderr is redirected.
  $ErrorActionPreference = "Continue"

  foreach ($permission in $permissions) {
    & $Adb -s $Serial "shell" "pm" "grant" $AppPackage $permission 2>$null | Out-Null
  }

  & $Adb -s $Serial "shell" "appops" "set" "--uid" $AppPackage "MANAGE_EXTERNAL_STORAGE" "allow" 2>$null | Out-Null
}

$SdkPath = Get-SdkPath
$JavaHome = Get-JavaHome
$Adb = Join-Path $SdkPath "platform-tools\adb.exe"
$AppApk = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
$TestApk = Join-Path $RepoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"

Push-Location $RepoRoot
try {
  $env:JAVA_HOME = $JavaHome
  $env:PATH = "$JavaHome\bin;$env:PATH"
  $env:_JAVA_OPTIONS = "-XX:TieredStopAtLevel=1"

  if ($SkipBuild) {
    Write-Host ("[{0}] SKIP Gradle build; using existing APKs" -f (Get-Date -Format "HH:mm:ss"))
  } else {
    Invoke-NativeChecked -FilePath $Gradle -Arguments @("-g", $GradleUserHome, "--console=plain", ":app:assembleDebug", ":app:assembleDebugAndroidTest") -Description "Gradle build" -TimeoutSeconds 1200 -IdleTimeoutSeconds 300
  }

  Install-TestApksAndPreflight -Adb $Adb -Serial $Serial -AppApk $AppApk -TestApk $TestApk -AppPackage $AppPackage -TestPackage $TestPackage -Runner $Runner
  Invoke-Adb @("shell", "pm", "clear", $AppPackage) | Out-Null
  Invoke-Adb @("shell", "pm", "clear", $TestPackage) | Out-Null
  Grant-Permissions

  $instrumentationOk = Invoke-InstrumentationTimed -Adb $Adb -Serial $Serial `
    -Arguments @("shell", "am", "instrument", "-w", "-r", "-e", "class", $FullClass, "-e", "visual_baseline_mode", "record", $Instrumentation) `
    -Name "Refresh visual baselines" -EstimateSeconds 750 -TimeoutSeconds 1200 -IdleTimeoutSeconds 300 -AppPackage $AppPackage -TestPackage $TestPackage -ArtifactsLocal $ArtifactsLocal

  Clear-LocalArtifactDirectory -Path $ArtifactsLocal -Description "recorded visual baseline artifacts" -Fatal
  Invoke-Adb @("pull", $ArtifactsRemote, $ArtifactsLocal) | Out-Null

  if (-not $instrumentationOk) {
    throw "Instrumentation reported test failures."
  }

  if (-not (Test-Path $GoldenSource)) {
    throw "Recorded goldens were not pulled to $GoldenSource"
  }

  New-Item -ItemType Directory -Force -Path $GoldenTarget | Out-Null

  # NOTE: must use -Path, not -LiteralPath, so the trailing '*' is treated as a
  # wildcard. With -LiteralPath PowerShell looks for a file literally named
  # '*', finds nothing, and Copy-Item silently no-ops — which means the staged
  # PNGs under $GoldenSource never reach the committed golden tree, and the
  # test would appear to "run fine" while leaving stale baselines in place.
  $sourceItems = Get-ChildItem -LiteralPath $GoldenSource -Force
  if ( -not $sourceItems -or $sourceItems.Count -eq 0 ) {
    throw "No recorded goldens found in $GoldenSource to copy"
  }
  foreach ( $item in $sourceItems ) {
    Copy-Item -LiteralPath $item.FullName -Destination $GoldenTarget -Recurse -Force
  }
  Write-Host ( "Copied {0} recorded golden(s) -> {1}" -f $sourceItems.Count, $GoldenTarget )
} finally {
  Pop-Location
}
