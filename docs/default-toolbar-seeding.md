# Default Sketch Toolbars: How It Works Today, Why Pre-Seeding Fails, and the Plan

Audience: developers who have attempted the "two default toolbars with pre-seeded symbols"
feature and can't figure out why the seeded symbols never show up on a device.

The toolbar in question is the **symbol toolbar** (line/point/area brush rows) in
`DrawingWindow`, *not* the preset bar (`mLayoutToolsPreset` / `mBtnPreset`, which only switches
brush style presets and never touches symbol enablement).

---

## 1. The data model (`ItemDrawer`)

`DrawingWindow extends ItemDrawer`. All toolbar state lives in **static** fields of
`ItemDrawer` (`src/com/topodroid/TDX/ItemDrawer.java`):

| Field | Meaning |
|---|---|
| `mToolbarPoint[8][16]`, `mToolbarLine[8][16]`, `mToolbarArea[8][16]` | Per-row symbol slots. 8 = `TOOLBAR_ROWS_MAX`, 16 = `TOOLBAR_SLOTS_MAX`. Every row holds **three** parallel symbol lists, one per type. |
| `mToolbarLock[8]` | Per-row lock: `SymbolType.POINT/LINE/AREA`, or `SymbolType.UNDEF` = unlocked. |
| `mToolbarCurrentType` | The "global" type. An **unlocked** row displays this type; a **locked** row always displays its lock type (`getToolbarDisplayType(row)`). So "unlocked top bar showing points" == row unlocked + `mToolbarCurrentType == POINT`. |
| `mToolbarActiveSlot[8]` | Which slot the picker overwrites / which is highlighted. |
| `mRecentPoint/Line/Area` | Legacy aliases of `mToolbar*[0]` (row 0). Still used by old code paths. |

Only the first `TDSetting.mToolbarRows` rows and first `TDSetting.mToolbarSlots` slots are
*visible*; the rest of the 8×16 arrays still exist and still get saved/loaded.

Relevant settings (`TDSetting` / `TDPrefKey`), all already committed:

- `mToolbarUpdate` default `3` = `TOOLBAR_UPDATE_MANUAL` → `ItemDrawer.isManualToolbar()` true.
  Everything below assumes manual mode; the legacy "recent symbols" modes (0/1/2) use a
  completely separate code path.
- `mToolbarSlots` default `8`, `mToolbarRows` default `2`. So "2 toolbars by default" is done.

### Row ordering on screen

`DrawingWindow.rebuildManualToolbarRows()` adds row layouts to `mLayoutTools`. The
**uncommitted worktree change** adds `getToolbarRowForViewIndex()` /
`getToolbarViewIndexForRow()` which invert the mapping: view index 0 (top) = highest row
index, last view index (bottom) = row 0. Consequence: **row 0 is the bottom bar, and raising
the row count adds new rows on top**. This part of the spec is implemented and covered by
`manualRows_displayAboveRowZeroAsTheyAreAdded` in `ToolbarRowsInstrumentedTest`.

---

## 2. Lifecycle: who loads/saves toolbar state, and when

```
App start
  TopoDroidApp → BrushManager.loadAllSymbolLibraries()
    reloadLineLibrary():
      SketchLineSymbolManager.ensureLineSymbols()   // writes user-fine/-standard/-thick .lin files
      new SymbolLineLibrary()                       // reads symbol files, enabled-state from DB config
      SketchLineSymbolManager.onLineLibraryLoaded() // force-enables user lines, legacy recent_lines seed

DrawingWindow.onStart()
  loadRecentSymbols(mApp_mData)
    → RecentSymbolsTask(LOAD)  [AsyncTask, background]
        → ItemDrawer.loadManualToolbarSymbols(data)     // ← THE LOAD + SEED CODE
    → onPostExecute → DrawingWindow.onRecentSymbolsLoaded()
        → setBtnRecentAll()                              // picks current type/symbol, redraws rows

DrawingWindow.onStop()
  saveRecentSymbols(mApp_mData)
    → RecentSymbolsTask(SAVE)
        → ItemDrawer.saveManualToolbarSymbols(data)      // writes ALL 8 rows, EVERY time
```

