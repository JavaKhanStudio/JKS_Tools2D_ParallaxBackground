package jks.tools2d.parallax.editor.texturepacking;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.editor.texturepacking.TexturePacker.Alias;

/** @author Nathan Sweet */
public class Rect implements Comparable<Rect> 
{
	public String name;
	public int offsetX, offsetY, regionWidth, regionHeight, originalWidth, originalHeight;
	public int x, y;
	public int width, height; // Portion of page taken by this region, including padding.
	public int index;
	public boolean rotated;
	public Set<Alias> aliases = new HashSet<Alias>();
	public int[] splits;
	public int[] pads;
	public boolean canRotate = true;

	private boolean isPatch;
	private BufferedImage image;
	private File file;
	int score1, score2;

	Rect (BufferedImage source, int left, int top, int newWidth, int newHeight, boolean isPatch) {
		image = new BufferedImage(source.getColorModel(),
			source.getRaster().createWritableChild(left, top, newWidth, newHeight, 0, 0, null),
			source.getColorModel().isAlphaPremultiplied(), null);
		offsetX = left;
		offsetY = top;
		regionWidth = newWidth;
		regionHeight = newHeight;
		originalWidth = source.getWidth();
		originalHeight = source.getHeight();
		width = newWidth;
		height = newHeight;
		this.isPatch = isPatch;
	}
	

	/** Clears the image for this rect, which will be loaded from the specified file by {@link #getImage(ImageProcessor)}. */
	public void unloadImage (File file) {
		this.file = file;
		image = null;
	}

	public BufferedImage getImage (ImageProcessor imageProcessor) {
		if (image != null) return image;

		BufferedImage image;
		try {
			image = ImageIO.read(file);
		} catch (IOException ex) {
			throw new RuntimeException("Error reading image: " + file, ex);
		}
		if (image == null) throw new RuntimeException("Unable to read image: " + file);
		String name = this.name;
		if (isPatch) name += ".9";
		return imageProcessor.processImage(image, name).getImage(null);
	}

	Rect () {
	}

	Rect (Rect rect) {
		x = rect.x;
		y = rect.y;
		width = rect.width;
		height = rect.height;
	}

	void set (Rect rect) {
		name = rect.name;
		image = rect.image;
		offsetX = rect.offsetX;
		offsetY = rect.offsetY;
		regionWidth = rect.regionWidth;
		regionHeight = rect.regionHeight;
		originalWidth = rect.originalWidth;
		originalHeight = rect.originalHeight;
		x = rect.x;
		y = rect.y;
		width = rect.width;
		height = rect.height;
		index = rect.index;
		rotated = rect.rotated;
		aliases = rect.aliases;
		splits = rect.splits;
		pads = rect.pads;
		canRotate = rect.canRotate;
		score1 = rect.score1;
		score2 = rect.score2;
		file = rect.file;
		isPatch = rect.isPatch;
	}

	public int compareTo (Rect o) {
		return name.compareTo(o.name);
	}

	@Override
	public boolean equals (Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Rect other = (Rect)obj;
		if (name == null) {
			if (other.name != null) return false;
		} else if (!name.equals(other.name)) return false;
		return true;
	}

	@Override
	public String toString () {
		return name + "[" + x + "," + y + " " + width + "x" + height + "]";
	}

	static public String getAtlasName (String name, boolean flattenPaths) {
		return flattenPaths ? new FileHandle(name).name() : name;
	}
}