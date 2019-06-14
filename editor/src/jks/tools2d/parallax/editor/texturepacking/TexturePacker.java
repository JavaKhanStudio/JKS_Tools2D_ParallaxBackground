package jks.tools2d.parallax.editor.texturepacking;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Comparator;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.tools.texturepacker.ColorBleedEffect;
import com.badlogic.gdx.tools.texturepacker.TexturePacker.ProgressListener;
import com.badlogic.gdx.tools.texturepacker.TexturePacker.Settings;
import com.badlogic.gdx.tools.texturepacker.TexturePackerFileProcessor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;

/** @author Nathan Sweet */
public class TexturePacker {
	private final Settings settings;
	private final Packer packer;
	private final ImageProcessor imageProcessor;
	private final Array<InputImage> inputImages = new Array();
	private File rootDir;

	/** @param rootDir Used to strip the root directory prefix from image file names, can be null. */
	public TexturePacker (File rootDir, Settings settings) {
		this.rootDir = rootDir;
		this.settings = settings;

		if (settings.pot) {
			if (settings.maxWidth != MathUtils.nextPowerOfTwo(settings.maxWidth))
				throw new RuntimeException("If pot is true, maxWidth must be a power of two: " + settings.maxWidth);
			if (settings.maxHeight != MathUtils.nextPowerOfTwo(settings.maxHeight))
				throw new RuntimeException("If pot is true, maxHeight must be a power of two: " + settings.maxHeight);
		}

		if (settings.grid)
			packer = new GridPacker(settings);
		else
			packer = new MaxRectsPacker(settings);
		imageProcessor = new ImageProcessor(rootDir, settings);
	}

	public TexturePacker (Settings settings) {
		this(null, settings);
	}

	public void addImage (File file) {
		InputImage inputImage = new InputImage();
		inputImage.file = file;
		inputImages.add(inputImage);
	}

	public void addImage (BufferedImage image, String name) {
		InputImage inputImage = new InputImage();
		inputImage.image = image;
		inputImage.name = name;
		inputImages.add(inputImage);
	}
	
	public void addImage (Texture texture, String name) {
		InputImage inputImage = new InputImage();
		inputImage.texture = texture;
		inputImage.name = name;
		inputImages.add(inputImage);
	}
	
	

