#!/usr/bin/env python3
"""Convert an approved master SVG into a TopoDroid point symbol file.

The approved NSS masters exported by drawing tools often contain helper
rectangles and clip-path scaffolding. This converter intentionally keeps only
styled visible stroke paths, applies SVG transforms, recenters the resulting
geometry, and writes TopoDroid's point-symbol path language.
"""

from __future__ import annotations

import argparse
import math
import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Iterable, List, Optional, Sequence, Tuple


COMMAND_RE = re.compile(r"[MmLlCcZz]|[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?")
TRANSFORM_RE = re.compile(r"(matrix|translate|scale)\s*\(([^)]*)\)")
SKIP_TAGS = {"defs", "clipPath", "mask", "pattern", "metadata", "title", "desc"}


@dataclass
class Segment:
    kind: str
    points: Tuple[Tuple[float, float], ...]


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


def parse_floats(text: str) -> List[float]:
    return [float(x) for x in re.findall(r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?", text)]


def multiply(a: Sequence[float], b: Sequence[float]) -> Tuple[float, float, float, float, float, float]:
    aa, ab, ac, ad, ae, af = a
    ba, bb, bc, bd, be, bf = b
    return (
        aa * ba + ac * bb,
        ab * ba + ad * bb,
        aa * bc + ac * bd,
        ab * bc + ad * bd,
        aa * be + ac * bf + ae,
        ab * be + ad * bf + af,
    )


def parse_transform(text: Optional[str]) -> Tuple[float, float, float, float, float, float]:
    matrix = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    if not text:
        return matrix
    for name, raw_args in TRANSFORM_RE.findall(text):
        args = parse_floats(raw_args)
        if name == "matrix" and len(args) == 6:
            nxt = tuple(args)  # type: ignore[assignment]
        elif name == "translate" and len(args) >= 1:
            tx = args[0]
            ty = args[1] if len(args) > 1 else 0.0
            nxt = (1.0, 0.0, 0.0, 1.0, tx, ty)
        elif name == "scale" and len(args) >= 1:
            sx = args[0]
            sy = args[1] if len(args) > 1 else sx
            nxt = (sx, 0.0, 0.0, sy, 0.0, 0.0)
        else:
            continue
        matrix = multiply(matrix, nxt)
    return matrix


def apply_matrix(matrix: Sequence[float], point: Tuple[float, float]) -> Tuple[float, float]:
    a, b, c, d, e, f = matrix
    x, y = point
    return (a * x + c * y + e, b * x + d * y + f)


def is_visible_stroke(elem: ET.Element) -> bool:
    style = elem.attrib.get("style", "")
    stroke = elem.attrib.get("stroke", "")
    if "display:none" in style.replace(" ", "") or "visibility:hidden" in style.replace(" ", ""):
        return False
    if "stroke:none" in style.replace(" ", "") or stroke == "none":
        return False
    return "stroke:" in style or bool(stroke)


def next_number(tokens: Sequence[str], idx: int) -> Tuple[float, int]:
    if idx >= len(tokens) or re.fullmatch(r"[A-Za-z]", tokens[idx]):
        raise ValueError("expected number")
    return float(tokens[idx]), idx + 1


def has_number(tokens: Sequence[str], idx: int) -> bool:
    return idx < len(tokens) and not re.fullmatch(r"[A-Za-z]", tokens[idx])


def parse_path(d: str, matrix: Sequence[float]) -> List[Segment]:
    tokens = COMMAND_RE.findall(d.replace(",", " "))
    out: List[Segment] = []
    idx = 0
    command: Optional[str] = None
    current = (0.0, 0.0)
    start = (0.0, 0.0)

    def pair(relative: bool) -> Tuple[float, float]:
        nonlocal idx, current
        x, idx2 = next_number(tokens, idx)
        y, idx3 = next_number(tokens, idx2)
        idx = idx3
        if relative:
            return current[0] + x, current[1] + y
        return x, y

    while idx < len(tokens):
        if re.fullmatch(r"[A-Za-z]", tokens[idx]):
            command = tokens[idx]
            idx += 1
        if command is None:
            raise ValueError("path data begins without a command")

        op = command
        relative = op.islower()
        upper = op.upper()

        if upper == "M":
            first = True
            while has_number(tokens, idx):
                point = pair(relative)
                current = point
                if first:
                    start = point
                    out.append(Segment("moveTo", (apply_matrix(matrix, point),)))
                    first = False
                else:
                    out.append(Segment("lineTo", (apply_matrix(matrix, point),)))
            command = "l" if relative else "L"
        elif upper == "L":
            while has_number(tokens, idx):
                point = pair(relative)
                current = point
                out.append(Segment("lineTo", (apply_matrix(matrix, point),)))
        elif upper == "C":
            while has_number(tokens, idx):
                x1, idx = next_number(tokens, idx)
                y1, idx = next_number(tokens, idx)
                x2, idx = next_number(tokens, idx)
                y2, idx = next_number(tokens, idx)
                x, idx = next_number(tokens, idx)
                y, idx = next_number(tokens, idx)
                if relative:
                    c1 = (current[0] + x1, current[1] + y1)
                    c2 = (current[0] + x2, current[1] + y2)
                    point = (current[0] + x, current[1] + y)
                else:
                    c1 = (x1, y1)
                    c2 = (x2, y2)
                    point = (x, y)
                current = point
                out.append(Segment("cubicTo", (
                    apply_matrix(matrix, c1),
                    apply_matrix(matrix, c2),
                    apply_matrix(matrix, point),
                )))
        elif upper == "Z":
            if current != start:
                current = start
                out.append(Segment("lineTo", (apply_matrix(matrix, start),)))
        else:
            raise ValueError(f"unsupported SVG path command: {op}")
    return out


def walk_visible_paths(elem: ET.Element, inherited: Sequence[float]) -> Iterable[Tuple[ET.Element, Tuple[float, float, float, float, float, float]]]:
    tag = local_name(elem.tag)
    if tag in SKIP_TAGS:
        return
    matrix = multiply(inherited, parse_transform(elem.attrib.get("transform")))
    if tag == "path" and elem.attrib.get("d") and is_visible_stroke(elem):
        yield elem, matrix
    for child in elem:
        yield from walk_visible_paths(child, matrix)


def transformed_bounds(segments: Sequence[Segment]) -> Tuple[float, float, float, float]:
    pts: List[Tuple[float, float]] = []
    for seg in segments:
        pts.extend(seg.points)
    if not pts:
        raise ValueError("no drawable points found")
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)


def parse_viewbox(root: ET.Element) -> Optional[Tuple[float, float, float, float]]:
    raw = root.attrib.get("viewBox")
    if not raw:
        return None
    vals = parse_floats(raw)
    if len(vals) != 4:
        return None
    return vals[0], vals[1], vals[0] + vals[2], vals[1] + vals[3]


def intersects(a: Tuple[float, float, float, float], b: Tuple[float, float, float, float], pad: float = 0.25) -> bool:
    return not (
        a[2] < b[0] - pad
        or a[0] > b[2] + pad
        or a[3] < b[1] - pad
        or a[1] > b[3] + pad
    )


def normalize_segments(segments: Sequence[Segment], target_max: float) -> List[Segment]:
    min_x, min_y, max_x, max_y = transformed_bounds(segments)
    width = max_x - min_x
    height = max_y - min_y
    if width <= 0 or height <= 0:
        raise ValueError("drawable bounds are empty")
    scale = target_max / max(width, height)
    cx = (min_x + max_x) / 2.0
    cy = (min_y + max_y) / 2.0

    normalized: List[Segment] = []
    for seg in segments:
        pts = tuple(((x - cx) * scale, (y - cy) * scale) for x, y in seg.points)
        normalized.append(Segment(seg.kind, pts))
    return normalized


def fmt(n: float) -> str:
    if math.isclose(n, 0.0, abs_tol=0.0005):
        n = 0.0
    text = f"{n:.3f}".rstrip("0").rstrip(".")
    return "0" if text == "-0" else text


def write_symbol(args: argparse.Namespace, segments: Sequence[Segment]) -> str:
    lines = [
        "encoding utf-8",
        "symbol point",
        f"name {args.name}",
        f"th_name {args.th_name}",
    ]
    if args.group:
        lines.append(f"group {args.group}")
    lines.extend([
        f"orientation {'yes' if args.orientable else 'no'}",
        "color 0xffffff",
        "path",
    ])
    for seg in segments:
        if seg.kind == "moveTo":
            (x, y), = seg.points
            lines.append(f"  moveTo {fmt(x)} {fmt(y)}")
        elif seg.kind == "lineTo":
            (x, y), = seg.points
            lines.append(f"  lineTo {fmt(x)} {fmt(y)}")
        elif seg.kind == "cubicTo":
            (x1, y1), (x2, y2), (x, y) = seg.points
            lines.append(f"  cubicTo {fmt(x1)} {fmt(y1)} {fmt(x2)} {fmt(y2)} {fmt(x)} {fmt(y)}")
    lines.extend(["endpath", "endsymbol", ""])
    text = "\n".join(lines)
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    return text


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("svg")
    parser.add_argument("output")
    parser.add_argument("--name", required=True)
    parser.add_argument("--th-name", required=True)
    parser.add_argument("--group", default="")
    parser.add_argument("--target-max", type=float, default=18.0)
    parser.add_argument("--orientable", action=argparse.BooleanOptionalAction, default=True)
    args = parser.parse_args()

    root = ET.parse(args.svg).getroot()
    segments: List[Segment] = []
    viewbox = parse_viewbox(root)
    for elem, matrix in walk_visible_paths(root, (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)):
        parsed = parse_path(elem.attrib["d"], matrix)
        if viewbox and not intersects(transformed_bounds(parsed), viewbox):
            continue
        segments.extend(parsed)
    if not segments:
        sys.exit(f"no visible stroke paths found in {args.svg}")

    normalized = normalize_segments(segments, args.target_max)
    write_symbol(args, normalized)
    print(f"wrote {args.output} from {len(segments)} path commands")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
