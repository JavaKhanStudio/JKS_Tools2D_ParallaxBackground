package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.JSON_PARALLAX;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX_PROJECT;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width;

import java.awt.Desktop;
import java.net.URI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab; 

public class VE_Tab_ParallaxConfig_Informations extends Tab
{

	// TODO
	VisTextArea infos ; 
	private Table mainTable ; 
	VisTextButton goTutorialFr, goTutorialEng ; 
	
	VE_Tab_ParallaxConfig_Informations()
	{
		super(false, false);
		mainTable = new Table() ; 
		mainTable.setLayoutEnabled(false);
		buildTextureSelector() ; 
	}
	
	String textInforation = 
			"Hello ! This tools was made to help you build beautiful parallax with ease"
			+ "\n\nYou can drag and drop project or export files anywhere too open them "
			+ "\n(." + PARALLAX + " OR ." + JSON_PARALLAX + " OR ." + PARALLAX_PROJECT + ")"
			+ "\n\nIf you want to import a group of picture, you can use .atlas, or drag single/multiple png files, as no other format are accepted" 
			+ "\n\nFinaly for more informations check the tutorials. Those buttons down bellow will guide you to them!"
			+ "\n\nFor any resquest, you can contact me at JavaKhanStudio@gmail.com"
			; 
	
	public void buildTextureSelector()
	{
		infos = new VisTextArea(textInforation) ;
		//infos.setDisabled(true);
		infos.setTouchable(Touchable.disabled);
		infos.setColor(new Color(0.35f,0.35f,0.35f,1));
		
		goTutorialEng = new VisTextButton("Tutorial (ENG)");
		goTutorialEng.addListener(new ChangeListener() 
		{
			@Override
			public void changed (ChangeEvent event, Actor actor) 
			{
				try
				{Desktop.getDesktop().browse(new URI("https://www.youtube.com/watch?v=FVxGCaReshc"));} 
				catch (Exception e) {} 
			}
		});
		
		goTutorialFr = new VisTextButton("Tutoriel (FR)");
		goTutorialFr.addListener(new ChangeListener() 
		{
			@Override
			public void changed (ChangeEvent event, Actor actor) 
			{
				try
				{Desktop.getDesktop().browse(new URI("https://www.youtube.com/watch?v=AkKpn8qj_pA"));} 
				catch (Exception e) {} 
			}
		});
		

		mainTable.add(infos) ;
		mainTable.add(goTutorialEng) ;
		mainTable.add(goTutorialFr) ;
		
	//	mainTable.add(container).expand().fill();
		
	}
	
	int decalX,decalY ;
	int buttonSizeX, buttonSizeY ; 
	
	public void resize()
	{
		decalX = size_Bloc_Selection_Parallax_Width/23 ; 
		decalY = Gdx.graphics.getHeight()/10 ; 
		mainTable.setWidth(size_Bloc_Selection_Parallax_Width);
		infos.setWidth(size_Bloc_Selection_Parallax_Width - (decalX * 2));		
		infos.setHeight(Gdx.graphics.getHeight()/1.5f);
		infos.setPosition(decalX, Gdx.graphics.getHeight() - infos.getHeight() - decalY);
		
		buttonSizeX = (size_Bloc_Selection_Parallax_Width - decalX * 3)/2 ; 
		buttonSizeY = (int) (buttonSizeX/2.5f) ; 
		
		goTutorialEng.setSize(buttonSizeX, buttonSizeY);
		goTutorialEng.setPosition(decalX, infos.getY() + buttonSizeY);
		
		goTutorialFr.setSize(buttonSizeX, buttonSizeY);
		goTutorialFr.setPosition(buttonSizeX + decalX * 2,  infos.getY() + buttonSizeY);
		
		
		
	}

	@Override
	public String getTabTitle()
	{return "INFOS";}

	@Override
	public Table getContentTable()
	{
		resize() ; 
		return mainTable;
	}	
	
}