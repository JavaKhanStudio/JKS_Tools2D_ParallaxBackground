package jks.tools2d.parallax.mains;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.heart.Parrallax_Heart;
import jks.tools2d.parallax.test.Enum_PageModel_Day;


public class Testing_Basic extends ApplicationAdapter
{
	
	
	private TextureAtlas atlas;
	private final float worldWidth = 40;
	private float worldHeight;
	
	private Color clearColor = new Color(0Xbeaf7bff);
	
	@Override
	public void create () 
	{
		worldHeight = Utils_Parralax.calculateOtherDimension(true, worldWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		OrthographicCamera worldCamera = new OrthographicCamera();
	    worldCamera.setToOrtho(false,worldWidth,worldHeight);
	    worldCamera.update();
	    
	    Parrallax_Heart.init(worldCamera, new OrthographicCamera(),Enum_PageModel_Day.DAY.page, worldWidth, worldHeight);
	    Parrallax_Heart.parallaxBackground.setDrawingHeight(6.78f);
//	    Gdx.input.setInputProcessor(new InputProcessus());
	}
	 




	@Override
	public void render () 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		Parrallax_Heart.render(delta);
	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
//		atlas.dispose();
	}
	
}
