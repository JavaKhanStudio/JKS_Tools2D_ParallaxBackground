package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * Where the layers in front of a LURKER layer cover its figure wholly: a page's {@link Parallax_Model#lurkerCover},
 * worked out once on the CPU from the atlas images (docs/lurker-layers.md, "Where it is covered"). The editor calls
 * {@link #bake} when it saves; the rounds' tools through tools/lurker-cover.sh.
 * <p>
 * On a page repeating on X, the figure's place against a nearer layer's tiles depends only on their relative offset
 * {@code d}: the figure's tile corner minus the nearer layer's, modulo that layer's period P ({@link
 * ParallaxLayer#getTotalWidth}). For each nearer IMAGE layer, this sweeps d over one period and keeps the intervals where
 * that layer's pixels of alpha at least {@link #OPAQUE} cover the figure's box (its pixels of alpha above 0), grown on
 * every side by {@link #MARGIN} of its height for the camera's Y and zoom. Only one layer covering it whole counts. The
 * layers are taken at rest, at their decal: their Y apart is the one they are saved with.
 * <p>
 * A nearer SEQUENCE layer's slots hold the segments its seed picks, and a game may draw its cycle from another seed: it
 * covers the figure only where every segment it can pick (weight above 0) would, swept in one slot and laid at each
 * slot of the cycle (r385). Its segments must then be as wide as each other, or the slots move with the seed: one of
 * another width leaves that layer out.
 * <p>
 * The result is triplets (how many layers in front of the lurker that layer is, from, to), from in [0, P) and to at
 * least from, possibly past P: the figure is covered at offset d when {@code ((d - from) mod P + P) mod P <= to - from}.
 * A layer covering every offset gives (n, 0, P). World units of the world the layers were built for: padX is not scaled
 * with it, so a reader in another world works it out again.
 */
public final class LurkerCover
{
	/** The alpha a nearer layer's pixel needs to hide what is behind it. */
	public static final int OPAQUE = 250;
	/** How far the figure's box is grown on every side, in fractions of its height. */
	public static final float MARGIN = 0.04f;

	/** An atlas page's alpha at pixel {@code (x, y)}, y down from its top as in the atlas, 0 to 255; below 0: no image. */
	public interface Alpha
	{
		int at(Texture page, int x, int y);
	}

	/** The pages' alpha read from their Pixmaps (RGBA8888 or any format getPixel answers in); a page not in it has none. */
	public static Alpha of(Map<Texture, Pixmap> pixmaps)
	{
		return (page, x, y) ->
		{
			Pixmap pixmap = pixmaps.get(page);
			return pixmap == null ? -1 : pixmap.getPixel(x, y) & 0xff;
		};
	}

	private LurkerCover()
	{}

	/**
	 * Sets every LURKER layer's {@link Parallax_Model#lurkerCover} of {@code page} from {@code layers}, the page's layers
	 * built for the world it is drawn in (the reader's {@code getDrawing}, or {@link WholePage_Model#buildDrawing}), one
	 * per model. Their positions are not read nor changed.
	 */
	public static void bake(WholePage_Model page, List<ParallaxLayer> layers, Alpha alpha)
	{bake(page, layers, alpha, OPAQUE, MARGIN);}

	/** The same with another alpha and margin ({@link #cover(List, int, boolean, boolean, Alpha, int, float)}). */
	public static void bake(WholePage_Model page, List<ParallaxLayer> layers, Alpha alpha, int opaque, float margin)
	{
		List<Parallax_Model> models = page.pageModel.pageList;
		for (int i = 0; i < models.size() && i < layers.size(); i++)
			if (models.get(i).kind == Enum_LayerKind.LURKER)
				models.get(i).lurkerCover = cover(layers, i, page.repeatOnX, page.repeatOnY, alpha, opaque, margin);
	}

	/**
	 * The cover triplets of the layer {@code lurker} of {@code layers} (back to front) on a page repeating as given. None
	 * when the page does not repeat on X, the figure has no pixel, or an image the sweep needs is missing.
	 */
	public static float[] cover(List<ParallaxLayer> layers, int lurker, boolean repeatOnX, boolean repeatOnY, Alpha alpha)
	{return cover(layers, lurker, repeatOnX, repeatOnY, alpha, OPAQUE, MARGIN);}

	/**
	 * The same with another alpha a covering pixel needs, and another margin, in fractions of the figure's height: what
	 * the tools measure the defaults against (tools/lurker-cover.sh's LURKER_OPAQUE and LURKER_MARGIN).
	 */
	public static float[] cover(List<ParallaxLayer> layers, int lurker, boolean repeatOnX, boolean repeatOnY, Alpha alpha, int opaque,
			float margin)
	{
		ParallaxLayer figure = layers.get(lurker);
		if (!repeatOnX || !figure.drawsImage())
			return new float[0];
		try
		{
			float[] box = figureBox(figure, alpha);
			if (box == null)
				return new float[0];
			float grow = margin * (box[3] - box[2]);
			float left = box[0] - grow, right = box[1] + grow, bottom = box[2] - grow, top = box[3] + grow;

			List<Float> out = new ArrayList<>();
			for (int n = 1; lurker + n < layers.size(); n++)
			{
				ParallaxLayer near = layers.get(lurker + n);
				float period = near.getTotalWidth();
				List<Tile> tiles = tilesOf(near);
				if (tiles == null || near.isTower() || !(period > 0))
					continue;
				// A SEQUENCE layer's runs are swept in one slot, repeated as many times as the cycle has slots.
				int slots = near.kind == Enum_LayerKind.SEQUENCE ? near.getSequenceLength() : 1;
				float step = period / slots;
				float apart = restY(figure) - restY(near);
				float[] runs = coveredRuns(near, tiles, step, apart + bottom, apart + top, repeatOnY, alpha, opaque);
				for (int r = 0; r < runs.length; r += 2)
				{
					if (runs[r + 1] - runs[r] > step)
					{
						// Covered all along: every offset.
						out.add((float) n);
						out.add(0f);
						out.add(period);
						continue;
					}
					float from = runs[r] - left, to = runs[r + 1] - right;
					if (to < from)
						continue;
					float turns = (float) Math.floor(from / step);
					from -= turns * step;
					to -= turns * step;
					for (int slot = 0; slot < slots; slot++)
					{
						out.add((float) n);
						out.add(from + slot * step);
						out.add(to + slot * step);
					}
				}
			}
			float[] cover = new float[out.size()];
			for (int i = 0; i < cover.length; i++)
				cover[i] = out.get(i);
			return cover;
		}
		catch (MissingImage missing)
		{return new float[0];}
	}

	/** A layer's Y at rest, as {@link ParallaxLayer#resetPosition} places it: what the reader draws its tile corner from. */
	private static float restY(ParallaxLayer layer)
	{return layer.getDecalPercentY() * (layer.getWorldHeight() / 100);}

	/** One image a layer draws in a box of its own: its region, the box's size, and where the packed image sits in it. */
	private static final class Tile
	{
		final TextureRegion region;
		final float width, height, trimLeft, trimBottom, packedWidth, packedHeight;

		Tile(TextureRegion region, float width, float height, float trimLeft, float trimBottom, float packedWidth, float packedHeight)
		{
			this.region = region;
			this.width = width;
			this.height = height;
			this.trimLeft = trimLeft;
			this.trimBottom = trimBottom;
			this.packedWidth = packedWidth;
			this.packedHeight = packedHeight;
		}

		/** An IMAGE or LURKER layer's one image. */
		static Tile of(ParallaxLayer layer)
		{
			return new Tile(layer.getRegion(), layer.getWidth(), layer.getHeight(), layer.getTrimLeft(), layer.getTrimBottom(),
					layer.getPackedWidthRatio(), layer.getPackedHeightRatio());
		}
	}

	/**
	 * The images a nearer layer can draw in one tile or slot: an IMAGE layer's one, each segment a SEQUENCE layer can
	 * pick; null when it covers nothing (another kind, or a SEQUENCE whose segments are not as wide as each other).
	 */
	private static List<Tile> tilesOf(ParallaxLayer near)
	{
		List<Tile> tiles = new ArrayList<>();
		if (near.kind == Enum_LayerKind.IMAGE)
			tiles.add(Tile.of(near));
		else if (near.kind == Enum_LayerKind.SEQUENCE && near.getSequenceLength() > 0)
		{
			List<TextureRegion> regions = near.getTexRegion();
			boolean anyWeighs = false;
			for (int i = 0; i < regions.size(); i++)
				anyWeighs |= near.getSequenceSegments().get(i).weight > 0;
			float height = near.getHeight(), aspect = -1;
			for (int i = 0; i < regions.size(); i++)
			{
				if (anyWeighs && near.getSequenceSegments().get(i).weight <= 0)
					continue;
				float[] box = near.getSegmentBox(i);
				if (aspect < 0)
					aspect = box[0];
				else if (Math.abs(box[0] - aspect) > aspect * 1e-4f)
					return null;
				tiles.add(new Tile(regions.get(i), height * box[0], height, box[1], box[2], box[3], box[4]));
			}
		}
		return tiles.isEmpty() ? null : tiles;
	}

	/** Where a tile's packed image is drawn in its box: left, bottom, width, height. */
	private static float[] imageBox(Tile tile, boolean fx, boolean fy)
	{
		return new float[] { tile.width * (fx ? 1 - tile.trimLeft - tile.packedWidth : tile.trimLeft),
				tile.height * (fy ? 1 - tile.trimBottom - tile.packedHeight : tile.trimBottom), tile.width * tile.packedWidth,
				tile.height * tile.packedHeight };
	}

	/** True when the layer's region can be read pixel for pixel: not turned in its atlas. */
	private static boolean readable(TextureRegion region)
	{return region != null && region.getTexture() != null && !(region instanceof AtlasRegion && (((AtlasRegion) region).rotate || ((AtlasRegion) region).degrees != 0));}

	/** The box of the figure's pixels of alpha above 0, from its tile corner: left, right, bottom, top; null: none. */
	private static float[] figureBox(ParallaxLayer figure, Alpha alpha)
	{
		TextureRegion region = figure.getRegion();
		if (!readable(region))
			return null;
		int columns = Math.abs(region.getRegionWidth()), rows = Math.abs(region.getRegionHeight());
		int minX = columns, maxX = -1, minY = rows, maxY = -1;
		for (int y = 0; y < rows; y++)
			for (int x = 0; x < columns; x++)
				if (read(alpha, region, x, y) > 0)
				{
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
		if (maxX < 0)
			return null;
		float[] image = imageBox(Tile.of(figure), figure.flipX, figure.flipY);
		float sx = image[2] / columns, sy = image[3] / rows;
		// Image columns from its left, rows from its top: flipped, they land from the other side.
		int left = figure.flipX ? columns - 1 - maxX : minX, right = figure.flipX ? columns - 1 - minX : maxX;
		int bottom = figure.flipY ? minY : rows - 1 - maxY, top = figure.flipY ? maxY : rows - 1 - minY;
		return new float[] { image[0] + left * sx, image[0] + (right + 1) * sx, image[1] + bottom * sy, image[1] + (top + 1) * sy };
	}

	/**
	 * The stretches of X, from the layer's tile corner, over which every point of the band {@code bottom..top} (from the
	 * same corner) is drawn opaque by the layer, whichever of {@code tiles} it draws: from, to pairs, each starting in
	 * [0, period), merged across the joins between tiles {@code period} apart. A pair longer than the period: covered all
	 * along.
	 */
	private static float[] coveredRuns(ParallaxLayer near, List<Tile> tiles, float period, float bottom, float top, boolean repeatOnY,
			Alpha alpha, int opaque)
	{
		List<float[]> tile = null;
		for (Tile each : tiles)
		{
			List<float[]> runs = tileRuns(near, each, bottom, top, repeatOnY, alpha, opaque);
			tile = tile == null ? runs : intersect(tile, runs);
			if (tile.isEmpty())
				return new float[0];
		}

		// The same stretches in the tiles around, merged where one tile's meets the next one's.
		float touch = Float.MAX_VALUE;
		for (Tile each : tiles)
			touch = Math.min(touch, each.width * each.packedWidth / Math.abs(each.region.getRegionWidth()) * 1e-3f);
		List<float[]> all = new ArrayList<>();
		for (int m = -2; m <= 3; m++)
			for (float[] run : tile)
				all.add(new float[] { run[0] + m * period, run[1] + m * period });
		all.sort((a, b) -> Float.compare(a[0], b[0]));
		List<float[]> merged = new ArrayList<>();
		for (float[] run : all)
		{
			float[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
			if (last != null && run[0] <= last[1] + touch)
				last[1] = Math.max(last[1], run[1]);
			else
				merged.add(run.clone());
		}
		List<Float> out = new ArrayList<>();
		for (float[] run : merged)
		{
			if (run[1] - run[0] > period + touch)
			{
				// One run longer than the period meets its own next copy: covered all along.
				return new float[] { 0, 2 * period + 1 };
			}
			if (run[0] >= 0 && run[0] < period)
			{
				out.add(run[0]);
				out.add(run[1]);
			}
		}
		float[] runs = new float[out.size()];
		for (int i = 0; i < runs.length; i++)
			runs[i] = out.get(i);
		return runs;
	}

	/** Where two lists of stretches, each sorted and apart, are both: the stretches they share. */
	private static List<float[]> intersect(List<float[]> a, List<float[]> b)
	{
		List<float[]> out = new ArrayList<>();
		for (int i = 0, j = 0; i < a.size() && j < b.size();)
		{
			float from = Math.max(a.get(i)[0], b.get(j)[0]), to = Math.min(a.get(i)[1], b.get(j)[1]);
			if (from < to)
				out.add(new float[] { from, to });
			if (a.get(i)[1] < b.get(j)[1])
				i++;
			else
				j++;
		}
		return out;
	}

	/** The stretches of X, from the box's corner, where one tile alone is opaque over the whole band; sorted, apart. */
	private static List<float[]> tileRuns(ParallaxLayer near, Tile tile, float bottom, float top, boolean repeatOnY, Alpha alpha, int opaque)
	{
		List<float[]> runs = new ArrayList<>();
		TextureRegion region = tile.region;
		if (!readable(region))
			return runs;
		int columns = Math.abs(region.getRegionWidth()), rows = Math.abs(region.getRegionHeight());
		boolean fx = near.flipX;
		float[] image = imageBox(tile, fx, near.flipY);
		float sx = image[2] / columns, sy = image[3] / rows;

		// The copies of the image the band can meet, drawn up the layer's box: their bottom and whether they are flipped.
		List<float[]> copies = new ArrayList<>();
		float stepY = near.getTotalHeight();
		if (repeatOnY && stepY > 0)
		{
			for (long m = (long) Math.floor((bottom - image[1] - image[3]) / stepY); m <= (long) Math.ceil((top - image[1]) / stepY); m++)
				copies.add(new float[] { image[1] + m * stepY, near.flipY ? 1 : 0 });
		}
		else
		{
			copies.add(new float[] { image[1], near.flipY ? 1 : 0 });
			if (near.isMirror)
				// Stacked above the strip, flipped on Y (ParallaxPageReader draws it when the page tiles on X alone).
				copies.add(new float[] { near.getHeight() + near.padY + imageBox(tile, fx, !near.flipY)[1], near.flipY ? 0 : 1 });
		}
		// Between the copies, or past them, nothing is drawn: the band must lie within them end to end.
		copies.sort((a, b) -> Float.compare(a[0], b[0]));
		float reached = bottom;
		for (float[] copy : copies)
			if (copy[0] <= reached && copy[0] + image[3] > reached)
				reached = copy[0] + image[3];
		if (reached < top)
			return runs;

		// Each column of texels, from the left as drawn: opaque over the whole band?
		int runStart = -1;
		for (int column = 0; column <= columns; column++)
		{
			boolean covered = column < columns && columnCovers(alpha, opaque, region, fx ? columns - 1 - column : column, copies, rows, sy, image[3], bottom, top);
			if (covered && runStart < 0)
				runStart = column;
			else if (!covered && runStart >= 0)
			{
				runs.add(new float[] { image[0] + runStart * sx, image[0] + column * sx });
				runStart = -1;
			}
		}
		return runs;
	}

	/** True when the image column {@code x} is opaque on every texel row of every copy that the band meets. */
	private static boolean columnCovers(Alpha alpha, int opaque, TextureRegion region, int x, List<float[]> copies, int rows, float sy, float height,
			float bottom, float top)
	{
		for (float[] copy : copies)
		{
			float low = Math.max(bottom, copy[0]), high = Math.min(top, copy[0] + height);
			if (high <= low)
				continue;
			int first = Math.max(0, (int) Math.floor((low - copy[0]) / sy));
			int last = Math.min(rows - 1, (int) Math.ceil((high - copy[0]) / sy) - 1);
			boolean flipped = copy[1] > 0;
			for (int row = first; row <= last; row++)
				if (read(alpha, region, x, flipped ? row : rows - 1 - row) < opaque)
					return false;
		}
		return true;
	}

	/** The region's alpha at image column x, row y from its top. */
	private static int read(Alpha alpha, TextureRegion region, int x, int y)
	{
		int value = alpha.at(region.getTexture(), region.getRegionX() + x, region.getRegionY() + y);
		if (value < 0)
			throw new MissingImage();
		return value;
	}

	/** A page the sweep needs has no image: the cover is left empty. */
	private static final class MissingImage extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		MissingImage()
		{super(null, null, false, false);}
	}
}
