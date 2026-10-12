"""Replace the context of each tag in new_packs.toml inside a packs.toml (r351).

Usage: python3 tools/r351-packs/splice.py PACKS_TOML [OUT]: the other tags' text is left byte for byte.
"""
import re
import sys
import tomllib
from pathlib import Path

new = tomllib.loads((Path(__file__).parent / "new_packs.toml").read_text())
src = Path(sys.argv[1]).read_text()
for tag, table in new.items():
    pattern = re.compile(r'(\[tags\.' + tag + r'\][^\[]*?context = """\n).*?(\n""")', re.DOTALL)
    src, n = pattern.subn(lambda m, t=table: m.group(1) + t["context"].strip("\n") + m.group(2), src, count=1)
    if n != 1:
        sys.exit(f"tag {tag}: no context found")
tomllib.loads(src)
Path(sys.argv[2] if len(sys.argv) > 2 else sys.argv[1]).write_text(src)
