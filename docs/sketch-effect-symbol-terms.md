# Sketch Effect Symbol Terms

TopoDroid Sketch line symbols can now carry an optional Sketch-only rendering
block named `sketch_effect`. This block improves curved-line rendering while
leaving the normal TopoDroid `effect` block in place for vanilla compatibility.

## Terms

| Term | Meaning |
|---|---|
| `effect` | The original TopoDroid line-symbol block. Vanilla TopoDroid reads this and morphs/stamps it normally. Sketch keeps it as the compatibility fallback. |
| `sketch_effect` | A Sketch-only top-level block that describes improved rendering metadata. It must sit outside the vanilla `effect` block, usually immediately after `endeffect`. |
| `carrier` | A continuous filled ribbon following the drawn centerline. It is used for the solid part of symbols like `wall:clay` or `pit`, avoiding gaps between repeated stamps on curves. |
| `stamp` | A rigid decoration path repeated along the line. It is used for marks such as ticks, ledges, teeth, and short bars. Stamps are rotated to the local tangent but are not morphed. |
| Dash-on segment | The visible portion of a dashed line cycle. For dashed symbols, carriers are clipped to dash-on segments and stamp placement restarts at each dash-on segment. |
| Advance | The repeat distance for rigid stamps. It comes from the width of the vanilla effect geometry, matching the old symbol cadence. |
| Reversed symbol | A line drawn with the reversed flag. Sketch negates carrier y-offsets and uses the reversed stamp geometry, matching vanilla reverse behavior. |

## Syntax

```text
effect
  ...
endeffect
sketch_effect 1
  carrier Y0 Y1
  stamp
    moveTo ...
    lineTo ...
    cubicTo ...
    addCircle ...
  endstamp
endsketch_effect
endsymbol
```

`sketch_effect 1` is versioned so later syntax can evolve without changing the
meaning of current symbol files.

`carrier Y0 Y1` declares one ribbon between two local y offsets from the drawn
line. Multiple carriers are allowed. For example, `ceiling-meander` uses one
carrier above and one below the centerline.

`stamp` uses the same path commands as vanilla line effects: `moveTo`, `lineTo`,
`cubicTo`, and `addCircle`. The stamp should contain only the rigid decoration,
not the solid carrier strip.

## Authoring Rules

- Keep the vanilla `effect` block unchanged unless you are intentionally changing
  vanilla appearance.
- Do not put `sketch_effect` inside `effect`.
- Use carriers for continuous solid strokes that should bend smoothly.
- Use stamps for repeated marks that should keep their shape on tight curves.
- For dashed symbols, author the dash as usual; Sketch clips carriers and resets
  stamp placement per dash-on segment.
- Existing custom symbols do not need `sketch_effect`; they continue to render
  through the current fallback path.

## Compatibility

Sketch reading vanilla symbols: no `sketch_effect` means the normal fallback
renderer is used.

Vanilla reading Sketch symbols: vanilla uses the unchanged `effect` block and
ignores the unknown top-level `sketch_effect` block.

Exporting surveys: no stripping is needed. The extra metadata remains legible to
Sketch and harmless to vanilla.

Windows source filenames may use `=` where installed symbol names use `:`, such
as `wall=clay` for `th_name wall:clay`. Bundled zip entries should use the
`th_name` value (`wall:clay`) so installed/private symbol files stay editable.
