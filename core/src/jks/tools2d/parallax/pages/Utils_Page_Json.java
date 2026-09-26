package jks.tools2d.parallax.pages;

import java.util.ArrayList;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Reads a page from JSON with libGDX's own {@link JsonReader}: the way a browser (GWT) game loads a saved page, where
 * {@link Utils_Page} (Kryo, .plax) does not exist. Reads field by field, without reflection, so the game needs no
 * reflection configuration.
 * <p>
 * Takes a .jplax (the JSON export) or a .plaxpj (the editor project): of a project it keeps what an export of it would
 * hold, the layers drawn from the atlas. A field missing from the file keeps its default, as Jackson leaves it.
 */
public final class Utils_Page_Json
{
	private Utils_Page_Json()
	{}

	/** Reads a .jplax or .plaxpj file from the internal (assets) storage. */
	public static WholePage_Model loadPage(String internalPath)
	{return loadPage(Gdx.files.internal(internalPath));}

	public static WholePage_Model loadPage(FileHandle file)
	{
		try
		{return readPage(file.readString("UTF-8"));}
		catch (RuntimeException e)
		{throw new GdxRuntimeException("Could not read the page " + file.path(), e);}
	}

	/** Reads a page from the text of a .jplax or a .plaxpj. */
	public static WholePage_Model readPage(String json)
	{
		JsonValue root = new JsonReader().parse(json);
		if (root == null || !root.isObject())
			throw new GdxRuntimeException("Not a page: the JSON is not an object");

		// A project holds its page under "saving", with a flag per layer telling whether it comes from the atlas.
		JsonValue saving = root.get("saving");
		if (saving != null)
			return readPage(saving, saving.get("inside"));
		return readPage(root, null);
	}

	private static WholePage_Model readPage(JsonValue json, JsonValue inside)
	{
		WholePage_Model page = new WholePage_Model();
		page.topHalf_top = readColor(json.get("topHalf_top"), page.topHalf_top);
		page.topHalf_bottom = readColor(json.get("topHalf_bottom"), page.topHalf_bottom);
		page.bottomHalf_top = readColor(json.get("bottomHalf_top"), page.bottomHalf_top);
		page.bottomHalf_bottom = readColor(json.get("bottomHalf_bottom"), page.bottomHalf_bottom);
		page.topHalfSize = readFloat(json, "topHalfSize", page.topHalfSize);
		page.bottomHalfSize = readFloat(json, "bottomHalfSize", page.bottomHalfSize);
		page.repeatOnX = json.getBoolean("repeatOnX", page.repeatOnX);
		page.repeatOnY = json.getBoolean("repeatOnY", page.repeatOnY);
		page.useOriginalSize = json.getBoolean("useOriginalSize", page.useOriginalSize);

		JsonValue pageModel = json.get("pageModel");
		if (pageModel == null || pageModel.isNull())
			return page;

		page.pageModel.atlasName = pageModel.getString("atlasName", page.pageModel.atlasName);
		page.pageModel.outside = pageModel.getBoolean("outside", page.pageModel.outside);

		JsonValue layers = pageModel.get("pageList");
		if (layers != null && !layers.isNull())
		{
			page.pageModel.pageList = new ArrayList<>(layers.size);
			int i = 0;
			for (JsonValue layer = layers.child; layer != null; layer = layer.next, i++)
				if (fromAtlas(inside, i))
					page.pageModel.pageList.add(readLayer(layer));
		}
		return page;
	}

	/** A project layer without a flag comes from the atlas, as the editor reads it. */
	private static boolean fromAtlas(JsonValue inside, int index)
	{
		if (inside == null || !inside.isArray() || index >= inside.size)
			return true;
		return inside.get(index).asBoolean();
	}

	private static Parallax_Model readLayer(JsonValue json)
	{
		Parallax_Model layer = new Parallax_Model();
		layer.regionName = json.getString("regionName", layer.regionName);
		layer.regionPosition = json.getInt("regionPosition", layer.regionPosition);
		layer.flipX = json.getBoolean("flipX", layer.flipX);
		layer.flipY = json.getBoolean("flipY", layer.flipY);
		layer.parallaxScalingSpeedX = readFloat(json, "parallaxScalingSpeedX", layer.parallaxScalingSpeedX);
		layer.parallaxScalingSpeedY = readFloat(json, "parallaxScalingSpeedY", layer.parallaxScalingSpeedY);
		layer.speedXAtRest = readFloat(json, "speedXAtRest", layer.speedXAtRest);
		layer.sizeRatio = readFloat(json, "sizeRatio", layer.sizeRatio);
		layer.decal_X_Ratio = readFloat(json, "decal_X_Ratio", layer.decal_X_Ratio);
		layer.decal_Y_Ratio = readFloat(json, "decal_Y_Ratio", layer.decal_Y_Ratio);
		layer.padX = readFloat(json, "padX", layer.padX);
		layer.padXFactor = readFloat(json, "padXFactor", layer.padXFactor);
		layer.padY = readFloat(json, "padY", layer.padY);
		layer.padYFactor = readFloat(json, "padYFactor", layer.padYFactor);
		layer.mirror = json.getBoolean("mirror", layer.mirror);
		return layer;
	}

	private static Color readColor(JsonValue json, Color fallback)
	{
		if (json == null || json.isNull())
			return fallback;
		return new Color(readFloat(json, "r", 0), readFloat(json, "g", 0), readFloat(json, "b", 0),
			readFloat(json, "a", 0));
	}

	/**
	 * Reads a float as the JVM holds it. In a browser (GWT) a float is a JavaScript double that is never rounded to 32
	 * bits: 0.9607843 * 255 gives 244.99999 there and 245.0000006 on the JVM, so a color read from JSON came out 1/255
	 * darker (r86). The bits round trip rounds it to float32 in the browser and changes nothing on the JVM.
	 */
	private static float readFloat(JsonValue json, String name, float defaultValue)
	{return Float.intBitsToFloat(Float.floatToIntBits(json.getFloat(name, defaultValue)));}
}
