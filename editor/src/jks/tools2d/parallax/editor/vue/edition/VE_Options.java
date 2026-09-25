package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectInfos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;

import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Saving;

/** Top bar: project folder and name, save project / export buttons and export formats. */
public class VE_Options extends Table
{
	private static final float decal = 3;
	private static final float buttonSize = 50;
	private static final float textHeight = 22;

	public static TextField parallaxPath, parallaxName;
	public static VisCheckBox formatLibGDX, formatJson, forceExport;

	public VE_Options()
	{
		float textWidth = Gdx.graphics.getWidth() / 4f;
		float pathWidth = textWidth * 2;

		ImageButton savingProject = Utils_Interface.buildSquareButton("editor/interfaces/saveProject.png", buttonSize);
		savingProject.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{Utils_Saving.saving_Parallax_Project(parallaxPath.getText(), parallaxName.getText(), true);}
		});

		ImageButton savingExport = Utils_Interface.buildSquareButton("editor/interfaces/saveParallax.png", buttonSize);
		savingExport.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{Utils_Saving.saving_Parallax(parallaxPath.getText(), parallaxName.getText());}
		});

		savingProject.setName("options.saveProject");
		savingExport.setName("options.export");
		savingProject.setBounds(Gdx.graphics.getWidth() - buttonSize * 3, Gdx.graphics.getHeight() - (buttonSize + decal), buttonSize, buttonSize);
		savingExport.setBounds(Gdx.graphics.getWidth() - buttonSize * 3, Gdx.graphics.getHeight() - (buttonSize + decal) * 2, buttonSize, buttonSize);

		formatLibGDX = new VisCheckBox("LibGDX");
		formatLibGDX.setChecked(true);
		formatJson = new VisCheckBox("JSON");
		forceExport = new VisCheckBox("F.Export");
		formatLibGDX.setName("options.formatLibgdx");
		formatJson.setName("options.formatJson");
		forceExport.setName("options.forceExport");

		parallaxPath = new TextField("", GVars_UI.baseSkin)
		{
			@Override
			public float getPrefWidth()
			{return super.getPrefWidth() * 3;}
		};
		parallaxName = new TextField("", GVars_UI.baseSkin);
		parallaxPath.setName("options.path");
		parallaxName.setName("options.name");

		Table projectPathTable = new Table();
		projectPathTable.setBounds(savingProject.getX() - pathWidth - decal, savingProject.getY() + textHeight / 2 - decal, pathWidth, textHeight);
		projectPathTable.add(new VisLabel("Project Path : "));
		projectPathTable.add(parallaxPath);

		Table projectNameTable = new Table();
		projectNameTable.setBounds(savingExport.getX() - textWidth - decal, savingExport.getY() + textHeight / 2 - decal, textWidth, textHeight);
		projectNameTable.add(new VisLabel("Project Name : "));
		projectNameTable.add(parallaxName).right();

		Table formatTable = new Table();
		formatTable.setBounds(savingExport.getWidth() + savingExport.getX(), savingExport.getY(), 100, savingExport.getHeight() + savingProject.getHeight());
		formatTable.add(new VisLabel("Exp Format")).left().row();
		formatTable.add(formatLibGDX).left().row();
		formatTable.add(formatJson).left().row();
		formatTable.add(forceExport).left();

		setInfos();

		addActor(savingProject);
		addActor(savingExport);
		addActor(projectPathTable);
		addActor(projectNameTable);
		addActor(formatTable);
	}

	public void setInfos()
	{
		parallaxPath.setText(projectInfos.projectPath);
		parallaxName.setText(projectInfos.projectName);
	}
}
