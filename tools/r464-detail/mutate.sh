#!/usr/bin/env bash
# r464: proves the detail scenes (engines/rounds/shaders s15-s18, engines/rounds/glint g06, g07) see a wrong copy of
# the detail levels, and DetailCases a wrong core. Puts in, one at a time and in one copy only: Godot ignoring the
# level; Godot keeping MIRROR's reflection at MEDIUM; Godot drawing an effect at OFF; Godot's glint drawn whatever its
# rule; Godot's MEDIUM glint in MIRROR's sun, not SWELL's; Godot showing a hidden layer; Godot ignoring the named
# layers' rules; Godot ignoring every layer's rule; jME drawing the stored effect, not the drawn one; core's reader
# never telling a layer what its level draws; core keeping the reflection; core's glint in MIRROR's sun at MEDIUM.
# Runs that engine's frame check on a round of the detail scenes alone (build/r464/rounds), or :core:test's
# BrowserSuiteTest for core, and fails unless every run FAILs. Restores the files on exit. ONLY=<regex> runs the
# mutants whose name matches it.
# headless: engines/godot/tools/frame-check.sh and engines/jme/tools/frame-check.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
DETAIL=engines/godot/addons/jks_parallax/plax_detail.gd
GD=engines/godot/addons/jks_parallax/plax_effects.gd
BG=engines/godot/addons/jks_parallax/plax_background.gd
JME=engines/jme/src/jks/tools2d/parallax/jme/JmeLayerEffects.java
READER=core/src/jks/tools2d/parallax/ParallaxPageReader.java
LAYER=core/src/jks/tools2d/parallax/ParallaxLayer.java
EFFECTS=core/src/jks/tools2d/parallax/GdxLayerEffects.java
FILES=("$DETAIL" "$GD" "$BG" "$JME" "$READER" "$LAYER" "$EFFECTS")
SAVED=$(mktemp -d)
for i in "${!FILES[@]}"; do cp "${FILES[$i]}" "$SAVED/$i"; done
restore() { for i in "${!FILES[@]}"; do cp "$SAVED/$i" "${FILES[$i]}"; done; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r464/mutant.log
ROUNDS=build/r464/rounds
mkdir -p build/r464
# The detail scenes alone, each round's own gates kept.
python3 - "$ROUNDS" <<'PY' || exit 1
import json, os, sys
for name in ['shaders', 'glint']:
    r = json.load(open(f'engines/rounds/{name}/round.json'))
    scenes = [s for s in r['scenes'] if any(k in s for k in ('detail', 'detailDefault', 'layerDetail'))]
    os.makedirs(os.path.join(sys.argv[1], name), exist_ok=True)
    json.dump(dict(r, scenes=scenes), open(os.path.join(sys.argv[1], name, 'round.json'), 'w'), indent=1)
PY
status=0
run() {
	if [ -n "${ONLY:-}" ] && ! [[ "$1" =~ $ONLY ]]; then restore; return; fi
	local name="$1"; shift
	if "$@" >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $name"; status=1
	else
		echo "caught: $name ($(grep -o 'FAIL: .*\|[0-9]* tests completed, [0-9]* failed\|error: .*' "$LOG" | tail -1))"
	fi
	restore
}
mutate() {
	sed -i "$2" "$1"
	grep -q "$3" "$1" || { echo "mutation did not apply: $2"; exit 2; }
}
GODOT=(engines/godot/tools/frame-check.sh "$ROUNDS/shaders" "$ROUNDS/glint")
JMECHECK=(engines/jme/tools/frame-check.sh "$ROUNDS/shaders" "$ROUNDS/glint")
CORE=(./gradlew :core:test --tests '*BrowserSuiteTest*' -q)

mutate "$DETAIL" 's/^static func apply(l: Dictionary, level: int, r: Dictionary) -> void:/static func apply(l: Dictionary, _level: int, r: Dictionary, level := Level.HIGH) -> void:/' 'level := Level.HIGH'
run "Godot ignoring the level" "${GODOT[@]}"
mutate "$DETAIL" 's/m.shaderEffect == "MIRROR" and level < int(r.mirror)/m.shaderEffect == "MIRROR" and false/' '"MIRROR" and false'
run "Godot keeping MIRROR's reflection at MEDIUM" "${GODOT[@]}"
mutate "$DETAIL" 's/if m.kind == "SHADER" and level >= int(r.effect):/if m.kind == "SHADER":/' 'if m.kind == "SHADER":$'
run "Godot drawing an effect at OFF" "${GODOT[@]}"
mutate "$GD" 's/var on: bool = glints(model) and l.get("drawn_glint", true)/var on: bool = glints(model)/' 'var on: bool = glints(model)$'
run "Godot's glint drawn whatever its rule" "${GODOT[@]}"
mutate "$GD" 's/var mirrored: bool = l.get("drawn_effect", model.get("shaderEffect", "")) == "MIRROR"/var mirrored: bool = model.get("shaderEffect", "") == "MIRROR"/' 'var mirrored: bool = model.get'
run "Godot's MEDIUM glint in MIRROR's sun" "${GODOT[@]}"
mutate "$DETAIL" 's/l.canvas.visible = l.shown/l.canvas.visible = true/' 'l.canvas.visible = true'
run "Godot showing a hidden layer" "${GODOT[@]}"
mutate "$DETAIL" 's/return bg._details.get(l.model.get("name", ""), bg._default_detail)/return bg._default_detail/' 'return bg._default_detail$'
run "Godot ignoring the named layers' rules" "${GODOT[@]}"
mutate "$BG" 's/_default_detail = PlaxDetail.rule(rule)/_default_detail = PlaxDetail.DEFAULT/' '_default_detail = PlaxDetail.DEFAULT$'
run "Godot ignoring every layer's rule" "${GODOT[@]}"
mutate "$JME" 's/Enum_ShaderEffect kind = layer.getDrawnEffect();/Enum_ShaderEffect kind = layer.getKind() == jks.tools2d.parallax.pages.Enum_LayerKind.SHADER ? layer.getShaderEffect() : null;/' 'layer.getShaderEffect() : null;'
run "jME drawing the stored effect" "${JMECHECK[@]}"
mutate "$READER" 's/		layer.setDetailDrawn(detail.atLeast(rule.getEffectFrom()), detail.atLeast(rule.getGlintFrom()), detail.atLeast(rule.getMirrorFrom()));/		\/\/ mutant: no setDetailDrawn/' 'mutant: no setDetailDrawn'
run "core's reader never telling a layer its level" "${CORE[@]}"
mutate "$LAYER" 's/shaderEffect == Enum_ShaderEffect.MIRROR \&\& !mirrorDrawn ? Enum_ShaderEffect.SWELL : shaderEffect/shaderEffect/' 'return shaderEffect;$'
run "core keeping the reflection" "${CORE[@]}"
mutate "$EFFECTS" 's/boolean mirror = layer.getDrawnEffect() == Enum_ShaderEffect.MIRROR;/boolean mirror = layer.getShaderEffect() == Enum_ShaderEffect.MIRROR;/' 'boolean mirror = layer.getShaderEffect()'
run "core's MEDIUM glint in MIRROR's sun" "${CORE[@]}"
exit $status
