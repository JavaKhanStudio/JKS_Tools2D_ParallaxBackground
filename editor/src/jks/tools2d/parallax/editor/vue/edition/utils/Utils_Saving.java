package jks.tools2d.parallax.editor.vue.edition.utils;

import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;
import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxName;
import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxPath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.esotericsoftware.kryo.io.Output;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType;
import com.kotcrab.vis.ui.util.dialog.OptionDialogAdapter;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.EditorPaths;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Serialization_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.VE_Options;
import jks.tools2d.parallax.editor.vue.edition.data.Outside_Source;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.data.WholePage_Editor;
import jks.tools2d.parallax.heart.GVars_Serialization;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.side.SquareBackground;

public final class Utils_Saving
{
	private static final int AUTO_SAVES_KEPT = 10;

	private Utils_Saving()
	{}

	/** Exports the parallax, first flattening loose images into a new atlas if there are any. */
	public static void saving_Parallax(String where, String whatName)
	{
		// Checked before flattening too: flattening rewrites the project, and would then export nothing.
		if (!VE_Options.formatLibGDX.isChecked() && !VE_Options.formatJson.isChecked())
		{
			Dialogs.showOKDialog(GVars_UI.mainUi, "Export", "Nothing exported: no format is selected."
					+ "\nTick LibGDX (." + FVars_Extensions.PARALLAX + ") or JSON (." + FVars_Extensions.JSON_PARALLAX + ").");
			return;
		}

		boolean oneOutside = false;
		for (ParallaxLayer layer : parallax_Heart.parallaxReader.layers)
			for (TextureRegion region : layer.getTexRegion())
			{
				Position_Infos info = GVars_Vue_Edition.imageRef.get(region);
				oneOutside |= info != null && !info.fromAtlas;
			}

		if (oneOutside || VE_Options.forceExport.isChecked())
			askForFlatening(where, whatName);
		else
			savingExport(where, whatName);
	}

	public static void savingExport(String where, String whatName)
	{
		WholePage_Model outputFinalModel = buildWholePageForExport(parallax_Heart.parallaxReader.layers);

		try
		{
			if (VE_Options.formatLibGDX.isChecked())
				saving_Parallax_Kryo(where, whatName, outputFinalModel);

			if (VE_Options.formatJson.isChecked())
				saving_Parallax_JSON(where, whatName, outputFinalModel);
		}
		catch (IOException | RuntimeException e)
		{
			Gdx.app.error("Utils_Saving", "Export failed", e);
			Dialogs.showErrorDialog(GVars_UI.mainUi, "Could not export " + whatName, e);
			return;
		}

		Dialogs.showOKDialog(GVars_UI.mainUi, "Saving", "Successful export of " + whatName + " as" +
				(VE_Options.formatLibGDX.isChecked() ? " ." + FVars_Extensions.PARALLAX : "") +
				(VE_Options.formatJson.isChecked() ? " ." + FVars_Extensions.JSON_PARALLAX : ""));
	}

	private static void askForFlatening(String where, String whatName)
	{
		Dialogs.showOptionDialog(GVars_UI.mainUi, "Export", "Warning! One or more images are not part of the texture atlas."
				+ "\nExporting will flatten the project into a new texture atlas. Save the project before that?",
				OptionDialogType.YES_NO_CANCEL, new OptionDialogAdapter()
				{
					@Override
					public void yes()
					{saving_Parallax_Project(parallaxPath.getText(), parallaxName.getText(), false, () -> flattenAndExport(where, whatName));}

					@Override
					public void no()
					{flattenAndExport(where, whatName);}
				});
	}

	private static void flattenAndExport(String where, String whatName)
	{
		// On failure the loose images are still loose: exporting now would silently drop their layers.
		if (Utils_TextureAtlas.flattenProject(parallaxPath.getText(), parallaxName.getText()))
			savingExport(where, whatName);
	}

	public static void saving_Parallax_Kryo(String where, String whatName, WholePage_Model outputFinalModel) throws IOException
	{
		try (Output output = new Output(new FileOutputStream(where + "/" + whatName + "." + FVars_Extensions.PARALLAX)))
		{GVars_Serialization.kryo.writeObject(output, outputFinalModel);}
	}

