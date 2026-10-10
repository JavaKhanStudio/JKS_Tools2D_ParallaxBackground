package jks.tools2d.parallax.shots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;

import org.lwjgl.glfw.GLFW;

import jks.tools2d.parallax.TransfertStyle;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The reference renderer (r130): stills of every scene of a round, drawn by the libGDX runtime, the frames the Godot and
 * jME readers are compared with. The shots mode of the demo's grading lab (ParallaxLab, r73), split out so that the
 * library checks its readers without the demo. tools/parallax-lab-shots.sh runs it off screen:
 *
 * <pre>
 * java -cp "shots/build/install/shots/lib/*" jks.tools2d.parallax.shots.ParallaxShots ROUND_DIR OUT_DIR
 * </pre>
 *
 * A round is a folder holding round.json: the scenes, each a page file and the folder of its atlas, both relative to
 * the working directory (the repository root). Each scene scrolls at 60 units/s stepped at 1/60 s and is saved
 * {@code <id>-t0/-t6/-t12.png}, 0, 6 and 12 s in, 1280x720. A scene may also:
 * {@code "transfer": {"at": 4, "seconds": 4, "page": ..., "atlasDir": ...}} cross-fade into that page (no page: into the
 * page on screen; a list of them: one after the other, r98; {@code "style": {"kind": "DEPTH_STAGGER", "stagger": 1}}: drawn
 * in that {@link TransfertStyle}, r249), {@code "tint": {"at": 4, "seconds": 4, "color": [r, g, b,
 * a]}} tint, both started {@code at} seconds into the scroll (r94, engines/godot/tests/transfer); {@code "speedY": 30}
 * also scroll up, and {@code "resize": {"at": 3, "size": [720, 1280]}} resize the window mid-scroll (r95,
 * engines/godot/tests/conformance); {@code "hooks": {"slot": {"region": "parallax4", "position": 1}}} draws the EMPTY
 * layer named slot with that atlas region stretched over each tile (r177, engines/godot/tests/effects);
 * {@code "sequenceSeed": 1234} draws the SEQUENCE layers from that game seed (r183, engines/godot/tests/sequence).
 * engines/godot/tests/shots.gd and engines/jme's JmeParallaxShots step the same way.
 */
public class ParallaxShots extends ApplicationAdapter
{
	private static final float SPEED = 60;
	private static final float[] SHOT_TIMES = { 0, 6, 12 };

	private final Path roundDir;
	private final Path shotsDir;
	private final List<Scene> scenes = new ArrayList<>();

	private Parallax_Heart heart;
	/** The layer names the scene on screen hooked. */
	private final List<String> hooked = new ArrayList<>();

	private static final class Scene
	{
		String id, page, atlasDir;
		final List<Transfer> transfers = new ArrayList<>();
		final List<Hook> hooks = new ArrayList<>();
		float tintAt = -1, tintSeconds, speedY, resizeAt = -1;
		int resizeWidth, resizeHeight;
		Color tint;
		/** The game's seed for the SEQUENCE layers; null: the pages' own. */
		Integer sequenceSeed;
	}

	/** An EMPTY layer's hook from a round: stretches the n-th atlas region of that name over every tile (r177). */
	private static final class Hook
	{
		String layer, region;
		int position;
	}

	/** A cross-fade {@code at} seconds into the scroll; {@code page} null: into the page on screen. */
	private static final class Transfer
	{
		float at, seconds;
		String page, atlasDir;
		TransfertStyle style = TransfertStyle.FADE;
	}

