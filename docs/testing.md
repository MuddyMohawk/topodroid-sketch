# Testing TopoDroid Sketch

Run commands from the repository root in PowerShell. Tests do not change application behavior, migrate surveys, or update symbol versions.

Coverage prioritizes silent damage: lost or altered survey data, saved drawing metadata, malformed archives, export pixels, and supported interchange. Installed-Vanilla compatibility is isolated for its deferred conversion sprint. Routine settings layout, exact UI wording/colors, shortcut-settings screenshots, retired symbol-size preferences, stylus-only protection tests and the private rock prototype are not maintained checks. The smoke workflow checks that one real field session preserves entered measurements, drawing geometry and styles, and exported pixels across a cold reload.

## What to run, and when to stop

Start with the smallest existing test or feature selection that covers the changed behavior. One successful relevant run is normally enough. Broaden coverage when a change crosses a persistence/format boundary, changes shared rendering or input code, exposes an unexpected failure, or warrants a release-level check. The existence of a larger suite is not a reason to run it after every edit.

| Change | Start with | Broaden only when needed |
|---|---|---|
| Documentation, comments, or other edits without behavior changes | Review the text and affected links. | No test run, APK build, or tablet refresh solely for documentation. |
| Local models, parsing, calculations, or queue/state logic | The affected local JVM class or feature; see the local-only command below. | Add Android checks if actual storage, graphics, activities, or production import/export paths are affected. |
| Symbols, line/area patterns, brush appearance, or special symbols | `fast -Feature symbols` | Add `exchange` when saved symbol/style metadata or exported pixels could change. Use relevant `full` methods when live-canvas or live drawing behavior is affected. Symbol work does not require device-switch/Bluetooth tests. |
| Survey database, shots, sections, or remembered drawing choices | `fast -Feature data` | Add `exchange` for archive/format changes and `devices` for incoming measurement routing. Use a focused UI case if the suspected loss happens during Save, Cancel, navigation, or deferred commit. |
| Stroke creation, undo/redo, erasing, or transforms | `fast -Feature drawing` | Add `smoke` when the actual toolbar/input-to-committed-object-to-export workflow is affected. |
| ZIP, CSV, Compass, PNG, or reference-image assets | `fast -Feature exchange`, or the specific existing regression case | Use relevant `full -Feature exchange` methods for live reference images, approved images, and real production file content. Ordinary `exchange` selections do not require Vanilla. |
| Dedicated Vanilla conversion/compatibility work | The specifically affected contract; `full -Feature vanilla` only when installed-app behavior is the subject | Vanilla compatibility is deferred. Do not rerun it for unrelated Sketch exports or add refresh workarounds to force a pass. `all` still includes it. |
| Instrument queues, switching, stale callbacks, or measurement provenance | Local `devices` checks | Use `full -Feature devices` when production orchestration, callbacks, or database routing changes. Simulated drivers do not establish physical radio reliability. |
| Obvious settings/layout/text/color changes | Manual inspection of the affected screen; compile app changes as required below. | Add focused tests only for a less-visible consequence such as writing the wrong preference, altering existing strokes, or losing saved data. Do not restore a broad cosmetic screenshot matrix. |
| Test selection, accounting, fixture isolation, or runner changes | `scripts/test-runner-contracts.ps1`; `python scripts/test_qa_physical_cwd.py` for tablet-folder recovery; `fast -Feature runner` for Android fixture/comparator changes | Run `scripts/test-runner-recovery-contracts.ps1` for subprocess deadlines, crashes, cancellation, or recovery changes. Rebuild the test APK when annotations or Android fixtures change. |
| Broad changes across features, or a release/final comprehensive check | One `all` run with its explicit device prerequisites | Do not first run `full` and then immediately repeat its coverage through `all`. If physical prerequisites are unavailable, run the appropriate `full` selection and explicitly report physical compatibility as unverified. |

Here `fast -Feature symbols`, for example, means `scripts/test-suite.ps1 -Suite fast -Feature symbols`. Complete examples follow below.

Local-only tests can run without starting an emulator or installing APKs:

```powershell
# Run the local device-logic checks; choose another feature as appropriate.
.\gradlew.bat -g .\.gradle-test-home --offline --console=plain --max-workers=2 :app:testDebugUnitTest -PqaFast=true -PqaFeatures=devices
# Or target one existing local class instead of an entire feature.
.\gradlew.bat -g .\.gradle-test-home --offline --console=plain --max-workers=2 :app:testDebugUnitTest -PqaFast=true --tests 'com.topodroid.TDX.ToolsetProfileTest'
```

