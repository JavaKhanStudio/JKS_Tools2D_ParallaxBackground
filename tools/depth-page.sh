#!/usr/bin/env bash
# depth-page.sh PAINTING OUT [--layers 4] [--theme "..."] — one flat painting cut into a page by its depth (r279).
#
#   tools/depth-page.sh build/r279/paint1.png build/r279/valley --theme "green valley, river, pines, mountains"
#
# tools/r279-depth-page/depth_page.py does it all (its --help): Depth Anything V2 Small, depth bands, the holes
# repainted, atlas, .jplax, lint, and the stills through tools/parallax-lab-shots.sh (cage). Runs in the ComfyUI venv.
exec "${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python" "$(dirname "$0")/r279-depth-page/depth_page.py" "$@"
