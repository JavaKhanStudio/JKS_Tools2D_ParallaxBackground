package jks.tools2d.parallax.rounds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxRings;
import jks.tools2d.parallax.ParallaxRings.Band;
import jks.tools2d.parallax.ParallaxRings.Gradient;
import jks.tools2d.parallax.ParallaxRings.Options;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The rings round (parallax-readers r47, engines/rounds/rings): core's {@link ParallaxRings} numbers for each scene,
 * written as {@code OUT/gdx/<id>.json}, the reference a 3D reader's own numbers are compared with
 * (tools/compare-rings.py). No window and no GL: the atlas is read for its regions' sizes only, on textures with nothing
 * behind them. A scene is a page, its atlas folder and {@code "rings"}: the {@link Options} fields it sets (front, far,
 * foot, lift, scale, pageDegrees, fogStrength; fogStrength left out is the page's own).
 * <p>
 * Run: {@code ./gradlew :rounds:rings -Pround=engines/rounds/rings -Pout=build/rings}, from the repository root.
 */
public final class RingsRound
{
	private RingsRound()
	{}

	public static void main(String[] args) throws IOException
	{
		Path round = Path.of(args[0]), out = Path.of(args[1]).resolve("gdx");
		Files.createDirectories(out);
		JsonValue scenes = new JsonReader().parse(Files.readString(round.resolve("round.json"))).get("scenes");
		float worldWidth = Gvars_Parallax.getWorldWidth();
		for (JsonValue scene = scenes.child; scene != null; scene = scene.next)
		{
			WholePage_Model page = Utils_Page_Json.loadPage(new FileHandle(Path.of(scene.getString("page")).toFile()));
			Path atlasDir = Path.of(scene.getString("atlasDir"));
			page.forceLoad(sizesOnly(atlasDir.resolve(page.pageModel.atlasName), atlasDir));
			Options options = options(scene.get("rings"), page);
			List<ParallaxLayer> layers = page.preloadValue;
			String json = json(ParallaxRings.bands(layers, worldWidth, options),
					ParallaxRings.gradient(page.topHalfSize, page.bottomHalfSize, worldWidth, options), options);
			Files.writeString(out.resolve(scene.getString("id") + ".json"), json);
			System.out.println(scene.getString("id") + ": " + layers.size() + " layers");
		}
	}

	/** The scene's options, over the defaults; its fog the page's unless it names one. jME's runner reads them too (r50). */
	public static Options options(JsonValue rings, WholePage_Model page)
	{
		Options o = new Options();
		o.fogStrength = page.getFogStrength();
		if (rings == null)
			return o;
		o.front = rings.getFloat("front", o.front);
		o.far = rings.getFloat("far", o.far);
		o.foot = rings.getFloat("foot", o.foot);
		o.lift = rings.getFloat("lift", o.lift);
		o.scale = rings.getFloat("scale", o.scale);
		o.pageDegrees = rings.getFloat("pageDegrees", o.pageDegrees);
		o.fogStrength = rings.getFloat("fogStrength", o.fogStrength);
		return o;
	}

	/** The atlas's regions, sized and ordered as libGDX loads them, on textures with no GL behind them. */
	static TextureAtlas sizesOnly(Path pack, Path imagesDir)
	{
		TextureAtlasData data = new TextureAtlasData(new FileHandle(pack.toFile()), new FileHandle(imagesDir.toFile()), false);
		TextureAtlas atlas = new TextureAtlas();
		for (TextureAtlasData.Region r : data.getRegions())
		{
			Texture page = unbacked((int) r.page.width, (int) r.page.height);
			AtlasRegion region = atlas.addRegion(r.name, page, r.left, r.top, r.rotate ? r.height : r.width, r.rotate ? r.width : r.height);
			region.index = r.index;
			region.offsetX = r.offsetX;
			region.offsetY = r.offsetY;
			region.originalWidth = r.originalWidth;
			region.originalHeight = r.originalHeight;
			region.rotate = r.rotate;
			region.packedWidth = r.width;
			region.packedHeight = r.height;
		}
		return atlas;
	}

	private static Texture unbacked(final int width, final int height)
	{
		return new Texture()
		{
			@Override
			public int getWidth()
			{return width;}

			@Override
			public int getHeight()
			{return height;}
		};
	}

	/** Numbers with 7 significant digits; a still ring's infinite radius is null. jME's runner writes its own with it (r50). */
	public static String json(List<Band> bands, Gradient g, Options o)
	{
		StringBuilder s = new StringBuilder("{\n \"options\": {");
		s.append("\"front\": ").append(num(o.front)).append(", \"far\": ").append(num(o.far)).append(", \"foot\": ").append(num(o.foot))
				.append(", \"lift\": ").append(num(o.lift)).append(", \"scale\": ").append(num(o.scale))
				.append(", \"pageDegrees\": ").append(num(o.pageDegrees)).append(", \"fogStrength\": ").append(num(o.fogStrength)).append("},\n");
		s.append(" \"gradient\": {\"topFrom\": ").append(num(g.topFrom)).append(", \"topTo\": ").append(num(g.topTo))
				.append(", \"bottomFrom\": ").append(num(g.bottomFrom)).append(", \"bottomTo\": ").append(num(g.bottomTo)).append("},\n");
		s.append(" \"bands\": [");
		for (int i = 0; i < bands.size(); i++)
		{
			Band b = bands.get(i);
			s.append(i == 0 ? "\n" : ",\n").append("  {\"layer\": ").append(b.layer)
					.append(", \"radius\": ").append(b.radius == Float.POSITIVE_INFINITY ? "null" : num(b.radius))
					.append(", \"bottom\": ").append(num(b.bottom)).append(", \"top\": ").append(num(b.top))
					.append(", \"y\": ").append(num(b.y)).append(", \"height\": ").append(num(b.height))
					.append(", \"repeat\": ").append(b.repeat).append(", \"imageShare\": ").append(num(b.imageShare))
					.append(", \"order\": ").append(b.order).append(", \"fog\": ").append(num(b.fog)).append('}');
		}
		return s.append("\n ]\n}\n").toString();
	}

	private static String num(float v)
	{return String.format(Locale.ROOT, "%.7g", v);}
}
