# Compatibility

- TopoDroid Sketch now matches current Vanilla persisted leg-type IDs (`NORMAL = 0` for plain splays, `XSPLAY = 2`, and `BACK = 3`); earlier Sketch databases and archives using interim IDs `2`, `3`, and `11` are not migrated and can be misclassified when reopened or imported.
- Vanilla briefly used the same interim numbering after the upstream change on April 6, 2026, then reverted it on May 14, 2026 without migration; surveys created by affected Vanilla builds can likewise retain incompatible leg-type values.
- Compass `.dat` export currently writes a named backsight as a separate reverse shot instead of combining it with the foresight record, producing duplicated length rather than one `A`-to-`B` FS/BS pair.
- Sketch symbol files now carry private `sketch_picker_category`, `sketch_picker_section`, and `sketch_search_terms` directives; a future Vanilla symbol conversion path must strip or safely ignore these directives while preserving the standard symbol definition.

## Unsupported import/export formats

- TopoDroid Sketch survey ZIP import intentionally ignores embedded `points.zip`, `lines.zip`, and `areas.zip`, and Sketch export does not create them; a future dedicated Vanilla import path may need to translate Vanilla-only symbol references and definitions into Sketch-standard tools before opening drawings.