	public static void saving_Parallax_JSON(String where, String whatName, WholePage_Model outputFinalModel) throws IOException
	{GVars_Serialization_Editor.objectMapper.writeValue(new File(where + "/" + whatName + "." + FVars_Extensions.JSON_PARALLAX), outputFinalModel);}

	/**
	 * Saves the project into {@code where}, copying its atlas there first: a project finds its atlas next to itself.
	 * Saved away from the project folder, loose images keep working through absolute paths.
	 */
	public static void saving_Parallax_Project(String where, String whatName, boolean showHardInfo)
	{saving_Parallax_Project(where, whatName, showHardInfo, null);}

	/**
	 * Same, then runs {@code afterSave} once the project is really saved. A project that did not load completely would
	 * lose layers over an existing file, so that save asks first.
	 */
	public static void saving_Parallax_Project(String where, String whatName, boolean showHardInfo, Runnable afterSave)
	{
		File target = projectFile(where, whatName);
		if (!GVars_Vue_Edition.loadedIncompletely || !target.exists())
		{
			if (saveProject(where, whatName, showHardInfo) && afterSave != null)
				afterSave.run();
			return;
		}

		Dialogs.showOptionDialog(GVars_UI.mainUi, "Saving", "This project did not load completely: the layers that could not be loaded"
				+ "\nare not in the editor, and saving over " + target.getName() + " removes them from it for good.",
				OptionDialogType.YES_CANCEL, new OptionDialogAdapter()
				{
					@Override
					public void yes()
					{
						if (!saveProject(where, whatName, showHardInfo))
							return;
						GVars_Vue_Edition.loadedIncompletely = false;
						if (afterSave != null)
							afterSave.run();
					}
				}).setYesButtonText("Save anyway");
	}

	private static boolean saveProject(String where, String whatName, boolean showHardInfo)
	{
		try
		{
			copyAtlasTo(Paths.get(where));
			WholePage_Editor page = buildWholePageAsProjectForSaving();
			if (isProjectFolder(where))
			{
				projectDatas.prepareForSaving(page);
				writeProject(where, whatName, projectDatas);
			}
			else
				writeProject(where, whatName, withAbsolutePaths(page));
		}
		catch (AtlasConflictException e)
		{
			Gdx.app.error("Utils_Saving", "Project not saved: " + e.getMessage());
			Dialogs.showErrorDialog(GVars_UI.mainUi, "The project was not saved. " + e.getMessage());
			return false;
		}
		catch (IOException | RuntimeException e)
		{
			Gdx.app.error("Utils_Saving", "Project save failed", e);
			Dialogs.showErrorDialog(GVars_UI.mainUi, "Could not save the project", e);
			return false;
		}

		if (showHardInfo)
			Dialogs.showOKDialog(GVars_UI.mainUi, "Saving", "The project has been saved");
		return true;
	}

	/** The parallax as games read it: only layers whose image is in the atlas. */
	public static WholePage_Model buildWholePageForExport(List<ParallaxLayer> parallaxs)
	{
		WholePage_Model outputFinalModel = new WholePage_Model();
		Page_Model outputModel = new Page_Model();

		for (ParallaxLayer layer : parallaxs)
			for (TextureRegion texRegion : layer.getTexRegion())
			{
				Position_Infos info = GVars_Vue_Edition.imageRef.get(texRegion);
				if (info != null && info.fromAtlas)
					outputModel.pageList.add(Utils_Page.buildFromPage(layer, info.url, info.position));
			}

		fillPage(outputFinalModel, outputModel);
		return outputFinalModel;
	}

	/** The parallax as the editor reopens it: every layer, loose images included. */
	public static WholePage_Editor buildWholePageAsProjectForSaving()
	{
		WholePage_Editor outputFinalModel = new WholePage_Editor();
		Page_Model outputModel = new Page_Model();

		for (ParallaxLayer layer : parallax_Heart.parallaxReader.layers)
			for (TextureRegion region : layer.getTexRegion())
			{
				Position_Infos info = GVars_Vue_Edition.imageRef.get(region);
				if (info == null)
				{
					Gdx.app.error("Utils_Saving", "No source known for region " + region + ", layer skipped");
					continue;
				}
				outputModel.pageList.add(Utils_Page.buildFromPage(layer, info.url, info.position));
				outputFinalModel.inside.add(info.fromAtlas);
			}

		fillPage(outputFinalModel, outputModel);
		return outputFinalModel;
	}