`-PqaFast=true` excludes the statistical calibration. The local-only command exercises JVM tests; Android bitmap rendering, Android persistence, actual picker/file workflows and installed Vanilla require their corresponding device checks. Currently `fast -Feature devices` requests only local methods, so the direct Gradle command avoids unnecessary emulator discovery/installation for that coverage.

After the relevant checks pass, stop unless new edits, a failure, or a concrete unresolved risk justify more testing. In particular:

- Do not run every feature because one changed, or routinely run `fast`, `smoke`, `full`, diagnostics and `all` in sequence. Choose the coverage needed for this change.
- Do not repeat a passing suite just to collect timings. Read its existing `command.json`. Use a small repeatability experiment only when investigating an actual runtime or intermittent-test problem.
- Use `-Repeat`/`-Reverse` only for targeted stability/order investigations. Keep each attempt's result; never rerun until green and discard failures.
- If a prerequisite or fixture fails, inspect that failure and its capture first. `BLOCKED` cases are unvalidated; do not repeatedly launch a broad suite against the same unresolved setup failure.
- If Android/emulator health fails, preserve the checkpoint and logs, restore health, then explicitly resume unfinished checks with matching APKs. Repeatedly restarting a complete sweep adds cost without resolving the cause.
- Run diagnostics to answer a specific performance/rendering question. Refresh baselines only for intentional, reviewed appearance changes; neither is routine validation.

Test scope is separate from the repository's completion requirements for application features/fixes: compile the APKs and follow the documented Samsung refresh when connected. A build/install/launch check does not require running unrelated test features. Documentation-only edits do not trigger that procedure.

## Commands and coverage

```powershell
.\scripts\test-suite.ps1 -Suite fast -Serial emulator-5554
.\scripts\test-suite.ps1 -Suite smoke -Serial emulator-5554 -SkipBuild
.\scripts\test-suite.ps1 -Suite full -Serial emulator-5554
.\scripts\test-suite.ps1 -Suite diagnostics -Serial emulator-5554
.\scripts\test-suite.ps1 -Suite physical -PhysicalSerial <explicit-tablet-serial>
.\scripts\test-suite.ps1 -Suite all -Serial emulator-5554 -PhysicalSerial <explicit-tablet-serial>
# Feature selections apply to both JVM and Android tests:
.\scripts\test-suite.ps1 -Suite fast -Feature symbols -Serial emulator-5554
.\scripts\test-suite.ps1 -Suite full -Feature symbols,exchange -Serial emulator-5554
.\scripts\test-suite.ps1 -Suite full -Feature devices -Serial emulator-5554
```

| Suite | Requested checks |
|---|---|
| `fast` | Local JVM logic plus Android logic, persistence, production export/import contracts, and rendering checks. Statistical calibration is excluded. |
| `smoke` | One real field session: UI-entered decimal shots, backsight and comment; dispatched Thick line, ordinary point and solid area; navigation save and cold storage reload; exact measurement/geometry/style comparison and PNG regions including interior fill. Plot creation and PNG export use production APIs. |
| `full` | Every correctness method on the emulator and all JVM checks, including the unchanged 4,000-dataset bedding calibration and installed-Vanilla compatibility. With `-Feature`, requests correctness for those features only. |
| `diagnostics` | Frame measurements, reported render hashes and PNG encoding benchmarks. Successful measurements are reported as `MEASURED`, not correctness passes. |
| `physical` | Only the explicitly selected tablet's dedicated, allowlisted Sketch/Vanilla compatibility check. Ordinary tests refuse physical targets before fixtures, installation, or package resets. |
| `all` | Every maintained JVM/emulator method, including diagnostics and emulator Vanilla compatibility, plus the explicitly selected physical-tablet compatibility check. |
| `refresh` | Baseline authoring only; stages candidates and never applies them as part of validation. Use the procedure below. |

Feature grouping is independent of execution tier:

