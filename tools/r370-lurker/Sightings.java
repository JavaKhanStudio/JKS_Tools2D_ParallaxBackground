import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;

import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * java -cp CORE tools/r370-lurker/Sightings.java PAGE.jplax ATLAS_DIR SECONDS [SEED] (r370): a LURKER page's sightings as
 * the reader runs them on ParallaxShots' clock (1/60 s steps at 60 units a second in a 40 x 22.5 world, drawn at 0, 6
 * and 12 s and every step after 12 s), without GL: one line per step where a figure's state changes,
 * "time layer in-sight|hidden there|gone", and at each still "still t layer ...". SEED replaces every LURKER layer's seed. What tools/r370-lurker/make_round.py
 * picks the round's seed and stills with.
 */
public class Sightings
{
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
		WholePage_Model page = Utils_Page_Json.loadPage(new FileHandle(args[0]));
		FileHandle atlasDir = new FileHandle(args[1]);
		float seconds = Float.parseFloat(args[2]);
		TextureAtlasData data = new TextureAtlasData(atlasDir.child(page.pageModel.atlasName), atlasDir, false);
		for (TextureAtlasData.Page atlasPage : data.getPages())
			atlasPage.texture = new Page(ImageIO.read(atlasPage.textureFile.file()));
		Gvars_Parallax.setWorldWidth(40);
		Gvars_Parallax.setWorldHeight(22.5f);
		page.forceLoad(new TextureAtlas(data), null);
		List<ParallaxLayer> layers = page.buildDrawing(40, 22.5f);
		if (args.length > 3)
			for (ParallaxLayer layer : layers)
				if (layer.getKind() == Enum_LayerKind.LURKER)
					layer.setLurker(Integer.parseInt(args[3]), layer.getLurkerOdds(), layer.getLurkerCover());

		ParallaxPageReader reader = new ParallaxPageReader();
		reader.setWorldSize(40, 22.5f);
		reader.addLayers(layers);
		reader.setRepeatOnX(page.repeatOnX);
		reader.setRepeatOnY(page.repeatOnY);
		reader.resetPositions();
		reader.setLayerEffects((LayerEffects) Proxy.newProxyInstance(Sightings.class.getClassLoader(), new Class<?>[] { LayerEffects.class },
				(proxy, method, arguments) -> method.getReturnType() == boolean.class ? false : null));
		Color color = new Color(Color.WHITE);
		Batch batch = (Batch) Proxy.newProxyInstance(Sightings.class.getClassLoader(), new Class<?>[] { Batch.class }, (proxy, method, arguments) ->
		{
			if (method.getName().equals("getColor"))
				return color;
			Class<?> type = method.getReturnType();
			if (type == boolean.class)
				return Boolean.FALSE;
			if (type == int.class)
				return 0;
			return type == float.class ? (Object) 0f : null;
		});
		OrthographicCamera camera = new OrthographicCamera();
		// What setToOrtho sets and the reader reads: its update needs libGDX's natives.
		camera.viewportWidth = 40;
		camera.viewportHeight = 22.5f;
		camera.position.set(20, 11.25f, 0);

		float step = 1 / 60f, time = 0;
		float[] stills = { 0, 6, 12 };
		String[] was = new String[layers.size()];
		reader.draw(camera, batch);
		still(layers, 0);
		print(layers, was, 0);
		for (int still = 1; time < seconds; still++)
		{
			float at = still < stills.length ? stills[still] : time + step / 2;
			for (; time < at; time += step)
			{
				reader.act(step, 60, 0);
				print(layers, was, time + step);
			}
			reader.draw(camera, batch);
			if (still < stills.length)
				still(layers, at);
		}
	}

	/** At a still: "still t layer in-sight|hidden there|gone". */
	private static void still(List<ParallaxLayer> layers, float time)
	{
		for (int i = 0; i < layers.size(); i++)
			if (layers.get(i).getKind() == Enum_LayerKind.LURKER)
				System.out.println(String.format(Locale.ROOT, "still %.0f %d %s %s", time, i, layers.get(i).isLurkerInSight() ? "in-sight" : "hidden",
						layers.get(i).isLurkerThere() ? "there" : "gone"));
	}

	private static void print(List<ParallaxLayer> layers, String[] was, float time)
	{
		for (int i = 0; i < layers.size(); i++)
		{
			ParallaxLayer layer = layers.get(i);
			if (layer.getKind() != Enum_LayerKind.LURKER)
				continue;
			String now = (layer.isLurkerInSight() ? "in-sight" : "hidden") + " " + (layer.isLurkerThere() ? "there" : "gone");
			if (!now.equals(was[i]))
				System.out.println(String.format(Locale.ROOT, "%.4f %d %s", time, i, now));
			was[i] = now;
		}
	}
}
