package jks.tools2d.parallax.mains;

import static jks.tools2d.parallax.gvars.GVars_Heart_Testing.*;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;

import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.inputs.GVars_Inputs;
import jks.tools2d.parallax.inputs.Testing_InputProcessus;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model; 

public class TestSimple extends ApplicationAdapter
{
	
	private static final String path = "" ; 
	
	public static final String hiver = path + "Hiver/Hiver.plax" ; 
	public static final String printemps = path + "printemps/Printemps.plax" ; 
	
	public static void main (String[] arg) 
	{
		
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(1820, 1080);

		new Lwjgl3Application(new TestSimple(), config);
	}
	
	@Override
	public void create () 
	{
		GlobalTimmer.registerTime("TEST");
		try {
//			Input input = new Input(Gdx.files.internal(printemps).read());
			parallax_Heart = new Parallax_Heart(printemps);
			WholePage_Model nextPage = Utils_Page.loadPage(hiver) ; 
			
			parallax_Heart.transfertIntoPage(nextPage, 5);
		} catch (Exception e) {
		
			e.printStackTrace();
		}
  
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