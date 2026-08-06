# Review: Title Sheet & Legend Point Symbol Plan

Status: critical plan review  
Date: 2026-08-06  
Reviewed plan: [title_sheet_and_legend_plan.md](title_sheet_and_legend_plan.md)

## 1. Verdict

The feature direction is strong and the existing special-point framework is the right place to build it. The plan is unusually good at protecting user edits, avoiding a new TDR record type, and thinking about rendering cost before implementation. I would approve the direction, but I would not implement the plan unchanged.

The largest required changes are:

1. Treat the editable legend as presentation state, not as the canonical future cave-resource inventory.
2. Replace the proposed direct reuse of `SymbolPreviewRenderer` with a deterministic, scene-space swatch renderer suitable for concurrent canvas and PNG rendering.
3. Define an atomic state/layout/bounds publication lifecycle before adding the composite renderer.
4. Expand the row schema so it can actually preserve the preview edits promised by the editor.
5. Keep Columns and Rows per Column entirely user-controlled; invalid capacity must never cause an automatic layout correction.
6. Remove PDF and structured drawing-export work from v1. PNG is the only visual export in scope, while `.dat` and `.csv` must remain unaffected survey-data exports.
7. Keep editor undo/redo out of v1 and use the same Save/Cancel transaction model as other symbol property dialogs.

These corrections do not require abandoning the special-point design or adding a migration. They mainly tighten the contracts between the model, editor, renderer, and supported export surface.

---

## 2. Decisions that should remain unchanged

The following plan decisions are sound:

- **Snapshot + Rescan.** Capturing a legend at placement and only adding newly used symbols when the user explicitly requests a Rescan protects custom labels, ordering, deletions, and preview edits.
- **Never silently delete stale rows.** A symbol that is no longer used should be identified in the editor, but remain in the saved legend until the user removes it.
- **Current-plot scope.** The legend should scan every scrap belonging to the current plot, not sibling plots or the contents of separate cross-sections. A visible cross-section line may still be a valid legend symbol; a section viewport marker is map furniture and is not.
- **Stable symbol identity.** Symbol kind plus full Therion name is a much better persisted identity than a library index.
- **Private special-point state.** Keeping the state in `-tdx-special` avoids a new TDR record type and database migration.
- **No symbol-version bump.** The normal packaged-symbol refresh process is sufficient during alpha development.
- **Dedicated editor.** This feature is too large for `drawing_point_dialog.xml` and should not expose irrelevant scale, level, raw-options, or orientation controls from the generic point dialog.
- **Standard stroke weight by default, edited per row.** Do not add a sixth table column. Do not fold a global XS/S/M/L/XL editor overhaul into this feature.
- **World-space rendering.** The legend belongs on the map and should scale with the sketch and PNG output, rather than staying fixed to the screen like an interface overlay.
- **Transactional Save/Cancel.** Editor controls operate on a draft. Save commits once; Cancel discards the draft. Cancelling immediately after placement removes the newly placed point.
- **No editor undo/redo in v1.** Property-dialog changes do not currently enter the sketch command-stack undo history. Preserve that convention until symbol editing is redesigned as a whole.

---

## 3. Capacity and layout must remain user-controlled

The original plan makes Rows per Column authoritative and automatically increases Columns when the requested capacity is too small. That conflicts with an important user workflow: the sketcher may deliberately choose a maximum width, then remove low-value entries until the legend fits that shape.

### Required contract

`Columns` and `Rows per Column` are both user-controlled. The editor never changes either value automatically.

The saved layout is valid only when:

```text
columns * rowsPerColumn >= includedRowCount
```

When it is invalid:

- retain exactly what the user typed;
- mark both fields as a relationship error rather than blaming only Columns;
- show the live numbers, for example:

  > 7 entries need 7 slots. 2 columns × 3 rows provides 6. Increase either setting or remove at least 1 entry.

