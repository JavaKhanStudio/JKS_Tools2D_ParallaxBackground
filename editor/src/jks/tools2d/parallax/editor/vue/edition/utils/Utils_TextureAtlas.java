package jks.tools2d.parallax.editor.vue.edition.utils;

import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.atlasMaxSize;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.Pixmap.Filter;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.kotcrab.vis.ui.util.dialog.Dialogs;

import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.pixmap.PixmapPacker;
import jks.tools2d.parallax.editor.vue.edition.pixmap.PixmapPackerIO;

public final class Utils_TextureAtlas
{
	static final int paddingSize = 50;
	static final int preferredPageSize = 4096;

	private Utils_TextureAtlas()
	{}

	/**
	 * Packs every image of the project (atlas regions and loose PNGs) into a new atlas next to the project, then points
	 * the project at it. Regions keep their name; loose images are named after their file.
	 *
	 * @return false (after telling the user) if the atlas could not be written; the project is then unchanged.
	 */
	public static boolean flattenProject(String path, String name)
	{
		FileHandle atlasFile = nextFreeAtlasFile(path, name);

		// Every distinct image, grouped under the region name it will have in the new atlas. Used by a layer or not, on
		// purpose (d12): after flatten the project points at this atlas alone, so an image left out leaves its library.
		Map<String, List<TextureRegion>> groups = new LinkedHashMap<>();
		Set<String> atlasNames = new HashSet<>();
		for (TextureRegion region : GVars_Vue_Edition.allImage)
		{
			Position_Infos info = GVars_Vue_Edition.imageRef.get(region);
			if (info != null && info.fromAtlas)
				atlasNames.add(info.url);
		}
		for (TextureRegion region : GVars_Vue_Edition.allImage)
		{
			Position_Infos info = GVars_Vue_Edition.imageRef.get(region);
			if (info == null)
				continue;

			String regionName = info.fromAtlas ? info.url : uniqueName(Utils_LoadingImages.extractName(info.url), atlasNames, groups);
			groups.computeIfAbsent(regionName, k -> new ArrayList<>()).add(region);
		}

		// Packing order: group by group, each by position. The guillotine packer only fills its last page, so the regions
		// of a name reach the atlas in that order, which is how a layer finds its region again (findLayer).
		List<String> packedNames = new ArrayList<>();
		List<TextureRegion> packedRegions = new ArrayList<>();
		Map<Position_Infos, int[]> newPositions = new HashMap<>();
		for (Map.Entry<String, List<TextureRegion>> group : groups.entrySet())
		{
			List<TextureRegion> regions = group.getValue();
			regions.sort(Comparator.comparingInt(region -> GVars_Vue_Edition.imageRef.get(region).position));
			for (int index = 0; index < regions.size(); index++)
			{
				packedNames.add(PixmapPackerIO.packedName(group.getKey(), regions.size() == 1 ? -1 : index));
				packedRegions.add(regions.get(index));
				newPositions.put(GVars_Vue_Edition.imageRef.get(regions.get(index)), new int[] { index });
			}
		}

		PixmapPacker packer = null;
		Map<Texture, Pixmap> sourcePixmaps = new IdentityHashMap<>();
		Map<Pixmap, Boolean> mustDispose = new IdentityHashMap<>();
		try
		{
			int[] pageSize = pageSize(packedNames, packedRegions, sourcePixmaps, mustDispose);
			packer = createPacker(pageSize[0], pageSize[1]);
			for (int i = 0; i < packedNames.size(); i++)
			{
				Pixmap pixels = extractRegion(packedRegions.get(i), sourcePixmaps, mustDispose);
				packer.pack(packedNames.get(i), pixels);
				pixels.dispose();
			}

			// Layers are always drawn scaled: linear filtering is what the 50px padding and tripled borders are for.
			// Mipmaps: a screen-wide layer is drawn smaller than its page, 200 of them draw a third faster with them.
			// Their levels average whole blocks: without bleeding, the black of transparent pixels outlines every shape.
			// Pixel art gives both up to stay sharp (d14).
			PixmapPackerIO.SaveParameters parameters = new PixmapPackerIO.SaveParameters();
			parameters.minFilter = projectDatas.pixelArt ? TextureFilter.Nearest : TextureFilter.MipMapLinearLinear;
			parameters.magFilter = projectDatas.pixelArt ? TextureFilter.Nearest : TextureFilter.Linear;
			parameters.bleed = true;
			parameters.etc2 = projectDatas.etc2;
			new PixmapPackerIO().save(atlasFile, packer, parameters);
		}
		catch (IOException | RuntimeException e)
		{
			Gdx.app.error("Utils_TextureAtlas", "Flattening failed", e);
			Dialogs.showErrorDialog(GVars_UI.mainUi, "Could not create the atlas " + atlasFile.name(), e);
			return false;
		}
		finally
		{
			if (packer != null)
				packer.dispose();
			for (Pixmap pixmap : sourcePixmaps.values())
				if (mustDispose.get(pixmap))
					pixmap.dispose();
		}

		// The project now only references the new atlas.
		for (Map.Entry<String, List<TextureRegion>> group : groups.entrySet())
			for (TextureRegion region : group.getValue())
			{
				Position_Infos info = GVars_Vue_Edition.imageRef.get(region);
				info.url = group.getKey();
				info.position = newPositions.get(info)[0];
				info.fromAtlas = true;
			}

		for (WatchedImage watched : GVars_Vue_Edition.activeFileWatching.values())
			watched.cancel();
		GVars_Vue_Edition.activeFileWatching.clear();
		if (projectDatas.outsideInfos != null)
			projectDatas.outsideInfos.clear();

		parallax_Heart.currentPage.pageModel.atlasName = atlasFile.name();
		// Written next to the project, which is not always the folder it was opened from.
		parallax_Heart.relativePath = atlasFile.parent().path();
		return true;
	}

