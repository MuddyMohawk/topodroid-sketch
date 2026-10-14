# Compatibility

- Large PNG exports created before bounded raster rendering can omit area fills on older Android; regenerate affected PNGs from the original sketches before using them for interchange.

- Cross-section station, splay, and out-of-plane visibility selections are Sketch-specific metadata carried by the optional `section_references.json` entry in native survey ZIPs; a future Vanilla conversion path must translate or omit these choices while preserving the section geometry.

- Per-survey symbol style and drawing-preset memories reference local preset slots and are omitted from survey ZIP exports; imported surveys start without learned choices.

- TopoDroid Sketch now matches current Vanilla persisted leg-type IDs (`NORMAL = 0` for plain splays, `XSPLAY = 2`, and `BACK = 3`); earlier Sketch databases and archives using interim IDs `2`, `3`, and `11` are not migrated and can be misclassified when reopened or imported.
- Vanilla briefly used the same interim numbering after the upstream change on April 6, 2026, then reverted it on May 14, 2026 without migration; surveys created by affected Vanilla builds can likewise retain incompatible leg-type values.
- Compass `.dat` export combines an immediately paired named reverse backsight with its foresight after normalizing direction, emitting one `A`-to-`B` record instead of duplicated traverse length.
- Compass `.dat` import now uses the Android provider's display name, so valid files exposed through opaque URI IDs retain their `.dat` suffix; multi-survey Compass exports are still read into the first survey pending confirmation of the intended behavior.
- Native Sketch ZIP import preserves fixed-point horizontal and vertical accuracy even when the point has no converted coordinate system.
- GPX export filenames now resolve back to the GPX format through the shared extension registry.
- Sketch symbol files now carry private `sketch_picker_category`, `sketch_picker_section`, and `sketch_search_terms` directives; a future Vanilla symbol conversion path must strip or safely ignore these directives while preserving the standard symbol definition.

- Survey shot instrument addresses now come from the receiving connection. Earlier quick swaps could persist the selected device instead of the actual source in survey databases and native ZIPs; existing instrument attribution is not reconstructed.

## Unsupported import/export formats

- TopoDroid Sketch survey ZIP import intentionally ignores embedded `points.zip`, `lines.zip`, and `areas.zip`, and Sketch export does not create them. Native Sketch ZIPs therefore render against the receiving installation's symbol definitions and code: differing builds or symbol state can reverse the apparent side of directional lines or omit newer smart-point text such as passage height. A future dedicated Vanilla import path may need to translate Vanilla-only symbol references and definitions into Sketch-standard tools before opening drawings.
