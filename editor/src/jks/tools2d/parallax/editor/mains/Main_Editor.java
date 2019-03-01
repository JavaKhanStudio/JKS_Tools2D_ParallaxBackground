package jks.tools2d.parallax.editor.mains;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.kryo.io.Input;

import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.vue.ScrollPaneTest;
import jks.tools2d.parallax.editor.vue.Vue_Selection;
import jks.tools2d.parallax.editor.vue.edition.Vue_Edition;
import jks.tools2d.parallax.pages.WholePage_Model; 

public class Main_Editor extends ApplicationAdapter 
{
	
	@Override
	public void create () 
	{
		GVars_Heart_Editor.init();
		Gdx.graphics.setVSync(true);
//		GVars_Heart_Editor.changeVue(new Vue_Selection(),true) ; 
//		directTestAtlas() ;
		directTestParallax() ; 
	}

	void directTestAtlas()
	{
		TextureAtlas textures = new TextureAtlas(new FileHandle("../editor/Files/Atlas/OneNight.atlas"));
		GVars_Heart_Editor.changeVue(new Vue_Edition(textures), true) ;  	
	}
	
	void directTestParallax()
	{
		Input input;
		try
		{
			input = new Input(new FileInputStream("../editor/Files/Parallax/whole." + FVars_Extensions.PARALLAX));
			GVars_Heart_Editor.changeVue(new Vue_Edition(GVars_Heart_Editor.kryo.readObject(input,WholePage_Model.class)),true) ;  
		} 
		catch (FileNotFoundException e)
		{e.printStackTrace();}
	}
	
	
	@Override
	public void render () 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    	float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
    
    	if (delta > 0) 
    	{
//    		GVars_Inputs.updateInput_ControllingInterface() ;
    		
    		GVars_Heart_Editor.vue.update(delta);
    		GVars_Heart_Editor.vue.render();
    	}
	}
	
    @Override
	public void resize(int width, int height) 
	{
//		if(GVars_Camera.mapRenderer != null)
//			GVars_Camera.mapRenderer.setView(GVars_Camera.camera);
	}

    @Override
    public void pause()
    {}

    @Override
    public void resume()
    {}

    @Override
	public void dispose() 
	{}
}