- allow the Save button to be tapped, but validate before committing;
- leave the dialog open, focus and announce the error, and commit nothing;
- never add columns, overflow the final column, hide rows, or drop rows.

This lets a user set Columns to 2 first and progressively remove rows until the warning disappears. It also follows existing TopoDroid form behavior more closely than silently normalizing a value during Save.

### Fill order

The order should be column-major: down, then right. That matches `files_for_codex/rendered_legend.png` and makes Up/Down operate on one understandable global sequence.

When more than one column is requested, distribute rows as evenly as possible while preserving that sequence and never exceeding Rows per Column. For example, seven entries in three columns should produce column counts of `3 / 2 / 2`, not `3 / 3 / 1`, unless a later design adds explicit blank-slot control.

Columns describes the requested maximum populated width. If there are fewer entries than requested columns, there is no need to render empty trailing columns.

### Controls

Integer steppers would be more field-friendly than bare numeric text fields, but they must still permit the user to reduce Columns immediately even when that temporarily makes the draft invalid. Validation should explain the relationship; it should not prevent editing either field.

---

## 4. Deletion and undo behavior

V1 should not add a row-level undo/redo stack or make saved legend property changes participate in sketch undo/redo.

The interaction contract should instead be:

- removing a row changes only the dialog draft;
- the underlying legend point is unchanged until Save;
- Cancel or Back discards the entire draft, including deletions and Rescan changes;
- Save commits the final draft as one ordinary property mutation;
- a deleted installed symbol can be recovered through `+ Symbol`;
- manually re-adding it must remove its identity from `dismissed`;
- later Rescans continue to honor `dismissed` and do not resurrect it automatically.

There is no need for an Undo snackbar in v1. If extra protection is desired without creating an undo system, confirm deletion only for a custom row that cannot be reconstructed from the installed symbol picker. Ordinary installed-symbol rows can be removed immediately from the draft.

All cancellation paths need the same semantics, including the explicit Cancel button, system Back, and outside-tap dismissal if outside dismissal remains enabled. On initial placement they delete the unconfigured point; on later edits they discard the draft and leave the saved point unchanged.

---

## 5. Separate canonical symbol usage from legend presentation

The plan suggests that a future resource-inventory exporter could read the legend as structured data. The legend is not authoritative enough for that role:

- users can remove common symbols such as walls;
- users can add an installed symbol that is not present in the plot;
- users can give one symbol a non-standard display label;
- blank/custom rows have no canonical symbol identity;
- two legends on the same plot can intentionally disagree;
- the legend snapshot does not retain occurrence coordinates needed for questions such as “gypsum near A1, A3, and A20.”

Introduce a separate immutable `PlotSymbolUsageSnapshot` or equivalent internal service derived from actual drawing commands. Each occurrence should be able to retain:

- symbol kind;
- canonical full Therion name;
- scene coordinates or representative geometry;
- scrap identity;
- later, nearest-station information.

The legend consumes an aggregated view of that snapshot. Future inventory tools consume the occurrence view. Persisted legend state contains only presentation choices.

The scanner should be exposed through a narrow `SpecialPointPlacementContext` or `DrawingWindow` service. The proposed direct walk through `DrawingCommandManager.mScraps` will not compile because that field is private. Snapshot command references or symbol identities quickly under `TDPath.mCommandsLock`, release the lock, and perform name resolution and layout work afterward.

Title Sheet fields should follow the same separation later: survey name, team, station counts, and similar facts remain canonical survey data. The point stores which fields to show, formatting, and explicit user overrides—not a second authoritative copy of the survey record.

---

## 6. Revise the persisted state model

The sample JSON is too small for the promised row editor. It stores text and weight but promises orientation and normal special-point editing. It also loses a useful label when a referenced symbol is no longer installed.

### Recommended root state

Keep the envelope versioning, but organize the payload around separate sections:

```text
objectId
titleSheet
legend
```

`objectId` should be a stable generated ID. It is cheap to reserve now and gives future attached custom ink or cross-feature references an unambiguous parent.

