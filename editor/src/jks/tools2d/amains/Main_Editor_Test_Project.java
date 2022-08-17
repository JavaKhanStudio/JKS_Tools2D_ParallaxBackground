package jks.tools2d.amains;

import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectInfos;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.kryo.io.Input;

import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Infos;
import jks.tools2d.parallax.heart.GVars_Serialization;
import jks.tools2d.parallax.pages.WholePage_Model; 

public class Main_Editor_Test_Project extends Main_Editor 
{
	
	@Override
	public void create () 
	{
		GVars_Heart_Editor.init();
		Gdx.graphics.setVSync(true);
		directTestParallax() ; 
	}

	void directTestAtlas()
	{
		projectInfos = new Project_Infos();
		projectInfos.projectName = "OneNight" ; 
		projectInfos.projectPath = "C:/Users/Simon/Documents/JKS_Tools2D_ParallaxBackground/editor/test" ; 
		FileHandle handler = new FileHandle("../editor/Files/Atlas/OneNight.atlas") ; 
		TextureAtlas textures = new TextureAtlas(handler);
		
		projectInfos = new Project_Infos();
		projectInfos.setPathInfo(handler);
		GVars_Heart_Editor.changeVue(new Vue_Edition(textures), true) ;  	
	}
	
	void directTestParallax()
	{
		projectInfos = new Project_Infos();
		projectInfos.projectName = "whole" ; 
		projectInfos.projectPath = "D:/Users/Simon/Documents/JKS_Tools2D_ParallaxBackground/editor/Files/Parallax" ; 
		
		projectDatas = new Project_Data() ; 
		Input input;
		try
		{
			input = new Input(new FileInputStream("../editor/Files/Parallax/whole." + FVars_Extensions.PARALLAX));
			GVars_Vue_Edition.relativePath = "../editor/Files/Parallax" ; 
			GVars_Heart_Editor.changeVue(new Vue_Edition(GVars_Serialization.kryo.readObject(input,WholePage_Model.class)),true) ;  
		} 
		catch (FileNotFoundException e)
		{e.printStackTrace();}
	}
	
}
