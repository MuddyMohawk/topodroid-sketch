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
.\scripts\tablet-refresh.ps1
```

The implementation lives in [scripts/tablet-refresh.ps1](../scripts/tablet-refresh.ps1). It builds using `.gradle-test-home`, the same dependency cache as the test scripts. Set `TOPO_TEST_GRADLE_HOME` to explicitly override that location for both refresh and tests. ADB is resolved from `sdk.dir` in `local.properties`, then `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or the standard local Android SDK installation.

If the current debug APK has already been built successfully, skip only the build step:

```powershell
.\scripts\tablet-refresh.ps1 -SkipBuild
```

The script checks the fixed tablet serial and expected model before installing or clearing package-owned data. It reports build, device detection, installation and launch results, then verifies that the Tester profile was retained. If the tablet is unavailable, the default command still completes the build, then exits nonzero with installation and launch reported as skipped.

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

Run these checks from the repository root. Initialize the connection variables in the current PowerShell session first; the refresh script keeps its variables private. Then confirm the app is installed and foregrounded:

```powershell
. .\scripts\android-test-common.ps1
$adb = Join-Path (Resolve-AndroidSdkPath -RepoRoot (Get-Location).Path) 'platform-tools/adb.exe'
$serial = 'R32X200DN0T'
$package = 'com.topodroid.TDX.sketch'

& $adb -s $serial shell pm path $package
& $adb -s $serial shell dumpsys activity activities |
  Select-String 'mResumedActivity|topResumedActivity'
```

Confirm runtime grants in Android's package state:

```powershell
$runtimePermissions = @(
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.CAMERA',
  'android.permission.RECORD_AUDIO',
  'android.permission.BLUETOOTH_SCAN',
  'android.permission.BLUETOOTH_CONNECT'
)
$dump = & $adb -s $serial shell dumpsys package $package
foreach ($permission in $runtimePermissions) {
  $dump | Select-String -SimpleMatch "${permission}:"
}
& $adb -s $serial shell appops get --uid $package MANAGE_EXTERNAL_STORAGE
```
