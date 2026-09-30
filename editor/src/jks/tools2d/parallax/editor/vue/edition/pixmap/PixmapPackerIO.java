package jks.tools2d.parallax.editor.vue.edition.pixmap;

import java.io.IOException;
import java.util.zip.Deflater;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.math.MathUtils;


/** Writes a {@link PixmapPacker} as a (legacy format) texture atlas. Copied from libGDX, with support for region indices. */
public class PixmapPackerIO 
{
	private static final char INDEX_SEPARATOR = '#';

	/** Name to pack an image under so it is written as region {@code regionName} with {@code index} (-1 for none). */
	public static String packedName(String regionName, int index)
	{return regionName + INDEX_SEPARATOR + index;}

	/** Image formats which can be used when saving a PixmapPacker. */
	public static enum ImageFormat {
		/** A simple compressed image format which is libgdx specific. */
		CIM(".cim"),
		/** A standard compressed image format which is not libgdx specific. */
		PNG(".png");
		
		private final String extension;
		
		/** Returns the file extension for the image format. */
		public String getExtension() {
			return extension;
		}
		
		ImageFormat(String extension) {
			this.extension = extension;
		}
	}
	
	/** Additional parameters which will be used when writing a PixmapPacker. */
	public static class SaveParameters {
		public ImageFormat format = ImageFormat.PNG;
		public TextureFilter minFilter = TextureFilter.Nearest;
		public TextureFilter magFilter = TextureFilter.Nearest;
		/** Gives transparent pixels the colour of their visible neighbours before writing, see {@link ColorBleed}. */
		public boolean bleed;
		/**
		 * Also writes every page as ETC2 ({@link Etc2}, {@code <atlas>_N.zktx}) and {@link #etc2AtlasFile} naming those
		 * pages, region for region the same atlas: a game on an OpenGL ES 3 GPU loads it for a quarter of the memory (d13).
		 */
		public boolean etc2;
	}

	/** {@code name.etc2.atlas}: the ETC2 copy of the atlas {@code name.atlas}. */
	public static FileHandle etc2AtlasFile (FileHandle atlasFile) {
		return atlasFile.sibling(atlasFile.nameWithoutExtension() + ".etc2." + atlasFile.extension());
	}

	/** Saves the provided PixmapPacker to the provided file. The resulting file will use the standard TextureAtlas file format and
	 * can be loaded by TextureAtlas as if it had been created using TexturePacker. Default {@link SaveParameters} will be used.
	 * 
	 * @param file the file to which the atlas descriptor will be written, images will be written as siblings
	 * @param packer the PixmapPacker to be written
	 * @throws IOException if the atlas file can not be written */
	public void save (FileHandle file, PixmapPacker packer) throws IOException {
		save(file, packer, new SaveParameters());
	}
	
	/** Saves the provided PixmapPacker to the provided file. The resulting file will use the standard TextureAtlas file format and
	 * can be loaded by TextureAtlas as if it had been created using TexturePacker.
	 * 
	 * @param file the file to which the atlas descriptor will be written, images will be written as siblings
	 * @param packer the PixmapPacker to be written
	 * @param parameters the SaveParameters specifying how to save the PixmapPacker
	 * @throws IOException if the atlas file can not be written */	
	public void save (FileHandle file, PixmapPacker packer, SaveParameters parameters) throws IOException {
		StringBuilder atlas = new StringBuilder(), etc2Atlas = new StringBuilder();
		write(file, packer, parameters, atlas, etc2Atlas);
		file.writeString(atlas.toString(), false, "UTF-8");
		if (parameters.etc2)
			etc2AtlasFile(file).writeString(etc2Atlas.toString(), false, "UTF-8");
	}

	/**
	 * The part of a page its regions use, from its top-left corner, with the packer's padding kept on the right and
	 * bottom edges too. Every page is allocated at the packer's page size, 4096px or more: written whole, a project of
	 * two small images became a 4096x4096 texture, 64 MB of video memory on every device that loads it. Sides stay
	 * powers of two when the atlas is mipmapped (OpenGL ES 2 and WebGL 1 draw a mipmapped NPOT texture black). Returns
	 * the page's own pixmap when nothing can be cut.
	 */
	static Pixmap trimmed (PixmapPacker packer, Page page, boolean powerOfTwo) {
		int[] size = writtenSize(packer, page, powerOfTwo);
		int width = size[0], height = size[1];
		if (width == page.image.getWidth() && height == page.image.getHeight())
			return page.image;

		Pixmap image = new Pixmap(width, height, page.image.getFormat());
		image.setBlending(Blending.None);
		image.drawPixmap(page.image, 0, 0, 0, 0, width, height);
		return image;
	}

