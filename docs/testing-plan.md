# TopoDroid Sketch — Testing Plan

> Status: draft for review. Scope is the TopoDroid Sketch fork's additions plus the Vanilla↔Sketch compatibility surface. Vanilla TopoDroid internals are out of scope except where a Sketch change touches them.

## 1. Purpose and scope

The goal is a test suite that lets us hand a build to beta testers with confidence in three things, in priority order:

1. **No data destruction** — installing, updating, importing, and exporting never lose or silently corrupt a user's surveys, sketches, symbols, or settings.
2. **Vanilla compatibility** — a Sketch export imports into vanilla TopoDroid (degraded, not broken), and a vanilla export imports into Sketch.
3. **No regressions** — new Sketch features don't break existing Sketch or vanilla behavior.

In scope: the Sketch additions (sketch lines, line presets, the toolbar overhaul, cross-section viewports, reference image, PNG export, the color picker, the in-app symbol editor, S Pen / Active Key / volume bindings, fixed line density, line morphing) and the compatibility surface (ZIP/manifest, symbol version, DB version).

Out of scope for now: vanilla-only features and vanilla internal correctness.

### Current state (baseline)

- Every test is an Android instrumentation test under `app/src/androidTest/` — all require a running emulator.
- There are **no JVM unit tests** (no `app/src/test` source set) and **no CI** (no workflows in `.github/`).
- The instrumented suite is pinned to one exact emulator profile — 2560×1600, 320 dpi, font scale 1.0, English — and fails fast on any mismatch (`VisualTestSupport`, the `2560x1600` / `320` / font-scale guards around lines 1681–1686).
- Coverage leans heavily on full-screen screenshot diffing. The top priority, data safety, is only incidentally exercised by a single ZIP round-trip (`VisualGoldenInstrumentedTest`).

This plan keeps what works, fills the data-safety gap, and re-bases visual testing onto something less brittle.

## 2. Principles

- **Test the invariant, not the pixels.** Where a property is measurable — dash count, stroke width, color at a point, row counts, file presence — assert the property. Reserve image comparison for cases where appearance genuinely is the contract.
- **Data safety is asserted on data, not screenshots.** A round-trip passes because shot/plot/symbol counts and contents match, not because a screenshot matches.
- **Layer by speed and fidelity.** Fast pure-logic tests run on every commit; slow device tests run before a release.
- **Visual correctness stays first-class.** For a fork whose output *is* the rendered sketch, rendering is part of the contract — but it's tested through component-scoped goldens and property assertions, not one fragile full-frame image.
- **A failing test should name what broke.** Prefer many small assertions over one large diff.

## 3. Test layers

### L1 — Fast JVM unit tests (new)

Pure logic, no emulator, runs in CI in seconds. Lives in a new `app/src/test` source set. Targets:

- Version/compat gating: `Archiver.checkManifestFile` / `checkVersionLine` — manifest DB version must fall within `[DATABASE_VERSION_MIN, DATABASE_VERSION]` (currently `[21, 60]`), and the version line must parse.
- Preset model: slot count, naming, rename, and "lowering the slot count hides but preserves definitions." This logic is pure `TDSetting`/prefs and currently sits in `PresetBarInstrumentedTest` — move it here.
- Symbol fallback mapping: a `SYMBOL_VERSION` 45 sketch line resolves to `user-fine`/`user-standard`/`user-thick`, and degrades to a plain `user` line under a vanilla-style read.
- PNG export filename builder (`<survey>_<sketch>_<type>_YYYY-MM-DD.png`).
- Line-symbol serialize/parse round-trip for the extended terms (`sketch_effect`, carriers, rigid stamps, dash-on segments, advance).

### L2 — Data-safety / IO integration tests (the priority-1 core)

Assert **data invariants**, not pixels. Prefer Robolectric (real-ish SQLite plus mockable file paths) so these run in CI; fall back to instrumented only for the system document-picker flow. Detailed cases are in §4 (contract) and §6 (cases).