	private static void fillPage(WholePage_Model page, Page_Model layers)
	{
		layers.atlasName = parallax_Heart.currentPage.pageModel.atlasName;
		page.pageModel = layers;

		SquareBackground top = parallax_Heart.topSquare;
		if (top != null)
		{
			page.topHalf_top = new Color(top.topColor);
			page.topHalf_bottom = new Color(top.bottomColor);
			page.topHalfSize = top.getScreenPercentage();
		}

		SquareBackground bottom = parallax_Heart.bottomSquare;
		if (bottom != null)
		{
			page.bottomHalf_top = new Color(bottom.topColor);
			page.bottomHalf_bottom = new Color(bottom.bottomColor);
			page.bottomHalfSize = bottom.getScreenPercentage();
		}

		page.useOriginalSize = parallax_Heart.currentPage.useOriginalSize;
		page.repeatOnX = parallax_Heart.parallaxReader.isRepeatOnX();
		page.repeatOnY = parallax_Heart.parallaxReader.isRepeatOnY();
	}

	private static boolean isProjectFolder(String where)
	{
		return GVars_Vue_Edition.relativePath != null
				&& Paths.get(where).toAbsolutePath().normalize().equals(Paths.get(GVars_Vue_Edition.relativePath).toAbsolutePath().normalize());
	}

	/**
	 * Copies the atlas and its page images into {@code folder}, keeping their layout. Files already there with the same
	 * content are left alone; a different file of the same name (another project's atlas) stops the save before
	 * anything is copied.
	 */
	private static void copyAtlasTo(Path folder) throws IOException
	{
		Path atlas = EditorPaths.atlasFile();
		folder = folder.toAbsolutePath().normalize();
		if (atlas == null || folder.equals(atlas.getParent()))
			return;

		Path source = atlas.getParent();
		List<Path> files = new ArrayList<>();
		files.add(atlas);
		FileHandle atlasHandle = new FileHandle(atlas.toFile());
		for (TextureAtlasData.Page page : new TextureAtlasData(atlasHandle, atlasHandle.parent(), false).getPages())
			files.add(page.textureFile.file().toPath().toAbsolutePath().normalize());

		List<Path> toCopy = new ArrayList<>();
		StringBuilder conflicts = new StringBuilder();
		for (Path file : files)
		{
			Path target = folder.resolve(source.relativize(file));
			if (!Files.exists(target))
				toCopy.add(file);
			else if (Files.mismatch(file, target) != -1)
				conflicts.append("\n").append(target);
		}
		if (conflicts.length() > 0)
			throw new AtlasConflictException("Saving it here copies its atlas into this folder, but different files of the same name are already there:"
					+ conflicts + "\nSave it into another folder, or move those files away.");

		for (Path file : toCopy)
		{
			Path target = folder.resolve(source.relativize(file));
			Files.createDirectories(target.getParent());
			Files.copy(file, target);
		}
	}

	private static final class AtlasConflictException extends IOException
	{
		AtlasConflictException(String message)
		{super(message);}
	}

	private static File projectFile(String where, String whatName)
	{return new File(where + "/" + whatName + "." + FVars_Extensions.PARALLAX_PROJECT);}

	private static void writeProject(String where, String whatName, Project_Data project) throws IOException
	{
		Files.createDirectories(Paths.get(where));
		GVars_Serialization_Editor.objectMapper.writeValue(projectFile(where, whatName), project);
	}

	/**
	 * Saves a timestamped copy of the project in the auto-save folder, keeping the 10 most recent ones. Image and
	 * atlas paths are made absolute since the copy does not sit next to the project.
	 */
	public static void autoSave()
	{
		Path folder = EditorPaths.autoSaveFolder();
		String prefix = "AUTO_" + VE_Options.parallaxName.getText() + "_";
		String saveName = prefix + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());

		try
		{
			Project_Data save = withAbsolutePaths(buildWholePageAsProjectForSaving());
			Path atlas = EditorPaths.atlasFile();
			if (atlas != null)
				save.saving.pageModel.atlasName = atlas.toString();
			writeProject(folder.toString(), saveName, save);
		}
		catch (IOException | RuntimeException e)
		{
			Gdx.app.error("Utils_Saving", "Auto-save failed", e);
			return;
		}

