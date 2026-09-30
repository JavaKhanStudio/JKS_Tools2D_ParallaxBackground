package jks.tools2d.parallax.jme;

import java.io.File;
import java.io.InputStream;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.TextureKey;
import com.jme3.texture.Texture2D;

/**
 * A libGDX {@code .atlas} read through jME's AssetManager: libGDX's own parser ({@link TextureAtlasData}, no GL), and
 * its regions in libGDX's order, so a layer's {@code regionPosition} finds the same image. Each page's texture is a
 * jME {@link Texture2D}, carried by a {@link PageTexture} that stands in for libGDX's GL texture.
 */
public final class JmeAtlas
{
	private JmeAtlas()
	{}

	/** The atlas at {@code atlasPath}, an asset path; its PNG pages are found next to it. */
	public static TextureAtlas load(AssetManager assets, String atlasPath)
	{
		AssetHandle pack = new AssetHandle(assets, atlasPath);
		TextureAtlasData data = new TextureAtlasData(pack, pack.parent(), false);
		for (TextureAtlasData.Page page : data.getPages())
			page.texture = new PageTexture(loadTexture(assets, ((AssetHandle) page.textureFile).path, page), (int) page.width, (int) page.height);
		// Builds the AtlasRegions exactly as libGDX does, over the textures set above.
		return new TextureAtlas(data);
	}

	/** Unflipped, as libGDX uploads it: the atlas's v coordinates then need no conversion. */
	private static Texture2D loadTexture(AssetManager assets, String path, TextureAtlasData.Page page)
	{
		TextureKey key = new TextureKey(path, false);
		key.setGenerateMips(page.useMipMaps);
		Texture2D texture = (Texture2D) assets.loadTexture(key);
		texture.setMinFilter(minFilter(page.minFilter));
		texture.setMagFilter(page.magFilter == TextureFilter.Nearest ? com.jme3.texture.Texture.MagFilter.Nearest
				: com.jme3.texture.Texture.MagFilter.Bilinear);
		texture.setWrap(com.jme3.texture.Texture.WrapAxis.S, page.uWrap == TextureWrap.Repeat
				? com.jme3.texture.Texture.WrapMode.Repeat : com.jme3.texture.Texture.WrapMode.EdgeClamp);
		texture.setWrap(com.jme3.texture.Texture.WrapAxis.T, page.vWrap == TextureWrap.Repeat
				? com.jme3.texture.Texture.WrapMode.Repeat : com.jme3.texture.Texture.WrapMode.EdgeClamp);
		texture.setAnisotropicFilter(1);
		return texture;
	}

	/** The GL minification filter libGDX sets, under jME's name for it. */
	private static com.jme3.texture.Texture.MinFilter minFilter(TextureFilter filter)
	{
		switch (filter)
		{
			case Nearest:
				return com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
			case Linear:
				return com.jme3.texture.Texture.MinFilter.BilinearNoMipMaps;
			case MipMapNearestNearest:
				return com.jme3.texture.Texture.MinFilter.NearestNearestMipMap;
			case MipMapLinearNearest:
				return com.jme3.texture.Texture.MinFilter.BilinearNearestMipMap;
			case MipMapNearestLinear:
				return com.jme3.texture.Texture.MinFilter.NearestLinearMipMap;
			default: // MipMap, MipMapLinearLinear
				return com.jme3.texture.Texture.MinFilter.Trilinear;
		}
	}

	/**
	 * Stands in for libGDX's texture in its regions: the size their u/v are computed from, and the jME texture
	 * {@link JmeBatch} draws. It never touches GL, so setting a filter or a wrap on it does nothing.
	 */
	public static final class PageTexture extends Texture
	{
		public final Texture2D texture;
		private final int width, height;

		public PageTexture(Texture2D texture, int width, int height)
		{
			this.texture = texture;
			this.width = width > 0 ? width : texture.getImage().getWidth();
			this.height = height > 0 ? height : texture.getImage().getHeight();
		}

		@Override
		public int getWidth()
		{return width;}

		@Override
		public int getHeight()
		{return height;}

		@Override
		public void setFilter(TextureFilter minFilter, TextureFilter magFilter)
		{}

		@Override
		public void setWrap(TextureWrap u, TextureWrap v)
		{}

		@Override
		public void dispose()
		{}
	}

	/** A libGDX file handle over a jME asset path: what TextureAtlasData reads the atlas and names its pages with. */
	static final class AssetHandle extends FileHandle
	{
		private final AssetManager assets;
		final String path;

		AssetHandle(AssetManager assets, String path)
		{
			this.assets = assets;
			this.path = path.replace('\\', '/');
			this.file = new File(this.path);
			this.type = FileType.Classpath;
		}

		@Override
		public InputStream read()
		{
			AssetInfo info = assets.locateAsset(new AssetKey<>(path));
			if (info == null)
				throw new AssetNotFoundException(path);
			return info.openStream();
		}

		@Override
		public FileHandle child(String name)
		{return new AssetHandle(assets, path.isEmpty() ? name : path + "/" + name);}

		@Override
		public FileHandle parent()
		{
			int slash = path.lastIndexOf('/');
			return new AssetHandle(assets, slash < 0 ? "" : path.substring(0, slash));
		}

		@Override
		public String path()
		{return path;}
	}
}
