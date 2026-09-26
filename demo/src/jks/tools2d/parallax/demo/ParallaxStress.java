package jks.tools2d.parallax.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * Stress test: two generated pages of {@code --layers} layers each, one per sample atlas, with random sizes, speeds,
 * paddings, flips and mirrors, auto-scrolled and cross-faded every {@code --every} seconds. Vsync is off: it prints the
 * frame time (act + render, GPU included through glFinish), the GL draw calls and texture binds, then exits.
 *
 * <pre>
 * ./gradlew :demo:stress --args="--layers 400 --repeat xy --seconds 10"
 * </pre>
 * {@code --repeat x|y|xy|none}, {@code --size min,max} (layer width in worlds), {@code --seed n},
 * {@code --shot file.png} saves the last frame, {@code --atlases a.atlas,b.atlas} (assets-relative) replaces the two
 * sample atlases. "cpu" is act + render before glFinish: the time the game thread spends issuing the frame.
 * {@code --plax page.plax} (assets-relative, its atlas too) shows that saved page instead, still, and
 * {@code --original-size true|false} overrides the pages' {@link WholePage_Model#useOriginalSize}: with {@code --shot},
 * a before/after of stripped atlas regions (tools/r41-stripped-shot.sh).
 */
public class ParallaxStress extends ApplicationAdapter
{
	private final int layerCount;
	private final boolean repeatX, repeatY;
	private final float minSize, maxSize, seconds, every;
	private final long seed;
	private final String shot;
	private final String[] atlases;
	private final String plax;
	private final Boolean originalSize;

	private Parallax_Heart heart;
	private WholePage_Model[] pages;
	private int shown;
	private GLProfiler profiler;

	private float elapsed, sinceTransfer;
	private final float[] frameMs = new float[1 << 16];
	private final float[] cpuMs = new float[frameMs.length];
	private int frames, drawCalls, textureBindings, vertices;

	public ParallaxStress(String[] args)
	{
		int layers = 200;
		String repeat = "x", size = "0.3,1.5", shotFile = null, atlasNames = "Hiver.atlas,Printemps.atlas", plaxFile = null, original = null;
		float secs = 10, transferEvery = 2;
		long s = 42;
		for (int i = 0; i + 1 < args.length; i += 2)
		{
			switch (args[i])
			{
				case "--layers": layers = Integer.parseInt(args[i + 1]); break;
				case "--repeat": repeat = args[i + 1]; break;
				case "--size": size = args[i + 1]; break;
				case "--seconds": secs = Float.parseFloat(args[i + 1]); break;
				case "--every": transferEvery = Float.parseFloat(args[i + 1]); break;
				case "--seed": s = Long.parseLong(args[i + 1]); break;
				case "--shot": shotFile = args[i + 1]; break;
				case "--atlases": atlasNames = args[i + 1]; break;
				case "--plax": plaxFile = args[i + 1]; break;
				case "--original-size": original = args[i + 1]; break;
				default: throw new IllegalArgumentException("Unknown option " + args[i]);
			}
		}
		layerCount = layers;
		repeatX = repeat.contains("x");
		repeatY = repeat.contains("y");
		String[] bounds = size.split(",");
		minSize = Float.parseFloat(bounds[0]);
		maxSize = Float.parseFloat(bounds[1]);
		seconds = secs;
		every = transferEvery;
		seed = s;
		shot = shotFile;
		atlases = atlasNames.split(",");
		plax = plaxFile;
		originalSize = original == null ? null : Boolean.valueOf(original);
	}

