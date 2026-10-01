package jks.tools2d.parallax;

import java.io.BufferedReader;
import java.io.IOException;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;

/**
 * A PARTICLES layer's libGDX {@link ParticleEffect} ({@link jks.tools2d.parallax.pages.Enum_LayerKind#PARTICLES}). Its
 * emitters stay at (0, 0) and its particles live in the layer's own space: {@link #draw} moves them to where the layer
 * is drawn, once per tile, and multiplies the page's tint and fade into each one, as the batch color does an image.
 * <p>
 * Allocates nothing per frame: {@link #allocate()} builds every particle an emitter can hold when the page loads, which
 * libGDX would otherwise do the first time each one is emitted. A finished effect starts again: a background loops.
 * {@link #warmUp()} runs it on until it is already falling when its page is first drawn.
 */
public class ParallaxParticles extends ParticleEffect
{
	private static final int VERTICES = 20;
	/** {@link #warmUp()} runs the effect on in steps this long, and never longer than {@link #MAX_WARM_UP} seconds. */
	static final float WARM_UP_STEP = 1 / 30f, MAX_WARM_UP = 30;

	/** Where the particles are drawn from, and the color multiplied into them; set by {@link #draw}. */
	private float offsetX, offsetY;
	private float tintR = 1, tintG = 1, tintB = 1, tintA = 1;
	private final float[] vertices = new float[VERTICES];

	/** The live particles' box, in the effect's own space, after the last {@link #update}; empty when none live. */
	private float minX, minY, maxX, maxY;
	private boolean empty = true;

	public ParallaxParticles()
	{}

	/** A copy of {@code effect}'s emitters, sprites and settings, with no particle alive: any effect a game built. */
	public ParallaxParticles(ParticleEffect effect)
	{super(effect);}

	@Override
	protected ParticleEmitter newEmitter(BufferedReader reader) throws IOException
	{return new Emitter(reader);}

	@Override
	protected ParticleEmitter newEmitter(ParticleEmitter emitter)
	{return new Emitter(emitter);}

	/** Builds every particle the emitters can hold, so that emitting one allocates nothing. Call once loaded. */
	public void allocate()
	{
		Array<ParticleEmitter> emitters = getEmitters();
		for (int i = 0, n = emitters.size; i < n; i++)
			if (emitters.get(i) instanceof Emitter)
				((Emitter) emitters.get(i)).allocate();
		// Its box is built on first use.
		getBoundingBox();
	}

	/**
	 * Runs the effect on, in steps of {@link #WARM_UP_STEP}, for as long as its slowest emitter takes to start and its
	 * longest-lived particle lives ({@link #warmUpSeconds()}): a page shows its snow already falling, not falling in, as a
	 * Godot scene's {@code preprocess} does. Load time only: a page's effects are warmed when it is built.
	 */
	public void warmUp()
	{
		for (float left = warmUpSeconds(); left > 0; left -= WARM_UP_STEP)
			update(Math.min(WARM_UP_STEP, left));
	}

	/** How long {@link #warmUp()} runs the effect: its longest delay and particle life, at most {@link #MAX_WARM_UP}. */
	public float warmUpSeconds()
	{
		float longest = 0;
		Array<ParticleEmitter> emitters = getEmitters();
		for (int i = 0, n = emitters.size; i < n; i++)
		{
			ParticleEmitter emitter = emitters.get(i);
			float delay = emitter.getDelay().isActive() ? emitter.getDelay().getLowMax() : 0;
			ParticleEmitter.ScaledNumericValue life = emitter.getLife();
			float lowest = Math.max(life.getLowMin(), life.getLowMax());
			float highest = Math.max(life.getHighMin(), life.getHighMax()) + (life.isRelative() ? lowest : 0);
			longest = Math.max(longest, (delay + Math.max(lowest, highest)) / 1000);
		}
		return Math.min(longest, MAX_WARM_UP);
	}

	/** Runs the effect {@code delta} seconds on, starting it again once finished, and measures where its particles are. */
	@Override
	public void update(float delta)
	{
		super.update(delta);
		if (isComplete())
			reset(false);

		BoundingBox box = getBoundingBox();
		empty = !box.isValid();
		if (!empty)
		{
			minX = box.min.x;
			minY = box.min.y;
			maxX = box.max.x;
			maxY = box.max.y;
		}
	}

	/** Moves every live particle, not the emitters: a VIEW-anchored effect's particles drift as its layer scrolls. */
	public void translateParticles(float x, float y)
	{
		if (x == 0 && y == 0)
			return;
		Array<ParticleEmitter> emitters = getEmitters();
		for (int i = 0, n = emitters.size; i < n; i++)
		{
			ParticleEmitter emitter = emitters.get(i);
			// An attached emitter carries its live particles when it moves; detached, it moves back alone.
			boolean attached = emitter.isAttached();
			emitter.setAttached(true);
			emitter.setPosition(emitter.getX() + x, emitter.getY() + y);
			emitter.setAttached(false);
			emitter.setPosition(emitter.getX() - x, emitter.getY() - y);
			emitter.setAttached(attached);
		}
	}

	/**
	 * Draws the particles moved by {@code (x, y)}, their colors multiplied by {@code tint}. The emitters set the batch's
	 * blend function as the .p asks.
	 */
	public void draw(Batch batch, float x, float y, Color tint)
	{
		offsetX = x;
		offsetY = y;
		tintR = tint.r;
		tintG = tint.g;
		tintB = tint.b;
		tintA = tint.a;
		draw(batch);
	}

	/** True when no particle is alive. */
	public boolean isEmpty()
	{return empty;}

	/** Left edge of the live particles, in the effect's own space: 0 is the layer's left. */
	public float getMinX()
	{return minX;}

	public float getMinY()
	{return minY;}

	public float getMaxX()
	{return maxX;}

	public float getMaxY()
	{return maxY;}

	private final class Emitter extends ParticleEmitter
	{
		Emitter(BufferedReader reader) throws IOException
		{super(reader);}

		Emitter(ParticleEmitter emitter)
		{super(emitter);}

		@Override
		protected Particle newParticle(Sprite sprite)
		{return new Tinted(sprite, this);}

		void allocate()
		{
			Particle[] particles = getParticles();
			if (getSprites() == null || getSprites().isEmpty())
				return;
			for (int i = 0; i < particles.length; i++)
				if (particles[i] == null)
					particles[i] = newParticle(getSprites().first());
		}
	}

	private final class Tinted extends ParticleEmitter.Particle
	{
		private final ParticleEmitter emitter;

		Tinted(Sprite sprite, ParticleEmitter emitter)
		{
			super(sprite);
			this.emitter = emitter;
			// The emitter builds these on a particle's first emission.
			tint = new float[3];
			getBoundingRectangle();
		}

		@Override
		public void draw(Batch batch)
		{
			float[] own = getVertices();
			float[] moved = vertices;
			Color color = getColor();
			// Premultiplied, the color already holds its alpha: the fade scales every channel.
			float fade = emitter.isPremultipliedAlpha() ? tintA : 1;
			float packed = Color.toFloatBits(color.r * tintR * fade, color.g * tintG * fade, color.b * tintB * fade, color.a * tintA);
			for (int i = 0; i < VERTICES; i += 5)
			{
				moved[i] = own[i] + offsetX;
				moved[i + 1] = own[i + 1] + offsetY;
				moved[i + 2] = packed;
				moved[i + 3] = own[i + 3];
				moved[i + 4] = own[i + 4];
			}
			batch.draw(getTexture(), moved, 0, VERTICES);
		}
	}
}