For v1:

- `titleSheet.enabled` should default to false because no Title Sheet content is rendered yet;
- `legend.enabled` defaults to true;
- use descriptive domain names such as `columns` and `rowsPerColumn` in Java even if the encoded JSON uses compact keys;
- add the rendered heading to the state or explicitly declare it fixed. Both supplied visual targets contain “Legend,” but the current layout specification omits it.

### Recommended row state

Each row needs:

- stable row ID;
- row kind: point, line, area, or custom;
- canonical full Therion name when applicable;
- captured fallback/default label;
- nullable user label override;
- preview specification appropriate to v1:
  - stroke weight;
  - point scale if supported;
  - orientation where meaningful;
  - text/value;
  - versioned special-point preview payload where required;
- enough status to distinguish an unresolved installed symbol from a symbol that is merely not used in the current scan.

If the symbol cannot be resolved after a library reload or archive restore, preserve the row and fallback label and render a clear unavailable/empty swatch. Do not discard the row during decode or Rescan.

### Validation and atomic commit

The 120-row cap alone does not protect `DataOutputStream.writeUTF`; free-form labels, values, and `dismissed` can still exceed the 65,535-byte limit.

Save should:

1. build and validate a complete immutable state;
2. encode the complete candidate `mOptions` string;
3. validate the encoded modified-UTF length;
4. prepare the matching render layout;
5. only then publish state, options, layout, and bounds as one successful commit.

Decode must also enforce row, tombstone, text-length, numeric-range, and supported-kind limits so a malformed TDR cannot allocate an enormous legend before the editor opens.

---

## 7. `SymbolPreviewRenderer` is not yet a production legend renderer

The existing class is excellent for picker buttons, but should not be retained directly inside a world-space composite object.

Specific conflicts:

- its padding is expressed as display-density-scaled dp inside the supplied rectangle, while legend swatches are measured in scene units;
- it hardcodes one Standard `SketchBrushStyle` and cannot accept per-row weight, orientation, text/value, or special behavior state;
- it retains synthetic mutable `DrawingPointPath`, `DrawingLinePath`, and `DrawingAreaPath` instances;
- line and area rendering mutate cached paint state, so the same retained preview can race between scene-cache rendering and PNG export;
- its draw path allocates a `RectF` and `Matrix` per row;
- it does not carry the drawing `xor_color`, so swatches can disagree with boxes and labels in inverted on-screen rendering.

Split the capability into:

1. a density-free, immutable scene-space swatch snapshot suitable for the legend and concurrent PNG rendering; and
2. the existing widget adapter, which adds UI-specific dp padding and button behavior.

The legend layout can then hold immutable swatch snapshots rather than live drawing paths. Decide explicitly whether “Standard” means the built-in fixed default or the user-configured Standard weight preset. If it follows the configured preset, include that generation/value in layout invalidation; an explicit per-row override remains frozen.

`DrawingPointFactory.createPreview()` also intercepts registered special points, so the authored 2×3 picker glyph will not automatically be used after `title-legend` is registered. Add an explicit behavior preview policy that can request the authored symbol glyph. Exclude `title-legend` itself from `+ Symbol` to avoid recursive or meaningless legend rows.

---

## 8. Make layout publication and bounds explicit

The plan says a stale renderer draws the previous layout and posts a rebuild. Today there is no cache slot, preparation callback, or atomic publication contract for that behavior, while `DrawingSemanticPointPath` asks the renderer for bounds during construction and immediately after state changes.

Use an immutable prepared object containing at least:

```text
state identity
measured text layouts
swatch snapshots
local geometry
exact local bounds
all generation/input keys
```

Prepare before publishing editor changes. For points loaded from TDR or invalidated by a symbol/font/settings reload, prepare outside `draw()` and outside command locks, then atomically publish, refresh bounds, and request a scene render. A renderer should never schedule UI work from `draw()` or `computeBounds()`.

