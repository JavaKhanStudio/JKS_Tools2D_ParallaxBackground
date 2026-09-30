package jks.tools2d.parallax.editor.vue.edition.pixmap;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;

/**
 * Writes an atlas page as ETC2 RGBA8 (GL_COMPRESSED_RGBA8_ETC2_EAC), the compressed format every OpenGL ES 3 GPU reads:
 * one byte a pixel where the PNG's page costs four in video memory (d13). The file is a gzipped KTX ({@code .zktx}),
 * which libGDX's TextureAtlas loads as it loads a PNG page (KTXTextureData).
 * <p>
 * Each 4x4 block is 16 bytes: 8 of EAC alpha, then 8 of ETC colour. The colour encoder only uses ETC1's individual and
 * differential modes (valid ETC2, never the T, H or planar modes), and picks per block the flip, mode, tables and
 * selectors with the least error. Mipmapped pages carry their whole mip chain, averaged 2x2 from the page as the GPU
 * would: a compressed texture cannot have its mipmaps generated.
 */
public final class Etc2
{
	/** GL_COMPRESSED_RGBA8_ETC2_EAC. */
	public static final int GL_COMPRESSED_RGBA8_ETC2_EAC = 0x9278;
	private static final int GL_RGBA = 0x1908;

	private static final int[][] COLOR_TABLES = { { 2, 8 }, { 5, 17 }, { 9, 29 }, { 13, 42 }, { 18, 60 }, { 24, 80 },
			{ 33, 106 }, { 47, 183 } };

	private static final int[][] ALPHA_TABLES = { { -3, -6, -9, -15, 2, 5, 8, 14 }, { -3, -7, -10, -13, 2, 6, 9, 12 },
			{ -2, -5, -8, -13, 1, 4, 7, 12 }, { -2, -4, -6, -13, 1, 3, 5, 12 }, { -3, -6, -8, -12, 2, 5, 7, 11 },
			{ -3, -7, -9, -11, 2, 6, 8, 10 }, { -4, -7, -8, -11, 3, 6, 7, 10 }, { -3, -5, -8, -11, 2, 4, 7, 10 },
			{ -2, -6, -8, -10, 1, 5, 7, 9 }, { -2, -5, -8, -10, 1, 4, 7, 9 }, { -2, -4, -8, -10, 1, 3, 7, 9 },
			{ -2, -5, -7, -10, 1, 4, 6, 9 }, { -3, -4, -7, -10, 2, 3, 6, 9 }, { -1, -2, -3, -10, 0, 1, 2, 9 },
			{ -4, -6, -8, -9, 3, 5, 7, 8 }, { -3, -5, -7, -9, 2, 4, 6, 8 } };

	private Etc2()
	{}

	/** Writes {@code page} (RGBA8888) to {@code file} as a gzipped KTX, with its mip chain when {@code mipmaps}. */
	public static void writeZktx(FileHandle file, Pixmap page, boolean mipmaps) throws IOException
	{
		byte[] ktx = ktx(rgba(page), page.getWidth(), page.getHeight(), mipmaps);
		try (OutputStream stream = file.write(false);
				DataOutputStream out = new DataOutputStream(new GZIPOutputStream(stream, 1 << 16)))
		{
			// libGDX's .zktx: the KTX's length, then the KTX.
			out.writeInt(ktx.length);
			out.write(ktx);
		}
	}

	/** A KTX 1 file holding the image and, when {@code mipmaps}, every level down to 1x1. */
	static byte[] ktx(int[] rgba, int width, int height, boolean mipmaps)
	{
		int levels = 1;
		if (mipmaps)
			for (int side = Math.max(width, height); side > 1; side >>= 1)
				levels++;

		byte[][] data = new byte[levels][];
		int[] level = rgba;
		int levelWidth = width, levelHeight = height;
		for (int i = 0; i < levels; i++)
		{
			data[i] = encode(level, levelWidth, levelHeight);
			if (i + 1 < levels)
			{
				level = halve(level, levelWidth, levelHeight);
				levelWidth = Math.max(1, levelWidth >> 1);
				levelHeight = Math.max(1, levelHeight >> 1);
			}
		}

		int size = 64;
		for (byte[] bytes : data)
			size += 4 + bytes.length;
		ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(new byte[] { (byte) 0xAB, 'K', 'T', 'X', ' ', '1', '1', (byte) 0xBB, '\r', '\n', 0x1A, '\n' });
		buffer.putInt(0x04030201);
		buffer.putInt(0); // glType: compressed
		buffer.putInt(1); // glTypeSize
		buffer.putInt(0); // glFormat: compressed
		buffer.putInt(GL_COMPRESSED_RGBA8_ETC2_EAC);
		buffer.putInt(GL_RGBA);
		buffer.putInt(width);
		buffer.putInt(height);
		buffer.putInt(0); // depth
		buffer.putInt(0); // array elements
		buffer.putInt(1); // faces
		buffer.putInt(levels);
		buffer.putInt(0); // key/value bytes
		for (byte[] bytes : data)
		{
			// Blocks are 16 bytes: every level is already a multiple of 4, no padding.
			buffer.putInt(bytes.length);
			buffer.put(bytes);
		}
		return buffer.array();
	}

