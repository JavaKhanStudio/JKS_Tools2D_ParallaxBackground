package jks.tools2d.parallax;

import java.net.URISyntaxException;

import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

import jks.tools2d.parallax.debug.GVars_Debug;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.mains.Main_Editor;
import jks.tools2d.parallax.editor.vue.edition.Vue_Edition;

public class Launcher_Editor 
{
	
	public static String launchingValue = "" ; 
	
	public static void main (String[] arg) throws URISyntaxException 
	{
		if(arg.length > 0)
			launchingValue = arg[0] ; 
		
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(1300, 800);
		config.useOpenGL3(true, 3, 2);
		config.setTitle("Parallax");
		config.setWindowIcon("skins/uis/parallaxIcon.png");
		config.setResizable(false);
		config.useVsync(true);

//		new DisplayMode(1920,1080,60,15)
//		config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayModes()[Lwjgl3ApplicationConfiguration.getDisplayModes().length - 1]);
		Vue_Edition.isVSynch = true ; 
		
		config.setBackBufferConfig(8, 8, 8, 8, 16, 2, 0);
		GVars_Debug.inDebug = false ; 
		
		config.setWindowListener(new Lwjgl3WindowAdapter() 
		{
            @Override
            public void filesDropped(String[] files) 
            {
            	GVars_Heart_Editor.vue.reciveFiles(files) ; 
            }
        });
		
		Lwjgl3Application application = new Lwjgl3Application(new Main_Editor(), config);
	
		System.exit(0);
	}
}
