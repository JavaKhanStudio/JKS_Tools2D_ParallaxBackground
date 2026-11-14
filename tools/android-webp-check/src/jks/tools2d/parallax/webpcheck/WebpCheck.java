package jks.tools2d.parallax.webpcheck;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.webp.ParallaxWebp;

/**
 * r523 on Android: (1) webp/test-data's lossless fixture decoded by ParallaxWebp (BitmapFactory here) against its PNG
 * decoded by libGDX, byte for byte, and the lossy one decoded; (2) the page in assets png/ and webp/ (one layer, its
 * atlas page a PNG or its WebP twin) loaded the way a game does (the shared AssetManager, internal files), scrolled and
 * drawn, saved as {@code page-png.png} / {@code page-webp.png} in the app's files, and compared. The report is
 * {@code results.txt}, also logged under the tag WEBP; its last line is {@code WEBP DONE}.
 */
public class WebpCheck extends ApplicationAdapter
{
	private static final String TAG = "WEBP";
	private final StringBuilder report = new StringBuilder();

	@Override
	public void create()
	{
		boolean pass = true;
		try
		{
			ParallaxWebp.install();
			line("app type " + Gdx.app.getType() + ", Android API " + Gdx.app.getVersion());

			Pixmap png = new Pixmap(Gdx.files.internal("fairy-crop.png"));
			long start = System.nanoTime();
			Pixmap webp = ParallaxWebp.pixmap(Gdx.files.internal("fairy-crop.webp"));
			line("fairy-crop.webp decoded " + webp.getWidth() + "x" + webp.getHeight() + " " + webp.getFormat() + " in "
					+ (System.nanoTime() - start) / 1000000 + " ms");
			int differ = differingBytes(png, webp);
			line("fairy-crop: " + differ + " bytes differ, PNG (libGDX) vs lossless WebP (BitmapFactory)");
			pass &= differ == 0 && png.getWidth() == webp.getWidth() && png.getHeight() == webp.getHeight();
			png.dispose();
			webp.dispose();
			Pixmap lossy = ParallaxWebp.pixmap(Gdx.files.internal("fairy-crop-lossy.webp"));
			line("fairy-crop-lossy.webp decoded " + lossy.getWidth() + "x" + lossy.getHeight());
			lossy.dispose();

			Pixmap framePng = frame("png"), frameWebp = frame("webp");
			int pixels = differingPixels(framePng, frameWebp);
			line("page: " + pixels + " of " + framePng.getWidth() * framePng.getHeight()
					+ " pixels differ, PNG atlas page frame vs WebP atlas page frame");
			pass &= pixels == 0;
			framePng.dispose();
			frameWebp.dispose();
		}
		catch (RuntimeException e)
		{
			line("error: " + e);
			pass = false;
		}
		line(pass ? "WEBP PASS" : "WEBP FAIL");
		Gdx.files.local("results.txt").writeString(report.toString(), false);
		line("WEBP DONE");
	}

	/** The page in assets {@code folder}, loaded through the shared AssetManager, scrolled 2 s and drawn. */
	private Pixmap frame(String folder)
	{
		AssetManager manager = new AssetManager(name -> Gdx.files.internal(folder + "/" + name));
		Gvars_Parallax.setManager(manager);
		WholePage_Model page = Utils_Page_Json.loadPage(Gdx.files.internal(folder + "/page.jplax"));
		Parallax_Heart heart = new Parallax_Heart();
		heart.relativePath = "";
		heart.setPage(page);
		heart.screenSpeedConstantX = 3;
		for (int i = 0; i < 120; i++)
			heart.act(1 / 60f);
		ScreenUtils.clear(Color.BLACK);
		heart.render();
		Pixmap frame = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
		PixmapIO.writePNG(Gdx.files.local("page-" + folder + ".png"), frame, 6, true);
		line(folder + ": loaded " + manager.getAssetNames());
		heart.dispose();
		manager.dispose();
		return frame;
	}

	private static int differingBytes(Pixmap a, Pixmap b)
	{
		int n = Math.min(a.getPixels().capacity(), b.getPixels().capacity()), differ = 0;
		for (int i = 0; i < n; i++)
			if (a.getPixels().get(i) != b.getPixels().get(i))
				differ++;
		return differ;
	}

	private static int differingPixels(Pixmap a, Pixmap b)
	{
		int differ = 0;
		for (int y = 0; y < a.getHeight(); y++)
			for (int x = 0; x < a.getWidth(); x++)
				if (a.getPixel(x, y) != b.getPixel(x, y))
					differ++;
		return differ;
	}

	private void line(String text)
	{
		Gdx.app.log(TAG, text);
		report.append(text).append('\n');
	}
}
