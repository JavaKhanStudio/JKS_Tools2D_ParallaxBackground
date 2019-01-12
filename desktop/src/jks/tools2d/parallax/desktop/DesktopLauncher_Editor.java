package jks.tools2d.parallax.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import jks.tools2d.parallax.editor.mains.Main_Editor;

public class DesktopLauncher_Editor 
{
	public static void main (String[] arg) 
	{
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();

		config.width = 1300;
		config.height = 800;

		new LwjglApplication(new Main_Editor(), config);
	}
}
