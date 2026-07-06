param(
  [string]$Serial = "emulator-5554",
  [switch]$SkipBuild
)

# Runs RenderPerfInstrumentedTest: times repeated off-screen renders of the
# Demo fixture (normal + edit mode) and reports mean/p50/p95 frame times
# (render_perf.txt in tmp-test-artifacts). Run before/after renderer changes
# on the same device for A/B numbers.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "android-test-common.ps1")
$RepoRoot = Split-Path -Parent $ScriptDir
$Gradle = Join-Path $RepoRoot "gradlew.bat"
$GradleUserHome = Resolve-GradleUserHome -RepoRoot $RepoRoot
$LocalProperties = Join-Path $RepoRoot "local.properties"
$ArtifactsLocal = Join-Path $RepoRoot "tmp-test-artifacts"
$ArtifactsRemote = "/sdcard/Android/data/com.topodroid.TDX.sketch/files/test-artifacts"
$AppPackage = "com.topodroid.TDX.sketch"
$TestPackage = "com.topodroid.TDX.sketch.test"
$Runner = "androidx.test.runner.AndroidJUnitRunner"
$Instrumentation = "$TestPackage/$Runner"
$RenderTests = @(
  @{
    Name = "Render perf timings"
    Class = "com.topodroid.TDX.RenderPerfInstrumentedTest#renderDemoFixture_reportsFrameTimes"
    Estimate = 240
    Timeout = 600
    IdleTimeout = 300
  }
)

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

function Grant-Permissions {
  $permissions = @(
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.BLUETOOTH_CONNECT"
  )

  $ErrorActionPreference = "Continue"

  foreach ($permission in $permissions) {
    & $Adb -s $Serial "shell" "pm" "grant" $AppPackage $permission 2>$null | Out-Null
  }

  & $Adb -s $Serial "shell" "appops" "set" "--uid" $AppPackage "MANAGE_EXTERNAL_STORAGE" "allow" 2>$null | Out-Null
}

function Stop-TestRuntime {
  Invoke-Adb @("shell", "am", "force-stop", $TestPackage) | Out-Null
  Invoke-Adb @("shell", "am", "force-stop", $AppPackage) | Out-Null
}

function Reset-TestRuntime {
  Stop-TestRuntime
  Invoke-Adb @("shell", "pm", "clear", $AppPackage) | Out-Null
  Invoke-Adb @("shell", "pm", "clear", $TestPackage) | Out-Null
  Grant-Permissions
}

$SdkPath = Get-SdkPath
$JavaHome = Get-JavaHome
$Adb = Join-Path $SdkPath "platform-tools\adb.exe"
$AppApkDir = Join-Path $RepoRoot "app\build\outputs\apk\debug"
$TestApkDir = Join-Path $RepoRoot "app\build\outputs\apk\androidTest\debug"

Push-Location $RepoRoot
try {
  $env:JAVA_HOME = $JavaHome
  $env:PATH = "$JavaHome\bin;$env:PATH"

  if ($SkipBuild) {
    Write-Host ("[{0}] SKIP Gradle build; using existing APKs" -f (Get-Date -Format "HH:mm:ss"))
  } else {
    Invoke-NativeChecked -FilePath $Gradle -Arguments @("-g", $GradleUserHome, "--console=plain", ":app:assembleDebug", ":app:assembleDebugAndroidTest") -Description "Gradle build" -TimeoutSeconds 1200 -IdleTimeoutSeconds 300
  }

  $AppApk = Resolve-GradleApkOutput -OutputDirectory $AppApkDir -Description "app debug"
  $TestApk = Resolve-GradleApkOutput -OutputDirectory $TestApkDir -Description "androidTest debug"
  # perf timings render off-screen at a fixed canvas size, so the emulator
  # golden profile (screen size/density/font) is not required: preflight
  # without Assert-EmulatorProfile so physical devices can run too
  Assert-AdbTarget -Adb $Adb -Serial $Serial
  Install-ApkChecked -Adb $Adb -Serial $Serial -ApkPath $AppApk -Description "Install app APK"
  Install-ApkChecked -Adb $Adb -Serial $Serial -ApkPath $TestApk -Description "Install test APK"
  Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $AppPackage
  Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $TestPackage
  Assert-InstrumentationRunner -Adb $Adb -Serial $Serial -TestPackage $TestPackage -Runner $Runner -AppPackage $AppPackage
  Write-Host "Preflight OK for $Serial ($AppPackage / $TestPackage)."
  Invoke-Adb @("shell", "pm", "clear", $AppPackage) | Out-Null
  Invoke-Adb @("shell", "pm", "clear", $TestPackage) | Out-Null
  Grant-Permissions

  $instrumentationOk = $true
  foreach ($test in $RenderTests) {
    Reset-TestRuntime
    $ok = Invoke-InstrumentationTimed -Adb $Adb -Serial $Serial `
      -Arguments @("shell", "am", "instrument", "-w", "-r", "-e", "class", $test.Class, $Instrumentation) `
      -Name $test.Name -EstimateSeconds $test.Estimate -TimeoutSeconds $test.Timeout -IdleTimeoutSeconds $test.IdleTimeout -AppPackage $AppPackage -TestPackage $TestPackage -ArtifactsLocal $ArtifactsLocal
    Stop-TestRuntime
    $instrumentationOk = $instrumentationOk -and $ok
  }

  Clear-LocalArtifactDirectory -Path $ArtifactsLocal -Description "render perf artifacts"
  Invoke-Adb @("pull", $ArtifactsRemote, $ArtifactsLocal) | Out-Null

  if (-not $instrumentationOk) {
    throw "Instrumentation reported test failures."
  }
} finally {
  Pop-Location
}
