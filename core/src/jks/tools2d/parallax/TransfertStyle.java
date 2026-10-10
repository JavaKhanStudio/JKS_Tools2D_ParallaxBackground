package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.Color;

/**
 * How a transfert (the cross-fade from one page into another) is drawn: the game's call, never stored in a page
 * ({@link jks.tools2d.parallax.heart.Parallax_Heart#transfertIntoPage(jks.tools2d.parallax.pages.WholePage_Model, float, TransfertStyle)}).
 * {@link #FADE}, every layer slot at once, is what a transfert without a style does. {@link #depthStagger} fades each
 * slot over its own window, the back ones first. {@link #dissolve} has each slot's incoming layer eat the outgoing one
 * in patches, through the engine's shaders ({@link LayerEffects#setDissolve}). {@link #throughColor} goes from each
 * slot's outgoing layer to a colour, white possible, then from that colour to its incoming one
 * ({@link LayerEffects#setGrade}), the gradients with it. A style is immutable: keep one and pass it to every
 * transfert.
 * <p>
 * Godot's copy is engines/godot/addons/jks_parallax/plax_transfert_style.gd: change {@link #slotRamp},
 * {@link #gradeOf} and {@link #gradient} there too.
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
		/**
		 * In each layer slot the outgoing layer goes to a colour ({@link #getColorR} G, B), then the incoming one comes
		 * out of it ({@link #gradeOf}), over the slot's window as DEPTH_STAGGER's ({@link #getStagger}).
		 */
		THROUGH_COLOR,
	}

	/** The largest stagger: the back slot is done when the front one starts at a third of the transfert. */
	public static final float MAX_STAGGER = 2;

	/** Today's transfert: every layer slot fades from the old page's layer to the new one's at once. */
	public static final TransfertStyle FADE = new TransfertStyle(Kind.FADE, 0, 0, 0, 0, 0, 0);

	/** A dissolve's patches across the view, and the softness of their edges, at most and at least. */
	public static final float MIN_PATCHES = 0.5f, MAX_PATCHES = 64, MIN_SOFTNESS = 0.01f, MAX_SOFTNESS = 0.5f;
	/** How far a dissolve's patches drift sideways, in patches per second of the reader's clock. */
	public static final float DISSOLVE_DRIFT = 0.15f;

	private final Kind kind;
	private final float stagger, patches, softness;
	/** THROUGH_COLOR's colour, opaque. */
	private final float colorR, colorG, colorB;

	private TransfertStyle(Kind kind, float stagger, float patches, float softness, float colorR, float colorG, float colorB)
	{
		this.kind = kind;
		this.stagger = stagger;
		this.patches = patches;
		this.softness = softness;
		this.colorR = colorR;
		this.colorG = colorG;
		this.colorB = colorB;
	}

	/**
	 * Each layer slot fades over its own window, the back ones first: the back slot over the first
	 * {@code 1 / (1 + stagger)} of the transfert, the front one over the last, the ones between spread evenly. 0 is
	 * {@link #FADE}; {@code stagger} is clamped to 0..{@link #MAX_STAGGER}.
	 */
	public static TransfertStyle depthStagger(float stagger)
	{
		float s = Math.max(0, Math.min(MAX_STAGGER, stagger));
		return s > 0 ? new TransfertStyle(Kind.DEPTH_STAGGER, s, 0, 0, 0, 0, 0) : FADE;
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
				Math.max(MIN_PATCHES, Math.min(MAX_PATCHES, patches)), Math.max(MIN_SOFTNESS, Math.min(MAX_SOFTNESS, softness)),
				0, 0, 0);
	}

	/**
	 * Through a colour (the transfert lab's E, r251): in each layer slot the outgoing layer is mixed toward
	 * {@code color} (its alpha ignored, each channel clamped to 0..1; {@link Color#WHITE} a fade to white) until it is
	 * all that colour halfway through the slot's window, then the incoming layer, drawn in its place, comes out of it
	 * ({@link #gradeOf}); the gradients go through it too ({@link #gradient}). The slots over {@link #depthStagger}'s
	 * windows ({@code stagger} 0: all at once, the whole screen the colour at the transfert's middle), clamped to
	 * 0..{@link #MAX_STAGGER}. The colour is mixed in after the page's fog. An engine that draws no grade
	 * ({@link LayerEffects#setGrade} false), and EMPTY and PARTICLES layers, fade out to the gradients instead.
	 */
	public static TransfertStyle throughColor(Color color, float stagger)
	{
		return new TransfertStyle(Kind.THROUGH_COLOR, Math.max(0, Math.min(MAX_STAGGER, stagger)), 0, 0,
				Math.max(0, Math.min(1, color.r)), Math.max(0, Math.min(1, color.g)), Math.max(0, Math.min(1, color.b)));
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

	/** THROUGH_COLOR's colour, red, green and blue, 0 to 1; 0 for every other style. */
	public float getColorR()
	{return colorR;}

	public float getColorG()
	{return colorG;}

	public float getColorB()
	{return colorB;}

	/**
	 * How much of THROUGH_COLOR's colour a layer of a slot at {@code ramp} ({@link #slotRamp}) is mixed toward, 0 to 1:
	 * rising from 0 to 1 over the first half of the slot's window, where the outgoing layer is drawn, falling back to 0
	 * over the second, where the incoming one is ({@link #incomingShows}).
	 */
	public static float gradeOf(float ramp)
	{return ramp < 0.5f ? 2 * ramp : 2 - 2 * ramp;}

	/** THROUGH_COLOR: whether a slot at {@code ramp} draws its incoming layer, rather than its outgoing one. */
	public static boolean incomingShows(float ramp)
	{return ramp >= 0.5f;}

	/**
	 * A gradient's colour {@code progress}, 0 to 1, into a transfert from {@code from} to {@code to}, into {@code out}:
	 * the two mixed, as with any style but THROUGH_COLOR, which goes from {@code from} to its colour, then to
	 * {@code to}, at the pace of the back slot (the gradients are behind it). SquareBackground, jME's JmeGradient and
	 * Godot's plax_background.gd _act_gradients draw with it.
	 */
	public Color gradient(Color from, Color to, float progress, Color out)
	{
		if (kind != Kind.THROUGH_COLOR)
			return out.set(from).lerp(to, progress);
		float ramp = slotRamp(progress, 0, 1);
		return out.set(incomingShows(ramp) ? to : from).lerp(colorR, colorG, colorB, 1, gradeOf(ramp));
	}

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
	{
		if (kind == Kind.FADE)
			return "FADE";
		if (kind == Kind.DISSOLVE)
			return kind + "(" + patches + ", " + softness + ", " + stagger + ")";
		if (kind == Kind.THROUGH_COLOR)
			return kind + "(" + colorR + ", " + colorG + ", " + colorB + ", " + stagger + ")";
		return kind + "(" + stagger + ")";
	}
}
