#!/usr/bin/env bash
# r269-history/rewrite.sh OUT [--wide] — rewrite this repository's history without the editor (r269, r285), in OUT,
# and prove the result. It never pushes: OUT/new.git has no remote, and the push is Simon's (PUSH.txt says how).
#
# OUT/old.git  a fresh `git clone --mirror` of origin, untouched: what GitHub holds today.
# OUT/new.git  a new repository, written from it by git filter-repo --invert-paths: the editor's paths gone from every commit of
#              every branch and tag. Commits that only touched them disappear; every other commit keeps its tree.
# OUT/drop.txt the paths dropped. Default: editor/ and demo/ (the editor and its demo, 2019-01-03 to 5daa089) and the
#              demo's Hiver page under test/assets (the r132 split counted it as the demo's). --wide adds the 2017-2019
#              experiments that are neither editor nor library: desktop/ (launchers, UI skins), android/, test/, Files/.
#
# The proof (exit 1 on any failure):
#   1. no object under a dropped path in any commit of new.git (rev-list --objects --all, and log --all -- paths);
#   2. the same branches and tags in both, and v2.5 if origin has it;
#   3. every branch and tag: its tree in new.git is old.git's minus the dropped paths, file for file and blob for blob
#      (ls-tree -r), and its core/ tree hash is the same;
#   4. develop's tree hash is the same in both (develop holds no dropped path today).
# GitHub's refs/pull/* are read-only: they are rewritten here but cannot be pushed, so PR #1's 2019 editor stays
# reachable there until GitHub Support purges it. Needs uv (uvx git-filter-repo), as r132-editor-split.sh.
set -euo pipefail
OUT=${1:?usage: tools/r269-history/rewrite.sh OUT [--wide]}
WIDE=${2:-}
[ -z "$WIDE" ] || [ "$WIDE" = --wide ] || { echo "unknown argument $WIDE" >&2; exit 2; }
[ -e "$OUT" ] && { echo "$OUT exists" >&2; exit 2; }
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
URL=$(git -C "$ROOT" remote get-url origin)
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

{
  printf '%s\n' editor/ demo/ test/assets/Hiver.atlas test/assets/Hiver.png test/assets/hiver/
  [ -z "$WIDE" ] || printf '%s\n' desktop/ android/ test/ Files/
} > "$OUT/drop.txt"

git clone -q --mirror "$URL" "$OUT/old.git"
# Into a new empty repository, as r132: filter-repo creates its refs there instead of rewriting a clone's, which the
# board's reference-transaction hook refuses (it guards every repository against rewriting another's commits).
git init -q --bare "$OUT/new.git"
uvx git-filter-repo --source "$OUT/old.git" --target "$OUT/new.git" --invert-paths \
  --paths-from-file "$OUT/drop.txt" > "$OUT/filter-repo.log" 2>&1 \
  || { cat "$OUT/filter-repo.log" >&2; exit 1; }
# filter-repo leaves no remote; make sure of it: a push from new.git has nowhere to go but the URL Simon types.
[ -z "$(git -C "$OUT/new.git" remote)" ] || { echo "new.git has a remote" >&2; exit 1; }

FAIL=0
fail() { echo "FAIL $*"; FAIL=1; }
pass() { echo "ok   $*"; }
# The dropped paths as one extended regex over repo-relative paths.
RE=$(sed -e 's/[.]/\\./g' -e 's|/$|/.*|' "$OUT/drop.txt" | paste -sd'|')

# 1. nothing under a dropped path, in any object reachable from any ref
N=$(git -C "$OUT/new.git" rev-list --objects --all | awk 'NF>1{print $2}' | grep -cE "^($RE)$" || true)
if [ "$N" = 0 ]; then pass "no object under a dropped path in new.git"; else fail "$N objects under dropped paths in new.git"; fi
mapfile -t PATHS < "$OUT/drop.txt"
N=$(git -C "$OUT/new.git" log --all --format=%H -- "${PATHS[@]}" | wc -l)
if [ "$N" = 0 ]; then pass "git log --all -- <dropped paths> is empty"; else fail "$N commits still touch dropped paths"; fi
N=$(git -C "$OUT/old.git" log --all --format=%H -- "${PATHS[@]}" | wc -l)
echo "     (old.git: $N commits touched them)"

# 2. the same refs
REFS=$(git -C "$OUT/old.git" for-each-ref --format='%(refname)' refs/heads refs/tags)
if [ "$REFS" = "$(git -C "$OUT/new.git" for-each-ref --format='%(refname)' refs/heads refs/tags)" ]; then
  pass "same $(echo "$REFS" | wc -l) branches and tags"
else
  fail "the branches or tags differ"
fi
if git ls-remote --exit-code --tags "$URL" v2.5.0 v2.5 > /dev/null 2>&1; then
  git -C "$OUT/new.git" rev-parse -q --verify refs/tags/v2.5.0 > /dev/null || fail "origin has a v2.5 tag new.git lacks"
fi

# 3. every ref: the same files, blob for blob, minus the dropped ones; core/ the same tree
for REF in $REFS; do
  OLDT=$(git -C "$OUT/old.git" ls-tree -r "$REF" | grep -vP "\t($RE)$" || true)
  NEWT=$(git -C "$OUT/new.git" ls-tree -r "$REF")
  [ "$OLDT" = "$NEWT" ] || { fail "$REF: tree is not old minus dropped paths"; continue; }
  OC=$(git -C "$OUT/old.git" rev-parse -q --verify "$REF^{tree}:core" || echo none)
  NC=$(git -C "$OUT/new.git" rev-parse -q --verify "$REF^{tree}:core" || echo none)
  [ "$OC" = "$NC" ] || { fail "$REF: core/ differs ($OC vs $NC)"; continue; }
  pass "$REF: $(echo "$NEWT" | grep -c . || true) files identical, core/ ${NC:0:10}"
done

# 4. develop as today
if [ "$(git -C "$OUT/old.git" rev-parse 'develop^{tree}')" = "$(git -C "$OUT/new.git" rev-parse 'develop^{tree}')" ]; then
  pass "develop's tree hash unchanged"
else
  fail "develop's tree changed"
fi

# The old -> new commit map, for every SHA quoted in docs and on the board.
cp "$OUT/new.git/filter-repo/commit-map" "$OUT/commit-map.txt"
du -sh "$OUT/old.git" "$OUT/new.git" | sed 's/^/     /'

cat > "$OUT/PUSH.txt" <<EOF
Not run by any script. Simon, from $OUT/new.git, once GitHub allows a force-push on develop and master:
  git -C $OUT/new.git push --force $URL 'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*'
Not --mirror: GitHub refuses writes to refs/pull/*, and --mirror would also delete refs it does not know.
Then every checkout of this repository on the machine is re-cloned (a pull would merge the old history back in).
EOF
if [ "$FAIL" = 0 ]; then echo "PROVEN: $OUT/new.git (push: $OUT/PUSH.txt)"; else echo "NOT PROVEN" >&2; exit 1; fi
