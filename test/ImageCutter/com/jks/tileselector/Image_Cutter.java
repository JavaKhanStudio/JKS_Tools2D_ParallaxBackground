package com.jks.tileselector;

import java.io.File;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;

import jks.tools2d.parallax.Launcher_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.vue.Vue_Selection; 

public class Image_Cutter extends ApplicationAdapter 
{
	
	@Override
	public void create () 
	{
		GVars_Heart_Editor.init();
		
		if(Vue_Selection.selectSingleFile
				(new FileHandle(
						new File(Launcher_Editor.launchingValue))))
		{
			return ;
		}
	
		GVars_Heart_Editor.changeVue(new Vue_TileSelector(),true) ; 
	}


	@Override
	public void render () 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    	float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
    
    	if (delta > 0) 
    	{
    		GVars_Heart_Editor.vue.update(delta);
    		GVars_Heart_Editor.vue.render();
    	}
	}
	
    @Override
	public void resize(int width, int height) 
	{
		GVars_UI.mainUi.getViewport().update(width, height, true);
		if(GVars_Heart_Editor.vue != null)
			GVars_Heart_Editor.vue.resize(width,height) ; 
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