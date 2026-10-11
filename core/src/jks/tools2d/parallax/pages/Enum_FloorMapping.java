package jks.tools2d.parallax.pages;

/**
 * How a FLOOR layer's rows map its image (r275, Simon tried both in the floor lab). Stored by name since .plax format
 * 11. Append only: a reader older than a mapping refuses the file rather than guess.
 */
public enum Enum_FloorMapping
{
	/**
	 * The image seen from above: each row stretched across by its depth about the view's middle, and the image's rows
	 * spaced by depth, so the ground recedes to the top row. The image's height is one tile of depth.
	 */
	MODE7,
	/** The image as painted, in perspective already: each row only shifted, at its speed (a fighting game's floor). */
	LINE_SCROLL,
}
