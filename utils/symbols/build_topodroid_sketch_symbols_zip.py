#!/usr/bin/env python3
"""Rebuild the packaged TopoDroid Sketch symbol pack.

The app's installer (TopoDroidApp.symbolsUncompress) expects zip entries named
    symbols_topodroid_sketch/<type>/<filename>
for example:
    symbols_topodroid_sketch/point/blocks
It strips the leading "symbols_topodroid_sketch/", uses the next path element as
the symbol type (point/line/area), and writes the remaining name into the
private folder.

Run this after editing any file under symbols-git/symbols_topodroid_sketch/,
then rebuild the app. READMEs and directory entries are ignored by the installer,
and skipped here.

Usage (from anywhere):  python utils/symbols/build_topodroid_sketch_symbols_zip.py
"""
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
PACK_DIR = "symbols_topodroid_sketch"
SRC = os.path.join(ROOT, "symbols-git", PACK_DIR)
OUT = os.path.join(ROOT, "res", "raw", f"{PACK_DIR}.zip")

SKIP = {"README", ".gitignore"}


def main():
    if not os.path.isdir(SRC):
        sys.exit(f"source tree not found: {SRC}")
    entries = []
    for typ in sorted(os.listdir(SRC)):           # point, line, area
        tdir = os.path.join(SRC, typ)
        if not os.path.isdir(tdir):
            continue
        for name in sorted(os.listdir(tdir)):
            if name in SKIP or name.endswith("~") or name.endswith("README"):
                continue
            fpath = os.path.join(tdir, name)
            if os.path.isfile(fpath):
                entries.append((f"{PACK_DIR}/{typ}/{name}", fpath))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        for arcname, fpath in entries:
            z.write(fpath, arcname)

    print(f"wrote {OUT}")
    print(f"{len(entries)} entries:")
    for arcname, _ in entries:
        print("  ", arcname)


if __name__ == "__main__":
    main()
