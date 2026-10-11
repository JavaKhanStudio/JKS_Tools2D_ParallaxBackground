#!/usr/bin/env python3
"""purge-board-shas.py: blank the pre-rewrite commit SHAs quoted on an Atelier board (r334).

Simon (r334): leave the board's notes unrepointed, and purge the old ids, so no agent spends time trying to read a
commit that no longer exists. Every 7-40 hex token that old-shas.py takes for an old SHA (a unique prefix of one in
commit-map.txt, and of no new one) becomes MARK, in a card's detail (where its notes are), question and decision, and
in the board's lessons. updated_at is left as it was. Dry run by default: it prints what it would change. --apply
first writes every row it changes, as it was, to --backup, and refuses without one.

Atelier has no command to edit a note, so this writes to its database: run it with Atelier's Python, which has psycopg,
and ATELIER_DSN (default: the DSN in Atelier's .env, else its default).

  ~/Documents/GitHub/atelier/.venv/bin/python tools/r269-history/purge-board-shas.py --project parallax \\
      [--apply --backup FILE.json]
"""

import argparse
import importlib.util
import json
import os
import sys
from pathlib import Path

import psycopg

HERE = Path(__file__).resolve().parent
MARK = "[pre-r285 commit]"
ATELIER_ENV = Path.home() / "Documents/GitHub/atelier/.env"
DEFAULT_DSN = "postgresql://atelier:atelier@127.0.0.1:5433/atelier"
TARGETS = (("task", "ref", ("detail", "question", "decision")), ("lesson", "id", ("text",)))


def old_shas():
    spec = importlib.util.spec_from_file_location("old_shas", HERE / "old-shas.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    pairs = mod.load_map()
    news = set(pairs.values())

    def is_old(tok):
        return sum(o.startswith(tok) for o in pairs) == 1 and not any(n.startswith(tok) for n in news)

    return mod.TOKEN, is_old


def dsn():
    if os.environ.get("ATELIER_DSN"):
        return os.environ["ATELIER_DSN"]
    lines = ATELIER_ENV.read_text().splitlines() if ATELIER_ENV.exists() else []
    found = [line.split("=", 1)[1] for line in lines if line.startswith("ATELIER_DSN=")]
    return found[0] if found else DEFAULT_DSN


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", required=True)
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--backup")
    args = ap.parse_args()
    if args.apply and not args.backup:
        sys.exit("purge-board-shas: --apply needs --backup FILE")
    token, is_old = old_shas()
    count = 0

    def purge(text):
        nonlocal count
        if not text:
            return text

        def swap(m):
            nonlocal count
            if not is_old(m.group(0)):
                return m.group(0)
            count += 1
            return MARK

        return token.sub(swap, text)

    changes = []
    with psycopg.connect(dsn()) as conn:
        for table, key, cols in TARGETS:
            rows = conn.execute(f"SELECT id, {key}, {', '.join(cols)} FROM {table} WHERE project = %s ORDER BY id",
                                (args.project,)).fetchall()
            for row in rows:
                before = dict(zip(cols, row[2:]))
                after = {c: purge(v) for c, v in before.items()}
                if after != before:
                    changes.append((table, row[0], row[1], before, after))
        for table, _, name, before, after in changes:
            print(f"{table} {name}: " + ", ".join(c for c in before if before[c] != after[c]))
        print(f"{count} old SHA(s) in {len(changes)} row(s) of {args.project}" + ("" if args.apply else " (dry run)"))
        if not args.apply or not changes:
            return 0
        Path(args.backup).write_text(json.dumps(
            [{"table": t, "id": i, "key": str(k), "before": b} for t, i, k, b, _ in changes], indent=1))
        with conn.transaction():
            for table, row_id, _, before, after in changes:
                cols = [c for c in before if before[c] != after[c]]
                conn.execute(f"UPDATE {table} SET {', '.join(c + ' = %s' for c in cols)} WHERE id = %s",
                             (*[after[c] for c in cols], row_id))
        print(f"applied; rows as they were: {args.backup}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
