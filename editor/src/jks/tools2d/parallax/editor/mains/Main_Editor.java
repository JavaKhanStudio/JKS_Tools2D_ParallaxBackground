package jks.tools2d.parallax.editor.mains;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;



public class Main_Editor extends ApplicationAdapter 
{
	@Override
	public void create () 
	{

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
	}
}
