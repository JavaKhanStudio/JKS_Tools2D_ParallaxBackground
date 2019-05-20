package jks.tools2d.parallax;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import jks.tools2d.parallax.mains.Testing_Basic;

public class DesktopLauncher_Day 
{
	public static void main (String[] arg) 
	{
		
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(1820, 1080);

		new Lwjgl3Application(new Testing_Basic(), config);
//		new LwjglApplication(new Testing_Basic(), config);
	}
}
