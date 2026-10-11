package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;

/**
 * ParallaxPageReader's cases on SHADER layers: tiled through the engine's LayerEffects, their phase, FOG's noise
 * across the tiles, the numbers every engine is handed. Shared builders in {@link ReaderFixtures}.
 */
final class ShaderCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: shaderLayerIsTiledThroughItsEffectInEveryRepeatMode", () -> new ShaderCases().shaderLayerIsTiledThroughItsEffectInEveryRepeatMode()),
				new BrowserCase("reader: shaderPhaseFollowsTheReaderClockAndWraps", () -> new ShaderCases().shaderPhaseFollowsTheReaderClockAndWraps()),
				new BrowserCase("reader: fogPhaseWrapsAtItsPeriod", () -> new ShaderCases().fogPhaseWrapsAtItsPeriod()),
				new BrowserCase("reader: fogNoiseRunsOnAcrossTheTiles", () -> new ShaderCases().fogNoiseRunsOnAcrossTheTiles()),
				new BrowserCase("reader: fogNoiseRunsOnUpTheTiles", () -> new ShaderCases().fogNoiseRunsOnUpTheTiles()),
				new BrowserCase("reader: bothPagesOfACrossFadeShadeOnOneClock", () -> new ShaderCases().bothPagesOfACrossFadeShadeOnOneClock()),
				new BrowserCase("reader: aShaderLayerWithoutItsEffectIsDrawnPlain", () -> new ShaderCases().aShaderLayerWithoutItsEffectIsDrawnPlain()),
				new BrowserCase("reader: shaderNumbersAreTheDrawnImages", () -> new ShaderCases().shaderNumbersAreTheDrawnImages()));
	}

	/**
	 * A SHADER layer between two image layers is tiled as an IMAGE layer with the same settings would be, every tile and
	 * its mirrored copy between one begin and one end of its effect, in its place in the draw order.
	 */
	void shaderLayerIsTiledThroughItsEffectInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";

			// The same layer as an IMAGE: what the SHADER one must draw.
			ParallaxPageReader plain = reader(onX, onY);
			plain.setWorldSize(40, 22.5f);
			ParallaxLayer image = new ParallaxLayer(region(1920, 1080), true, 40, 0.02f, 0.02f, 0.3f);
			image.setDecalPercentX(25);
			image.setDecalPercentY(25);
			image.setMirror(true);
			plain.addLayers(list(image));
			draws.clear();
			plain.act(1 / 60f, 500, 300);
			plain.draw(camera, batch);
			List<float[]> expected = new ArrayList<>(draws);

			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			RecordingEffects effects = new RecordingEffects();
			reader.setLayerEffects(effects);
			ParallaxLayer wave = shaded(0.5f, 2, 1);
			wave.setMirror(true);
			reader.addLayers(list(layer(0), wave, layer(0)));
			draws.clear();
			reader.act(1 / 60f, 500, 300);
			reader.draw(camera, batch);

			equal(1, effects.runs.size(), name + ": one effect run a frame");
			float[] run = effects.runs.get(0);
			int from = (int) run[0], to = (int) run[1];
			isTrue(from > 0, name + ": after the layer behind it");
			isTrue(to < draws.size(), name + ": and before the layer in front of it");
			isTrue(expected.size() > 0, name + ": the layer shows");
			equal(expected.size(), to - from, name + ": its tiles, mirrored copies included, are inside the run");
			for (int i = 0; i < expected.size(); i++)
				for (int k = 0; k < 4; k++)
					equal(expected.get(i)[k], draws.get(from + i)[k], 1e-4f, name + ": tile " + i + " as an image layer draws it");
		}
	}

	/** The effect's phase is speed times the reader's clock, wrapped to one wavelength, never negative; 0 without a wavelength. */
	void shaderPhaseFollowsTheReaderClockAndWraps()
	{
		ParallaxPageReader reader = reader(true, false);
		RecordingEffects effects = new RecordingEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer up = shaded(0.5f, 3, 2), down = shaded(0.5f, 3, -2), flat = shaded(0.5f, 0, 2);
		reader.addLayers(list(up, down, flat));
		for (int i = 0; i < 120; i++)
			reader.act(1 / 60f, 0, 0);
		reader.draw(camera, batch);

		equal(2, (float) reader.getEffectTime(), 1e-4f, "the clock counts the acted seconds");
		equal(3, effects.runs.size(), "runs");
		equal(1, effects.runs.get(0)[2], 1e-3f, "2 s at 2 units/s is 4, one past a wavelength of 3");
		equal(2, effects.runs.get(1)[2], 1e-3f, "-4 wraps to 2");
		equal(0, effects.runs.get(2)[2], 0, "no wavelength, no phase");
	}

	/**
	 * FOG's phase wraps at {@link Enum_ShaderEffect#FOG_PERIOD} wavelengths, where its patches repeat (r216): wrapped at
	 * one, as WAVE's, the patches would jump every wavelength the fog drifts.
	 */
	void fogPhaseWrapsAtItsPeriod()
	{
		ParallaxLayer fog = ParallaxLayer.shader(region(1920, 1080), 40, 0.3f, Enum_ShaderEffect.FOG, 0.5f, 3, 2);
		equal(4, fog.getShaderPhase(2), 1e-4f, "4 is past one wavelength of 3, inside 8");
		equal(23, fog.getShaderPhase(11.5), 1e-4f, "23 is inside 8 wavelengths of 3");
		equal(1, fog.getShaderPhase(12.5), 1e-4f, "25 wraps to 1 past 24");
		equal(20, fog.getShaderPhase(-2), 1e-4f, "-4 wraps to 20");
		equal(1, shaded(0.5f, 3, 2).getShaderPhase(2), 1e-4f, "WAVE still wraps at one wavelength");
	}

	/**
	 * r229: FOG's noise starts where the layer would be had it never wrapped on X, kept within its period, so it runs on
	 * across the tiles instead of starting again at each one: getEffectStartX is the unwrapped scroll, modulo 8
	 * wavelengths, plus the image's edge in its tile; the view's width and the direction go to the shader with it.
	 */
	void fogNoiseRunsOnAcrossTheTiles()
	{
		// 20 wide, a period of 80: a tile edge is no whole number of periods.
		ParallaxLayer fog = ParallaxLayer.shader(region(1920, 1080), 40, 0.5f, Enum_ShaderEffect.FOG, 1, 10, 0);
		fog.setParallaxSpeedRatioX(1);
		float width = fog.getWidth();
		equal(20, width, 1e-4f, "the layer's tile");
		equal(80, fog.getShaderPeriod(), 0, "8 wavelengths");
		equal(0, fog.getEffectStartX(), 0, "unscrolled: the noise starts at the image's left edge");

		float scrolled = 0;
		for (int i = 0; i < 11; i++)
		{
			fog.act(1, 25, 0, true, false);
			scrolled -= 25;
			isTrue(Math.abs(fog.getScrollX()) < width, "the position itself still wraps within a tile, " + fog.getScrollX());
			float apart = (fog.getEffectStartX() - scrolled) % 80;
			isTrue(Math.abs(apart) < 1e-3f || Math.abs(Math.abs(apart) - 80) < 1e-3f,
					"after " + -scrolled + " units the noise starts " + fog.getEffectStartX() + ", the unwrapped " + scrolled + " modulo 80");
			isTrue(Math.abs(fog.getEffectStartX()) < 80 + width, "and stays a small number");
		}

		ParallaxLayer still = ParallaxLayer.shader(region(1920, 1080), 40, 0.5f, Enum_ShaderEffect.FOG, 1, 10, 0);
		still.setParallaxSpeedRatioX(1);
		still.act(1, 25, 0, false, false);
		equal(-25, still.getEffectStartX(), 1e-4f, "not tiled on X: never wrapped, the noise its image had");

		ParallaxLayer flipped = ParallaxLayer.shader(region(1920, 1080), 40, 0.5f, Enum_ShaderEffect.FOG, 1, 10, 0);
		flipped.setFlipX(true);
		equal(20, flipped.getEffectStartX(), 1e-4f, "flipped on X: from the image's right edge");
		float[] numbers = GdxLayerEffects.uniforms(flipped, 0, 33, 12, 0, new float[15]);
		equal(33, numbers[9], 0, "the view's width");
		equal(20, numbers[10], 1e-4f, "where the noise starts");
		equal(-1, numbers[11], 0, "running leftward");
		equal(1, GdxLayerEffects.uniforms(fog, 0, 33, 12, 0, numbers)[11], 0, "rightward unflipped");

		fog.resetPosition();
		equal(0, fog.getEffectStartX(), 1e-4f, "reset: from the decal again");
	}

	/**
	 * r262: the same on Y. getEffectStartY is the unwrapped scroll up, modulo 10 wavelengths (FOG's y terms repeat every
	 * 2, 2 and 1.25), plus the image's edge in its tile; the shader takes it above the reader's drawing height, which
	 * the reader hands its effects with the view's size.
	 */
	void fogNoiseRunsOnUpTheTiles()
	{
		// 20 wide, 11.25 tall, a period up of 100: a tile edge is no whole number of periods.
		ParallaxLayer fog = ParallaxLayer.shader(region(1920, 1080), 40, 0.5f, Enum_ShaderEffect.FOG, 1, 10, 0);
		fog.setParallaxSpeedRatioY(1);
		float height = fog.getHeight();
		equal(11.25f, height, 1e-4f, "the layer's tile");
		equal(100, fog.getShaderPeriodY(), 0, "10 wavelengths up");
		equal(0, fog.getEffectStartY(), 0, "unscrolled: the noise starts at the image's bottom edge");

		float scrolled = 0;
		for (int i = 0; i < 13; i++)
		{
			fog.act(1, 0, 15, false, true);
			scrolled -= 15;
			isTrue(Math.abs(fog.getScrollY()) < height, "the position itself still wraps within a tile, " + fog.getScrollY());
			float apart = (fog.getEffectStartY() - scrolled) % 100;
			isTrue(Math.abs(apart) < 1e-3f || Math.abs(Math.abs(apart) - 100) < 1e-3f,
					"after " + -scrolled + " units the noise starts " + fog.getEffectStartY() + ", the unwrapped " + scrolled + " modulo 100");
			isTrue(Math.abs(fog.getEffectStartY()) < 100 + height, "and stays a small number");
		}
		equal(0, fog.getEffectStartX(), 0, "X untouched by a wrap on Y");

		ParallaxLayer still = ParallaxLayer.shader(region(1920, 1080), 40, 0.5f, Enum_ShaderEffect.FOG, 1, 10, 0);
		still.setParallaxSpeedRatioY(1);
		still.act(1, 0, 15, false, false);
		equal(-15, still.getEffectStartY(), 1e-4f, "not tiled on Y: never wrapped, the noise its image had");

		ParallaxLayer flipped = ParallaxLayer.shader(region(1920, 1080), 40, 0.5f, Enum_ShaderEffect.FOG, 1, 10, 0);
		flipped.setFlipY(true);
		equal(11.25f, flipped.getEffectStartY(), 1e-4f, "flipped on Y: from the image's top edge");
		float[] numbers = GdxLayerEffects.uniforms(flipped, 0, 33, 12, 2, new float[15]);
		equal(12, numbers[12], 0, "the view's height");
		equal(13.25f, numbers[13], 1e-4f, "where the noise starts, above the drawing height");
		equal(-1, numbers[14], 0, "running down");
		equal(1, GdxLayerEffects.uniforms(fog, 0, 33, 12, 2, numbers)[14], 0, "upward unflipped");

		ParallaxPageReader reader = reader(false, true);
		reader.setDrawingHeight(3);
		final float[] view = new float[3];
		reader.setLayerEffects(new LayerEffects()
		{
			@Override
			public boolean begin(Batch batch, ParallaxLayer layer, float phase)
			{return false;}

			@Override
			public void end(Batch batch, ParallaxLayer layer)
			{}

			@Override
			public void setView(float width, float height, float floor)
			{
				view[0] = width;
				view[1] = height;
				view[2] = floor;
			}
		});
		reader.draw(camera, batch);
		equal(40, view[0], 1e-4f, "the reader hands its effects the view's width");
		equal(22.5f, view[1], 1e-4f, "its height");
		equal(3, view[2], 0, "and its drawing height");
	}

	/** During a cross-fade both pages' SHADER layers are drawn through their effect at the same phase, faded. */
	void bothPagesOfACrossFadeShadeOnOneClock()
	{
		ParallaxPageReader reader = reader(true, false);
		RecordingEffects effects = new RecordingEffects();
		reader.setLayerEffects(effects);
		reader.addLayers(list(shaded(0.5f, 3, 0.7f)));
		for (int i = 0; i < 60; i++)
			reader.act(1 / 60f, 100, 0);
		reader.addLayersTransfert(page(list(shaded(0.25f, 3, 0.7f))), 1);
		reader.act(0.5f, 100, 0);
		draws.clear();
		reader.draw(camera, batch);

		equal(2, effects.runs.size(), "both pages' layers shaded");
		equal(0.5f, effects.runs.get(0)[3], 0, "the outgoing one first");
		equal(0.25f, effects.runs.get(1)[3], 0, "then the incoming one");
		equal(effects.runs.get(0)[2], effects.runs.get(1)[2], 0, "one clock: the incoming layer does not start its effect over");
		equal(0.5f, draws.get((int) effects.runs.get(1)[0])[5], 1e-2f, "faded with its page");
	}

	/** An engine that cannot draw the effect (a shader that did not compile) draws the layer as an image. */
	void aShaderLayerWithoutItsEffectIsDrawnPlain()
	{
		ParallaxPageReader reader = reader(true, false);
		RecordingEffects effects = new RecordingEffects();
		effects.shades = false;
		reader.setLayerEffects(effects);
		reader.addLayers(list(shaded(0.5f, 3, 1)));
		reader.draw(camera, batch);
		equal(0, effects.runs.size(), "no run");
		isTrue(draws.size() >= 4, draws.size() + " tiles drawn all the same");
	}

	/**
	 * What every engine hands its shader: the region's texture coordinates, the drawn image's size (the packed part of a
	 * stripped region), and amplitude, wavelength, phase; FOG's amplitude within 0..1; no wavelength, no effect.
	 */
	void shaderNumbersAreTheDrawnImages()
	{
		float unit = 40f / 4559;
		List<TextureRegion> regions = new ArrayList<>();
		regions.add(strippedRegion(3403, 580, 4559, 580, 913, 0));
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.SHADER, regions, true, 40, 0, 0, 1);
		layer.setUseOriginalSize(true);
		layer.setShaderEffect(Enum_ShaderEffect.FOG);
		layer.setShaderAmplitude(1.5f);
		layer.setShaderWavelength(4);
		float[] numbers = GdxLayerEffects.uniforms(layer, 0.75f, new float[9]);
		// The region drawn: half a texel inside the atlas region (r218).
		equal(0.5f / 4096, numbers[0], 1e-7f, "u");
		equal(0.5f / 4096, numbers[1], 1e-7f, "v");
		equal(3402.5f / 4096, numbers[2], 1e-6f, "u2");
		equal(579.5f / 4096, numbers[3], 1e-6f, "v2");
		equal(3403 * unit, numbers[4], 1e-4f, "the packed image's width, not the layer's");
		equal(580 * unit, numbers[5], 1e-4f, "its height");
		equal(1, numbers[6], 0, "FOG thins by 100% at most");
		equal(4, numbers[7], 0, "wavelength");
		equal(0.75f, numbers[8], 0, "phase");

		layer.setShaderEffect(Enum_ShaderEffect.WAVE);
		equal(1.5f, GdxLayerEffects.uniforms(layer, 0.75f, numbers)[6], 0, "WAVE's amplitude is a distance, not clamped");
		layer.setShaderWavelength(0);
		GdxLayerEffects.uniforms(layer, 0.75f, numbers);
		equal(0, numbers[6], 0, "no wavelength: no shift");
		equal(1, numbers[7], 0, "and a wavelength that divides safely");
		equal(0, numbers[8], 0, "at phase 0");
	}
}
