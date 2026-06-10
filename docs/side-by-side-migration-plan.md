# Side-by-Side Install Migration Plan: TopoDroid Sketch ↔ TopoDroid-X

## Goal

- Allow vanilla TopoDroid-X (`com.topodroid.TDX`, pinned test APK currently `TopoDroidX-6.4.53-36.apk`) and TopoDroid Sketch to be installed on the same Android device at the same time.
- Allow a `.tdr` ZIP exported from Sketch to be imported into vanilla TopoDroid-X for compatibility testing.
- Keep code churn minimal: avoid renames that break upstream diffs without functional benefit.

## TL;DR — what actually has to change

- **`applicationId`** must be unique per app on a device. Currently both apps are `com.topodroid.TDX`. Change the fork to e.g. `com.topodroid.TDX.sketch`.
- **FileProvider authority** must be globally unique across all installed apps. Currently `com.topodroid.fileprovider` in both apps. A second app registering the same authority fails to install with `INSTALL_FAILED_CONFLICTING_PROVIDER`.
- **Public `Documents/` folder** currently resolves to `Documents/TDX/TopoDroid/` in both apps. Same SQLite DB, same `tdr/` files, same `zip/` exports. They will silently stomp on each other every launch. Must change the fork's root.
- Everything else (app name, launcher icon, version strings, intent action names, internal Java package, in-app branding) is cosmetic for the side-by-side question.

## What does NOT need to change

- **The Java package `com.topodroid.TDX`.** It is decoupled from `applicationId` via Gradle's `namespace`. Renaming would touch ~500 source files, every layout XML using `<com.topodroid.TDX.SomeView>`, every test, and every reflection lookup — for zero behavioral benefit.
- **Custom intent action `TopoDroid.intent.action.Import`.** Keeping this string identical is what *enables* the round-trip handover. If a user shares a Sketch ZIP, the Android share sheet will offer both apps as receivers.
- **The custom intent category `com.topodroid.TDX.CATEGORY_SURVEY`.** Same reason as the intent action — shared identity helps cross-app sharing.
- **`SharedPreferences`, internal SQLite, app settings file.** These live under `getFilesDir()` / `getExternalFilesDir()`, which Android scopes to `applicationId`. Once the applicationId change lands, prefs and the device DB auto-isolate.

---

## Phase 0 — Audit before changing anything

- Verify `TDVersion.DATABASE_VERSION` (Sketch is at `60`, `src/com/topodroid/util/TDVersion.java:36`) against the pinned vanilla APK. If vanilla is also `60`, ZIP imports into vanilla should pass the database-version ceiling. If vanilla is older, vanilla will reject Sketch ZIPs with `ERR_DB_NEW` (`Archiver.java:633`).
- If they diverge, decide: hold the fork's DB at vanilla's level (and store any new DB columns nullable + additive only), or accept that exports won't import into vanilla.
- Verify `TDVersion.SYMBOL_VERSION` (Sketch is at `"44"`) against vanilla. Affects how `user-fine` / `user-standard` / `user-thick` symbol files seed when re-imported.
- Output of this phase: a one-page compatibility note pinned to the vanilla baseline version, so the team knows which way breakage will manifest if either schema gets bumped later.

---

## Phase 1 — Make side-by-side installable (the only critical phase)

### 1.1 Change `applicationId`

- Edit `app/build.gradle:20`:
  - From: `applicationId 'com.topodroid.TDX'`
  - To: `applicationId 'com.topodroid.TDX.sketch'`
- Leave `namespace 'com.topodroid.TDX'` (`app/build.gradle:16`) unchanged. This is what lets Java sources stay where they are.
- `AndroidManifest.xml` line 4 has `package="com.topodroid.TDX"` — this is the legacy AGP 7 attribute. Remove it (AGP 8+ prefers it gone) or leave it matching `namespace`.

### 1.2 Change FileProvider authority

- `AndroidManifest.xml:402`:
  - From: `android:authorities="com.topodroid.fileprovider"`
  - To: `android:authorities="com.topodroid.TDX.sketch.fileprovider"`
- `src/com/topodroid/util/MyFileProvider.java:59`:
  - From: `private static final String FILEPROVIDER_AUTHORITY = "com.topodroid.fileprovider";`
  - To: `private static final String FILEPROVIDER_AUTHORITY = "com.topodroid.TDX.sketch.fileprovider";`
- Cleaner alternative for the constant: replace it with `BuildConfig.APPLICATION_ID + ".fileprovider"` so the manifest and Java stay in sync forever.
- Lines 364–371 of `AndroidManifest.xml` have a commented-out `provider` element using `com.topodroid.TDX.provider`. Leave commented or update consistently — do not silently uncomment.

### 1.3 Change public-Documents root

