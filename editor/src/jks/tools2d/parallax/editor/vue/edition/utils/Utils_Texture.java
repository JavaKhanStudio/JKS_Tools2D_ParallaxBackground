package jks.tools2d.parallax.editor.vue.edition.utils;

import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.allImage;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.imageRef;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.outsideTextureReserve;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.textureLink;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.trashedValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;

public final class Utils_Texture
{
	/** The filters the textures had before pixel art turned them Nearest, given back when it is turned off. */
	private static final Map<Texture, TextureFilter[]> smoothFilters = new IdentityHashMap<>();

	private Utils_Texture()
	{}

	/**
	 * Draws every image of the project as the export will (Project_Data.pixelArt): Nearest when it is on, the texture's
	 * own filters when it is off. Called again for every texture the project gains while it is on.
	 */
	public static void applyPixelArt()
	{
		boolean pixelArt = projectDatas != null && projectDatas.pixelArt;
		for (TextureRegion region : allImage)
		{
			Texture texture = region.getTexture();
			if (pixelArt)
			{
				smoothFilters.computeIfAbsent(texture, t -> new TextureFilter[] { t.getMinFilter(), t.getMagFilter() });
				texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
			}
			else
			{
				TextureFilter[] smooth = smoothFilters.get(texture);
				if (smooth != null)
					texture.setFilter(smooth[0], smooth[1]);
			}
		}
		if (!pixelArt)
			smoothFilters.clear();
	}

	/** Forgets the textures of the project being closed. */
	public static void forgetPixelArt()
	{smoothFilters.clear();}

	/** Loads a loose image file as a mipmapped texture, or returns null (and logs) if it cannot be read. */
	public static TextureRegion getTextureRegionFromPath(String path)
	{
		try
		{
			Texture texture = new Texture(new FileHandle(path), true);
			texture.setFilter(TextureFilter.MipMap, TextureFilter.MipMap);
			return new TextureRegion(texture);
		}
		catch (RuntimeException e)
		{
			Gdx.app.error("Utils_Texture", "Cannot load " + path, e);
			return null;
		}
	}

	/** Makes every layer drawing {@code target} draw {@code newTexture} instead. Both stay in the image list. */
	public static void changeTextureInPage(TextureRegion target, TextureRegion newTexture)
	{
		if (target == null || newTexture == null || target == newTexture)
			return;

		moveLayers(target, newTexture);
		GVars_Vue_Edition.refreshActiveTab();
	}

	/**
	 * Swaps {@code oldRegion} for {@code newRegion} everywhere (layers, image list, save info), e.g. after the file was
	 * edited on disk.
	 */
	public static void replaceTexture(TextureRegion oldRegion, TextureRegion newRegion, boolean disposeOld)
	{
		moveLayers(oldRegion, newRegion);

		for (ParallaxLayer trashed : trashedValues)
			if (trashed.getTexRegion().get(0) == oldRegion)
				trashed.setTexRegion(newRegion);

		Position_Infos info = imageRef.remove(oldRegion);
		if (info != null)
			imageRef.put(newRegion, info);

		Collections.replaceAll(allImage, oldRegion, newRegion);
		outsideTextureReserve.replaceAll((path, region) -> region == oldRegion ? newRegion : region);

		GVars_Vue_Edition.setItems();
		GVars_Vue_Edition.refreshActiveTab();

		if (disposeOld)
		{
			smoothFilters.remove(oldRegion.getTexture());
			oldRegion.getTexture().dispose();
		}
		applyPixelArt();
	}

	private static void moveLayers(TextureRegion from, TextureRegion to)
	{
		ArrayList<ParallaxLayer> layers = textureLink.remove(from);
		if (layers == null)
			return;

		for (ParallaxLayer layer : layers)
			layer.setTexRegion(to);

		textureLink.computeIfAbsent(to, k -> new ArrayList<>()).addAll(layers);
	}
}
