# Understanding the TopoDroid Palette

A guide to how drawing symbols ("tools") are stored, loaded, enabled, and **ordered** in
TopoDroid Sketch — and how to drive it for a freehand / trace-over workflow.

The palette feels confusing because the word "palette" is used for several different things,
and because the order you see is produced by rules you can't drag-and-drop. Once you separate
the concepts below it becomes predictable.

---

## 1. The four things people call "the palette"

| Concept | What it actually is | Where it lives |
|---|---|---|
| **Tool / symbol** | One drawable thing: a point (`stalagmite`), a line (`wall`), or an area (`water`). | A plain-text file (or a built-in definition). |
| **Library** | The *master catalogue* of every tool currently installed, per type. | `SymbolPointLibrary`, `SymbolLineLibrary`, `SymbolAreaLibrary`, held by `BrushManager`. |
| **Palette (enabled set)** | The *subset* of the library that is switched **on**. Everything else is hidden from the pickers. | A per-symbol flag, persisted in the database. |
| **Recent bar** | The 6 quick-tap buttons at the bottom of the sketch, per type. | An in-memory MRU cache (`ItemDrawer.mRecent*`). |

Three tools (`user`, `label`, `section`…) you can never turn off are **system tools**.
Everything else is a file you can edit, delete, or add to.

Key mental model:

```
files on disk ─load─▶ LIBRARY (everything installed)
                         │  filter by "enabled" flag
                         ▼
                      PALETTE (what the pickers show)
                         │  most-recently-used
                         ▼
                   RECENT BAR (6 quick buttons)
```

---

## 2. Where tools come from (how things are *added*)

There are three sources, loaded in this order into each library
(`SymbolPointLibrary` etc., see `loadSystemPoints` / `loadUserPoints`):

1. **System tools — built into the code, always present, always enabled, cannot be edited or
   deleted.**
   - Points: `user`, `label`, `section` (plus the internally-managed `picture` and `reference`).
   - Lines: `user`, `wall`, `section`.
   - Areas: `user`, `water`.
   These are hard-coded in the `loadSystem…()` methods. A custom file with the same name is
   **skipped**.

2. **Bundled tool packs — text files unzipped into the app's private folders.**
   On first run TopoDroid installs the **speleo** pack only. Eight more ship inside the apk as
   `res/raw/symbols_*.zip` (`extra`, `mine`, `geo`, `archeo`, `anthro`, `paleo`, `bio`, `karst`)
   and are installed on demand (`TopoDroidApp.installSymbols` → `reloadSymbols`). Files land in:
   ```
   Android/data/com.topodroid.TDX.sketch/files/point/   (vanilla: com.topodroid.TDX)
                                              /line/
                                              /area/
   ```
   (`Documents/TopoDroid Sketch/…` is the user-facing data folder; the symbol definition files
   themselves live in the app-private `files/` tree above, which is keyed by the applicationId.)

3. **Custom tool files — anything you drop into those `point` / `line` / `area` folders.**
   After a **cold restart** TopoDroid scans the folders and loads them. This is how you add a
   symbol that doesn't ship with the app. (The format is documented in
   `assets/man/page_symbol_point.htm`, `…_line.htm`, `…_area.htm`.)

### Installing / replacing packs
Main window **PALETTE menu → "Drawing Tools" dialog** (`SymbolReload`):
- **Install** = add the checked packs *without* removing what's there.
- **Replace** = delete the currently installed tool files first, then install the checked packs.
  Your **enabled choices survive a Replace** because they're stored in the database keyed by
  tool name, not in the files — re-installing a pack later restores its on/off state.

> Gating: the extra packs only appear when the secondary **"Palette" setting** is enabled and
> the app is in **Expert** activity level (`MainWindow`: `mWithPalettes = TDLevel.overExpert &&
> TDSetting.mPalettes`).

### Fork addition — the three Sketch lines
This fork programmatically generates three `user`-group lines on every line-library load
(`SketchLineSymbolManager`): **Sketch_Fine, Sketch_Standard, Sketch_Thick**
(th-names `u:user-fine`, `u:user-standard`, `u:user-thick`). They are written into the `line`
folder, force-enabled, and **seeded once into the recent-line bar**. Their width/colour come from
settings (defaults 1.0× / 2.0× / 5.0×). This is the backbone of the freehand line workflow and
the reason the bottom bar already shows usable trace lines on a fresh install.

