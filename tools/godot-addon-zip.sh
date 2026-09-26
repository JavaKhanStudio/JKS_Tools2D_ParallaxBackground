#!/usr/bin/env bash
# godot-addon-zip.sh [OUT_DIR] — packs the Godot reader for a release (d11): jks-parallax-godot-X.Y.Z.zip, with
# addons/jks_parallax/ at its root (where the Godot Asset Library installer and a game both expect it), LICENSE and
# NOTICE inside the addon. X.Y.Z is gradle.properties' version. The Release workflow attaches the zip to the GitHub
# release, and the Asset Library listing downloads it from there (RELEASING.md, "Godot Asset Library").
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-build/godot-addon}"
VERSION=$(grep '^version=' gradle.properties | cut -d= -f2)
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

mkdir -p "$STAGE/addons" "$OUT"
cp -r engines/godot/addons/jks_parallax "$STAGE/addons/"
cp LICENSE NOTICE "$STAGE/addons/jks_parallax/"
ZIP="$(cd "$OUT" && pwd)/jks-parallax-godot-$VERSION.zip"
rm -f "$ZIP"
(cd "$STAGE" && zip -qr -X "$ZIP" addons)
echo "$ZIP"