	/** Width and height {@link #trimmed} writes a page at; works on a {@link PixmapPacker#layoutOnly()} packer too. */
	public static int[] writtenSize (PixmapPacker packer, Page page, boolean powerOfTwo) {
		int width = 0, height = 0;
		for (PixmapPacker.PixmapPackerRectangle rect : page.rects.values()) {
			width = Math.max(width, (int)(rect.x + rect.width) + packer.padding);
			height = Math.max(height, (int)(rect.y + rect.height) + packer.padding);
		}
		if (powerOfTwo) {
			width = MathUtils.nextPowerOfTwo(width);
			height = MathUtils.nextPowerOfTwo(height);
		}
		return new int[] { Math.min(width, packer.pageWidth), Math.min(height, packer.pageHeight) };
	}

	private void write (FileHandle file, PixmapPacker packer, SaveParameters parameters, StringBuilder atlas, StringBuilder etc2Atlas)
		throws IOException {
		int index = 0;
		for (Page page : packer.pages) {
			if (page.rects.size > 0) {
				String pageName = file.nameWithoutExtension() + "_" + (++index);
				FileHandle pageFile = file.sibling(pageName + parameters.format.getExtension());
				FileHandle etc2File = file.sibling(pageName + ".zktx");
				Pixmap image = trimmed(packer, page, parameters.minFilter.isMipMap());
				int width = image.getWidth(), height = image.getHeight();
				try {
					if (parameters.bleed)
						ColorBleed.bleed(image);
					switch (parameters.format) {
						case CIM:{
							PixmapIO.writeCIM(pageFile, image);
							break;
						}
						case PNG: {
							PixmapIO.writePNG(pageFile, image, Deflater.BEST_COMPRESSION, false);
							break;
						}
					}
					if (parameters.etc2)
						Etc2.writeZktx(etc2File, image, parameters.minFilter.isMipMap());
				} finally {
					if (image != page.image) image.dispose();
				}
				String body = pageBody(packer, page, parameters, width, height);
				atlas.append("\n").append(pageFile.name()).append("\n").append(body);
				etc2Atlas.append("\n").append(etc2File.name()).append("\n").append(body);
			}
		}
	}

	/** Everything the atlas says of a page after its file name. */
	private static String pageBody (PixmapPacker packer, Page page, SaveParameters parameters, int width, int height) {
		StringBuilder writer = new StringBuilder();
		writer.append("size: " + width + "," + height + "\n");
		writer.append("format: " + packer.pageFormat.name()  + "\n");
		writer.append("filter: " + parameters.minFilter.name() + "," + parameters.magFilter.name() + "\n");
		writer.append("repeat: none" + "\n");
		for (String name : page.rects.keys()) {
			int separator = name.lastIndexOf(INDEX_SEPARATOR);
			String regionName = separator < 0 ? name : name.substring(0, separator);
			String regionIndex = separator < 0 ? "-1" : name.substring(separator + 1);
			writer.append(regionName + "\n");
			PixmapPacker.PixmapPackerRectangle rect = page.rects.get(name);
			writer.append("  rotate: false" + "\n");
			writer.append("  xy: " + (int) rect.x + "," + (int) rect.y + "\n");
			writer.append("  size: " + (int) rect.width + "," + (int) rect.height + "\n");
			if (rect.splits != null) {
				writer.append("  split: " + rect.splits[0] + ", " + rect.splits[1] + ", " + rect.splits[2] + ", " + rect.splits[3] + "\n");
				if (rect.pads != null) {
					writer.append("  pad: " + rect.pads[0] + ", " + rect.pads[1] + ", " + rect.pads[2] + ", " + rect.pads[3] + "\n");
				}
			}
			writer.append("  orig: " + rect.originalWidth + ", " + rect.originalHeight + "\n");
			writer.append("  offset: " + rect.offsetX + ", " + (int)(rect.originalHeight - rect.height - rect.offsetY) + "\n");
			writer.append("  index: " + regionIndex + "\n");
		}
		return writer.toString();
	}

}
