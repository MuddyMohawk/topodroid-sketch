# TopoDroid Sketch

This is a fork of the main TopoDroid repository. It is focused on adding features to better support a paper-style free hand sketching style instead of using symbols.
The intended workflow is to create beautiful sketches on the sketching screen and export those as images for the cartographer.

This was essentially entirely vibe-coded with Codex.

Testing and development was done with a Cavway X1 and an Active Tab 3.

This was written in English; other translations are likely not working

#### TODO
- Add safety warnings when importing vanilla TopoDroid stuff
- Switch the defaults for preset 2 to a line-point spacing of 15 instead of 20
- Add a 4th sketch line. I love sketch lines.
- Performance check for large sketches
- Option for rearranging render order (eg, survey station designations on top)
- Extend the visual regression suite to cover S Pen button, Active Key, and action-binding flows (undo/redo, palette toggle, preset toggle, back, erase/sketch toggle). Current coverage only exercises taps on the drawing surface and toolbars.
- Tweak the Sketch icon a bit, it's too zoomed in
- Add an option to disable all drawing except that from the stylus pen

#### TODO bugs:
- Post-install splash screen needs some proofreading
- There's some weird differences in the back key via S Pen stylus vs Active Key
- Exporting to PNG with Grid lines on and transparent background off results in a pure black background. The grid lines aren't being preserved.
- Export to PNG, the north arrow and the scale bar are weird and can overlay the sketch 
  - station designation font size does not affect export size 
  - actually all the sketch settings might be respected (eg leg line size)?
- Taking screenshot with volume-up doesn't work (vanilla bug)
- Emulator test suite should probably test if the emulator is actually running lol
- On the startup screens, the L icon size isn't displayed as default even though it is
- The `undo` action seems weird over many actions. Potentially vanilla bug

#### Future Possible Features / Brainstorming
- Change the recent items bar to be a fixed selection. Make it two rows and maybe have a setting for how many items are in it
- Measure distance between two points on the 2D sketch screen
- Ceiling height text objects
- Fix the text box/text input scaling. Fonts / Architects Daughter?
- Sketch line collision to prevent sketching through another line
- Change the user-lines to wall-lines?
- in-app generic symbol editor?
  - this could be quite good for things like colors
- Add a straight line option to compliment bezier
- Version control - file/edit/shot/survey history. Scroll back and pick versions. Check the existing backup feature?
- Better PDF export
- At-station cross-section viewport support
- Use the new color picker to support more color settings everywhere
- Press-and-hold the stylus button while hovering to erase
- Option for double row/larger recents palette
- Side-by-side installation with vanilla TopoDroid
- Set usage profile to expert by default
- Per-screen action bindings (eg double tab the Active Key from the survey page to enter the last sketch. Double tap it in the sketch page works as the back action)
- Always more actions. Some thoughts:
  - Take a shot
  - Download data via bluetooth from device (multi-device?)
  - switch between profile and plan (and cross-sections?)
  - press-and-hold to pan like Krita does
  - 2,3,4... finger tap and finger drag actions
- Pie in the sky: Advanced GPS/gnss tools. RTK when.
- Inventory/Rope Audit/Vandalism tracking tools
- Investigate and enhance the point symbols. They could be good. They need to be aligned with the NSS conventional symbols
  - Better, finer, sand symbol
  - Mud symbol in alignment with the NSS symbol
  - Bedrock symbol
  - cobbles
  - Randomized rock symbol?
- Sketch layers
- Opacity? that would be useful for doing fade-in-fade-out overlapping layers
- Display Cavway line features on the sketch (is this a thing already?)
- Sort shots by their ordering, not their shot ID
- Bulk reassign splays?
- Setting to automatically detect if there hasn't been any successful wifi or data connections in the last N minutes, and then toggle airplane mode to save battery
- Long-press erase mode for the S pen. Attempted once, was bugged and didn't work.
- Legend, sketch info, etc viewport style box to use with the png export. Jealous of that Therion fanciness
- More naming options for png export? (create a name based off of the selected export options? eg append `s` for splay, `n` for north arrow)
- Option to toggle the display of backsights
- Option to automatically use the Cavway backsight mark to actually make backsights into backsights
  - Better alerting and information for bad backsights?
  - automatically label them as going from the `to` station to the `from` station (eg, from A1->A0)