Include at least these invalidation inputs:

- legend state;
- label style;
- point/overall scale;
- grid unit;
- text font generation;
- symbol-library/preview generation;
- point and line ink settings;
- configured Standard weight if it is dynamic.

### Exact bounds

The generic special-point path currently converts dynamic bounds into a square. That is harmless for small framed values but expensive for a wide multi-column legend. A five-column legend could intersect a large amount of empty vertical space and be rendered when nowhere near the viewport.

Keep the current conservative square fallback for existing special points, but let the legend provide exact transformed bounds/intersection behavior for:

- render culling;
- PNG extents;
- body hit-testing;
- selection outline;
- multiselection.

The body-hit prepass creates a transient `SelectionPoint`, so dragging must explicitly rebucket the underlying point afterward. Deduplicate identity when both the large footprint and the normal point anchor are hit.

### Visual geometry

- Use rectangular swatches, approximately 3:2 or 4:3, rather than the square specified in the plan. Both visual references use wider boxes.
- Render the `Legend` heading or remove it from the visual target; do not leave it implicit.
- Bound long labels. Either wrap automatically to a defined maximum width with variable row height, or support explicit line breaks plus a maximum line width. Unbounded single-line labels defeat the user’s column-shaping controls and can create enormous PNG bounds.
- Prefer a stable top-left composition anchor so Rescan and Title Sheet growth extend down/right without moving already aligned content. If center anchoring is retained to match normal points, state explicitly that changing rows can shift all table edges around the anchor.

---

## 9. Editor UI revisions

### Full-height structure

Implement the editor as a genuinely full-height dialog:

- fixed segmented section control at the top;
- one `0dp`/weight-scoped scrolling body containing the table, add controls, layout settings, and formatting control;
- fixed Save/Cancel footer outside the scrolling body;
- keyboard `adjustResize` behavior so a focused name field remains visible.

The HTML mockup describes a fixed footer, but its body is not actually bounded and its actions remain in ordinary document flow. The Android layout must make the behavior structural rather than relying on content height.

### Switch and segmented controls

Use real checked controls with accessibility state. AppCompat is not required to use `android.widget.Switch` on this project’s API level. A tinted platform `Switch`, or a small subclass of it, provides touch, keyboard, RTL, saved state, and accessibility behavior that a raw custom `View implements Checkable` would have to recreate.

Likewise, build the segmented bar from checked `CompoundButton`/`RadioButton` semantics. Reuse the existing toolbar colors, not its undersized touch geometry.

There is one unresolved v1 issue: turning Legend off while Title Sheet renders nothing creates an invisible point. Do not silently forbid the user’s choice, but define a recovery experience before making the switch active. Acceptable options include a selectable screen-only edit marker that is excluded from PNG, or deferring the active switch until Title Sheet has real output. An invisible point recoverable only by remembering its exact center is not acceptable.

### Touch and accessibility

- Every actionable target should be at least 48dp, although the visible pencil, triangle, or X may remain smaller.
- Make the whole switch row tappable.
- Disable Up on the first row and Down on the last.
- Preserve keyboard focus after moving a row.
- Give dynamic descriptions such as “Move Folia up” and “Remove Folia.”
- Announce reorder position and capacity validation.
- Do not dim the entire stale row; retain normal control contrast and use a warning icon/text badge.
- Do not rely on red alone for invalid capacity.

### Row editor and formatting

Keep the per-row sheet curated for a legend swatch rather than opening the normal point dialog wholesale:

- special point text/value where applicable;
- orientation where it communicates the symbol;
- stroke-weight control with Standard detent and Reset;
- live swatch preview.

Do not expose levels, raw options, or other placement-only controls.

The Label Formatting dialog should share the full `SketchTextStyle` editing infrastructure used by labels: font, emphasis, size, color, opacity, and live sample. Alignment should be intentionally fixed left because the table owns alignment. Screen-fixed sizing should be unavailable because the object and PNG are world-space.

