package jks.tools2d.parallax;

import javax.swing.JOptionPane;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.mains.Main_Editor_Test;

public class Launcher_Editor_Test 
{
	public static void main (String[] arg) 
	{
		try
		{
			Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
			config.setWindowedMode(1300, 800);
	
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
			JOptionPane.showMessageDialog(null, e.getMessage());
		}
	}
}
