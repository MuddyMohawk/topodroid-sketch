# Layers and Backdrop Images — Design Plan

## Goals

Add per-plot layer support to TopoDroid Sketch primarily so a sketcher can stack 2–5 cave-passage levels with per-layer name, visibility, opacity, and ordering. Add backdrop-image support so cross-sections can be sketched by tracing over a photograph of the passage. Maintain compatibility with vanilla TopoDroid such that fork-saved files still load in vanilla (with graceful loss of layer metadata and backdrops), and vanilla files still load cleanly in the fork.

## Non-goals for v1

Per-shot layer assignment, per-layer rendering of legs/splays/stations, and any DB-schema changes for shots are explicitly out of scope. That belongs to a future v2; the v1 model assumes shots are shared across all layers and only the freehand sketch differs per layer. This is the smaller and safer feature.

Cross-layer move/copy of strokes is also out — if a stroke is on the wrong layer, the user redraws it. Therion export keeps its existing 1:1 scrap-to-Therion-scrap mapping; layers do not change export semantics. Compass `.dat` export is shot-data only and is unaffected. Per-stroke opacity tools, layer groups/folders, and any UI sophistication beyond an editable list are deferred. The render-loop change does not touch SVG/DXF/PDF/cSurvey vector exporters in v1; those continue to flatten everything.

## Conceptual model

A **layer** is an existing `Scrap` that has gained three new fields: a display name, an opacity (0–100), and a visibility flag. Plus a sortable order field separate from `mScrapIdx` so users can reorder without breaking persistence keys. Internally the class is still `Scrap` and the Java code can keep saying "scrap"; the user-facing UI says "Layer". This avoids a churn rename across dozens of files for no functional benefit.

A **backdrop image** is a new primitive: a bitmap with a scene-coordinates bounding box, an opacity, a visibility flag, and an owning-layer index. Backdrop images are stored as a sibling list on `DrawingCommandManager` (next to `mXSectionOutlines`, not inside any scrap) and rendered during the owning layer's draw pass.

A **placed cross-section viewport** (the v1.16 feature) is unchanged conceptually but its render is moved into the owning-layer's draw pass so its visibility and opacity inherit from the layer it belongs to.

The unifying mental model: a layer's draw pass renders, in order, the layer's backdrop images, then the layer's placed viewports, then the layer's strokes — all wrapped in `canvas.saveLayerAlpha` if the layer's opacity is below 100. Backdrops and viewports cascade their owning layer's visibility and opacity for free.

## Why backdrop image is a viewport-style primitive, not a full-canvas overlay

The primary use case is cross-sections drawn over photographs. Alignment is the whole point: the photo's passage walls have to line up with the survey's wall splays so the sketch is metrically meaningful. A full-canvas backdrop would force the user to pan/zoom the entire canvas to align, which decouples the photo from the shot reference frame. A bbox-clipped placeable image with drag and pinch-scale gestures gives explicit alignment handles inside the plot's own coordinate system — exactly what the existing placed-viewport infrastructure already provides for cross-sections. Reusing `mSelectionFixed`, the pseudo-point hit-grid, and `shiftHotItem` means the backdrop gets edit-mode gestures effectively for free.

For the secondary use case — tracing an old plan-view map of a whole cave — the same primitive works: a single very large backdrop sized to span the relevant area. Users who want a "fill the canvas" effect achieve it by sizing the bbox to cover the visible scene.

## Data model changes

`Scrap` gains four fields:

```java
String  mDisplayName;  // default: "Layer " + mOrder
int     mOpacity;      // 0..100, default 100
boolean mVisible;      // default true
int     mOrder;        // render order, separate from mScrapIdx
```

A new class `BackdropImage`:

```java
class BackdropImage {
  int     mScrapId;      // owning layer
  String  mImageFile;    // relative to plot dir
  float   mCx, mCy;      // scene center
  float   mScale;        // scene units per pixel
  float   mRotation;     // degrees
  int     mOpacity;      // 0..100
  boolean mVisible;
  Bitmap  mBitmap;       // lazily loaded
}
```

