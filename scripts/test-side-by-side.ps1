param(
  [string]$Serial = "emulator-5554",
  [string]$VanillaApk = "",
  [string]$SketchApk = "",
  [switch]$UseInstalledVanilla,
  [switch]$SkipSketchInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "android-test-common.ps1")
$RepoRoot = Split-Path -Parent $ScriptDir
$LocalProperties = Join-Path $RepoRoot "local.properties"
$VanillaPackage = "com.topodroid.TDX"
$SketchPackage = "com.topodroid.TDX.sketch"
$VanillaActivity = "$VanillaPackage/.MainWindow"
$SketchActivity = "$SketchPackage/com.topodroid.TDX.MainWindow"
$VanillaFileProvider = "com.topodroid.fileprovider"
$SketchFileProvider = "com.topodroid.TDX.sketch.fileprovider"

if ([string]::IsNullOrWhiteSpace($VanillaApk)) {
  $VanillaApk = Join-Path $RepoRoot "test-fixtures\apks\TopoDroidX-6.4.53-36.apk"
}
if ([string]::IsNullOrWhiteSpace($SketchApk)) {
  $SketchApk = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
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

function Get-ApkBadging {
  param(
    [string]$Aapt,
    [string]$ApkPath
  )

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
    throw "$Description APK native ABIs [$($apkAbiList -join ', ')] do not match device ABIs [$($deviceAbiList -join ', ')]. Use a universal/x86_64 APK or an emulator image matching the APK."
  }
}

function Install-Apk {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$ApkPath,
    [string]$Description,
    [switch]$AllowDowngrade
  )

  $args = @("install", "-r", "-t")
  if ($AllowDowngrade) {
    $args += "-d"
  }
  $args += $ApkPath
  $output = Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments $args -Description "Install $Description APK"
  $text = $output -join "`n"
  if ($text -notmatch "Success") {
    $output | ForEach-Object { Write-Host $_ }
    throw "Install $Description APK did not report Success."
  }
}

function Assert-ResolveActivity {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$PackageName,
    [string]$ExpectedComponent
  )

  $output = Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @("shell", "cmd", "package", "resolve-activity", "--brief", $PackageName) -Description "Resolve $PackageName"
  $text = ($output -join "`n")
  if ($text -notmatch [regex]::Escape($ExpectedComponent)) {
    $output | ForEach-Object { Write-Host $_ }
    throw "Expected $PackageName to resolve to $ExpectedComponent."
  }
}

function Assert-ProviderAuthority {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$PackageName,
    [string]$Authority
  )

  $output = Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @("shell", "dumpsys", "package", $PackageName) -Description "Check provider authority for $PackageName"
  $text = ($output -join "`n")
  if ($text -notmatch [regex]::Escape("[$Authority]")) {
    throw "Expected $PackageName to register provider authority $Authority."
  }
}

function Start-AppChecked {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$Component
  )

  $output = Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @("shell", "am", "start", "-W", "-n", $Component) -Description "Launch $Component"
  $text = ($output -join "`n")
  if ($text -notmatch "Status: ok") {
    $output | ForEach-Object { Write-Host $_ }
    throw "Launch $Component did not report Status: ok."
  }
  $output | ForEach-Object { Write-Host $_ }
}

function Assert-RemoteDirectory {
  param(
    [string]$Adb,
    [string]$Serial,
    [string]$Path
  )

  $quotedPath = "'" + ($Path -replace "'", "'\''") + "'"
  $output = Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @("shell", "if [ -d $quotedPath ]; then echo exists; else echo missing; fi") -Description "Check $Path"
  $result = (($output -join "`n").Trim())
  if ($result -ne "exists") {
    throw "Expected remote directory to exist: $Path"
  }
}

$SdkPath = Get-SdkPath
$Adb = Join-Path $SdkPath "platform-tools\adb.exe"
$Aapt = Get-AaptPath -SdkPath $SdkPath

Assert-AdbTarget -Adb $Adb -Serial $Serial
$deviceAbisText = ((Invoke-AdbChecked -Adb $Adb -Serial $Serial -Arguments @("shell", "getprop", "ro.product.cpu.abilist") -Description "Get device ABIs") -join "").Trim()
$deviceAbis = @($deviceAbisText -split "," | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
Write-Host "Device ABIs: $($deviceAbis -join ', ')"

if (-not $UseInstalledVanilla) {
  $vanillaBadging = Get-ApkBadging -Aapt $Aapt -ApkPath $VanillaApk
  $vanillaPackageName = Get-BadgingPackageName -Badging $vanillaBadging
  $vanillaVersion = Get-BadgingVersionLabel -Badging $vanillaBadging
  $vanillaAbis = Get-BadgingNativeAbis -Badging $vanillaBadging
  Assert-ApkPackage -Actual $vanillaPackageName -Expected $VanillaPackage -Description "Vanilla"
  Assert-ApkAbiCompatible -ApkAbis $vanillaAbis -DeviceAbis $deviceAbis -Description "Vanilla"
  Write-Host "Installing vanilla $vanillaVersion from $VanillaApk"
  Install-Apk -Adb $Adb -Serial $Serial -ApkPath $VanillaApk -Description "vanilla" -AllowDowngrade
} else {
  Write-Host "Using already-installed vanilla package $VanillaPackage"
}

if (-not $SkipSketchInstall) {
  $sketchBadging = Get-ApkBadging -Aapt $Aapt -ApkPath $SketchApk
  $sketchPackageName = Get-BadgingPackageName -Badging $sketchBadging
  $sketchVersion = Get-BadgingVersionLabel -Badging $sketchBadging
  Assert-ApkPackage -Actual $sketchPackageName -Expected $SketchPackage -Description "Sketch"
  Write-Host "Installing Sketch $sketchVersion from $SketchApk"
  Install-Apk -Adb $Adb -Serial $Serial -ApkPath $SketchApk -Description "Sketch"
} else {
  Write-Host "Using already-installed Sketch package $SketchPackage"
}

Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $VanillaPackage
Assert-InstalledPackage -Adb $Adb -Serial $Serial -PackageName $SketchPackage
Assert-ResolveActivity -Adb $Adb -Serial $Serial -PackageName $VanillaPackage -ExpectedComponent $VanillaActivity
Assert-ResolveActivity -Adb $Adb -Serial $Serial -PackageName $SketchPackage -ExpectedComponent $SketchActivity
Assert-ProviderAuthority -Adb $Adb -Serial $Serial -PackageName $VanillaPackage -Authority $VanillaFileProvider
Assert-ProviderAuthority -Adb $Adb -Serial $Serial -PackageName $SketchPackage -Authority $SketchFileProvider

Write-Host "Launching vanilla..."
Start-AppChecked -Adb $Adb -Serial $Serial -Component $VanillaActivity
Write-Host "Launching Sketch..."
Start-AppChecked -Adb $Adb -Serial $Serial -Component $SketchActivity

Assert-RemoteDirectory -Adb $Adb -Serial $Serial -Path "/sdcard/Documents/TDX"
Assert-RemoteDirectory -Adb $Adb -Serial $Serial -Path "/sdcard/Documents/TopoDroid Sketch"

Write-Host "Side-by-side smoke OK for $VanillaPackage and $SketchPackage."