| `-Feature` | Coverage |
|---|---|
| `symbols` | Packaged definitions, symbol/style rendering, special symbols, and associated saved-format/export contracts. |
| `drawing` | Committed strokes, undo/redo, erasing, transforms and a representative toolbar workflow. |
| `data` | Database isolation, shot/section identity, saved selections/styles and data integrity. |
| `exchange` | Sketch archives, import/export, PNG, CSV/Compass, reference-image assets and associated file-content contracts. |
| `devices` | Measurement provenance, switching/queue sequencing and stale callbacks; communication drivers are simulated. |
| `vanilla` | The dedicated installed-Vanilla compatibility workflow. Deferred known failures remain visible and nonzero when requested. |
| `runner` | Target/path safety, fixture ownership, recovery and visual-comparator negative controls. |

Choose multiple features with commas; their union runs each method once. Method annotations can override class features. Symbol selections exclude device-switch/connection tests. `-Group` is an alias for `-Feature`. The `physical` and `refresh` entry points use their dedicated selections.

`all` always selects every maintained JVM and Android method, every diagnostic, emulator Vanilla compatibility and the explicitly selected physical check. It rejects `-Feature` and `-Selector` rather than allowing a subset to be called all. A scoped `full` is full correctness for the chosen features; unrelated methods are reported `NOT_REQUESTED`.

The existing `test-fast.ps1`, `test-full.ps1`, `test-render-identity.ps1`, and `test-render-perf.ps1` entry points retain `-Serial` and `-SkipBuild`. `test-physical-compat.ps1` requires an explicit `-Serial`. `start-test-run.ps1` supports the suite names above and runs with a log and PID file; use `watch-test-run.ps1` to follow it.

`-SkipBuild` uses the existing compiled APKs, prints their absolute filenames and SHA-256 hashes, and installs each once. It still executes the requested JVM tests. Use it only when the app/test APKs already contain the code, symbols, assets and baselines being validated; rebuild after changing any of those. Without it, the runner builds the app, Android test APK, and JVM test classes once. Build and installation time are reported separately from validation.

For investigation, `-Selector` accepts exact APK-discovered class names or `class#method` selectors within the chosen tier/features. Unmatched and empty selections fail. Selectors are unavailable with `all`; other suites mark unselected methods `NOT_REQUESTED`. Selected Android-only investigations do not repeat JVM calibration. `-Repeat 5` records five separate UI attempts without retrying failures into passes; `-Reverse` changes the starting order.

For example, validate the styled ZIP regression without rerunning every export workflow:

```powershell
.\scripts\test-suite.ps1 -Suite full -Feature exchange -Serial emulator-5554 -Selector 'com.topodroid.TDX.SketchBrushRoundTripInstrumentedTest#sketchZipRoundTrip_preservesStyledLineAndPointOptions'
```

Sketch PNG/ZIP content checks call the real production import/export APIs, without driving the format or document picker. They retain payload, metadata, pixel, and approved-image assertions. These selections do not request the dedicated `vanilla` method.

## Prerequisites

- JDK 21 (the Android Studio bundled JBR), the configured Android SDK, and the local Gradle dependency cache. The runner uses `.gradle-test-home`, or `TOPO_TEST_GRADLE_HOME` when set, and runs Gradle offline.
- Python 3 on `PATH` for result reporting. Baseline authoring also requires a current Pillow installation. These runtimes are checked before the long run; applying already reviewed candidates only needs PowerShell.
- A connected, booted emulator for ordinary Android tests. Visual tests require 2560×1600, density 320, font scale 1.0, and an English locale. An emulator with failing Android system services must be repaired before interpreting UI results.
- `all` additionally requires an explicit `-PhysicalSerial` and installed Vanilla (`com.topodroid.TDX`) on both devices. Prerequisites are checked before the long run. Missing prerequisites are failures, never silent omissions. No script automatically selects the Samsung field tablet.
- `full` requires installed Vanilla on the emulator when its selection includes compatibility (`vanilla` or unfiltered `full`). A symbol-only or device-only selection does not require Vanilla. `physical` remains the explicitly selected tablet's dedicated compatibility path.
- The selected tablet must be unlockable without an authentication prompt. The runner wakes it during preflight and immediately before its compatibility check; a locked target is refused before the long run.
- Compatibility uses the installed Vanilla app. `test-side-by-side.ps1` resolves the actual installed launcher activities; `-UseInstalledVanilla` and `-SkipSketchInstall` remain available.

Do not run generic connected instrumentation against every attached device. Use the guarded runner. There is no destructive physical-clean option.

## Isolation, results, and timings