- Single source of truth: `src/com/topodroid/util/TDFile.java:392–397`. The string `"TDX"` is hardcoded in `getCBD()`.
- Change `"TDX"` → `"TDX-Sketch"` (or `"TopoDroid Sketch"` — pick whatever users will recognize in their file browser).
- Resulting public layout for the fork:
  - `Documents/TDX-Sketch/TopoDroid/distox14.sqlite`
  - `Documents/TDX-Sketch/TopoDroid/zip/`, `tmp/`, `thconfig/`, `c3export/`
  - `Documents/TDX-Sketch/TopoDroid/<survey>/tdr/`, `/photo/`, `/audio/`, `/note/`, `/out/`
- Vanilla's `Documents/TDX/TopoDroid/...` is untouched.
- The inner `TopoDroid` folder is the user-changeable `DISTOX_CWD` preference — no change needed there.
- Update `res/xml/file_paths.xml:2` so the FileProvider can still grant URIs over zip exports:
  - From: `<external-path name="zips" path="Documents/TDX/" />`
  - To: `<external-path name="zips" path="Documents/TDX-Sketch/" />`

### 1.4 Update launcher shortcuts

- `targetPackage` in `res/xml/shortcuts.xml` and `res/xml-v22/shortcuts.xml` is the **applicationId**, not the Java package, so it must change with 1.1.
- For each of the three `<shortcut>` entries in each file:
  - From: `android:targetPackage="com.topodroid.TDX"`
  - To: `android:targetPackage="com.topodroid.TDX.sketch"`
- Leave `android:targetClass="com.topodroid.TDX.MainWindow"` unchanged — it's the Java FQN.

### 1.5 Update test scripts and instrumentation constants

- `scripts/test-fast.ps1`, `scripts/test-full.ps1`, `scripts/refresh-visual-baselines.ps1`:
  - Update `$AppPackage` to the new applicationId.
  - Update `$TestPackage` to `<applicationId>.test`.
  - Update `$ArtifactsRemote` from `/sdcard/Android/data/com.topodroid.TDX/files/test-artifacts` to use the new applicationId.
- `app/src/androidTest/java/com/topodroid/TDX/VisualTestSupport.java:111-112`:
  - Update `PACKAGE_NAME` and `TEST_PACKAGE` constants.
  - Or better: read from `androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName()`.

### 1.6 Stop point

- After Phase 1, both apps install side-by-side and never see each other's storage.
- This is a valid stopping point if all you need is parallel install. Phase 2 only matters if you want round-trip import into vanilla.

---

## Phase 2 — Round-trip ZIP into vanilla TopoDroid-X

### 2.1 Manifest version line

- `Archiver.checkVersionLine` (`Archiver.java:669`) on the vanilla side parses `6.4.27 604027` from the manifest.
- If `6.4.27 > vanilla.VERSION`, vanilla returns `ret = 1` ("OK with warning") and import proceeds.
- No action needed unless the fork's version drops below `MAJOR_MIN.MINOR_MIN.SUB_MIN` (currently `2.1.1`, so not a real risk).

### 2.2 Database version ceiling

- The hard rule is at `Archiver.java:632`: `if ( manifest_DB_version > current_DB_version ) return ERR_DB_NEW;`
- Vanilla's `TDVersion.DATABASE_VERSION` is the import ceiling — do not bump the fork past it.
- Already covered in Phase 0; reaffirm here as a release-gate check.

### 2.3 Schema additions stay additive

- Any new columns the fork has added to survey/plot/scrap tables must be tolerated by vanilla.
- Vanilla will ignore unknown columns when reading rows but may fail if it tries to recreate the schema.
- Audit the fork's diff against `DataHelper.java`. Any `ALTER TABLE` or new `CREATE TABLE` since vanilla's baseline must be a no-op when re-run on a vanilla install.
- If the fork has stuffed fork-only data into vanilla columns (e.g. encoded a viewport into a `comment` field), document it explicitly so the team knows what survives a round-trip and what doesn't.

### 2.4 Keep cross-app handoff strings unchanged

- Leave `TopoDroid.intent.action.Import` (manifest line 177, `MainWindow.java:1232`) as-is.
- Leave `com.topodroid.TDX.CATEGORY_SURVEY` (`TopoDroidApp.java:3439`) as-is.
- These are what cause both apps to appear in the share sheet for a `.tdr` ZIP — the entire point of round-trip testing.

---

## Phase 3 — UX polish (cosmetic, non-blocking)

### App identity

- `res/values/strings.xml:51`: change `<string name="app_name">TopoDroid-X</string>` → `TopoDroid Sketch`. The other locale `strings.xml` files have `app_name` commented out or omitted, so a single English change is enough (the fork is English-only per the README).
- Replace `res/mipmap/ic_launcher` with a visually distinct variant (color tint, "S" badge, etc.) so users can tell the two apps apart on their home screen.

### In-app references to the old applicationId

- Several user-visible strings reference `com.topodroid.TDX` in `adb shell appops set ... MANAGE_EXTERNAL_STORAGE allow` instructions.
- Search and update:
  - `res/values*/strings.xml`
  - `int18/values*/strings.xml`
  - `int18/man-fr/manual00.htm` (lines 51, 87–89)
