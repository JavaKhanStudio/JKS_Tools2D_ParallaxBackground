package jks.tools2d.parallax.webp;

import java.io.IOException;
import java.io.InputStream;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.texture.plugins.AWTLoader;

/**
 * WebP images for a jMonkeyEngine game (r552), whose desktop AssetManager has no loader for them: one call,
 * {@link #register(AssetManager)}, and a page whose atlas pages are {@code .webp} loads through
 * {@code JmeAtlas.load}, as do the game's own {@code .webp} textures. Decoded by TwelveMonkeys as on a libGDX desktop,
 * then handed to jME's own {@link AWTLoader}, the loader of its PNG pages: a lossless WebP page is the same jME image as
 * its PNG, flipped or not as its {@link TextureKey} asks. Desktop only (java.awt). jme3-core and jme3-desktop are the
 * game's: this artifact does not bring them, and a jME game without WebP pages does not need this artifact at all.
 */
public final class JmeWebp implements AssetLoader
{
	/** Makes {@code assets} load a {@code .webp} file as a jME image, through this loader. */
	public static void register(AssetManager assets)
	{assets.registerLoader(JmeWebp.class, "webp");}

	@Override
	public Object load(AssetInfo info) throws IOException
	{
		boolean flip = info.getKey() instanceof TextureKey && ((TextureKey) info.getKey()).isFlipY();
		try (InputStream in = info.openStream())
		{return new AWTLoader().load(DesktopWebp.read(in), flip);}
	}
}
