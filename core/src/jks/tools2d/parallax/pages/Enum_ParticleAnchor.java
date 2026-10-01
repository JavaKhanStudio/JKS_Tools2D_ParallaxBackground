package jks.tools2d.parallax.pages;

/**
 * Where a PARTICLES layer's effect sits when the page scrolls (docs/effect-layers.md, Simon's choice on r178). Stored by
 * name since .plax format 6. Append only.
 */
public enum Enum_ParticleAnchor
{
	/**
	 * Pinned to the layer's box, at its bottom-left corner, and scrolled and tiled with it: a chimney's smoke, a
	 * waterfall's spray. Snow ends at the effect's edge.
	 */
	LAYER,
	/**
	 * Emitted from the view, at the layer's decal from its bottom-left corner, drawn once whatever the page repeats on:
	 * snow or rain that never runs out. Its particles drift by the layer's scroll, so the depth still shows.
	 */
	VIEW,
}
