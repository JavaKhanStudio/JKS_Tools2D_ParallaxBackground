package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;

import jks.tools2d.parallax.Enum_Detail;
import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.LayerDetail;
import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;

/**
 * ParallaxPageReader's cases on detail levels (r464): what a game's {@link Enum_Detail} leaves of each layer, the
 * default one and those it names ({@link LayerDetail}). Shared builders in {@link ReaderFixtures}.
 */
final class DetailCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: detailOffDrawsEveryShaderLayerAsItsImageInEveryRepeatMode", () -> new DetailCases().detailOffDrawsEveryShaderLayerAsItsImageInEveryRepeatMode()),
				new BrowserCase("reader: detailMediumDrawsAMirrorThroughSwellInSwellsSun", () -> new DetailCases().detailMediumDrawsAMirrorThroughSwellInSwellsSun()),
				new BrowserCase("reader: detailOffKeepsThePageFog", () -> new DetailCases().detailOffKeepsThePageFog()),
				new BrowserCase("reader: aNamedLayerIsDrawnFromItsOwnLevels", () -> new DetailCases().aNamedLayerIsDrawnFromItsOwnLevels()),
				new BrowserCase("reader: aHiddenLayerScrollsOnAndBothPagesOfACrossFadeFollowTheLevel", () -> new DetailCases().aHiddenLayerScrollsOnAndBothPagesOfACrossFadeFollowTheLevel()));
	}

	/** Stands for an engine's shaders: records each layer begun, the effect it is drawn through, its glint and its fog. */
	private final class DetailEffects implements LayerEffects
	{
		final List<ParallaxLayer> layers = new ArrayList<>();
		final List<Enum_ShaderEffect> drawn = new ArrayList<>();
		final List<Boolean> glints = new ArrayList<>();
		final List<Float> fogs = new ArrayList<>();
		final List<float[]> suns = new ArrayList<>();

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{throw new AssertionError("the reader calls the begin that takes the fog");}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fog, Color fogColor)
		{
			layers.add(layer);
			drawn.add(layer.getDrawnEffect());
			glints.add(layer.drawsGlint());
			fogs.add(fog);
			float[] glint = GdxLayerEffects.glintUniforms(layer, new float[14]);
			suns.add(new float[] { glint[0], glint[4], glint[5] });
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{}

		void clear()
		{
			layers.clear();
			drawn.clear();
			glints.clear();
			fogs.clear();
			suns.clear();
		}
	}

	/** A glinting sea through MIRROR, 12 x 4.5 world units in a 40-wide world, named so. */
	private static ParallaxLayer sea(String name)
	{
		ParallaxLayer layer = ParallaxLayer.shader(region(1024, 384), 40, 0.3f, Enum_ShaderEffect.MIRROR, 0.12f, 1.25f, 0.35f);
		layer.setGlintStrength(0.5f);
		layer.setName(name);
		layer.setParallaxSpeedRatioX(0.02f);
		layer.setDecalPercentY(10);
		return layer;
	}

	/** A WAVE layer named so, glinting. */
	private static ParallaxLayer wave(String name)
	{
		ParallaxLayer layer = shaded(0.5f, 2, 1);
		layer.setGlintStrength(0.4f);
		layer.setName(name);
		return layer;
	}

	/**
	 * At OFF no layer is begun through an effect: the same tiles, in the same places, as at HIGH, every SHADER layer
	 * drawn as its image; HIGH again draws them through their effects, as stored.
	 */
	void detailOffDrawsEveryShaderLayerAsItsImageInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";
			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			DetailEffects effects = new DetailEffects();
			reader.setLayerEffects(effects);
			reader.addLayers(list(layer(0), wave("w"), sea("sea"), layer(0)));
			reader.act(1 / 60f, 500, 300);

			draws.clear();
			reader.draw(camera, batch);
			List<float[]> high = new ArrayList<>(draws);
			equal(2, effects.layers.size(), name + ": at HIGH, both SHADER layers through their effects");
			equal(Enum_ShaderEffect.WAVE, effects.drawn.get(0), name + ": WAVE");
			equal(Enum_ShaderEffect.MIRROR, effects.drawn.get(1), name + ": MIRROR, as stored");

			effects.clear();
			draws.clear();
			reader.setDetail(Enum_Detail.OFF);
			reader.draw(camera, batch);
			equal(0, effects.layers.size(), name + ": at OFF, no effect begun");
			isTrue(high.size() >= 4, name + ": the page shows, " + high.size() + " tiles");
			equal(high.size(), draws.size(), name + ": every tile drawn all the same");
			for (int i = 0; i < high.size(); i++)
				for (int k = 0; k < 4; k++)
					equal(high.get(i)[k], draws.get(i)[k], 1e-4f, name + ": tile " + i + " where HIGH draws it");

			effects.clear();
			reader.setDetail(Enum_Detail.HIGH);
			reader.draw(camera, batch);
			equal(2, effects.layers.size(), name + ": HIGH again: both effects back");
			equal(Enum_ShaderEffect.MIRROR, effects.drawn.get(1), name + ": the reflection too");
		}
	}

	/**
	 * At MEDIUM a MIRROR layer is drawn through SWELL, with its glint in SWELL's sun (Simon on r464), and no MIRROR
	 * numbers: an engine picks its shader, its defines and its uniforms by the drawn effect. Its periods do not move, so
	 * the water does not jump. Every other effect is drawn as stored, glint included.
	 */
	void detailMediumDrawsAMirrorThroughSwellInSwellsSun()
	{
		ParallaxPageReader reader = reader(true, false);
		DetailEffects effects = new DetailEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer sea = sea("sea");
		reader.addLayers(list(wave("w"), sea));
		float periodX = sea.getShaderPeriod(), periodY = sea.getShaderPeriodY(), phase = sea.getShaderPhase(7.5);

		reader.draw(camera, batch);
		equal(GdxLayerEffects.MIRROR_SUN_X, effects.suns.get(1)[1], 0, "HIGH: MIRROR's sun");
		equal(GdxLayerEffects.MIRROR_SUN_WIDTH, effects.suns.get(1)[2], 0, "and its width");

		effects.clear();
		reader.setDetail(Enum_Detail.MEDIUM);
		reader.draw(camera, batch);
		equal(2, effects.layers.size(), "MEDIUM: both layers through an effect");
		equal(Enum_ShaderEffect.WAVE, effects.drawn.get(0), "WAVE as stored");
		isTrue(effects.glints.get(0), "with its glint");
		equal(Enum_ShaderEffect.SWELL, effects.drawn.get(1), "the MIRROR through SWELL");
		equal(Enum_ShaderEffect.MIRROR, sea.getShaderEffect(), "its stored effect untouched");
		isTrue(effects.glints.get(1), "its glint drawn");
		equal(0.5f, effects.suns.get(1)[0], 0, "at its strength");
		equal(GdxLayerEffects.GLINT_SUN_X, effects.suns.get(1)[1], 0, "in SWELL's sun");
		equal(GdxLayerEffects.GLINT_SUN_WIDTH, effects.suns.get(1)[2], 0, "and its width");
		equal(periodX, sea.getShaderPeriod(), 0, "its period across unmoved");
		equal(periodY, sea.getShaderPeriodY(), 0, "and up");
		equal(phase, sea.getShaderPhase(7.5), 0, "and its phase");
	}

	/** At OFF a SHADER layer of a fogged page is still fogged, through the plain shader the IMAGE layers are. */
	void detailOffKeepsThePageFog()
	{
		ParallaxPageReader reader = reader(true, false);
		DetailEffects effects = new DetailEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer front = atSpeed(1), sea = sea("sea");
		sea.setParallaxSpeedRatioX(0.25f);
		reader.addLayers(list(sea, front));
		reader.setFog(0.5f, Color.WHITE);
		reader.setDetail(Enum_Detail.OFF);
		reader.draw(camera, batch);
		equal(1, effects.layers.size(), "the sea alone begun: the front layer has no fog");
		equal(sea, effects.layers.get(0), "the sea");
		equal(null, effects.drawn.get(0), "through no effect");
		isTrue(!effects.glints.get(0), "nor its glint");
		equal(0, effects.suns.get(0)[0], 0, "its glint's strength handed as none");
		equal(fog(0.5f, 0.25f, 1), effects.fogs.get(0), 1e-5f, "at its fog");
	}

	/**
	 * A layer the game names is drawn from its own levels: shown from HIGH only, its effect from HIGH, its glint from
	 * HIGH, a sea's reflection kept at MEDIUM; the others by the default, which the game may change too.
	 */
	void aNamedLayerIsDrawnFromItsOwnLevels()
	{
		ParallaxPageReader only = reader(true, false);
		only.setLayerEffects(new DetailEffects());
		only.addLayers(list(layer(0)));
		draws.clear();
		only.draw(camera, batch);
		int alone = draws.size();

		ParallaxPageReader reader = reader(true, false);
		DetailEffects effects = new DetailEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer birds = layer(0.4f);
		birds.setName("birds");
		reader.addLayers(list(layer(0), birds, wave("plain"), wave("dull"), sea("sea")));
		reader.setDetail(Enum_Detail.MEDIUM);
		reader.setLayerDetail("birds", LayerDetail.DEFAULT.shownFrom(Enum_Detail.HIGH));
		reader.setLayerDetail("plain", LayerDetail.DEFAULT.effectFrom(Enum_Detail.HIGH));
		reader.setLayerDetail("dull", LayerDetail.DEFAULT.glintFrom(Enum_Detail.HIGH));
		reader.setLayerDetail("sea", LayerDetail.DEFAULT.mirrorFrom(Enum_Detail.MEDIUM));
		draws.clear();
		reader.draw(camera, batch);
		equal(2, effects.layers.size(), "dull and sea through an effect, plain drawn as its image");
		equal("dull", effects.layers.get(0).getName(), "dull");
		equal(Enum_ShaderEffect.WAVE, effects.drawn.get(0), "through its WAVE");
		isTrue(!effects.glints.get(0), "without its glint");
		equal(Enum_ShaderEffect.MIRROR, effects.drawn.get(1), "the sea keeps its reflection at MEDIUM");
		isTrue(effects.glints.get(1), "and its glint");
		equal(GdxLayerEffects.MIRROR_SUN_X, effects.suns.get(1)[1], 0, "in MIRROR's sun");

		int medium = draws.size();
		reader.setDetail(Enum_Detail.HIGH);
		draws.clear();
		reader.draw(camera, batch);
		equal(alone, draws.size() - medium, "the birds come at HIGH, as many tiles as a layer alone");

		effects.clear();
		reader.setLayerDetail("birds", null);
		reader.setLayerDefaultDetail(LayerDetail.DEFAULT.shownFrom(Enum_Detail.HIGH));
		reader.setDetail(Enum_Detail.MEDIUM);
		draws.clear();
		reader.draw(camera, batch);
		equal(2, effects.layers.size(), "the named layers by their own: dull and sea through an effect");
		equal(medium - alone, draws.size(), "the layers not named, birds now among them, hidden at MEDIUM by the default");
	}

	/**
	 * A layer the level hides still scrolls: shown again, it is where a layer always shown is. Both pages of a
	 * cross-fade follow the level.
	 */
	void aHiddenLayerScrollsOnAndBothPagesOfACrossFadeFollowTheLevel()
	{
		ParallaxPageReader reader = reader(true, false);
		DetailEffects effects = new DetailEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer hidden = atSpeed(0.5f), shown = atSpeed(0.5f);
		hidden.setName("hidden");
		reader.addLayers(list(hidden, shown, sea("sea")));
		reader.setLayerDetail("hidden", LayerDetail.DEFAULT.shownFrom(Enum_Detail.HIGH));
		reader.setDetail(Enum_Detail.OFF);
		for (int i = 0; i < 90; i++)
		{
			reader.act(1 / 60f, 100, 0);
			reader.draw(camera, batch);
		}
		equal(shown.getCurrentDistanceX(), hidden.getCurrentDistanceX(), 1e-4f, "scrolled as the layer shown");

		reader.addLayersTransfert(page(list(sea("sea"))), 1);
		reader.act(0.5f, 100, 0);
		effects.clear();
		reader.draw(camera, batch);
		equal(0, effects.layers.size(), "OFF: neither page's sea begun");
		reader.setDetail(Enum_Detail.MEDIUM);
		reader.draw(camera, batch);
		equal(2, effects.layers.size(), "MEDIUM: the outgoing and the incoming sea");
		equal(Enum_ShaderEffect.SWELL, effects.drawn.get(0), "the outgoing one through SWELL");
		equal(Enum_ShaderEffect.SWELL, effects.drawn.get(1), "the incoming one too");
	}
}
