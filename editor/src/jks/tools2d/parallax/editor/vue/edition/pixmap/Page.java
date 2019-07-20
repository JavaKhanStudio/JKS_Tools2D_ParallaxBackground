package jks.tools2d.parallax.editor.vue.edition.pixmap;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.OrderedMap;

import jks.tools2d.parallax.editor.vue.edition.pixmap.PixmapPacker.PixmapPackerRectangle;

public class Page 
{
	OrderedMap<String, PixmapPackerRectangle> rects = new OrderedMap();
	Pixmap image;
	Texture texture;
	final Array<String> addedRects = new Array();
	boolean dirty;

	/** Creates a new page filled with the color provided by the {@link PixmapPacker#getTransparentColor()} */
	public Page (PixmapPacker packer) {
		image = new Pixmap(packer.pageWidth, packer.pageHeight, packer.pageFormat);
		final Color transparentColor = packer.getTransparentColor();
		this.image.setColor(transparentColor);
		this.image.fill();
	}

	public Pixmap getPixmap () {
		return image;
	}

	public OrderedMap<String, PixmapPackerRectangle> getRects () {
		return rects;
	}

	/** Returns the texture for this page, or null if the texture has not been created.
	 * @see #updateTexture(TextureFilter, TextureFilter, boolean) */
	public Texture getTexture () {
		return texture;
	}

	/** Creates the texture if it has not been created, else reuploads the entire page pixmap to the texture if the pixmap has
	 * changed since this method was last called.
	 * @return true if the texture was created or reuploaded. */
	public boolean updateTexture (TextureFilter minFilter, TextureFilter magFilter, boolean useMipMaps) {
		if (texture != null) {
			if (!dirty) return false;
			texture.load(texture.getTextureData());
		} else {
			texture = new Texture(new PixmapTextureData(image, image.getFormat(), useMipMaps, false, true)) {
				@Override
				public void dispose () {
					super.dispose();
					image.dispose();
				}
			};
			texture.setFilter(minFilter, magFilter);
		}
		dirty = false;
		return true;
	}
}
