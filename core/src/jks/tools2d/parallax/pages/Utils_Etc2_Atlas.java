package jks.tools2d.parallax.pages;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/**
 * Loads the ETC2 copy of an atlas the editor writes ({@code name.etc2.atlas}, see
 * {@link jks.tools2d.parallax.heart.Gvars_Parallax#setCompressedAtlases}). Its {@code .zktx} pages carry their whole mip
 * chain, but libGDX's KTXTextureData still calls glGenerateMipmap on a page whose atlas filter is a MipMap one, and
 * OpenGL ES 3 answers that, on a compressed texture, with GL_INVALID_OPERATION: an error a game's glGetError check or
 * GLProfiler trips on. Here those pages are loaded without asking for mipmaps; their filters stay the atlas's.
 * <p>
 * A PNG atlas asking mipmaps of a page whose sides are not powers of two (the 2019 samples' {@code filter:
 * MipMap,MipMap} on a 5020x5160 page) gets none on WebGL 1 and OpenGL ES 2, which refuse glGenerateMipmap on such a
 * texture and leave it incomplete: every layer from it drew black (r212). Those pages load as Linear,Linear there.
 * <p>
 * Their second {@code MipMap}, the mag filter, no GL takes: libGDX passes it to glTexParameter and gets GL_INVALID_ENUM
 * on desktop too. Every atlas loaded here magnifies with that filter's texel half instead (r228).
 */
public final class Utils_Etc2_Atlas
{
	public static final String SUFFIX = ".etc2.atlas";

	private Utils_Etc2_Atlas()
	{}

	public static boolean isEtc2Atlas(String atlasFile)
	{return atlasFile != null && atlasFile.endsWith(SUFFIX);}

	/**
	 * The atlas in {@code atlasFile}, its pages next to it; an ETC2 copy's pages without generated mipmaps, a
	 * non-power-of-two page without them where the GL cannot make them.
	 */
	public static TextureAtlas load(FileHandle atlasFile)
	{
		TextureAtlasData data = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
		fit(data, isEtc2Atlas(atlasFile.name()), npotMipMapsRefused());
		return new TextureAtlas(data);
	}

	/**
	 * True on WebGL 1 and OpenGL ES 2 (a browser or phone game without GL 3): glGenerateMipmap fails there on a texture
	 * whose sides are not powers of two. Desktop GL makes them on any texture, GL 3 contexts too.
	 */
	public static boolean npotMipMapsRefused()
	{
		if (Gdx.app == null || Gdx.gl30 != null)
			return false;
		ApplicationType type = Gdx.app.getType();
		return type == ApplicationType.WebGL || type == ApplicationType.Android || type == ApplicationType.iOS;
	}

	/**
	 * Drops the mipmaps {@code data}'s pages may not have: all of an ETC2 copy's (their .zktx carries them), and, when
	 * {@code npotRefused}, a non-power-of-two page's, which then filters Linear,Linear. A MipMap mag filter becomes
	 * {@link #magnifying} it.
	 */
	static void fit(TextureAtlasData data, boolean etc2, boolean npotRefused)
	{
		for (Page page : data.getPages())
		{
			page.magFilter = magnifying(page.magFilter);
			if (etc2)
				page.useMipMaps = false;
			else if (npotRefused && page.useMipMaps
					&& !(MathUtils.isPowerOfTwo((int) page.width) && MathUtils.isPowerOfTwo((int) page.height)))
			{
				page.useMipMaps = false;
				page.minFilter = TextureFilter.Linear;
				page.magFilter = TextureFilter.Linear;
			}
		}
	}

	/**
	 * {@code filter} as GL_TEXTURE_MAG_FILTER takes it: a MipMap filter is no magnifying one, and every GL answers it
	 * with GL_INVALID_ENUM and keeps Linear (r228). The level half is dropped, the texel half kept.
	 */
	static TextureFilter magnifying(TextureFilter filter)
	{
		switch (filter)
		{
			case MipMapNearestNearest:
			case MipMapNearestLinear:
				return TextureFilter.Nearest;
			case MipMap:
			case MipMapLinearNearest:
			case MipMapLinearLinear:
				return TextureFilter.Linear;
			default:
				return filter;
		}
	}

	/**
	 * Makes {@code manager} load every {@code .etc2.atlas} through {@link Loader}, and every {@code .atlas} the manager's
	 * own TextureAtlasLoader would have loaded: its mag filter fitted, its mipmaps dropped where the GL cannot make them.
	 */
	public static void register(AssetManager manager)
	{
		if (!(manager.getLoader(TextureAtlas.class, "x" + SUFFIX) instanceof Loader))
			manager.setLoader(TextureAtlas.class, SUFFIX, new Loader(manager.getFileHandleResolver()));
		if (manager.getLoader(TextureAtlas.class, "x.atlas").getClass() == TextureAtlasLoader.class)
			manager.setLoader(TextureAtlas.class, ".atlas", new Loader(manager.getFileHandleResolver()));
	}

	/** An AssetManager's TextureAtlasLoader whose pages get only the mipmaps {@link Utils_Etc2_Atlas#load} gives them. */
	public static class Loader extends TextureAtlasLoader
	{
		private TextureAtlasData data;

		public Loader(FileHandleResolver resolver)
		{super(resolver);}

		@Override
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public Array<AssetDescriptor> getDependencies(String fileName, FileHandle atlasFile, TextureAtlasParameter parameter)
		{
			data = new TextureAtlasData(atlasFile, atlasFile.parent(), parameter != null && parameter.flip);
			fit(data, isEtc2Atlas(fileName), npotMipMapsRefused());
			Array<AssetDescriptor> dependencies = new Array<>();
			for (Page page : data.getPages())
			{
				TextureParameter params = new TextureParameter();
				params.format = page.format;
				params.genMipMaps = page.useMipMaps;
				params.minFilter = page.minFilter;
				params.magFilter = page.magFilter;
				dependencies.add(new AssetDescriptor(page.textureFile, Texture.class, params));
			}
			return dependencies;
		}

		@Override
		public TextureAtlas load(AssetManager manager, String fileName, FileHandle file, TextureAtlasParameter parameter)
		{
			for (Page page : data.getPages())
				page.texture = manager.get(page.textureFile.path().replaceAll("\\\\", "/"), Texture.class);
			TextureAtlas atlas = new TextureAtlas(data);
			data = null;
			return atlas;
		}
	}
}
