package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;

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

	/** Puts the batch back as {@link #begin} found it; called only after a begin that returned true. */
	void end(Batch batch, ParallaxLayer layer);
}
