#!/usr/bin/env bash
# ai-page.sh --theme "..." [--layers 4] [--style painted|pixel] OUT — a parallax page painted by a local ComfyUI (r246).
#
#   tools/ai-page.sh --theme "snowy pine valley at dawn" --colours 8a93a8,5d7a8c,1c2e33 build/ai/snow
#
# tools/r239-ai-strips/ai_page.py does it all (its --help): strips, cut, atlas, .jplax, lint, and the stills through
# tools/parallax-lab-shots.sh (cage). The parallax-pages skill's "Generating a page" says what to look at.
exec python3 "$(dirname "$0")/r239-ai-strips/ai_page.py" "$@"
