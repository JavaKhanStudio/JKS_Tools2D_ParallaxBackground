package jks.tools2d.parallax.editor.mains;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileChooser.Mode;
import com.kotcrab.vis.ui.widget.file.FileChooser.SelectionMode;
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter;




public class Main_Editor extends ApplicationAdapter 
{
	@Override
	public void create () 
	{
//		FileChooser.setFavoritesPrefsName("com.your.package.here.filechooser");
		VisUI.load(Gdx.files.internal("skins/uis/uiskin.json")) ;
		//chooser creation
		FileChooser fileChooser = new FileChooser(Mode.OPEN);
		fileChooser.setSelectionMode(SelectionMode.DIRECTORIES);
		fileChooser.setListener(new FileChooserAdapter() 
		{
			@Override
			public void selected (Array<FileHandle> file) 
			{
				
			}
		});

		/*
		//button listener
		selectFileButton.addListener(new ChangeListener() 
		{
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				//displaying chooser with fade in animation
				getStage().addActor(fileChooser.fadeIn());
			}
		});
		*/
		
		
	}

	@Override
	public void render () 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		
	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
	}
}