Persistence is the `configs` key/value table of the main app database
(`DataHelper.getValue/setValue`). Keys written per row `N` (0..7):

```
toolbar_row_N_points   "name name ..."   (th-names, space separated)
toolbar_row_N_lines
toolbar_row_N_areas
toolbar_row_N_lock     "point"|"line"|"area"|"unlocked"
toolbar_row_N_slot     active slot int
```

plus legacy single-row keys `toolbar_points`, `toolbar_lines`, `toolbar_areas` (read as a
row-0 fallback by `getToolbarRowNames()`).

**Gotcha:** `DataHelper.setValue()` silently rejects empty strings. A row serialized to ""
does *not* clear the old DB value — stale content resurrects on the next load.

### Symbol enablement (critical background)

A toolbar slot only keeps a symbol if that symbol is **enabled** in its library. Enablement
comes from DB config keys `p_<thname>` / `l_<thname>` / `a_<thname>`:

- `SymbolLibrary.makeConfigList()` (run on every library construction) sets
  `enabled = getSymbolEnabled(prefix+thname)`; an **absent key means disabled**.
- On the *first* encounter of a symbol file with no DB key, `loadUserPoints()` /
  `loadUserLines()` enables it only if it's in the hard-coded `DefaultPoints` /
  `DefaultLines` arrays, and writes the key.
- `DefaultPoints` (SymbolPointLibrary) contains pillar, stalactite, stalagmite, water-flow,
  continuation, air-draught… **but NOT soda-straw**.
- `reference` is a built-in *system* point (`loadSystemPoints()`); unlike `user`/`label`, its
  `p_reference` config key is never written, so `makeConfigList()` leaves it **disabled**
  unless something else enables it.
- `DefaultLines` (SymbolLineLibrary) = arrow, border, chimney, pit, wall-presumed,
  rock-border, slope — **not** water-flow or ceiling-meander. The line seeding code works
  around this with `enableDefaultToolbarLine()` (force-enables at runtime, in config, and in
  the DB).
- Opening a plot whose `.tdr` file does **not** exist yet (i.e. every brand-new sketch!) runs
  `BrushManager.makeEnabledListFromConfig()` + `ItemDrawer.resetRecentSymbols()`
  (`DrawingSurface.modeloadDataStream`), which **nulls any toolbar slot whose symbol is not
  config-enabled**. Seeded symbols survive this only if their config keys were written.

---

## 3. The load/seed algorithm, step by step

`ItemDrawer.loadManualToolbarSymbols(data)` (with the worktree changes):

1. `mToolbarCurrentType = POINT` (worktree change; was LINE).
2. For every row 0..7: read lock and active slot from DB.
   `lockFromString(value, row)` (worktree change): if the DB value is **null**, row 0
   defaults to `LINE`-locked, others unlocked. If the DB has *any* stored value (e.g.
   "unlocked" written by a previous save), that wins.
3. Row 0, per type, `loadManualToolbarList(..., seedDefaultLines=true)`:
   - `names = toolbar_row_0_<type>` falling back to legacy `toolbar_<type>`.
   - **Seed path** — only if `names` is empty **and** type == LINE: walk
     `DEFAULT_MANUAL_LINES` = `{water-flow, ceiling-meander, floor-meander, pit, chimney,
     user-fine, user-standard, user-thick}`, look each up by th-name, force-enable it
     (`enableDefaultToolbarLine`), put it in the next slot.
   - **Named path** — if `names` is non-empty: parse, keep only symbols that exist **and are
     enabled** (silently drops the rest).
   - Either way, `fillToolbarList()` then pads remaining slots (all 16) with enabled library
     symbols in library order. This is why a "failed" seed still shows a full bar of
     arbitrary symbols — the padding masks the failure.
4. Rows 1..7: `loadOrCopyManualToolbarList` — load saved names if present, otherwise **copy
   row 0's list**. There is no seeding here at all.

