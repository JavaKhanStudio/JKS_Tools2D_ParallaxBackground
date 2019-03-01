package jks.tools2d.parallax.mains;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.heart.Parallax_Utils_Astre;
import jks.tools2d.parallax.heart.Parallax_Utils_Page;
import jks.tools2d.parallax.side.Enum_AstreType;
import jks.tools2d.parallax.test.Enum_ColdNight;
import jks.tools2d.parallax.test.inputs.GVars_Inputs;
import jks.tools2d.parallax.test.inputs.InputProcessus;


public class Testing_Basic extends ApplicationAdapter
{
	
	private final float worldWidth = 40;

	@Override
	public void create () 
	{
		GlobalTimmer.registerTime("TEST");
	    Parallax_Heart.init(worldWidth, new AssetManager());
//	    Parallax_Heart.worldCamera.setToOrtho(false, 100, 100);
//	    Viewport viewport = new ExtendViewport(100,100,Parallax_Heart.worldCamera) ;
//		viewport.apply();
//	    Enum_PageModel_Day.STATU_QUO.loadThemAll();
	    Parallax_Heart.topSquarePercent = 0.3f ; 
	    Parallax_Heart.parallaxMainPage.setDrawingHeight(2.4f);
	    Parallax_Utils_Astre.startAstre(Enum_AstreType.SUN, 10);
		
	    Parallax_Utils_Page.setPage(Enum_ColdNight.COLD_NIGHT.wholePage) ; 
	    Parallax_Utils_Page.setSecondPage(Enum_ColdNight.COLD_WATER.wholePage) ; 
//		Parallax_Utils_Page.setPage(Enum_PageModel_Day.DAY.wholePage) ; 
		
//		GlobalTimmer.getElapse("TEST", "WHOLE", true);
	    Gdx.input.setInputProcessor(new InputProcessus());
	}
	 
	@Override
	public void render () 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		GVars_Inputs.updateInput(delta);
		Parallax_Heart.act(delta);
		Parallax_Heart.renderMainPage() ;
		Parallax_Heart.renderSecondePage() ; 
	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
//		atlas.dispose();
	}
	
}
