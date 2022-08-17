package jks.tools2d.parallax;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

import jks.tools2d.amains.Main_Editor_Test;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.vue.edition.Vue_Edition;

public class Launcher_Editor_Test_Project 
{
	public static void main (String[] arg) 
	{
		try
		{
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setWindowedMode(1300, 800);
			config.useVsync(true);
			Vue_Edition.isVSynch = true ; 
			config.setBackBufferConfig(8, 8, 8, 8, 32, 2, 4);	
	
			config.setWindowListener(new Lwjgl3WindowAdapter() 
			{
	            @Override
	            public void filesDropped(String[] files) 
	            {
	            	GVars_Heart_Editor.vue.reciveFiles(files) ; 
	            }
	        });
	
			new Lwjgl3Application(new Main_Editor_Test(), config);		
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		System.exit(0);
	}
}
