package jks.tools2d.parallax.editor.gvars;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

/** Jackson mix-in that makes any TextureRegion reachable from a saved model invisible to JSON. */
@JsonIgnoreType
public class MyMixInForIgnoreType
{}
