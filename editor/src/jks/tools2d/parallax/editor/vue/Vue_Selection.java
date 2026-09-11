package jks.tools2d.parallax.editor.vue;

import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.ATLAS;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.JSON_PARALLAX;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX_PROJECT;
import static jks.tools2d.parallax.editor.gvars.GVars_UI.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectInfos;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.relativePath;

import java.io.File;
import java.io.FileFilter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kotcrab.vis.ui.util.dialog.Dialogs;

import jks.tools2d.filechooser.FC_List;
import jks.tools2d.filechooser.FileChooser_Listener;
import jks.tools2d.libgdxutils.Utils_Scene2D;
import jks.tools2d.parallax.editor.gvars.EditorPaths;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_Serialization_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Infos;
import jks.tools2d.parallax.editor.vue.model.AVue_Model;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;

/** Start screen: pick a project, a parallax or an atlas, or start an empty project. */
public class Vue_Selection extends AVue_Model
{
	private static final float sizeMultChooser = 0.7f;

	@Override
	public void init()
	{
		FileHandle filesRoot = Gdx.files.absolute(EditorPaths.filesRoot().toString());

		FC_List chooser = new FC_List(baseSkin, new FileChooser_Listener()
		{
			@Override
			public void choose(FileHandle file)
			{selectSingleFile(file);}

			@Override
			public void choose(Array<FileHandle> files)
			{}

			@Override
			public void cancel()
			{}
		});
		chooser.setSize(Gdx.graphics.getWidth() * sizeMultChooser, Gdx.graphics.getHeight() * sizeMultChooser);
		chooser.setPosition(Gdx.graphics.getWidth() / 2f - chooser.getWidth() / 2, Gdx.graphics.getHeight() / 2f - chooser.getHeight() / 2);
		chooser.setFileFilter(buildFileFilter());
		chooser.setDirectory(filesRoot);

		TextButton createNew = new TextButton("NEW", baseSkin);
		createNew.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				projectInfos = new Project_Infos();
				projectInfos.projectName = "newProject";
				projectInfos.projectPath = filesRoot.path();
				projectDatas = new Project_Data();
				relativePath = projectInfos.projectPath;
				GVars_Heart_Editor.changeVue(new Vue_Edition(), true);
			}
		});
		int buttonSize = (int) (chooser.getX() * 0.75f);
		createNew.setBounds((chooser.getX() - buttonSize) / 2, Gdx.graphics.getHeight() / 2f - buttonSize / 2f, buttonSize, buttonSize);

		LabelStyle titleStyle = new LabelStyle(baseSkin.get(LabelStyle.class));
		titleStyle.fontColor = Color.LIGHT_GRAY;
		Label title = new Label("Open a project (." + PARALLAX_PROJECT + "), a parallax (." + PARALLAX + " / ." + JSON_PARALLAX + ")"
				+ "\nor an atlas (." + ATLAS + ") to create a new project from it", titleStyle);
		title.setAlignment(Align.center);
		title.setSize(chooser.getWidth(), (Gdx.graphics.getHeight() - chooser.getHeight()) / 2);
		title.setPosition(chooser.getX(), chooser.getY() + chooser.getHeight());

		GVars_UI.mainUi.addActor(createNew);
		GVars_UI.mainUi.addActor(chooser);
		GVars_UI.mainUi.addActor(title);
	}

	private static FileFilter buildFileFilter()
	{
		return file ->
		{
			if (!file.isFile())
				return true;

			String extension = Utils_Scene2D.getExtension(file);
			return PARALLAX.equals(extension) || ATLAS.equals(extension) || PARALLAX_PROJECT.equals(extension) || JSON_PARALLAX.equals(extension);
		};
	}

	/** Opens the edition view for a project, parallax or atlas file. Returns false if it is none of those or unreadable. */
	public static boolean selectSingleFile(FileHandle file)
	{
		String extension = file.extension();
		if (!PARALLAX.equals(extension) && !JSON_PARALLAX.equals(extension) && !ATLAS.equals(extension) && !PARALLAX_PROJECT.equals(extension))
			return false;

		Object toOpen;
		try
		{
			if (PARALLAX.equals(extension))
				toOpen = Utils_Page.loadPage(file);
			else if (JSON_PARALLAX.equals(extension))
				toOpen = GVars_Serialization_Editor.objectMapper.readValue(file.file(), WholePage_Model.class);
			else if (ATLAS.equals(extension))
				toOpen = new TextureAtlas(file);
			else
				toOpen = GVars_Serialization_Editor.objectMapper.readValue(file.file(), Project_Data.class);
		}
		catch (Exception e)
		{
			Gdx.app.error("Vue_Selection", "Cannot open " + file, e);
			if (GVars_UI.mainUi != null)
				Dialogs.showErrorDialog(GVars_UI.mainUi, "Cannot open " + file.name(), e);
			return false;
		}

		projectInfos = new Project_Infos();
		projectInfos.setPathInfo(file);
		projectDatas = toOpen instanceof Project_Data ? (Project_Data) toOpen : new Project_Data();
		relativePath = projectInfos.projectPath;
		GVars_Heart_Editor.changeVue(new Vue_Edition(toOpen), true);
		return true;
	}

	@Override
	public void destroy()
	{GVars_UI.reset();}

	@Override
	public void update(float delta)
	{GVars_UI.mainUi.act(delta);}

	@Override
	public void render()
	{
		ScreenUtils.clear(0, 0, 0, 1);
		GVars_UI.mainUi.draw();
	}

	@Override
	public void receiveFiles(String[] files)
	{
		if (files.length == 1)
			selectSingleFile(new FileHandle(new File(files[0])));
	}

	@Override
	public void resize(int width, int height)
	{}
}
