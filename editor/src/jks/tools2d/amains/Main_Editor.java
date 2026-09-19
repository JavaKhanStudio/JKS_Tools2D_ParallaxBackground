package jks.tools2d.amains;

import java.io.File;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;

import jks.tools2d.filewatch.FileWatchService;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.driver.EditorDriver;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.vue.Vue_Selection;
import jks.tools2d.parallax.heart.Gvars_Parallax;

public class Main_Editor extends ApplicationAdapter
{
	private final String fileToOpen;
	private final int driverPort;
	private EditorDriver driver;

	public Main_Editor()
	{this(null, 0);}

	/**
	 * @param fileToOpen project/parallax/atlas to open right away, or null to show the file selection.
	 * @param driverPort port {@link EditorDriver} listens on, 0 for none.
	 */
	public Main_Editor(String fileToOpen, int driverPort)
	{
		this.fileToOpen = fileToOpen;
		this.driverPort = driverPort;
	}

	@Override
	public void create()
	{
		GVars_Heart_Editor.init();
		if (driverPort > 0)
			driver = EditorDriver.start(driverPort);

		if (fileToOpen != null && Vue_Selection.selectSingleFile(Gdx.files.absolute(new File(fileToOpen).getAbsolutePath())))
			return;

		GVars_Heart_Editor.changeVue(new Vue_Selection(), true);
	}

	@Override
	public void render()
	{
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);

		GVars_Heart_Editor.vue.update(delta);
		GVars_Heart_Editor.vue.render();
		if (driver != null)
			driver.afterRender();
	}

	@Override
	public void resize(int width, int height)
	{
		if (width <= 0 || height <= 0)
			return;

		GVars_UI.mainUi.getViewport().update(width, height, true);
		if (GVars_Heart_Editor.vue != null)
			GVars_Heart_Editor.vue.resize(width, height);
	}

	@Override
	public void dispose()
	{
		if (driver != null)
			driver.stop();
		if (GVars_Heart_Editor.vue != null)
			GVars_Heart_Editor.vue.destroy();
		FileWatchService.shutdown();
		GVars_UI.dispose();
		Utils_Interface.disposeTextures();
		Gvars_Parallax.getManager().dispose();
	}
}
