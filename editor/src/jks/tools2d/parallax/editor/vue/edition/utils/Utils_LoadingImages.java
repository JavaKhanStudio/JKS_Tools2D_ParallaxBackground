package jks.tools2d.parallax.editor.vue.edition.utils;

import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.atlasMaxSize;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.activeFileWatching;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.allImage;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.currentlySelectedParallax;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.imageRef;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.outsideTextureReserve;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.textureLink;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import java.util.ArrayList;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kotcrab.vis.ui.util.dialog.Dialogs;

import jks.tools2d.libgdxutils.Utils_Scene2D;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.EditorPaths;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.VE_Tab_TextureList_Adding;
import jks.tools2d.parallax.editor.vue.edition.data.Outside_Source;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;

public final class Utils_LoadingImages
{
	private Utils_LoadingImages()
	{}

	/** Files dropped on the edition view: PNGs become new images, anything else is reported. */
	public static void fileReception(String[] files)
	{
		if (projectDatas.outsideInfos == null)
			projectDatas.outsideInfos = new ArrayList<>();

		StringBuilder errors = new StringBuilder();
		for (String path : files)
		{
			String extension = Utils_Scene2D.getExtension(path).toLowerCase();
			if ("png".equals(extension))
				errors.append(loadPNG(path));
			else if (FVars_Extensions.ATLAS.equals(extension))
				errors.append("\n").append(extractName(path)).append(".atlas: changing the atlas of an open project is not supported,")
						.append("\n open the .atlas from the start screen to create a project from it.");
			else
				errors.append("\n").append(path).append(": only .png images can be added.");
		}

		if (errors.length() > 0)
			Dialogs.showErrorDialog(GVars_UI.mainUi, errors.toString().trim());

		GVars_Vue_Edition.setItems();
	}

	/** Adds a loose PNG to the project. Returns an error message, or an empty string when it worked. */
	public static String loadPNG(String path)
	{
		if (outsideTextureReserve.containsKey(path))
			return "\n" + extractName(path) + " is already in the project.";

		TextureRegion textureRegion = Utils_Texture.getTextureRegionFromPath(path);
		if (textureRegion == null)
			return "\nCould not read " + path;

		if (textureRegion.getRegionWidth() > atlasMaxSize || textureRegion.getRegionHeight() > atlasMaxSize)
		{
			textureRegion.getTexture().dispose();
			return "\n" + extractName(path) + " is bigger than the " + atlasMaxSize + " pixels limit of an atlas page.";
		}

		registerOutsideImage(path, textureRegion);
		projectDatas.outsideInfos.add(new Outside_Source(path, extractName(path)));
		return "";
	}

	/** Makes a loose image available in the image list and reloads it when the file changes. */
	public static void registerOutsideImage(String path, TextureRegion region)
	{
		allImage.add(region);
		imageRef.put(region, new Position_Infos(false, path, 0));
		outsideTextureReserve.put(path, region);
		// Keyed by the path as saved, but a relative one is watched in the project folder, not the working directory.
		activeFileWatching.put(path, new WatchedImage(EditorPaths.resolveProjectFile(path).toString(), region));
	}

	/** File name without folder nor extension, whatever the platform separator. */
	public static String extractName(String path)
	{return new FileHandle(path).nameWithoutExtension();}

	/**
	 * Removes the layers using {@code text}; with {@code hardClean} the image also leaves the project.
	 */
	public static void removeFile(TextureRegion text, boolean hardClean)
	{
		ArrayList<ParallaxLayer> layers = textureLink.remove(text);
		if (layers != null)
			parallax_Heart.parallaxReader.layers.removeAll(layers);

		if (currentlySelectedParallax != null && currentlySelectedParallax.getTexRegion().get(0) == text)
			currentlySelectedParallax = null;

		if (!hardClean)
			return;

		allImage.remove(text);
		VE_Tab_TextureList_Adding.imageList.getItems().removeValue(text, true);

		Position_Infos position = imageRef.remove(text);
		if (position != null && !position.fromAtlas)
		{
			projectDatas.outsideInfos.removeIf(source -> source.url.equals(position.url));
			outsideTextureReserve.remove(position.url);

			WatchedImage watched = activeFileWatching.remove(position.url);
			if (watched != null)
				watched.cancel();

			// Deleted layers waiting for "undo" must not come back with a disposed texture.
			for (int i = GVars_Vue_Edition.trashedValues.size - 1; i >= 0; i--)
				if (GVars_Vue_Edition.trashedValues.get(i).getTexRegion().get(0) == text)
				{
					GVars_Vue_Edition.trashedValues.removeIndex(i);
					GVars_Vue_Edition.trashedValuesPosition.removeIndex(i);
				}

			text.getTexture().dispose();
		}
	}
}
