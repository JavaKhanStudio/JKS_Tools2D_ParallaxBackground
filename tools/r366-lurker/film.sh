#!/usr/bin/env bash
# film.sh PAGE_DIR OUT — an ai-page.sh --lurker page filmed off screen beside its twin without the lurker (r366).
#
#   tools/ai-page.sh --theme "dark pine forest at dusk" --seed 3 --lurker "a hooded figure" build/r366/page
#   tools/r366-lurker/film.sh build/r366/page/page build/r366
#
# Copies PAGE_DIR's page and atlas to OUT/round, writes a00 (the page, its LURKER layer taken out) and a01 (the page),
# films 24 s of each from 12 s on (tools/ai-film.sh: cage, OUT/film/a01.mp4) and runs moments.py on them:
# OUT/film/a01-moments.png and a01-feet.png.
set -eu
cd "$(dirname "$0")/../.." || exit 1
PAGE_DIR="$1" OUT="$2"
mkdir -p "$OUT/round"
cp "$PAGE_DIR"/*.atlas "$PAGE_DIR"/*.png "$OUT/round/"
python3 - "$PAGE_DIR" "$OUT/round" <<'PY'
import glob, json, os, sys
src, dst = sys.argv[1], sys.argv[2]
page_file = [p for p in glob.glob(os.path.join(src, '*.jplax'))][0]
with open(page_file) as f:
    page = json.load(f)
lurkers = [l for l in page['pageModel']['pageList'] if l.get('kind') == 'LURKER']
if not lurkers:
    sys.exit(f'{page_file}: no LURKER layer')
with open(os.path.join(dst, 'a01.jplax'), 'w') as f:
    json.dump(page, f, indent=1)
page['pageModel']['pageList'] = [l for l in page['pageModel']['pageList'] if l.get('kind') != 'LURKER']
with open(os.path.join(dst, 'a00.jplax'), 'w') as f:
    json.dump(page, f, indent=1)
scenes = [{'id': sid, 'page': os.path.join(dst, sid + '.jplax'), 'atlasDir': dst, 'name': sid, 'about': about}
          for sid, about in (('a00', 'the page without its lurker'), ('a01', 'the page with its lurker'))]
with open(os.path.join(dst, 'round.json'), 'w') as f:
    json.dump({'round': 'r366-lurker', 'scenes': scenes}, f, indent=1)
PY
tools/ai-film.sh "$OUT/round" "$OUT/film" 24 24
python3 tools/r366-lurker/moments.py "$OUT/film"
