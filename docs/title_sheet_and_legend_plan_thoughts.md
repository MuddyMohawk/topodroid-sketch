# Title Sheet & Legend — response to the plan review

Companion to [title_sheet_and_legend_plan.md](title_sheet_and_legend_plan.md) rev 2 and [title_sheet_and_legend_plan_review.md](title_sheet_and_legend_plan_review.md).

## Summary

The review is good. I verified its load-bearing technical claims against the code rather than taking them on trust, and every one I checked was correct — including two things the original plan simply got wrong. I accepted the large majority of it.

Three items where I did not accept the recommendation as written: the capacity behaviour (§1 below — reconciled rather than flipped, and it needs your call because it revisits a decision you made), the structured exporters (§2 — descoped as asked, but I think the reasoning is wrong and want it on record), and the exact-bounds recommendation (§3 — agreed in substance, but the mechanism needs a constraint the review did not mention).

---

## 1. Capacity — I did not simply adopt the review's answer, and this needs your decision

**The conflict.** You chose "Rows is a soft cap, columns auto-extend" when I asked, explicitly rejecting "Block save until it fits" as "frustrating on a tablet in a cave." The review argues the opposite: never auto-correct, block the commit, keep the dialog open.

Both are right about something real:

- The review's case is genuinely good and I had not considered it. The workflow it protects — *"I want a wide two-column legend; let me prune entries until it fits"* — is a real cartographic decision, and auto-extending `columns` from 2 to 3 silently overrides exactly that intent. My original plan was wrong to mutate a value the user typed.
- Your case is also right. Leaving the dialog open and committing nothing is the one failure mode with no recovery path underground. Blocking Save is a dead end.

**What I put in the plan** separates the two concerns the review conflated — *storing* a value and *rendering* to it:

- The typed `columns` and `rowsPerColumn` are stored verbatim, always. Nothing the user typed is ever changed. This fully satisfies the review's objection.
- Save always succeeds. If the requested shape cannot hold the rows, the *renderer* uses more columns so nothing is hidden, and the editor keeps showing the unsatisfied-request warning on reopen. This fully satisfies your objection.

The user who wants exactly 2 columns sees a persistent warning and prunes until it clears — their `2` was never touched. The user who needs to save and move on gets a wider legend and loses nothing.

**Your call.** The visible behaviour is close to what you originally chose, so if you prefer the review's stricter version — Save refuses and the dialog stays open — say so and I will change it. I would not recommend it.

## 2. Structured exporters — descoped as instructed, but I disagree with the reasoning

The review says to remove Therion / SVG / DXF / SHP / XVI / cSurvey from scope and drop those files from the inventory. I have done that.

But "out of scope" here does not mean "no behaviour." It means the legend point will be **emitted as a bare anchor marker** into those formats — a `u:title-legend` point at the legend's centre, with all of its content gone. A Therion user gets an unknown user-symbol reference; an SVG user gets a stray dot where their legend was.

That is defensible only because the fork already does the same for `u:pit-depth` and `u:bedding-attitude`, so it is at least *consistent*. But those points at least still mean something as a marker. A legend anchor does not.

The actual fix is two lines in each of `DrawingSvgBase` and `DrawingDxf`, following the `isPointReference` skip that already exists — smaller than the paragraph describing why we are not doing it. My read is that the review optimised for scope discipline in the abstract and did not price this particular item.

I have left it out per the instruction, recorded it as known v1 behaviour in the plan, and it is follow-up #1. Worth reconsidering if it is cheap to fold in during Phase 3.

## 3. Exact bounds — agreed, with a constraint the review omitted

The review is right that a square bbox is wasteful for a wide multi-column legend. But it cannot simply be replaced, and the reason matters for implementation:

`refreshSpecialBounds()` produces a symmetric square **because `mLandscape` is not known at that moment** — it is stamped later, on the render thread, inside `Scrap.drawAll`. A non-orientable point takes a 90° quarter-turn in landscape presentation, so a tight rect computed at refresh time would be wrong in one of the two presentations.

So the plan splits it: the conservative square stays as the coarse `DrawingPath` RectF (correct in both orientations, and unchanged for the existing special points, which protects the bedding-attitude render hashes), and the legend exposes **exact oriented bounds** through its own accessor for the four places where orientation *is* known — culling, PNG extents, hit-testing and the selection outline. Same outcome the review wanted, without a shared-helper change that would put existing hashes at risk.

---

## Fully accepted — the review was right and the plan was wrong

**Two outright errors in my plan:**

- **`res/raw/symbols_topodroid_sketch.zip` must be regenerated.** I wrote the plan as though adding the file under `symbols-git/` was sufficient. It is not: the app installs from the packaged archive (`TopoDroidApp.installSymbols:2225`), and `utils/symbols/build_topodroid_sketch_symbols_zip.py` must be run and the zip committed. Without this the symbol would simply never appear on a device, and it would have been a confusing failure to debug.
- **`DrawingCommandManager.mScraps` is `private`.** My scanner design walked it directly and would not have compiled. The snapshot builder has to live inside `DrawingCommandManager` behind a narrow accessor.