Points are never seeded anywhere. The top bar's point content is simply "all enabled points,
alphabetical" (air-draught, blocks, clay, continuation, …) from the padding step.

`saveManualToolbarSymbols(data)` unconditionally writes all 8 rows' names + lock + slot, plus
the legacy keys.

### Where the picker/lock UI hooks in

- Picker selection: `itemPickerSelected(type, index, row)` → `replaceManualToolbarSymbol`
  overwrites the row's active slot; if the row is unlocked it also sets
  `mToolbarCurrentType = type` (this is how the user flips an unlocked bar between
  points/lines/areas).
- Lock toggle: `itemPickerLockChanged(row, locked, type)` → `setToolbarRowLock`.
- `setBtnRecentAll()` (worktree change): on load completion, sets current type to POINT and
  selects the first selectable symbol in an **unlocked** row (`setCurrentUnlockedToolbarSymbol`),
  i.e. the top point bar — matching "default toolset = point symbols".

---

## 4. Root causes: why the seeded defaults never appear

**RC1 — The seed is gated on DB keys being absent, but the keys are written on every
`onStop`.** This is the big one. The first time *any* sketch window is closed,
`saveManualToolbarSymbols` writes `toolbar_row_*` (and legacy `toolbar_lines`) for all 8
rows. From then on the seed path is unreachable forever — including on every device that ever
ran a build *before* the current seed code, or where the seed half-fired. There is no seed
version marker and no forced re-seed, so iterating on `DEFAULT_MANUAL_LINES` appears to "do
nothing" on any device that has ever opened a sketch.

**RC2 — `setValue` rejects empty values**, so clearing a row never clears its DB key; stale
symbol lists resurrect and keep the seed gate closed.

**RC3 — There is no point seeding at all.** The spec's top bar (reference, pillar,
stalactite, stalagmite, water-flow, soda-straw, continuation, air-draught) cannot appear from
this code; the point list is whatever `fillToolbarList` pads in.

**RC4 — `DEFAULT_MANUAL_LINES` doesn't match the spec.** It has `floor-meander` and lacks
`section`; spec order is water-flow, section, ceiling-meander, pit, chimney, user-fine,
user-standard, user-thick.

**RC5 — Enablement coupling.** The named load path silently drops disabled symbols, and
several spec'd symbols are disabled by default: `soda-straw` (not in `DefaultPoints`) and
`reference` (system point, config key never written). Any seeding that doesn't force-enable +
write the `p_`/`l_` config key (the way `enableDefaultToolbarLine` does) will be undone by
the next `makeConfigList()` / `makeEnabledListFromConfig()` / `resetRecentSymbols()` — which,
per §2, runs on every brand-new plot.

**RC6 — Async ordering.** `RecentSymbolsTask` is an `AsyncTask`; seeding races plot loading
and the first toolbar redraw. Worse, if it ever ran before the line library finished loading,
every `getSymbolByThName` lookup would return null, the seed would produce nothing, and the
subsequent save would *persist* the garbage (see RC1). A guard is cheap insurance.

**RC7 — Red herring:** `SketchLineSymbolManager.seedRecentLinesIfNeeded()` (the existing
"pre-seeds the user-sketch lines" code, gated by `personal_sketch_lines_seeded`) writes the
`recent_lines` key. That key is only read by the **legacy** (non-manual) toolbar modes. It
has no effect on the manual toolbar and should not be mistaken for the manual seed.

---

## 5. State of the uncommitted worktree attempt

Already correct (keep these):

- Row/view inversion (`getToolbarRowForViewIndex` etc. + `rebuildManualToolbarRows`): new
  rows appear on top, row 0 stays the bottom bar.
- `mToolbarCurrentType` default POINT + `setBtnRecentAll` selecting POINT in an unlocked row.
- `lockFromString(value, row)`: row 0 defaults to LINE-locked — but only on a virgin DB
  (RC1 neutralizes it on real devices).
- `ToolbarRowsInstrumentedTest` updated for 2 rows, row-0 line lock, row ordering.
- `assets/ai/settings.txt` doc text for rows default = 2.

