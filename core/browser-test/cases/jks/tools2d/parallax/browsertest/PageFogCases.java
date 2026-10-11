package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isFalse;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;

import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * ParallaxPageReader's cases on a page's depth fog: each layer's share, the engine's page fog shader, each page of a
 * cross-fade its own. Shared builders in {@link ReaderFixtures}.
 */
final class PageFogCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: pageFogMixesEachLayerMoreTheSlowerItScrolls", () -> new PageFogCases().pageFogMixesEachLayerMoreTheSlowerItScrolls()),
				new BrowserCase("reader: noFogDrawsEveryImageLayerAsBefore", () -> new PageFogCases().noFogDrawsEveryImageLayerAsBefore()),
				new BrowserCase("reader: eachPageOfACrossFadeHasItsOwnFog", () -> new PageFogCases().eachPageOfACrossFadeHasItsOwnFog()),
				new BrowserCase("reader: pageFogDrawsTheImageLayersInOneShaderTheirFogInTheBatchColor", () -> new PageFogCases().pageFogDrawsTheImageLayersInOneShaderTheirFogInTheBatchColor()));
	}

	/** Stands for an engine's shaders with the depth fog: records each layer begun, its fog and the fog's colour. */
	private final class FogEffects implements LayerEffects
	{
		final List<ParallaxLayer> layers = new ArrayList<>();
		final List<Float> fogs = new ArrayList<>();
		final List<Color> colors = new ArrayList<>();

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{throw new AssertionError("the reader calls the begin that takes the fog");}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fog, Color fogColor)
		{
			layers.add(layer);
			fogs.add(fog);
			colors.add(new Color(fogColor));
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{}

		/** The fog {@code layer} was last begun with; -1 when it was not begun. */
		float fogOf(ParallaxLayer layer)
		{
			int i = layers.lastIndexOf(layer);
			return i < 0 ? -1 : fogs.get(i);
		}

		Color colorOf(ParallaxLayer layer)
		{return colors.get(layers.lastIndexOf(layer));}
	}

	/** A FOG layer of {@code haze} (format 9's depth haze, no longer drawn), 12 x 6.75 world units in a 40-wide world. */
	private static ParallaxLayer mist(float haze)
	{
		ParallaxLayer layer = ParallaxLayer.shader(region(1920, 1080), 40, 0.3f, Enum_ShaderEffect.FOG, 0.5f, 3, 1);
		layer.setShaderHaze(haze);
		return layer;
	}

	/**
	 * r217: the page's fog hands each IMAGE, SEQUENCE and SHADER layer to the engine's effects with
	 * 1 - exp(-strength (1 / speed - 1 / front)), front the page's fastest speed ratio X, in its colour: none for the
	 * front layer, more the slower a layer scrolls, all of it for a still one; an EMPTY layer is drawn as is, and a FOG
	 * layer's format 9 haze changes nothing.
	 */
	void pageFogMixesEachLayerMoreTheSlowerItScrolls()
	{
		ParallaxPageReader reader = reader(true, false);
		FogEffects effects = new FogEffects();
		reader.setLayerEffects(effects);
		Color night = new Color(0.2f, 0.25f, 0.4f, 1);
		reader.setFog(0.05f, night);
		ParallaxLayer still = atSpeed(0), far = atSpeed(0.005f), slot = ParallaxLayer.empty("slot", 0.3f), near = atSpeed(-0.05f);
		ParallaxLayer wave = shaded(0.5f, 3, 1), haze = mist(0.9f), front = atSpeed(0.1f);
		haze.setParallaxSpeedRatioX(0.1f);
		slot.setParallaxSpeedRatioX(0.001f);
		reader.addLayers(list(still, far, slot, near, wave, haze, front));
		reader.draw(camera, batch);

		equal(-1, effects.fogOf(front), 0, "the front layer is drawn as is");
		equal(0, effects.fogOf(haze), 0, "a FOG layer at the front's speed: its effect, no fog; its old haze ignored");
		equal(fog(0.05f, 0.02f, 0.1f), effects.fogOf(wave), 1e-6f, "a SHADER layer at 0.02");
		equal(fog(0.05f, 0.05f, 0.1f), effects.fogOf(near), 1e-6f, "a layer scrolling backward: by its speed's size");
		equal(-1, effects.fogOf(slot), 0, "an EMPTY layer is not fogged");
		equal(fog(0.05f, 0.005f, 0.1f), effects.fogOf(far), 1e-6f, "the far layer, 1 - exp(-0.05 x 190)");
		isTrue(effects.fogOf(far) > 0.99f && effects.fogOf(near) < 0.4f, "far near-hidden, near under half");
		equal(1, effects.fogOf(still), 0, "a still layer: all fog");
		isTrue(effects.colorOf(far).equals(night), "in the page's colour " + effects.colorOf(far));

		equal(0, ParallaxPageReader.fogOf(0.05f, 0, far), 0, "a page that does not scroll has no fog");
		equal(0, ParallaxPageReader.fogOf(0, 0.1f, far), 0, "strength 0: none");
		equal(0.1f, ParallaxPageReader.frontSpeedOf(list(far, near, front)), 0, "the front: the fastest speed's size");
		equal(0, ParallaxPageReader.frontSpeedOf(new ArrayList<ParallaxLayer>()), 0, "of no layer: 0");
	}

	/**
	 * Without fog, the IMAGE layers are never handed to the engine's effects, so they draw as before r211, with no
	 * shader and no flush; a LayerEffects written before the fog still draws the SHADER layers.
	 */
	void noFogDrawsEveryImageLayerAsBefore()
	{
		ParallaxPageReader reader = reader(true, false);
		FogEffects effects = new FogEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer back = atSpeed(0.001f), fog = mist(0.5f);
		reader.addLayers(list(back, fog));
		reader.draw(camera, batch);
		equal(-1, effects.fogOf(back), 0, "no fog hands the layer behind to nobody, whatever the FOG layer's old haze");
		equal(0, effects.fogOf(fog), 0, "the FOG layer is drawn through its effect, fog 0");

		ParallaxPageReader old = reader(true, false);
		RecordingEffects before = new RecordingEffects();
		old.setLayerEffects(before);
		old.setFog(0.5f, Color.WHITE);
		old.addLayers(list(atSpeed(0.001f), mist(0)));
		draws.clear();
		old.draw(camera, batch);
		equal(1, before.runs.size(), "a LayerEffects without the fog: the FOG layer only, through its effect");
		isTrue(before.runs.get(0)[0] > 0, "the fogged layer behind it is drawn, plain");
	}

	/** Stands for an engine with a page fog: records its begins and ends, and the SHADER layers begun. */
	private final class PageFogEffects implements LayerEffects
	{
		int begins, ends, shaderLayers;
		boolean on;
		final Color tint = new Color(), fog = new Color(), incoming = new Color();

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{
			isTrue(layer.getKind() == Enum_LayerKind.SHADER, "only a SHADER layer is begun under the page fog");
			shaderLayers++;
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{}

		@Override
		public boolean beginPageFog(Batch batch, Color tint, Color fog, Color incomingFog)
		{
			isFalse(on, "the page fog begun twice");
			begins++;
			on = true;
			this.tint.set(tint);
			this.fog.set(fog);
			incoming.set(incomingFog);
			return true;
		}

		@Override
		public void endPageFog(Batch batch)
		{
			isTrue(on, "the page fog ended without a begin");
			ends++;
			on = false;
		}
	}

	/** The red of the draws, in order, a run of equal ones kept once: one per layer drawn. */
	private List<Float> redsByLayer()
	{
		List<Float> reds = new ArrayList<>();
		for (float[] draw : draws)
			if (reds.isEmpty() || reds.get(reds.size() - 1) != draw[4])
				reds.add(draw[4]);
		return reds;
	}

	/**
	 * r217: an engine with a page fog draws a page's IMAGE layers through one shader, each layer's fog in the batch
	 * color's red, no begin per layer; a SHADER layer is still begun and drawn with the tint (the batch color put back
	 * after a fogged layer, outside a cross-fade too); a hook gets the game's shader and the tint, the page fog begun
	 * again after it; the fog ends with the draw.
	 */
	void pageFogDrawsTheImageLayersInOneShaderTheirFogInTheBatchColor()
	{
		ParallaxPageReader reader = reader(true, false);
		final PageFogEffects effects = new PageFogEffects();
		reader.setLayerEffects(effects);
		Color night = new Color(0.2f, 0.25f, 0.4f, 1);
		reader.setFog(0.05f, night);
		reader.addColorTransfert(new Color(0.5f, 1, 1, 1), 0);
		final float[] hookRed = { -1 };
		reader.setLayerHook("slot", new LayerHook()
		{
			@Override
			public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
			{
				isFalse(effects.on, "a hook draws with the game's shader");
				hookRed[0] = batch.getColor().r;
			}
		});
		ParallaxLayer slot = ParallaxLayer.empty("slot", 0.3f);
		slot.setParallaxSpeedRatioX(0.03f);
		ParallaxLayer wave = shaded(0.5f, 3, 1);
		reader.addLayers(list(atSpeed(0.01f), wave, slot, atSpeed(0.05f), atSpeed(0.1f)));
		reader.draw(camera, batch);

		List<Float> reds = redsByLayer();
		equal(4, reds.size(), "four layers drawn: " + reds);
		equal(fog(0.05f, 0.01f, 0.1f), reds.get(0), 1e-6f, "the back layer: its fog in the red");
		equal(0.5f, reds.get(1), 0, "the SHADER layer after it: the tint, not the fog");
		equal(fog(0.05f, 0.05f, 0.1f), reds.get(2), 1e-6f, "a layer after the hook");
		equal(0, reds.get(3), 0, "the front layer: no fog");
		equal(0.5f, hookRed[0], 0, "the hook gets the tint");
		equal(1, effects.shaderLayers, "only the SHADER layer begun");
		equal(2, effects.begins, "the page fog begun, then again after the hook");
		equal(2, effects.ends, "ended before the hook and after the page");
		isTrue(effects.fog.equals(night) && effects.tint.equals(new Color(0.5f, 1, 1, 1)), "with the page's fog colour and the tint");
		isTrue(batch.getColor().equals(Color.WHITE), "the batch white after the draw");

		ParallaxPageReader clear = reader(true, false);
		PageFogEffects none = new PageFogEffects();
		clear.setLayerEffects(none);
		clear.addLayers(list(atSpeed(0.01f), atSpeed(0.1f)));
		clear.draw(camera, batch);
		equal(0, none.begins, "no fog, no page fog: the page drawn as before");
	}

	/**
	 * During a cross-fade each page's layers take their own page's fog, strength, front and colour; once it ends the
	 * incoming page's is the reader's. A page read before format 10 fogs as its largest FOG layer's haze.
	 */
	void eachPageOfACrossFadeHasItsOwnFog()
	{
		ParallaxPageReader reader = reader(true, false);
		FogEffects effects = new FogEffects();
		reader.setLayerEffects(effects);
		reader.setFog(0.1f, Color.WHITE);
		ParallaxLayer oldBack = atSpeed(0.01f), oldFront = atSpeed(0.1f);
		reader.addLayers(list(oldBack, oldFront));
		ParallaxLayer newBack = atSpeed(0.01f), newFront = atSpeed(0.2f);
		WholePage_Model next = page(list(newBack, newFront));
		Color dusk = new Color(0.8f, 0.5f, 0.6f, 1);
		next.setFogStrength(0.02f);
		next.fogColor = dusk;
		reader.addLayersTransfert(next, 2);
		reader.act(1, 0, 0);
		reader.draw(camera, batch);
		equal(fog(0.1f, 0.01f, 0.1f), effects.fogOf(oldBack), 1e-6f, "the outgoing page: its own fog");
		equal(fog(0.02f, 0.01f, 0.2f), effects.fogOf(newBack), 1e-6f, "the incoming page: its strength and front");
		isTrue(effects.colorOf(newBack).equals(dusk) && effects.colorOf(oldBack).equals(Color.WHITE), "each its colour");

		reader.act(2, 0, 0);
		equal(0.02f, reader.getFogStrength(), 0, "the fade over, the incoming page's fog is the reader's");
		isTrue(reader.getFogColor().equals(dusk), "and its colour");

		WholePage_Model legacy = new WholePage_Model();
		legacy.pageModel.pageList = new ArrayList<>();
		Parallax_Model mistModel = new Parallax_Model(), image = new Parallax_Model();
		mistModel.kind = Enum_LayerKind.SHADER;
		mistModel.shaderEffect = Enum_ShaderEffect.FOG;
		mistModel.shaderHaze = 0.3f;
		image.shaderHaze = 0.9f;
		legacy.pageModel.pageList.add(image);
		legacy.pageModel.pageList.add(mistModel);
		equal(0.3f, legacy.getFogStrength(), 0, "format 9: the largest FOG layer's haze, an IMAGE layer's ignored");
		legacy.setFogStrength(0);
		equal(0, legacy.getFogStrength(), 0, "a fog set, even 0, is the page's");
	}
}
