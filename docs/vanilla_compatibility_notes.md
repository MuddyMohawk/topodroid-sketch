# Compatibility

- TopoDroid Sketch now matches current Vanilla persisted leg-type IDs (`NORMAL = 0` for plain splays, `XSPLAY = 2`, and `BACK = 3`); earlier Sketch databases and archives using interim IDs `2`, `3`, and `11` are not migrated and can be misclassified when reopened or imported.
- Vanilla briefly used the same interim numbering after the upstream change on April 6, 2026, then reverted it on May 14, 2026 without migration; surveys created by affected Vanilla builds can likewise retain incompatible leg-type values.
- Compass `.dat` export currently writes a named backsight as a separate reverse shot instead of combining it with the foresight record, producing duplicated length rather than one `A`-to-`B` FS/BS pair.
- Survey ZIP export does not currently add `points.zip`, `lines.zip`, or `areas.zip`, even when `DISTOX_ZIP_WITH_SYMBOLS` is enabled; the importer can consume those entries, so custom symbol files are not preserved by a current Sketch export/import round trip.
