package jks.tools2d.parallax.demo;

import java.io.File;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;

import org.lwjgl.glfw.GLFW;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * Game-side usage of the library: the showcase pages (demo/showcase, r108), the ones graded best in demo/lab, loaded
 * from JSON and cross-faded on demand. A scene holds variants of one place (day and sunset, winter and spring): SPACE
 * fades between them, ENTER into the next scene. The build puts the pages and their atlases on the classpath, see
 * demo/build.gradle.
 */
public class ParallaxDemo extends ApplicationAdapter
{
	private static final float TRANSFER_SECONDS = 3;
	private static final float MANUAL_SPEED = 400;
	private static final Color NIGHT_TINT = new Color(0.45f, 0.5f, 0.85f, 1);

	/** Scene name, then its pages (demo/showcase/&lt;page&gt;.jplax). Round 2 of demo/lab may change who is here. */
	private static final String[][] SCENES = {
			{ "Calm", "calm-day", "calm-sunset" },
			{ "Seasons", "winter", "spring" },
			{ "One night", "one-night" },
			{ "Purple fairy", "purple-fairy" } };

	private Parallax_Heart heart;
	private WholePage_Model[][] pages;
	private int scene, variant;
	private boolean night;

	private SpriteBatch hudBatch;
	private BitmapFont font;

	/** --shots DIR: presses the keys itself and writes stills instead of opening for play, see tools/demo-shots.sh. */
	private final File shotsDir;

	public ParallaxDemo(File shotsDir)
	{this.shotsDir = shotsDir;}

	/**
	 * Keeps the window on X11 (Xwayland on a Wayland desktop), as before LWJGL 3.3.6: its GLFW 3.4 opens a native
	 * Wayland window whenever WAYLAND_DISPLAY is set, which libGDX 1.14 cannot place and cage draws shifted (r163).
	 * Called before new Lwjgl3Application, whose glfwInit reads the hint. Without an X display GLFW picks for itself.
	 */
	static void keepX11()
	{
		if (System.getenv("DISPLAY") != null && GLFW.glfwPlatformSupported(GLFW.GLFW_PLATFORM_X11))
			GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
	}

	public static void main(String[] args)
	{
		File shots = args.length == 2 && "--shots".equals(args[0]) ? new File(args[1]) : null;
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Parallax demo");
		config.setWindowIcon("parallaxIcon.png");
		config.setWindowedMode(1280, 720);
		config.useVsync(true);
		keepX11();
		new Lwjgl3Application(new ParallaxDemo(shots), config);
	}

	@Override
	public void create()
	{
		pages = new WholePage_Model[SCENES.length][];
		for (int s = 0; s < SCENES.length; s++)
		{
			pages[s] = new WholePage_Model[SCENES[s].length - 1];
			for (int v = 0; v < pages[s].length; v++)
				pages[s][v] = Utils_Page_Json.loadPage("showcase/" + SCENES[s][v + 1] + ".jplax");
		}

		heart = new Parallax_Heart();
		heart.setPage(pages[0][0]);
		heart.screenSpeedConstantX = 60;

		hudBatch = new SpriteBatch();
		font = new BitmapFont();

		if (shotsDir != null)
		{
			takeShots();
			Gdx.app.exit();
			return;
		}

		Gdx.input.setInputProcessor(new InputAdapter()
		{
			@Override
			public boolean keyDown(int keycode)
			{return press(keycode);}
		});
	}

	private boolean press(int keycode)
	{
		switch (keycode)
		{
			case Keys.SPACE:
				if (pages[scene].length == 1)
					return true;
				variant = (variant + 1) % pages[scene].length;
				heart.transfertIntoPage(pages[scene][variant], TRANSFER_SECONDS);
				return true;
			case Keys.ENTER:
				scene = (scene + 1) % pages.length;
				variant = 0;
				heart.transfertIntoPage(pages[scene][variant], TRANSFER_SECONDS);
				return true;
			case Keys.N:
				night = !night;
				heart.parallaxReader.addColorTransfert(night ? NIGHT_TINT : Color.WHITE, TRANSFER_SECONDS);
				return true;
			case Keys.R:
				heart.parallaxReader.resetPositions();
				return true;
			default:
				return false;
		}
	}

	@Override
	public void render()
	{
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);

		if (Gdx.input.isKeyPressed(Keys.LEFT))
			heart.screenSpeedConsumableX = -MANUAL_SPEED;
		if (Gdx.input.isKeyPressed(Keys.RIGHT))
			heart.screenSpeedConsumableX = MANUAL_SPEED;

		heart.act(delta);
		draw();
	}

	private void draw()
	{
		ScreenUtils.clear(Color.BLACK);
		heart.render();

		String[] names = SCENES[scene];
		String where = names[0] + (pages[scene].length > 1 ? " (" + names[variant + 1] + ")" : "");
		String hud = where + "   SPACE: " + (pages[scene].length > 1 ? "variant" : "-") + "   ENTER: next scene   N: night tint   LEFT/RIGHT: scroll   R: reset   "
				+ Gdx.graphics.getFramesPerSecond() + " fps";
		hudBatch.begin();
		// A shadow under the text: some skies are as light as the font.
		font.setColor(Color.BLACK);
		font.draw(hudBatch, hud, 11, Gdx.graphics.getHeight() - 11);
		font.setColor(Color.WHITE);
		font.draw(hudBatch, hud, 10, Gdx.graphics.getHeight() - 10);
		hudBatch.end();
	}

	/** Plays the keys at fixed steps of 1/60 s and writes a still at each mark: what a player sees, HUD included. */
	private void takeShots()
	{
		shotsDir.mkdirs();
		// Seconds into the run, key pressed then (0 = none), still name.
		Object[][] script = {
				{ 5f, 0, "calm-day" },
				{ 5f, Keys.SPACE, "" }, { 6.5f, 0, "calm-fading" }, { 9f, 0, "calm-sunset" },
				{ 9f, Keys.ENTER, "" }, { 13f, 0, "winter" },
				{ 13f, Keys.SPACE, "" }, { 17f, 0, "spring" },
				{ 17f, Keys.ENTER, "" }, { 21f, 0, "one-night" },
				{ 21f, Keys.N, "" }, { 25f, 0, "one-night-tinted" },
				{ 25f, Keys.N, "" }, { 25f, Keys.ENTER, "" }, { 29f, 0, "purple-fairy" } };
		float step = 1 / 60f, time = 0;
		for (Object[] line : script)
		{
			for (; time < (float) line[0]; time += step)
				heart.act(step);
			if ((int) line[1] != 0)
				press((int) line[1]);
			else
			{
				draw();
				Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
				PixmapIO.writePNG(new FileHandle(new File(shotsDir, line[2] + ".png")), pixmap, 6, true);
				pixmap.dispose();
			}
		}
	}

	@Override
	public void resize(int width, int height)
	{
		heart.resize(width, height);
		hudBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
	}

	@Override
	public void dispose()
	{
		heart.dispose();
		hudBatch.dispose();
		font.dispose();
		// Every atlas was loaded as an internal file, so the shared AssetManager owns them.
		Gvars_Parallax.getManager().dispose();
	}
}