	public ParallaxShots(Path roundDir, Path shotsDir)
	{
		this.roundDir = roundDir;
		this.shotsDir = shotsDir;
	}

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
		if (args.length != 2)
		{
			System.err.println("usage: ParallaxShots ROUND_DIR OUT_DIR");
			System.exit(2);
		}
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Parallax shots - " + Paths.get(args[0]).getFileName());
		config.setWindowedMode(1280, 720);
		config.useVsync(true);
		keepX11();
		new Lwjgl3Application(new ParallaxShots(Paths.get(args[0]), Paths.get(args[1])), config);
	}

	@Override
	public void create()
	{
		JsonValue round = new JsonReader().parse(new FileHandle(roundDir.resolve("round.json").toFile()));
		for (JsonValue s = round.get("scenes").child; s != null; s = s.next)
		{
			Scene scene = new Scene();
			scene.id = s.getString("id");
			scene.page = s.getString("page");
			scene.atlasDir = s.getString("atlasDir");
			scene.speedY = s.getFloat("speedY", 0);
			if (s.has("sequenceSeed"))
				scene.sequenceSeed = s.getInt("sequenceSeed");
			JsonValue resize = s.get("resize");
			if (resize != null)
			{
				scene.resizeAt = resize.getFloat("at");
				scene.resizeWidth = resize.get("size").getInt(0);
				scene.resizeHeight = resize.get("size").getInt(1);
			}
			JsonValue transfers = s.get("transfer");
			if (transfers != null)
				for (JsonValue t = transfers.isArray() ? transfers.child : transfers; t != null; t = transfers.isArray() ? t.next : null)
				{
					Transfer transfer = new Transfer();
					transfer.at = t.getFloat("at");
					transfer.seconds = t.getFloat("seconds");
					transfer.page = t.getString("page", null);
					transfer.atlasDir = t.getString("atlasDir", scene.atlasDir);
					transfer.style = style(t.get("style"));
					scene.transfers.add(transfer);
				}
			JsonValue hooks = s.get("hooks");
			if (hooks != null)
				for (JsonValue h = hooks.child; h != null; h = h.next)
				{
					Hook hook = new Hook();
					hook.layer = h.name;
					hook.region = h.getString("region");
					hook.position = h.getInt("position", 0);
					scene.hooks.add(hook);
				}
			JsonValue tint = s.get("tint");
			if (tint != null)
			{
				scene.tintAt = tint.getFloat("at");
				scene.tintSeconds = tint.getFloat("seconds");
				float[] c = tint.get("color").asFloatArray();
				scene.tint = new Color(c[0], c[1], c[2], c[3]);
			}
			scenes.add(scene);
		}

		heart = new Parallax_Heart();
		takeShots();
		Gdx.app.exit();
	}

	/** Shows {@code scene} from its start. */
	private void show(Scene scene)
	{
		WholePage_Model page = Utils_Page_Json.loadPage(new FileHandle(Paths.get(scene.page).toFile()));
		heart.relativePath = Paths.get(scene.atlasDir).toAbsolutePath().toString();
		heart.setPage(page);
		heart.parallaxReader.resetPositions();
		for (String name : hooked)
			heart.parallaxReader.setLayerHook(name, null);
		hooked.clear();
		for (Hook hook : scene.hooks)
		{
			TextureRegion region = findRegion(page.getLoadedAtlas(), hook.region, hook.position);
			heart.parallaxReader.setLayerHook(hook.layer, (batch, layer, x, y, width, height) -> batch.draw(region, x, y, width, height));
			hooked.add(hook.layer);
		}
		if (scene.sequenceSeed != null)
			heart.parallaxReader.setSequenceSeed(scene.sequenceSeed);
		else
			heart.parallaxReader.clearSequenceSeed();
		// A tint outlives setPage: the scene before may have left one.
		heart.parallaxReader.addColorTransfert(Color.WHITE, 0);
	}

	/** Stills of every scene at a few moments of the same scroll. */
	private void takeShots()
	{
		try
		{Files.createDirectories(shotsDir);}
		catch (IOException e)
		{throw new RuntimeException(e);}
		float step = 1 / 60f;
		heart.screenSpeedConstantX = SPEED;
		for (Scene scene : scenes)
		{
			resizeWindow(1280, 720);
			show(scene);
			heart.screenSpeedConstantY = scene.speedY;
			float time = 0;
			int transferred = 0;
			boolean tinted = false, resized = false;
			for (float at : SHOT_TIMES)
			{
				for (; time < at; time += step)
				{
					while (transferred < scene.transfers.size() && time >= scene.transfers.get(transferred).at)
					{
						Transfer transfer = scene.transfers.get(transferred++);
						WholePage_Model into = heart.currentPage;
						if (transfer.page != null)
						{
							into = Utils_Page_Json.loadPage(new FileHandle(Paths.get(transfer.page).toFile()));
							heart.relativePath = Paths.get(transfer.atlasDir).toAbsolutePath().toString();
						}
						heart.transfertIntoPage(into, transfer.seconds, transfer.style);
					}
					if (!resized && scene.resizeAt >= 0 && time >= scene.resizeAt)
					{
						resized = true;
						resizeWindow(scene.resizeWidth, scene.resizeHeight);
					}
					if (!tinted && scene.tintAt >= 0 && time >= scene.tintAt)
					{
						tinted = true;
						heart.parallaxReader.addColorTransfert(scene.tint, scene.tintSeconds);
					}
					heart.act(step);
				}
				ScreenUtils.clear(Color.BLACK);
				heart.render();
				Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
				PixmapIO.writePNG(new FileHandle(shotsDir.resolve(scene.id + "-t" + (int) at + ".png").toFile()), pixmap, 6, true);
				pixmap.dispose();
			}
		}
	}

	/**
	 * Resizes the window from inside create(): GLFW only reports the new size when its events are polled, and it does not
	 * call resize() on a listener still in create(), so this waits for the size and calls it. Mesa's software X11 drawable
	 * (llvmpipe under Xvfb, CI) keeps its old size until a buffer swap: without one, the rows past it read back black (r96).
	 */
	private void resizeWindow(int width, int height)
	{
		if (Gdx.graphics.getBackBufferWidth() == width && Gdx.graphics.getBackBufferHeight() == height)
			return;
		Gdx.graphics.setWindowedMode(width, height);
		long giveUp = System.nanoTime() + 5_000_000_000L;
		while ((Gdx.graphics.getBackBufferWidth() != width || Gdx.graphics.getBackBufferHeight() != height) && System.nanoTime() < giveUp)
		{
			GLFW.glfwPollEvents();
			try
			{Thread.sleep(10);}
			catch (InterruptedException e)
			{Thread.currentThread().interrupt();}
		}
		if (Gdx.graphics.getBackBufferWidth() != width || Gdx.graphics.getBackBufferHeight() != height)
			throw new IllegalStateException("the window stayed " + Gdx.graphics.getBackBufferWidth() + "x" + Gdx.graphics.getBackBufferHeight()
					+ ", not " + width + "x" + height);
		GLFW.glfwSwapBuffers(((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle());
		Gdx.gl.glViewport(0, 0, width, height);
		resize(width, height);
	}

	/** The n-th region of that name, in atlas order, as a page names its layers' images. */
	static TextureRegion findRegion(TextureAtlas atlas, String name, int position)
	{
		int seen = 0;
		for (TextureAtlas.AtlasRegion region : atlas.getRegions())
			if (region.name.equals(name) && seen++ == position)
				return region;
		throw new IllegalArgumentException("no region " + name + " #" + position + " in the atlas");
	}

	@Override
	public void resize(int width, int height)
	{
		heart.resize(width, height);
	}

	@Override
	public void dispose()
	{
		heart.dispose();
		Gvars_Parallax.getManager().dispose();
	}

	/**
	 * A transfer's {@code "style": {"kind": "DEPTH_STAGGER", "stagger": 1}} or
	 * {@code {"kind": "DISSOLVE", "patches": 5, "softness": 0.08, "stagger": 0.5}} or
	 * {@code {"kind": "THROUGH_COLOR", "color": "ffffff", "stagger": 0}}; none, or another kind: FADE.
	 */
	static TransfertStyle style(JsonValue style)
	{
		String kind = style == null ? null : style.getString("kind", null);
		if ("DEPTH_STAGGER".equals(kind))
			return TransfertStyle.depthStagger(style.getFloat("stagger", 0));
		if ("DISSOLVE".equals(kind))
			return TransfertStyle.dissolve(style.getFloat("patches", 5), style.getFloat("softness", 0.08f), style.getFloat("stagger", 0));
		if ("THROUGH_COLOR".equals(kind))
			return TransfertStyle.throughColor(Color.valueOf(style.getString("color", "ffffff")), style.getFloat("stagger", 0));
		return TransfertStyle.FADE;
	}
}
