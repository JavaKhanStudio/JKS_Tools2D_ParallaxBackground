package jks.tools2d.filewatch;

import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;
import java.util.Date;
import java.util.Timer;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition;

public class FileWatching_Image extends FileWatchingModel
{

	public TextureRegion concernTexture ; 
	
	public FileWatching_Image(String watchFile, TextureRegion texture) 
	{
		super(watchFile);
		concernTexture = texture ; 
		
		Timer timer = new Timer();
        timer.schedule(this , new Date(), 1000 );
	}

	@Override
	public void onModified(Kind<?> kind) 
	{
		if(kind == StandardWatchEventKinds.ENTRY_MODIFY)
		{
			GVars_Vue_Edition.textureChange.add(this) ;
			
		}
		else if(kind == StandardWatchEventKinds.ENTRY_DELETE)
		{
			
		}
		
	}


}
