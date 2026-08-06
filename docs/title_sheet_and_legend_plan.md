# Title Sheet & Legend point symbol

Final implementation plan. It reconciles the original proposal, the findings in
[title_sheet_and_legend_plan_review.md](title_sheet_and_legend_plan_review.md), and the team's
[response](title_sheet_and_legend_plan_thoughts.md). The decisions below supersede the open
questions and alternatives in those two companion documents.

## Context

Cave maps need a legend. Today TopoDroid Sketch has none — `docs/roadmap.md:113` lists it as future work ("Legend, sketch info, etc viewport style box to use with the png export"), and `docs/text-object-overhaul-plan.md:37` deliberately deferred the abstraction until "a second caller exists". The text-object overhaul has since landed (`SketchTextStyle`, `SketchTextLayoutSnapshot`, `SketchTextRenderer`), so the second caller is arriving now.

The feature: a point symbol the sketcher drops on the map that renders a lineless table of boxes — each box holds a symbol swatch, with that symbol's name beside it — populated from the symbols actually used in the sketch, then hand-tuned in a dedicated editor. It pans and drags like any other symbol and appears in PNG export. A "Title Sheet" tab is reserved beside it for a later release (cave name, team, station counts pulled from survey metadata).

Target look: `files_for_codex/rendered_legend.png` for the rendered product, `files_for_codex/title_sheet_and_legend_handdrawn_mockup.jpg` and `files_for_codex/legend-mockup.html` for the editor. The plan is normative where the HTML differs: its red capacity fields become the amber non-blocking warning in §4, and its live Legend switch becomes the checked-disabled v1 control in §7.

**The good news:** this fork already has a purpose-built extension framework for exactly this shape of feature — `SpecialPointBehavior` / `DrawingSemanticPointPath` / `SpecialPointEnvelope`, shipping today for passage-height, pit-depth and bedding-attitude. Placement, drag, `.tdr` persistence, private-option stripping and bbox refresh all come free. Almost all new code is the legend's own model, layout, renderer and editor.

## Decisions

| Question | Decision |
| --- | --- |
| Freshness | **Snapshot + Rescan.** Table captured at placement. User edits are sacred. Rescan merges newly-used symbols in and flags rows whose symbol is no longer used — never silently deletes. |
| Capacity | **Requested shape + lossless render.** Requested columns and rows-per-column persist unchanged. Capacity never blocks Save. If needed, layout derives additional rendered columns; see §4. |
| Custom rows | **`+ Custom row`:** a labelled row with an empty swatch. Attached artwork is explicitly not a v1 feature. |
| Line weight | **The configured Standard style by default, per-row override in the row sheet.** No sixth column. The legacy XS/S/M/L/XL radio group elsewhere is untouched. |
| Section switches | **Legend stays enabled in v1.** Its switch is shown checked but disabled until Title Sheet has renderable content, avoiding an unrecoverable invisible point; see §7. |
| Heading and labels | **A fixed, localized “Legend” heading.** Row labels honor explicit newlines and also wrap to a bounded width. The heading is not editable or hideable in v1. |
| Export surface | **PNG is the supported visual export.** Compass `.dat` and `.csv` remain unchanged. PDF receives canvas-path parity and a smoke test; SVG/DXF receive only cheap visible-marker suppression, not vector legend output; see §8. |
| Undo | **No editor undo/redo.** Save/Cancel transaction only, matching every other property dialog. |

---

## 1. Two separate models: canonical usage vs. legend presentation

The legend is **presentation state and nothing more**. It must not become the project's record of what is in the cave: users delete common symbols, add symbols not present in the plot, rename symbols to non-standard meanings, create custom rows with no canonical identity, and may place two deliberately disagreeing legends on one plot. It also carries no occurrence coordinates, so it can never answer "gypsum near A1, A3, A20".

So build two things:

