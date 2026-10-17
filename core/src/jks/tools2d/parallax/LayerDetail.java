package jks.tools2d.parallax;

/**
 * From which {@link Enum_Detail} a layer, its SHADER effect, its glint and a MIRROR layer's reflection are drawn (r464).
 * A reader draws every layer by {@link #DEFAULT} unless the game names another for it
 * ({@link ParallaxPageReader#setLayerDetail}): a layer not shown at a level is not drawn at all (it still scrolls); a
 * SHADER layer below its effect's level is drawn as its image; below its glint's, without the glint; a MIRROR layer
 * below its mirror's level is drawn through SWELL.
 *
 * <pre>
 * reader.setDetail(Enum_Detail.MEDIUM);
 * reader.setLayerDetail("birds", LayerDetail.DEFAULT.shownFrom(Enum_Detail.HIGH));
 * reader.setLayerDetail("sea", LayerDetail.DEFAULT.mirrorFrom(Enum_Detail.MEDIUM));
 * </pre>
 *
 * Immutable: each {@code ...From} returns a new one.
 */
public final class LayerDetail
{
	/** Shown at every level, its effect and glint from MEDIUM, a MIRROR's reflection at HIGH only. */
	public static final LayerDetail DEFAULT = new LayerDetail(Enum_Detail.OFF, Enum_Detail.MEDIUM, Enum_Detail.MEDIUM, Enum_Detail.HIGH);

	private final Enum_Detail shown, effect, glint, mirror;

	private LayerDetail(Enum_Detail shown, Enum_Detail effect, Enum_Detail glint, Enum_Detail mirror)
	{
		this.shown = shown;
		this.effect = effect;
		this.glint = glint;
		this.mirror = mirror;
	}

	/** The same, the layer drawn only from {@code level} up; null: at every level. */
	public LayerDetail shownFrom(Enum_Detail level)
	{return new LayerDetail(or(level), effect, glint, mirror);}

	/** The same, a SHADER layer's effect drawn from {@code level} up, its image plain below; null: at every level. */
	public LayerDetail effectFrom(Enum_Detail level)
	{return new LayerDetail(shown, or(level), glint, mirror);}

	/** The same, its glint drawn from {@code level} up (and never without its effect); null: at every level. */
	public LayerDetail glintFrom(Enum_Detail level)
	{return new LayerDetail(shown, effect, or(level), mirror);}

	/** The same, a MIRROR layer's reflection drawn from {@code level} up, through SWELL below; null: at every level. */
	public LayerDetail mirrorFrom(Enum_Detail level)
	{return new LayerDetail(shown, effect, glint, or(level));}

	public Enum_Detail getShownFrom()
	{return shown;}

	public Enum_Detail getEffectFrom()
	{return effect;}

	public Enum_Detail getGlintFrom()
	{return glint;}

	public Enum_Detail getMirrorFrom()
	{return mirror;}

	/** Whether a layer under this is drawn at {@code level}. */
	public boolean shows(Enum_Detail level)
	{return level.atLeast(shown);}

	private static Enum_Detail or(Enum_Detail level)
	{return level == null ? Enum_Detail.OFF : level;}
}
