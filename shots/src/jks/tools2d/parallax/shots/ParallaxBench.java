package jks.tools2d.parallax.shots;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.ScreenUtils;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.rounds.Round;
import jks.tools2d.parallax.rounds.Round.Scene;
import jks.tools2d.parallax.rounds.Round.Transfer;

/**
 * libGDX's latency on the bench round (parallax-readers:r17): for each scene, the time to load it (page, atlas, first
 * frame finished on the GPU) and the time of each of {@link #FRAMES} frames after {@link #WARMUP} unmeasured ones
 * (act 1/60 s, clear, render, glFinish), vsync off. A scene's transfers start at load and last their seconds, so a
 * cross-fade is timed while it draws both pages. Writes OUT_JSON, the shape tools/readers-health/health.py reads:
 *
 * <pre>
 * {"reader": "gdx", "renderer": "...", "frames": 600, "scenes": [{"id", "layers", "loadMs", "meanMs", "p50Ms", "p95Ms", "maxMs"}]}
 * </pre>
 *
 * tools/parallax-bench.sh runs it off screen. engines/jme and engines/godot write the same file from their own runners.
 */
public class ParallaxBench extends ApplicationAdapter
{
	static final int WARMUP = 60, FRAMES = 600;

	private final Path roundDir, outJson;
	private Parallax_Heart heart;

	public ParallaxBench(Path roundDir, Path outJson)
	{
		this.roundDir = roundDir;
		this.outJson = outJson;
	}

	public static void main(String[] args)
	{
		if (args.length != 2)
		{
			System.err.println("usage: ParallaxBench ROUND_DIR OUT_JSON");
			System.exit(2);
		}
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Parallax bench - " + Paths.get(args[0]).getFileName());
		config.setWindowedMode(1280, 720);
		config.useVsync(false);
		ParallaxShots.keepX11();
		new Lwjgl3Application(new ParallaxBench(Paths.get(args[0]), Paths.get(args[1])), config);
	}

	@Override
	public void create()
	{
		List<Scene> scenes = Round.read(roundDir).scenes;
		heart = new Parallax_Heart();
		heart.screenSpeedConstantX = 60;
		StringBuilder json = new StringBuilder();
		json.append("{\"reader\": \"gdx\", \"renderer\": \"").append(escape(Gdx.gl.glGetString(GL20.GL_RENDERER)))
				.append("\", \"frames\": ").append(FRAMES).append(", \"scenes\": [");
		for (int i = 0; i < scenes.size(); i++)
		{
			Scene scene = scenes.get(i);
			long start = System.nanoTime();
			WholePage_Model page = Utils_Page_Json.loadPage(new FileHandle(Paths.get(scene.page).toFile()));
			heart.relativePath = Paths.get(scene.atlasDir).toAbsolutePath().toString();
			heart.setPage(page);
			heart.parallaxReader.resetPositions();
			heart.screenSpeedConstantY = scene.speedY;
			int layers = page.pageModel.pageList.size();
			for (Transfer transfer : scene.transfers)
			{
				WholePage_Model into = Utils_Page_Json.loadPage(new FileHandle(Paths.get(transfer.page).toFile()));
				heart.relativePath = Paths.get(transfer.atlasDir).toAbsolutePath().toString();
				heart.transfertIntoPage(into, transfer.seconds, transfer.style);
				layers += into.pageModel.pageList.size();
			}
			frame();
			double loadMs = (System.nanoTime() - start) / 1e6;
			for (int f = 0; f < WARMUP; f++)
				frame();
			double[] ms = new double[FRAMES];
			for (int f = 0; f < FRAMES; f++)
			{
				long t = System.nanoTime();
				frame();
				ms[f] = (System.nanoTime() - t) / 1e6;
			}
			double mean = Arrays.stream(ms).average().orElse(0);
			Arrays.sort(ms);
			json.append(i == 0 ? "\n" : ",\n").append(String.format(Locale.ROOT,
					"  {\"id\": \"%s\", \"layers\": %d, \"loadMs\": %.2f, \"meanMs\": %.3f, \"p50Ms\": %.3f, \"p95Ms\": %.3f, \"maxMs\": %.3f}",
					scene.id, layers, loadMs, mean, ms[FRAMES / 2], ms[FRAMES * 95 / 100], ms[FRAMES - 1]));
		}
		json.append("\n]}\n");
		try
		{
			Files.createDirectories(outJson.toAbsolutePath().getParent());
			Files.writeString(outJson, json);
		}
		catch (IOException e)
		{throw new UncheckedIOException(e);}
		System.out.print(json);
		Gdx.app.exit();
	}

	/** One frame as a game draws it, finished on the GPU before it returns. */
	private void frame()
	{
		heart.act(1 / 60f);
		ScreenUtils.clear(Color.BLACK);
		heart.render();
		Gdx.gl.glFinish();
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
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
}
