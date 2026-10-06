package jks.tools2d.parallax;

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
	 * ({@link GdxLayerEffects#HAZE_R}, G, B) by {@code haze}, 0 to 1, its alpha kept: the depth haze of the FOG layers in
	 * front of it ({@link ParallaxPageReader#hazeOf}). The reader calls it for a SHADER layer, and for an IMAGE or
	 * SEQUENCE layer whose haze is above 0, drawn plain. One that draws no haze draws the SHADER layers' effects only.
	 */
	default boolean begin(Batch batch, ParallaxLayer layer, float phase, float haze)
	{return layer.getKind() == Enum_LayerKind.SHADER && begin(batch, layer, phase);}

	/** Puts the batch back as {@link #begin} found it; called only after a begin that returned true. */
	void end(Batch batch, ParallaxLayer layer);
}
