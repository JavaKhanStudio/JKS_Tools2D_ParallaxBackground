package jks.tools2d.parallax.jme;

import java.util.Map;
import java.util.WeakHashMap;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;

/**
 * The SHADER layers' effects in jME: a ParallaxEffect material per layer (its numbers change every frame), which
 * {@link JmeBatch} draws the layer's run with. The numbers are {@link GdxLayerEffects#uniforms}, the shader
 * ParallaxEffect.frag: libGDX's, line for line. An IMAGE or SEQUENCE layer behind a FOG layer's depth haze gets one too,
 * with no effect (Plain). {@link PlaxBackground} sets it on its reader.
 */
public class JmeLayerEffects implements LayerEffects
{
	static final String MATERIAL = "jks/tools2d/parallax/jme/ParallaxEffect.j3md";

	/** A layer's material and the vectors its parameters hold, refilled each frame. */
	private static final class Shaded
	{
		final Material material;
		/** The effect, null for a layer drawn through the haze only. */
		final Enum_ShaderEffect kind;
		final Vector4f region = new Vector4f();
		final Vector2f size = new Vector2f();
		final Vector3f effect = new Vector3f();

		Shaded(Material material, Enum_ShaderEffect kind)
		{
			this.material = material;
			this.kind = kind;
		}
	}

	private final AssetManager assets;
	/** By layer, dropped with it: a page's layers go when the game stops showing it. */
	private final Map<ParallaxLayer, Shaded> shaded = new WeakHashMap<>();
	private final float[] numbers = new float[9];

	public JmeLayerEffects(AssetManager assets)
	{this.assets = assets;}

	@Override
	public boolean begin(Batch batch, ParallaxLayer layer, float phase)
	{return begin(batch, layer, phase, 0);}

	@Override
	public boolean begin(Batch batch, ParallaxLayer layer, float phase, float haze)
	{
		if (!(batch instanceof JmeBatch))
			return false;
		Enum_ShaderEffect kind = layer.getKind() == Enum_LayerKind.SHADER ? layer.getShaderEffect() : null;
		Shaded s = shaded.get(layer);
		if (s == null || s.kind != kind)
		{
			Material material = new Material(assets, MATERIAL);
			material.setBoolean("Fog", kind == Enum_ShaderEffect.FOG);
			material.setBoolean("Plain", kind == null);
			JmeBatch.renderAsRuns(material);
			s = new Shaded(material, kind);
			shaded.put(layer, s);
		}
		GdxLayerEffects.uniforms(layer, phase, numbers);
		s.region.set(numbers[0], numbers[1], numbers[2], numbers[3]);
		s.size.set(numbers[4], numbers[5]);
		s.effect.set(numbers[6], numbers[7], numbers[8]);
		s.material.setVector4("Region", s.region);
		s.material.setVector2("Size", s.size);
		s.material.setVector3("Effect", s.effect);
		s.material.setFloat("Haze", Math.max(0, Math.min(1, haze)));
		((JmeBatch) batch).setEffect(s.material);
		return true;
	}

	@Override
	public void end(Batch batch, ParallaxLayer layer)
	{((JmeBatch) batch).setEffect(null);}
}
