package jks.tools2d.parallax.pages;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * Decodes an atlas page image libGDX cannot (r523): its Pixmap reader is stb_image, which has no WebP. Set one with
 * {@link jks.tools2d.parallax.heart.Gvars_Parallax#setImageDecoder}, and every page whose file ends with
 * {@link #suffix()} is decoded by it, on both ways a page loads (the AssetManager's and a relative path's). The
 * parallax-background-webp artifact's {@code ParallaxWebp.install()} sets the WebP one. A browser game needs none:
 * the browser decodes WebP itself.
 */
public interface PageImageDecoder
{
	/** The file name ending this decoder reads, with its dot, lower case: {@code ".webp"}. */
	String suffix();

	/** The image in {@code file}, in RGBA8888, not premultiplied, as libGDX's own Pixmap reader gives a PNG. */
	Pixmap decode(FileHandle file);
}