- Tweak bad backsight orange line to be a little more subtle
- Sound alerts/noises/haptics for specific events? (data successfully download, shots are good, shots are bad, pairing, multi-device noises?)
- Expand the preset functionality into more of saved-brushes functionality, adding the ability to save line/point/area brush types in addition to the current settings.

### TopoDroid Sketch v0.24.1 Changelog:

**Architecture**
- Changed things so I could work in Android Studio. This was probably unnecessary. I'm a noob. Also to run on Windows, I accidentally wiped my linux drive.
- Migrated naming, app manifest, strings etc from TopoDroid to TopoDroid Sketch. The apps can be installed side-by-side. The underlying java package/class names and such are unchanged.
  - _The default storage location is now `Documents/TopoDroid Sketch/` instead of `Documents/TDX`_
  - The versioning was changed from vanilla TopoDroid. See the section `Versioning` for the details. Not well tested. 

**Sketch Lines**
- Added three new "sketch lines", which are programmatically generated custom line symbols based on the existing `user` lines 
- The sketch lines has three variants: Thin, Standard, and Thick (user-fine, user-standard, user-thick).
- Added per-variant width settings for new sketching lines, with defaults of 1.0x, 2.0x, and 5.0x.
- Added color options to the new sketch lines
- Made the three sketch lines default in the recent-line toolbar
- If exported and imported into the `TopoDroidX-6.4.25-36`, the new sketch lines fall back into the `user` line type. It'll be ugly, but still compatible.
- If exported with the personal line box checked, it can be imported into another copy of TopoDroid Sketch and the lines are preserved

**Lines**
- Added a setting, "Fixed line pattern density", which disables the auto-scaling of lines (most notable with dashed lines, eg pits and ceiling ledges)

**Cross-Section Viewports**
- Added the ability to place cross-sections directly on the plan sketch in a viewport style experience. These can be moved around and edited by selecting them in edit mode (may require TopoDroid to be in Expert mode in the main settings)
  - To do this, use the `section` line tool and draw across the passage like normal TopoDroid. Then select the "place on plan" button in the resulting pop-up window. Tap where you want to place your cross-section. Its position can be further adjusted in edit mode.  
- Display of sketch references for cross-sections (legs, splays, etc) can be toggled by selecting the cross-section in edit mode
- Not currently supported for station cross-sections

**Reference Image**
- Added the ability to place a reference image on a sketch (eg, a photo for a cross-section). The image can be scaled, moved, rotated, and its opacity and visibility can be changed. The reference image is included with the PNG export if it's visible

**Line Presets**
- Added two drawing presets to the sketch screen, which appear as "P1" and "P2". These are intended to allow a sketcher to switch between drawing thin, detailed lines, and smooth, straight lines
- Preset 1's defaults are a line style of `fine` and a line point spacing of 1
- Preset 2's defaults are a line style of `bezier` and a line point spacing of 10 (todo: switch to 15?)
- Added a presets menu to the sketch settings screen to allow customization of each preset

**S Pen, Active Key, and Volume Button Support**
- Added support for the S Pen button for single click, double click, and long click inputs
    - *Note that this is only tested with the IP68 S pen that has no bluetooth and no battery. It may not work with other pens. Additionally, the pen must be held close to the screen in order for the button to work*
- Added the following actions:
  - Undo: Perform the undo action in the sketch screen
  - Redo: Performs the redo action in the sketch screen
  - Toggle palette: Toggles the recently used bottom pallete between LINE, POINT, and AREA
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
- Added tests for the three new user sketch lines, the drawing presets, ZIP export/import, and compass export.

