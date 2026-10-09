package jks.tools2d.parallax;

/**
 * How a transfert (the cross-fade from one page into another) is drawn: the game's call, never stored in a page
 * ({@link jks.tools2d.parallax.heart.Parallax_Heart#transfertIntoPage(jks.tools2d.parallax.pages.WholePage_Model, float, TransfertStyle)}).
 * {@link #FADE}, every layer slot at once, is what a transfert without a style does. {@link #depthStagger} fades each
 * slot over its own window, the back ones first. A style is immutable: keep one and pass it to every transfert.
 * <p>
 * Godot's copy is engines/godot/addons/jks_parallax/plax_transfert_style.gd: change {@link #slotRamp} there too.
 */
public final class TransfertStyle
{
	public enum Kind
	{
		/** Every layer slot fades at once. */
		FADE,
		/** Each layer slot fades over its own window, the back ones first ({@link #getStagger}). */
		DEPTH_STAGGER,
	}

	/** The largest stagger: the back slot is done when the front one starts at a third of the transfert. */
	public static final float MAX_STAGGER = 2;

	/** Today's transfert: every layer slot fades from the old page's layer to the new one's at once. */
	public static final TransfertStyle FADE = new TransfertStyle(Kind.FADE, 0);

	private final Kind kind;
	private final float stagger;

	private TransfertStyle(Kind kind, float stagger)
	{
		this.kind = kind;
		this.stagger = stagger;
	}

	/**
	 * Each layer slot fades over its own window, the back ones first: the back slot over the first
	 * {@code 1 / (1 + stagger)} of the transfert, the front one over the last, the ones between spread evenly. 0 is
	 * {@link #FADE}; {@code stagger} is clamped to 0..{@link #MAX_STAGGER}.
	 */
	public static TransfertStyle depthStagger(float stagger)
	{
		float s = Math.max(0, Math.min(MAX_STAGGER, stagger));
		return s > 0 ? new TransfertStyle(Kind.DEPTH_STAGGER, s) : FADE;
	}

	public Kind getKind()
	{return kind;}

	/** How far apart the slots' windows are, 0 to {@link #MAX_STAGGER}; 0 for every style but DEPTH_STAGGER. */
	public float getStagger()
	{return stagger;}

	/**
	 * How far along layer slot {@code slot} of {@code total} (0 at the back) is, 0 to 1, when the transfert is at
	 * {@code progress}, 0 to 1: the incoming layer's opacity, the outgoing one's being 1 minus it. Every slot is at 1
	 * when the transfert is.
	 */
	public float slotRamp(float progress, int slot, int total)
	{
		float k = total > 1 ? slot / (float) (total - 1) : 0;
		return Math.max(0, Math.min(1, progress * (1 + stagger) - stagger * k));
	}

	@Override
	public String toString()
	{return kind == Kind.FADE ? "FADE" : kind + "(" + stagger + ")";}
}