`DrawingCommandManager` gains `private List<BackdropImage> mBackdropImages` alongside `mXSectionOutlines`.

`DrawingPath.mScrap` (already present on every path) continues to identify the owning layer for strokes — no change to existing per-path data.

## Render loop changes

The current `executeAll` block at `DrawingCommandManager.java:1666–1674` is the special case "current scrap full, others grey outline". Replace it with a layered loop:

```
for each scrap in mScraps ordered by mOrder:
  if !scrap.mVisible: continue
  int saveCount = (scrap.mOpacity < 100)
    ? canvas.saveLayerAlpha(bbox, scrap.mOpacity*255/100)
    : -1
  drawBackdropsOwnedBy(scrap.mScrapIdx, canvas, matrix, bbox)
  drawXSectionViewportsOwnedBy(scrap.mScrapIdx, canvas, matrix, scale, bbox)
  scrap.drawAll(canvas, matrix, scale, bbox)
  if (saveCount >= 0) canvas.restoreToCount(saveCount)
```

The existing per-scrap viewport filter changes from `isScrapId(mCurrentScrap.mScrapIdx)` (line 1636) to `isScrapId(scrap.mScrapIdx)` inside the per-scrap loop. The OVERVIEW-mode loop (line 1655) gets the same treatment, and the export bitmap path uses the same iteration so PNG export naturally honors visibility/opacity.

The active editing layer is still tracked as `mCurrentScrap`, but it is no longer privileged in rendering — visualizing which layer is active becomes a UI concern (highlighted row in the Layers panel, optional thin badge near the layer name). Erase, selection, and draw-target operations remain scoped to `mCurrentScrap`, preserving the existing invariant.

A "focus current layer" display mode that re-creates the old grey-outline UX should be retained as an optional toggle in the existing `DisplayMode` flags. It's off by default per the answer earlier in this conversation, but useful for users who want to focus on one layer without juggling visibility toggles.

## UI: the Layers panel

`PlotScrapsDialog` is replaced (or, less risky, extended in place) by a Layers panel. Each row shows: a visibility eye, an opacity slider, an editable display name, a reorder grip, a row-type icon (stroke layer / backdrop image / xsection viewport), and an active-target indicator. Backdrop and viewport rows nest under their owning layer at one indent level. Header actions: add layer, import backdrop image (file picker), reorder mode.

Tapping a layer row sets it as the active editing target. Tapping a backdrop or viewport row enters its edit mode (drag/scale handles on the canvas). Long-press provides delete and "move to layer" actions.

The 4–5 layer expectation means a vertical scrollable list with no grouping is sufficient. No need for folders, search, or thumbnails in v1.

## Persistence and vanilla-TopoDroid compatibility

The vanilla `.tdr` loader in `DrawingIO.doLoadDataStream` is a byte-tag dispatch loop. Unknown top-level tags hit `default: todo = false` and the loader bails. The `'E'` end-marker also sets `todo = false` and exits cleanly. Crucially, **bytes after `'E'` are simply not read** — the stream is closed and the rest of the function continues without inspecting them.

This gives us a clean forward-compatibility strategy. Append a fork-only extension block after `'E'`. Vanilla stops at `'E'` and never sees the extension. Our loader sees `'E'`, does not bail, and continues reading the extension block until a sentinel `'X'` end marker.

Proposed extension format:

```
'E'           // existing terminator — vanilla stops here
'Q'           // extension start (fork only)
  byte 1     // extension format version
  'L'        // layer metadata sub-block
    int n
    repeat n times:
      int    scrap_idx
      UTF    display_name
      int    opacity      // 0..100
      byte   visible      // 0 / 1
      int    order
  'B'        // backdrop sub-block
    int n
    repeat n times:
      int    scrap_idx_owner
      UTF    image_filename   // relative to plot directory
      float  cx, cy
      float  scale
      float  rotation
      int    opacity
      byte   visible
'X'          // extension end
```