	/** The page's pixels as RGBA ints (0xRRGGBBAA, Pixmap's order). */
	static int[] rgba(Pixmap page)
	{
		if (page.getFormat() != Format.RGBA8888)
			throw new IllegalArgumentException("ETC2 export needs an RGBA8888 page, not " + page.getFormat());
		int width = page.getWidth(), height = page.getHeight();
		int[] pixels = new int[width * height];
		ByteBuffer bytes = page.getPixels().duplicate().order(ByteOrder.BIG_ENDIAN);
		bytes.position(0);
		bytes.asIntBuffer().get(pixels);
		return pixels;
	}

	/** The next mip level: each pixel the average of a 2x2 block (a 1-pixel side stays 1). */
	static int[] halve(int[] rgba, int width, int height)
	{
		int halfWidth = Math.max(1, width >> 1), halfHeight = Math.max(1, height >> 1);
		int[] half = new int[halfWidth * halfHeight];
		for (int y = 0; y < halfHeight; y++)
			for (int x = 0; x < halfWidth; x++)
			{
				int x0 = Math.min(x * 2, width - 1), x1 = Math.min(x * 2 + 1, width - 1);
				int y0 = Math.min(y * 2, height - 1), y1 = Math.min(y * 2 + 1, height - 1);
				int a = rgba[y0 * width + x0], b = rgba[y0 * width + x1], c = rgba[y1 * width + x0], d = rgba[y1 * width + x1];
				int pixel = 0;
				for (int shift = 24; shift >= 0; shift -= 8)
				{
					int sum = (a >>> shift & 0xFF) + (b >>> shift & 0xFF) + (c >>> shift & 0xFF) + (d >>> shift & 0xFF);
					pixel |= ((sum + 2) >> 2) << shift;
				}
				half[y * halfWidth + x] = pixel;
			}
		return half;
	}

	/** ETC2 RGBA8 blocks of an image, row of blocks after row of blocks; edges repeat their last pixel. */
	static byte[] encode(int[] rgba, int width, int height)
	{
		int blocksX = (width + 3) / 4, blocksY = (height + 3) / 4;
		byte[] out = new byte[blocksX * blocksY * 16];
		IntStream.range(0, blocksY).parallel().forEach(by ->
		{
			int[] r = new int[16], g = new int[16], b = new int[16], a = new int[16];
			for (int bx = 0; bx < blocksX; bx++)
			{
				// Pixel i of a block is column i / 4, row i % 4: ETC's order.
				for (int i = 0; i < 16; i++)
				{
					int x = Math.min(bx * 4 + i / 4, width - 1), y = Math.min(by * 4 + i % 4, height - 1);
					int pixel = rgba[y * width + x];
					r[i] = pixel >>> 24;
					g[i] = pixel >>> 16 & 0xFF;
					b[i] = pixel >>> 8 & 0xFF;
					a[i] = pixel & 0xFF;
				}
				int offset = (by * blocksX + bx) * 16;
				putLong(out, offset, alphaBlock(a));
				putLong(out, offset + 8, colorBlock(r, g, b));
			}
		});
		return out;
	}

	private static void putLong(byte[] out, int offset, long value)
	{
		for (int i = 0; i < 8; i++)
			out[offset + i] = (byte) (value >>> (56 - 8 * i));
	}

	// ---------------------------------------------------------------- EAC alpha

	static long alphaBlock(int[] a)
	{
		int min = 255, max = 0;
		for (int value : a)
		{
			min = Math.min(min, value);
			max = Math.max(max, value);
		}
		// One value (fully opaque or fully transparent, most blocks): table 13's index 4 adds 0.
		if (min == max)
			return ((long) min << 56) | (1L << 52) | (13L << 48) | repeat(4);

		long best = 0;
		long bestError = Long.MAX_VALUE;
		int range = max - min;
		for (int table = 0; table < 16; table++)
		{
			int[] modifiers = ALPHA_TABLES[table];
			int low = modifiers[3], high = modifiers[7];
			int guess = Math.max(1, Math.min(15, Math.round(range / (float) (high - low))));
			for (int multiplier = Math.max(1, guess - 1); multiplier <= Math.min(15, guess + 1); multiplier++)
			{
				int center = Math.round((min + max) / 2f - (high + low) * multiplier / 2f);
				for (int base = Math.max(0, center - 1); base <= Math.min(255, center + 1); base++)
				{
					long error = 0, indices = 0;
					for (int i = 0; i < 16 && error < bestError; i++)
					{
						int bestIndex = 0, bestPixel = Integer.MAX_VALUE;
						for (int index = 0; index < 8; index++)
						{
							int decoded = clamp(base + modifiers[index] * multiplier);
							int diff = (decoded - a[i]) * (decoded - a[i]);
							if (diff < bestPixel)
							{
								bestPixel = diff;
								bestIndex = index;
							}
						}
						error += bestPixel;
						indices |= (long) bestIndex << (45 - 3 * i);
					}
					if (error < bestError)
					{
						bestError = error;
						best = ((long) base << 56) | ((long) multiplier << 52) | ((long) table << 48) | indices;
					}
				}
			}
		}
		return best;
	}

