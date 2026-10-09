package jks.tools2d.parallax.etc2check;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.profiling.GLErrorListener;
import com.badlogic.gdx.graphics.glutils.KTXTextureData;
import com.badlogic.gdx.graphics.profiling.GLInterceptor;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.utils.ScreenUtils;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * r140: every .plax in the assets, drawn once from its PNG atlas and once from its ETC2 copy
 * ({@link Gvars_Parallax#setCompressedAtlases}), each the way a game loads it (the shared AssetManager, internal files),
 * scrolled 2 s at 60 units/s and saved as {@code <page>-png.png} / {@code <page>-etc2.png} in the app's files. Every GL
 * error is caught by GLProfiler, with the GL call that raised it. A last pass loads each ETC2 atlas through libGDX's
 * own {@code new TextureAtlas(file)}, which asks for glGenerateMipmap: what the page loader avoids since r173. The
 * report is {@code results.txt}, also logged under the tag ETC2; its last line is {@code ETC2 DONE}.
 * <p>
 * r206: started with a hold ({@code "hiver etc2"}, {@code "hiver png"}, or {@code "none"} for nothing loaded), it loads
 * that one page in that one mode and keeps drawing it, scrolling, until stopped, after logging {@code ETC2 HOLDING}:
 * what {@code dumpsys meminfo} then reads as GL mtrack is that page's video memory (tools/android-etc2-memory.sh).
 */
public class Etc2Check extends ApplicationAdapter
{
	private static final String TAG = "ETC2";
	private static final float STEP = 1 / 60f;

	private final List<String> pages = new ArrayList<>();
	private final List<String> errors = new ArrayList<>();
	private final StringBuilder report = new StringBuilder();
	private GLProfiler profiler;
	private int run;
	private boolean failed;
	private final String hold;
	private Parallax_Heart held;

	public Etc2Check()
	{this(null);}

	/** @param hold {@code "<page> png"}, {@code "<page> etc2"} or {@code "none"}; null runs the check. */
	public Etc2Check(String hold)
	{this.hold = hold;}

	@Override
	public void create()
	{
		for (FileHandle file : Gdx.files.internal("").list())
			if (file.name().endsWith(".plax"))
				pages.add(file.nameWithoutExtension());
		profiler = new GLProfiler(Gdx.graphics);
		profiler.setListener(new GLErrorListener()
		{
			@Override
			public void onError(int error)
			{errors.add(GLInterceptor.resolveErrorNumber(error) + " from " + glCall());}
		});
		profiler.enable();
		line("GL " + Gdx.graphics.getGLVersion().getDebugVersionString().replace('\n', ' '));
		line("gl30 " + (Gdx.gl30 != null) + ", supportsEtc2 " + Gvars_Parallax.supportsEtc2() + ", screen "
				+ Gdx.graphics.getBackBufferWidth() + "x" + Gdx.graphics.getBackBufferHeight());
		line("extensions with ETC/compressed: " + extensions());
		line("pages " + pages);
		if (hold != null)
			startHold();
	}

	/** Loads the held page in its mode, or nothing for {@code none}, and says so once it is on the GPU. */
	private void startHold()
	{
		String[] parts = hold.split(" ");
		if (parts.length == 2)
		{
			Gvars_Parallax.setManager(new AssetManager());
			Gvars_Parallax.setCompressedAtlases(parts[1].equals("etc2"));
			WholePage_Model model = Utils_Page.loadPage(parts[0] + ".plax");
			held = new Parallax_Heart();
			held.setPage(model);
			held.screenSpeedConstantX = 60;
			for (Texture texture : model.getLoadedAtlas().getTextures())
				line("  " + describe(texture));
		}
		line("ETC2 HOLDING " + hold + ", GL errors " + (errors.isEmpty() ? "none" : errors));
	}

	@Override
	public void render()
	{
		if (hold != null)
		{
			ScreenUtils.clear(Color.BLACK);
			if (held != null)
			{
				held.act(STEP);
				held.render();
			}
			return;
		}
		if (run < pages.size() * 2)
		{
			String page = pages.get(run / 2);
			drawPage(page, run % 2 == 1);
		}
		else if (run == pages.size() * 2)
		{
			for (String page : pages)
				loadWithGdxLoader(page);
			line(failed || pages.isEmpty() ? "ETC2 FAIL" : "ETC2 PASS");
			Gdx.files.local("results.txt").writeString(report.toString(), false);
			line("ETC2 DONE");
			Gdx.app.exit();
		}
		run++;
	}

	/** Loads {@code page} as a game does, scrolls it 2 s, draws it and saves the frame. */
	private void drawPage(String page, boolean etc2)
	{
		String mode = etc2 ? "etc2" : "png";
		errors.clear();
		AssetManager manager = new AssetManager();
		Gvars_Parallax.setManager(manager);
		Gvars_Parallax.setCompressedAtlases(etc2);
		long start = System.nanoTime();
		WholePage_Model model = Utils_Page.loadPage(page + ".plax");
		Parallax_Heart heart = new Parallax_Heart();
		heart.setPage(model);
		long loadMs = (System.nanoTime() - start) / 1_000_000;
		List<String> loadErrors = new ArrayList<>(errors);
		line(page + " " + mode + ": loaded " + manager.getAssetNames() + " in " + loadMs + " ms");
		for (Texture texture : model.getLoadedAtlas().getTextures())
			line("  " + describe(texture));
		line("  GL errors while loading: " + (loadErrors.isEmpty() ? "none" : loadErrors));

		heart.screenSpeedConstantX = 60;
		for (int i = 0; i < 120; i++)
			heart.act(STEP);
		errors.clear();
		ScreenUtils.clear(Color.BLACK);
		heart.render();
		Pixmap frame = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
		PixmapIO.writePNG(Gdx.files.local(page + "-" + mode + ".png"), frame, 6, true);
		frame.dispose();
		line("  GL errors while drawing: " + (errors.isEmpty() ? "none" : errors));
		boolean etc2Loaded = false;
		for (String asset : manager.getAssetNames())
			etc2Loaded |= asset.endsWith(".etc2.atlas");
		if (!loadErrors.isEmpty() || !errors.isEmpty() || etc2Loaded != etc2)
			failed = true;

		heart.dispose();
		manager.dispose();
	}

	/** The pre-r173 path: libGDX's TextureAtlas loader, which generates mipmaps on a MipMap-filtered page. */
	private void loadWithGdxLoader(String page)
	{
		FileHandle file = Gdx.files.internal(page + ".etc2.atlas");
		if (!file.exists())
			return;
		errors.clear();
		TextureAtlas atlas = new TextureAtlas(file);
		line(page + " etc2 through new TextureAtlas(file), not the page loader: GL errors "
				+ (errors.isEmpty() ? "none" : errors.toString()) + " (r173 skips this call; information only)");
		atlas.dispose();
	}

	private static String describe(Texture texture)
	{
		TextureData data = texture.getTextureData();
		int w = texture.getWidth(), h = texture.getHeight();
		if (data instanceof KTXTextureData)
		{
			int levels = ((KTXTextureData) data).getNumberOfMipMapLevels();
			long bytes = 0;
			for (int l = 0; l < levels; l++)
				bytes += (long) ((Math.max(1, w >> l) + 3) / 4) * ((Math.max(1, h >> l) + 3) / 4) * 16;
			return w + "x" + h + " KTX compressed, " + levels + " levels in the file, uploaded "
					+ mib(bytes) + " (ETC2 RGBA8: 1 byte a pixel), filter " + texture.getMinFilter() + "/" + texture.getMagFilter();
		}
		long bytes = (long) w * h * 4;
		if (data.useMipMaps())
			bytes = bytes * 4 / 3;
		return w + "x" + h + " " + data.getClass().getSimpleName() + " " + data.getFormat() + ", mipmaps " + data.useMipMaps()
				+ ", uploaded " + mib(bytes) + ", filter " + texture.getMinFilter() + "/" + texture.getMagFilter();
	}

	private static String mib(long bytes)
	{return String.format("%.2f MiB", bytes / 1048576.0);}

	/** The GL call GLProfiler's interceptor was in when the error was read, and who made it. */
	private static String glCall()
	{
		StackTraceElement[] stack = new Throwable().getStackTrace();
		for (int i = 0; i < stack.length - 1; i++)
			if (stack[i].getClassName().contains("Interceptor") && !stack[i + 1].getClassName().contains("Interceptor"))
				return stack[i].getMethodName() + " in " + stack[i + 1].getClassName().replaceAll(".*\\.", "") + "." + stack[i + 1].getMethodName();
		return "?";
	}

	private static String extensions()
	{
		StringBuilder found = new StringBuilder();
		for (String ext : Gdx.gl.glGetString(GL20.GL_EXTENSIONS).split(" "))
			if (ext.contains("ETC") || ext.contains("compression") || ext.contains("ASTC"))
				found.append(ext).append(' ');
		return found.toString().trim();
	}

	private void line(String text)
	{
		Gdx.app.log(TAG, text);
		report.append(text).append('\n');
	}
}
