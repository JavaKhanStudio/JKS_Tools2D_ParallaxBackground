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
 * canvas cut into panels (one zoomed to the whole stage, r452: lab.js), and the uniforms a lab's own shaders take. The
 * window names {@link #exportClip} gives a lab (fogLabAct, hazeLabAct, effectsLabAct and their Ready) are what
 * tools/*-lab.sh call: never rename one.
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

	/**
	 * The panel zoomed (r452), -1 when every panel shows: lab.js sets it when a panel is clicked or tapped, and makes the
	 * canvas the panel's shape (as tall as the window allows); {@link #frame} draws that panel over the whole canvas.
	 */
	private static int focus = -1;
	/** The panels {@link #frame} placed this frame: {@link #endPanels} hands their rectangles to lab.js. */
	private static int placed;
	private static boolean exported;

	/** Clears the canvas and cuts it into panels: {@link #panel} draws into one, {@link #endPanels} ends. */
	static void beginPanels()
	{
		clear();
		Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
		if (!exported)
			exportResize();
		exported = true;
		focus = focusAsked();
		placed = 0;
	}

	/**
	 * Draws into the panel {@code width} by {@code panelHeight} whose top-left is at {@code x}, {@code top} (GL's y
	 * goes up), through a frame {@code frameHeight} tall, its bottom {@code frameDrop} below the panel's. False when the
	 * panel is not shown (another is zoomed): skip its drawing.
	 */
	static boolean panel(int x, int top, int width, int panelHeight, int frameHeight, int frameDrop)
	{return frame(x, top - panelHeight, width, panelHeight, x, top - panelHeight - frameDrop, width, frameHeight);}

	/**
	 * The next panel: drawn into the scissor {@code sx, sy, sw, sh} through the viewport {@code vx, vy, vw, vh}, in the
	 * canvas's pixels, GL's y up; every lab places its panels through here, in the order of their titles. When a panel
	 * is zoomed, that one is drawn over the whole canvas, scaled about its middle so all of it shows, and every other
	 * one is cut to nothing: false, skip its drawing.
	 */
	static boolean frame(int sx, int sy, int sw, int sh, int vx, int vy, int vw, int vh)
	{
		int i = placed++;
		publish(i, sx, sy, sw, sh);
		if (focus < 0)
		{
			Gdx.gl.glScissor(sx, sy, sw, sh);
			Gdx.gl.glViewport(vx, vy, vw, vh);
			return true;
		}
		if (i != focus)
		{
			Gdx.gl.glScissor(0, 0, 0, 0);
			return false;
		}
		int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
		float scale = Math.min(width / (float) sw, height / (float) sh);
		float dx = width / 2f - (sx + sw / 2f) * scale, dy = height / 2f - (sy + sh / 2f) * scale;
		Gdx.gl.glScissor(Math.round(sx * scale + dx), Math.round(sy * scale + dy), Math.round(sw * scale), Math.round(sh * scale));
		Gdx.gl.glViewport(Math.round(vx * scale + dx), Math.round(vy * scale + dy), Math.round(vw * scale), Math.round(vh * scale));
		return true;
	}

	private static native int focusAsked() /*-{
		return typeof $wnd.labFocus === 'number' ? $wnd.labFocus : -1;
	}-*/;

	private static native void publish(int i, int x, int y, int width, int height) /*-{
		($wnd.labPanelRects = $wnd.labPanelRects || [])[i] = [x, y, width, height];
	}-*/;

	/** Tells lab.js how many panels this frame placed, and the canvas they were placed in. */
	private static native void published(int count, int width, int height) /*-{
		if ($wnd.labPanelsPlaced)
			$wnd.labPanelsPlaced(count, width, height);
	}-*/;

	/** Gives lab.js {@code window.labResize(width, height)}: the canvas that size, from the next frame (the zoom's). */
	static native void exportResize() /*-{
		$wnd.labResize = $entry(function(width, height) {
			@com.badlogic.gdx.Gdx::graphics.@com.badlogic.gdx.Graphics::setWindowedMode(II)(width, height);
		});
	}-*/;

	/**
	 * Panel {@code i} of four, two by two: A, B on top, C, D below, a 4 px gap between them, under the canvas's
	 * {@code height}.
	 */
	static boolean quarter(int i, int height, int width, int panelHeight, int frameHeight, int frameDrop)
	{return panel((i % 2) * (width + 4), i < 2 ? height : height - panelHeight - 4, width, panelHeight, frameHeight, frameDrop);}

	static void endPanels()
	{
		published(placed, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
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