Not yet done: everything in §6. The test's `DEFAULT_LINE_NAMES` also still encodes the old
line list (RC4).

---

## 6. Implementation plan

All changes in `ItemDrawer.java` unless noted. The strategy that escapes RC1 is a
**versioned, forced seed**: stop inferring "first run" from key absence; store an explicit
seed version and re-seed (overwriting stored rows) whenever the code's version is newer.

### 6.1 Versioned seed gate

```java
static final String KEY_TOOLBAR_SEED = "toolbar_seed_version";
static final int TOOLBAR_SEED_VERSION = 1;   // bump to force a re-seed in future releases

private static boolean needsToolbarSeed( DataHelper data ) {
  if ( data == null ) return true;            // unit tests / no DB
  String v = data.getValue( KEY_TOOLBAR_SEED );
  int cur = 0;
  if ( v != null ) try { cur = Integer.parseInt(v.trim()); } catch (NumberFormatException e) {}
  return cur < TOOLBAR_SEED_VERSION;
}
```

In `loadManualToolbarSymbols`: if `needsToolbarSeed(data)`, run the seeding of §6.2
**ignoring stored row names**, then `data.setValue(KEY_TOOLBAR_SEED, "1")`. Otherwise load
stored names as today. This re-seeds existing devices exactly once per version bump (it
overwrites their saved layout once — acceptable and exactly what's wanted while the defaults
are being established).

**Guard (RC6):** only mark the seed version as done if the libraries were actually loaded:
`BrushManager.getLineLibSize() > 0 && BrushManager.getPointLibSize() > 0`. If not, seed
nothing and leave the version unset so the next load retries.

### 6.2 The seed itself

```java
private static final String[] DEFAULT_ROW0_LINES = {
  SymbolLibrary.WATER_FLOW, SymbolLibrary.SECTION, "ceiling-meander",
  SymbolLibrary.PIT, SymbolLibrary.CHIMNEY,
  "user-fine", "user-standard", "user-thick"          // replaces DEFAULT_MANUAL_LINES
};
private static final String[] DEFAULT_ROW1_POINTS = {
  SymbolLibrary.REFERENCE, SymbolLibrary.PILLAR, SymbolLibrary.STALACTITE,
  SymbolLibrary.STALAGMITE, SymbolLibrary.WATER_FLOW, SymbolLibrary.SODA_STRAW,
  SymbolLibrary.CONTINUATION, SymbolLibrary.AIR_DRAUGHT
};
```

Seeding steps (replacing the body of the current seed branch):

1. Generalize `enableDefaultToolbarLine` to
   `enableDefaultToolbarSymbol(int type, Symbol s, DataHelper data)` using prefix
   `"p_"/"l_"/"a_"` (`symbol.setEnabled(true); symbol.setConfigEnabled(true);
   data.setSymbolEnabled(prefix + s.getThName(), true)`). This is what keeps `soda-straw`,
   `reference`, `water-flow`, `ceiling-meander` alive through
   `makeEnabledListFromConfig()`/`resetRecentSymbols()` (RC5/RC6).
2. Seed `mToolbarLine[0]` from `DEFAULT_ROW0_LINES`, `mToolbarPoint[1]` (and, for
   consistency, `mToolbarPoint[0]`) from `DEFAULT_ROW1_POINTS`, force-enabling each. Keep the
   existing `fillToolbarList` padding afterwards — slots past 8 are invisible at the default
   slot count and it protects larger slot settings.
3. Locks: `mToolbarLock[0] = SymbolType.LINE`; all other rows `TOOLBAR_LOCK_UNLOCKED`.
   `mToolbarCurrentType = SymbolType.POINT`. Active slots = 0.
4. Rows ≥ 2 keep the copy-row behavior ("don't particularly matter").
5. **Immediately persist**: call the existing `saveManualToolbarSymbols(data)` (it writes
   names + locks + slots), then write `KEY_TOOLBAR_SEED`. Persisting the names right away
   means the named-load path (which is stable) is used on every subsequent load, and the
   defeated-empty-`setValue` problem (RC2) can't bite because the seeded strings are non-empty.

