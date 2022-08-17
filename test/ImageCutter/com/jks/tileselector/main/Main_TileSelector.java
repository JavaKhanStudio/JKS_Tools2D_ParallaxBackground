package com.jks.tileselector.main;


import static com.jks.tileselector.vars.FVars_TileSelector.*;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.jks.tileselector.Image_Cutter;

import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;

// Please note that on macOS your application needs to be started with the -XstartOnFirstThread JVM argument
public class Main_TileSelector {
	public static void main (String[] arg) 
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		
		config.setWindowedMode(sizeX + sizeBuffer,sizeY + sizeBuffer);
		config.setDecorated(true);
		config.setWindowPosition(0, 25);
		config.setResizable(false);
		
		config.setWindowListener(new Lwjgl3WindowAdapter() 
		{
            @Override
            public void filesDropped(String[] files) 
            {
            	GVars_Heart_Editor.vue.reciveFiles(files) ;
            }
        });
		
		new Lwjgl3Application(new Image_Cutter(), config);
	}
}
