package jks.tools2d.parallax.editor.vue;

import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.ATLAS;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.JSON_PARALLAX;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.PARALLAX_PROJECT;
import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectInfos;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.relativePath;

import java.io.File;
import java.io.FileFilter;

import org.apache.commons.lang3.StringUtils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.kryo.io.Input;

import jks.tools2d.filechooser.FC_List;
import jks.tools2d.filechooser.FileChooser_Listener;
import jks.tools2d.libgdxutils.Utils_Scene2D;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.editor.vue.edition.Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Infos;
import jks.tools2d.parallax.editor.vue.model.AVue_Model;
import jks.tools2d.parallax.heart.GVars_Serialization;
import jks.tools2d.parallax.pages.WholePage_Model;

public class Vue_Selection extends AVue_Model 
{

	boolean showParallax = true; 
	boolean showParallaxJson = true ;
	boolean showAtlas = true; 
	boolean showFiles = true ; 
	boolean showParallaxProject = true ; 
	
	final float sizeMutlChooser = 0.7f ; 
	
	FC_List chooser ; 
	TextButton creatNew ; 
	int buttonSize ; 
	
	@Override
	public void init() 
	{	
		buildSelection(GVars_Ui.baseSkin) ;	
	}
	
	public void buildSelection(Skin skin)
	{
		
		String filePath = new File("").getAbsolutePath();
		String fileRelativePath = filePath + "/Files" ; 
		FileHandle relative = new FileHandle(fileRelativePath); 
		
		chooser = new FC_List(skin,buildFileChooser_Listener()) ;
		chooser.setSize(Gdx.graphics.getWidth() * sizeMutlChooser, Gdx.graphics.getHeight() * sizeMutlChooser);
		chooser.setPosition(Gdx.graphics.getWidth()/2 - chooser.getWidth()/2, Gdx.graphics.getHeight()/2 - chooser.getHeight()/2);
		
		creatNew = new TextButton("NEW",baseSkin) ; 
		creatNew.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				projectInfos = new Project_Infos();
				projectInfos.setPathInfo(new FileHandle(fileRelativePath + "/newProject."));
				projectDatas = new Project_Data() ; 
				relativePath = projectInfos.projectPath ; 	
				GVars_Heart_Editor.changeVue(new Vue_Edition(), true);
			}
		}) ; 	
		
		buttonSize = (int) (chooser.getX() * 0.75f) ; 
		creatNew.setBounds((chooser.getX() - buttonSize)/2, Gdx.graphics.getHeight()/2 - buttonSize/2, buttonSize, buttonSize);
		
		Label title = new Label("You can select a Project : (." + FVars_Extensions.PARALLAX + ")\n Paralax : (." + FVars_Extensions.PARALLAX + " / ." +  FVars_Extensions.JSON_PARALLAX + ") \n Or an atlas (." + FVars_Extensions.ATLAS + ") to creat a new project",skin) ; 
		title.getStyle().fontColor = Color.LIGHT_GRAY ; 

		title.setAlignment(0, 0);
		title.setSize(chooser.getWidth(), ((Gdx.graphics.getHeight() - chooser.getHeight()) / 2));
		title.setPosition(chooser.getX(), chooser.getY() + chooser.getHeight());
		 
		chooser.setFileFilter(buildFileFilter());	
		chooser.setDirectory(relative);
		
		GVars_Ui.mainUi.addActor(creatNew); 
		GVars_Ui.mainUi.addActor(chooser); 
		GVars_Ui.mainUi.addActor(title);
	}
	
	public FileChooser_Listener buildFileChooser_Listener()
	{
		FileChooser_Listener heyLisen = new FileChooser_Listener()
		{
			@Override
			public void choose(FileHandle file)
			{selectSingleFile(file) ;}
			
			@Override
			public void choose(Array<FileHandle> files) {} // NA
			
			@Override
			public void cancel() {} // NA
		};
		
		return heyLisen ; 
	}
	
	public FileFilter buildFileFilter()
	{
		return new FileFilter() 
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
                	if(JSON_PARALLAX.equals(extension) && showParallaxJson)
                	{return true ;}
            	}
            	else if(showFiles)
            	{return true ;}
            	
            	return false ;
            }		
         };
	}
	
	public static boolean selectSingleFile(FileHandle file)
	{
		
		if(StringUtils.isEmpty(file.extension()))
			return false ; 
		
		try
		{	
			projectInfos = new Project_Infos();
			projectInfos.setPathInfo(file);
			projectDatas = new Project_Data() ; 
			relativePath = projectInfos.projectPath ; 
			
			if(PARALLAX.equals(file.extension()))
				GVars_Heart_Editor.changeVue(new Vue_Edition(GVars_Serialization.kryo.readObject(new Input(file.read()),WholePage_Model.class)), true);
			else if(JSON_PARALLAX.equals(file.extension()))
				GVars_Heart_Editor.changeVue(new Vue_Edition(GVars_Serialization.objectMapper.readValue(file.file(), WholePage_Model.class)), true);
			else if(ATLAS.equals(file.extension()))
				GVars_Heart_Editor.changeVue(new Vue_Edition(new TextureAtlas(file)), true);
			else if(PARALLAX_PROJECT.equals(file.extension()))
				GVars_Heart_Editor.changeVue(new Vue_Edition(GVars_Serialization.objectMapper.readValue(file.file(), Project_Data.class)), true);
			else
				return false ; 
		}
		catch(Exception e)
		{e.printStackTrace(); return false ;}
		
		return true ; 
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
	    	selectSingleFile(handle) ; 
		}	
	}

	@Override
	public void resize(int x, int y) 
	{

	}

}
