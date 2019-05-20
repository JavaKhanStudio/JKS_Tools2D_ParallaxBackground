package jks.tools2d.parallax.editor.vue.edition.data;

import com.badlogic.gdx.files.FileHandle;

public class Project_Infos
{
	public String projectName ; 
	public String projectPath ; 

	public void setPathInfo(FileHandle filehandle)
	{
		projectName = filehandle.name().substring(0,filehandle.name().lastIndexOf(".")) ; 
		projectPath = filehandle.path().substring(0,filehandle.path().lastIndexOf("/")) ; 
	}

	
}