**Genuinely sharp catches I would not have found without the review:**

- **`createPreview` intercepts special points.** Confirmed at `DrawingPointFactory.java:35-41` and `SymbolPreviewRenderer.java:123-127`: once `title-legend` is registered, its own picker icon would try to render a whole legend table. Needs an explicit authored-glyph policy. This would have shipped as a bizarre-looking bug.
- **`decodeState` rejects any non-exact version.** `PitDepthPointBehavior.java:33` is `if (version != STATE_VERSION) return null`. Copying that pattern means the first schema bump silently blanks every existing user's legend. The plan now requires decoding every version ≤ current.
- **Rebucketing after a body hit.** Confirmed the concern is real *and* that the codebase already solves it for `DrawingLabelPath` at `Scrap.java:2921-2926`, with a comment describing precisely this failure. The legend needs the same branch — cheap, but invisible until someone drags a legend and then cannot select it again.
- **`SymbolPreviewRenderer` retains mutable drawing paths.** Verified: `PointScene.mPoint`, `LineScene.mLine`, `AreaScene.mArea` are all retained instances, and line/area drawing mutates cached paint state. My plan stored those objects in the layout snapshot, which would have raced between the scene-cache thread and PNG export — exactly the class of concurrency bug this codebase has already been burned by. Also correct on the dp-vs-scene-unit padding mismatch, the per-call `RectF`/`Matrix` allocation, and the missing `xor_color`.
- **The invisible-point problem.** Turning Legend off while Title Sheet renders nothing yields a point recoverable only by remembering where you put it. I had not thought about it at all. Flagged in the plan as blocking the Enable switch.

**Accepted without reservation:**

- **Canonical usage vs. presentation.** The strongest point in the review. My line about a future inventory exporter reading the legend as structured data was wrong — the legend is editable, deletable, renameable and coordinate-free, so it can never be authoritative. `PlotSymbolUsageSnapshot` is the right shape, and it also makes the Title Sheet's data story correct by construction.
- **Row schema.** Mine stored text and weight while the editor promised orientation and special-point editing. Stable row IDs, captured fallback labels, an explicit unresolved-symbol state, and a real preview spec are all needed.
- **Validation and atomic commit ordering.** The 120-row cap does not protect `writeUTF`; free-form labels and tombstones can. Encode-then-validate-then-prepare-then-publish is the right sequence.
- **`android.widget.Switch` is available at minSdk 21.** I over-engineered here. I correctly established that AppCompat is absent, then wrongly concluded a hand-rolled `Checkable` view was necessary. The platform `Switch` gives touch, keyboard, RTL, saved state and accessibility for free; tinting it is the whole job.
- **Column-major fill, even distribution (3/2/2 not 3/3/1), rectangular swatches, render the "Legend" heading, bound the label width.** All correct; the swatch aspect ratio and the heading were things I had missed while looking at the same reference images.
- **Full-height layout must be structural.** The mockup's footer only looks fixed because the content is short. Fair.
- **Accessibility specifics** — 48dp targets, disabled Up/Down at the ends, focus preservation, dynamic descriptions, don't dim the whole stale row, don't rely on red alone. All adopted.
- **Performance wording.** "Zero per-frame time during pan/zoom" was too strong; gestures still drive background rebuilds. The review's phrasing is more honest and is now the plan's contract.
- **Verification depth** — P95 and max rather than average, repeated gestures rather than one static render, a 120-row guard case, concurrent cache+PNG rendering, library reload with a saved legend.

## Minor disagreements, not worth changing the plan over

- **`.dat` / `.csv` non-interference.** The review treats this as a real risk. It almost certainly is not: those are shot-data exports that never touch drawing commands, and the legend lives entirely inside a drawing item's options string. The regression test is cheap insurance and I have kept it, but it should be one test, not a workstream.
- **`+ Blank` → `+ Custom row`.** You chose "empty box, hand-draw on top afterwards"; the review says don't encourage detached ink. These are compatible and the plan reflects the merge: v1 renders an empty box and the user may draw into it, but we document rather than advertise it, and we do not build affordances that imply the ink is attached. Nothing is lost relative to your choice.

## Net effect on scope

The review adds real work in Phase 1 — the usage snapshot, the swatch-renderer split, the prepared-layout lifecycle — and removes work at the back end by cutting the exporters and PDF. My estimate is roughly scope-neutral, with the balance shifted earlier, which is the right direction: the additions are all contract work that is expensive to retrofit, and the deletions are all leaf work that is cheap to add later.

The two structural additions I would defend as worth the cost even under schedule pressure are the usage/presentation split and the immutable swatch snapshot. Both are the kind of thing that is nearly free now and very expensive after there is field data and a second caller.

## Where a decision is needed from you

The plan lists seven in §13. The two that block Phase 1 are:

1. **Capacity behaviour** (§1 above) — because it revises something you already decided.
2. **Disabled-legend recovery** — because it determines whether the Enable switch ships at all in v1.

The remaining five (Standard-weight meaning, heading editability, label wrapping, bounds limits, custom-row delete confirmation) can be settled during Phase 1 without reordering anything.
