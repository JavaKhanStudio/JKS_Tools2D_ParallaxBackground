#!/usr/bin/env bash
# r269-history/release-zips.sh — read this repository's GitHub releases (read only) and print, for Simon, what removes
# the editor from them (r269, r286): every asset with its size and downloads, one DELETE per ParallaxEditor zip, and
# one PATCH per release whose notes send readers to that zip, with the notes it would write. It changes nothing: every
# gh call here is a GET; the printed commands are Simon's to run.
set -euo pipefail
REPO=repos/JavaKhanStudio/JKS_Tools2D_ParallaxBackground
# The notes' "Design a parallax" paragraph, as .github/workflows/release.yml writes it since r284.
# shellcheck disable=SC2016 # the backticks are Markdown, meant literal
NEW='**Design a parallax** — a page is a `.jplax` JSON file you can write by hand: see the README\x27s\n[File formats](https:\/\/github.com\/JavaKhanStudio\/JKS_Tools2D_ParallaxBackground#file-formats).\n'
SUB="s/\\*\\*Design a parallax\\*\\* — \`ParallaxEditor-[^\`]*\\.zip\`:.*?Java 17 or newer\\.\\n/$NEW/s"

echo "== assets (id, name, bytes, downloads; * = editor zip)"
gh api "$REPO/releases" --paginate --jq '.[] | "\(.tag_name) release \(.id)", (.assets[] |
  "  \(if (.name | test("^ParallaxEditor-.*\\.zip$")) then "*" else " " end) \(.id) \(.name) \(.size) \(.download_count)")'

echo "== delete the editor zips"
gh api "$REPO/releases" --paginate --jq '.[] | .assets[] | select(.name | test("^ParallaxEditor-.*\\.zip$")) |
  "gh api -X DELETE '"$REPO"'/releases/assets/\(.id)   # \(.name)"'

echo "== rewrite the notes that point at them"
for ID in $(gh api "$REPO/releases" --paginate --jq '.[] | select(.body // "" | test("ParallaxEditor-")) | .id'); do
  echo "gh api $REPO/releases/$ID --jq .body | perl -0pe '$SUB' | gh api -X PATCH $REPO/releases/$ID -F body=@-"
  echo "--- release $ID would read:"
  gh api "$REPO/releases/$ID" --jq .body | perl -0pe "$SUB" | sed 's/^/    /'
done
