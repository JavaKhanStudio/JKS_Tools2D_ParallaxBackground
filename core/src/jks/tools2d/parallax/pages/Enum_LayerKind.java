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
	/**
	 * An atlas region, tiled as an IMAGE is, drawn through one of the effects the library ships (format 7):
	 * {@link Parallax_Model#shaderEffect}, with its numbers. See {@link Enum_ShaderEffect}.
	 */
	SHADER,
	/**
	 * Several atlas regions chained along one layer (format 8): a cycle of {@link Parallax_Model#sequenceLength}
	 * segments drawn once, from {@link Parallax_Model#sequenceSeed} and the segments' weights, then tiled as an IMAGE
	 * layer's one image is. See {@link Sequence_Segment}.
	 */
	SEQUENCE,
	/**
	 * A ground (format 11): an atlas region in an IMAGE layer's box, drawn as {@link Parallax_Model#floorRows} rows, each
	 * row at its own depth: the bottom row scrolls at the layer's speed, the top row at
	 * {@link Parallax_Model#floorTopSpeed}, the rows between at a speed straight in their height (1 / their depth), mapped
	 * as {@link Parallax_Model#floorMapping} says. The layers in front of it scroll at the speed of the row under their
	 * foot. See {@link Enum_FloorMapping}, docs/depth-tricks.md §1.
	 */
	FLOOR,
}