	private static String uniqueName(String baseName, Set<String> atlasNames, Map<String, ?> taken)
	{
		String name = baseName;
		for (int suffix = 2; atlasNames.contains(name) || taken.containsKey(name); suffix++)
			name = baseName + "_" + suffix;
		return name;
	}

	/**
	 * The page size that writes the fewest pixels, pages being cropped on write (PixmapPackerIO.trimmed): every
	 * power-of-two shape from {@link #smallestPageSide} up to 4096px pages (more if an image needs it) is laid out
	 * without pixels, and the one whose cropped pages add up to the least wins, fewer pages on a tie. Four 3645px-wide
	 * strips 2350px high in all fill a 4096x4096 page; 4096x2048 pages hold them in 4096x2048 + 4096x1024, a quarter
	 * less video memory.
	 * <p>
	 * (The 2019 version used 3x the largest image, easily a 15000px page.) Sides are powers of two when mipmapped, which
	 * pixel art is not: OpenGL ES 2 and WebGL 1 cannot mipmap any other texture, and draw it black.
	 */
	private static int[] pageSize(List<String> names, List<TextureRegion> regions, Map<Texture, Pixmap> sourcePixmaps,
			Map<Pixmap, Boolean> mustDispose)
	{
		// The sizes the packer will place: stripped of their whitespace when createPacker strips.
		int[][] sizes = new int[names.size()][];
		PixmapPacker stripper = parallax_Heart.currentPage.useOriginalSize ? createPacker(atlasMaxSize, atlasMaxSize).layoutOnly() : null;
		int largestWidth = 0, largestHeight = 0;
		for (int i = 0; i < names.size(); i++)
		{
			TextureRegion region = regions.get(i);
			if (stripper == null)
				sizes[i] = new int[] { flattenedWidth(region), flattenedHeight(region) };
			else
			{
				Pixmap pixels = extractRegion(region, sourcePixmaps, mustDispose);
				Rectangle rect = stripper.pack(names.get(i), pixels);
				pixels.dispose();
				sizes[i] = new int[] { (int) rect.width, (int) rect.height };
			}
			largestWidth = Math.max(largestWidth, sizes[i][0]);
			largestHeight = Math.max(largestHeight, sizes[i][1]);
		}

		// Guillotine pages keep the padding on their edges, plus the padding added to each image.
		int border = paddingSize * 3;
		int maxWidth = Math.min(atlasMaxSize, MathUtils.nextPowerOfTwo(Math.max(preferredPageSize, largestWidth + border)));
		int maxHeight = Math.min(atlasMaxSize, MathUtils.nextPowerOfTwo(Math.max(preferredPageSize, largestHeight + border)));
		int minWidth = Math.min(maxWidth, MathUtils.nextPowerOfTwo(Math.max(smallestPageSide, largestWidth + border)));
		int minHeight = Math.min(maxHeight, MathUtils.nextPowerOfTwo(Math.max(smallestPageSide, largestHeight + border)));

		int[] best = { maxWidth, maxHeight };
		long bestArea = Long.MAX_VALUE;
		int bestPages = Integer.MAX_VALUE;
		for (int width = maxWidth; width >= minWidth; width /= 2)
			for (int height = maxHeight; height >= minHeight; height /= 2)
			{
				PixmapPacker layout = new PixmapPacker(width, height, Format.RGBA8888, paddingSize, true, new PixmapPacker.GuillotineStrategy()).layoutOnly();
				for (int i = 0; i < names.size(); i++)
					layout.packLayout(names.get(i), sizes[i][0], sizes[i][1]);

				long area = 0;
				for (jks.tools2d.parallax.editor.vue.edition.pixmap.Page page : layout.getPages())
				{
					int[] written = PixmapPackerIO.writtenSize(layout, page, !projectDatas.pixelArt);
					area += (long) written[0] * written[1];
				}
				int pages = layout.getPages().size;
				if (area < bestArea || area == bestArea && pages < bestPages)
				{
					best = new int[] { width, height };
					bestArea = area;
					bestPages = pages;
				}
			}
		return best;
	}

