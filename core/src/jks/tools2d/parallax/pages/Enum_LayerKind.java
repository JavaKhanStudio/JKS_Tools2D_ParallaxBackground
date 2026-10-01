package jks.tools2d.parallax.pages;

/**
 * What a layer draws (docs/effect-layers.md). Stored by name since .plax format 5: a page saved before reads IMAGE.
 * Append only: a reader older than a kind refuses the file rather than guess.
 */
public enum Enum_LayerKind
{
	/** An atlas region, tiled: every layer saved before format 5. */
	IMAGE,
	/**
	 * Nothing, in a box the world's size times sizeRatio: a slot in the draw order that the game fills, through the
	 * {@link jks.tools2d.parallax.LayerHook} registered under the layer's name.
	 */
	EMPTY,
	/**
	 * An engine's own particle effect, from a file the page names per engine (format 6): libGDX's ParticleEffect, from
	 * {@link Parallax_Model#particlesLibgdx}, in a box the world's size times sizeRatio. See {@link Enum_ParticleAnchor}.
	 */
	PARTICLES,
}
