package jks.tools2d.parallax.jme;

import java.util.Map;
import java.util.WeakHashMap;

import com.badlogic.gdx.graphics.Color;
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
 * ParallaxEffect.frag: libGDX's, line for line. An IMAGE or SEQUENCE layer the page's depth fog reaches gets one too,
 * with no effect (Plain), and so does any layer a transfert's dissolve masks ({@link #setDissolve}) or one through a
 * colour grades ({@link #setGrade}). {@link PlaxBackground}
 * sets it on its reader.
 */
public class JmeLayerEffects implements LayerEffects
{
	static final String MATERIAL = "jks/tools2d/parallax/jme/ParallaxEffect.j3md";

	/** A layer's material and the vectors its parameters hold, refilled each frame. */
	private static final class Shaded
	{
		final Material material;
		/** The effect, null for a layer drawn through the fog only. */
		final Enum_ShaderEffect kind;
		final Vector4f region = new Vector4f();
		final Vector2f size = new Vector2f();
		final Vector3f effect = new Vector3f();
		final Vector3f fog = new Vector3f();
		final Vector4f dissolve = new Vector4f();
		final Vector2f cells = new Vector2f();
		final Vector4f grade = new Vector4f();
		final Vector3f across = new Vector3f();
		final Vector3f up = new Vector3f();

		Shaded(Material material, Enum_ShaderEffect kind)
		{
			this.material = material;
			this.kind = kind;
		}
	}

	private final AssetManager assets;
	/** By layer, dropped with it: a page's layers go when the game stops showing it. */
	private final Map<ParallaxLayer, Shaded> shaded = new WeakHashMap<>();
	private final float[] numbers = new float[15];
	/** The view's size in world units, which FOG's noise is laid across, and where the layers' y 0 is ({@link #setView}). */
	private float viewWidth, viewHeight, viewFloor;
	/** The dissolve the next layers begun draw through ({@link #setDissolve}). */
	private float side, ramp, softness, drift, cellsX, cellsY;
	/** The colour the next layers begun are mixed toward, and by how much ({@link #setGrade}). */
	private float gradeR, gradeG, gradeB, gradeAmount;

	public JmeLayerEffects(AssetManager assets)
	{this.assets = assets;}

	@Override
	public boolean begin(Batch batch, ParallaxLayer layer, float phase)
	{return begin(batch, layer, phase, 0, GdxLayerEffects.HAZE_R, GdxLayerEffects.HAZE_G, GdxLayerEffects.HAZE_B);}

	@Override
	public boolean begin(Batch batch, ParallaxLayer layer, float phase, float haze)
	{return begin(batch, layer, phase, haze, GdxLayerEffects.HAZE_R, GdxLayerEffects.HAZE_G, GdxLayerEffects.HAZE_B);}

	@Override
	public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fog, Color fogColor)
	{return begin(batch, layer, phase, fog, fogColor.r, fogColor.g, fogColor.b);}

	private boolean begin(Batch batch, ParallaxLayer layer, float phase, float haze, float fogR, float fogG, float fogB)
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
		GdxLayerEffects.uniforms(layer, phase, viewWidth, viewHeight, viewFloor, numbers);
		s.region.set(numbers[0], numbers[1], numbers[2], numbers[3]);
		s.size.set(numbers[4], numbers[5]);
		s.effect.set(numbers[6], numbers[7], numbers[8]);
		s.material.setVector4("Region", s.region);
		s.material.setVector2("Size", s.size);
		s.material.setVector3("Effect", s.effect);
		s.across.set(numbers[9], numbers[10], numbers[11]);
		s.material.setVector3("Across", s.across);
		s.up.set(numbers[12], numbers[13], numbers[14]);
		s.material.setVector3("Up", s.up);
		s.material.setFloat("Haze", Math.max(0, Math.min(1, haze)));
		s.fog.set(fogR, fogG, fogB);
		s.material.setVector3("FogColor", s.fog);
		s.dissolve.set(side, ramp, softness, drift);
		s.cells.set(cellsX, cellsY);
		s.material.setVector4("Dissolve", s.dissolve);
		s.material.setVector2("Cells", s.cells);
		s.grade.set(gradeR, gradeG, gradeB, Math.max(0, Math.min(1, gradeAmount)));
		s.material.setVector4("Grade", s.grade);
		((JmeBatch) batch).setEffect(s.material);
		return true;
	}

	@Override
	public boolean setDissolve(int side, float ramp, float softness, float drift, float cellsX, float cellsY)
	{
		this.side = side;
		this.ramp = ramp;
		this.softness = softness;
		this.drift = drift;
		this.cellsX = cellsX;
		this.cellsY = cellsY;
		return true;
	}

	@Override
	public void setView(float width, float height, float floor)
	{
		viewWidth = width;
		viewHeight = height;
		viewFloor = floor;
	}

	@Override
	public boolean setGrade(float r, float g, float b, float amount)
	{
		gradeR = r;
		gradeG = g;
		gradeB = b;
		gradeAmount = amount;
		return true;
	}

	@Override
	public void end(Batch batch, ParallaxLayer layer)
	{((JmeBatch) batch).setEffect(null);}
}