	/**
	 * Pages are not tried smaller than this: a page is cropped to what it holds anyway, and more pages cost a texture
	 * switch each where their layers are drawn.
	 */
	static final int smallestPageSide = 2048;

	private static PixmapPacker createPacker(int pageWidth, int pageHeight)
	{
		// A page drawing regions at their original size keeps the fill-rate win of stripping: the packer strips each
		// image and stores its original size and offset. Other pages stretch whatever is packed, so nothing is stripped.
		boolean strip = parallax_Heart.currentPage.useOriginalSize;
		return new PixmapPacker(pageWidth, pageHeight, Format.RGBA8888, paddingSize, true, strip, strip, new PixmapPacker.GuillotineStrategy());
	}

	/**
	 * A page drawing regions at their original size (WholePage_Model.useOriginalSize) gets back the whitespace an
	 * external packer stripped, so {@link #createPacker}'s packer strips it again and records the original size and
	 * offset. Copied as packed, the image would have no offset left and be drawn over the whole layer.
	 */
	private static AtlasRegion strippedRegion(TextureRegion region)
	{
		if (!parallax_Heart.currentPage.useOriginalSize || !(region instanceof AtlasRegion))
			return null;
		AtlasRegion atlasRegion = (AtlasRegion) region;
		boolean stripped = atlasRegion.originalWidth != atlasRegion.packedWidth || atlasRegion.originalHeight != atlasRegion.packedHeight;
		return stripped ? atlasRegion : null;
	}

	private static int flattenedWidth(TextureRegion region)
	{
		AtlasRegion stripped = strippedRegion(region);
		return stripped != null ? stripped.originalWidth : region.getRegionWidth();
	}

	private static int flattenedHeight(TextureRegion region)
	{
		AtlasRegion stripped = strippedRegion(region);
		return stripped != null ? stripped.originalHeight : region.getRegionHeight();
	}

	/**
	 * Copies the pixels of a region, with its stripped whitespace back when {@link #strippedRegion} says so, reading
	 * each source texture back from its data only once.
	 */
	private static Pixmap extractRegion(TextureRegion region, Map<Texture, Pixmap> sourcePixmaps, Map<Pixmap, Boolean> mustDispose)
	{
		Pixmap source = sourcePixmaps.get(region.getTexture());
		if (source == null)
		{
			TextureData data = region.getTexture().getTextureData();
			if (!data.isPrepared())
				data.prepare();
			source = data.consumePixmap();
			mustDispose.put(source, data.disposePixmap());
			sourcePixmaps.put(region.getTexture(), source);
		}

		// offsetY counts from the bottom of the original image, pixmap rows from its top.
		AtlasRegion stripped = strippedRegion(region);
		int left = stripped == null ? 0 : (int) stripped.offsetX;
		int top = stripped == null ? 0 : stripped.originalHeight - (int) stripped.offsetY - region.getRegionHeight();

		Pixmap pixels = new Pixmap(flattenedWidth(region), flattenedHeight(region), Format.RGBA8888);
		pixels.setFilter(Filter.NearestNeighbour);
		pixels.setBlending(Blending.None);
		pixels.drawPixmap(source, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(),
				left, top, region.getRegionWidth(), region.getRegionHeight());
		return pixels;
	}

	/** {@code name.atlas}, or {@code name1.atlas}, {@code name2.atlas}... if it already exists. */
	public static FileHandle nextFreeAtlasFile(String path, String name)
	{
		FileHandle file = new FileHandle(path + "/" + name + ".atlas");
		for (int suffix = 1; file.exists(); suffix++)
			file = new FileHandle(path + "/" + name + suffix + ".atlas");
		return file;
	}
}