	private static long repeat(int index)
	{
		long indices = 0;
		for (int i = 0; i < 16; i++)
			indices |= (long) index << (45 - 3 * i);
		return indices;
	}

	// ---------------------------------------------------------------- ETC colour

	/** The best of both flips, each in differential mode when its two base colours allow it, else individual mode. */
	static long colorBlock(int[] r, int[] g, int[] b)
	{
		long best = 0, bestError = Long.MAX_VALUE;
		for (int flip = 0; flip < 2; flip++)
		{
			float[][] averages = new float[2][3];
			for (int i = 0; i < 16; i++)
			{
				int sub = subBlock(i, flip);
				averages[sub][0] += r[i] / 8f;
				averages[sub][1] += g[i] / 8f;
				averages[sub][2] += b[i] / 8f;
			}

			for (int differential = 1; differential >= 0; differential--)
			{
				int[][] bases = new int[2][3];
				int[][] codes = new int[2][3];
				boolean possible = true;
				for (int sub = 0; sub < 2; sub++)
					for (int c = 0; c < 3; c++)
					{
						float value = averages[sub][c];
						codes[sub][c] = differential == 1 ? Math.round(value * 31 / 255f) : Math.round(value * 15 / 255f);
					}
				if (differential == 1)
					for (int c = 0; c < 3; c++)
					{
						int delta = codes[1][c] - codes[0][c];
						possible &= delta >= -4 && delta <= 3;
					}
				if (!possible)
					continue;
				for (int sub = 0; sub < 2; sub++)
					for (int c = 0; c < 3; c++)
						bases[sub][c] = differential == 1 ? codes[sub][c] << 3 | codes[sub][c] >> 2 : codes[sub][c] << 4 | codes[sub][c];

				long error = 0;
				long indices = 0;
				int[] tables = new int[2];
				for (int sub = 0; sub < 2; sub++)
				{
					long subBest = Long.MAX_VALUE;
					long subIndices = 0;
					for (int table = 0; table < 8; table++)
					{
						int[] modifier = COLOR_TABLES[table];
						int[] values = { modifier[0], modifier[1], -modifier[0], -modifier[1] };
						long tableError = 0, tableIndices = 0;
						for (int i = 0; i < 16 && tableError < subBest; i++)
						{
							if (subBlock(i, flip) != sub)
								continue;
							int bestIndex = 0, bestPixel = Integer.MAX_VALUE;
							for (int index = 0; index < 4; index++)
							{
								int dr = clamp(bases[sub][0] + values[index]) - r[i];
								int dg = clamp(bases[sub][1] + values[index]) - g[i];
								int db = clamp(bases[sub][2] + values[index]) - b[i];
								int pixel = dr * dr + dg * dg + db * db;
								if (pixel < bestPixel)
								{
									bestPixel = pixel;
									bestIndex = index;
								}
							}
							tableError += bestPixel;
							tableIndices |= ((long) (bestIndex >> 1) << (16 + i)) | ((long) (bestIndex & 1) << i);
						}
						if (tableError < subBest)
						{
							subBest = tableError;
							subIndices = tableIndices;
							tables[sub] = table;
						}
					}
					error += subBest;
					indices |= subIndices;
				}

				if (error < bestError)
				{
					bestError = error;
					long block;
					if (differential == 1)
						block = ((long) codes[0][0] << 59) | ((long) (codes[1][0] - codes[0][0] & 7) << 56)
								| ((long) codes[0][1] << 51) | ((long) (codes[1][1] - codes[0][1] & 7) << 48)
								| ((long) codes[0][2] << 43) | ((long) (codes[1][2] - codes[0][2] & 7) << 40);
					else
						block = ((long) codes[0][0] << 60) | ((long) codes[1][0] << 56) | ((long) codes[0][1] << 52)
								| ((long) codes[1][1] << 48) | ((long) codes[0][2] << 44) | ((long) codes[1][2] << 40);
					best = block | ((long) tables[0] << 37) | ((long) tables[1] << 34) | ((long) differential << 33)
							| ((long) flip << 32) | indices;
				}
			}
		}
		return best;
	}

	/** Flip 0: two 2x4 halves side by side; flip 1: two 4x2 halves stacked. */
	private static int subBlock(int pixel, int flip)
	{return flip == 0 ? (pixel / 4 < 2 ? 0 : 1) : (pixel % 4 < 2 ? 0 : 1);}

	private static int clamp(int value)
	{return value < 0 ? 0 : value > 255 ? 255 : value;}
}
