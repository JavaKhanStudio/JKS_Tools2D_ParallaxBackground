package jks.tools2d.parallax.editor.mains;

import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectInfos;

import java.io.File;

import com.badlogic.gdx.Gdx;

import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Infos;
import jks.tools2d.parallax.heart.GVars_Serialization; 

public class Main_Editor_Test extends Main_Editor 
{
	
	@Override
	public void create () 
	{
		GVars_Heart_Editor.init();
		Gdx.graphics.setVSync(true);
		directTestParallax() ; 
	}


	void directTestParallax()
	{
		projectInfos = new Project_Infos();
		projectInfos.projectName = "whole" ; 
		projectInfos.projectPath = "D:/Users/Simon/Documents/JKS_Tools2D_ParallaxBackground/editor/Files/Parallax" ; 
		
		projectDatas = new Project_Data() ; 

		try
		{
			GVars_Vue_Edition.relativePath = "../editor/Files/Parallax" ; 
			Project_Data project = GVars_Serialization.objectMapper.readValue(new File(GVars_Vue_Edition.relativePath + "/whole." + FVars_Extensions.PARALLAX_PROJECT), Project_Data.class) ; 
			
			GVars_Heart_Editor.changeVue(new Vue_Edition(project),true) ;  
		} 
		catch (Exception e)
		{e.printStackTrace();} 
		
	}
	
}