		// Only this project's saves: "AUTO_<name>_<timestamp>", not "AUTO_<name>_night_<timestamp>".
		Pattern ownSave = Pattern.compile(Pattern.quote(prefix) + "\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\." + FVars_Extensions.PARALLAX_PROJECT);
		List<Path> saves = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, file -> ownSave.matcher(file.getFileName().toString()).matches()))
		{
			stream.forEach(saves::add);
			saves.sort(null); // timestamps sort chronologically
			for (int i = 0; i < saves.size() - AUTO_SAVES_KEPT; i++)
				Files.deleteIfExists(saves.get(i));
		}
		catch (IOException e)
		{
			Gdx.app.error("Utils_Saving", "Could not clean old auto-saves", e);
		}
	}

	/** A copy of the project whose loose image paths are absolute. */
	private static Project_Data withAbsolutePaths(WholePage_Editor page)
	{
		for (int i = 0; i < page.pageModel.pageList.size(); i++)
			if (!page.inside.get(i))
				page.pageModel.pageList.get(i).regionName = absolute(page.pageModel.pageList.get(i).regionName);

		Project_Data copy = new Project_Data();
		copy.saving = page;
		copy.defaults = projectDatas.defaults;
		copy.outsideInfos = new ArrayList<>();
		if (projectDatas.outsideInfos != null)
			for (Outside_Source source : projectDatas.outsideInfos)
				copy.outsideInfos.add(new Outside_Source(absolute(source.url), source.name));
		return copy;
	}

	private static String absolute(String path)
	{return EditorPaths.resolveProjectFile(path).toAbsolutePath().toString();}

	public static boolean hasLooseImages()
	{return projectDatas.outsideInfos != null && !projectDatas.outsideInfos.isEmpty();}

	public static void showNoLooseImages()
	{Dialogs.showOKDialog(GVars_UI.mainUi, "Packing", "There is nothing to copy: every image of this project comes from its atlas.");}

	/**
	 * Copies the loose images into a folder next to the project and stores them with paths relative to it, so the
	 * project folder can be moved or shared.
	 */
	public static void packTextures()
	{
		if (!hasLooseImages())
		{
			showNoLooseImages();
			return;
		}

		String folderName = parallaxName.getText() + "_images";
		Path target = Paths.get(parallaxPath.getText()).resolve(folderName);
		Set<String> usedNames = new HashSet<>();
		StringBuilder errors = new StringBuilder();

		for (Outside_Source source : projectDatas.outsideInfos)
		{
			String fileName = source.name + ".png";
			for (int suffix = 2; !usedNames.add(fileName); suffix++)
				fileName = source.name + "_" + suffix + ".png";

			String newUrl = folderName + "/" + fileName;
			try
			{
				Path from = EditorPaths.resolveProjectFile(source.url);
				Path to = target.resolve(fileName);
				Files.createDirectories(target);
				if (!from.toAbsolutePath().normalize().equals(to.toAbsolutePath().normalize()))
					Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException e)
			{
				errors.append("\n").append(source.url).append(": ").append(e.getMessage());
				continue;
			}

			renameOutsideImage(source.url, newUrl, target.resolve(fileName));
			source.url = newUrl;
		}

		if (errors.length() > 0)
			Dialogs.showErrorDialog(GVars_UI.mainUi, "Some images could not be copied:" + errors);
		else
			Dialogs.showOKDialog(GVars_UI.mainUi, "Packing", "Images copied to " + target);
	}

	private static void renameOutsideImage(String oldUrl, String newUrl, Path newFile)
	{
		for (Position_Infos info : GVars_Vue_Edition.imageRef.values())
			if (!info.fromAtlas && oldUrl.equals(info.url))
				info.url = newUrl;

		TextureRegion region = GVars_Vue_Edition.outsideTextureReserve.remove(oldUrl);
		if (region == null)
			return;

		GVars_Vue_Edition.outsideTextureReserve.put(newUrl, region);
		WatchedImage watched = GVars_Vue_Edition.activeFileWatching.remove(oldUrl);
		if (watched != null)
		{
			watched.cancel();
			region = watched.getRegion();
		}
		GVars_Vue_Edition.activeFileWatching.put(newUrl, new WatchedImage(newFile.toString(), region));
	}
}
