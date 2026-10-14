import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;

import jks.tools2d.parallax.LurkerCover;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * java [-Dlurker.opaque=250 -Dlurker.margin=0.04] -cp CORE tools/lurker-cover/LurkerBake.java PAGE.jplax ATLAS_DIR WORLD_W WORLD_H [--track SPEED FPS FRAMES] (r369):
 * every LURKER layer's cover (core's LurkerCover), as one JSON line {"layer index": [n, from, to, ...]}, which
 * tools/lurker-cover.sh writes into the page. The atlas is read with ImageIO: no GL, no window.
 * <p>
 * --track: then, one line a film frame of tools/ai-film.sh (ParallaxShots' clock: 1/60 s steps, stills at 0, 6, 12 s,
 * then a frame every FPS), the layers scrolled at SPEED as its heart scrolls them: "f covered|seen|out d...", covered
 * when every copy of the figure whose tile meets the view lies in a cover interval, out when none meets it.
 */
public class LurkerBake
{
	/** An atlas page with no GL texture behind it: the atlas's regions sit on it, its pixels are in an image. */
	static final class Page extends Texture
	{
		final BufferedImage image;

		Page(BufferedImage image)
		{this.image = image;}

		@Override
		public int getWidth()
		{return image.getWidth();}

		@Override
		public int getHeight()
		{return image.getHeight();}

		@Override
		public void setFilter(TextureFilter minFilter, TextureFilter magFilter)
		{}

		@Override
		public void setWrap(TextureWrap u, TextureWrap v)
		{}
	}

	public static void main(String[] args) throws IOException
	{
		FileHandle pageFile = new FileHandle(args[0]);
		FileHandle atlasDir = new FileHandle(args[1]);
		float worldWidth = Float.parseFloat(args[2]), worldHeight = Float.parseFloat(args[3]);
		WholePage_Model page = Utils_Page_Json.loadPage(pageFile);

		TextureAtlasData data = new TextureAtlasData(atlasDir.child(page.pageModel.atlasName), atlasDir, false);
		for (TextureAtlasData.Page atlasPage : data.getPages())
			atlasPage.texture = new Page(ImageIO.read(atlasPage.textureFile.file()));
		TextureAtlas atlas = new TextureAtlas(data);

		Gvars_Parallax.setWorldWidth(worldWidth);
		Gvars_Parallax.setWorldHeight(worldHeight);
		page.forceLoad(atlas, null);
		List<ParallaxLayer> layers = page.buildDrawing(worldWidth, worldHeight);
		int opaque = Integer.parseInt(System.getProperty("lurker.opaque", String.valueOf(LurkerCover.OPAQUE)));
		float margin = Float.parseFloat(System.getProperty("lurker.margin", String.valueOf(LurkerCover.MARGIN)));
		LurkerCover.bake(page, layers, (texture, x, y) -> texture instanceof Page ? ((Page) texture).image.getRGB(x, y) >>> 24 : -1, opaque, margin);

		StringBuilder json = new StringBuilder("{");
		Map<Integer, float[]> covers = new HashMap<>();
		for (int i = 0; i < page.pageModel.pageList.size(); i++)
			if (page.pageModel.pageList.get(i).kind == Enum_LayerKind.LURKER)
			{
				float[] cover = page.pageModel.pageList.get(i).lurkerCover;
				covers.put(i, cover);
				json.append(json.length() > 1 ? ", " : "").append('"').append(i).append("\": [");
				for (int k = 0; k < cover.length; k++)
					json.append(k > 0 ? ", " : "").append(cover[k]);
				json.append(']');
			}
		System.out.println(json.append('}'));

		if (args.length > 4 && args[4].equals("--track"))
			track(page, layers, covers, Float.parseFloat(args[5]), Integer.parseInt(args[6]), Integer.parseInt(args[7]), worldWidth);
	}

	/** ParallaxShots.takeShots' clock and scroll, without drawing: where each figure stands against its cover. */
	private static void track(WholePage_Model page, List<ParallaxLayer> layers, Map<Integer, float[]> covers, float speed, int fps,
			int frames, float viewWidth)
	{
		float[] shotTimes = { 0, 6, 12 };
		float step = 1 / 60f;
		float time = 0;
		for (int shot = 0; shot < shotTimes.length + frames; shot++)
		{
			float at = shot < shotTimes.length ? shotTimes[shot] : time + (Math.round(60f / fps) - 0.5f) * step;
			for (; time < at; time += step)
				for (ParallaxLayer layer : layers)
					layer.act(step, speed, 0, page.repeatOnX, page.repeatOnY);
			if (shot < shotTimes.length)
				continue;
			for (Map.Entry<Integer, float[]> entry : covers.entrySet())
				System.out.println((shot - shotTimes.length) + " " + entry.getKey() + " " + state(layers, entry.getKey(), entry.getValue(), viewWidth));
		}
	}

	/** "covered", "seen" or "out", then each copy in view's offset against each cover entry's layer. */
	private static String state(List<ParallaxLayer> layers, int lurker, float[] cover, float viewWidth)
	{
		ParallaxLayer figure = layers.get(lurker);
		float period = figure.getTotalWidth(), width = figure.getWidth();
		float first = figure.getCurrentDistanceX() - (float) Math.ceil((figure.getCurrentDistanceX() + width) / period) * period;
		StringBuilder offsets = new StringBuilder();
		boolean any = false, all = true;
		for (float x = first; x < viewWidth; x += period)
		{
			if (x + width <= 0)
				continue;
			any = true;
			boolean covered = false;
			for (int k = 0; k < cover.length; k += 3)
			{
				ParallaxLayer near = layers.get(lurker + (int) cover[k]);
				float p = near.getTotalWidth();
				float d = ((x - near.getCurrentDistanceX()) % p + p) % p;
				offsets.append(String.format(Locale.ROOT, " %.3f", d));
				if (((d - cover[k + 1]) % p + p) % p <= cover[k + 2] - cover[k + 1])
					covered = true;
			}
			all &= covered;
		}
		return (!any ? "out" : all ? "covered" : "seen") + offsets;
	}
}