Each sub-block is self-describing with its own count, so future versions can add new sub-blocks without breaking older fork builds (an older fork sees `'B'`, doesn't know about it, can either skip-by-sub-block-length or stop reading the extension — defensive parsing is straightforward).

Backdrop bitmap files live in the plot directory alongside the `.tdr`, named e.g. `<plotname>-bg-<uuid>.png`. They are referenced by relative filename in the extension block.

The round-trip behavior this produces:

When vanilla loads a fork-saved file, it reads scraps and strokes normally, sees `'E'`, stops, and ignores the extension bytes. Each scrap renders as a vanilla scrap (current full / others grey outline) with no layer metadata applied. Backdrop bitmap files are present in the survey directory but unreferenced and unused.

When vanilla saves the file (after editing in vanilla), it writes everything up to its `'E'` terminator and stops. The extension block is dropped. Layer metadata and backdrop image references are lost on a vanilla-side save — but the strokes survive, including any new strokes vanilla added.

When the fork loads a vanilla file, no `'Q'` extension is present after `'E'`, so all scraps get default layer metadata (opacity 100, visible true, name "Layer N", order = `mScrapIdx`). Identical visual result to before.

The conclusion: **no "export to vanilla" checkbox is needed for layer/backdrop compatibility**. The format degrades gracefully in both directions. The existing v1.16 sketch-line fallback (user-fine → user inside vanilla's symbol resolver) is unrelated and stays as it is. If a future export-to-vanilla checkbox is added for other reasons (e.g., explicitly stripping unknown content, simplifying the file for archival), it would also strip the extension block — but that's an option, not a requirement.

ZIP export bundles the `.tdr` plus any backdrop bitmap files in the plot directory. Vanilla's ZIP importer copies all files into the survey directory; unknown bitmap files sit inert. This needs verification on a vanilla install before shipping (see open questions).

## PNG export

Current PNG export iterates `mScraps` and renders all of them through the existing draw method. With the layered render loop, the natural default is to honor on-screen visibility and opacity — the exported PNG matches what the user composed on screen. This is the right behavior for the cartographer-tracing workflow: compose the view, export it, hand off.

Add a single new option to the PNG export dialog: "All layers visible at 100%" (default off). When checked, the export overrides all visibility/opacity settings and renders every layer fully. Useful for archival or for sharing a flattened sketch independent of the current view state.

Backdrop images render at their layer's effective opacity multiplied by their own opacity. To exclude a copyrighted reference backdrop from a shared export, the user toggles the backdrop's visibility off (or its layer off) before exporting — the existing visibility UX covers this without needing a separate "exclude backdrops" toggle. If we later find users frequently want backdrops excluded but everything else shown, a "Exclude backdrop images" export option is trivial to add.

PNG export goldens in the visual regression suite will need a baseline refresh after the render-loop change. Single-layer plots should produce pixel-identical output (one layer at 100% opacity going through the new loop yields the same pixels as the old code). Multi-layer plots in the test corpus will look different — that's the point.

## Visual regression test impact

The instrumentation suite under `app/src/androidTest/` captures golden screenshots for tap/swipe scenarios. Plots that today have only one scrap (probably the bulk of the suite) should produce identical pixels under the new loop, since one visible scrap at 100% opacity is the same as the current `drawAll`. Plots that exercise multiple scraps will need refreshed baselines via `scripts\refresh-visual-baselines.ps1`.

New tests to add: layer visibility toggling, opacity slider effect on a known stroke, backdrop image rendering (load fixture image, place at known position, screenshot-diff), layer reordering producing the expected z-order, ZIP round-trip with layer metadata preserved.

## Sequenced work breakdown

The work splits into five phases, each shippable in isolation. Phases 1–2 alone deliver useful multi-layer support without backdrops. Phases 3–4 add the backdrop primitive. Phase 5 ties PNG export to the layered model.

**Phase 1 — render loop and scrap fields.** Add the four new fields to `Scrap` with default values. Replace the current-scrap-full/others-grey-outline block in `executeAll` with the layered loop. Re-route placed-viewport rendering to the per-scrap pass. Refresh visual baselines and confirm single-scrap plots are pixel-identical.

**Phase 2 — persistence.** Implement post-`'E'` extension block read in `DrawingIO.doLoadDataStream` and corresponding write in the export path. Round-trip test in fork: save, load, confirm fields preserved. Manual cross-app test: save in fork, load in vanilla, confirm strokes load and extension bytes are silently dropped without crash. ZIP export round-trip test in fork.

**Phase 3 — Layers panel UI.** Build the new dialog with row visibility, opacity slider, name editing, add/delete, reorder. Wire to the per-scrap fields with live preview. The active layer is still set via the dialog (matching current scrap-switching UX).

**Phase 4 — backdrop images.** Add the `BackdropImage` class and the sibling list on `DrawingCommandManager`. Build the file-picker import flow that copies the chosen image into the plot directory at a downscaled max dimension (~4096 px) and creates a backdrop entry. Adapt the existing placed-viewport edit gestures (drag, pinch-scale) to also drive backdrop placement. Integrate backdrop rows under their owning layer in the Layers panel. Add to extension block persistence.

**Phase 5 — PNG export integration.** Audit the PNG export render path so it honors visibility and opacity through the same layered loop. Add the "All layers visible at 100%" override option. Refresh PNG goldens.

Phase 1 is the riskiest because the render loop is the hot path and underlies every existing screenshot. Phase 4 is the biggest by line count (file picker, image lifecycle, gestures) but is well-isolated. Phase 2 deserves careful test coverage because once a fork file is saved with extension data and re-saved by vanilla, that vanilla-side save permanently strips fork data — users need to understand this so they don't accidentally round-trip through vanilla and lose layer metadata.

## Risks

`Canvas.saveLayerAlpha` allocates an offscreen bitmap proportional to the bbox each time it's called. With 5 layers all at <100% opacity on a wide-scrolled large sketch, frame cost can climb. The mitigation is to skip `saveLayerAlpha` entirely when opacity is exactly 100 (most layers most of the time), and to clip the saved layer to the visible bbox rather than the full plot. Worth profiling on the test tablet before declaring v1 done.

Cached cross-section viewport paths (`mSketchPaths`, `mRefPaths` in `DrawingOutlinePath`) are populated when the section is placed and are not refreshed if the source section is later edited. This is a pre-existing limitation, not introduced by layers, but it interacts badly with a layered model where the user might naturally expect viewports to update when their owning layer is edited. Out of scope for this v1; document the limitation.

Eraser and selection scoped to the active layer may surprise users who expect to erase across visible content. Document the behavior in release notes; cross-layer erase is a future feature with its own design questions (does the eraser respect visibility? opacity? z-order?).

Backdrop bitmap memory. A camera photo at full resolution is ~10–30 MB decoded. With 5 layers and a couple of backdrops, memory can climb fast on the test tablet. Downscale on import to a max dimension (~4096 px on the long edge) and re-encode as PNG. Document that v1 doesn't support per-backdrop crop or rotate beyond a single rotation angle.

Vanilla loader strictness. The post-`'E'` strategy depends on vanilla never asserting "stream-end after `'E'`". The current loader doesn't, but a future vanilla update could. Test against the current vanilla release before shipping and re-test on each vanilla bump.

## Open questions

Should layer membership be settable per-stroke after creation (select a stroke, "move to layer 2")? v1 says no — strokes go into the active layer at draw time, redraw if wrong. Worth revisiting in v2 if it becomes a frequent ask.

Default layer count in a new plot: 1 layer ("Layer 1") or 2 (a "Layer 1" plus an empty "Backdrop" layer for image imports)? v1 proposal: 1, with the "+ add layer" path being the obvious next step.

When a user places a cross-section viewport on the plan, it inherits the active layer (matching today's behavior where it tags the current scrap). Should there be UI to change a viewport's owning layer later? v1: no, redraw / re-place. Defer.

ZIP-import behavior in vanilla TopoDroid when the survey directory contains backdrop bitmap files vanilla has no record of. Likely silently copied and ignored, but needs verification on a vanilla install before shipping.

Naming: keep `Scrap` as the Java class name internally and use "Layer" only in user-facing strings? The proposal here is yes — renaming `Scrap` everywhere is high churn for no functional benefit, and the dual-vocabulary precedent already exists ("scrap" appears in Therion semantics anyway).