### L3 — Compatibility contract

Two halves:

- **Automated approximation (CI):** checked-in fixture ZIPs plus a "vanilla-field" parser that reads a Sketch ZIP using only what vanilla 6.4.27 / 604027 understands. It asserts the manifest DB version is ≤ vanilla's ceiling, sketch-line symbols are present as `user` lines, no *required* new columns are introduced, and the manifest/version line parses. This guards regressions cheaply, but it is a *model* of vanilla, not vanilla.
- **Manual matrix (release):** real Sketch and vanilla APKs on a device — a Sketch ZIP imports into vanilla; a vanilla ZIP imports into Sketch; sketch lines render as `user` lines; cross-section viewports degrade without crashing.

### L4 — Visual

Visual correctness stays central; the *technique* changes.

- **Property assertions** where measurable — generalize the existing `LinePatternInstrumentedTest` approach (dash-run count, stroke width at a sample point, centerline color, curve continuity). Robust and self-diagnosing.
- **Component-scoped goldens** — render a single symbol or line to a small fixed-size bitmap (no app chrome) and compare with an SSIM/tolerance threshold. Far more stable than a full screen and not tied to the emulator profile.
- **Integration smoke goldens (small set)** — a couple of full-frame screens plus the PNG-export golden, kept specifically to catch compositing bugs that only emerge at full screen / export (grid-on with transparent background off, north-arrow / scale-bar overlay).
- **Release-time contact sheet** — render every symbol × preset × width to one image for a human to eyeball as a checklist step. Cheap, high-value for a cartographer-facing app.

## 4. Data-safety contract (priority 1)

The bright-line invariants the suite enforces. Each becomes a test.

1. **Import never overwrites or deletes an existing survey** without explicit confirmation. The duplicate-name guard holds on *both* import paths, including the SAF/document-picker path.
2. **Import is recoverable.** Before an import overwrites global state, a snapshot of the symbol libraries and sketch-line prefs is written, and a failed or unwanted import can be restored from it. *(Depends on the snapshot feature — §7 item 1.)*
3. **A foreign import is flagged.** Importing a ZIP whose symbols differ from the current set warns the user; a self-export round-trip does not.
4. **An app update preserves all data.** Upgrading the installed version never drops DB rows or deletes/moves files under `Documents/TopoDroid Sketch/`; `onUpgrade` runs to completion and is safe to re-run.
5. **Storage stays isolated.** All paths resolve under `Documents/TopoDroid Sketch/`; the app never reads or writes vanilla's `Documents/TDX/`.
6. **Export is faithful.** An export contains the data it claims to — not just "a file appeared" (see export-fidelity cases in §6).

Invariants 2 and 3 describe target behavior that does not exist in the code yet (§7).

## 5. Release gate — the seven flows

Must pass before any build goes to beta. Tiered by where each runs.

| # | Flow | Asserts | Runs where | Needs |
|---|------|---------|------------|-------|
| 1 | Fresh install | Starts clean; default symbols/prefs seed; storage dir created under `Documents/TopoDroid Sketch/`; can create a survey | Scripted emulator | — |
| 2 | Upgrade the app | Install QA build vN, seed surveys/plots/settings, install vN+1 over it; all data survives; `onUpgrade` completes | Scripted emulator | two Sketch QA APKs |
| 3 | Side-by-side install with vanilla | Both APKs install (no `CONFLICTING_PROVIDER` / `DUPLICATE_PACKAGE`); separate storage roots; zero cross-contamination | Scripted adb / manual | vanilla + Sketch APKs |
| 4 | Export vanilla → Sketch | A real vanilla ZIP imports; shots/plots preserved; vanilla lines render | Emulator (fixture) | checked-in vanilla ZIP |
| 5 | Export Sketch → vanilla | Sketch ZIP imports into vanilla; sketch lines fall back to `user`; viewports degrade without crash | Manual physical | vanilla APK |
| 6 | Export .png | Golden (tolerance) plus properties: transparent bg where set, grid present, drawn line color present | Emulator | golden PNG |
| 7 | Export Compass .dat | Text golden with date normalization; serializer also unit-tested (L1) | Emulator | golden .dat |

