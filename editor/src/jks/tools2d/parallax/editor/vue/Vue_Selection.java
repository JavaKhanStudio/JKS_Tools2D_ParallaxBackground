package jks.tools2d.parallax.editor.vue;

import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.ATLAS;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX_PROJECT;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.datas;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.infos;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.relativePath;

import java.io.File;
import java.io.FileFilter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.kryo.io.Input;

import jks.tools2d.filechooser.FC_List;
import jks.tools2d.filechooser.FileChooser_Listener;
import jks.tools2d.libgdxutils.Utils_Scene2D;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_Serialization;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.editor.vue.edition.Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Infos;
import jks.tools2d.parallax.editor.vue.model.AVue_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

public class Vue_Selection extends AVue_Model 
{

	@Override
	public void init() 
	{	
		buildSelection(GVars_Ui.baseSkin) ; 
	}
	
	boolean showParallax = true; 
	boolean showAtlas = true; 
	boolean showFiles = true ; 
	boolean showParallaxProject = true ; 
	
	final float sizeMutlChooser = 0.7f ; 
	public void buildSelection(Skin skin)
	{
		FileChooser_Listener heyLisen = new FileChooser_Listener()
		{
			@Override
			public void choose(FileHandle file)
			{
				selectSigleFile(file) ; 	
			}
			
			@Override
			public void choose(Array<FileHandle> files) {} // NA
			
			@Override
			public void cancel() {} // NA
		};

		
		FC_List chooser = new FC_List(skin,heyLisen) ;
		chooser.setSize(Gdx.graphics.getWidth() * sizeMutlChooser, Gdx.graphics.getHeight() * sizeMutlChooser);
		chooser.setPosition(Gdx.graphics.getWidth()/2 - chooser.getWidth()/2, Gdx.graphics.getHeight()/2 - chooser.getHeight()/2);
		
		Label title = new Label("Select a Paralax (." + FVars_Extensions.PARALLAX + ") to modify it / Or an atlas (." + FVars_Extensions.ATLAS + ") to creat a new",skin) ; 
		title.getStyle().fontColor = Color.LIGHT_GRAY ; 

		title.setAlignment(0, 0);
		title.setSize(chooser.getWidth(), ((Gdx.graphics.getHeight() - chooser.getHeight()) / 2));
		title.setPosition(chooser.getX(), chooser.getY() + chooser.getHeight());
		
		FileFilter filter = new FileFilter() 
		{
            @Override
            public boolean accept(File pathname) 
            {
            	if(pathname.isFile())
            	{
            		String extension = Utils_Scene2D.getExtension(pathname) ; 
                	if(PARALLAX.equals(extension) && showParallax)
                	{return true ;}
                	if(ATLAS.equals(extension) && showAtlas)
                	{return true ;}
                	if(PARALLAX_PROJECT.equals(extension) && showParallaxProject)
                	{return true ;}
            	}
            	else if(showFiles)
            	{return true ;}
            	
            	return false ;
            }		
         };
         
		chooser.setFileFilter(filter);

		String filePath = new File("").getAbsolutePath();
		String relativePath = filePath + "/Files" ; 
		FileHandle relative = new FileHandle(relativePath); 
		chooser.setDirectory(relative);
		
		GVars_Ui.mainUi.addActor(chooser); 
		GVars_Ui.mainUi.addActor(title);
	}
	
	public void selectSigleFile(FileHandle file)
	{
		
		infos = new Project_Infos();
		infos.setPathInfo(file);
		datas = new Project_Data() ; 
		relativePath = infos.projectPath ; 
		
		try
		{
			if(PARALLAX.equals(file.extension()))
				GVars_Heart_Editor.changeVue(new Vue_Edition(GVars_Serialization.kryo.readObject(new Input(file.read()),WholePage_Model.class)), true);
			else if(ATLAS.equals(file.extension()))
				GVars_Heart_Editor.changeVue(new Vue_Edition(new TextureAtlas(file)), true);
			else if(PARALLAX_PROJECT.equals(file.extension()))
				GVars_Heart_Editor.changeVue(new Vue_Edition(GVars_Serialization.objectMapper.readValue(file.file(), Project_Data.class)), true);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}
	

	@Override
	public void destroy() 
	{	
		GVars_Ui.reset();
	}

	@Override
	public void restart() 
	{	
	}

	@Override
	public void update(float delta)
	{	
	}

	@Override
	public void render() 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		GVars_Ui.mainUi.draw() ;	
	}

	@Override
	public void reciveFiles(String[] files)
	{
		if(files.length == 1)
		{
			FileHandle handle = new FileHandle(files[0]); 
	    	selectSigleFile(handle) ; 
		}	
	}

	@Override
	public void resize(int x, int y) 
	{

	}

}
