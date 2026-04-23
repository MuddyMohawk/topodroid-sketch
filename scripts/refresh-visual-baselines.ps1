param(
  [string]$Serial = "emulator-5554"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
$Gradle = Join-Path $RepoRoot "gradlew.bat"
$GradleUserHome = Join-Path $RepoRoot ".gradle-test-home"
$LocalProperties = Join-Path $RepoRoot "local.properties"
$ArtifactsLocal = Join-Path $RepoRoot "tmp-recorded-latest"
$ArtifactsRemote = "/sdcard/Android/data/com.topodroid.TDX/files/test-artifacts"
$GoldenSource = Join-Path $ArtifactsLocal "recorded-goldens\emulator_2560x1600_320dpi_font1.0"
$GoldenTarget = Join-Path $RepoRoot "app\src\androidTest\assets\goldens\emulator_2560x1600_320dpi_font1.0"
$AppPackage = "com.topodroid.TDX"
$TestPackage = "com.topodroid.TDX.test"
$Runner = "$TestPackage/androidx.test.runner.AndroidJUnitRunner"
$FullClass = "com.topodroid.TDX.VisualGoldenInstrumentedTest"

function Get-SdkPath {
  if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
  if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
  if (Test-Path $LocalProperties) {
    $sdkLine = Get-Content $LocalProperties | Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1
    if ($sdkLine) {
      return ($sdkLine -replace '^sdk.dir=', '') -replace '\\:', ':'
    }
  }
  return (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
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
  & $Adb -s $Serial @Arguments
}

function Grant-Permissions {
  $permissions = @(
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_ADMIN",
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.POST_NOTIFICATIONS"
  )

  foreach ($permission in $permissions) {
    try {
      Invoke-Adb @("shell", "pm", "grant", $AppPackage, $permission) | Out-Null
    } catch {
      # Ignore permissions that are not runtime-grantable on this platform.
    }
  }

  try {
    Invoke-Adb @("shell", "appops", "set", "--uid", $AppPackage, "MANAGE_EXTERNAL_STORAGE", "allow") | Out-Null
  } catch {
    # Ignore if the platform rejects the app-op.
  }
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

  & $Gradle -g $GradleUserHome ":app:assembleDebug" ":app:assembleDebugAndroidTest"

  Invoke-Adb @("install", "-r", "-t", $AppApk) | Out-Null
  Invoke-Adb @("install", "-r", "-t", $TestApk) | Out-Null
  Invoke-Adb @("shell", "pm", "clear", $AppPackage) | Out-Null
  Invoke-Adb @("shell", "pm", "clear", $TestPackage) | Out-Null
  Grant-Permissions

  if (Test-Path $ArtifactsLocal) {
    Remove-Item -LiteralPath $ArtifactsLocal -Recurse -Force
  }

  Invoke-Adb @("shell", "am", "instrument", "-w", "-e", "class", $FullClass, "-e", "visual_baseline_mode", "record", $Runner)

  if (Test-Path $ArtifactsLocal) {
    Remove-Item -LiteralPath $ArtifactsLocal -Recurse -Force
  }
  Invoke-Adb @("pull", $ArtifactsRemote, $ArtifactsLocal) | Out-Null

  if (-not (Test-Path $GoldenSource)) {
    throw "Recorded goldens were not pulled to $GoldenSource"
  }

  New-Item -ItemType Directory -Force -Path $GoldenTarget | Out-Null
  Copy-Item -LiteralPath (Join-Path $GoldenSource '*') -Destination $GoldenTarget -Recurse -Force
} finally {
  Pop-Location
}