### Symbol picker

The dialog-friendly picker should provide:

- Points / Lines / Areas checked tabs;
- search by displayed and canonical symbol name;
- production swatches at field-friendly size;
- already-in-legend indication;
- installed symbols only;
- optional multi-select with a fixed `Add (n)` action.

Do not reuse the symbol-enable management adapter verbatim; its checkbox/edit/group columns serve a different task.

### Custom rows

For v1, treat `+ Blank` as `+ Custom row`: focus a label field and render an empty swatch. Do not encourage detached hand-drawn ink that fails to move with the legend. State honestly that custom artwork is not attached yet, and hide or disable the pencil unless it opens meaningful custom-row settings.

If attached artwork becomes important later, use the stable object and row IDs reserved in the state rather than inferring ownership from geometry location.

---

## 10. Supported export boundary

PDF and structured drawing exports are out of scope. Remove the current plan section that proposes changes to Therion, SVG, DXF, SHP, XVI, cSurvey, and PDF behavior, and remove those exporter files from the modified-files list.

### PNG

PNG is the only supported visual export for this feature. It renders through the normal canvas path, but the following still require explicit tests:

- exact extents include the heading and every row without excessive square-bound whitespace;
- the newest committed layout is used after Save, Rescan, symbol reload, and text-style changes;
- scene-space spacing is independent of display density;
- missing symbols and custom rows render predictably;
- long but valid labels remain within guarded geometry limits;
- concurrent scene-cache and PNG rendering is deterministic;
- portrait and landscape presentations produce correct orientation and bounds;
- 1×, 2×, and 4× PNG output remains legible and does not crop the legend;
- disabling content clears old bounds rather than leaving a stale large export extent.

`xor_color` remains relevant to inverted on-screen drawing, but normal PNG export uses the ordinary color path; do not describe XOR as a PNG requirement unless the PNG exporter later adds an inverted mode.

### `.dat` and `.csv`

These are survey-shot data exports, not sketch-object exports. Adding, editing, disabling, or deleting a legend must not add records, columns, comments, or metadata to either format.

Add a regression test showing that `.dat` and `.csv` output is semantically identical before and after placing and editing a legend. Future inventory integration should use an internal structured API or a separately designed export, not quietly change the established survey-data schemas.

### TDR persistence

The `-tdx-special` envelope remains important for internal sketch persistence and archive integrity, but it should not be described as a supported external drawing export. Test normal TDR save/load, malformed state, missing installed symbols, and state-size limits.

---

## 11. Performance claim and verification

The scene cache should make interaction feel responsive, but “zero per-frame time during pan/zoom” is too strong. Pan, zoom, and drag continue to request background scene rebuilds while a stale bitmap keeps the gesture responsive.

Use the more accurate contract:

> At rest, the legend is part of the cached scene. During pan, zoom, and drag, stale cached pixels keep input responsive while background renders converge. Expensive layout and swatch preparation never runs in the gesture or draw path.

Verification should include:

- 20-row normal case and 120-row guard case;
- repeated drag, pan, and zoom rather than one static render;
- P95 and maximum input latency or frame convergence, not only average render duration;
- concurrent scene-cache and PNG rendering of the same legend;
- symbol-library reload while a saved legend is present;
- exact culling of a wide, short legend and a narrow, tall legend;
- invalid-capacity editing without any automatic value mutation;
- label-length and overall-bounds guards preventing PNG memory exhaustion.

The authored picker glyph, final rendered legend, row editor, selection outline, and PNG should receive separate visual fixtures. Existing drawings with no legend should remain render-identical unless an intentional shared renderer correction is made and documented.

---

## 12. Files and implementation scope corrections

Add these likely modifications to the plan’s file inventory:

- `SpecialPointPlacementContext.java` or another narrow usage-snapshot service entry point;
- `DrawingCommandManager.java` for a locked current-plot symbol snapshot;
- `SymbolPreviewRenderer.java` for the density-free immutable swatch core;
- `SymbolPreviewButton.java` for the widget adapter and row state;
- `DrawingPointFactory.java` for authored-glyph preview policy;
- `Selection.java` / selection movement handling for body-hit rebucketing;
- `res/raw/symbols_topodroid_sketch.zip`, regenerated after adding the source symbol;
- pure model tests for capacity, Rescan, tombstones, and codec validation;
- PNG and data-export regression tests.

Remove from v1 scope:

- PDF behavior and tests;
- Therion, SVG, DXF, SHP, XVI, and cSurvey exporter changes;
- vector legend output;
- editor undo/redo;
- global symbol size/weight dialog overhaul;
- detached or attached custom artwork;
- canonical resource-inventory export.

No migration or compatibility path is needed for introducing the first state version. Future state versions must continue to decode v1 rather than following the current special-point pattern of accepting only the newest exact version.

---

## 13. Suggested implementation sequence

### Phase 1 — model and contracts

- Confirm the capacity/error behavior and fill algorithm.
- Add `PlotSymbolUsageSnapshot` and pure aggregation/filter logic.
- Define root and row state, stable IDs, validation, and atomic encoding.
- Build the immutable scene-space swatch snapshot API.
- Define prepared layout publication and exact bounds.
- Add pure tests using a fake symbol resolver.

### Phase 2 — editor

- Dedicated full-height dialog and draft model.
- Accessible tabs and switch treatment.
- Table rows, reorder, delete, Rescan, custom row, and capacity validation.
- Dialog-friendly searchable symbol picker.
- Curated row editor and shared label-formatting editor.
- All Save/Cancel/Back/initial-placement lifecycle paths.

### Phase 3 — sketch integration and PNG

- Point placement and authored picker glyph.
- Composite draw, exact selection, rebucketing, drag, and bounds.
- Scene-cache invalidation and library/font/settings rebuild paths.
- PNG rendering and `.dat`/`.csv` non-interference tests.
- Performance, visual, and render-identity gates.
- Package the symbol archive.

### Phase 4 — field acceptance

- Build the app and Android test APK.
- Run fast/full regression suites.
- Install and launch on the field tablet when connected, following the quick-refresh procedure.
- Place, edit, Rescan, invalidate capacity, delete to validity, save, drag, reopen, and export PNG at multiple scales.
- Verify a wide two-column workflow specifically, because preserving that user-directed constraint is an acceptance requirement.

---

## 14. Remaining product decisions

The plan team should get explicit answers to these before implementation:

1. Does “Standard weight” mean the fixed built-in standard or the user-configured Standard preset?
2. Is the rendered `Legend` heading editable and/or hideable?
3. How is a deliberately disabled legend recovered while Title Sheet has no rendered content?
4. Are labels automatically wrapped to a fixed scene width, or are explicit line breaks supported?
5. What maximum label length and overall scene bounds protect PNG generation?
6. Should a custom row deletion receive confirmation because `+ Symbol` cannot reconstruct it?

None of these require a broader symbol-editor overhaul, but leaving them implicit will produce divergent assumptions in the codec, editor, and renderer.

---

## 15. Priority order

If the plan is revised only partially, address these in this order:

1. User-controlled capacity and invalid-Save behavior.
2. Canonical symbol usage versus presentation-only legend state.
3. Immutable scene-space swatch rendering and concurrent PNG safety.
4. Atomic state/layout/bounds publication.
5. Row schema, missing-symbol fallback, and persistence limits.
6. Exact bounds, body-hit rebucketing, and drag behavior.
7. Full-height accessible editor and disabled-legend recovery.
8. Narrow PNG/data export boundary and revised verification matrix.

With those corrections, the special-point approach should support a polished v1 without committing the project to the wrong inventory model, an unsafe preview-render path, or automatic layout behavior that overrides the sketcher’s intent.
