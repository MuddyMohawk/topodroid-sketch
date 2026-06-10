# Adding Custom User Lines to Vanilla TopoDroid

TopoDroid lets you add your own line types to the drawing palette — for example a
fine / standard / thick set of "user" lines — without recompiling the app. Each line
is a small plain-text *symbol file* you drop into TopoDroid's line-symbol folder. Reload
the palette and your lines appear alongside the built-in ones.

This guide covers where the files live, the exact file format, a ready-to-use
three-width example, and how to make the lines show up.

---

## 1. Where the files go

TopoDroid keeps its drawing symbols in three folders inside its private app storage —
`point`, `line`, and `area`. Custom **lines** go in the `line` folder.

**TopoDroid v6 and later:**

```
/storage/emulated/0/Android/data/com.topodroid.TDX/files/line/
```

**Older versions (pre-v6):**

```
/sdcard/TopoDroid/symbol/line/
```

A few things to know before you go looking:

- This is **not** your survey folder. Survey data lives in `Documents/TDX/`; symbols do not.
- On **Android 11+**, the `Android/data/...` path is sandboxed. Most file-manager apps and
  plain USB/MTP browsing cannot see into it. To reach it you'll need one of:
  a file manager that supports Storage Access Framework access to `Android/data`
  (many do via "Add storage" → the `Android/data` folder), the device's built-in **Files**
  app, or a PC over USB using `adb push` / `adb pull`.
- This folder is **deleted when you uninstall TopoDroid**. Keep a backup copy of your
  custom line files somewhere safe.

---

## 2. The symbol file format

A line symbol is a plain-text file with **no file extension**. The filename *is* the
symbol's internal name — e.g. a file literally named `user-fine`.

Here is a complete, valid template:

```
symbol line
name My_Fine_Line
th_name u:my-fine-line
group u:user
color 0xff0000 0xff
width 1.0
level 1
roundtrip 3
endsymbol
```

Field by field:

| Line | Meaning |
|------|---------|
| `symbol line` | Header that starts the definition. Always the first line. |
| `name` | The label shown in the palette. **Underscores become spaces**, so `My_Fine_Line` displays as "My Fine Line". |
| `th_name` | The Therion / internal name. **Must be unique.** Prefix user lines with `u:` so they export as user-defined lines and round-trip safely into vanilla TopoDroid and Therion. |
| `group` | Which palette group the line belongs to. `u:user` (written exactly like that, with the `u:` prefix) keeps it in the user-lines group. |
| `color` | Two hex tokens: **RGB** then **alpha**. `0xff0000 0xff` is opaque red. (`0x0000ff 0xff` is blue, `0x000000 0xff` is black, and so on.) |
| `width` | Stroke width. Use small whole numbers like `1`, `2`, `3` for a fine/standard/thick set. |
| `level` | Drawing layer. `1` is the user level — leave it at `1`. |
| `roundtrip` | Export detail level. `3` is the normal value — leave it at `3`. |
| `endsymbol` | Closes the definition. |

Optional extras the format also accepts:

- `closed yes` — makes the line a closed loop (like a rock border).
- `dash 6 4` — a dashed line (on-length, off-length pairs).
- `style straight` — draw as straight segments instead of smooth curves.
- Lines beginning with `#` are comments and are ignored.

---

## 3. A ready-made fine / standard / thick set

To get three user lines of increasing width, create **three files** in the `line` folder.

**File `user-fine`:**

```
symbol line
name User_Fine
th_name u:user-fine
group u:user
color 0x0000ff 0xff
width 1.0
level 1
roundtrip 3
endsymbol
```

**File `user-standard`:**

```
symbol line
name User_Standard
th_name u:user-standard
group u:user
color 0x00aa00 0xff
width 2.0
level 1
roundtrip 3
endsymbol
```

**File `user-thick`:**

```
symbol line
name User_Thick
th_name u:user-thick
group u:user
color 0xff0000 0xff
width 3.0
level 1
roundtrip 3
endsymbol
```

That gives you a blue 1-wide, green 2-wide, and red 3-wide line. Change the `color` and
`width` values to taste; just keep each `th_name` unique.

---

## 4. Make the lines appear

1. Save your symbol file(s) into the `line` folder (Section 1).
2. **Restart TopoDroid**, or in a sketch open the line tool picker → **Palette** → reload
   the drawing tools.
3. Open a sketch and tap the line-tool picker. If a new line isn't showing, open
   **Palette** and tick it to enable it.
4. The lines now appear in the line list. The ones you use show up in the quick bar of
   recently used tools.

---

## 5. Tips and caveats

- **Easiest start:** rather than typing a file from scratch, copy an existing line file
  already in the `line` folder, rename the copy, and just change `name`, `th_name`,
  `color`, and `width`. That guarantees the format is right.
- **Keep `th_name` unique** and prefixed with `u:`. Duplicates collide; a missing `u:`
  prefix breaks the user-line round-trip.
- **Back up your files** — uninstalling TopoDroid wipes the symbol folders.
- **Export behaviour:** these lines export to Therion as `u:yourname` (user-defined
  lines). If you process the sketch in Therion and want them rendered, you'll need to
  supply a MetaPost definition for each on the Therion side. If you only ever screenshot
  or export the sketch as an image (PNG/PDF), this doesn't matter — the line just draws
  with the color and width you set.
