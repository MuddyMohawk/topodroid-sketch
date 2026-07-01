# Testing

The current tests are intended to catch obviously destructive TopoDroid Sketch releases before they reach users, mostly via emulator and physical device tests. There are no unit/JVM tests yet, just emulator tests and adb-driven tests against my Active Tab 3.

**What This Protects**
- Broken Sketch debug APK or androidTest APK install.
- Package identity mistakes, including side-by-side install conflicts with vanilla TopoDroid.
- Accidental storage-root mixups between `Documents/TopoDroid Sketch/` and vanilla `Documents/TDX/`.
- Broken default Sketch drawing UI: 8 manual toolbar slots, 1 toolbar row, and presets `Fine`, `Smooth`, and `Straight`.
- Sketch line drawing, visual rendering, ZIP export, and Sketch -> Sketch ZIP import regressions.
- Sketch brush-style metadata staying private: `-tdx-brush` survives Sketch save/load and Sketch ZIP round trips, while Therion and cSurvey-style exports strip it.
- Reference image visibility, transform, erasing, PNG export, and ZIP round-trip regressions.
- Real Sketch -> vanilla -> Sketch ZIP compatibility on the physical ARM tablet using vanilla TopoDroid `6.4.53`.

**What This Does Not Cover**
- No automated S Pen, hardware button, Active Key, volume/action-binding, or Bluetooth-device testing. Hardware support is hard.
- No vanilla-specific workflow correctness beyond install/launch/storage-root and the Sketch -> vanilla -> Sketch ZIP compatibility round trip.
- No clean vanilla visual-conversion export for styled Sketch drawings; that is deferred to a future compatibility sprint.
- No completed protection yet for failed DB migrations or the foreign ZIP symbol overwrite policy. Phase 7 intentionally defers symbol backup/staging because current testers are alpha users.

*Nerd Stuff*

**Test Strategy**
- Emulator tests are the Sketch behavior, export, and visual-regression gate. They run against one pinned emulator profile and compare screenshots/exported files against fixtures under `app/src/androidTest/assets/goldens/emulator_2560x1600_320dpi_font1.0/`.
- Physical tablet tests are the real vanilla compatibility gate. The current vanilla APK is ARM-only, so the compatibility round trip runs on the connected Active Tab target rather than the x86_64 emulator.
- `scripts\test-fast.ps1 -Serial emulator-5554 -SkipBuild` last passed on June 3, 2026 using existing APKs. The visual sketch golden test passed in about 69 seconds and the Compass export fixture passed in about 41 seconds, plus install/preflight/artifact time.
- `scripts\test-full.ps1 -Serial emulator-5554 -SkipBuild` last passed on June 3, 2026 using existing APKs. All 25 instrumentation cases passed: 4 visual/export tests, 7 reference-image tests, and 14 line/preset/toolbar model tests. The full run took about 10.5 minutes after preflight.
- `scripts\test-physical-compat.ps1 -Serial R32X200DN0T -SkipBuild` last passed on June 3, 2026 on the physical ARM tablet. The instrumented Sketch -> vanilla -> Sketch round trip passed in about 130 seconds after preflight/install/setup.
- The emulator screenshot issue from June 2026 was a real test setup bug: the visual tests were forcing legacy toolbar mode and two preset slots. Stable test preferences now force the Sketch defaults, and the sketch golden test asserts that UI before drawing.

**Requirements**
- Windows PowerShell (plz no bully)
- Android SDK platform-tools. The scripts use `ANDROID_SDK_ROOT`, `ANDROID_HOME`, `local.properties` `sdk.dir`, or `%LOCALAPPDATA%\Android\Sdk`.
- JDK 21. The scripts prefer `JAVA_HOME`, Android Studio's bundled JBR, or common local JDK 21 install paths.
- A running emulator for emulator tests. Scripts default to `-Serial emulator-5554`; pass `-Serial <id>` to target another emulator.
- Existing debug and androidTest APKs under `app\build\outputs\apk\...` if using `-SkipBuild`; use this only when those APKs were already built from the code under test.
- Emulator profile must match the golden profile exactly: `2560x1600`, `320 dpi`, font scale `1.0`, English locale.
- Physical vanilla compatibility testing requires a connected ARM tablet. The current target is `R32X200DN0T`, model `SM-T577U`, Android 13, ABI `arm64-v8a`.
- The physical runner defaults to the vanilla APK at `test-fixtures\apks\TopoDroidX-6.4.53-36.apk`. The `test-fixtures/` directory is gitignored; keep externally sourced vanilla APKs there with a note recording source URL, versionName, versionCode, package name, ABI list, and download date.

