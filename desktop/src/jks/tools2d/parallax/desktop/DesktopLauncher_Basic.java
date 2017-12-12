package jks.tools2d.parallax.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import jks.tools2d.parallax.mains.Testing_Basic;

public class DesktopLauncher_Basic 
{
	public static void main (String[] arg) 
	{
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 800;
		config.height = 450;
		new LwjglApplication(new Testing_Basic(), config);
	}
}
