param([switch]$SkipBuild)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/android-test-common.ps1"

if (!$SkipBuild) {
  # Use the same dependency cache as the test scripts, even without a tablet.
  Push-Location $repo
  try {
    & "$repo/gradlew.bat" -g (Resolve-GradleUserHome -RepoRoot $repo) :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Failed to build the debug APK.' }
  } finally { Pop-Location }
  Write-Host 'Build: succeeded.'
} else {
  Write-Host 'Build: skipped; using the existing debug APK.'
}

$adb = Join-Path (Resolve-AndroidSdkPath -RepoRoot $repo) 'platform-tools/adb.exe'
$serial = 'R32X200DN0T'
$expectedModel = 'SM-T577U'
$package = 'com.topodroid.TDX.sketch'
$activity = 'com.topodroid.TDX.MainWindow'
$preferencesFile = "${package}_preferences.xml"

try {
  $deviceState = (Invoke-AdbChecked -Adb $adb -Serial $serial -Arguments @('get-state') -Description 'Detect field tablet') -join "`n"
  if ($deviceState.Trim() -ne 'device') { throw "Unexpected device state: $deviceState" }
} catch {
  throw "Tablet: unavailable. Installation and launch skipped; field tablet $serial is not connected and ready. $($_.Exception.Message)"
}
$model = ((Invoke-AdbChecked -Adb $adb -Serial $serial -Arguments @('shell','getprop','ro.product.model') -Description 'Verify field tablet model') -join "`n").Trim()
if ($model -ne $expectedModel) {
  throw "Refusing to clear app data on unexpected device '$model' ($serial)."
}
Write-Host "Tablet: detected $model ($serial)."

$apk = Resolve-GradleApkOutput -OutputDirectory (Join-Path $repo 'app/build/outputs/apk/debug') -Description 'debug'
Install-ApkChecked -Adb $adb -Serial $serial -ApkPath $apk -Description 'Install field tablet debug APK'
Write-Host "Installation: succeeded ($apk)."

# Follow physical-tablet-quick-refresh.md: reset only this package's owned data.
# The public Documents/TopoDroid Sketch/ survey tree must never be removed.
$cleared = (Invoke-AdbChecked -Adb $adb -Serial $serial -Arguments @('shell','pm','clear',$package) -Description 'Clear package-owned data') -join "`n"
if ($cleared.Trim() -ne 'Success') { throw "Package data clear did not report success: $cleared" }

$testerPreferences = @'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="DISTOX_EXTRA_BUTTONS">4</string>
</map>
'@
Invoke-AdbChecked -Adb $adb -Serial $serial -Arguments @('shell','run-as',$package,'mkdir','-p','shared_prefs') -Description 'Prepare Tester preferences' | Out-Null
$testerPreferences | & $adb -s $serial shell run-as $package tee "shared_prefs/$preferencesFile" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Failed to seed the Tester usage profile.' }

$runtimePermissions = @(
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.CAMERA',
  'android.permission.RECORD_AUDIO',
  'android.permission.BLUETOOTH_SCAN',
  'android.permission.BLUETOOTH_CONNECT'
)
foreach ($permission in $runtimePermissions) {
  Invoke-AdbChecked -Adb $adb -Serial $serial -Arguments @('shell','pm','grant',$package,$permission) -Description "Grant $permission" | Out-Null
}
Invoke-AdbChecked -Adb $adb -Serial $serial -Arguments @('shell','appops','set','--uid',$package,'MANAGE_EXTERNAL_STORAGE','allow') -Description 'Grant all-files access' | Out-Null
Invoke-AdbChecked -Adb $adb -Serial $serial -Arguments @('shell','am','force-stop',$package) -Description 'Stop app before launch' | Out-Null
$launch = (Invoke-AdbChecked -Adb $adb -Serial $serial -Arguments @('shell','am','start','-W','-n',"$package/$activity") -Description 'Launch field tablet app') -join "`n"
if ($launch -notmatch '(?m)^Status:\s*ok\s*$') { throw "App launch did not report success: $launch" }
Write-Host 'Launch: succeeded.'

$storedPreferencesText = (Invoke-AdbChecked -Adb $adb -Serial $serial -Arguments @('shell','run-as',$package,'cat',"shared_prefs/$preferencesFile") -Description 'Verify Tester profile after launch') -join "`n"
if (!$storedPreferencesText.Contains('<string name="DISTOX_EXTRA_BUTTONS">4</string>')) {
  throw 'TopoDroid Sketch did not retain the Tester usage profile after launch.'
}
Write-Host 'Refresh: succeeded; Tester profile verified, permissions granted, public survey tree preserved.'
