package jks.tools2d.parallax.pages;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.badlogic.gdx.utils.Array;

/**
 * Loads the ETC2 copy of an atlas the editor writes ({@code name.etc2.atlas}, see
 * {@link jks.tools2d.parallax.heart.Gvars_Parallax#setCompressedAtlases}). Its {@code .zktx} pages carry their whole mip
 * chain, but libGDX's KTXTextureData still calls glGenerateMipmap on a page whose atlas filter is a MipMap one, and
 * OpenGL ES 3 answers that, on a compressed texture, with GL_INVALID_OPERATION: an error a game's glGetError check or
 * GLProfiler trips on. Here those pages are loaded without asking for mipmaps; their filters stay the atlas's.
 */
public final class Utils_Etc2_Atlas
{
	public static final String SUFFIX = ".etc2.atlas";

	private Utils_Etc2_Atlas()
	{}

	public static boolean isEtc2Atlas(String atlasFile)
	{return atlasFile != null && atlasFile.endsWith(SUFFIX);}

	/** The atlas in {@code atlasFile}, its pages next to it; an ETC2 copy's pages without generated mipmaps. */
	public static TextureAtlas load(FileHandle atlasFile)
	{
		TextureAtlasData data = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
		if (isEtc2Atlas(atlasFile.name()))
			for (Page page : data.getPages())
				page.useMipMaps = false;
		return new TextureAtlas(data);
	}

	/** Makes {@code manager} load every {@code .etc2.atlas} through {@link Loader}. */
	public static void register(AssetManager manager)
	{
		if (!(manager.getLoader(TextureAtlas.class, "x" + SUFFIX) instanceof Loader))
			manager.setLoader(TextureAtlas.class, SUFFIX, new Loader(manager.getFileHandleResolver()));
	}

	/** An AssetManager's TextureAtlasLoader whose pages are loaded without generated mipmaps. */
	public static class Loader extends TextureAtlasLoader
	{
		public Loader(FileHandleResolver resolver)
		{super(resolver);}

		@Override
		@SuppressWarnings("rawtypes")
		public Array<AssetDescriptor> getDependencies(String fileName, FileHandle atlasFile, TextureAtlasParameter parameter)
		{
			Array<AssetDescriptor> dependencies = super.getDependencies(fileName, atlasFile, parameter);
			for (AssetDescriptor dependency : dependencies)
				if (dependency.params instanceof TextureParameter)
					((TextureParameter) dependency.params).genMipMaps = false;
			return dependencies;
		}
	}
}
