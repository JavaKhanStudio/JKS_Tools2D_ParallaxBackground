package jks.tools2d.parallax.mains;

import static jks.tools2d.parallax.gvars.GVars_Heart_Testing.parallax_Heart;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import jks.tools.export.Enum_ParralaxSituation;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.inputs.GVars_Inputs;
import jks.tools2d.parallax.inputs.Testing_InputProcessus; 

public class Testing_Basic extends ApplicationAdapter
{
	
//	private final float worldWidth = 40;

	@Override
	public void create () 
	{
		GlobalTimmer.registerTime("TEST");
		parallax_Heart = new Parallax_Heart();

//	    parallax_Heart.parallaxReader.setDrawingHeight(2.4f);
//	    Parallax_Utils_Astre.startAstre(Enum_AstreType.SUN, 10);
		
	  
	    parallax_Heart.setPage(Enum_ParralaxSituation.DAY.page) ; 
	    
//	    parallax_Heart.setPage(Enum_ColdNight.COLD_NIGHT.wholePage) ; 
//	    Parallax_Utils_Page.setSecondPage(Enum_ColdNight.COLD_WATER.wholePage) ; 
//		Parallax_Utils_Page.setPage(Enum_PageModel_Day.DAY.wholePage) ; 
		
//		GlobalTimmer.getElapse("TEST", "WHOLE", true);
	    Gdx.input.setInputProcessor(new Testing_InputProcessus());
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