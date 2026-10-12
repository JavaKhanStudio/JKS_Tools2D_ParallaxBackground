package jks.tools2d.parallax.jme;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.lwjgl.opengl.GL11;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.system.AppSettings;

import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.rounds.Round;
import jks.tools2d.parallax.rounds.Round.Scene;
import jks.tools2d.parallax.rounds.Round.Transfer;

/**
 * jME's latency on the bench round (parallax-readers:r19), as shots/'s ParallaxBench times libGDX: for each scene its
 * load time (page, atlas, first frame finished on the GPU) and the time of each of {@link #FRAMES} frames after
 * {@link #WARMUP} unmeasured ones, vsync off, the frame rate unbounded. jME owns the loop, so a frame is timed from
 * its update (the last frame's swap finished first) to the gui viewport's postFrame, the last drawn, with the GPU
 * finished: act 1/60 s and render, no swap, as ParallaxBench times libGDX's. A scene's transfers start at load and last their seconds. Writes OUT_JSON
 * in ParallaxBench's shape; engines/jme/tools/bench.sh runs it off screen.
 */
public class JmeParallaxBench extends SimpleApplication
{
	static final int WARMUP = 60, FRAMES = 600;
	private static final float STEP = 1 / 60f;

	private final Path roundDir, outJson;
	private List<Scene> scenes;
	private PlaxBackground bg;
	private final StringBuilder json = new StringBuilder();

	// Where the bench is: the scene, its frame count (-1: its first frame, the end of its load), when it started.
	private int sceneIndex = -1, frame, layers;
	private long loadStart, frameStart;
	private double loadMs;
	private final double[] ms = new double[FRAMES];

	public JmeParallaxBench(Path roundDir, Path outJson)
	{
		this.roundDir = roundDir;
		this.outJson = outJson;
	}

	public static void main(String[] args)
	{
		if (args.length != 2)
		{
			System.err.println("usage: JmeParallaxBench ROUND_DIR OUT_JSON");
			System.exit(2);
		}
		JmeParallaxBench app = new JmeParallaxBench(Paths.get(args[0]), Paths.get(args[1]));
		AppSettings settings = new AppSettings(true);
		settings.setRenderer(AppSettings.LWJGL_OPENGL32);
		settings.setResolution(1280, 720);
		settings.setGammaCorrection(false);
		settings.setSamples(0);
		settings.setVSync(false);
		settings.setFrameRate(-1);
		settings.setTitle("jME parallax bench");
		settings.setAudioRenderer(null);
		app.setSettings(settings);
		app.setShowSettings(false);
		app.setPauseOnLostFocus(false);
		app.start();
	}

	@Override
	public void simpleInitApp()
	{
		setDisplayFps(false);
		setDisplayStatView(false);
		flyCam.setEnabled(false);
		assetManager.registerLocator(Paths.get("").toAbsolutePath().toString(), FileLocator.class);
		scenes = Round.read(roundDir).scenes;
		bg = new PlaxBackground();
		bg.autoAct = false;
		stateManager.attach(bg);
		guiViewPort.addProcessor(new FrameEnd(this::frameDrawn));
		json.append("{\"reader\": \"jme\", \"renderer\": \"").append(escape(GL11.glGetString(GL11.GL_RENDERER)))
				.append("\", \"measure\": \"act, render, glFinish (no swap)\", \"frames\": ").append(FRAMES)
				.append(", \"scenes\": [");
		load(0);
	}

	/** The start of a frame: the last one swapped and finished, this one's act. */
	@Override
	public void simpleUpdate(float tpf)
	{
		if (sceneIndex >= scenes.size())
			return;
		GL11.glFinish();
		if (frame == WARMUP + FRAMES)
		{
			record();
			if (sceneIndex + 1 == scenes.size())
			{
				finish();
				return;
			}
			load(sceneIndex + 1);
			return;
		}
		frameStart = System.nanoTime();
		bg.act(STEP);
	}

	/** The end of a frame, every viewport drawn, before the swap: its time once the GPU has finished it. */
	private void frameDrawn()
	{
		if (sceneIndex >= scenes.size())
			return;
		GL11.glFinish();
		long now = System.nanoTime();
		if (frame < 0)
			loadMs = (now - loadStart) / 1e6;
		else if (frame >= WARMUP)
			ms[frame - WARMUP] = (now - frameStart) / 1e6;
		frame++;
	}

	/** Loads scene {@code index} and starts its transfers: its first frame, drawn after this update, ends its load. */
	private void load(int index)
	{
		sceneIndex = index;
		Scene scene = scenes.get(index);
		loadStart = System.nanoTime();
		WholePage_Model page = PlaxBackground.loadPage(assetManager, scene.page);
		TextureAtlas atlas = JmeAtlas.load(assetManager, scene.atlasDir + "/" + page.pageModel.atlasName);
		bg.setPage(page, atlas);
		bg.resetPositions();
		bg.speedConstantX = 60;
		bg.speedConstantY = scene.speedY;
		layers = page.pageModel.pageList.size();
		for (Transfer transfer : scene.transfers)
		{
			WholePage_Model into = PlaxBackground.loadPage(assetManager, transfer.page);
			bg.transfertIntoPage(into, JmeAtlas.load(assetManager, transfer.atlasDir + "/" + into.pageModel.atlasName),
					transfer.seconds, transfer.style);
			layers += into.pageModel.pageList.size();
		}
		frame = -1;
	}

	private void record()
	{
		double mean = Arrays.stream(ms).average().orElse(0);
		double[] sorted = ms.clone();
		Arrays.sort(sorted);
		json.append(sceneIndex == 0 ? "\n" : ",\n").append(String.format(Locale.ROOT,
				"  {\"id\": \"%s\", \"layers\": %d, \"loadMs\": %.2f, \"meanMs\": %.3f, \"p50Ms\": %.3f, \"p95Ms\": %.3f, \"maxMs\": %.3f}",
				scenes.get(sceneIndex).id, layers, loadMs, mean, sorted[FRAMES / 2], sorted[FRAMES * 95 / 100], sorted[FRAMES - 1]));
	}

	private void finish()
	{
		sceneIndex = scenes.size();
		json.append("\n]}\n");
		try
		{
			Files.createDirectories(outJson.toAbsolutePath().getParent());
			Files.writeString(outJson, json);
		}
		catch (IOException e)
		{throw new UncheckedIOException(e);}
		System.out.print(json);
		stop();
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	/** Calls back at the end of the viewport it is added to. */
	private static final class FrameEnd implements com.jme3.post.SceneProcessor
	{
		private final Runnable then;
		private boolean ready;

		FrameEnd(Runnable then)
		{this.then = then;}

		@Override
		public void initialize(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp)
		{ready = true;}

		@Override
		public void postFrame(com.jme3.texture.FrameBuffer out)
		{then.run();}

		@Override
		public void reshape(com.jme3.renderer.ViewPort vp, int w, int h)
		{}

		@Override
		public boolean isInitialized()
		{return ready;}

		@Override
		public void preFrame(float tpf)
		{}

		@Override
		public void postQueue(com.jme3.renderer.queue.RenderQueue rq)
		{}

		@Override
		public void cleanup()
		{}

		@Override
		public void setProfiler(com.jme3.profile.AppProfiler profiler)
		{}
	}
}