`SketchTestRunner` discovers methods from the built Android test APK. Every method declares `@TestTier` and `@TestFeatures`, inherited from its class unless overridden on the method. New unclassified methods fail discovery. Both Android and JVM tests share the feature enum from test-only `app/src/qa/java`; the app APK does not contain it. JVM calibration uses `@Category(SlowChecks.class)`. Gradle scans all top-level compiled JVM classes for `@Test` methods (without requiring a `Test` filename suffix) and applies the same feature selection; a selected Android feature with no JVM methods reports those local methods `NOT_REQUESTED`.

`QaFixtureRule` runs before `@Before`. It closes owned database handles before switching `DISTOX_CWD` and paths to `Documents/TopoDroid Sketch QA/<run>/<case>`. Survey databases are created only for cases declaring `@QaFixtureRule.Database`; pure logic/rendering cases still receive isolated file paths. Packaged symbols are initialized explicitly on the emulator. Preferences, survey selection, controller state, and drawing-manager caches are isolated between cases. Physical checks preserve the tablet's edited symbols.

Before physical instrumentation, the host records the original `DISTOX_CWD` (including an absent preference) in `<run>/physical-compat-cwd.json` and a private device marker. The fixture records its exact QA directory in that marker before switching paths. Normal cleanup restores the preference; the host also restores it after an interrupted test, changing only its exact active QA value and preserving other preferences or a later user-selected directory. No public survey files are touched.

If the tablet disconnects, recovery remains pending and the next physical run refuses to start. Reconnect it, then explicitly recover using the same selected serial:

```powershell
python scripts/qa_physical_cwd.py recover --adb "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" --serial <explicit-tablet-serial> --journal review-artifacts/<interrupted-run>/physical-compat-cwd.json
```

The device marker also allows recovery without the original host journal. If ownership is missing, conflicting, or another QA folder is active, the safeguard refuses to guess. Inspect the evidence and original record before manual recovery. Never clear package data or the public survey tree as a recovery shortcut.

The installed Vanilla release uses its configured working directory. Compatibility adds one uniquely named `compat_*` survey there and retains it and its ZIP as evidence; it never deletes existing Vanilla surveys or resets Vanilla. Sketch's side of the round trip uses the isolated QA directory.

Cleanup removes only the current method's newly created working directory and exactly recorded, uniquely named QA downloads. Helpers require recorded survey IDs before explicit survey deletion. Failed cases keep their files and database. Never delete or clear the public `Documents/TopoDroid Sketch/` survey tree.

Each invocation writes a unique directory under `review-artifacts/test-repair-*`, including:

- `report.html`, `test-results.txt`, `results.json`, and `results.csv`: every discovered method, feature classification, tier, status, duration, and failure/blocked notes;
- `apks.txt`: actual APKs used, sizes, and SHA-256 hashes;
- `timings.json`: build, install, JVM, discovery, fixture, test, cleanup, and complete validation durations;
- `command.json`: actual command start/end and total elapsed time including preflight, build/install, evidence transfer and reporting;
- raw instrumentation logs, JVM XML, and device artifacts. Missing method/process terminal results are blocked failures, not passes.

Only JVM XML written by the current invocation counts as a terminal result. An earlier passing report cannot conceal a new build or execution failure.

### Emulator interruptions and checkpoints

The host checks the device's boot identity before and after each instrumentation group and every 15 seconds while it runs. Each health probe has a five-second deadline. A disconnected/frozen device, changed boot identity, missing process completion, or Android-aborted instrumentation is an infrastructure interruption, distinct from a method assertion failure. The affected results are `BLOCKED`; remaining groups on that target stop. Independent physical checks may still run. The command remains nonzero. No emulator restart or whole-test retry happens automatically.

The no-progress watchdog allows 180 seconds for UI/smoke groups and 90 seconds for other instrumentation groups. A UI workflow may contain two exports, each with its own 45-second file condition, plus dialog interaction between progress events. The total group cap and 15-second health checks remain active; the five/ten/45-second control, navigation and file conditions are unchanged. Export stages emit progress only when real work advances.

The multi-application compatibility round trip has a ten-minute total cap, with its 90-second no-progress watchdog and ordinary per-step conditions retained. Its three-minute former cap could expire during a valid 45-second Vanilla visibility assertion after the preceding Sketch workflow had consumed most of that budget. The cap bounds a whole workflow; it does not extend an export or visibility assertion.