**Script Preflight**

`scripts\android-test-common.ps1` is shared by the emulator scripts. Before running instrumentation it verifies:
- the requested adb serial exists and is online
- emulator size, density, font scale, and locale match the expected profile
- app APK install succeeds
- androidTest APK install succeeds
- app package `com.topodroid.TDX.sketch` is installed
- test package `com.topodroid.TDX.sketch.test` is installed
- instrumentation runner `com.topodroid.TDX.sketch.test/androidx.test.runner.AndroidJUnitRunner` targets the expected app package

The emulator scripts clear app/test package data, grant storage/media permissions, and allow `MANAGE_EXTERNAL_STORAGE` for the app package before instrumentation. Public TopoDroid files under `Documents/TopoDroid Sketch/` can survive `pm clear`, so the test helper also cleans known test surveys and artifacts inside the app.

The physical compatibility runner has its own preflight for tablet safety: it verifies the serial, model, ABI, APK package names, installed package identities, launcher activities, instrumentation target, and distinct FileProvider authorities before it starts the compatibility flow.

**How To Run**
- `scripts\test-fast.ps1`
  - Builds `:app:assembleDebug` and `:app:assembleDebugAndroidTest` unless `-SkipBuild` is supplied.
  - Installs and preflights app/test APKs.
  - Runs two named smoke tests: the sketch screen golden and Compass export fixture.
  - Each test has its own estimate, total timeout, and idle/no-instrumentation-progress timeout.
- `scripts\test-full.ps1`
  - Builds unless `-SkipBuild` is supplied, installs, preflights, clears packages, grants permissions, then runs every existing instrumentation class.
  - UI-heavy tests run one test method at a time with an estimate, a total timeout, and an idle timeout. This adds startup overhead but gives much better failure isolation.
  - The pure/model-style instrumentation tests (`LinePatternInstrumentedTest`, `PresetBarInstrumentedTest`, and `ToolbarRowsInstrumentedTest`) run as one grouped chunk because they do not drive app activity teardown and normally finish in seconds.
- `scripts\refresh-visual-baselines.ps1`
  - Builds unless `-SkipBuild` is supplied, then runs `VisualGoldenInstrumentedTest` in `visual_baseline_mode=record`.
  - Pulls recorded artifacts and copies new baselines into `app/src/androidTest/assets/goldens/emulator_2560x1600_320dpi_font1.0/`.
  - Run only after intentional UI, rendering, or export changes.
- `scripts\start-test-run.ps1 -Suite fast|full|refresh|physical -Serial emulator-5554 [-SkipBuild] [-GradleUserHome <path>]`
  - Starts one of the test scripts in a hidden background PowerShell process.
  - Writes stdout/stderr logs under `tmp-test-runs/`.
  - Prints the PID and log paths so the run can be polled without blocking the shell for the full emulator or physical run.
  - `-SkipBuild` is forwarded to the target script with hashtable splatting so it binds as the real switch.
  - `-GradleUserHome <path>` sets `TOPO_TEST_GRADLE_HOME` for the child process when a Gradle cache needs to be isolated.
  - For `-Suite physical`, the default serial changes to `R32X200DN0T` if no serial is supplied, and physical-only options such as `-VanillaApk`, `-SketchApk`, `-TestApk`, `-AllowDowngrade`, and `-DestructiveClean` are forwarded.
- `scripts\watch-test-run.ps1 -Log <logPath> -PidFile <pidPath>`
  - Follows a background test log and prints a watcher heartbeat every interval.
  - Stops once the recorded PID is no longer active.
- `scripts\test-side-by-side.ps1 [-VanillaApk <path>] [-SketchApk <path>]`
  - Preflights the vanilla APK package name and native ABI before installing.
  - Installs/uses vanilla `com.topodroid.TDX` and Sketch `com.topodroid.TDX.sketch`, verifies both packages resolve launcher activities, verifies distinct FileProvider authorities, launches both apps, and checks the public storage roots.
  - Default vanilla APK path is `test-fixtures\apks\TopoDroidX-6.4.53-36.apk`.
  - `-UseInstalledVanilla -SkipSketchInstall` runs a non-installing smoke against packages already on the emulator.
