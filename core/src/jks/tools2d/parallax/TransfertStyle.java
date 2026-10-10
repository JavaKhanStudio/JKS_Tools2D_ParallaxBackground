package jks.tools2d.parallax;

/**
 * How a transfert (the cross-fade from one page into another) is drawn: the game's call, never stored in a page
 * ({@link jks.tools2d.parallax.heart.Parallax_Heart#transfertIntoPage(jks.tools2d.parallax.pages.WholePage_Model, float, TransfertStyle)}).
 * {@link #FADE}, every layer slot at once, is what a transfert without a style does. {@link #depthStagger} fades each
 * slot over its own window, the back ones first. {@link #dissolve} has each slot's incoming layer eat the outgoing one
 * in patches, through the engine's shaders ({@link LayerEffects#setDissolve}). A style is immutable: keep one and pass
 * it to every transfert.
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
		/**
		 * In each layer slot the incoming layer eats the outgoing one in patches, over the slot's window as
		 * DEPTH_STAGGER's ({@link #getStagger}); {@link #getPatches} across the view, {@link #getSoftness} at their edges.
		 */
		DISSOLVE,
	}

	/** The largest stagger: the back slot is done when the front one starts at a third of the transfert. */
	public static final float MAX_STAGGER = 2;

	/** Today's transfert: every layer slot fades from the old page's layer to the new one's at once. */
	public static final TransfertStyle FADE = new TransfertStyle(Kind.FADE, 0, 0, 0);

	/** A dissolve's patches across the view, and the softness of their edges, at most and at least. */
	public static final float MIN_PATCHES = 0.5f, MAX_PATCHES = 64, MIN_SOFTNESS = 0.01f, MAX_SOFTNESS = 0.5f;
	/** How far a dissolve's patches drift sideways, in patches per second of the reader's clock. */
	public static final float DISSOLVE_DRIFT = 0.15f;

	private final Kind kind;
	private final float stagger, patches, softness;

	private TransfertStyle(Kind kind, float stagger, float patches, float softness)
	{
		this.kind = kind;
		this.stagger = stagger;
		this.patches = patches;
		this.softness = softness;
	}

	/**
	 * Each layer slot fades over its own window, the back ones first: the back slot over the first
	 * {@code 1 / (1 + stagger)} of the transfert, the front one over the last, the ones between spread evenly. 0 is
	 * {@link #FADE}; {@code stagger} is clamped to 0..{@link #MAX_STAGGER}.
	 */
	public static TransfertStyle depthStagger(float stagger)
	{
		float s = Math.max(0, Math.min(MAX_STAGGER, stagger));
		return s > 0 ? new TransfertStyle(Kind.DEPTH_STAGGER, s, 0, 0) : FADE;
	}

	/**
	 * In each layer slot the incoming layer eats the outgoing one in patches (the transfert lab's D, r230): about
	 * {@code patches} of them across the view, their edges {@code softness} soft (0.01 hard, 0.5 a blur), the slots
	 * over {@link #depthStagger}'s windows ({@code stagger} 0: all at once). Patches clamped to
	 * {@link #MIN_PATCHES}..{@link #MAX_PATCHES}, softness to {@link #MIN_SOFTNESS}..{@link #MAX_SOFTNESS}, stagger to
	 * 0..{@link #MAX_STAGGER}. An engine that draws no dissolve ({@link LayerEffects#setDissolve} false) fades instead,
	 * and so do EMPTY and PARTICLES layers, which no shader of the reader's draws.
	 */
	public static TransfertStyle dissolve(float patches, float softness, float stagger)
	{
		return new TransfertStyle(Kind.DISSOLVE, Math.max(0, Math.min(MAX_STAGGER, stagger)),
				Math.max(MIN_PATCHES, Math.min(MAX_PATCHES, patches)), Math.max(MIN_SOFTNESS, Math.min(MAX_SOFTNESS, softness)));
	}

	public Kind getKind()
	{return kind;}

	/** How far apart the slots' windows are, 0 to {@link #MAX_STAGGER}; 0 for FADE. */
	public float getStagger()
	{return stagger;}

	/** A dissolve's patches across the view's width; 0 for every other style. */
	public float getPatches()
	{return patches;}

	/** How soft a dissolve's patch edges are, {@link #MIN_SOFTNESS} to {@link #MAX_SOFTNESS}; 0 for every other style. */
	public float getSoftness()
	{return softness;}

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

	/**
	 * A dissolve's noise cells up a view {@code viewWidth} by {@code viewHeight}: {@link #getPatches} across it, its
	 * height in the same cells, stretched 2.5 times so that FOG's noise, 3-4 times taller than wide, makes patches about
	 * round. Godot's copy: plax_transfert_style.gd cells_up.
	 */
	public float cellsUp(float viewWidth, float viewHeight)
	{return viewWidth > 0 ? patches * viewHeight / viewWidth * 2.5f : 0;}

	/**
	 * How far a dissolve's noise has drifted on x at {@code seconds} of the reader's clock, in cells:
	 * {@link #DISSOLVE_DRIFT} a second, wrapped at 8, where the noise repeats (FOG's 7, 17 and 23 per 8). Godot's copy:
	 * plax_transfert_style.gd drift.
	 */
	public static float drift(double seconds)
	{
		double d = (seconds * DISSOLVE_DRIFT) % 8;
		return (float) (d < 0 ? d + 8 : d);
	}

	@Override
	public String toString()
	{return kind == Kind.FADE ? "FADE" : kind == Kind.DISSOLVE ? kind + "(" + patches + ", " + softness + ", " + stagger + ")" : kind + "(" + stagger + ")";}
}
