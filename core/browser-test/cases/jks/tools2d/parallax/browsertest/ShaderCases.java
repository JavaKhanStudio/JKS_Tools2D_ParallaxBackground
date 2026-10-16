package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
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
				new BrowserCase("reader: swellRepeatsOverFogsPeriods", () -> new ShaderCases().swellRepeatsOverFogsPeriods()),
				new BrowserCase("reader: mirrorIsASwellThatMirrorsItsPagesSky", () -> new ShaderCases().mirrorIsASwellThatMirrorsItsPagesSky()),
				new BrowserCase("reader: fogNoiseRunsOnAcrossTheTiles", () -> new ShaderCases().fogNoiseRunsOnAcrossTheTiles()),
				new BrowserCase("reader: fogNoiseRunsOnUpTheTiles", () -> new ShaderCases().fogNoiseRunsOnUpTheTiles()),
				new BrowserCase("reader: bothPagesOfACrossFadeShadeOnOneClock", () -> new ShaderCases().bothPagesOfACrossFadeShadeOnOneClock()),
				new BrowserCase("reader: aShaderLayerWithoutItsEffectIsDrawnPlain", () -> new ShaderCases().aShaderLayerWithoutItsEffectIsDrawnPlain()),
				new BrowserCase("reader: shaderNumbersAreTheDrawnImages", () -> new ShaderCases().shaderNumbersAreTheDrawnImages()),
				new BrowserCase("reader: glintWavesAreWholeTurnsOverTheEffectPeriod", () -> new ShaderCases().glintWavesAreWholeTurnsOverTheEffectPeriod()));
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
	 * r431: SWELL's waves are whole turns over 8 wavelengths across and 10 up, FOG's periods: its phase wraps there, and
	 * so does where it starts along the layer. At one wavelength its second wave (5 turns in 8) jumped each time the layer
	 * wrapped (r419). Its amplitude, in world units, is not held within 1 as FOG's is, and it has a shader of its own.
	 */
	void swellRepeatsOverFogsPeriods()
	{
		ParallaxLayer swell = ParallaxLayer.shader(region(1920, 1080), 40, 0.5f, Enum_ShaderEffect.SWELL, 1.7f, 10, 2);
		equal(80, swell.getShaderPeriod(), 0, "8 wavelengths across");
		equal(100, swell.getShaderPeriodY(), 0, "10 wavelengths up");
		equal(30, swell.getShaderPhase(15), 1e-4f, "30 is past one wavelength, inside 8");
		equal(4, swell.getShaderPhase(42), 1e-4f, "84 wraps to 4 past 80");
		swell.setParallaxSpeedRatioX(1);
		for (int i = 0; i < 5; i++)
			swell.act(1, 25, 0, true, false);
		float apart = Math.abs((swell.getEffectStartX() + 125) % 80);
		isTrue(apart < 1e-3f || Math.abs(apart - 80) < 1e-3f, "starts at " + swell.getEffectStartX() + ", the unwrapped -125 modulo 80");
		isTrue(Math.abs(swell.getEffectStartX()) < 80, "and stays within the period");
		float[] numbers = GdxLayerEffects.uniforms(swell, 3, new float[15]);
		equal(1.7f, numbers[6], 0, "the amplitude in world units, as stored");
		equal(10, numbers[7], 0, "wavelength");
		equal(3, numbers[8], 0, "phase");
		isTrue(GdxLayerEffects.fragment(Enum_ShaderEffect.SWELL) != GdxLayerEffects.fragment(Enum_ShaderEffect.FOG)
				&& GdxLayerEffects.fragment(Enum_ShaderEffect.SWELL) != GdxLayerEffects.fragment(Enum_ShaderEffect.WAVE), "a shader of its own");
	}

	/**
	 * r455: MIRROR is SWELL's water mirroring its page's sky: SWELL's periods, a shader of its own, Simon's four numbers,
	 * the horizon in local() (its fraction of the layer's height) and the depth, the sky's two colours the page's top
	 * gradient; its glint's sun is Simon's, every other effect's GLINT_SUN_*.
	 */
	void mirrorIsASwellThatMirrorsItsPagesSky()
	{
		ParallaxLayer sea = ParallaxLayer.shader(region(1024, 384), 40, 0.5f, Enum_ShaderEffect.MIRROR, 0.12f, 1.25f, 0.35f);
		equal(10, sea.getShaderPeriod(), 0, "8 wavelengths across");
		equal(12.5f, sea.getShaderPeriodY(), 0, "10 wavelengths up");
		isTrue(GdxLayerEffects.fragment(Enum_ShaderEffect.MIRROR) != GdxLayerEffects.fragment(Enum_ShaderEffect.SWELL), "a shader of its own");
		sea.setMirrorHorizon(0.25f);
		sea.setMirrorDepth(3);
		sea.setMirrorSky(new Color(0.8f, 0.9f, 0.95f, 1), new Color(0.3f, 0.55f, 0.8f, 1));
		float[] m = GdxLayerEffects.mirrorUniforms(sea, new float[12]);
		equal(0.15f, m[0], 0, "Simon's strength");
		equal(3, m[1], 0, "relief");
		equal(1, m[2], 0, "shine");
		equal(1, m[3], 0, "tint");
		equal(0.25f * sea.getHeight(), m[4], 1e-5f, "the horizon a quarter of the layer up, in world units");
		equal(3, m[5], 0, "the depth");
		equal(0.9f, m[7], 0, "the horizon's sky");
		equal(0.55f, m[10], 0, "and the top's");
		sea.setMirrorDepth(0);
		equal(0.01f, GdxLayerEffects.mirrorUniforms(sea, m)[5], 0, "no depth divides by none");
		sea.setGlintStrength(0.5f);
		float[] glint = GdxLayerEffects.glintUniforms(sea, new float[14]);
		equal(GdxLayerEffects.MIRROR_SUN_X, glint[4], 0, "MIRROR's sun");
		equal(GdxLayerEffects.MIRROR_SUN_WIDTH, glint[5], 0, "and its width");
		sea.setShaderEffect(Enum_ShaderEffect.SWELL);
		equal(GdxLayerEffects.GLINT_SUN_X, GdxLayerEffects.glintUniforms(sea, glint)[4], 0, "SWELL keeps the shipped sun");
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

	/**
	 * A SHADER layer's glint (r408): its waves are whole turns over the effect's period, which a glinting layer takes as
	 * FOG's (8 wavelengths across, 10 up) whatever its effect, so it runs on across the layer's wrap; it pulses on the
	 * reader's clock; no strength, no wavelength or not a SHADER layer: none.
	 */
	void glintWavesAreWholeTurnsOverTheEffectPeriod()
	{
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.SHADER, Arrays.asList(region(1920, 1080)), true, 40, 0, 0, 1);
		layer.setShaderEffect(Enum_ShaderEffect.WAVE);
		layer.setShaderWavelength(2);
		equal(2, layer.getShaderPeriod(), 0, "WAVE alone repeats every wavelength");
		layer.setGlintStrength(0.5f);
		equal(16, layer.getShaderPeriod(), 0, "glinting, every 8 across");
		equal(20, layer.getShaderPeriodY(), 0, "and 10 up");
		layer.setAnimTime(12.5);
		float[] glint = GdxLayerEffects.glintUniforms(layer, new float[14]);
		equal(0.5f, glint[0], 0, "its strength");
		equal(Float.floatToIntBits((float) (12.5 * GdxLayerEffects.GLINT_TWINKLE % 1000.0)), Float.floatToIntBits(glint[2]), 0, "its pulses on the reader's clock");
		for (int k = 6; k < 12; k++)
			equal(Math.round(glint[k]), glint[k], 0, "wave " + k + " a whole number of turns");
		// 16 world units over 2.5 glints of 0.8: 8 pieces across; 20 up over 0.9: 22 rows.
		equal(8, glint[6], 0, "pieces across the period");
		equal(22, glint[7], 0, "rows up it");
		equal(16, glint[12], 0, "over the period across");
		equal(20, glint[13], 0, "and up");

		layer.setShaderEffect(Enum_ShaderEffect.FOG);
		equal(16, layer.getShaderPeriod(), 0, "FOG's own period is the glint's");
		layer.setShaderWavelength(0);
		equal(0, GdxLayerEffects.glintUniforms(layer, glint)[0], 0, "no wavelength: no glint");
		layer.setShaderWavelength(2);
		layer.setGlintStrength(0);
		equal(0, GdxLayerEffects.glintUniforms(layer, glint)[0], 0, "no strength: none");
		ParallaxLayer image = new ParallaxLayer(region(1920, 1080), true, 40, 0, 0, 1);
		image.setShaderWavelength(2);
		image.setGlintStrength(0.5f);
		isTrue(!image.glints(), "an IMAGE layer never glints");
	}
}