**misc UI**
- Added a new, more capable color picker widget
- Added sketch grid appearance settings for both grid width and grid color
- Added the new 1 foot sketch-grid unit alongside the existing 2 feet, yard, meter, and 10 cm options
- Added an option (on by default) for overlapping areas (eg water) to darken instead of lighten. This is mostly for drawing deeper water pools
- Made the default icon size large

### Testing

Instrumentation tests live under `app/src/androidTest/`. They drive a running emulator with real taps and swipes (Espresso + UIAutomator), export files to `/sdcard/...`, and compare screenshots and text exports to golden fixtures. They're probably a bit brittle outside of the particular environment they were created in.

**Requirements**
- An emulator running at **2560 × 1600, 320 dpi, font scale 1.0, English locale**. The tests asserts this profile on startup and will fail fast on a mismatch. Goldens are matched to this profile.
- Android SDK platform-tools on `PATH`, or `local.properties` pointing at `sdk.dir` (the scripts will read it either way, probably).
- JDK 21

**Scripts** (PowerShell, under `scripts/`)
- `scripts\test-fast.ps1` — sketch-draw + Compass-export only.
- `scripts\test-full.ps1` — all four tests including the ZIP round-trip (import via DocumentsUI) and PNG export.
- `scripts\refresh-visual-baselines.ps1` — re-runs in record mode and copies the new PNG/`.dat` fixtures into `app/src/androidTest/assets/goldens/emulator_2560x1600_320dpi_font1.0/`. Run this after any intentional UI or rendering change that invalidates the existing goldens.

Each script defaults to `-Serial emulator-5554`. Pass `-Serial <id>` to target a different device.

**What's covered**
1. Create a survey, enter shots, open a plan sketch, draw with P1/P2 presets and the three user-line widths (fine/standard/thick), screenshot-diff against golden .PNG files.
2. Export to ZIP with symbols on, validate `lines.zip` inside contains `user-fine`/`user-standard`/`user-thick`, delete the survey, re-import the ZIP through the system document picker, re-open the plot, screenshot-diff.
3. Export to PNG, pixel-exact compare against golden .pngs
4. Export to Compass `.dat`, normalize the dynamic `SURVEY DATE:` line (this is fine, right?), compare against golden.

**Where output lands**
- `tmp-test-artifacts/<testCaseName>/`: Actual screenshots, exported files, and on failure also `expected-<name>` and `diff-<name>` PNGs for visual diagnosis.
- `tmp-recorded-latest/recorded-goldens/...`: Only written by `refresh-visual-baselines.ps1`; the script copies from here into the tracked goldens directory.

Both `tmp-*` dirs are gitignored and are regenerated on every run.

### Versioning
- TopoDroid Sketch uses SemVer for the app version: MAJOR.MINOR.PATCH, currently 0.22.1.
- Android versionName is the human app version shown by Android and in app UI.
- Android versionCode is a monotonically increasing integer used by Android to allow upgrades. For Sketch it is derived from the SemVer parts with a Sketch epoch, currently 722010
- Git SHA is not included in versionName; it is only added to QA APK filenames for traceability.
- QA debug APKs can be built with packageQaDebug, producing names like TopoDroid-Sketch-v0.22.1-722010-<sha>-debug.apk.
- TopoDroid file compatibility is versioned separately from the Android app version.
- TOPODROID_COMPAT_VERSION_NAME / TOPODROID_COMPAT_VERSION_CODE describe the vanilla TopoDroid file/protocol baseline Sketch currently exports as compatible with, currently 6.4.27 / 604027.
- ZIP manifests, TDR/sketch streams, and parser-sensitive import/export paths use the compatibility version, not Sketch’s Android versionCode.
- Export provenance may say TopoDroid Sketch v 0.22.1, but compatibility gates should still use 604027 until the file format baseline changes.
- Dev note: Only bump the compatibility version after merging/testing against a newer vanilla TopoDroid baseline.
- Dev mote: Do not bump database, symbol, or compatibility versions merely for normal Sketch app releases.
- - Dev note: Until 1.0, group all changelog items together, don't split out by 0.23, 0.24, etc

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
