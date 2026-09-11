package jks.tools2d.parallax;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

import jks.tools2d.amains.Main_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;

/** Desktop entry point. An optional argument opens that .plax/.jplax/.plaxpj/.atlas file directly. */
public class Launcher_Editor
{
	public static void main(String[] args)
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Parallax Editor");
		config.setWindowedMode(1300, 800);
		config.setWindowIcon("skins/uis/parallaxIcon.png");
		config.setWindowSizeLimits(1100, 680, -1, -1);
		config.useVsync(true);
		config.setBackBufferConfig(8, 8, 8, 8, 16, 2, 0);
		config.setWindowListener(new Lwjgl3WindowAdapter()
		{
			@Override
			public void filesDropped(String[] files)
			{
				if (GVars_Heart_Editor.vue != null)
					GVars_Heart_Editor.vue.receiveFiles(files);
			}
		});

		new Lwjgl3Application(new Main_Editor(args.length > 0 ? args[0] : null), config);
	}
}
