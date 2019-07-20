package jks.tools2d.filewatch;

import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Texture;

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
//			Position_Infos pos = GVars_Vue_Edition.imageRef.get(concernTexture) ; 
//			GVars_Vue_Edition.imageRef.remove(concernTexture) ; 
//			TextureRegion region = UtilsTexture.getTextureRegionFromPath(pos.url) ; 
//
//			GVars_Vue_Edition.imageRef.put(region, pos) ; 
//			ArrayList<ParallaxLayer> layers = GVars_Vue_Edition.textureLink.get(concernTexture) ; 
//			
//			for(ParallaxLayer layer : layers)
//			{
//				layer.setTexRegion(region);
//			}
//			
//			concernTexture = region ; 
			
		}
		else if(kind == StandardWatchEventKinds.ENTRY_DELETE)
		{
			
		}
		
	}


}
