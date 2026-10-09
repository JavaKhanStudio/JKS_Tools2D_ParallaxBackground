package jks.tools2d.parallax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxNativesLoader;

import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The rule "act() and draw() run every frame: no allocation" as a gate: a big page, every repeat mode, a cross-fade and
 * a tint, measured with the thread's allocation counter. One layer in ten plays core/test-data/particles/snow.p, one in
 * ten is a SHADER layer, half WAVE, half FOG, one in ten a SEQUENCE of 64 slots, drawn from the game's seed.
 */
class FrameAllocationTest
{
	private static final int LAYERS = 300, FRAMES = 2000;
	private static final TextureRegion HOOKED = region();
	private static final Path ROOT = Path.of(System.getProperty("parallax.repoRoot", ".."));
	private static ParallaxParticles snow;

	@BeforeAll
	static void natives()
	{
		GdxNativesLoader.load();
		Texture page = new Texture()
		{
			@Override
			public int getWidth()
			{return 64;}

			@Override
			public int getHeight()
			{return 64;}
		};
		TextureAtlas atlas = new TextureAtlas();
		atlas.addRegion("snowflake", page, 0, 0, 8, 8);
		snow = new ParallaxParticles();
		snow.load(new FileHandle(ROOT.resolve("core/test-data/particles/snow.p").toFile()), atlas);
	}

	@Test
	void aFrameAllocatesNothing()
	{
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		assumeTrue(threads.isThreadAllocatedMemorySupported(), "this JVM does not count allocations");
		threads.setThreadAllocatedMemoryEnabled(true);

		OrthographicCamera camera = new OrthographicCamera();
		camera.setToOrtho(false, 40, 22.5f);
		camera.update();
		CountingBatch batch = new CountingBatch();

		for (boolean onX : new boolean[] { true, false })
			for (boolean onY : new boolean[] { true, false })
			{
				ParallaxPageReader reader = new ParallaxPageReader();
				reader.setRepeatOnX(onX);
				reader.setRepeatOnY(onY);
				reader.addLayers(layers(1));
				reader.setLayerEffects(batch);
				// A fog on both pages: through the page fog's shader on X, through each layer's begin otherwise.
				batch.pageFog = onX;
				reader.setFog(0.02f, Color.SLATE);
				reader.setSequenceSeed(5);
				reader.setLayerHook("hooked", (hookBatch, layer, x, y, width, height) -> hookBatch.draw(HOOKED, x, y, width, height));
				WholePage_Model next = page(layers(2));
				next.setFogStrength(0.03f);

				// Warm up: the JIT, and the one-time allocations of starting a transfer, are not per frame.
				float speedX = onX ? 300 : 0, speedY = onY ? 40 : 0;
				frames(reader, camera, batch, FRAMES, speedX, speedY);
				reader.addLayersTransfert(next, 1000);
				reader.addColorTransfert(Color.SKY, 1000);
				frames(reader, camera, batch, FRAMES, speedX, speedY);

				long before = threads.getCurrentThreadAllocatedBytes();
				batch.draws = 0;
				batch.pageFogs = 0;
				frames(reader, camera, batch, FRAMES, speedX, speedY);
				long allocated = threads.getCurrentThreadAllocatedBytes() - before;

				String mode = (onX ? "X" : "") + (onY ? "Y" : "") + (onX || onY ? "" : "none");
				assertTrue(reader.isInTransfer(), mode + ": the frames measured are cross-fading");
				assertTrue(batch.draws >= FRAMES * LAYERS, mode + ": both pages were drawn, " + batch.draws + " draws");
				assertTrue(batch.particles >= FRAMES * 10, mode + ": particles were drawn, " + batch.particles);
				assertTrue(batch.shaded >= FRAMES * 10, mode + ": SHADER layers were drawn through their effect, " + batch.shaded);
				if (onX)
					assertTrue(batch.pageFogs >= FRAMES, mode + ": the page fog was drawn, " + batch.pageFogs);
				else
					assertTrue(batch.shaded >= FRAMES * LAYERS, mode + ": the fogged layers were begun one by one, " + batch.shaded);
				// The counter itself costs a few bytes; a per-frame allocation costs FRAMES times at least 16.
				assertTrue(allocated < FRAMES * 16L, mode + ": " + allocated + " bytes allocated over " + FRAMES + " frames");
			}
	}

	/** Scrolls only on the repeated axes: a layer scrolled out of an unrepeated view is not drawn. */
	private static void frames(ParallaxPageReader reader, OrthographicCamera camera, Batch batch, int count, float speedX, float speedY)
	{
		for (int i = 0; i < count; i++)
		{
			reader.act(1 / 60f, speedX, speedY);
			reader.draw(camera, batch);
		}
	}

