package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;

import jks.tools2d.parallax.pages.Enum_LayerKind;

/**
 * What draws a SHADER layer through its {@link jks.tools2d.parallax.pages.Enum_ShaderEffect}, for one engine:
 * {@link GdxLayerEffects} with libGDX's shaders (the default of a {@link ParallaxPageReader}), jME's JmeLayerEffects
 * with a material on JmeBatch's meshes. The reader calls {@link #begin} before the layer's tiles and {@link #end} after
 * them, every frame: allocate nothing.
 */
public interface LayerEffects
{
	/**
	 * Makes the batch draw what follows through {@code layer}'s effect, at {@code phase} (see
	 * {@link ParallaxLayer#getShaderPhase}); false when it cannot, and the layer is drawn without it.
	 */
	boolean begin(Batch batch, ParallaxLayer layer, float phase);

	/**
	 * {@link #begin(Batch, ParallaxLayer, float)}, with the layer's color then mixed toward the mist's white
	 * ({@link GdxLayerEffects#HAZE_R}, G, B) by {@code haze}, 0 to 1, its alpha kept. One that draws no haze draws the
	 * SHADER layers' effects only.
	 */
	default boolean begin(Batch batch, ParallaxLayer layer, float phase, float haze)
	{return layer.getKind() == Enum_LayerKind.SHADER && begin(batch, layer, phase);}

	/**
	 * {@link #begin(Batch, ParallaxLayer, float)}, with the layer's color then mixed toward {@code fogColor} by
	 * {@code fog}, 0 to 1, its alpha kept: the page's depth fog at the layer's depth ({@link ParallaxPageReader#fogOf}).
	 * The reader calls it for a SHADER layer, and for an IMAGE or SEQUENCE layer whose fog is above 0, drawn plain. One
	 * that draws no fog colour of its own mixes toward the mist's white ({@link #begin(Batch, ParallaxLayer, float, float)}).
	 */
	default boolean begin(Batch batch, ParallaxLayer layer, float phase, float fog, Color fogColor)
	{return begin(batch, layer, phase, fog);}

	/**
	 * Ends a layer begun by a begin that returned true. It may leave the layer's shader bound, so that the next layer
	 * drawn through the same one costs a flush and its numbers, not two shader switches: {@link #release} then puts the
	 * batch back as the first begin found it.
	 */
	void end(Batch batch, ParallaxLayer layer);

	/**
	 * Puts back the shader {@link #end} left bound, if any. The reader calls it before drawing anything it did not begin
	 * (a layer drawn plain, a hook, particles) and after its last layer. One whose end puts the batch back needs none.
	 */
	default void release(Batch batch)
	{}

	/**
	 * Draws what follows, the IMAGE and SEQUENCE layers of a page with a fog, through one shader, so that no flush
	 * separates them: each layer's color is {@code tint} times its texture, mixed toward {@code fog} (its page drawn
	 * with batch color green 0) or {@code incomingFog} (green 1, the page a cross-fade brings in) by the batch color's
	 * red, its alpha the batch color's alpha. The reader then sets the batch color (fog, page, 0, alpha) per layer. It
	 * still begins each SHADER layer, which {@link #release} comes back from to this shader. False when this engine
	 * draws no page fog: the reader begins each fogged layer instead ({@link #begin(Batch, ParallaxLayer, float, float, Color)}).
	 */
	default boolean beginPageFog(Batch batch, Color tint, Color fog, Color incomingFog)
	{return false;}

	/** Ends {@link #beginPageFog}: the batch draws with the shader it had before it. */
	default void endPageFog(Batch batch)
	{}

	/**
	 * The width of the view the layers begun next are drawn in, in world units: FOG's noise is laid across it, from where
	 * each layer's starts ({@link ParallaxLayer#getEffectStartX}), so it runs on across the tiles (r229). The reader calls
	 * it every frame before its layers.
	 */
	default void setViewWidth(float width)
	{}

	/** {@link #setDissolve} sides: no mask; the outgoing layer, kept where the mask is not; the incoming, where it is. */
	int DISSOLVE_NONE = 0, DISSOLVE_OUTGOING = 1, DISSOLVE_INCOMING = 2;

	/**
	 * Makes the layers begun next, until it is called again with {@link #DISSOLVE_NONE}, draw through a dissolve's mask
	 * ({@link TransfertStyle#dissolve}): m, 0 to 1, rises where FOG's noise over the camera view, {@code cellsX} by
	 * {@code cellsY} cells across it, shifted {@code drift} cells on x, is under {@code ramp}, with {@code softness} of
	 * soft edge; {@link #DISSOLVE_OUTGOING} multiplies the layer's alpha by 1 - m, {@link #DISSOLVE_INCOMING} by m.
	 * {@link TransfertStyle#cellsUp} and {@link TransfertStyle#drift} reckon the numbers. The reader then begins every IMAGE, SEQUENCE and SHADER
	 * layer, fog or not. False when this engine draws no dissolve: the reader fades the layers instead.
	 */
	default boolean setDissolve(int side, float ramp, float softness, float drift, float cellsX, float cellsY)
	{return false;}

	/**
	 * Makes the layers begun next, until it is called again with {@code amount} 0, mixed toward {@code r}, {@code g},
	 * {@code b} by {@code amount}, 0 to 1, after the page's fog, their alpha kept: a transfert through a colour
	 * or a fog creep ({@link TransfertStyle#throughColor}, {@link TransfertStyle#fogCreep},
	 * {@link TransfertStyle#gradeAt}). The reader then begins every IMAGE,
	 * SEQUENCE and SHADER layer, fog or not. False when this engine draws no grade: the reader fades the layers out to
	 * the gradients instead.
	 */
	default boolean setGrade(float r, float g, float b, float amount)
	{return false;}
}