- Update to the new applicationId so users debugging permissions don't get misled.

### Versioning

- Adopt a Sketch-specific version scheme that clearly diverges from vanilla, e.g. `6.4.27-sketch.5`.
- Reason: a user looking at app info should be able to tell which app is which without opening it.

### Settings/About screen

- Audit `res/values/strings.xml` for any user-facing "TopoDroid" → "TopoDroid Sketch" rebrand once Phase 1 is shipped and stable.
- Don't do this in the same release as Phase 1 if you can avoid it — keep the install-identity changes isolated for easier rollback.

---

## Files touched in Phase 1 (the critical phase)

- `app/build.gradle` — `applicationId`
- `AndroidManifest.xml` — FileProvider authority; optionally remove `package=`
- `src/com/topodroid/util/MyFileProvider.java` — `FILEPROVIDER_AUTHORITY` constant (or switch to `BuildConfig.APPLICATION_ID`)
- `src/com/topodroid/util/TDFile.java` — `"TDX"` → `"TDX-Sketch"` in `getCBD()`
- `res/xml/file_paths.xml` — `Documents/TDX/` → `Documents/TDX-Sketch/`
- `res/xml/shortcuts.xml` — `targetPackage` (3 entries)
- `res/xml-v22/shortcuts.xml` — `targetPackage` (3 entries)
- `scripts/test-fast.ps1` — `$AppPackage`, `$TestPackage`, `$ArtifactsRemote`
- `scripts/test-full.ps1` — same three variables
- `scripts/refresh-visual-baselines.ps1` — same three variables
- `app/src/androidTest/java/com/topodroid/TDX/VisualTestSupport.java` — `PACKAGE_NAME`, `TEST_PACKAGE` constants

That's the complete critical-path footprint.

---

## Validation checklist

### After Phase 1

- Both APKs install side-by-side on a single device without `INSTALL_FAILED_CONFLICTING_PROVIDER` or `INSTALL_FAILED_DUPLICATE_PACKAGE`.
- The vanilla APK native ABI matches the test target. `TopoDroidX-6.4.53-36.apk` is ARM-only (`arm64-v8a`, `armeabi-v7a`), which matches the ARM tablet target, but it does not install on the current `x86_64` emulator.
- Each app shows its own surveys list (no cross-contamination).
- `Documents/TDX/` on the device contains only vanilla data.
- `Documents/TDX-Sketch/` contains only fork data.
- `Android/data/com.topodroid.TDX/` and `Android/data/com.topodroid.TDX.sketch/` exist as separate dirs.
- Each app's "Share ZIP" works without the FileProvider crashing.
- All instrumentation tests still pass after the script and `VisualTestSupport` constant updates.

### After Phase 2

- Sketch exports a ZIP. The system share sheet shows both Sketch and vanilla as recipients.
- Vanilla imports the Sketch ZIP without errors.
- New sketch lines render as `user` lines in vanilla.
- Cross-section viewports degrade visibly in vanilla but don't crash.
- Vanilla re-exports that imported survey. Sketch can import the round-tripped ZIP.

---

## Risks and notes

### `PRIVATE_STORAGE` flag

- `TDandroid.java:93` sets `PRIVATE_STORAGE = false` — the app uses public Documents.
- If this is ever flipped to `true`, all paths move under `Android/data/<applicationId>/files/`, which is automatically per-app, and the Phase 1.3 change becomes redundant (but harmless).
- Do not flip this without a separate migration plan — switching forces a one-way data move and uninstall would delete user data.

### Pre-existing fork users

- Users currently running TopoDroid Sketch as `com.topodroid.TDX` (overwriting vanilla) will see the next install as a *new app from Android's perspective* — settings and surveys do not migrate automatically.
- Consider a first-run import-from-old-folder helper:
  - Reads `Documents/TDX/TopoDroid/` and copies it to `Documents/TDX-Sketch/TopoDroid/`.
  - Reads device DB / settings out of the old `Android/data/com.topodroid.TDX/files/` dir.
  - Optional, but friendly. Defer if the user base is small enough that a manual move is acceptable.

### `BuildConfig.APPLICATION_ID` hygiene

- Several places in the codebase hardcode `"com.topodroid.TDX"` as a string (test scripts, the commented-out `provider` declaration).
- The proper Android pattern is to read `BuildConfig.APPLICATION_ID` at runtime.
- Optional cleanup: sweep for the literal `"com.topodroid.TDX"` and replace with `BuildConfig.APPLICATION_ID` wherever it represents the install identity (not the Java package).
- Good hygiene but not required for Phase 1.

### Java-package rename is a trap

- It is tempting to also rename `com.topodroid.TDX` → `com.topodroid.sketch` so everything matches.
- Do not.
- Hundreds of files of mechanical churn, breaks every diff against upstream forever, zero functional benefit.
- The Gradle `namespace` exists precisely to keep the Java package and the install identity decoupled.
