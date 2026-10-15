package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * What ParallaxPageReader's cases share, in code GWT translates: a {@link RecordingBatch} that records the reader's
 * draws without a window, the view, and builders of regions, layers, pages and readers. One class of cases per subject
 * extends it; {@link BrowserSuite} runs them all, core/test BrowserSuiteTest on the JVM and `tools/browser-test.sh` in
 * Chrome. Cover all four repeat modes (X, Y, XY, none).
 */
abstract class ReaderFixtures
{
	final RecordingBatch batch = new RecordingBatch();
	final List<float[]> draws = batch.draws;
	OrthographicCamera camera;

	/** A region of the given pixel size, without any texture behind it. */
	static TextureRegion region(final int width, final int height)
	{
		return new TextureRegion()
		{
			@Override
			public int getRegionWidth()
			{return width;}

			@Override
			public int getRegionHeight()
			{return height;}
		};
	}

	/** An atlas region packed with its whitespace stripped, on a 4096px page that has no GL texture behind it. */
	static AtlasRegion strippedRegion(int width, int height, int originalWidth, int originalHeight, int offsetX, int offsetY)
	{
		Texture page = new Texture()
		{
			@Override
			public int getWidth()
			{return 4096;}

			@Override
			public int getHeight()
			{return 4096;}
		};
		AtlasRegion region = new AtlasRegion(page, 0, 0, width, height);
		region.originalWidth = originalWidth;
		region.originalHeight = originalHeight;
		region.offsetX = offsetX;
		region.offsetY = offsetY;
		return region;
	}

	/** A region of the given pixel size on a 4096px page that has no GL texture behind it: one with texture coordinates. */
	static TextureRegion texturedRegion(int width, int height)
	{
		Texture page = new Texture()
		{
			@Override
			public int getWidth()
			{return 4096;}

			@Override
			public int getHeight()
			{return 4096;}
		};
		return new TextureRegion(page, 0, 0, width, height);
	}

	static ParallaxLayer layer(float decalY)
	{
		ParallaxLayer layer = new ParallaxLayer(region(1920, 1080), true, 40, 0.02f, 0.02f, 1);
		layer.setDecalPercentY(decalY);
		return layer;
	}

	static WholePage_Model page(final List<ParallaxLayer> layers)
	{
		return new WholePage_Model()
		{
			@Override
			public List<ParallaxLayer> getDrawing(String relativePath, float worldWidth, float worldHeight)
			{return layers;}
		};
	}

	static List<ParallaxLayer> list(ParallaxLayer... layers)
	{return new ArrayList<>(Arrays.asList(layers));}

	ParallaxPageReader reader(boolean onX, boolean onY)
	{
		camera = new OrthographicCamera();
		camera.setToOrtho(false, 40, 22.5f);
		camera.update();
		ParallaxPageReader reader = new ParallaxPageReader();
		reader.setRepeatOnX(onX);
		reader.setRepeatOnY(onY);
		return reader;
	}

	/**
	 * Stands for an engine's shaders: records, as indexes into the draws, where each SHADER layer began and ended, and the
	 * phase it was handed.
	 */
	final class RecordingEffects implements LayerEffects
	{
		final List<float[]> runs = new ArrayList<>();
		boolean shades = true;
		/** How many draws were made when the last {@link #beginPatch} came, and how many came: -1 and 0 before. */
		int patchFrom = -1, patches;
		private boolean open;

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{
			isTrue(!open, "a SHADER layer begun inside another");
			if (!shades)
				return false;
			open = true;
			runs.add(new float[] { draws.size(), -1, phase, layer.getShaderAmplitude() });
			return true;
		}

		@Override
		public boolean beginPatch(Batch batch, ParallaxLayer layer)
		{
			isTrue(open, "a patch begun outside its layer");
			patchFrom = draws.size();
			patches++;
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			isTrue(open, "ended without a begin");
			open = false;
			runs.get(runs.size() - 1)[1] = draws.size();
		}
	}


	/** A layer of the 40-wide world scrolling at speed ratio X {@code speed}. */
	static ParallaxLayer atSpeed(float speed)
	{
		ParallaxLayer layer = layer(0);
		layer.setParallaxSpeedRatioX(speed);
		return layer;
	}

	static float fog(float strength, float speed, float front)
	{return 1 - (float) Math.exp(-strength * (1 / speed - 1 / front));}

	/** A SHADER layer drawing a 1920x1080 region through WAVE, 12 x 6.75 world units in a 40-wide world. */
	static ParallaxLayer shaded(float amplitude, float wavelength, float speed)
	{
		ParallaxLayer layer = ParallaxLayer.shader(region(1920, 1080), 40, 0.3f, Enum_ShaderEffect.WAVE, amplitude, wavelength, speed);
		layer.setParallaxSpeedRatioX(0.02f);
		layer.setParallaxSpeedRatioY(0.02f);
		layer.setDecalPercentX(25);
		layer.setDecalPercentY(25);
		return layer;
	}
}
