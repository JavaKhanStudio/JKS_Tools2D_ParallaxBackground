"""compare.py BEFORE AFTER — the r528 proof: each case shots.sh ran into BEFORE and AFTER exits the same, and its stills
are pixel-identical (its clip byte-identical). Exit 1 naming each case that differs."""
import hashlib
import sys
from pathlib import Path

import numpy as np
from PIL import Image

before, after = Path(sys.argv[1]), Path(sys.argv[2])
bad = []
for case in sorted(p for p in before.iterdir() if (p / "exit").exists()):
    other = after / case.name
    if not (other / "exit").exists():
        print(f"{case.name}: not run after")
        continue
    a, b = (case / "exit").read_text().strip(), (other / "exit").read_text().strip()
    notes = [] if a == b else [f"exit {a} -> {b}"]
    for f in sorted(case.glob("*.png")):
        g = other / f.name
        if not g.exists():
            notes.append(f"{f.name} missing")
            continue
        x, y = np.asarray(Image.open(f).convert("RGB"), int), np.asarray(Image.open(g).convert("RGB"), int)
        if x.shape != y.shape:
            notes.append(f"{f.name} {x.shape} -> {y.shape}")
        elif (x != y).any():
            notes.append(f"{f.name}: {int((x != y).any(axis=2).sum())} px differ, at most {int(abs(x - y).max())}/255")
    for f in sorted(case.glob("*.mp4")):
        g = other / f.name
        if not g.exists() or hashlib.sha256(f.read_bytes()).digest() != hashlib.sha256(g.read_bytes()).digest():
            notes.append(f"{f.name} differs")
    stills = len(list(case.glob("*.png"))) + len(list(case.glob("*.mp4")))
    print(f"{case.name}: exit {a}, {stills} files, " + ("; ".join(notes) if notes else "identical"))
    if notes:
        bad.append(case.name)
sys.exit(f"differ: {' '.join(bad)}" if bad else 0)
