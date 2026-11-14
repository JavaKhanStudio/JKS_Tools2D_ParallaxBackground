package jks.tools2d.parallax.pages;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;

/**
 * An AssetManager's Texture loader for the files a {@link PageImageDecoder} reads (r523): libGDX's TextureLoader with
 * the decoder in place of stb_image. The pixels are decoded off the GL thread, the texture made on it.
 */
public class DecodedTextureLoader extends AsynchronousAssetLoader<Texture, TextureParameter>
{
	private final PageImageDecoder decoder;
	private TextureData data;

	public DecodedTextureLoader(FileHandleResolver resolver, PageImageDecoder decoder)
	{
		super(resolver);
		this.decoder = decoder;
	}

	public PageImageDecoder getDecoder()
	{return decoder;}

	/** Whether {@code decoder} reads {@code file}. */
	public static boolean decodes(PageImageDecoder decoder, FileHandle file)
	{return decoder != null && file != null && file.name().toLowerCase().endsWith(decoder.suffix());}

	/** {@code file}'s pixels as texture data, in {@code format} (null: the decoder's), mipmapped if asked. */
	public static TextureData data(PageImageDecoder decoder, FileHandle file, Pixmap.Format format, boolean mipMaps)
	{
		Pixmap pixmap = decoder.decode(file);
		return new PixmapTextureData(pixmap, format, mipMaps, true);
	}

	@Override
	public void loadAsync(AssetManager manager, String fileName, FileHandle file, TextureParameter parameter)
	{
		data = data(decoder, file, parameter == null ? null : parameter.format, parameter != null && parameter.genMipMaps);
		if (!data.isPrepared())
			data.prepare();
	}

	@Override
	public Texture loadSync(AssetManager manager, String fileName, FileHandle file, TextureParameter parameter)
	{
		Texture texture = new Texture(data);
		data = null;
		if (parameter != null)
		{
			texture.setFilter(parameter.minFilter, parameter.magFilter);
			texture.setWrap(parameter.wrapU, parameter.wrapV);
		}
		return texture;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, TextureParameter parameter)
	{return null;}
}
