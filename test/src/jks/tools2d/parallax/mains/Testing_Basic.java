package jks.tools2d.parallax.mains;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.heart.Parallax_Utils_Page;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.test.Enum_PageModel_Day;
import jks.tools2d.parallax.test.inputs.GVars_Inputs;
import jks.tools2d.parallax.test.inputs.InputProcessus;


public class Testing_Basic extends ApplicationAdapter
{
	
	private final float worldWidth = 70;

	@Override
	public void create () 
	{
		
	    Parallax_Heart.init(worldWidth);
		Parallax_Heart.parallaxMainPage.setDrawingHeight(6.78f);
		Parallax_Utils_Page.setPage(Enum_PageModel_Day.DAY.page) ; 
    
	    Gdx.input.setInputProcessor(new InputProcessus());
	}
	 




	@Override
	public void render () 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		GVars_Inputs.updateInput(delta);
		Parallax_Heart.act(delta);
		Parallax_Heart.renderMainPage(delta);
	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
//		atlas.dispose();
	}
	
}
