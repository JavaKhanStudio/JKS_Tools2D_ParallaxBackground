package jks.tools2d.colorplay;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;

import jks.tools2d.packing.filechooser.FileChooserTest;

public class ChangeColorOnTextureTest extends ApplicationAdapter 
{

	public static Texture texture ; 
	
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
		texture = new Texture("single/chiot.png") ;
 	}
	
	@Override
	public void render () 
	{
	    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);	  
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		

	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
//		atlas.dispose();
	}
	
	
	
}