These sort into three execution tiers (see §6): **CI** (L1 + L2 Robolectric, every push), the **scripted emulator pass** (#1, #2, #4, #6, #7), and the **manual physical-device pass** (#3, #5, and the hardware inputs no emulator can drive — S Pen button, Active Key, volume bindings, BT to the Cavway).

## 6. Cadence and coverage

### Cadence

- **Every commit / PR (CI):** L1 unit tests plus L2 Robolectric data-safety tests. Fast, no device. *(CI does not exist yet — §7.)*
- **Pre-release (emulator):** the scripted emulator gate (#1, #2, #4, #6, #7) plus the L4 component/property/visual suite.
- **Pre-release (physical tablet):** side-by-side (#3), Sketch→vanilla (#5), and all hardware-input flows.

### Feature coverage map

| Sketch feature | Layer(s) | Status today |
|----|----|----|
| Sketch lines (fine/standard/thick, widths, color) | L1 + L4 | Partial — visual golden only |
| Line presets (P1/P2/…, rename, slots) | L1 | Covered — move to unit |
| Toolbar overhaul (manual slots, rows, lock) | L1 / L2 | Partial |
| Cross-section viewports | L4 + L3 manual | Gap |
| Reference image (place/scale/rotate/opacity, export) | L2 + L4 | Partial |
| PNG export | L4 + L2 fidelity | Partial |
| Fixed line density / morphing / straight style | L4 property | Partial — `LinePattern` |
| In-app symbol editor | L1 / L2 | Gap |
| S Pen / Active Key / volume bindings | Manual physical | Gap — no emulator path |
| Color picker, grid settings, darken areas | L4 | Gap |
| ZIP import/export + symbols | L2 + L3 | Partial — one round-trip |
| DB upgrade | L2 | Gap |

### Key data-safety / IO cases (L2)

- Import a truncated/corrupt `survey.sql` → prior surveys, the global symbol library, and sketch-line prefs are unchanged (or restorable from snapshot).
- Import a ZIP with differing symbols → warning raised, snapshot written, the user's edited symbols recoverable.
- Import a duplicate survey name → refused on *both* paths (inline and SAF); the existing survey is untouched. This replaces the `forceDeleteSurveyByName` workaround in `VisualGoldenInstrumentedTest`.
- Upgrade from each realistic prior `DATABASE_VERSION` → completes; rows intact; `updateTables` safe to re-run.
- Export fidelity: PNG with grid-on and transparent-off keeps grid lines (guards a known bug); Compass `.dat` content matches; ZIP manifest version and DB version are correct.

## 7. Codebase changes for consideration

Found while reading the import and upgrade paths. These are implementation items, separate from the test plan; several are prerequisites for the data-safety contract above. Ordered by priority.

### 1. Add a pre-import snapshot (deferred beyond Phase 7)

`Archiver.unArchive` decompresses `points.zip` / `lines.zip` / `areas.zip` straight into the **app-wide** symbol directories (`TDPath.getSymbolLineDirname()` and siblings) and then calls `BrushManager.reloadLineLibrary()` and `SketchLineSymbolManager.syncPrefsFromSymbolFiles()`. There is no backup: importing any survey that carries symbols overwrites the user's global symbols and resets the sketch-line width prefs. For a self-export this is the intended round-trip; for a foreign or older ZIP it silently clobbers the in-app-symbol-editor work.

Suggested for a later data-safety/compatibility sprint: before unzipping, copy the symbol dirs and sketch-line prefs to a restore folder, and keep it after import so the user can roll back. Phase 7 intentionally does not implement this because current field testers are alpha users and the NSS brush work needs only graceful vanilla degradation. Guards invariant 2 when revisited.

### 2. Warn on foreign-symbol import

Same code path. There is currently no signal that an import is about to change the user's symbols. Suggested: compare the incoming symbol files / prefs against the current set and, when they differ, warn before proceeding. Guards invariant 3.

### 3. Make import atomic, or at least recoverable

`Archiver.unArchive` writes every entry — `.tdr` plots, notes, photos, symbols — to its live destination as it streams, and loads `survey.sql` into the live DB *last* (`app_data.loadFromFile`). If the SQL load fails (`ERR_SQL`), the files and symbols are already written: orphaned plot files and a mutated symbol set with no survey row. There is no staging directory or rollback. The snapshot in item 1 covers the symbol half; consider also staging plot/note/photo files into a temp survey dir and moving them into place only after the SQL load succeeds. Guards invariant 1 / atomicity.

### 4. Close the duplicate-survey check-then-act gap

The duplicate-name guard lives in `Archiver.checkManifestFile` (it returns `ERR_SURVEY` when `hasSurveyName` is true). But the SAF/document-picker path — `ImportZipTask` → static `Archiver.unArchive(app, fis)` — skips that inline check and trusts an earlier `getOkManifest` call in `MainWindow`. The existing ZIP test documents the resulting race and works around it with `forceDeleteSurveyByName`. Suggested: re-assert the survey-name guard inside the stream import, immediately before `loadFromFile`, so the check and the act are atomic.

### 5. Wrap `updateTables` in a transaction (or guard every step)

`DataHelper.DistoXOpenHelper.updateTables` (around line 8290) is a fall-through `switch(oldVersion)` of additive `ALTER … ADD COLUMN` / `CREATE TABLE` steps up to v60. It is **not** wrapped in an outer transaction, and only some steps guard with `columnExists(...)`. If one `ALTER` throws midway, the version is never bumped (it's set only after the method returns, in `checkUpgrade`), so the next launch re-runs from the same version and hits "duplicate column" on the already-applied steps — a stuck, half-migrated DB. Suggested: wrap the whole migration in a transaction and commit once on success, or make every step idempotent via `columnExists`. Guards invariant 4.

### 6. Reconcile the DB version floor

`DataHelper.checkUpgrade` treats `oldVersion < 14` as a tampered DB, but `TDVersion.DATABASE_VERSION_MIN` is `21` and `Archiver` enforces `21` on import. The two floors disagree. Low risk, but worth aligning so "minimum supported DB" means one thing.

### 7. Test infrastructure (prerequisite for CI)

- No `app/src/test` source set exists — add one so L1 unit tests can run on the JVM.
- No CI — add a workflow that runs L1 (plus L2 Robolectric) on every push. This is what turns "regressions" from "noticed when someone boots the emulator" into "caught on the PR."
- The instrumented suite hard-pins the emulator profile and (per the side-by-side plan) hardcodes `PACKAGE_NAME` / `TEST_PACKAGE` in `VisualTestSupport`; read these from `InstrumentationRegistry…getTargetContext().getPackageName()` so the suite isn't tied to one applicationId.

### 8. Known export bugs the fidelity tests should guard

Already tracked in the README TODO; listed here so the export-fidelity cases (§6) are written to fail until fixed: PNG with grid-on and transparent-off produces a black background (grid lines lost); the north arrow and scale bar can overlay the sketch; station-designation font size doesn't affect export scale.

## 8. Suggested build order

1. Stand up `app/src/test` plus a CI workflow with one trivial L1 test (proves the pipeline).
2. Move the preset and line-pattern logic into L1; add the version/compat, filename, and serialize tests.
3. Build the L2 data-safety core (duplicate-name refusal, upgrade idempotency) against current behavior.
4. Implement the pre-import snapshot and foreign-import warning (§7 items 1–2); add their tests.
5. Add the L3 vanilla-field simulation plus checked-in fixtures.
6. Re-base the visual suite onto property assertions and component goldens; trim full-frame goldens to the integration-smoke set.
7. Script the emulator and physical-device release passes; document the manual matrix.