	public void pack (File outputDir, String packFileName) {
		if (packFileName.endsWith(settings.atlasExtension))
			packFileName = packFileName.substring(0, packFileName.length() - settings.atlasExtension.length());
		outputDir.mkdirs();

		for (int i = 0, n = settings.scale.length; i < n; i++) 
		{
			imageProcessor.setScale(settings.scale[i]);
			for (InputImage inputImage : inputImages) 
			{
				if (inputImage.file != null)
					imageProcessor.addImage(inputImage.file);
				else if(inputImage.image != null)
					imageProcessor.addImage(inputImage.image, inputImage.name);
				else if(inputImage.texture != null)
					imageProcessor.addImage(inputImage.image, inputImage.name);
			}

			Array<Page> pages = packer.pack(imageProcessor.getImages());

			String scaledPackFileName = settings.getScaledPackFileName(packFileName, i);
			writeImages(outputDir, scaledPackFileName, pages);
			try {
				writePackFile(outputDir, scaledPackFileName, pages);
			} catch (IOException ex) {
				throw new RuntimeException("Error writing pack file.", ex);
			}
			imageProcessor.clear();
		}
	}

	
	private void writeImages (File outputDir, String scaledPackFileName, Array<Page> pages) {
		File packFileNoExt = new File(outputDir, scaledPackFileName);
		File packDir = packFileNoExt.getParentFile();
		String imageName = packFileNoExt.getName();

		int fileIndex = 0;
		for (Page page : pages) {
			int width = page.width, height = page.height;
			int paddingX = settings.paddingX;
			int paddingY = settings.paddingY;
			if (settings.duplicatePadding) {
				paddingX /= 2;
				paddingY /= 2;
			}
			width -= settings.paddingX;
			height -= settings.paddingY;
			if (settings.edgePadding) {
				page.x = paddingX;
				page.y = paddingY;
				width += paddingX * 2;
				height += paddingY * 2;
			}
			if (settings.pot) {
				width = MathUtils.nextPowerOfTwo(width);
				height = MathUtils.nextPowerOfTwo(height);
			}
			width = Math.max(settings.minWidth, width);
			height = Math.max(settings.minHeight, height);
			page.imageWidth = width;
			page.imageHeight = height;

			File outputFile;
			while (true) {
				outputFile = new File(packDir, imageName + (fileIndex++ == 0 ? "" : fileIndex) + "." + settings.outputFormat);
				if (!outputFile.exists()) break;
			}
			new FileHandle(outputFile).parent().mkdirs();
			page.imageName = outputFile.getName();

			BufferedImage canvas = new BufferedImage(width, height, getBufferedImageType(settings.format));
			Graphics2D g = (Graphics2D)canvas.getGraphics();

			if (!settings.silent) System.out.println("Writing " + canvas.getWidth() + "x" + canvas.getHeight() + ": " + outputFile);

			for (Rect rect : page.outputRects) {
				BufferedImage image = rect.getImage(imageProcessor);
				int iw = image.getWidth();
				int ih = image.getHeight();
				int rectX = page.x + rect.x, rectY = page.y + page.height - rect.y - rect.height;
				if (settings.duplicatePadding) {
					int amountX = settings.paddingX / 2;
					int amountY = settings.paddingY / 2;
					if (rect.rotated) {
						// Copy corner pixels to fill corners of the padding.
						for (int i = 1; i <= amountX; i++) {
							for (int j = 1; j <= amountY; j++) {
								plot(canvas, rectX - j, rectY + iw - 1 + i, image.getRGB(0, 0));
								plot(canvas, rectX + ih - 1 + j, rectY + iw - 1 + i, image.getRGB(0, ih - 1));
								plot(canvas, rectX - j, rectY - i, image.getRGB(iw - 1, 0));
								plot(canvas, rectX + ih - 1 + j, rectY - i, image.getRGB(iw - 1, ih - 1));
							}
						}
						// Copy edge pixels into padding.
						for (int i = 1; i <= amountY; i++) {
							for (int j = 0; j < iw; j++) {
								plot(canvas, rectX - i, rectY + iw - 1 - j, image.getRGB(j, 0));
								plot(canvas, rectX + ih - 1 + i, rectY + iw - 1 - j, image.getRGB(j, ih - 1));
							}
						}
						for (int i = 1; i <= amountX; i++) {
							for (int j = 0; j < ih; j++) {
								plot(canvas, rectX + j, rectY - i, image.getRGB(iw - 1, j));
								plot(canvas, rectX + j, rectY + iw - 1 + i, image.getRGB(0, j));
							}
						}
					} else {
						// Copy corner pixels to fill corners of the padding.
						for (int i = 1; i <= amountX; i++) {
							for (int j = 1; j <= amountY; j++) {
								plot(canvas, rectX - i, rectY - j, image.getRGB(0, 0));
								plot(canvas, rectX - i, rectY + ih - 1 + j, image.getRGB(0, ih - 1));
								plot(canvas, rectX + iw - 1 + i, rectY - j, image.getRGB(iw - 1, 0));
								plot(canvas, rectX + iw - 1 + i, rectY + ih - 1 + j, image.getRGB(iw - 1, ih - 1));
							}
						}
						// Copy edge pixels into padding.
						for (int i = 1; i <= amountY; i++) {
							copy(image, 0, 0, iw, 1, canvas, rectX, rectY - i, rect.rotated);
							copy(image, 0, ih - 1, iw, 1, canvas, rectX, rectY + ih - 1 + i, rect.rotated);
						}
						for (int i = 1; i <= amountX; i++) {
							copy(image, 0, 0, 1, ih, canvas, rectX - i, rectY, rect.rotated);
							copy(image, iw - 1, 0, 1, ih, canvas, rectX + iw - 1 + i, rectY, rect.rotated);
						}
					}
				}
				copy(image, 0, 0, iw, ih, canvas, rectX, rectY, rect.rotated);
				if (settings.debug) {
					g.setColor(Color.magenta);
					g.drawRect(rectX, rectY, rect.width - settings.paddingX - 1, rect.height - settings.paddingY - 1);
				}
			}

			if (settings.bleed && !settings.premultiplyAlpha
				&& !(settings.outputFormat.equalsIgnoreCase("jpg") || settings.outputFormat.equalsIgnoreCase("jpeg"))) {
				canvas = new ColorBleedEffect().processImage(canvas, settings.bleedIterations);
				g = (Graphics2D)canvas.getGraphics();
			}

			if (settings.debug) {
				g.setColor(Color.magenta);
				g.drawRect(0, 0, width - 1, height - 1);
			}

			ImageOutputStream ios = null;
			try {
				if (settings.outputFormat.equalsIgnoreCase("jpg") || settings.outputFormat.equalsIgnoreCase("jpeg")) {
					BufferedImage newImage = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
					newImage.getGraphics().drawImage(canvas, 0, 0, null);
					canvas = newImage;

					Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
					ImageWriter writer = writers.next();
					ImageWriteParam param = writer.getDefaultWriteParam();
					param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					param.setCompressionQuality(settings.jpegQuality);
					ios = ImageIO.createImageOutputStream(outputFile);
					writer.setOutput(ios);
					writer.write(null, new IIOImage(canvas, null, null), param);
				} else {
					if (settings.premultiplyAlpha) canvas.getColorModel().coerceData(canvas.getRaster(), true);
					ImageIO.write(canvas, "png", outputFile);
				}
			} catch (IOException ex) {
				throw new RuntimeException("Error writing file: " + outputFile, ex);
			} finally {
				if (ios != null) {
					try {
						ios.close();
					} catch (Exception ignored) {
					}
				}
			}
		}
	}