	public static void main(String[] args)
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Parallax stress");
		config.setWindowedMode(1280, 720);
		config.useVsync(false);
		config.setForegroundFPS(0);
		new Lwjgl3Application(new ParallaxStress(args), config);
	}

	@Override
	public void create()
	{
		heart = new Parallax_Heart();
		Random random = new Random(seed);
		if (plax != null)
		{
			WholePage_Model page = Utils_Page.loadPage(plax);
			pages = new WholePage_Model[] { page, page };
		}
		else
			pages = new WholePage_Model[] { page(atlases[0], random), page(atlases[atlases.length - 1], random) };
		if (originalSize != null)
			for (WholePage_Model page : pages)
				page.useOriginalSize = originalSize;
		heart.setPage(pages[0]);
		heart.screenSpeedConstantX = plax != null ? 0 : 80;
		heart.screenSpeedConstantY = repeatY && plax == null ? 40 : 0;

		profiler = new GLProfiler(Gdx.graphics);
		profiler.enable();
	}

	/** A page of {@link #layerCount} layers drawn from every region of that atlas, back to front. */
	private WholePage_Model page(String atlasName, Random random)
	{
		WholePage_Model page = new WholePage_Model(atlasName);
		page.repeatOnX = repeatX;
		page.repeatOnY = repeatY;
		page.topHalf_top.set(0.2f, 0.3f, 0.6f, 1);
		page.topHalf_bottom.set(0.7f, 0.8f, 1, 1);
		page.bottomHalf_top.set(0.3f, 0.3f, 0.3f, 1);
		page.bottomHalf_bottom.set(0.1f, 0.1f, 0.1f, 1);

		Gvars_Parallax.getManager().load(atlasName, TextureAtlas.class);
		Gvars_Parallax.getManager().finishLoadingAsset(atlasName);
		Array<AtlasRegion> regions = Gvars_Parallax.getManager().get(atlasName, TextureAtlas.class).getRegions();

		ArrayList<Parallax_Model> models = new ArrayList<>(layerCount);
		for (int i = 0; i < layerCount; i++)
		{
			AtlasRegion region = regions.get(random.nextInt(regions.size));
			int position = 0;
			for (int r = 0; r < regions.size && regions.get(r) != region; r++)
				if (regions.get(r).name.equals(region.name))
					position++;

			float depth = (i + 1f) / layerCount;
			Parallax_Model model = new Parallax_Model();
			model.regionName = region.name;
			model.regionPosition = position;
			model.sizeRatio = minSize + random.nextFloat() * (maxSize - minSize);
			model.parallaxScalingSpeedX = 0.005f + depth * 0.05f;
			model.parallaxScalingSpeedY = repeatY ? 0.005f + depth * 0.05f : 0;
			model.decal_X_Ratio = random.nextFloat() * 100;
			model.decal_Y_Ratio = random.nextFloat() * 90 - 20;
			model.padX = random.nextFloat() < 0.3f ? random.nextFloat() * 10 : 0;
			model.padY = repeatY && random.nextFloat() < 0.3f ? random.nextFloat() * 10 : 0;
			model.flipX = random.nextBoolean();
			model.flipY = random.nextFloat() < 0.1f;
			model.mirror = random.nextFloat() < 0.2f;
			model.speedXAtRest = random.nextFloat() < 0.1f ? random.nextFloat() * 50 : 0;
			models.add(model);
		}
		page.pageModel.pageList = models;
		return page;
	}

	@Override
	public void render()
	{
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		elapsed += delta;
		sinceTransfer += delta;
		if (every > 0 && plax == null && sinceTransfer >= every)
		{
			sinceTransfer = 0;
			shown = 1 - shown;
			heart.transfertIntoPage(pages[shown], every * 0.8f);
		}

		profiler.reset();
		long start = System.nanoTime();
		ScreenUtils.clear(Color.BLACK);
		heart.act(delta);
		heart.render();
		float cpu = (System.nanoTime() - start) / 1e6f;
		Gdx.gl.glFinish();
		float ms = (System.nanoTime() - start) / 1e6f;

		// The first second warms the JIT and uploads the textures: not measured.
		if (elapsed > 1 && frames < frameMs.length)
		{
			cpuMs[frames] = cpu;
			frameMs[frames++] = ms;
			drawCalls += profiler.getDrawCalls();
			textureBindings += profiler.getTextureBindings();
			vertices += (int) profiler.getVertexCount().total;
		}

		if (elapsed >= seconds + 1)
		{
			if (shot != null)
				saveShot();
			report();
			Gdx.app.exit();
		}
	}

	private void report()
	{
		if (frames == 0)
			return;
		float[] sorted = Arrays.copyOf(frameMs, frames);
		Arrays.sort(sorted);
		double sum = 0, cpu = 0;
		for (int i = 0; i < frames; i++)
		{
			sum += sorted[i];
			cpu += cpuMs[i];
		}
		System.out.printf("stress layers=%d repeat=%s%s size=%.2f..%.2f frames=%d | frame ms avg=%.2f p50=%.2f p99=%.2f max=%.2f (cpu avg %.2f) | per frame: draw calls %.1f, texture binds %.1f, vertices %.0f%n",
				layerCount, repeatX ? "x" : "", repeatY ? "y" : "", minSize, maxSize, frames,
				sum / frames, sorted[frames / 2], sorted[(int) (frames * 0.99)], sorted[frames - 1], cpu / frames,
				drawCalls / (float) frames, textureBindings / (float) frames, vertices / (float) frames);
	}

	private void saveShot()
	{
		Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
		PixmapIO.writePNG(Gdx.files.absolute(shot), pixmap, 6, true);
		pixmap.dispose();
	}

	@Override
	public void resize(int width, int height)
	{heart.resize(width, height);}

	@Override
	public void dispose()
	{
		heart.dispose();
		Gvars_Parallax.getManager().dispose();
	}
}
