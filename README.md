# TopoDroid Sketch

This is a fork of the main TopoDroid repository. It is focused on adding features to better support a paper-style free hand sketching style with a .png export instead of using symbols and Therion.

This was essentially entirely vibe-coded with Codex and Claude. From my estimation, it would have taken 6-8 years with the current weekly time investment to learn Android app development and make this fork otherwise. 

Testing and development was done with a Cavway X1 and an Active Tab 3.

This was written in English; other translations are likely not working

#### TODO
- Add safety warnings when importing vanilla TopoDroid stuff
- Option for rearranging render order (eg, survey station designations on top)
- Tweak the Sketch icon a bit, it's too zoomed in
- Change the update check and versioning to track something besides `https://raw.githubusercontent.com/marcocorvi/speleoapks/main/tdversion.txt`

#### TODO bugs:
- Export to PNG, the north arrow and the scale bar are weird and can overlay the sketch 
  - station designation font size does not affect export size 
  - actually all the sketch settings might not be respected (eg leg lines size)?
- The "location is needed" pop-ups upon install even if permission was granted during installation. Vanilla bug.
- The `undo` action seems weird over many actions. Potentially vanilla bug
- Taking screenshot with volume-up doesn't work. Vanilla bug.
- Seems like the scaling of dashed line is odd, like it has a minimum size? Vanilla bug.
- Had a bug where I placed a section cross-section but then couldn't select it via the edit tool. Wasn't replicable but noted here.

#### Future Possible Features / Brainstorming
- Add TopoDroid Sketch-specific settings to the installation splash window
- Add an option to disable all drawing except that from the stylus pen
  - Maybe still detect fingers for panning/zooming, drawing only from stylus?
- Performance check for large sketches
- Measure distance between two points on the 2D sketch screen
- Text objects for ceiling height circles
- Fix the text box/text input scaling. Enhance with fonts (Architects Daughter)?
- Sketch line collision to prevent sketching through another line
- Change the user-lines to wall-lines?
- copy/create new symbols
- Version control? - file/edit/shot/survey history. Scroll back and pick versions. Check the existing backup feature?
- Better PDF export
- At-station cross-section viewport support
- Use the new color picker to support more color settings everywhere
- Set usage profile to expert by default
- Per-screen action bindings (eg double tab the Active Key from the survey page to enter the last sketch. Double tap it in the sketch page works as the back action)
- Press-and-hold the stylus button while hovering to erase
- Always more actions. Some thoughts:
  - Take a shot
  - Download data via bluetooth from device (multi-device?)
  - switch between profile and plan (and cross-sections?)
  - press-and-hold to pan like Krita does
  - 2,3,4... finger tap and finger drag actions
  - switch between toolsets
  - switch between tools/brushes
- Pie in the sky: Advanced GPS/gnss tools. RTK when.
- Inventory/Rope Audit/Vandalism tracking tools
- Investigate and enhance the point symbols. They could be good. They need to be aligned with the NSS/UIS conventional symbols
  - Better, finer, sand symbol
  - Mud symbol in alignment with the NSS symbol
  - Bedrock symbol
  - cobbles
  - Randomized rock symbol?
  - better size range for symbols
