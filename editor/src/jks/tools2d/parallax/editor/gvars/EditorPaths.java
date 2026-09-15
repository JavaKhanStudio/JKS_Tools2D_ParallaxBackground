package jks.tools2d.parallax.editor.gvars;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jks.tools2d.parallax.editor.vue.Vue_Edition;
import jks.tools2d.parallax.pages.WholePage_Model;

/** Where the editor looks for and writes files. */
public final class EditorPaths
{
	private EditorPaths()
	{}

	/** The sample "Files" folder when started from the editor module, the user home otherwise. */
	public static Path filesRoot()
	{
		Path files = Paths.get("Files").toAbsolutePath();
		return Files.isDirectory(files) ? files : Paths.get(System.getProperty("user.home"));
	}

	public static Path autoSaveFolder()
	{
		Path files = Paths.get("Files").toAbsolutePath();
		return Files.isDirectory(files)
				? files.resolve("AutoSave")
				: Paths.get(System.getProperty("user.home"), ".parallax-editor", "autosave");
	}

	/**
	 * Resolves an image path saved in a project: absolute paths as-is, relative ones against the project folder
	 * (falling back to the working directory, where 2019 projects put their "packing_" folders).
	 */
	public static Path resolveProjectFile(String path)
	{
		Path file = Paths.get(path);
		if (file.isAbsolute() || GVars_Vue_Edition.relativePath == null)
			return file;

		Path inProject = Paths.get(GVars_Vue_Edition.relativePath).resolve(file);
		return Files.exists(inProject) || !Files.exists(file) ? inProject : file.toAbsolutePath();
	}

	/**
	 * Points the heart at the folder a loaded page's atlas is in, leaving only its file name in the page, as it is
	 * exported. A page may name it by a path: relative to the project folder, or absolute in auto-saves.
	 */
	public static void locateAtlas(WholePage_Model page)
	{
		if (page.pageModel.atlasName == null)
			return;

		Path atlas = Paths.get(Vue_Edition.parallax_Heart.relativePath).resolve(page.pageModel.atlasName);
		if (atlas.getParent() == null)
			return;

		Vue_Edition.parallax_Heart.relativePath = atlas.getParent().toString();
		page.pageModel.atlasName = atlas.getFileName().toString();
	}

	/** The atlas file of the open page, or null when it has none. */
	public static Path atlasFile()
	{
		String atlasName = Vue_Edition.parallax_Heart.getAtlasName();
		return atlasName == null ? null : Paths.get(Vue_Edition.parallax_Heart.relativePath, atlasName).toAbsolutePath().normalize();
	}
}
