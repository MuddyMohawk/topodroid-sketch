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
$ArtifactsLocal = Join-Path $RepoRoot "tmp-test-artifacts"
$ArtifactsRemote = "/sdcard/Android/data/com.topodroid.TDX.sketch/files/test-artifacts"
$AppPackage = "com.topodroid.TDX.sketch"
$TestPackage = "com.topodroid.TDX.sketch.test"
$Runner = "androidx.test.runner.AndroidJUnitRunner"
$Instrumentation = "$TestPackage/$Runner"
$FullTests = @(
  @{
    Name = "Visual golden sketch screen"
    Class = "com.topodroid.TDX.VisualGoldenInstrumentedTest#createSurvey_addShots_createSketch_drawPresetsAndLineWeights_matchesGolden"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Visual ZIP round-trip"
    Class = "com.topodroid.TDX.VisualGoldenInstrumentedTest#exportZip_includesSurveyCore_and_importRoundTripsThroughPicker"
    Estimate = 300
    Timeout = 600
    IdleTimeout = 300
  },
  @{
    Name = "Imported demo sketch visual fidelity"
    Class = "com.topodroid.TDX.ImportedSketchVisualInstrumentedTest#importDemoSketch_liveScreenAndPngExport_matchGoldens"
    Estimate = 300
    Timeout = 600
    IdleTimeout = 300
  },
  @{
    Name = "Visual PNG export"
    Class = "com.topodroid.TDX.VisualGoldenInstrumentedTest#exportPng_matchesGolden"
    Estimate = 180
    Timeout = 420
    IdleTimeout = 240
  },
  @{
    Name = "Compass export fixture"
    Class = "com.topodroid.TDX.VisualGoldenInstrumentedTest#exportCompass_matchesFixture"
    Estimate = 120
    Timeout = 300
    IdleTimeout = 180
  },
  @{
    Name = "Reference visible PNG export"
    Class = "com.topodroid.TDX.ReferenceImageInstrumentedTest#exportPng_includesVisibleReferenceImage"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Reference hidden PNG export"
    Class = "com.topodroid.TDX.ReferenceImageInstrumentedTest#exportPng_omitsHiddenReferenceImage"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Reference ZIP round-trip"
    Class = "com.topodroid.TDX.ReferenceImageInstrumentedTest#zipRoundTrip_restoresReferenceMetadataAndAsset"
    Estimate = 300
    Timeout = 600
    IdleTimeout = 300
  },
  @{
    Name = "Reference live screen"
    Class = "com.topodroid.TDX.ReferenceImageInstrumentedTest#liveScreen_showsVisibleReferenceImage"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Reference corner handle"
    Class = "com.topodroid.TDX.ReferenceImageInstrumentedTest#cornerHandleDrag_scalesReferenceImage"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Reference protected erase"
    Class = "com.topodroid.TDX.ReferenceImageInstrumentedTest#eraser_keepsProtectedReferenceWhileRemovingLine"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Reference enabled erase"
    Class = "com.topodroid.TDX.ReferenceImageInstrumentedTest#eraser_canDeleteReferenceWhenEnabled"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Model/rendering/style tests"
    Class = "com.topodroid.TDX.LinePatternInstrumentedTest,com.topodroid.TDX.AreaLinePatternInstrumentedTest,com.topodroid.TDX.AreaPatternRendererInstrumentedTest,com.topodroid.TDX.PresetBarInstrumentedTest,com.topodroid.TDX.ToolbarRowsInstrumentedTest,com.topodroid.TDX.SymbolPreviewRendererInstrumentedTest,com.topodroid.prefs.RetiredSymbolSizePreferenceInstrumentedTest,com.topodroid.TDX.LineStyleSnappingInstrumentedTest,com.topodroid.TDX.SketchBrushStyleInstrumentedTest,com.topodroid.TDX.SketchBrushCompatibilityInstrumentedTest,com.topodroid.TDX.SmoothPointScaleInstrumentedTest,com.topodroid.TDX.SymbolOpacityParserInstrumentedTest,com.topodroid.TDX.TopoDroidSketchSymbolSliceInstrumentedTest,com.topodroid.TDX.SketchAffinePointInstrumentedTest,com.topodroid.TDX.BreakdownRockOcclusionInstrumentedTest,com.topodroid.TDX.TitleLegendExportInstrumentedTest,com.topodroid.TDX.SpecialPointInstrumentedTest#titleLegend_roundTripsRequestedShapeAndPreparedExpansion,com.topodroid.TDX.SpecialPointInstrumentedTest#titleLegend_targetedInstallIsMissingOnlyAndNonOverwriting,com.topodroid.TDX.SpecialPointInstrumentedTest#titleLegend_newerStateRemainsVisibleAndBytePreserved"
    Estimate = 45
    Timeout = 300
    IdleTimeout = 150
  },
  @{
    Name = "Sketch style bar tests"
    Class = "com.topodroid.TDX.SketchStyleBarInstrumentedTest"
    Estimate = 240
    Timeout = 600
    IdleTimeout = 300
  },
  @{
    Name = "Sketch brush ZIP round-trip"
    Class = "com.topodroid.TDX.SketchBrushRoundTripInstrumentedTest"
    Estimate = 300
    Timeout = 600
    IdleTimeout = 300
  },
  @{
    Name = "Smooth point scale UI"
    Class = "com.topodroid.TDX.SmoothPointScaleUiInstrumentedTest"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Toolbar selection"
    Class = "com.topodroid.TDX.ToolbarSelectionInstrumentedTest"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Toolbar toggle restyle"
    Class = "com.topodroid.TDX.ToolbarToggleRestyleInstrumentedTest"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
  },
  @{
    Name = "Breakdown rock selected-state affine handles"
    Class = "com.topodroid.TDX.BreakdownRockWindowInteractionInstrumentedTest"
    Estimate = 150
    Timeout = 360
    IdleTimeout = 240
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
    Invoke-NativeChecked -FilePath $Gradle -Arguments @("-g", $GradleUserHome, "--console=plain", ":app:testDebugUnitTest", ":app:assembleDebug", ":app:assembleDebugAndroidTest") -Description "Gradle build and JVM tests" -TimeoutSeconds 1200 -IdleTimeoutSeconds 300
  }

  $AppApk = Resolve-GradleApkOutput -OutputDirectory $AppApkDir -Description "app debug"
  $TestApk = Resolve-GradleApkOutput -OutputDirectory $TestApkDir -Description "androidTest debug"
  Install-TestApksAndPreflight -Adb $Adb -Serial $Serial -AppApk $AppApk -TestApk $TestApk -AppPackage $AppPackage -TestPackage $TestPackage -Runner $Runner
  Invoke-Adb @("shell", "pm", "clear", $AppPackage) | Out-Null
  Invoke-Adb @("shell", "pm", "clear", $TestPackage) | Out-Null
  Grant-Permissions

  $instrumentationOk = $true
  foreach ($test in $FullTests) {
    Reset-TestRuntime
    $ok = Invoke-InstrumentationTimed -Adb $Adb -Serial $Serial `
      -Arguments @("shell", "am", "instrument", "-w", "-r", "-e", "class", $test.Class, $Instrumentation) `
      -Name $test.Name -EstimateSeconds $test.Estimate -TimeoutSeconds $test.Timeout -IdleTimeoutSeconds $test.IdleTimeout -AppPackage $AppPackage -TestPackage $TestPackage -ArtifactsLocal $ArtifactsLocal
    Stop-TestRuntime
    $instrumentationOk = $instrumentationOk -and $ok
  }

  Clear-LocalArtifactDirectory -Path $ArtifactsLocal -Description "full test artifacts"
  Invoke-Adb @("pull", $ArtifactsRemote, $ArtifactsLocal) | Out-Null

  if (-not $instrumentationOk) {
    throw "Instrumentation reported test failures."
  }
} finally {
  Pop-Location
}