	static private void plot (BufferedImage dst, int x, int y, int argb) {
		if (0 <= x && x < dst.getWidth() && 0 <= y && y < dst.getHeight()) dst.setRGB(x, y, argb);
	}

	static private void copy (BufferedImage src, int x, int y, int w, int h, BufferedImage dst, int dx, int dy, boolean rotated) {
		if (rotated) {
			for (int i = 0; i < w; i++)
				for (int j = 0; j < h; j++)
					plot(dst, dx + j, dy + w - i - 1, src.getRGB(x + i, y + j));
		} else {
			for (int i = 0; i < w; i++)
				for (int j = 0; j < h; j++)
					plot(dst, dx + i, dy + j, src.getRGB(x + i, y + j));
		}
	}

	private void writePackFile (File outputDir, String scaledPackFileName, Array<Page> pages) throws IOException {
		File packFile = new File(outputDir, scaledPackFileName + settings.atlasExtension);
		File packDir = packFile.getParentFile();
		packDir.mkdirs();

		if (packFile.exists()) {
			// Make sure there aren't duplicate names.
			TextureAtlasData textureAtlasData = new TextureAtlasData(new FileHandle(packFile), new FileHandle(packFile), false);
			for (Page page : pages) {
				for (Rect rect : page.outputRects) {
					String rectName = Rect.getAtlasName(rect.name, settings.flattenPaths);
					for (Region region : textureAtlasData.getRegions()) {
						if (region.name.equals(rectName)) {
							throw new GdxRuntimeException(
								"A region with the name \"" + rectName + "\" has already been packed: " + rect.name);
						}
					}
				}
			}
		}

		Writer writer = new OutputStreamWriter(new FileOutputStream(packFile, true), "UTF-8");
		for (Page page : pages) {
			writer.write("\n" + page.imageName + "\n");
			writer.write("size: " + page.imageWidth + "," + page.imageHeight + "\n");
			writer.write("format: " + settings.format + "\n");
			writer.write("filter: " + settings.filterMin + "," + settings.filterMag + "\n");
			writer.write("repeat: " + getRepeatValue() + "\n");

			page.outputRects.sort();
			for (Rect rect : page.outputRects) {
				writeRect(writer, page, rect, rect.name);
				Array<Alias> aliases = new Array(rect.aliases.toArray());
				aliases.sort();
				for (Alias alias : aliases) {
					Rect aliasRect = new Rect();
					aliasRect.set(rect);
					alias.apply(aliasRect);
					writeRect(writer, page, aliasRect, alias.name);
				}
			}
		}
		writer.close();
	}

	private void writeRect (Writer writer, Page page, Rect rect, String name) throws IOException {
		writer.write(Rect.getAtlasName(name, settings.flattenPaths) + "\n");
		writer.write("  rotate: " + rect.rotated + "\n");
		writer.write("  xy: " + (page.x + rect.x) + ", " + (page.y + page.height - rect.height - rect.y) + "\n");

		writer.write("  size: " + rect.regionWidth + ", " + rect.regionHeight + "\n");
		if (rect.splits != null) {
			writer.write("  split: " //
				+ rect.splits[0] + ", " + rect.splits[1] + ", " + rect.splits[2] + ", " + rect.splits[3] + "\n");
		}
		if (rect.pads != null) {
			if (rect.splits == null) writer.write("  split: 0, 0, 0, 0\n");
			writer.write("  pad: " + rect.pads[0] + ", " + rect.pads[1] + ", " + rect.pads[2] + ", " + rect.pads[3] + "\n");
		}
		writer.write("  orig: " + rect.originalWidth + ", " + rect.originalHeight + "\n");
		writer.write("  offset: " + rect.offsetX + ", " + (rect.originalHeight - rect.regionHeight - rect.offsetY) + "\n");
		writer.write("  index: " + rect.index + "\n");
	}

	private String getRepeatValue () {
		if (settings.wrapX == TextureWrap.Repeat && settings.wrapY == TextureWrap.Repeat) return "xy";
		if (settings.wrapX == TextureWrap.Repeat && settings.wrapY == TextureWrap.ClampToEdge) return "x";
		if (settings.wrapX == TextureWrap.ClampToEdge && settings.wrapY == TextureWrap.Repeat) return "y";
		return "none";
	}