---

## 3. How ordering works (the part that's actually confusing)

There are **two different orderings**, and they follow different rules.

### a) Order in the library and in the full picker (the `>>` list)
Built in `SymbolLibrary.addSymbol` + `sortSymbolByName`:

1. **System tools come first, in fixed code order** (e.g. lines: `user`, `wall`, `section`).
2. **Everything else is sorted alphabetically by *display name*** — `sortSymbolByName(systemNr)`
   sorts from the first non-system entry to the end, comparing `getName()` (the localized
   `name`, not the th-name).

So the picker order is: *system tools (fixed) → all enabled tools A→Z by display name.*
**You cannot drag to reorder.** The only lever you have is the **display name**: if you want a
custom tool to sit near the top of the list, give it a name that sorts early (this is why the
fork's lines are named `Sketch_…`). Internally there is also a red-black tree keyed by th-name,
but that's only for fast lookup — it does not affect the visible order.

Note: ordering is by name, so changing the app language can change the order, and two tools with
the same name collide (the second is rejected — "already in library").

### b) Order in the 6-button recent bar
This is **not** alphabetical — it's a most-recently-used cache (`ItemDrawer.updateRecent`),
with 6 slots per type. How a freshly-used tool is inserted is controlled by
**Settings → "Toolbar update" ("How to insert symbols in bottom bar")**, i.e.
`TDSetting.mToolbarUpdate`:

| Mode | Behavior |
|---|---|
| **0** (default) | Replace the **oldest** slot with the new tool; other buttons keep their place. Buttons feel "stable" but your last-used isn't guaranteed to be first. |
| **1** | Insert the new tool at the **front**, shift the rest right (classic MRU). Last-used is always button 1. |
| **2** | Insert at the front **and drop the oldest**. Like mode 1 but strictly bounded. |

On startup the recent bar is seeded with the **first N enabled tools** in library order
(`setRecentSymbols`), then this fork prepends the three Sketch lines. Disabling a tool clears it
from the recent bar (`resetRecentSymbols`). The recent sets are saved to the database between
sessions.

To reach anything **not** in the 6 buttons, tap **`>>`** to open the **Drawing Tool Picker**
(three tabs: Point / Line / Area). Selecting there makes it the current tool and pushes it into
the recent bar.

---

## 4. How enabling & persistence work (the on/off state)

Each symbol carries **two** boolean flags (`Symbol.java`):

- **`enabled`** — the *live* state used while drawing (what the pickers honor right now).
- **`config`** — the *persisted* state, saved in the database (rows keyed `p_<thname>`,
  `l_<thname>`, `a_<thname>`).

The enable dialog edits the live flag; on **BACK** it copies live → config and writes the DB
(`SymbolEnableDialog.SaveSymbols` → `makeConfigEnabledList`). On load, config → live
(`makeConfigList` / `setEnabledConfig`). First-run defaults come from small hard-coded lists
(`DefaultPoints`, `DefaultLines`, `DefaultAreas`) — a tool with no DB row yet is enabled only if
it's in that list; once a DB row exists, the DB wins.

### Palette scopes (global vs survey vs sketch)
This is the layer the manual (`page_tools.htm`) calls the "global / survey / sketch" palettes:

- **Global palette** = the database config above. Edited from the **Main window PALETTE button →
  `SymbolEnableDialog`**. This is your default for new surveys.
- **Survey palette** = starts as a copy of the global palette. If you open a sketch that uses a
  tool not enabled globally, it's *added* to the survey palette.
- **Sketch palette** = each sketch stores its own enabled set in its file header
  (`BrushManager.preparePalette` serializes enabled th-names; `makeEnabledListFromPalette`
  restores them). On reopen the sketch palette is **merged** with the survey palette. Any item
  whose tool isn't installed is replaced by the **`user`** tool, with the original name kept in
  its Therion options string (so nothing is silently lost). The three `user` tools are always in
  every palette. Edit a sketch's own palette from the **Drawing window PALETTE menu**.

### Groups
A tool may declare a `group`. Groups do exactly one thing: when a sketch references a tool you
don't have, TopoDroid substitutes another tool **from the same group**, falling back to `user`.
Groups are shown (English names) in the enable dialog.

### Canvas views / "levels" — why a tool sometimes won't draw
Separate from enable/disable, each tool has a `level` bitmask (Base/Floor/Decoration/Ceiling/
Artifacts). If "canvas views" are on and you pick a tool whose level isn't currently visible,
selection falls back to `user` (`ItemDrawer.pointSelected`/`lineSelected`/`areaSelected`). So
"my symbol turns into user when I pick it" usually means a hidden view, not a palette problem.

---

## 5. UI map — which control does what

| You want to… | Go to | Code |
|---|---|---|
| Turn tools on/off for everything (global) | Main window **PALETTE** button | `SymbolEnableDialog` |
| Install/replace extra tool packs | Main window **PALETTE menu → Drawing Tools** | `SymbolReload` |
| Turn tools on/off **for this sketch** | Drawing window **PALETTE menu** | `SymbolEnableDialog` |
| Pick a tool not in the 6 buttons | Sketch bottom bar **`>>`** | Drawing Tool Picker |
| Switch the bottom bar between Point/Line/Area | Toggle-palette action (button / S Pen / Active key) | `DrawingWindow` |
| Change how the 6 buttons update | Settings → **Toolbar update** | `TDSetting.mToolbarUpdate` |
| Add/edit a tool definition | Edit files in `…/files/point|line|area`, cold restart | `loadUser*` |

> Activity-level gating: in **Basic** level the enable dialog only exposes **Lines**; **points
> and areas** need **over-Basic** (`TDLevel.overBasic`). The extra-pack installer needs
> **Expert** + the Palette setting.

---

## 6. Best-practice recipe for the trace-over workflow

Because the final map is hand-traced from screenshots, the goal is a **small, fast, legible**
palette — not the 200-symbol superset.

1. **Prune hard.** Enable only the handful of points/lines/areas you actually trace. A lean
   palette keeps the `>>` picker short and means the 6 recent slots are almost always enough.
   (Disabling never deletes — re-enable anytime; the choice is remembered.)
2. **Lean on the fork's Sketch lines + presets.** `Sketch_Fine/Standard/Thick` plus the **P1/P2
   line presets** (fine+spacing 1 vs bezier+spacing ~10) cover most freehand passage drawing
   without touching the symbol set at all.
3. **Set Toolbar update to mode 1 (or 2).** For drawing, "last-used jumps to front" (mode 1)
   feels far better than the default "replace oldest" (mode 0).
4. **Control picker order via display names.** You can't reorder by hand. To make a custom tool
   appear early, rename its `name` so it sorts early (e.g. prefix with `A_` or `Sketch_`). Keep
   `th_name` = filename unchanged for compatibility.
5. **Use the sketch palette per project.** Set the global palette once for your cave's
   convention; individual sketches will carry their own enabled set in their header and merge
   back on open, so collaborators' sketches won't silently drop symbols (they degrade to `user`
   with the name preserved).
6. **After editing tool files, cold-restart.** Library loading happens at startup; new/edited
   files aren't picked up live.
7. **Avoid "Replace" unless you mean it.** It wipes installed tool *files* (custom ones too).
   Your enabled flags survive, but your custom symbol definitions won't — back them up first.

---

## 7. Source map (for future code work)

- `BrushManager` — owns the three libraries; `loadAllSymbolLibraries`, `preparePalette`,
  `makeEnabledListFromPalette`, recent-symbol bridges, all the static paints.
- `SymbolLibrary` (+ `SymbolPointLibrary` / `SymbolLineLibrary` / `SymbolAreaLibrary`) — the
  master list (`mSymbols`) + RB-tree lookup; `addSymbol`, `sortSymbolByName`, `loadSystem*`,
  `loadUser*`, `makeConfigList`, `makeEnabledListFromStrings`, special-index tracking.
- `Symbol` — one tool; `enabled` vs `config` flags, th-name/prefix/group/level/name.
- `SymbolEnableDialog` — the enable/disable ("palette") dialog; saves config on BACK.
- `SymbolReload` — install/replace extra packs.
- `SymbolsPalette` — lightweight set of th-names used for the per-sketch palette serialization.
- `ItemDrawer` — the 6-slot recent bar and `mToolbarUpdate` insertion logic.
- `SketchLineSymbolManager` *(fork)* — generates/enables/seeds the three Sketch lines.
- Manual: `assets/man/page_tools.htm`, `page_tools_list.htm`, `page_symbol_reload.htm`,
  `page_symbol_point|line|area.htm`.
