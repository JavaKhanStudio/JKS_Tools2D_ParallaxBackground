package jks.tools2d.parallax.webp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;

import org.lwjgl.glfw.GLFW;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * r523's frame check (tools/r523-webp/frame-check.sh): the same page drawn by libGDX from a PNG atlas page and from
 * its lossless WebP twin, each both ways a page loads (a relative path: Utils_Etc2_Atlas.load; the AssetManager:
 * Utils_Etc2_Atlas.Loader), at two moments of a scroll. Writes OUT/&lt;way&gt;-&lt;png|webp&gt;-t&lt;s&gt;.png and
 * OUT/results.txt, one line a frame pair with the pixels that differ.
 * <p>
 * Args: PNG_DIR WEBP_DIR OUT. Each DIR holds page.jplax and the atlas it names, beside its page image.
 */
public class WebpFrameCheck extends ApplicationAdapter
{
	private static final float[] TIMES = { 0, 4 };
	private final File pngDir, webpDir, out;

	WebpFrameCheck(File pngDir, File webpDir, File out)
	{
		this.pngDir = pngDir;
		this.webpDir = webpDir;
		this.out = out;
	}

	public static void main(String[] args)
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("r523 WebP frame check");
		config.setWindowedMode(1280, 720);
		config.useVsync(false);
		// As ParallaxShots.keepX11: GLFW 3.4 would go native Wayland inside cage (r163).
		if (System.getenv("DISPLAY") != null && GLFW.glfwPlatformSupported(GLFW.GLFW_PLATFORM_X11))
			GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
		new Lwjgl3Application(new WebpFrameCheck(new File(args[0]), new File(args[1]), new File(args[2])), config);
	}

	@Override
	public void create()
	{
		ParallaxWebp.install();
		out.mkdirs();
		boolean pass = true;
		try (PrintWriter results = new PrintWriter(new FileWriter(new File(out, "results.txt"))))
		{
			for (String way : new String[] { "path", "manager" })
			{
				Pixmap[] png = frames(way, pngDir), webp = frames(way, webpDir);
				for (int i = 0; i < TIMES.length; i++)
				{
					int differ = differing(png[i], webp[i]);
					pass &= differ == 0;
					results.printf("%s t%d: %d of %d pixels differ, PNG frame vs WebP frame%n", way, (int) TIMES[i], differ,
							png[i].getWidth() * png[i].getHeight());
					save(png[i], way + "-png-t" + (int) TIMES[i]);
					save(webp[i], way + "-webp-t" + (int) TIMES[i]);
				}
			}
			results.println(pass ? "WEBP PASS" : "WEBP FAIL");
		}
		catch (IOException e)
		{throw new RuntimeException(e);}
		Gdx.app.exit();
	}

	/** The page in {@code dir}, drawn at each of {@link #TIMES} of a scroll, loaded the {@code way} named. */
	private Pixmap[] frames(String way, File dir)
	{
		WholePage_Model page = Utils_Page_Json.loadPage(new FileHandle(new File(dir, "page.jplax")));
		Parallax_Heart heart = new Parallax_Heart();
		AssetManager manager = null;
		if (way.equals("manager"))
		{
			manager = new AssetManager(name -> new FileHandle(new File(dir, name)));
			Gvars_Parallax.setManager(manager);
			heart.relativePath = "";
		}
		else
			heart.relativePath = dir.getAbsolutePath();
		heart.setPage(page);
		heart.screenSpeedConstantX = 3;
		Pixmap[] shots = new Pixmap[TIMES.length];
		float time = 0, step = 1 / 60f;
		for (int i = 0; i < TIMES.length; i++)
		{
			for (; time < TIMES[i]; time += step)
				heart.act(step);
			ScreenUtils.clear(Color.BLACK);
			heart.render();
			shots[i] = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
		}
		heart.dispose();
		page.disposeOwnedAtlas();
		if (manager != null)
			manager.dispose();
		return shots;
	}

	private static int differing(Pixmap a, Pixmap b)
	{
		int differ = 0;
		for (int y = 0; y < a.getHeight(); y++)
			for (int x = 0; x < a.getWidth(); x++)
				if (a.getPixel(x, y) != b.getPixel(x, y))
					differ++;
		return differ;
	}

	private void save(Pixmap pixmap, String name)
	{
		PixmapIO.writePNG(new FileHandle(new File(out, name + ".png")), pixmap, 6, true);
		pixmap.dispose();
	}
}
