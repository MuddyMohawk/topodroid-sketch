# Compatibility

- Current Vanilla TopoDroid and TopoDroid Sketch use different persisted leg-type IDs. Vanilla uses `XSPLAY = 2` and `BACK = 3`, while Sketch retained the interim upstream scheme `SPLAY = 2`, `XSPLAY = 3`, and `BACK = 11`. Importing surveys between them can therefore misclassify plain splays, cross splays, and backsights; cross-version import needs version-aware translation before it can be considered safe.
- Vanilla briefly used Sketch's retained numbering after the upstream change on April 6, 2026, then reverted it on May 14, 2026 without migrating existing survey records. Vanilla surveys created during that interval may contain backsight type `11` and remain unopenable in current Vanilla, while current Vanilla archives can be misread by Sketch as described above.