**`PlotSymbolUsageSnapshot`** — immutable, derived on demand from drawing commands, never persisted. Each occurrence carries symbol kind, canonical full Therion name, scene coordinates, and scrap identity (nearest-station resolution comes later). The legend consumes an *aggregated* view (distinct symbols ordered POINT, LINE, AREA, then by each installed library's display order); a future resource-inventory tool consumes the *occurrence* view.

**`TitleLegendPointState`** — persisted, presentation-only.

The same separation applies to the Title Sheet later: survey name, team and station counts stay canonical survey data; the point stores which fields to show, their formatting, and explicit user overrides — never a second copy of the survey record.

### Scanner mechanics

`DrawingCommandManager.mScraps` is `private` (`DrawingCommandManager.java:99`), so the snapshot builder lives **inside** `DrawingCommandManager` and is reached through a narrow accessor on `SpecialPointPlacementContext` / `DrawingWindow`, in the style of `computeNearestStationLrud`.

Do not resolve a mutable library index against a different library generation. Capture the symbol-library generation, then—using the existing render lock order, `mSyncScrap` followed by `TDPath.mCommandsLock`—copy only symbol kind + library index and occurrence coordinates into a local array. Release both locks, resolve names, and verify the generation is unchanged. Retry from a fresh snapshot if it changed. Use a bounded retry and reschedule rather than spinning during a library reload. Never hold the commands lock across name resolution or layout work — that lock is contended by the render thread, and acquiring symbol-library locks beneath it would create a lock-order hazard.

Scope is the **current plot**, all of its scraps. Collected: `DrawingPointPath.mPointType`, `DrawingLinePath.mLineType`, `DrawingAreaPath.mAreaType`. Excluded: the legend point itself, `DrawingLabelPath`, and `BrushManager.isPointMedia` / `isPointSection` / `isPointReference` / `isPointPicture` — photo, audio, picture, section-viewport markers and reference images are map furniture, not cave symbols. A cross-section *line* drawn in the plot is a legitimate legend entry; the section *viewport marker* is not.

Placement uses the snapshot to create the initial rows. Later editor opens take a fresh snapshot only to resolve symbols and compute `USED` / `NOT_USED` badges; they do not silently append rows. The explicit Rescan action merges newly used identities into the draft, respects tombstones and never deletes or reorders an existing row.

---

## 2. Persisted state model

Immutable value object, JSON-encoded into the `-tdx-special` private option by the existing `SpecialPointEnvelope` (`SpecialPointEnvelope.java:57`). **No `.tdr` record change and no new record tag.** Current TopoDroid Sketch preserves this envelope in TDR and strips `-tdx-*` from its external drawing exports. Do not claim that older Sketch or vanilla TopoDroid round-trips preserve the object: an installation without the symbol can remap it to `user`, and older code does not share all of this fork's private-option behavior. Archive and cross-version behavior therefore gets explicit tests and release notes (§12).

### Root

```text
objectId        stable generated ID, reserved now for future attached ink / cross-references
titleSheet { enabled }      default FALSE — nothing is rendered for it in v1
legend     { enabled, requestedColumns, rowsPerColumn, textStyle, rows[], dismissed[] }
```

`legend.enabled` defaults to `true` and is validated/encoded as `true` in v1. The UI reserves its future switch but cannot create the all-disabled state. Use descriptive names (`requestedColumns`, `rowsPerColumn`) in Java even where encoded keys are compact. `renderedColumns` is derived layout state and is never persisted.

On initial placement, scan first, default `requestedColumns = 2`, and default `rowsPerColumn = max(1, ceil(initialRowCount / 2))`. This produces a valid two-column starting shape without empty trailing columns. Those values become user-owned immediately; later Rescans never normalize them.

### Row

```text
rowId           stable generated ID
kind            POINT | LINE | AREA | CUSTOM
thName          canonical full Therion name (null for CUSTOM)
fallbackLabel   the symbol's default name, captured at add time
userLabel       nullable override
preview {       weightMode (STANDARD | CUSTOM), customWeight,
                pointScale, orientation, textValue,
                specialPayload (versioned, for special points such as pit-depth) }
resolutionStatus  derived: RESOLVED | UNRESOLVED_SYMBOL
usageStatus       derived: USED | NOT_USED | NOT_APPLICABLE
```

Design points:

- **Identity is `kind + full th-name`, never a library index.** Indices shift whenever a symbol is added or renamed — the water/clay alphabetical shift already forced `RenderIdentity` to pin areas by `SymbolLibrary.WATER/CLAY`.
- `fallbackLabel` is *captured*, not derived on the fly. If the symbol is uninstalled or the library reloads, the row still shows a meaningful name. `userLabel` wins when present; otherwise the row shows the live symbol name if resolvable, else `fallbackLabel`.
- **An unresolved symbol is never discarded** during decode or Rescan. Keep the row, show `fallbackLabel`, render an explicit unavailable/empty swatch, and mark it distinctly from "installed but not used in this plot" — those are different problems with different fixes.
- `preview` is what makes the row editor's promises real. `STANDARD` resolves through the current user-configured Standard sketch style; a custom override persists its explicit value. The earlier schema stored only text and weight while the editor offered orientation and special-point editing.
- `dismissed` holds tombstones (`kind:thName`) so Rescan never resurrects deleted rows. Re-adding a symbol through `+ Symbol` removes its tombstone.
- Resolution and usage status are runtime-derived, not persisted. `CUSTOM` is `NOT_APPLICABLE`; a canonical row is `USED` only when the fresh plot snapshot contains its identity. A `NOT_USED` badge is informational whether the row became stale or was deliberately added from the picker; it never deletes or disables the row.

### Validation and atomic commit

The 120-row cap alone does **not** protect `DataOutputStream.writeUTF` — free-form labels, values and tombstones can still blow the 65,535-byte modified-UTF ceiling. Save runs in strict order:

1. build and validate a complete immutable state;
2. encode the complete candidate `mOptions` string;
3. validate encoded modified-UTF length (`SketchTextInput.fitsModifiedUtf`, as `DrawingTextDialog.java:372-386` does);
4. prepare the matching render layout (§5);
5. **only then** publish state, options, layout and bounds as one commit.

Decode enforces the same limits so a malformed or hostile TDR cannot allocate an enormous legend before the editor opens: at most 120 rows and 240 tombstones; at most 256 Unicode code points in any user-facing label/value or canonical symbol name; at most 64 in generated IDs; numeric fields finite and inside their documented ranges; only supported row kinds. Normalize CRLF/CR to LF before counting/encoding. These per-field limits complement, rather than replace, the final modified-UTF check.

**Version policy:** `decodeState` accepts every version from 1 through current and upgrades older payloads in memory. Unknown keys inside a supported version are ignored and are not promised to survive a later edit. A payload whose version is newer than the app is preserved byte-for-byte but not edited or re-encoded; show an unavailable/newer-version marker and a read-only explanation instead of silently resetting it. The existing special points use `if (version != STATE_VERSION) return null` (`PitDepthPointBehavior.java:33`), which would silently blank a user's legend on the first schema bump. Do not copy that.

---

## 3. Fill order and geometry

**Column-major: down, then right.** This matches `rendered_legend.png` and makes Up/Down operate on one understandable global sequence.

With multiple columns, distribute as evenly as possible while preserving that sequence and never exceeding `rowsPerColumn` — seven entries in three rendered columns gives `3 / 2 / 2`, not `3 / 3 / 1`. `requestedColumns` is the preferred populated width, not a hard maximum. Fewer entries produce fewer columns, with no empty trailing columns rendered; insufficient requested capacity derives additional rendered columns under §4.

Visual geometry:

- **Rectangular swatch boxes**, roughly 3:2 to 2:1 — both visual references use wide boxes, not the squares the previous revision specified.
- **Render the localized "Legend" heading.** Both targets show it. It is fixed and visible in v1 rather than persisted as presentation state.
- **Bound label width.** Honor explicit line breaks, then word-wrap each resulting paragraph at a fixed v1 maximum of 18 resolved text line-heights, with character-level fallback for a single overlong token. Row height grows to fit. Unbounded single-line labels defeat the column-shaping controls and can produce enormous PNG extents.
- Boxes only — no grid lines between rows or columns ("lineless table of boxes").
- Text through `SketchTextRenderer` at `SketchTextStyle.SizeMode.AUTO_GRID` by default, so the legend scales with the map like a printed legend should.

**Anchor:** top-left composition anchor. Rescan and future Title Sheet growth then extend down and right without moving already-aligned content. (`cx, cy` remains the drag anchor; the table is laid out from it.)

---

## 4. Capacity — requested shape, lossless render

The editor stores the user's request, while the prepared layout derives what it must actually draw. For the `n` included rows (resolved, unused/stale, unresolved and custom all count; the heading and tombstones do not):

```text
requiredColumns = ceil(n / rowsPerColumn)
renderedColumns = n == 0
  ? 0
  : max(min(requestedColumns, n), requiredColumns)
expanded = renderedColumns > requestedColumns
shortfall = max(0, n - requestedColumns * rowsPerColumn)
```

`requestedColumns` and `rowsPerColumn` are integer steppers with a validated range of 1…120. They are persisted exactly as shown. `renderedColumns` and `expanded` live only in the immutable `TitleLegendLayout` and are recomputed from state.

Capacity is a non-blocking relationship warning, not invalid data. When `expanded` is true, show an amber warning icon and message (not red `EditText.setError`, and not color alone):

> **Will render with 3 columns.** 7 entries do not fit in the requested 2 columns at 3 rows per column. Your requested values will be saved unchanged. To render 2 columns, remove 1 entry or increase Rows per Column to 4.

Capacity **never blocks Save and never mutates the requested values**. Save commits `2` and `3`, prepares a three-column layout, and closes normally. Reopening shows `2` and `3` again with the same warning. Deleting one row immediately produces six entries, a two-column `3 / 3` layout, and no warning. Rescan, `+ Symbol`, deletion and stepper changes all recompute this preview live.

This guarantee is specific to capacity. Save may still report a genuine blocking persistence failure, such as malformed/out-of-range data or a state that exceeds the modified-UTF ceiling. Those failures preserve the draft and explain how to correct it; they never silently discard rows. A large but valid requested shape is still saved. PNG allocation is preflighted separately at export time, so a whole-plot bitmap that is too large can fail clearly without turning layout preference into a Save blocker.

Fill consumes the single `rows[]` list column-major. If `base = n / renderedColumns` and `extra = n % renderedColumns`, column `i` gets `base + (i < extra ? 1 : 0)` rows. Examples: `7, requested 2, cap 3 → 3 / 2 / 2`; `6, requested 2, cap 3 → 3 / 3`; `4, requested 3, cap 6 → 2 / 1 / 1`; `2, requested 5 → 1 / 1` with no empty columns.

---

## 5. Rendering

### Swatch rendering — split `SymbolPreviewRenderer`

The existing class is excellent for picker buttons and **must not be embedded directly in a world-space composite object**. Concrete conflicts:

- padding is display-density-scaled dp inside the supplied rect, while legend swatches are measured in scene units;
- it hardcodes one Standard `SketchBrushStyle` and cannot take per-row weight, orientation, text/value or special-point state;
- it *retains* mutable `DrawingPointPath` / `DrawingLinePath` / `DrawingAreaPath` instances (`PointScene.mPoint`, `LineScene.mLine`, `AreaScene.mArea`), and line/area drawing mutates cached paint state — so one retained preview can race between scene-cache rendering and PNG export;
- `draw()` allocates a `RectF` and `Matrix` per call, i.e. per row;
- it carries no `xor_color`, so swatches would disagree with their boxes and labels in inverted on-screen rendering.

Split it into:

1. **an immutable, density-free, scene-space swatch snapshot** — the geometry and paint state needed to draw one swatch into a scene-space rect, safe to share across the render thread, the scene-cache thread and export threads, and accepting `xor_color`;
2. **the existing widget adapter** — `SymbolPreviewButton` plus the dp padding and button behaviour, layered on top.

The legend layout holds swatch *snapshots*, never live drawing paths.

"Standard weight" means `TDSetting.getSketchStyle(TDSetting.SKETCH_STYLE_STANDARD)`, not the factory constant. Its generation/value is a layout invalidation input, so changing the configured Standard style refreshes default swatches. A row in `CUSTOM` weight mode remains frozen until its override is reset.

### The picker glyph

`DrawingPointFactory.createPreview` intercepts registered special points (`DrawingPointFactory.java:35-41`), and `SymbolPreviewRenderer.makePointScene` then renders through the point's own renderer when `hasUsableSpecialState()` (`SymbolPreviewRenderer.java:123-127`). Once `title-legend` is registered, its toolbar/picker icon would therefore try to draw a whole legend table.

Add an explicit preview policy — `SpecialPointBehavior.default boolean previewUsesAuthoredGlyph() { return false; }`, checked in `makePointScene` — and have the legend return `true` so the authored 2×3 grid glyph is used.

Also **exclude `title-legend` from the `+ Symbol` picker** so a legend cannot list itself.

### Prepared layout: atomic publication

There is no cache slot or preparation callback today, and `DrawingSemanticPointPath` asks the renderer for bounds during construction and immediately after every state change. So define the lifecycle explicitly.

`TitleLegendLayout` is an immutable prepared object containing: state identity, the measured heading and per-row `SketchTextLayoutSnapshot`s, swatch snapshots, local geometry, **exact local bounds**, derived capacity values, and every generation/input key.

Invalidation inputs: legend state, label style, point/overall scale, grid unit, locale/configuration (for the heading), text font generation, symbol-library/preview generation, point/line/area ink settings, and the configured Standard style.

Preparation rules:

- Prepare **before** publishing editor changes — it is step 4 of the Save sequence in §2. Add a prepared-commit API rather than calling today's `setSpecialState()` and `updatePointObject()` in sequence. Under `TDPath.mCommandsLock`, publish state, fully encoded options, layout and coarse/exact bounds together; mark the drawing modified and invalidate the scene only after that commit. Render, TDR save and export must observe either the old complete snapshot or the new one, never a mixture.
- Add a manager-owned post-load/invalidation coordinator. It snapshots affected legend points under the command lock, prepares outside all command and symbol-library locks, then conditionally commits each result only if its state and generation keys still match. Run it after TDR loading when libraries/fonts are ready, and after symbol-library, font, grid-unit, ink-setting or configured-Standard-style changes.
- A loaded point with no prepared layout draws the small authored legend glyph as a temporary unavailable/loading marker and uses its small conservative bounds. Normal UI rendering may keep the last valid layout while a global invalidation rebuilds. PNG/PDF export waits for the current preparation generation (or reports a clear export failure) before computing bounds.
- A renderer must **never** schedule UI work from `draw()` or `computeBounds()`. The earlier "draw the stale layout and post a rebuild" idea is withdrawn.
- Preparation must not run on the render thread: `SymbolPreviewRenderer.measureInkBounds` allocates a shared 256×256 probe bitmap under a global `CACHE_LOCK` (`SymbolPreviewRenderer.java:216`), which the render, cache and export threads all contend for.
- Each legend draw may create one invocation-local scratch bundle (`Paint`, `Path`, `Matrix`, `RectF`) and reuse it across every row. No temporary object is allocated per row. Nothing mutable is shared between concurrent screen, scene-cache, PNG and PDF draws; verify the O(1)-allocation claim with the 120-row performance fixture rather than relying on wording alone.

`FramedTextPointRenderer.java` is the closest existing template for "box plus measured text", but **do not copy its per-frame `layout()` call** — it rebuilds every `SketchTextLayoutSnapshot` and `Paint` on every draw. Tolerable at one row; not at twenty.

### Bounds

`DrawingSemanticPointPath.refreshSpecialBounds()` converts dynamic bounds into a symmetric square. That is correct-but-conservative and must stay as the coarse `DrawingPath` RectF, because `mLandscape` is not known at refresh time (it is stamped later, in `Scrap.drawAll`). Leave the shared helper alone — changing it would put the bedding-attitude render hashes at risk.

The legend additionally exposes **exact local bounds** through its own accessor. Callers pass the presentation orientation explicitly and rotate those bounds when landscape is active. Do not rely on the path's last render-stamped `mLandscape`: export bounds are computed before drawing. Add an exact-intersection hook after `DrawingSemanticPointPath`'s coarse-square rejection, then thread orientation through the relevant manager/scrap operations:

- render culling (a five-column legend must not force a draw when nowhere near the viewport);
- PNG/PDF extents via an orientation-aware `DrawingCommandManager.getBitmapBounds` → `Scrap.getBitmapBounds` path (the existing `DrawingPath.computeBounds(RectF, boolean)` boolean is **not** an orientation flag);
- body hit-testing;
- selection outline;
- multiselection.

Every prepared-layout replacement, including transition to zero rows, must replace rather than union with old bounds; otherwise a previously large legend leaves stale PNG/PDF extents.

---

## 6. Selection, hit-testing and drag

`Selection.insertPath` indexes only a point at `(cx, cy)` for `DRAWING_PATH_POINT`, but the legend's footprint is a large rectangle. `Scrap.getItemsAt` (`Scrap.java:319`) already runs a text-overlay rectangle pass and an affine-silhouette pass before the bucketed index — add a third.

- Thread `DrawingCommandManager.mLandscape` through `getItemsAt` / `addItemAt` into the matching `Scrap` calls. Add the pre-pass there, testing `DrawingPointPath.hitSpecialBounds(x, y, touch_slop, landscape)` (default `false`, overridden on `DrawingSemanticPointPath`). Add `SelectionSet.removeDuplicateSpecialItems()` alongside the existing dedupe calls, so a tap hitting both the body and the `(cx, cy)` anchor yields one selection.
- The hit test un-rotates by the explicit landscape quarter-turn, mirroring `DrawingLabelPath.effectiveOrientation()` + `SketchTextRenderer.hitTest` (`SketchTextRenderer.java:116`); it never trusts a render-stamped orientation value.
- **Rebucketing on drag is required.** A body hit produces a *transient* `SelectionPoint`, so the canonical indexed point is left in its old bucket. `Scrap.shiftHotItem` already handles exactly this for `DrawingLabelPath` (`Scrap.java:2921-2926`, with a comment stating the problem); add the same `mSelection.rebucketPath(path)` + `selection_fixed.shiftPathPointsBy(...)` branch for the legend.
- Selection outline: add a branch in `Scrap.drawSelection` (`Scrap.java:3931`) drawing the table rect, beside the existing `DrawingLabelPath.textBoundsPath` branch.
- `DrawingSurface.shiftHotItem` already pairs `staleBlitWindow(STALE_BLIT_GESTURE_MS)` with `requestSceneRender()` (`DrawingSurface.java:1521`) — the documented anti-lag contract.

---

## 7. Editor UI

Too large for the `R.id.special_point_extension` slot in `drawing_point_dialog.xml`, and the generic point dialog's scale radios, orientation seekbar, raw options and levels are all irrelevant here. So: a dedicated dialog, with a small framework extension.

Framework changes (three small files):

- `SpecialPointPlacementAction`: add `OPEN_DEDICATED_EDITOR`.
- `SpecialPointBehavior`: add `default Dialog createDedicatedEditor( DrawingWindow parent, DrawingSemanticPointPath point ) { return null; }` and `default boolean previewUsesAuthoredGlyph() { return false; }`.
- `DrawingWindow`: honour the new action in `finalizePointPlacement` (`DrawingWindow.java:6756`), and check for a dedicated editor in the edit-properties dispatch before falling through to `new DrawingPointDialog(...)` (`DrawingWindow.java:8690`).

### Structure

`DrawingTitleLegendDialog extends MyDialog`, genuinely full-height:

- fixed segmented section control at the top, with **Legend selected on every open** (tab selection is transient, not persisted);
- **one `0dp` / weight-scoped scrolling body** containing the table, add controls, layout settings and the formatting button;
- fixed Save/Cancel footer **outside** the scrolling body;
- `adjustResize` so a focused name field stays visible above the keyboard.

The HTML mockup shows a fixed footer, but its body is not actually bounded — the Android layout must make this structural rather than incidental to content height.

### Controls

**Use real checked controls.** AppCompat is not required to use `android.widget.Switch` at this project's `minSdk 21` — it brings touch, keyboard, RTL, saved state and accessibility behaviour that a hand-rolled `View implements Checkable` would have to recreate. Tint a platform `Switch` (or a small subclass) with `TDColor.SKETCH_TOGGLE_*`. Likewise build the segmented bar from checked `CompoundButton`/`RadioButton` semantics, reusing the existing toolbar *colours* but not its undersized touch geometry.

Extract the reusable bar as `com.topodroid.ui.SegmentedToggleBar` so the preset/style bars (`DrawingWindow.java:3294-3416`) can be refactored onto it later.

### Table

A `LinearLayout` of row views inside the scrolling body — deliberately not `ListView`/`RecyclerView`, since counts are small (≤120, typically ≤20) and view recycling around per-row `EditText`s reliably produces text-bleed bugs.

Row: swatch | edit pencil | name `EditText` | up/down triangles (equilateral, packed tight) | delete.

Touch and accessibility:

- every actionable target ≥48dp, though the visible pencil/triangle/× may be smaller;
- whenever a switch becomes active, its whole row is tappable; the disabled v1 rows expose the helper explanation to accessibility services;
- Up disabled on the first row, Down on the last;
- keyboard focus preserved across a move;
- dynamic descriptions — "Move Folia up", "Remove Folia";
- announce reorder position and changes to the rendered-capacity warning;
- **do not dim the whole stale row** — keep normal control contrast and use a warning icon/badge;
- **do not use red/error styling for expanded capacity, and never rely on color alone** for any warning.

### Row editor

Curated for a legend swatch — not the generic point dialog:

- special-point text/value where applicable;
- orientation where it communicates the symbol;
- compact continuous stroke-weight slider with a numeric readout, a snap point labelled Standard, and Reset;
- live swatch preview.

It reuses the same underlying controllers/options where practical, so a pit-depth row gets the familiar value controls, but edits only the legend row's representative preview. It never mutates any occurrence in the sketch—there may be many occurrences with different values—and a newly scanned row starts from that symbol's default preview state rather than an arbitrary occurrence. No levels, raw options or placement-only controls.

### Label formatting

Shares the full `SketchTextStyle` infrastructure used by text objects: font, emphasis, size, colour, opacity, live sample. It formats every row label; the fixed localized heading derives the same style at `1.15×` the resolved row-label line height. **Alignment is fixed left** — the table owns alignment. **Screen-fixed sizing is unavailable** — the object and its PNG output are world-space.

### Symbol picker

`ItemPickerDialog` is coupled to `ItemDrawer` (an Activity base class) via `itemPickerSelected(...)`, so build a dialog-friendly `LegendSymbolPickerDialog` providing: Points / Lines / Areas checked tabs; search by displayed *and* canonical name; production swatches at field-friendly size; already-in-legend indication; installed symbols only; multi-select with a fixed `Add (n)` action. Do not reuse the symbol-enable management adapter verbatim — its checkbox/edit/group columns serve a different task.

### Custom rows

`+ Custom row` focuses a label field, renders an empty swatch and shows a one-time in-editor note that artwork is not attached in v1. Keep the pencil in its column for alignment but disabled, with an accessibility explanation. Do not encourage detached hand-drawn ink that will not move with the legend. When attached artwork arrives, it hangs off the reserved `objectId` / `rowId`, never off inferred geometry.

### Lifecycle

All editor changes act on a **draft**. Removing a row, reordering, and Rescan change only the draft.

- Save runs blocking persistence/safety validation, evaluates capacity as a non-blocking warning, prepares the candidate layout, then uses the atomic prepared-commit contract in §5. Capacity can never keep this button from closing the editor.
- Cancel and system Back share one semantic: **on initial placement, delete the unconfigured point** (mirroring `DrawingWindow.cancelUnconfiguredBeddingPoint`, `DrawingWindow.java:6730`); **on a later edit, discard the draft** and leave the saved point untouched. Disable outside-tap dismissal for this full-height, multi-field editor so a stray tap cannot discard a large draft.
- Tapping × removes a row from the draft and recomputes order, capacity and preview immediately. A canonical deletion adds its identity to the draft tombstones; a custom deletion does not. `+ Symbol` clears a matching draft tombstone before re-adding. Keep a dialog-session cache of removed canonical row objects so re-adding before the dialog closes restores their label and preview overrides; after Save, a later re-add starts from symbol defaults.
- No per-row confirmation, Undo snackbar or editor undo stack. The explicit draft-level Save/Cancel transaction is the protection and matches existing property-dialog behavior.

Consistent with every other property dialog, editor changes do not enter sketch undo/redo. Do not promise otherwise.

### V1 section switches

The Legend switch is present in the intended location, checked and disabled, with helper text: “Legend can be turned off when Title Sheet is available.” The Title Sheet tab remains selectable and shows a disabled off switch plus its “Coming in a later release” explanation.

This deliberately avoids creating a point with no visible or selectable output. `legend.enabled` remains reserved in state and true in every v1 save. When Title Sheet gains real output, enable both switches and either require at least one section to remain visible or add a separately designed screen-only edit marker. That future choice does not burden v1 with an otherwise useless rendering path.

---

## 8. Export boundary

### Supported output

PNG is the only supported visual export for the legend. It renders through the normal canvas path, so `SpecialPointRenderer` covers it. Tests still required:

- extents include the heading and every row, without excessive square-bound whitespace;
- the newest committed layout is used after Save, Rescan, symbol reload and text-style changes;
- scene-space spacing is independent of display density;
- missing symbols and custom rows render predictably;
- long but valid labels stay within guarded geometry limits;
- concurrent scene-cache and PNG rendering is deterministic;
- portrait and landscape produce correct orientation and bounds;
- 1×, 2× and 4× output stays legible and uncropped;
- zero rows and every state/layout replacement clear old bounds instead of leaving a stale large extent.

`xor_color` matters for inverted on-screen drawing; normal PNG export uses the ordinary colour path, so it is not a PNG requirement.

Compass `.dat` and `.csv` are survey-shot data exports, not sketch-object exports. Placing, editing or deleting a legend must not add records, columns, comments or metadata to either. Add one regression test proving output is semantically identical before and after. Any future inventory integration uses an internal structured API or a separately designed export — never a quiet change to an established survey-data schema.

### Narrow compatibility hygiene outside the supported surface

The earlier claim that PDF/SVG/DXF were all a two-line fix was not accurate. Treat them separately:

| Format | V1 behavior |
| --- | --- |
| PDF | `DrawingWindow`/`OverviewWindow` already draw onto `PdfDocument.Canvas` through `DrawingCommandManager.executeAll`, so the complete legend follows the canvas renderer with no legend-specific serializer. Keep this best-effort parity and add a focused pixel/extents smoke test, including the inverted-colour path. This does not expand the documented supported-export surface. |
| SVG | Add one guard in the shared `DrawingSvgBase.toSvg` path so the authored 2×3 fallback glyph is not emitted as though it were the legend. This covers grouped, ungrouped, Walls and embedded-TDR point emission. It can still leave an unused marker definition, empty group or legend-influenced document bounds. |
| DXF | Add an early guard in the main point branch (covering the direct ACAD9 `INSERT`) and a guard in the point `toDxf` helper (covering ACAD13/14 and embedded TDR). It can still leave unused layers/blocks and legend-influenced extents. |

Use one public `TitleLegendPointBehavior.isTitleLegend(DrawingPointPath)` identity helper for the SVG/DXF guards rather than duplicating magic strings. Test that no visible title-legend entity is emitted in grouped/ungrouped SVG, current/legacy DXF and embedded-section paths. These guards are deliberately described as **visible-marker suppression**, not SVG/DXF legend support. A complete omission fix would also filter exporter bounding boxes and unused definitions; actual vector legend support would serialize boxes, wrapped text and every swatch. Both remain follow-ups.

Therion, SHP, XVI and cSurvey receive no v1 changes or behavior promise. Their existing fallback is format-specific; do not summarize them all as an “anchor-only marker.” Record this limitation without implying that the legend is represented there.

### TDR

`-tdx-special` is internal sketch persistence and archive integrity, **not** a supported external drawing export. Test normal Sketch-to-Sketch save/load and archive restoration, malformed/newer state, missing installed symbols, state-size limits, and the documented limitations of a Sketch → vanilla TopoDroid → Sketch physical round-trip.

---

## 9. Files

**New**

| Path | Purpose |
| --- | --- |
| `symbols-git/symbols_topodroid_sketch/point/title-legend` | symbol definition (`th_name u:title-legend`, `orientation no`, authored 2×3 grid glyph) |
| `src/com/topodroid/TDX/TitleLegendPointBehavior.java` | `SpecialPointBehavior` |
| `src/com/topodroid/TDX/TitleLegendPointState.java` | immutable state + rows + JSON codec + validation |
| `src/com/topodroid/TDX/TitleLegendPointRenderer.java` | `SpecialPointRenderer` |
| `src/com/topodroid/TDX/TitleLegendLayout.java` | immutable prepared layout + exact bounds |
| `src/com/topodroid/TDX/TitleLegendPreparationCoordinator.java` | post-load/global-invalidation preparation and export readiness |
| `src/com/topodroid/TDX/PlotSymbolUsageSnapshot.java` | canonical occurrence snapshot |
| `src/com/topodroid/TDX/SymbolSwatchSnapshot.java` | immutable scene-space swatch core |
| `src/com/topodroid/TDX/DrawingTitleLegendDialog.java` | the editor |
| `src/com/topodroid/TDX/LegendRowEditDialog.java` | curated per-row editor |
| `src/com/topodroid/TDX/LegendSymbolPickerDialog.java` | dialog-friendly searchable picker |
| `src/com/topodroid/ui/SegmentedToggleBar.java` | reusable checked segmented bar |
| `res/layout/…` | `drawing_title_legend_dialog.xml`, `legend_row.xml`, `legend_row_edit_dialog.xml`, `legend_symbol_picker_dialog.xml` |
| `res/drawable/…` | segmented control, equilateral move triangles, delete and warning assets |
| `app/src/test/java/com/topodroid/TDX/TitleLegendModelTest.java` | codec, merge, tombstones, capacity and fill-order tests |
| `app/src/androidTest/java/com/topodroid/TDX/TitleLegendInstrumentedTest.java` | placement, editor transaction, scan, selection, drag and TDR tests |
| `app/src/androidTest/java/com/topodroid/TDX/TitleLegendExportInstrumentedTest.java` | PNG/PDF and SVG/DXF suppression tests |
| `app/src/androidTest/assets/fixtures/title_legend_fixture.zip` | deterministic archive/export fixture |
| `app/src/androidTest/assets/goldens/…/title_legend_*.png` | screen, PNG and PDF-rasterized visual baselines |

**Modified**

| Path(s) | Purpose |
| --- | --- |
| `SpecialPointBehavior.java`, `SpecialPointRenderer.java`, `SpecialPointPlacementAction.java`, `SpecialPointRegistry.java`, `SpecialPointPlacementContext.java` | dedicated editor, authored-preview policy, preparation/context and exact-culling hooks |
| `DrawingSemanticPointPath.java`, `DrawingPointPath.java`, `DrawingPointFactory.java` | prepared commit, exact bounds/hit defaults and authored picker glyph |
| `DrawingCommandManager.java`, `DrawingSurface.java`, `DrawingWindow.java` | stable-generation usage snapshot, preparation/export barrier, orientation-aware bounds, current-manager delegation, placement/edit/cancel and invalidation triggers |
| `Scrap.java`, `SelectionSet.java` | oriented bounds, body hit/add, dedupe, drag rebucketing and selection outline |
| `BrushManager.java`, `SymbolPointLibrary.java` | symbol-library generation and default-enabled title-legend entry |
| `SymbolPreviewRenderer.java`, `SymbolPreviewButton.java` | extract immutable scene-space core and retain widget adapter |
| `TopoDroidApp.java` | approved existing-install symbol delivery (§9) |
| `src/com/topodroid/io/svg/DrawingSvgBase.java`, `src/com/topodroid/io/dxf/DrawingDxf.java` | narrow visible fallback suppression (§8) |
| `res/values/strings.xml`, `res/values/help_pages.xml` | localized UI/accessibility text and help |
| `res/raw/symbols_topodroid_sketch.zip` | regenerated packaged symbol set |
| `TopoDroidSketchSymbolSliceInstrumentedTest.java`, `PhysicalCompatInstrumentedTest.java`, `VisualGoldenInstrumentedTest.java`, `RenderPerfInstrumentedTest.java` | extend existing gates |
| `scripts/test-full.ps1` | run the new hard-coded instrumented test classes |

### Symbol delivery gate

After adding the symbol source file, run `utils/symbols/build_topodroid_sketch_symbols_zip.py` and include the regenerated `res/raw/symbols_topodroid_sketch.zip`. The app installs symbols from that archive (`TopoDroidApp.installSymbols`, `:2225`), not from `symbols-git/`.

Quick-refresh data clearing is sufficient only during development. It is **not** an ordinary-upgrade test: an existing install with a non-empty point directory and an unchanged `SYMBOL_VERSION` will not receive the new file. The recommended release behavior is a one-time, targeted install-if-missing for only `point/title-legend`, with non-overwrite semantics and a completion flag so a user's later deletion is respected. Set the flag only after extraction succeeds or a same-name file already exists; a failed attempt remains retryable. Do not bump `TDVersion.SYMBOL_VERSION` as part of this feature. Because that targeted install is a compatibility path, call it out for explicit approval before implementation; if it is rejected, an explicitly approved symbol-version bump is the alternative. Test clean install, ordinary alpha upgrade, failed/retried extraction, existing same-name user file, and user deletion after the one-time attempt.

---

## 10. Performance contract

Accurate statement:

> At rest, the legend is part of the cached scene. During pan, zoom and drag, stale cached pixels keep input responsive while background renders converge. Expensive layout and swatch preparation never runs in the gesture or draw path.

(The previous "zero per-frame time during pan/zoom" was too strong — gestures continue to request background scene rebuilds.)

The five ways to break it, all avoided by this design:

1. Recomputing text layout per draw → prepared `TitleLegendLayout`.
2. Shared mutable `Path`/`Paint` state → immutable swatch snapshots plus one invocation-local reusable scratch bundle.
3. A wrong bbox — `intersects(bbox)` is the only cull → conservative square as the coarse rect, exact bounds where orientation is known.
4. Drag calling `requestSceneRender()` without `staleBlitWindow()` → the existing `shiftHotItem` path already does both.
5. `canvas.saveLayer` → not used.

Any prepared commit or global invalidation must reach `requestSceneRender()`, or the cache blits stale pixels. Export waits for the matching preparation generation rather than racing a pending rebuild.

---

## 11. Implementation sequence

**Phase 1 — model and contracts.** `PlotSymbolUsageSnapshot` plus generation-safe aggregation/filter logic. Root and row state, stable IDs, limits, atomic encoding and version-tolerant decode. Pure capacity/fill logic. Immutable scene-space swatch API. Prepared-commit, post-load/invalidation coordinator, export readiness and orientation-aware exact bounds. Pure tests against a fake symbol resolver.

**Phase 2 — editor.** Full-height dialog and draft model. Accessible tabs and reserved disabled switches. Rows, reorder, delete, Rescan, custom rows and live non-blocking capacity warning. Searchable picker. Curated row editor and shared label formatting. Every Save/Cancel/Back/initial-placement path.

**Phase 3 — sketch integration and output.** Placement and authored picker glyph. Composite draw, exact selection/culling, rebucketing, drag and bounds. Scene-cache invalidation and library/font/settings rebuild paths. PNG rendering, PDF canvas-path smoke coverage, SVG/DXF visible-marker suppression and `.dat`/`.csv` non-interference. Performance, visual and render-identity gates. Regenerate the symbol archive and complete the approved existing-install delivery path.

**Phase 4 — field acceptance.** Build app + test APK, run the suites, install on the field tablet, then exercise: place, edit, Rescan, create an expanded-capacity draft, Save it, reopen with the original requested values and warning intact, prune until the render contracts to the requested width, save, drag, reselect and export PNG at multiple scales. **Specifically verify the two-column workflow:** seven rows at `2 × 3` render as three columns without blocking Save; reopening still shows requested `2`; deleting to six rows returns the render to two columns and clears the warning.

---

## 12. Verification

1. **Build:** `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`, `JAVA_HOME` = `C:\Program Files\Android\Android Studio\jbr`.
2. **Suite wiring:** add the new instrumented classes to the hard-coded list in `scripts/test-full.ps1`; they will not be discovered automatically. Put a legend test in `test-fast.ps1` only if it proves stable and fast enough to serve as its smoke gate.
3. **Render identity — the gate.** Run `scripts\test-render-identity.ps1`, copy `tmp-test-artifacts` to a named baseline directory outside the script's output path, implement, rerun and byte-compare `render_hashes.txt`. **Existing drawings with no legend must stay byte-identical**, unless a shared-renderer correction from the `SymbolPreviewRenderer` split is intentional and documented. The current script reports hashes but does not perform that comparison itself.
4. **Performance:** run `scripts\test-render-perf.ps1`, plus live `adb shell setprop log.tag.TDRenderPerf DEBUG` / `adb logcat -s TDRenderPerf` on the `scraps` section. Measure the 20-row normal case and 120-row guard case; repeated drag/pan/zoom; **P95 and maximum** latency/convergence; O(1) per-draw allocations; concurrent scene-cache, PNG and PDF-canvas rendering; reload with a saved legend; exact culling of wide-short and narrow-tall layouts; export waiting on a current preparation generation.
5. **Pure model tests:** the exact §4 formula and all four examples; requested values unchanged through Save/reopen; fill order; Rescan merge, draft tombstones and stale flags; supported-version upgrades; ignored unknown keys in supported versions; byte-preserved newer payloads; malformed payloads; per-field/decode limits; modified-UTF ceiling; unresolved-symbol fallback; library-generation retry.
6. **Instrumented behavior tests:** scanner correctness/exclusions; initial placement and every Save/Cancel/Back path; non-dismissible outside tap; checked-disabled v1 switches; atomic commit under concurrent draw/save; post-load preparation; invalidation from symbol/font/grid/ink/Standard-style/locale changes; portrait/landscape body hit; drag → rebucket → re-hit; exact selection/culling; TDR round-trip; PNG allocation preflight.
7. **Output tests:** the full PNG matrix in §8; production-path PDF rasterized with `PdfRenderer` and checked for pixels/extents in portrait and landscape; no visible title-legend entity in grouped/ungrouped/embedded SVG and current/legacy/embedded DXF; one `.dat`/`.csv` non-interference regression.
8. **Visual fixtures:** separate baselines for the authored picker glyph, editor, rendered legend, selection outline, PNG and rasterized PDF. Include point/line/area, custom and unresolved rows, wrapping, custom weight/orientation and `7 → 3/2/2` layout.
9. **Installation and compatibility:** assert the raw ZIP contains a default-enabled `title-legend`; test clean install and ordinary upgrade without clearing data; preserve an existing same-name user file; prove the one-time attempt respects later deletion; round-trip a Sketch archive with `points.zip`; physically exercise Sketch → vanilla TopoDroid → Sketch and document what is and is not preserved rather than assuming it.
10. **Physical tablet:** follow `docs/physical-tablet-quick-refresh.md` exactly (Samsung Tab Active 3, serial `R32X200DN0T`). Report the build result, whether the tablet was detected, and whether install and launch succeeded. If it is not connected, say so explicitly. Quick refresh is a development check, not the ordinary-upgrade test in item 9.

---

## 13. Settled v1 product contract

No product decision above remains open: capacity is non-blocking and lossless; Standard follows the configured Standard style; the heading is fixed; the Legend switch is reserved but disabled; labels wrap after explicit line breaks; user-facing strings are capped at 256 code points; row deletion is one-tap inside the Save/Cancel draft and has no undo stack.

The only pre-implementation approval gate is the release compatibility choice in §9: the recommended one-time targeted install-if-missing versus an explicitly approved `SYMBOL_VERSION` bump. That is called out because it affects existing field testers and user-managed symbol files; it is not left for an implementer to guess.

---

## 14. Explicitly out of v1 scope

Full SVG/DXF composition; exporter-specific definition/bounds cleanup; Therion / SHP / XVI / cSurvey behavior changes; editor undo/redo; the global symbol size/weight dialog overhaul; a live all-disabled section state; editable/hideable heading; detached or attached custom artwork; canonical resource-inventory export.

No TDR record migration is needed for the first state version, but symbol installation and cross-version/archive round-trips still require the explicit delivery path and tests above. Every future state version must continue to decode v1.

## 15. Documentation to hand back

At completion, supply exact proposed wording for: a README "Title sheet and legend" section; a compatibility note that legend state lives in a private option and survives tested current-Sketch archives but is not promised through older/vanilla round-trips; the precise output matrix in §8 (never the inaccurate blanket phrase “anchor-only marker”); CHANGELOG entries for the symbol, editor, reusable segmented-bar widget, installation path and output boundary; and a roadmap update marking the legend item done while retaining Title Sheet, vector legend export, attached custom artwork and the resource-inventory API as future work.
