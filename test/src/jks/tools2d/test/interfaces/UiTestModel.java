package jks.tools2d.test.interfaces;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import jks.tools2d.parallax.editor.gvars.GVars_UI;

public class UiTestModel extends ApplicationAdapter
{
	
	@Override
	public void create () 
	{
		GVars_UI.init();
	}
	
	
	@Override
	public void render () 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		GVars_UI.mainUi.act(delta);
		GVars_UI.mainUi.draw();
	}
	
	@Override
	public void dispose() 
	{
//		super.dispose();
//		atlas.dispose();
	}
}
