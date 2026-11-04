"""r505: creature.py loaded with one line replaced, as r261's mutate.sh does, for search.py and corpus.py."""
import os
import sys
import types

STRIPS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips")
sys.path.insert(0, STRIPS)
from layer import components, read_alpha, spread  # noqa: E402, F401 # for search.py, crowd.py and corpus.py


def load(name, old=None, new=None):
    with open(os.path.join(STRIPS, "creature.py")) as f:
        src = f.read()
    if old:
        assert old in src
        src = src.replace(old, new)
    mod = types.ModuleType(name)
    exec(compile(src, name, "exec"), mod.__dict__)  # noqa: S102 # our own file, one line replaced
    return mod
