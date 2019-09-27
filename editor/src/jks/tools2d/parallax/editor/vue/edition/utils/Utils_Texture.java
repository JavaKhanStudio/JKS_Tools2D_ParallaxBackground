package jks.tools2d.parallax.editor.vue.edition.utils;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class Utils_Texture 
{

	public static TextureRegion getTextureRegionFromPath(String path)
	{
		try
		{
			Texture texture = new Texture(new FileHandle(path),true) ;
			texture.setFilter(TextureFilter.MipMap, TextureFilter.MipMap);
			return new TextureRegion(texture) ; 
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		return null ; 
		
	}
	
	public static Pixmap extractPixMap(Texture texture)
	{

		if (!texture.getTextureData().isPrepared()) 
		    texture.getTextureData().prepare();
		
		texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
		
		Pixmap atlasPixmapSave = texture.getTextureData().consumePixmap();
		atlasPixmapSave.setFilter(Pixmap.Filter.NearestNeighbour);	
		atlasPixmapSave.setBlending(Blending.None);
		

		return atlasPixmapSave ; 
	}
	
	public static Pixmap buildPixmapCopy(Pixmap sourcePixmap)
	{

		Pixmap newPix = new Pixmap(sourcePixmap.getWidth(),sourcePixmap.getHeight(), Format.RGBA8888) ;
		newPix.setFilter(Pixmap.Filter.NearestNeighbour);	
		newPix.setBlending(Blending.None);
		return newPix ; 
	}
	
}