UI groups have a three-minute total allowance per selected method, with a three-minute minimum. This avoids truncating a group of legitimate workflows whose individual executions can exceed the former two-minute average allowance. These outer runner caps are failure safeguards, not timing targets or replacements for per-step assertions.

Raw instrumentation output and `checkpoint.json` are saved before diagnostic collection. Diagnostics have a 15-second total budget, with at most five seconds per call; an unavailable health probe stops further diagnostics. Evidence transfer has a 30-second limit and is skipped for stopped targets. If animation restoration cannot run, the report records the previous values for manual recovery. Failed QA directories remain as evidence; public survey data is never cleared to recover a device.

After saving interruption evidence, the host makes one bounded five-second request to stop only the Sketch app and its test package on that target. This prevents Android instrumentation from continuing after its host client has stopped. It does not clear package data, stop the adb server, or reset Vanilla. Long interaction cases report real completed steps so the no-progress watchdog can distinguish ongoing work from silence.

After restarting the emulator and checking its profile, resume only unfinished checks into a new output directory:

```powershell
.\scripts\test-suite.ps1 -Suite all -Serial emulator-5554 -PhysicalSerial <same-explicit-serial> -SkipBuild -ResumeFrom review-artifacts/interrupted-run -OutputDirectory review-artifacts/resumed-run
```

Resume requires the same suite, features, serials, selectors, order, `Repeat=1`, and exact SHA-256 hashes of both APKs. Each run retains `used-app.apk` and `used-test.apk`; resume installs those preserved binaries, even if newer APKs have been built. Completed passes, assertion failures, and measurements are retained. Interrupted/unstarted checks execute once, with the original interruption report kept separately. Changed test code or baselines require a separate validation run and cannot inherit passing results under the old APK's identity. Current checkpoints use version 3, reflecting the retired picker prerequisite and the separate Vanilla feature. Older checkpoints/reports remain historical evidence and cannot be resumed with this inventory schema. Authoring and repeated-attempt investigations start separate runs.

For host-only recovery verification, run `scripts/test-runner-contracts.ps1`, `scripts/test-runner-recovery-contracts.ps1`, and `python scripts/test_qa_physical_cwd.py`. The latter compiles a disposable fake adb process and exercises success, assertion failure, ANR, reboot, disconnection, and a hung client through the actual instrumentation runner. It does not contact a real device.

There is no shared export-picker prerequisite. Assertion failures in independent methods do not stop the remaining suite. Assertions and cleanup failures are both retained, with cleanup diagnostics attached to the original failure.

Ordinary controls use five-second conditions; navigation and frame readiness use ten seconds; asynchronous export files use 45 seconds. Intentional gesture timing remains. Screens must produce three identical actual frames within the frame deadline; reference-image tests additionally verify metadata, visible canvas intersection, and actual fixture colors on screen. They never substitute an offscreen render or rerun the entire test to manufacture a pass.

UI commands temporarily disable the emulator's three system animation scales and restore their exact previous values afterward. This prevents slow emulator transitions from consuming control/frame deadlines. The physical tablet's animation settings are untouched. Back navigation dispatches once per observed activity transition, allowing ten seconds for each transition.

`QaJUnit4` preserves AndroidJUnit4 execution and measures `@Before`, the test body, and `@After` separately. Screen capture reads Android's native `screencap` output after the bitmap screenshot API proved unreliable on this emulator. Production archive/export APIs seed rendering and payload fixtures. Only the dedicated installed-Vanilla workflow retains its existing picker interactions; it is outside ordinary Sketch exchange selections.

The remaining drawing-event fixtures check committed objects, canceled previews and undoable changes rather than stylus-only protection. The document picker checks the observed result of its last action before deciding whether another waiting iteration is allowed. Each navigation/dismissal step has ten seconds and file confirmation has five seconds; a completed action advances the finite state machine and starts only that next step's budget. It does not repeat actions or retry an entire workflow. Helper elapsed time is reported separately from those per-step conditions.

The retained Vanilla workflow uses the existing format/document-picker helpers. Their visible-choice or file-selection failures remain reported as failures; they do not establish corrupt Sketch payloads. Investigate those helpers only when that dedicated workflow needs them. Do not reintroduce a shared picker gate for production content checks.

