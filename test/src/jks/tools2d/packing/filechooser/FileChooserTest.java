package jks.tools2d.packing.filechooser;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public class FileChooserTest extends ApplicationAdapter 
{
	public static void main (String[] arg) 
	{
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();

		config.width = 1300;
		config.height = 800;

		new LwjglApplication(new FileChooserTest(), config);
	}
	
	@Override
 	public void create () 
 	{
		Skin skin = new Skin(Gdx.files.internal("data/uiskin.json"));
 	}
	
	
	
	

}
