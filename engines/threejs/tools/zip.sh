#!/usr/bin/env bash
# zip.sh [OUT_DIR] — packs the three.js reader for a release (r34): jks-parallax-threejs-X.Y.Z.zip, with
# jks_parallax_threejs/ at its root holding plax.js (the ES module a game imports; it imports 'three', which the game
# maps to its own copy), README.md, LICENSE and NOTICE. X.Y.Z is gradle.properties' version. The Release workflow
# attaches the zip to the GitHub release (RELEASING.md).
set -euo pipefail
cd "$(dirname "$0")/../../.."
OUT="${1:-build/threejs-zip}"
VERSION=$(grep '^version=' gradle.properties | cut -d= -f2)
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

mkdir -p "$STAGE/jks_parallax_threejs" "$OUT"
cp engines/threejs/src/plax.js engines/threejs/README.md LICENSE NOTICE "$STAGE/jks_parallax_threejs/"
ZIP="$(cd "$OUT" && pwd)/jks-parallax-threejs-$VERSION.zip"
rm -f "$ZIP"
(cd "$STAGE" && zip -qr -X "$ZIP" jks_parallax_threejs)
echo "$ZIP"
