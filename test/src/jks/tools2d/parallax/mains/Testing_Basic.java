package jks.tools2d.parallax.mains;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;

import jks.tools2d.parallax.data.Enum_ColdNight;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.inputs.GVars_Inputs;
import jks.tools2d.parallax.inputs.InputProcessus;

import static jks.tools2d.parallax.gvars.GVars_Heart_Testing.parallax_Heart; 
import static jks.tools2d.parallax.gvars.GVars_Heart_Testing.parallax_Heart_second; 

public class Testing_Basic extends ApplicationAdapter
{
	
	private final float worldWidth = 40;

	@Override
	public void create () 
	{
		GlobalTimmer.registerTime("TEST");
		parallax_Heart = new Parallax_Heart(worldWidth, new AssetManager());
		parallax_Heart_second = new Parallax_Heart(worldWidth, new AssetManager());

	    parallax_Heart.parallaxPage.setDrawingHeight(2.4f);
//	    Parallax_Utils_Astre.startAstre(Enum_AstreType.SUN, 10);
		
	    parallax_Heart.setPage(Enum_ColdNight.COLD_NIGHT.wholePage) ; 
//	    Parallax_Utils_Page.setSecondPage(Enum_ColdNight.COLD_WATER.wholePage) ; 
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
		parallax_Heart.act(delta);
		parallax_Heart.render() ;
	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
//		atlas.dispose();
	}
	
}