	private int getBufferedImageType (Format format) {
		switch (settings.format) {
		case RGBA8888:
		case RGBA4444:
			return BufferedImage.TYPE_INT_ARGB;
		case RGB565:
		case RGB888:
			return BufferedImage.TYPE_INT_RGB;
		case Alpha:
			return BufferedImage.TYPE_BYTE_GRAY;
		default:
			throw new RuntimeException("Unsupported format: " + settings.format);
		}
	}



	/** @author Regnarock
	 * @author Nathan Sweet */
	static public class Alias implements Comparable<Alias> {
		public String name;
		public int index;
		public int[] splits;
		public int[] pads;
		public int offsetX, offsetY, originalWidth, originalHeight;

		public Alias (Rect rect) {
			name = rect.name;
			index = rect.index;
			splits = rect.splits;
			pads = rect.pads;
			offsetX = rect.offsetX;
			offsetY = rect.offsetY;
			originalWidth = rect.originalWidth;
			originalHeight = rect.originalHeight;
		}

		public void apply (Rect rect) {
			rect.name = name;
			rect.index = index;
			rect.splits = splits;
			rect.pads = pads;
			rect.offsetX = offsetX;
			rect.offsetY = offsetY;
			rect.originalWidth = originalWidth;
			rect.originalHeight = originalHeight;
		}

		public int compareTo (Alias o) {
			return name.compareTo(o.name);
		}
	}

	

	
	/** Packs using defaults settings.
	 * @see TexturePacker#process(Settings, String, String, String) */
	static public void process (String input, String output, String packFileName) {
		process(new Settings(), input, output, packFileName);
	}

	/** @param input Directory containing individual images to be packed.
	 * @param output Directory where the pack file and page images will be written.
	 * @param packFileName The name of the pack file. Also used to name the page images. */
	static public void process (Settings settings, String input, String output, String packFileName) {
		try {
			TexturePackerFileProcessor processor = new TexturePackerFileProcessor(settings, packFileName, null);
			// Sort input files by name to avoid platform-dependent atlas output changes.
			processor.setComparator(new Comparator<File>() {
				public int compare (File file1, File file2) {
					return file1.getName().compareTo(file2.getName());
				}
			});
			processor.process(new File(input), new File(output));
		} catch (Exception ex) {
			throw new RuntimeException("Error packing images.", ex);
		}
	}

	/** @return true if the output file does not yet exist or its last modification date is before the last modification date of
	 *         the input file */
	static public boolean isModified (String input, String output, String packFileName, Settings settings) {
		String packFullFileName = output;

		if (!packFullFileName.endsWith("/")) {
			packFullFileName += "/";
		}

		// Check against the only file we know for sure will exist and will be changed if any asset changes:
		// the atlas file
		packFullFileName += packFileName;
		packFullFileName += settings.atlasExtension;
		File outputFile = new File(packFullFileName);

		if (!outputFile.exists()) {
			return true;
		}

		File inputFile = new File(input);
		if (!inputFile.exists()) {
			throw new IllegalArgumentException("Input file does not exist: " + inputFile.getAbsolutePath());
		}

		return isModified(inputFile, outputFile.lastModified());
	}

	static private boolean isModified (File file, long lastModified) {
		if (file.lastModified() > lastModified) return true;
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children)
				if (isModified(child, lastModified)) return true;
		}
		return false;
	}

	static public boolean processIfModified (String input, String output, String packFileName) {
		// Default settings (Needed to access the default atlas extension string)
		Settings settings = new Settings();

		if (isModified(input, output, packFileName, settings)) {
			process(settings, input, output, packFileName);
			return true;
		}
		return false;
	}

	static public boolean processIfModified (Settings settings, String input, String output, String packFileName) {
		if (isModified(input, output, packFileName, settings)) {
			process(settings, input, output, packFileName);
			return true;
		}
		return false;
	}


	static final class InputImage {
		File file;
		String name;
		BufferedImage image;
		Texture texture ; 
	}

	static public void main (String[] args) throws Exception {
		Settings settings = null;
		String input = null, output = null, packFileName = "pack.atlas";

		switch (args.length) {
		case 4:
			settings = new Json().fromJson(Settings.class, new FileReader(args[3]));
		case 3:
			packFileName = args[2];
		case 2:
			output = args[1];
		case 1:
			input = args[0];
			break;
		default:
			System.out.println("Usage: inputDir [outputDir] [packFileName] [settingsFileName]");
			System.exit(0);
		}

		if (output == null) {
			File inputFile = new File(input);
			output = new File(inputFile.getParentFile(), inputFile.getName() + "-packed").getAbsolutePath();
		}
		if (settings == null) settings = new Settings();

		process(settings, input, output, packFileName);
	}
}
