package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.JSON_PARALLAX;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX_PROJECT;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.parallax.editor.gvars.GVars_UI;

/** Help text and links to the video tutorials. */
public class VE_Tab_Meta_Informations extends Tab
{
	private static final String TEXT = "Hello! This tool helps you build beautiful parallax backgrounds with ease."
			+ "\n\nDrag and drop a project or an export anywhere to open it"
			+ "\n(." + PARALLAX + ", ." + JSON_PARALLAX + " or ." + PARALLAX_PROJECT + ")."
			+ "\n\nTo import pictures, use an .atlas, or drag one or more .png files: no other format is accepted."
			+ "\n\nFor more information, check the tutorials with the buttons below."
			+ "\n\nFor any request, contact me at JavaKhanStudio@gmail.com";

	private final Table mainTable = new Table();
	private final TextArea infos;
	private final VisTextButton goTutorialFr, goTutorialEng;

	VE_Tab_Meta_Informations()
	{
		super(false, false);
		mainTable.setLayoutEnabled(false);

		infos = new TextArea(TEXT, GVars_UI.baseSkin);
		infos.setTouchable(Touchable.disabled);
		infos.setColor(new Color(0.35f, 0.35f, 0.35f, 1));

		goTutorialEng = linkButton("Tutorial (ENG)", "https://www.youtube.com/watch?v=FVxGCaReshc");
		goTutorialFr = linkButton("Tutoriel (FR)", "https://www.youtube.com/watch?v=AkKpn8qj_pA");

		mainTable.addActor(infos);
		mainTable.addActor(goTutorialEng);
		mainTable.addActor(goTutorialFr);
	}

	private static VisTextButton linkButton(String text, String url)
	{
		VisTextButton button = new VisTextButton(text);
		button.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{Gdx.net.openURI(url);}
		});
		return button;
	}

	/** Manual layout (the table layout is off), from the size of the left panel. */
	public void resize()
	{
		int decalX = size_Bloc_Selection_Parallax_Width / 23;
		int decalY = Gdx.graphics.getHeight() / 10;

		infos.setWidth(size_Bloc_Selection_Parallax_Width - decalX * 2);
		infos.setHeight(Gdx.graphics.getHeight() / 1.9f);
		infos.setPosition(decalX, Gdx.graphics.getHeight() - infos.getHeight() - decalY);

		int buttonSizeX = (size_Bloc_Selection_Parallax_Width - decalX * 3) / 2;
		int buttonSizeY = (int) (buttonSizeX / 2.5f);

		goTutorialEng.setSize(buttonSizeX, buttonSizeY);
		goTutorialEng.setPosition(decalX, infos.getY() - buttonSizeY);

		goTutorialFr.setSize(buttonSizeX, buttonSizeY);
		goTutorialFr.setPosition(buttonSizeX + decalX * 2, infos.getY() - buttonSizeY);
	}

	@Override
	public String getTabTitle()
	{return "INFOS";}

	@Override
	public Table getContentTable()
	{
		resize();
		return mainTable;
	}
}