- In-app symbol editor
- Sketch layers
- Opacity? that would be useful for doing fade-in-fade-out overlapping layers
- Opacity in symbol editor
- Display Cavway line features on the sketch (is this a thing already?)
- Bulk reassign splays? (I've been told this is a skill issue)
- Setting to automatically detect if there hasn't been any successful wifi or data connections in the last N minutes, and then toggle airplane mode to save battery?
- Legend, sketch info, etc viewport style box to use with the png export. Jealous of that Therion fanciness
- More naming options for png export? (create a name based off of the selected export options? eg append `s` for splay, `n` for north arrow)
- Option to toggle the display of backsights
- Option to automatically use the Cavway backsight mark to actually make backsights into backsights
  - Better alerting and information for bad backsights?
  - automatically label them as going from the `to` station to the `from` station (eg, from A1->A0)
  - Remotely clear unsent shots on the Cavway?
- Tweak bad backsight orange line to be a little more subtle
- Sound alerts/noises/haptics for specific events? (data successfully download, shots are good, shots are bad, pairing, multi-device noises?)
- Expand the preset functionality into more of saved-brushes functionality, adding the ability to save line/point/area brush types in addition to the current settings.
- Fdroid distribution and updating

### TopoDroid Sketch v0.31.4 Changelog:

**Architecture**
- Changed things so I could work in Android Studio. This was probably unnecessary. I'm a noob. Also to run on Windows, I accidentally wiped my linux drive.
- Migrated naming, app manifest, strings etc from TopoDroid to TopoDroid Sketch. The apps can be installed side-by-side. The underlying java package/class names and such are unchanged.
  - _The default storage location is now `Documents/TopoDroid Sketch/` instead of `Documents/TDX`_
  - The versioning was changed from vanilla TopoDroid. See the section `Versioning` for the details. Not well tested. 

**Lines**

- Added a setting, "Fixed line pattern density", which disables the auto-scaling of lines (most notable with dashed lines, eg pits and ceiling ledges)
- Added a straight line option in addition to the existing Fine, Normal, Coarse, Bezier, and Simplified lines styles
- Changed the vanilla morphing of line-symbols from warp-to-fit to a rigid-stamping that prevents the ugly morphing of things like ceiling ledges
- Extended the line symbols with additional terms; sketch_effect, carriers, rigid stamps, dash-on segments, and advance. These are used to make prettier curved brush lines. This bumped the TDVersion.SYMBOL_VERSION from 44 to 45. However, this is still compatible with vanilla TopoDroid. Probably.
- Changed most of the lines from the default speleo symbol pack to be white

*Sketch Lines*
- Added three new "sketch lines", which are programmatically generated custom line symbols based on the existing `user` lines 
- The sketch lines has three variants: Thin, Standard, and Thick (user-fine, user-standard, user-thick).
- Added per-variant width settings for new sketching lines, with defaults of 1.0x, 2.0x, and 5.0x.
- Added color options to the new sketch lines
- Made the three sketch lines default in the recent-line toolbar
- Compatibility intent: when exported and imported into vanilla TopoDroid, the new sketch lines should fall back into the `user` line type. It'll be ugly, but still compatible. The current emulator has only smoke-tested side-by-side install/launch with an already-installed vanilla 6.4.27; real vanilla ZIP import still needs a compatible vanilla APK/device.
- If exported with the personal line box checked, it can be imported into another copy of TopoDroid Sketch and the lines are preserved

**Cross-Section Viewports**
- Added the ability to place cross-sections directly on the plan sketch in a viewport style experience. These can be moved around and edited by selecting them in edit mode (may require TopoDroid to be in Expert mode in the main settings)
  - To do this, use the `section` line tool and draw across the passage like normal TopoDroid. Then select the "place on plan" button in the resulting pop-up window. Tap where you want to place your cross-section. Its position can be further adjusted in edit mode.  
- Display of sketch references for cross-sections (legs, splays, etc) can be toggled by selecting the cross-section in edit mode
- Not currently supported for station cross-sections

**Reference Image**
- Added the ability to place a reference image on a sketch (eg, a photo for a cross-section). The image can be scaled, moved, rotated, and its opacity and visibility can be changed. The reference image is included with the PNG export if it's visible

**Line Presets**
- Added drawing presets to the sketch screen, which appear as "P1" and "P2". These are intended to allow a sketcher to switch between drawing thin, detailed lines, and smooth, straight lines
- Fine's defaults are a line style of `fine` and a line point spacing of 1
- Smooth's defaults are a line style of `bezier` and a line point spacing of 10
- Straight's defaults are a line style of `straight` and a line point spacing of 5
- Added a presets menu to the sketch settings screen to allow customization of each preset. Users can also add up to 8 preset slots.

**Toolbar Overhaul**
- Added a setting for an overhauled toolbar/recents bar. This is on by default under Settings -> Secondary sketch settings -> Toolbar mode
- The new toolbar replaces the old recents-style functionality with manually selected slots, which are saved on a per-survey basis
  - To change the tool in a slot, select the slot, tap the >> button the far right, and select the new tool/brush you want from the palette
- Added an option for multiple toolbar rows. These rows can be locked to a specific toolset (eg line, point, area

**S Pen, Active Key, and Volume Button Support**
- Added support for the S Pen button for single click, double click, and long click inputs
    - *Note that this is only tested with the IP68 S pen that has no bluetooth and no battery. It may not work with other pens. Additionally, the pen must be held close to the screen in order for the button to work*
- Added the following actions:
  - Undo: Perform the undo action in the sketch screen
  - Redo: Performs the redo action in the sketch screen
  - Toggle palette: Toggles the recently used bottom palette between LINE, POINT, and AREA
- Toggle preset: Toggles the active line drawing preset between Preset 1 and Preset 2
  - Back: Goes back a screen (eg exit sketch page to shot list, or goes back one screen in the settings)
  - Toggle erase/sketch: Toggles between the erase sketch mode and the drawing sketch mode
- Added the ability to bind actions to S Pen button inputs in the *TopoDroid main settings -> Devices -> Action Key Bindings* menu
- Default S Pen key bindings are `undo` for single click, `back` on double click, and `toggle preset` on long-click
- Added the ability to bind actions to the Samsung Active key in the *TopoDroid main settings -> Devices -> Action Key Bindings* menu
- Default Active Key bindings are `toggle erase/sketch` on single press, `back` on double press, and `toggle preset` on long press
- Added the ability to bind actions to the Volume Up and Volume Down keys (single / long / double press each) in the same *Action Key Bindings* menu. Default binding for all six is `none`. If an action is bound, it overwrites the ability to change volume with that key while the app is open. This also overrides the busted volume-up screenshot action present in vanilla topodroid

**PNG Sketch Export**
- Added a PNG export option for sketches
- Stations, legs, splays, grid, scale bar (kinda meh), north direction, and background transparency are all toggleable options
- The output can be scaled from 0.05 to 4.0. The default of 1.00 is great for handing to a cartographer, but the files it produces are too large to really view on the tablet. I recommend 0.25 scale for that.
- The default filename is `<survey_name>_<sketch_name>_<sketch_type (eg plan, profile)>_YYYY-MM-DD.png`. Example: `F-Survey_toob_plan_2026-04-15.png`.

**Testing**
- Added tests to cover import/export, vanilla compatibility, some regression testing. See the testing section for details.

**misc UI**
- Added a new, more capable color picker widget
- Added sketch grid appearance settings for both grid width and grid color
- Added the new 1 foot sketch-grid unit alongside the existing 2 feet, yard, meter, and 10 cm options
- Added an option (on by default) for overlapping areas (eg water) to darken instead of lighten. This is mostly for drawing deeper water pools
- Made the default icon size large
- Added an editor in the palette selection window to allow for in-app editing of most symbols. Built-in symbols are not currently supported.

### Testing

The current tests are intended to catch obviously destructive TopoDroid Sketch releases before they reach users, mostly via emulator and physical device tests. There are no unit/JVM tests yet, just emulator tests and adb-driven tests against my Active Tab 3.

**What This Protects**
- Broken Sketch debug APK or androidTest APK install.
- Package identity mistakes, including side-by-side install conflicts with vanilla TopoDroid.
- Accidental storage-root mixups between `Documents/TopoDroid Sketch/` and vanilla `Documents/TDX/`.
- Broken default Sketch drawing UI: 8 manual toolbar slots, 1 toolbar row, and presets `Fine`, `Smooth`, and `Straight`.
- Sketch line drawing, visual rendering, ZIP export, and Sketch -> Sketch ZIP import regressions.
- Reference image visibility, transform, erasing, PNG export, and ZIP round-trip regressions.
- Real Sketch -> vanilla -> Sketch ZIP compatibility on the physical ARM tablet using vanilla TopoDroid `6.4.53`.

**What This Does Not Cover**
- No automated S Pen, hardware button, Active Key, volume/action-binding, or Bluetooth-device testing. Hardware support is hard.
- No vanilla-specific workflow correctness beyond install/launch/storage-root and the Sketch -> vanilla -> Sketch ZIP compatibility round trip.
- No completed protection yet for failed DB migrations or the foreign ZIP symbol overwrite policy. Haven't made a decision for these yet.

*Nerd Stuff*

AI wrote the tests, AI wrote the following documentation for those tests:

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
- The physical runner defaults to the vanilla APK at `Files for Codex\TopoDroidX-6.4.53-36.apk`. That path is a local drop zone, not a recommended long-term fixture location. Long term, keep externally sourced vanilla APKs outside tracked source, for example under a gitignored `test-fixtures/apks/` directory with a checked-in checksum/README that records source URL, versionName, versionCode, package name, ABI list, and download date.

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
  - Default vanilla APK path is `Files for Codex\TopoDroidX-6.4.53-36.apk`.
  - `-UseInstalledVanilla -SkipSketchInstall` runs a non-installing smoke against packages already on the emulator.
- `scripts\test-physical-compat.ps1 -Serial R32X200DN0T -VanillaApk "Files for Codex\TopoDroidX-6.4.53-36.apk" -SketchApk "app\build\outputs\apk\debug\app-debug.apk"`
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

For Codex/Claude or any other environment where a direct shell command buffers output until completion, use `scripts\start-test-run.ps1` and then either run the printed `scripts\watch-test-run.ps1` command or poll the stdout log every 30 seconds. Do not start another long emulator or physical run without either visible terminal output or a pollable log. A typical monitored smoke run is:

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
  - Draws six curved Sketch strokes covering presets `Fine` and `Smooth` across `user-fine`, `user-standard`, and `user-thick`.
  - Exports a Sketch ZIP with personal symbols enabled.
  - Verifies the ZIP contains `lines.zip` and the expected Sketch line symbol entries.
  - Copies the ZIP to Downloads and starts vanilla through the exported ZIP import intent.
  - Waits for the imported survey to appear on vanilla's main list.
  - Opens the imported survey in vanilla, exports it as ZIP, and waits for `Documents/TDX/TopoDroid/zip/<survey>.zip`.
  - Deletes only the generated Sketch survey/artifacts for that unique run name so the round-trip import is not blocked by a duplicate survey row.
  - Starts Sketch through the ZIP import intent with the vanilla-exported ZIP.
  - Waits for the survey to reappear in Sketch, opens the survey, opens plan sketch `1`, and captures `roundtrip-opened-in-sketch.png`.

### Versioning
- TopoDroid Sketch uses SemVer for the app version: MAJOR.MINOR.PATCH, currently 0.22.1.
- Android versionName is the human app version shown by Android and in app UI.
- Android versionCode is a monotonically increasing integer used by Android to allow upgrades. For Sketch it is derived from the SemVer parts with a Sketch epoch, currently 722010
- Git SHA is not included in versionName; it is only added to QA APK filenames for traceability.
- QA debug APKs can be built with packageQaDebug, producing names like TopoDroid-Sketch-v0.22.1-722010-<sha>-debug.apk.
- TopoDroid file compatibility is versioned separately from the Android app version.
- TOPODROID_COMPAT_VERSION_NAME / TOPODROID_COMPAT_VERSION_CODE describe the vanilla TopoDroid file/protocol baseline Sketch currently exports as compatible with, currently 6.4.27 / 604027.
- ZIP manifests, TDR/sketch streams, and parser-sensitive import/export paths use the compatibility version, not Sketch’s Android versionCode.
- Export provenance may say TopoDroid Sketch v X.XX.X, but compatibility gates should still use 604027 until the file format baseline changes.
- Dev note: Only bump the compatibility version after merging/testing against a newer vanilla TopoDroid baseline.
- Dev mote: Do not bump database, symbol, or compatibility versions merely for normal Sketch app releases.
- Dev note: Until 1.0, group all changelog items together, don't split out by 0.23, 0.24, etc

# topodroid

[![Join the chat at https://gitter.im/marcocorvi/topodroid](https://badges.gitter.im/marcocorvi/topodroid.svg)](https://gitter.im/marcocorvi/topodroid?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
TopoDroid code base

This is the TopoDroid app (com.topodroid.TDX) repository.

TopoDroid is a productivity Android app for cave surveying.
It is specially designed to do cave surveying with the DistoX (v. 1, 2, and BLE), the Cavway X1, the BRIC (4 and 5), the SAP (5 and 6) and DiscoX,
although it can be profitably used even without it.

Visit the website https://sites.google.com/site/speleoapps for more informations about TopoDroid, and in particular for the old version changes doc.

The most recent TopoDroid apks, as well as recent version changes info, are on http://marcocorvi.altervista.org/caving/speleoapps/speleoapks/TopoDroidApks.html 

All the code is provided under GNU General Public Licence v. 3
