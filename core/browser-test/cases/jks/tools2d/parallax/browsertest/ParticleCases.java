package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;
import static jks.tools2d.parallax.browsertest.Check.notSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.utils.Array;

import jks.tools2d.parallax.ParallaxParticles;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;

/**
 * ParallaxPageReader's cases on PARTICLES layers: pinned to their layer or to the view, faded and tinted with their
 * page, warmed on their first frame, dollied. Shared builders in {@link ReaderFixtures}.
 */
final class ParticleCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: particlesArePinnedToTheirLayerInEveryRepeatMode", () -> new ParticleCases().particlesArePinnedToTheirLayerInEveryRepeatMode()),
				new BrowserCase("reader: viewAnchoredParticlesAreDrawnOnceAndDriftWithTheLayer", () -> new ParticleCases().viewAnchoredParticlesAreDrawnOnceAndDriftWithTheLayer()),
				new BrowserCase("reader: particlesPastTheirBoxAreDrawnWhenTheBoxIsNot", () -> new ParticleCases().particlesPastTheirBoxAreDrawnWhenTheBoxIsNot()),
				new BrowserCase("reader: particlesFadeAndTintWithTheirPage", () -> new ParticleCases().particlesFadeAndTintWithTheirPage()),
				new BrowserCase("reader: aParticleLayerWithoutAnEffectDrawsNothing", () -> new ParticleCases().aParticleLayerWithoutAnEffectDrawsNothing()),
				new BrowserCase("reader: aFreshParticleLayerIsAlreadyFallingOnItsFirstFrame", () -> new ParticleCases().aFreshParticleLayerIsAlreadyFallingOnItsFirstFrame()),
				new BrowserCase("reader: dollyScalesTheParticlesAboutTheViewsMiddle", () -> new ParticleCases().dollyScalesTheParticlesAboutTheViewsMiddle()));
	}

	/**
	 * One emitter holding one still particle, 1 world unit square, centered {@code offsetX} right of the effect's origin,
	 * alive 100 s: drawn, it shows where the reader put the origin.
	 */
	static ParallaxParticles dot(float offsetX)
	{
		ParticleEmitter emitter = new ParticleEmitter();
		emitter.setMaxParticleCount(1);
		emitter.setMinParticleCount(1);
		emitter.setContinuous(true);
		emitter.getDuration().setLow(1000);
		emitter.getLife().setHigh(100000);
		emitter.getXScale().setHigh(1);
		emitter.getTransparency().setHigh(1);
		if (offsetX != 0)
		{
			emitter.getXOffsetValue().setActive(true);
			emitter.getXOffsetValue().setLow(offsetX);
		}
		emitter.setAdditive(false);
		emitter.setSprites(new Array<Sprite>(new Sprite[] { new Sprite(strippedRegion(10, 10, 10, 10, 0, 0)) }));
		ParticleEffect effect = new ParticleEffect();
		effect.getEmitters().add(emitter);
		return new ParallaxParticles(effect);
	}

	/** The particles' draws, recognized by their 1-unit width: a browser's floats are doubles, compare loosely. */
	private List<float[]> dots()
	{
		List<float[]> dots = new ArrayList<>();
		for (float[] draw : draws)
			if (Math.abs(draw[2] - 1) < 1e-3f && Math.abs(draw[3] - 1) < 1e-3f)
				dots.add(draw);
		return dots;
	}

	/**
	 * A PARTICLES layer pinned to its layer draws its effect at the corner of every tile the view shows, in its place in
	 * the draw order: the image layers around it are 40 wide, the particles 1.
	 */
	void particlesArePinnedToTheirLayerInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";
			draws.clear();
			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			ParallaxLayer snow = ParallaxLayer.particles(dot(0), Enum_ParticleAnchor.LAYER, 0.3f); // 12 x 6.75, a step of 13 x 7.75
			snow.setPadX(1);
			snow.setPadY(1);
			snow.setParallaxSpeedRatioX(0.02f);
			snow.setParallaxSpeedRatioY(0.02f);
			snow.setDecalPercentX(25); // its corner at 10, 5.625: inside the view when nothing repeats
			snow.setDecalPercentY(25);
			reader.addLayers(list(layer(0), snow, layer(0)));
			reader.act(1 / 60f, 500, 300);
			reader.draw(camera, batch);

			List<float[]> dots = dots();
			isTrue(dots.size() > 0, name + ": the particles are drawn");
			isTrue(draws.indexOf(dots.get(0)) > 0, name + ": after the layer behind them");
			isTrue(draws.indexOf(dots.get(dots.size() - 1)) < draws.size() - 1, name + ": and before the layer in front of them");
			float cornerX = snow.getCurrentDistanceX(), cornerY = snow.getCurrentDistanceY();
			for (float[] dot : dots)
			{
				float x = dot[0] + 0.5f - cornerX, y = dot[1] + 0.5f - cornerY;
				float stepsX = x / 13, stepsY = y / 7.75f;
				equal(Math.round(stepsX), stepsX, 1e-3f, name + ": a particle on a tile's corner, x " + dot[0]);
				equal(Math.round(stepsY), stepsY, 1e-3f, name + ": a particle on a tile's corner, y " + dot[1]);
				if (!onX)
					equal(0, Math.round(stepsX), name + ": not repeated on X");
				if (!onY)
					equal(0, Math.round(stepsY), name + ": not repeated on Y");
				isTrue(dot[0] + 1 > 0 && dot[0] < 40 && dot[1] + 1 > 0 && dot[1] < 22.5f, name + ": off-screen particle at " + dot[0] + ", " + dot[1]);
			}
			// The corners in the view: 3 or 4 across 40 at a step of 13, 3 or 4 up 22.5 at 7.75.
			int across = onX ? 3 : 1, down = onY ? 3 : 1;
			isTrue(dots.size() >= across * down && dots.size() <= (onX ? 4 : 1) * (onY ? 4 : 1), name + ": " + dots.size() + " particles drawn");
		}
	}

	/**
	 * Anchored to the view, the effect is emitted from the layer's decal in the view, and drawn once whatever the page
	 * repeats on; the particles it emitted then move with the layer's scroll, and the emitter does not.
	 */
	void viewAnchoredParticlesAreDrawnOnceAndDriftWithTheLayer()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";
			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			ParallaxParticles effect = dot(0);
			ParallaxLayer rain = ParallaxLayer.particles(effect, Enum_ParticleAnchor.VIEW, 0.3f);
			rain.setParallaxSpeedRatioX(0.1f);
			rain.setParallaxSpeedRatioY(0.1f);
			rain.setDecalPercentX(50);
			rain.setDecalPercentY(50);
			reader.addLayers(list(rain));

			reader.act(1 / 60f, 0, 0); // emits the particle at the emitter
			draws.clear();
			reader.draw(camera, batch);
			equal(1, dots().size(), name + ": drawn once");
			equal(20, dots().get(0)[0] + 0.5f, 1e-4f, name + ": at the decal across the view");
			equal(11.25f, dots().get(0)[1] + 0.5f, 1e-4f, name + ": and up it");

			reader.act(1, 60, 30); // the layer moves 6 left and 3 down
			draws.clear();
			reader.draw(camera, batch);
			equal(1, dots().size(), name + ": still drawn once");
			equal(14, dots().get(0)[0] + 0.5f, 1e-3f, name + ": the particle drifted with the layer");
			equal(8.25f, dots().get(0)[1] + 0.5f, 1e-3f, name + ": both ways");
			equal(0, effect.getEmitters().get(0).getX(), 0, name + ": the emitter stayed in the view");
		}
	}

	/**
	 * Dollied (r278) at zoom 0.5, a VIEW layer at half the front's speed is drawn at zoom 0.75: its particle, emitted at
	 * the view's middle, stays there, 2/3 of a unit wide; drifted 6 left and 3 down with the layer, it is 4 and 2 from
	 * the middle in the camera's world units, two thirds of the way.
	 */
	void dollyScalesTheParticlesAboutTheViewsMiddle()
	{
		ParallaxPageReader reader = reader(true, false);
		reader.setWorldSize(40, 22.5f);
		camera.zoom = 0.5f;
		reader.setDolly(true);
		ParallaxLayer rain = ParallaxLayer.particles(dot(0), Enum_ParticleAnchor.VIEW, 0.3f);
		rain.setParallaxSpeedRatioX(0.1f);
		rain.setParallaxSpeedRatioY(0.1f);
		rain.setDecalPercentX(50);
		rain.setDecalPercentY(50);
		ParallaxLayer front = layer(0);
		front.setParallaxSpeedRatioX(0.2f);
		reader.addLayers(list(rain, front));

		reader.act(1 / 60f, 0, 0);
		draws.clear();
		reader.draw(camera, batch);
		float[] dot = draws.get(0);
		equal(2 / 3f, dot[2], 1e-4f, "two thirds of a unit wide");
		equal(20, dot[0] + dot[2] / 2, 1e-4f, "at the view's middle across");
		equal(11.25f, dot[1] + dot[3] / 2, 1e-4f, "and up");

		reader.act(1, 60, 30);
		draws.clear();
		reader.draw(camera, batch);
		dot = draws.get(0);
		equal(16, dot[0] + dot[2] / 2, 1e-3f, "drifted 6 left in its view, 4 in the camera's");
		equal(9.25f, dot[1] + dot[3] / 2, 1e-3f, "3 down, 2");
	}

	/** Particles reach past their box: a box out of the view still draws the particles that are in it. */
	void particlesPastTheirBoxAreDrawnWhenTheBoxIsNot()
	{
		ParallaxPageReader reader = reader(false, false);
		reader.setWorldSize(40, 22.5f);
		ParallaxLayer spray = ParallaxLayer.particles(dot(13), Enum_ParticleAnchor.LAYER, 0.3f); // 12 wide, a particle 13 right of it
		spray.setDecalPercentX(-31.25f); // the box from -12.5 to -0.5, the particle around 0.5
		reader.addLayers(list(spray));
		reader.act(1 / 60f, 0, 0);
		reader.draw(camera, batch);

		equal(1, dots().size(), "the particle in the view is drawn");
		equal(0.5f, dots().get(0)[0] + 0.5f, 1e-4f, "at its place");
	}

	/** The particles take their page's tint and its opacity during a cross-fade, as an image layer does. */
	void particlesFadeAndTintWithTheirPage()
	{
		ParallaxPageReader reader = reader(false, false);
		reader.setWorldSize(40, 22.5f);
		ParallaxLayer snow = ParallaxLayer.particles(dot(0), Enum_ParticleAnchor.LAYER, 0.3f);
		snow.setDecalPercentX(50);
		snow.setDecalPercentY(50);
		List<ParallaxLayer> page = list(snow);
		reader.addLayers(page);
		reader.addLayersTransfert(page(page), 1); // into itself: the incoming copy has an effect of its own
		reader.addColorTransfert(new Color(0.5f, 1, 1, 1), 0);
		reader.act(0.5f, 0, 0);
		reader.draw(camera, batch);

		notSame(snow.getParticles(), reader.transferLayers.get(0).getParticles(), "the copy's effect");
		equal(2, dots().size(), "both pages' particles mid-fade");
		for (float[] dot : dots())
		{
			// A packed color loses alpha's lowest bit (NumberUtils.intToFloatColor): 127 comes back 126.
			equal(0.5f, dot[5], 2 / 255f, "at half opacity");
			equal(0.5f, dot[4], 1 / 255f, "tinted");
		}
	}

	void aParticleLayerWithoutAnEffectDrawsNothing()
	{
		ParallaxPageReader reader = reader(true, true);
		reader.addLayers(list(ParallaxLayer.particles(null, Enum_ParticleAnchor.LAYER, 0.3f), ParallaxLayer.particles(null, Enum_ParticleAnchor.VIEW, 1)));
		reader.act(1 / 60f, 100, 100);
		reader.draw(camera, batch);
		equal(0, draws.size(), "draws");
	}

	/**
	 * A layer built and drawn on its first frame shows its effect already running: 20 particles a second, each living
	 * 3 s, rising 1 unit a second from a 12-wide line, is about 60 particles spread 3 units up, not the one or none
	 * emitted in a frame. Its cross-fade copy is warmed as well.
	 */
	void aFreshParticleLayerIsAlreadyFallingOnItsFirstFrame()
	{
		ParticleEmitter emitter = new ParticleEmitter();
		emitter.setMaxParticleCount(60);
		emitter.setContinuous(true);
		emitter.getDuration().setLow(1000);
		emitter.getEmission().setHigh(20);
		emitter.getLife().setHigh(3000);
		emitter.getSpawnShape().setShape(ParticleEmitter.SpawnShape.line);
		emitter.getSpawnWidth().setHigh(12);
		emitter.getXScale().setHigh(1);
		emitter.getVelocity().setActive(true);
		emitter.getVelocity().setHigh(1);
		emitter.getAngle().setActive(true);
		emitter.getAngle().setHigh(90);
		emitter.getTransparency().setHigh(1);
		emitter.setAdditive(false);
		emitter.setSprites(new Array<Sprite>(new Sprite[] { new Sprite(strippedRegion(10, 10, 10, 10, 0, 0)) }));
		ParticleEffect effect = new ParticleEffect();
		effect.getEmitters().add(emitter);

		ParallaxPageReader reader = reader(false, false);
		reader.setWorldSize(40, 22.5f);
		ParallaxLayer rising = ParallaxLayer.particles(new ParallaxParticles(effect), Enum_ParticleAnchor.LAYER, 0.3f);
		rising.setDecalPercentX(25);
		rising.setDecalPercentY(25);
		List<ParallaxLayer> page = list(rising);
		reader.addLayers(page);
		reader.act(1 / 60f, 0, 0);
		reader.draw(camera, batch);

		List<float[]> dots = dots();
		isTrue(dots.size() >= 50, dots.size() + " particles out on the first frame, not about 60");
		float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
		for (float[] dot : dots)
		{
			lowest = Math.min(lowest, dot[1]);
			highest = Math.max(highest, dot[1]);
		}
		isTrue(highest - lowest > 2.5f, "the particles are spread " + (highest - lowest) + " up, not all just emitted");

		reader.addLayersTransfert(page(page), 1); // the incoming copy builds an effect of its own
		isTrue(!reader.transferLayers.get(0).getParticles().isEmpty(), "the cross-fade copy's particles are out too");
	}
}
