package jks.tools2d.parallax;

/**
 * How much of a page a game draws (r464): a player's choice of quality, set at run time on a reader
 * ({@link ParallaxPageReader#setDetail}, {@link jks.tools2d.parallax.heart.Parallax_Heart#setDetail}), never stored in a
 * page. Each layer is drawn, plain or not drawn from the levels its {@link LayerDetail} names. Lowest first: a level
 * added later goes in its place among them, by name.
 */
public enum Enum_Detail
{
	/** The paintings alone: every SHADER layer drawn as its image, without its effect or its glint (the page fog kept). */
	OFF,
	/** Every effect but MIRROR's reflection: a MIRROR layer drawn through SWELL, its glint in SWELL's sun. */
	MEDIUM,
	/** The page as stored. */
	HIGH;

	/** Whether this level is {@code level} or above it. */
	public boolean atLeast(Enum_Detail level)
	{return compareTo(level) >= 0;}
}
