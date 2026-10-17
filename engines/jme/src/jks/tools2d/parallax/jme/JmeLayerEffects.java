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
		final Vector4f glint = new Vector4f();
		final Vector2f sun = new Vector2f();
		final Vector4f glintA = new Vector4f();
		final Vector4f glintB = new Vector4f();
		final Vector2f wrap = new Vector2f();
		final Vector4f mirror = new Vector4f();
		final Vector2f mirrorAt = new Vector2f();
		final Vector3f sky = new Vector3f();
		final Vector3f zenith = new Vector3f();
		float haze;
		/**
		 * The layer's animated patch's material (r397), made when first drawn: the same vectors but its frame's region
		 * and size, so the batch's run of patches is not drawn with the layer's region.
		 */
		Material patch;
		final Vector4f patchRegion = new Vector4f();
		final Vector2f patchSize = new Vector2f();

		Shaded(Material material, Enum_ShaderEffect kind)
		{
			this.material = material;
			this.kind = kind;
		}
	}

	private final AssetManager assets;
	/** By layer, dropped with it: a page's layers go when the game stops showing it. */
	private final Map<ParallaxLayer, Shaded> shaded = new WeakHashMap<>();
	private final float[] numbers = new float[15], glints = new float[14], mirrors = new float[12];
	/** The view's size in world units, which FOG's noise is laid across, and where the layers' y 0 is ({@link #setView}). */
	private float viewWidth, viewHeight, viewLeft, viewFloor;
	/** SWELL's Wrap: 1 on an axis the page repeats on, 0 on one it does not ({@link #setRepeat}); unset, across only. */
	private float wrapX = 1, wrapY;
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
		Enum_ShaderEffect kind = layer.getDrawnEffect();
		Shaded s = shaded.get(layer);
		if (s == null || s.kind != kind)
		{
			Material material = new Material(assets, MATERIAL);
			material.setBoolean("Fog", kind == Enum_ShaderEffect.FOG);
			material.setBoolean("Swell", kind == Enum_ShaderEffect.SWELL);
			material.setBoolean("Mirror", kind == Enum_ShaderEffect.MIRROR);
			material.setBoolean("Plain", kind == null);
			JmeBatch.renderAsRuns(material);
			s = new Shaded(material, kind);
			shaded.put(layer, s);
		}
		GdxLayerEffects.uniforms(layer, phase, viewWidth, viewHeight, viewLeft, viewFloor, numbers);
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
		s.haze = Math.max(0, Math.min(1, haze));
		s.material.setFloat("Haze", s.haze);
		s.fog.set(fogR, fogG, fogB);
		s.material.setVector3("FogColor", s.fog);
		s.dissolve.set(side, ramp, softness, drift);
		s.cells.set(cellsX, cellsY);
		s.material.setVector4("Dissolve", s.dissolve);
		s.material.setVector2("Cells", s.cells);
		s.grade.set(gradeR, gradeG, gradeB, Math.max(0, Math.min(1, gradeAmount)));
		s.material.setVector4("Grade", s.grade);
		GdxLayerEffects.glintUniforms(layer, glints);
		s.glint.set(glints[0], glints[1], glints[2], glints[3]);
		s.sun.set(glints[4], glints[5]);
		s.glintA.set(glints[6], glints[7], glints[8], glints[9]);
		s.glintB.set(glints[10], glints[11], glints[12], glints[13]);
		s.material.setVector4("Glint", s.glint);
		s.material.setVector2("Sun", s.sun);
		s.material.setVector4("GlintA", s.glintA);
		s.material.setVector4("GlintB", s.glintB);
		s.wrap.set(wrapX, wrapY);
		s.material.setVector2("Wrap", s.wrap);
		if (kind == Enum_ShaderEffect.MIRROR)
		{
			GdxLayerEffects.mirrorUniforms(layer, mirrors);
			s.mirror.set(mirrors[0], mirrors[1], mirrors[2], mirrors[3]);
			s.mirrorAt.set(mirrors[4], mirrors[5]);
			s.sky.set(mirrors[6], mirrors[7], mirrors[8]);
			s.zenith.set(mirrors[9], mirrors[10], mirrors[11]);
			s.material.setVector4("MirrorNumbers", s.mirror);
			s.material.setVector2("MirrorAt", s.mirrorAt);
			s.material.setVector3("Sky", s.sky);
			s.material.setVector3("Zenith", s.zenith);
		}
		((JmeBatch) batch).setEffect(s.material);
		return true;
	}

	@Override
	public boolean beginPatch(Batch batch, ParallaxLayer layer)
	{
		Shaded s = shaded.get(layer);
		if (!(batch instanceof JmeBatch) || s == null || layer.getAnimDrawnRegion() == null)
			return false;
		if (s.patch == null)
		{
			s.patch = new Material(assets, MATERIAL);
			s.patch.setBoolean("Fog", s.kind == Enum_ShaderEffect.FOG);
			s.patch.setBoolean("Swell", s.kind == Enum_ShaderEffect.SWELL);
			s.patch.setBoolean("Mirror", s.kind == Enum_ShaderEffect.MIRROR);
			s.patch.setBoolean("Plain", s.kind == null);
			JmeBatch.renderAsRuns(s.patch);
		}
		GdxLayerEffects.patchUniforms(layer, numbers);
		s.patchRegion.set(numbers[0], numbers[1], numbers[2], numbers[3]);
		s.patchSize.set(numbers[4], numbers[5]);
		s.patch.setVector4("Region", s.patchRegion);
		s.patch.setVector2("Size", s.patchSize);
		s.patch.setVector3("Effect", s.effect);
		s.patch.setVector3("Across", s.across);
		s.patch.setVector3("Up", s.up);
		s.patch.setFloat("Haze", s.haze);
		s.patch.setVector3("FogColor", s.fog);
		s.patch.setVector4("Dissolve", s.dissolve);
		s.patch.setVector2("Cells", s.cells);
		s.patch.setVector4("Grade", s.grade);
		s.patch.setVector4("Glint", s.glint);
		s.patch.setVector2("Sun", s.sun);
		s.patch.setVector4("GlintA", s.glintA);
		s.patch.setVector4("GlintB", s.glintB);
		s.patch.setVector2("Wrap", s.wrap);
		if (s.kind == Enum_ShaderEffect.MIRROR)
		{
			s.patch.setVector4("MirrorNumbers", s.mirror);
			s.patch.setVector2("MirrorAt", s.mirrorAt);
			s.patch.setVector3("Sky", s.sky);
			s.patch.setVector3("Zenith", s.zenith);
		}
		((JmeBatch) batch).setEffect(s.patch);
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
	{setView(width, height, 0, floor);}

	@Override
	public void setView(float width, float height, float left, float floor)
	{
		viewWidth = width;
		viewHeight = height;
		viewLeft = left;
		viewFloor = floor;
	}

	@Override
	public void setRepeat(boolean x, boolean y)
	{
		wrapX = x ? 1 : 0;
		wrapY = y ? 1 : 0;
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
