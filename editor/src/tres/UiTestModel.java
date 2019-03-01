package tres;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import jks.tools2d.parallax.editor.gvars.GVars_Ui;

public class UiTestModel extends ApplicationAdapter
{
	
	@Override
	public void create () 
	{
		GVars_Ui.init();
	}
	
	
	@Override
	public void render () 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		GVars_Ui.mainUi.draw();
	}
	
	@Override
	public void dispose() 
	{
//		super.dispose();
//		atlas.dispose();
	}
}
