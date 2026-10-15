# The animation lab's beach (r407)

`beach.jplax` is r365's AI page ("tropical beach, palm trees, warm sunny afternoon", `tools/ai-page.sh`, painted,
seed 11), copied from `build/r365/after/beach-painted/page/` as it was written: the strips r375's
`tools/r375-animated-layer/shaders.py` animated. `beach.atlas`, `beach.png` and `beach-sea.png` are its atlas, repacked
by `make_lab.py` under the push check's 1 MB (the generated one was 2.4 MB): each strip at its full size, the sea on a
page of its own, every visible pixel as painted. The lab draws it within 1/255 of the generated atlas. `AnimLab`
(core/browser-test) makes its sea, `l1`, a `SHADER` layer when it loads it; the file is the page as generated. Never
re-export it: a lab judged on it is judged on these pixels.