A visible emulator window can still show a stale tablet frame while Android or adb is unresponsive. Record that separately from a confirmed hard crash. A user-reported restart may restore a snapshot with the same guest boot ID; unchanged identity alone neither disproves the restart nor establishes healthy UI. Recovery checks also verify bounded transport/window queries and a fresh compositor capture before device validation resumes.

The `fast` validation target is less than 120 seconds on the development host with the emulator running. This covers JVM execution, discovery, fixtures, tests, and cleanup; build/install are separate. A validation overrun fails the command and retains coverage. Use `command.json` for actual start-to-finish time, including preflight, build/install, evidence transfer and reporting. The target is not a guarantee of whole-command duration. Ordinary changes need one relevant run, not three timing runs. If runtime consistency is the subject of the work, use a bounded, relevant experiment and report every sample, including interruptions and recovery time.

## Visual baseline procedure

Normal validation cannot record or replace approved baselines. The authoring runner requires an explicit authoring flag, and `refresh` stages candidates by default:

```powershell
.\scripts\refresh-visual-baselines.ps1 -Serial emulator-5554 -CandidateDirectory review-artifacts/my-candidates
# Inspect my-candidates/review.html, candidate images, expected images, and diffs.
.\scripts\refresh-visual-baselines.ps1 -Apply -CandidateDirectory review-artifacts/my-candidates
# Recompile the Android test APK so validation uses the newly approved assets.
```

Candidates require passing structural checks and three identical captures from the current invocation. Old artifacts left by previous runs cannot qualify. The manifest records candidate and previous-approved hashes; `-Apply` refuses modified candidates or approved assets changed since staging. A later unrelated assertion failure does not invalidate an independently verified screen; such a run still reports its failure.

The Demo PNG case performs three independent full-size exports during authoring. Each must finish with a valid PNG end marker, contain bright drawing strokes in every vertical third, and produce identical pixels when decoded at sample size eight. Its approved asset stores that lossless sampled comparison image; ordinary validation still writes the full-size export and compares its sampled pixels. Approved PNGs missing their end marker are rejected before decoding. The candidate report labels an incomplete old asset explicitly rather than treating its partial decode as valid.

Validation output and authoring candidate directories must be new or empty. Reusing a nonempty directory is refused so previous failures and authoring events cannot be overwritten or mistaken for current evidence. `-Apply` consumes an existing reviewed candidate directory.

Screens compare canvas, visible symbol rows, preset row, and style row independently. Each region allows channel differences up to eight and at most 0.5% changed pixels. Canonical strokes also have tight regions, committed-object counts, and foreground-ink checks. Negative controls reject missing one/all strokes, wrong weight, missing rows, and incorrect selection highlights. Fixed model curves are rendering fixtures; smoke dispatches real input and checks the committed model after storage reload. Legend/contact-sheet checks include approved representative image crops and glyph-level assertions.

Render hashes without an approved comparison are diagnostic reports. Production water/clay ordering assertions remain correctness checks. The production rock renderer and its existing field-performance thresholds remain covered. The private prototype benchmark has been retired, as have held-navigation/stylus cases. Do not add replacement stylus cases.

## Regressions and handoff

Do not add ignore markers, reduce assertions, change production behavior, or accept baseline changes to conceal confirmed app defects. Keep failing regression methods and their exact reproduction commands in the handoff report. Report the scope actually executed, every failed/blocked check, the APK identities and whole-command duration. A scoped pass is not a complete-suite pass, and an older passing APK does not validate changed code.

Run-specific counts, timings, approved-asset identities, failures and exact reproductions belong in each run's report, not this procedure. Preserve previous failures and distinguish a new scoped validation from a complete-suite result.

Installed-Vanilla import visibility/round-trip failures remain known, deferred compatibility work. Keep the assertions and report failures when that coverage is explicitly requested; do not reset Vanilla, retry into green, or add import-list refresh workarounds. Use `full -Feature vanilla -Selector 'com.topodroid.TDX.PhysicalCompatInstrumentedTest'` or `physical -PhysicalSerial <explicit-serial>` only when investigating that path. `all` always requests it.

After feature/fix work, compile both APKs. If the Samsung field tablet is connected, follow [Physical Tablet Quick Refresh](physical-tablet-quick-refresh.md) exactly. This separately authorized completion step clears package-owned preferences/device/calibration/symbol data, seeds the Tester profile, grants permissions, and launches the debug app. It must preserve the public survey database and files. Report build, device detection, installation, and launch results.