- `scripts\test-physical-compat.ps1 -Serial R32X200DN0T -VanillaApk "test-fixtures\apks\TopoDroidX-6.4.53-36.apk" -SketchApk "app\build\outputs\apk\debug\app-debug.apk"`
  - Builds Sketch and the androidTest APK unless `-SkipBuild` is supplied.
  - Verifies the serial is online, the model is `SM-T577U`, and the ABI list includes `arm64-v8a` or `armeabi-v7a`.
  - Verifies APK package names: vanilla `com.topodroid.TDX`, Sketch `com.topodroid.TDX.sketch`, and test package `com.topodroid.TDX.sketch.test`.
  - Installs vanilla, Sketch, and the Sketch test APK with `adb install -r -t`. It adds `-d` only when `-AllowDowngrade` is explicitly supplied.
  - Verifies both launcher activities, the instrumentation runner target, and distinct FileProvider authorities.
  - Grants runtime permissions and `MANAGE_EXTERNAL_STORAGE` appops for both packages before first launch.
  - Launches both apps, dismisses standard permission/startup prompts up to a fixed limit, and fails with diagnostics if prompts remain.
  - Runs `PhysicalCompatInstrumentedTest#sketchVanillaZipRoundTrip_onPhysicalTablet`, which creates a uniquely named Sketch survey, exports a ZIP with personal Sketch line symbols, imports it into vanilla, exports it from vanilla, imports the vanilla ZIP back into Sketch, and opens the returned plan sketch.
  - Default mode is non-destructive: it does not `pm clear`, does not wipe existing surveys, and uses a unique `compat_yyyyMMdd_HHmmss` survey name. `-DestructiveClean` is reserved for a dedicated test tablet; it confirms serial/model/packages before clearing package data and deleting known `compat_*` artifacts.

**Progress And Artifacts**

The scripts print start/pass/fail lines with elapsed times for the Gradle build and each instrumentation chunk. Gradle is run with `--console=plain`; if the build is quiet, the script prints heartbeat lines every 30 seconds with elapsed time, time since last output, and remaining time before total timeout. Instrumentation uses `am instrument -r` so Android reports per-test status.

Each instrumentation chunk has an expected runtime estimate; if elapsed time passes the estimate, the script prints a warning. While a chunk is running, the script prints heartbeat lines every 30 seconds with elapsed time, time since last instrumentation output, and remaining time before total timeout. On total timeout, idle timeout, or ANR-like instrumentation output, the shared script attempts to dump focused-window/process state and pull a UI hierarchy to `tmp-test-artifacts/instrumentation-timeout-window.xml`.

The physical runner prints `START`, `PASS`, and `FAIL` lines for every major step with elapsed time, package, and survey context. The round-trip instrumentation phase has a runtime estimate, a total timeout, an idle/no-output timeout, and 30-second heartbeat lines. On failure it pulls a screenshot, UI hierarchy, focused-window/activity state, package dumps, permission/appops state, logcat tail, and any ZIP/test artifacts it can find.

For CI, agent shells, or any other environment where a direct shell command buffers output until completion, use `scripts\start-test-run.ps1` and then either run the printed `scripts\watch-test-run.ps1` command or poll the stdout log every 30 seconds. Do not start another long emulator or physical run without either visible terminal output or a pollable log. A typical monitored smoke run is:

`scripts\start-test-run.ps1 -Suite fast -Serial emulator-5554 -SkipBuild`

Output locations:
- `tmp-test-artifacts/<testCaseName>/`: screenshots, exported files, ZIPs, and on visual failure `expected-*` / `diff-*` images.
- `tmp-recorded-latest/recorded-goldens/...`: temporary output from `refresh-visual-baselines.ps1` before it copies fixtures into the tracked golden directory.
- `tmp-physical-compat-runs/<timestamp>/`: physical tablet logs, diagnostics, pulled ZIPs, screenshots, UI XML, and Sketch instrumentation artifacts.

Both `tmp-*` directories are gitignored and regenerated by the scripts.

If Windows has a screenshot artifact locked, the fast/full scripts warn after three cleanup attempts instead of failing a passed test run. `refresh-visual-baselines.ps1` treats cleanup failure as fatal because stale baseline artifacts could be copied into tracked fixtures.

**Emulator Test Cases**

