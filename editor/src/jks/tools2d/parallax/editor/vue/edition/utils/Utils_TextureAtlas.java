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

		// Every distinct image, grouped under the region name it will have in the new atlas.
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

		PixmapPacker packer = createPacker(GVars_Vue_Edition.allImage);
		Map<Texture, Pixmap> sourcePixmaps = new IdentityHashMap<>();
		Map<Pixmap, Boolean> mustDispose = new IdentityHashMap<>();
		Map<Position_Infos, int[]> newPositions = new HashMap<>();
		try
		{
			for (Map.Entry<String, List<TextureRegion>> group : groups.entrySet())
			{
				List<TextureRegion> regions = group.getValue();
				regions.sort(Comparator.comparingInt(region -> GVars_Vue_Edition.imageRef.get(region).position));

				for (int index = 0; index < regions.size(); index++)
				{
					TextureRegion region = regions.get(index);
					Pixmap pixels = extractRegion(region, sourcePixmaps, mustDispose);
					packer.pack(PixmapPackerIO.packedName(group.getKey(), regions.size() == 1 ? -1 : index), pixels);
					pixels.dispose();
					newPositions.put(GVars_Vue_Edition.imageRef.get(region), new int[] { index });
				}
			}

			// Layers are always drawn scaled: linear filtering is what the 50px padding and tripled borders are for.
			// Mipmaps: a screen-wide layer is drawn smaller than its page, 200 of them draw a third faster with them.
			// Their levels average whole blocks: without bleeding, the black of transparent pixels outlines every shape.
			PixmapPackerIO.SaveParameters parameters = new PixmapPackerIO.SaveParameters();
			parameters.minFilter = TextureFilter.MipMapLinearLinear;
			parameters.magFilter = TextureFilter.Linear;
			parameters.bleed = true;
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
	 * 4096px pages, the texture size every GPU handles, unless an image needs more; the packer adds pages as needed.
	 * (The 2019 version used 3x the largest image, easily a 15000px page.) Sides are powers of two: OpenGL ES 2 and
	 * WebGL 1 cannot mipmap any other texture, and draw it black.
	 */
	private static PixmapPacker createPacker(List<TextureRegion> regions)
	{
		int largestWidth = 0, largestHeight = 0;
		for (TextureRegion region : regions)
		{
			largestWidth = Math.max(largestWidth, region.getRegionWidth());
			largestHeight = Math.max(largestHeight, region.getRegionHeight());
		}

		// Guillotine pages keep the padding on their edges, plus the padding added to each image.
		int border = paddingSize * 3;
		int pageWidth = Math.min(atlasMaxSize, MathUtils.nextPowerOfTwo(Math.max(preferredPageSize, largestWidth + border)));
		int pageHeight = Math.min(atlasMaxSize, MathUtils.nextPowerOfTwo(Math.max(preferredPageSize, largestHeight + border)));
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
