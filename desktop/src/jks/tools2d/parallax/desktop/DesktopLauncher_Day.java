package jks.tools2d.parallax.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import jks.tools2d.parallax.mains.Testing_Basic;

public class DesktopLauncher_Day 
{
	public static void main (String[] arg) 
	{
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();

//		config.width = 800;
//		config.height = 550;
		
		config.width = 1300;
		config.height = 800;
		
//		config.width = LwjglApplicationConfiguration.getDesktopDisplayMode().width;
//        config.height = LwjglApplicationConfiguration.getDesktopDisplayMode().height;
//        config.fullscreen = true;
		
		new LwjglApplication(new Testing_Basic(), config);
	}
}
