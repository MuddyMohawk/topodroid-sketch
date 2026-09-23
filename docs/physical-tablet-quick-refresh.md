# Physical Tablet Quick Refresh

Use this workflow after any tablet-facing change to build and install the current TopoDroid Sketch debug APK on the field tablet with fresh app-owned data, grant its required permissions, and open it for manual QA. Clearing package data refreshes the packaged symbols without requiring a symbol-version bump during development.

This is a **survey-preserving app reset**, not a fully non-destructive update. It clears preferences, device and calibration data, and locally installed or edited symbols, then seeds the usage profile to **Tester** as the sole private preference before first launch. It must not delete or clear the public `Documents/TopoDroid Sketch/` tree, which contains the survey database and survey files.

## Target

- Device: Samsung SM-T577U
- Android: 13
- ADB serial: `R32X200DN0T`
- App package: `com.topodroid.TDX.sketch`
- Launcher activity: `com.topodroid.TDX.MainWindow`

## Build, Install, Clear App Data, Grant, and Launch

Run from the repository root in PowerShell:

```powershell
& '.\gradlew.bat' -g '.\.gradle-codex-home' :app:assembleDebug
if ($LASTEXITCODE -ne 0) {
  throw 'Failed to build the debug APK.'
}

$adb = 'C:\Users\Jonathan\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$serial = 'R32X200DN0T'
$expectedModel = 'SM-T577U'
$package = 'com.topodroid.TDX.sketch'
$activity = 'com.topodroid.TDX.MainWindow'
$preferencesFile = "${package}_preferences.xml"

$deviceState = & $adb -s $serial get-state 2>$null
if ($LASTEXITCODE -ne 0 -or $deviceState.Trim() -ne 'device') {
  throw "The field tablet $serial is not connected and ready."
}

$model = (& $adb -s $serial shell getprop ro.product.model).Trim()
if ($LASTEXITCODE -ne 0 -or $model -ne $expectedModel) {
  throw "Refusing to clear app data on unexpected device '$model' ($serial)."
}

$apk = Get-ChildItem 'app\build\outputs\apk\debug' -Filter '*.apk' |
  Sort-Object LastWriteTimeUtc -Descending |
  Select-Object -First 1

if ($null -eq $apk) {
  throw 'No debug APK was produced.'
}

& $adb -s $serial install -r -t $apk.FullName
if ($LASTEXITCODE -ne 0) {
  throw "Failed to install $($apk.FullName)."
}

& $adb -s $serial shell pm clear $package
if ($LASTEXITCODE -ne 0) {
  throw "Failed to clear package-owned data for $package."
}

$testerPreferences = @'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="DISTOX_EXTRA_BUTTONS">4</string>
</map>
'@

& $adb -s $serial shell run-as $package mkdir -p shared_prefs
if ($LASTEXITCODE -ne 0) {
  throw 'The installed APK does not permit private preference seeding.'
}

$testerPreferences | & $adb -s $serial shell run-as $package tee "shared_prefs/$preferencesFile" | Out-Null
if ($LASTEXITCODE -ne 0) {
  throw 'Failed to seed the Tester usage profile.'
}

$runtimePermissions = @(
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.CAMERA',
  'android.permission.RECORD_AUDIO',
  'android.permission.BLUETOOTH_SCAN',
  'android.permission.BLUETOOTH_CONNECT'
)
foreach ($permission in $runtimePermissions) {
  & $adb -s $serial shell pm grant $package $permission
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to grant $permission."
  }
}

& $adb -s $serial shell appops set --uid $package MANAGE_EXTERNAL_STORAGE allow
if ($LASTEXITCODE -ne 0) {
  throw 'Failed to allow MANAGE_EXTERNAL_STORAGE.'
}

& $adb -s $serial shell am force-stop $package
& $adb -s $serial shell am start -W -n "$package/$activity"
if ($LASTEXITCODE -ne 0) {
  throw 'Failed to launch TopoDroid Sketch.'
}

$storedPreferences = & $adb -s $serial shell run-as $package cat "shared_prefs/$preferencesFile"
$readPreferencesExitCode = $LASTEXITCODE
$storedPreferencesText = $storedPreferences -join "`n"
if ($readPreferencesExitCode -ne 0 -or
    -not $storedPreferencesText.Contains('<string name="DISTOX_EXTRA_BUTTONS">4</string>')) {
  throw 'TopoDroid Sketch did not retain the Tester usage profile after launch.'
}
```

The APK is discovered from Gradle's output instead of using a hard-coded version number. `install -r` first installs the new APK whether or not the target package is already present. The subsequent `pm clear` removes package-owned data, including existing preferences, private symbol files, and the stored symbol-version marker. Before first launch, the workflow creates a minimal default preference file containing only the Tester usage profile (`DISTOX_EXTRA_BUTTONS=4`). On first launch, TopoDroid Sketch installs the symbols packaged in the new APK and retains that profile; the final check fails the workflow if it does not.

The survey database and survey directories live in the public `Documents/TopoDroid Sketch/` tree, so `pm clear` leaves them in place. Do not add any command that removes content from that public directory.

The androidTest APK is only needed when generating instrumentation artifacts or running device tests. It is not needed for manual field QA.

## Permission Coverage

The workflow grants every runtime permission TopoDroid Sketch uses on this Android 13 tablet:

- Precise and coarse location
- Camera
- Microphone
- Bluetooth scan and connect
- All-files access through the separate `MANAGE_EXTERNAL_STORAGE` app-op

Android grants the normal install-time permissions automatically:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `VIBRATE`
- Legacy `BLUETOOTH` and `BLUETOOTH_ADMIN`

Legacy `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` correctly remain denied on Android 13. `TDandroid.createPermissions` skips them on API 33 and newer, and `Environment.isExternalStorageManager()` uses the allowed `MANAGE_EXTERNAL_STORAGE` app-op instead. `POST_NOTIFICATIONS` and `READ_MEDIA_*` are not declared by the app.

The clean-install audit on 2026-08-04 found all six runtime permissions `granted=true`, all normal permissions granted, and `MANAGE_EXTERNAL_STORAGE` in UID mode `allow`. The app cold-launched directly into `MainWindow` without an Android permission dialog.

## Optional Verification

Confirm the app is installed and foregrounded:

```powershell
& $adb -s $serial shell pm path $package
& $adb -s $serial shell dumpsys activity activities |
  Select-String 'mResumedActivity|topResumedActivity'
```

Confirm runtime grants in Android's package state:

```powershell
$dump = & $adb -s $serial shell dumpsys package $package
foreach ($permission in $runtimePermissions) {
  $dump | Select-String -SimpleMatch "${permission}:"
}
& $adb -s $serial shell appops get --uid $package MANAGE_EXTERNAL_STORAGE
```
