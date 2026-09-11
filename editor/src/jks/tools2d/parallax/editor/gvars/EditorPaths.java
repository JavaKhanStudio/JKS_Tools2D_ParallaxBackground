package jks.tools2d.parallax.editor.gvars;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
}