`VisualGoldenInstrumentedTest`
- `createSurvey_addShots_createSketch_drawPresetsAndSketchLines_matchesGolden`
  - Creates a canonical survey.
  - Adds canonical leg/splay shots.
  - Opens a plan sketch.
  - Asserts the Sketch drawing UI shows the expected default toolbar/preset layout: 8 manual toolbar slots and presets `Fine`, `Smooth`, `Straight`.
  - Draws the canonical sketch using Sketch line symbols and preset-driven line behavior.
  - Captures `sketch_screen.png` and compares it to the emulator golden.
- `exportZip_includesSketchLineSymbols_and_importRoundTripsThroughPicker`
  - Creates a canonical survey and sketch.
  - Draws the canonical sketch.
  - Exports a ZIP with symbol export enabled.
  - Verifies the ZIP contains Sketch line symbols in `lines.zip`.
  - Deletes the survey.
  - Re-imports the ZIP through Android DocumentsUI.
  - Reopens the imported plot and compares `zip_roundtrip_screen.png` to the golden.
  - This is a Sketch -> Sketch round trip, not a vanilla compatibility test.
- `exportPng_matchesGolden`
  - Creates/draws the canonical sketch.
  - Exports a PNG with a deterministic filename.
  - Compares the exported PNG against `export_png.png`.
- `exportCompass_matchesFixture`
  - Creates a canonical survey with shots.
  - Exports Compass `.dat`.
  - Normalizes the dynamic `SURVEY DATE:` line.
  - Compares the text export against `export_compass.dat`.

`ReferenceImageInstrumentedTest`
- `exportPng_includesVisibleReferenceImage`
  - Creates a sketch.
  - Inserts a generated visible reference image from Downloads.
  - Applies scale/rotation/alpha/position changes.
  - Exports PNG.
  - Asserts the exported PNG contains the fixture colors.
- `exportPng_omitsHiddenReferenceImage`
  - Inserts a reference image and marks it hidden.
  - Exports PNG.
  - Asserts the exported PNG does not contain the fixture colors.
- `zipRoundTrip_restoresReferenceMetadataAndAsset`
  - Inserts a reference JPEG.
  - Applies transform and visibility metadata.
  - Exports ZIP.
  - Deletes the survey.
  - Imports the ZIP back through DocumentsUI.
  - Asserts reference metadata and asset survive the Sketch -> Sketch ZIP round trip.
- `liveScreen_showsVisibleReferenceImage`
  - Inserts a visible reference image.
  - Captures the live drawing screen.
  - Asserts the fixture colors are visible on screen.
- `cornerHandleDrag_scalesReferenceImage`
  - Inserts a reference image.
  - Drags the reference corner handle.
  - Asserts scene width/height increase and orientation does not unexpectedly change.
- `eraser_keepsProtectedReferenceWhileRemovingLine`
  - Disables reference-image erasing.
  - Inserts a reference image and draws a normal sketch line across it.
  - Erases at the reference center.
  - Asserts the reference remains and the sketch line is removed.
- `eraser_canDeleteReferenceWhenEnabled`
  - Enables reference-image erasing.
  - Inserts a reference image.
  - Erases at the reference center.
  - Asserts the reference is deleted.
  - Current risk: in full grouped runs, the suite has produced an Android input-dispatch ANR around this part of the reference-image group. Evidence points to activity teardown / focus pressure between tests rather than a confirmed eraser logic failure, but this is not fully resolved.

`LinePatternInstrumentedTest`
- `fixedLinePatternDensity_keepsDashRepeatCountStableAcrossZoom`
  - Renders fixed-density dashed line behavior at multiple zoom levels.
  - Asserts dash repeat count remains stable.
- `legacyLinePatternDensity_changesDashRepeatCountAcrossZoom`
  - Renders legacy density behavior at multiple zoom levels.
  - Asserts repeat count changes with zoom, proving the test can distinguish old/new behavior.
- `sketchCarrierEffect_drawsContinuousCurvedCarrier`
  - Renders a Sketch carrier effect on a curved line.
  - Asserts the effect draws as a continuous curved carrier.
- `sketchDashedEffect_stampsOncePerDashOnSegment`
  - Renders a dashed Sketch effect on a segment.
  - Asserts stamp placement occurs once per dash.
- `sketchDashedEffect_anchorsStampToCurvedLine`
  - Renders dashed Sketch stamps along a curved line.
  - Asserts stamps anchor to the curve instead of drifting.

`PresetBarInstrumentedTest`
- `freshPresetPrefs_defaultToThreeSlotsAndStraightP3`
  - Starts from clean preset prefs.
  - Asserts defaults are 3 slots: `Fine`, `Smooth`, `Straight`.
  - Asserts preset 3 has the expected settings title and can be selected.
