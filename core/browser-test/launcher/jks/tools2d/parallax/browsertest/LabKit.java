package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;

/**
 * The labs' frame (r311), the half of the kit {@link LabControls} is not: the clip's clock, a live frame's step, the
 * canvas cut into panels, and the uniforms a lab's own shaders take. The window names {@link #exportClip} gives a lab
 * (fogLabAct, hazeLabAct, effectsLabAct and their Ready) are what tools/*-lab.sh call: never rename one.
 */
final class LabKit
{
	private LabKit()
	{}

	/** A lab a headless tool steps: {@code act(seconds)} moves it on by that much of the clip's clock. */
	interface Clip
	{
		void act(float seconds);
	}

	/** Gives the page {@code window[name + "Act"](seconds)}, which steps {@code lab}, then sets {@code name + "Ready"}. */
	static native void exportClip(String name, Clip lab) /*-{
		$wnd[name + "Act"] = $entry(function(seconds) {
			lab.@jks.tools2d.parallax.browsertest.LabKit.Clip::act(F)(seconds);
		});
		$wnd[name + "Ready"] = true;
	}-*/;

	/**
	 * How many steps of 1/60 s, the clip's clock, make {@code seconds} of a clip: a still or a clip steps the hearts by
	 * that, whatever the frame rate. The step is the literal {@code 1 / 60f}, never a static final float holding it: GWT
	 * steps the clock to another time through the field (r311: fog-lab --sets moved 4/255 in 4263 pixels).
	 */
	static int clipSteps(float seconds)
	{
		int steps = 0;
		for (float t = 0; t < seconds - 1e-4f; t += 1 / 60f)
			steps++;
		return steps;
	}

	/** Steps every heart by {@code seconds}, at 1/60 s: {@link #clipSteps}. */
	static void clip(float seconds, Parallax_Heart... hearts)
	{
		for (int n = clipSteps(seconds); n > 0; n--)
			act(1 / 60f, hearts);
	}

	static void act(float delta, Parallax_Heart... hearts)
	{
		for (Parallax_Heart heart : hearts)
			heart.act(delta);
	}

	/** A live frame's step: the frame's time, at most 1/15 s, so a stalled tab does not jump. */
	static float liveDelta()
	{return Math.min(Gdx.graphics.getDeltaTime(), 1 / 15f);}

	/** The whole canvas black, its viewport the whole back buffer. */
	static void clear()
	{
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
	}

	/** Clears the canvas and cuts it into panels: {@link #panel} draws into one, {@link #endPanels} ends. */
	static void beginPanels()
	{
		clear();
		Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
	}

	/**
	 * Draws into the panel {@code width} by {@code panelHeight} whose top-left is at {@code x}, {@code top} (GL's y
	 * goes up), through a frame {@code frameHeight} tall, its bottom {@code frameDrop} below the panel's.
	 */
	static void panel(int x, int top, int width, int panelHeight, int frameHeight, int frameDrop)
	{
		Gdx.gl.glScissor(x, top - panelHeight, width, panelHeight);
		Gdx.gl.glViewport(x, top - panelHeight - frameDrop, width, frameHeight);
	}

	/**
	 * Panel {@code i} of four, two by two: A, B on top, C, D below, a 4 px gap between them, under the canvas's
	 * {@code height}.
	 */
	static void quarter(int i, int height, int width, int panelHeight, int frameHeight, int frameDrop)
	{panel((i % 2) * (width + 4), i < 2 ? height : height - panelHeight - 4, width, panelHeight, frameHeight, frameDrop);}

	static void endPanels()
	{
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
	}

	/**
	 * A lab's own effect shader, set as GdxLayerEffects sets its own: {@code numbers} from
	 * {@link jks.tools2d.parallax.GdxLayerEffects#uniforms}, then the haze toward {@code fog}. Each by
	 * fetchUniformLocation(name, false): a uniform the GPU dropped (u_across in WAVE) is skipped, a by-name set throws
	 * (r290).
	 */
	static void setEffect(ShaderProgram program, float[] numbers, float haze, float fogR, float fogG, float fogB)
	{
		program.setUniformf(program.fetchUniformLocation("u_region", false), numbers[0], numbers[1], numbers[2], numbers[3]);
		program.setUniformf(program.fetchUniformLocation("u_size", false), numbers[4], numbers[5]);
		program.setUniformf(program.fetchUniformLocation("u_effect", false), numbers[6], numbers[7], numbers[8]);
		program.setUniformf(program.fetchUniformLocation("u_across", false), numbers[9], numbers[10], numbers[11]);
		program.setUniformf(program.fetchUniformLocation("u_up", false), numbers[12], numbers[13], numbers[14]);
		setHaze(program, haze, fogR, fogG, fogB);
	}

	/** A shader's haze toward the fog's colour: {@link #setEffect}'s end, and a lab's PLAIN shader's whole. */
	static void setHaze(ShaderProgram program, float haze, float fogR, float fogG, float fogB)
	{
		program.setUniformf(program.fetchUniformLocation("u_haze", false), haze);
		program.setUniformf(program.fetchUniformLocation("u_fog", false), fogR, fogG, fogB);
	}

	/**
	 * A lab's own {@link LayerEffects}: keeps the view the reader hands it, and turns a layer into the uniforms
	 * {@link #setEffect} takes.
	 */
	abstract static class Effects implements LayerEffects
	{
		private final float[] numbers = new float[15];
		private float viewWidth, viewHeight, viewFloor;

		@Override
		public void setView(float width, float height, float floor)
		{
			viewWidth = width;
			viewHeight = height;
			viewFloor = floor;
		}

		/** {@code layer}'s numbers at {@code phase}, in the view: {@link GdxLayerEffects#uniforms}. */
		float[] numbers(ParallaxLayer layer, float phase)
		{return GdxLayerEffects.uniforms(layer, phase, viewWidth, viewHeight, viewFloor, numbers);}
	}
}