	private static List<ParallaxLayer> layers(long seed)
	{
		Random random = new Random(seed);
		List<ParallaxLayer> layers = new ArrayList<>(LAYERS);
		for (int i = 0; i < LAYERS; i++)
		{
			// One layer in ten EMPTY: half drawn by a hook, half named for none. One in ten snowing, half from the view.
			ParallaxLayer layer = i % 10 == 5 ? ParallaxLayer.empty(i % 20 == 5 ? "hooked" : "nobody", 0.05f + random.nextFloat())
					: i % 10 == 3 ? ParallaxLayer.shader(region(), 40, 0.05f + random.nextFloat(), i % 20 == 3 ? Enum_ShaderEffect.WAVE : Enum_ShaderEffect.FOG, 0.5f, 2, 1)
					: i % 10 == 1 ? ParallaxLayer.sequence(new ArrayList<>(List.of(region(), narrowRegion(), region())), new int[] { 3, 5, 2 },
							(int) seed * 31 + i, 64, 40, 0.05f + random.nextFloat())
					: i % 10 == 7 ? ParallaxLayer.particles(new ParallaxParticles(snow), i % 20 == 7 ? Enum_ParticleAnchor.VIEW : Enum_ParticleAnchor.LAYER, 0.05f + random.nextFloat())
					: new ParallaxLayer(region(), true, 40, 0.01f + random.nextFloat() * 0.05f, 0.01f + random.nextFloat() * 0.05f, 0.05f + random.nextFloat());
			layer.setParallaxSpeedRatioX(0.01f + random.nextFloat() * 0.05f);
			layer.setParallaxSpeedRatioY(0.01f + random.nextFloat() * 0.05f);
			layer.setDecalPercentX(random.nextFloat() * 100);
			layer.setDecalPercentY(random.nextFloat() * 50);
			layer.setPadX(random.nextFloat() * 5);
			layer.setPadY(random.nextFloat() * 5);
			layer.setFlipX(random.nextBoolean());
			layer.setMirror(random.nextBoolean());
			layers.add(layer);
		}
		return layers;
	}

	private static WholePage_Model page(List<ParallaxLayer> layers)
	{
		return new WholePage_Model()
		{
			@Override
			public List<ParallaxLayer> getDrawing(String relativePath, float worldWidth, float worldHeight)
			{return layers;}
		};
	}

	/** A segment a third as wide as {@link #region()} for its height. */
	private static TextureRegion narrowRegion()
	{
		return new TextureRegion()
		{
			@Override
			public int getRegionWidth()
			{return 640;}

			@Override
			public int getRegionHeight()
			{return 1080;}
		};
	}

	private static TextureRegion region()
	{
		return new TextureRegion()
		{
			@Override
			public int getRegionWidth()
			{return 1920;}

			@Override
			public int getRegionHeight()
			{return 1080;}
		};
	}

	/**
	 * Counts region draws and allocates nothing, unlike a Proxy (which boxes every float it is handed); and stands for
	 * the engine's shaders, computing what GdxLayerEffects hands them.
	 */
	private static final class CountingBatch implements Batch, LayerEffects
	{
		long draws, particles, shaded, pageFogs;
		/** Whether it draws the page fog in one shader, as GdxLayerEffects does, or is begun per fogged layer. */
		boolean pageFog;
		private final float[] numbers = new float[9];

		@Override
		public boolean beginPageFog(Batch batch, Color tint, Color fog, Color incomingFog)
		{
			if (pageFog)
				pageFogs++;
			return pageFog;
		}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{
			GdxLayerEffects.uniforms(layer, phase, numbers);
			shaded++;
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{}

		private final Color color = new Color();
		private final Matrix4 matrix = new Matrix4();

		@Override
		public void draw(TextureRegion region, float x, float y, float width, float height)
		{draws++;}

		@Override
		public void setColor(float r, float g, float b, float a)
		{color.set(r, g, b, a);}

		@Override
		public void setColor(Color tint)
		{color.set(tint);}

		@Override
		public Color getColor()
		{return color;}

		@Override public void begin() {}
		@Override public void end() {}
		@Override public void setPackedColor(float packedColor) {}
		@Override public float getPackedColor() {return color.toFloatBits();}
		@Override public void draw(Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {}
		@Override public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {}
		@Override public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {}
		@Override public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) {}
		@Override public void draw(Texture texture, float x, float y) {}
		@Override public void draw(Texture texture, float x, float y, float width, float height) {}
		@Override public void draw(Texture texture, float[] spriteVertices, int offset, int count) {particles++;}
		@Override public void draw(TextureRegion region, float x, float y) {}
		@Override public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {}
		@Override public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, boolean clockwise) {}
		@Override public void draw(TextureRegion region, float width, float height, Affine2 transform) {}
		@Override public void flush() {}
		@Override public void disableBlending() {}
		@Override public void enableBlending() {}
		@Override public void setBlendFunction(int srcFunc, int dstFunc) {}
		@Override public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor, int srcFuncAlpha, int dstFuncAlpha) {}
		@Override public int getBlendSrcFunc() {return 0;}
		@Override public int getBlendDstFunc() {return 0;}
		@Override public int getBlendSrcFuncAlpha() {return 0;}
		@Override public int getBlendDstFuncAlpha() {return 0;}
		@Override public Matrix4 getProjectionMatrix() {return matrix;}
		@Override public Matrix4 getTransformMatrix() {return matrix;}
		@Override public void setProjectionMatrix(Matrix4 projection) {}
		@Override public void setTransformMatrix(Matrix4 transform) {}
		@Override public void setShader(ShaderProgram shader) {}
		@Override public ShaderProgram getShader() {return null;}
		@Override public boolean isBlendingEnabled() {return true;}
		@Override public boolean isDrawing() {return true;}
		@Override public void dispose() {}
	}
}
