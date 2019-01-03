package jks.tools2d.parallax.editor.mains;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

public class DesktopLauncher_Main 
{
	public static void main (String[] arg) 
	{
		
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 800;
		config.height = 450;
		new LwjglApplication(new Main_Editor(), config);
	}
}
