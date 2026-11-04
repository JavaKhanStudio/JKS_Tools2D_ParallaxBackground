"""r514: r513's moved strips through the real gate. disk.sh asks creature.py's command line of the stored cut; this re-keys
each painted strip with layer.py as ai_page does (--mask, its kind's --grow from the round's recipe, ai_page.GROW) and
prints both answers, so the command line's word on a strip can be held to what a run says today.

    python3 tools/r514-read-jobs/gate.py R513_LOG
"""
import json
import os
import re
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
D = os.path.normpath(os.path.join(HERE, "../r239-ai-strips"))
GROW = {"mountains": 8, "hills": 8, "pines": 40, "dunes": 8, "sea": 4, "ruins": 16, "palms": 24}  # ai_page.py's


def kind_of(cut):
    """The strip's kind by its layer's index in the round's --kinds, or None: a probe has no recipe."""
    recipe = os.path.join(os.path.dirname(cut), "..", "recipe.json")
    if not os.path.exists(recipe):
        return None
    with open(recipe) as f:
        m = re.search(r"--kinds (\S+)", json.load(f).get("command", ""))
    i = re.match(r"l(\d+)", os.path.basename(cut))
    return m[1].split(",")[int(i[1])] if m and i and int(i[1]) < len(m[1].split(",")) else None


def gate(item):
    want, cut = item
    stem = cut[:-len("_cut.png")]
    kind = kind_of(cut)
    grow = GROW.get(kind, 8)
    args = ["--mask", f"{stem}_mask.png", "--grow", str(grow)] if os.path.exists(f"{stem}_mask.png") else []
    with tempfile.TemporaryDirectory() as t:
        run = subprocess.run([sys.executable, f"{D}/layer.py", f"{stem}_ai.png", f"{t}/cut.png", *args],
                             check=False, capture_output=True, text=True, env=dict(os.environ, OMP_NUM_THREADS="1"))
    blobs = re.findall(r"(\d+) px blob at x=(\d+) y=(\d+)", run.stderr)
    return f"cli {want} gate {run.returncode} {kind}/grow {grow} {cut.split('/build/', 1)[1]} {blobs or ''}"


def main():
    with open(sys.argv[1]) as f:
        moved = re.findall(r"^\d -> (\d)\s+(\S+)$", f.read(), re.MULTILINE)
    with ThreadPoolExecutor(4) as ex:
        print("\n".join(ex.map(gate, moved)))


if __name__ == "__main__":
    main()
