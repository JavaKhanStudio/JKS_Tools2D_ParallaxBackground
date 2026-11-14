package jks.tools2d.parallax.webp;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.DecodedTextureLoader;
import jks.tools2d.parallax.pages.PageImageDecoder;

/**
 * WebP images for a libGDX game (r523), whose own Pixmap reader (stb_image) has none. One call,
 * {@link #install()}, and every page whose atlas pages are {@code .webp} loads, both ways a page loads. On desktop the
 * pixels are decoded in pure Java by TwelveMonkeys imageio-webp (BSD-3, no natives); on Android by the system's own
 * BitmapFactory, and the TwelveMonkeys jars can be left out of the game. iOS has neither: ship PNG there. A browser game
 * needs none of this: the browser decodes WebP.
 * <p>
 * WebP saves disk and download only: a texture is RGBA8888 in video memory whatever its file was.
 */
public final class ParallaxWebp
{
	public static final String SUFFIX = ".webp";

	private static final PageImageDecoder DECODER = new PageImageDecoder()
	{
		@Override
		public String suffix()
		{return SUFFIX;}

		@Override
		public Pixmap decode(FileHandle file)
		{return pixmap(file);}
	};

	private ParallaxWebp()
	{}

	/** Every page's {@code .webp} atlas pages decode through {@link #decoder()} from now on. */
	public static void install()
	{Gvars_Parallax.setImageDecoder(DECODER);}

	/** The WebP decoder, for {@link Gvars_Parallax#setImageDecoder}. */
	public static PageImageDecoder decoder()
	{return DECODER;}

	/** Makes {@code manager} load a {@code .webp} file as a Texture: a game's own images, beside its pages. */
	public static void register(AssetManager manager)
	{manager.setLoader(Texture.class, SUFFIX, new DecodedTextureLoader(manager.getFileHandleResolver(), DECODER));}

	/** The image in {@code file}, lossless or lossy, with or without alpha, as RGBA8888 not premultiplied. */
	public static Pixmap pixmap(FileHandle file)
	{
		if (Gdx.app != null && Gdx.app.getType() == ApplicationType.Android)
			return AndroidWebp.decode(file);
		return DesktopWebp.decode(file);
	}

	/** {@code file} as a texture, without mipmaps. Call on the GL thread. */
	public static Texture texture(FileHandle file)
	{return new Texture(DecodedTextureLoader.data(DECODER, file, null, false));}

	/** ARGB pixels, as Java and Android hand them, into a new RGBA8888 Pixmap. */
	static Pixmap fromArgb(int[] argb, int width, int height)
	{
		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		java.nio.ByteBuffer bytes = pixmap.getPixels();
		bytes.position(0);
		for (int c : argb)
		{
			bytes.put((byte) (c >>> 16));
			bytes.put((byte) (c >>> 8));
			bytes.put((byte) c);
			bytes.put((byte) (c >>> 24));
		}
		bytes.position(0);
		return pixmap;
	}
}