- `renamedPreset_updatesDisplayedNameAndSettingsTitle`
  - Renames a preset.
  - Asserts display name and settings title update.
- `loweringSlotCount_hidesButPreservesPresetDefinitions`
  - Lowers the visible preset slot count.
  - Asserts hidden preset definitions are preserved and selection behavior remains sane.

`ToolbarRowsInstrumentedTest`
- `freshManualToolbar_keeps029DefaultsInRowZero`
  - Starts from clean toolbar prefs.
  - Asserts the manual toolbar keeps the expected default row-0 slot layout.
- `manualRows_copyRowZeroDefaultsAndRemainAvailableWhenHidden`
  - Exercises multi-row manual toolbar prefs.
  - Asserts hidden rows preserve copied defaults and remain available.
- `rowLock_controlsDisplayedTypeOnlyForThatRow`
  - Locks a toolbar row to a displayed item type.
  - Asserts the lock affects only that row.
- `pickerLockCallbacks_updateLockAndTabImmediately`
  - Exercises picker lock callbacks.
  - Asserts lock and tab state update immediately.
- `replacingDuplicateSymbol_swapsOnlyWithinSameRowAndType`
  - Replaces a duplicate toolbar symbol.
  - Asserts replacement is constrained to the same row and item type.
- `legacyRecentModes_keepSixSlotSingleRowBehavior`
  - Switches to legacy recent-toolbar modes.
  - Asserts the old six-slot single-row behavior is preserved.

**Vanilla <-> Sketch Compatibility**

There are two automation levels:
- `scripts\test-side-by-side.ps1` performs package coexistence smoke testing. It is useful on emulator or tablet when you only need to prove install/launch/storage-root behavior.
- `scripts\test-physical-compat.ps1` is the real vanilla ZIP compatibility gate. It targets the connected ARM tablet and drives both packages through a Sketch -> vanilla -> Sketch ZIP round trip.

Package identities:
- vanilla TopoDroid package: `com.topodroid.TDX`
- Sketch package: `com.topodroid.TDX.sketch`

Current emulator findings from June 3, 2026:
- `TopoDroidX-6.4.53-36.apk` has package `com.topodroid.TDX`, version `6.4.53` / `604053`, and native ABIs `arm64-v8a`, `armeabi-v7a`.
- The current emulator is `x86_64`, so the dropped 6.4.53 APK cannot install there. This is an emulator/fixture mismatch, not evidence that the APK is wrong for the ARM tablet target. The script fails fast with an ABI mismatch before attempting install.
- The already-installed vanilla `6.4.27` / `604027` launches side by side with Sketch `0.30.1` / `730010`.
- The smoke verified both launcher activities, distinct FileProvider authorities (`com.topodroid.fileprovider` and `com.topodroid.TDX.sketch.fileprovider`), and both public roots: `Documents/TDX/` and `Documents/TopoDroid Sketch/`.

Physical compatibility test case:
- `PhysicalCompatInstrumentedTest#sketchVanillaZipRoundTrip_onPhysicalTablet`
  - Uses a unique survey name such as `compat_20260603_123456`.
  - Launches Sketch on the physical tablet without enforcing the emulator golden profile.
  - Creates the canonical survey with three shots: `1 -> 2`, `2 -> 3`, and a splay `2 -> 4`.
  - Opens plan sketch `1`.
  - Draws styled Sketch content: Thin/Standard/Thick style strokes on the user line plus an ordinary styled point.
  - Exports a Sketch ZIP with personal symbols enabled.
  - Verifies the ZIP contains `lines.zip` and the expected Sketch line symbol entries.
  - Copies the ZIP to Downloads and starts vanilla through the exported ZIP import intent.
  - Waits for the imported survey to appear on vanilla's main list.
  - Opens the imported survey in vanilla, exports it as ZIP, and waits for `Documents/TDX/TopoDroid/zip/<survey>.zip`.
  - Deletes only the generated Sketch survey/artifacts for that unique run name so the round-trip import is not blocked by a duplicate survey row.
  - Starts Sketch through the ZIP import intent with the vanilla-exported ZIP.
  - Waits for the survey to reappear in Sketch, opens the survey, opens plan sketch `1`, and captures `roundtrip-opened-in-sketch.png`.
  - Does not assert style preservation through vanilla; successful open is the graceful-degradation contract.