Note on `section`: the line `section` is a system line, always enabled. It is forbidden only
inside x-section plots (`forbidLineSection`), where tapping its slot correctly refuses — by
design, leave as is.

### 6.3 Cleanups

- Delete `DEFAULT_MANUAL_LINES` (superseded). Leave
  `SketchLineSymbolManager.seedRecentLinesIfNeeded()` alone — it serves the legacy toolbar
  modes — but add a comment that it does **not** feed the manual toolbar (RC7).
- `lockFromString(value, row)`'s row-0-default can stay as a belt-and-braces fallback; the
  forced seed makes it mostly moot.

### 6.4 Tests (`ToolbarRowsInstrumentedTest`)

- Update `DEFAULT_LINE_NAMES` to the new row-0 list (note: assertions compare `getThName()`,
  which is the deprefixed name — `user-fine`, not `u:user-fine`).
- Add `DEFAULT_POINT_NAMES` and assert `mToolbarPoint[1]` (and the display type of row 1 ==
  POINT, row 0 == LINE, row 0 locked / row 1 unlocked — partially present already).
- Add a re-seed test: pre-populate via `setToolbarRowLock`/arrays, call
  `loadManualToolbarSymbols(null)` and assert defaults win (with `data == null`,
  `needsToolbarSeed` returns true, so this exercises the forced path).
- Keep `manualRows_displayAboveRowZeroAsTheyAreAdded` as is.

### 6.5 On-device verification checklist

1. Fresh install (or `adb shell pm clear`): open any plot → bottom bar = 8 spec'd lines,
   locked (lock glyph), top bar = 8 spec'd points, unlocked; drawing tool preselected from
   the top bar.
2. Close the sketch, reopen → identical (proves save/load round-trip of seeded names).
3. **Upgrade path**: install over a build that already saved toolbar keys → seeded layout
   appears once (version bump), user edits afterwards persist.
4. Create a brand-new plot (the missing-`.tdr` path) → seeded symbols still present
   (proves config-enable writes, RC6).
5. Raise rows to 3 in settings → new bar appears **on top**; rows 0/1 untouched.
6. Open an x-section plot → section line slot shows but refuses selection with the toast.

---

## 7. File/symbol quick reference

| What | Where |
|---|---|
| Toolbar arrays, load/save/seed | `src/com/topodroid/TDX/ItemDrawer.java` (`loadManualToolbarSymbols`, `saveManualToolbarSymbols`, `loadManualToolbarList`, `fillToolbarList`, `enableDefaultToolbarLine`, `lockFromString`) |
| Async load/save trigger | `src/com/topodroid/TDX/RecentSymbolsTask.java`; `DrawingWindow.onStart/onStop` |
| Row UI build, redraw, selection | `src/com/topodroid/TDX/DrawingWindow.java` (`rebuildManualToolbarRows` ~2727, `redrawManualToolbars` ~2773, `setBtnRecentAll` ~11523, `setCurrentUnlockedToolbarSymbol` ~11371, `itemPickerSelected` via `ItemDrawer`) |
| Settings & defaults | `src/com/topodroid/prefs/TDSetting.java` (~86–97), `TDPrefKey.java` (~206–208, 855) |
| Symbol enablement | `SymbolLibrary.makeConfigList/makeEnabledListFromConfig`, `SymbolPointLibrary.DefaultPoints` + `loadSystemPoints` (reference!), `SymbolLineLibrary.DefaultLines` |
| New-plot enable reset | `DrawingSurface.modeloadDataStream` (~1306) |
| User sketch lines + legacy recent seed | `src/com/topodroid/TDX/SketchLineSymbolManager.java` |
| DB config table | `DataHelper.getValue/setValue/getSymbolEnabled` (~5530+) |
| Tests | `app/src/androidTest/java/com/topodroid/TDX/ToolbarRowsInstrumentedTest.java` |
