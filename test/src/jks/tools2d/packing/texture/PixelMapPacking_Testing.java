package jks.tools2d.packing.texture;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;


public class PixelMapPacking_Testing extends ApplicationAdapter
{
	
	public static final String name = "test" ; 
	
	@Override
	public void create () 
	{
		TextureAtlas textures = new TextureAtlas(new FileHandle("../editor/Files/Atlas/OneNight.atlas"));
		PixmapPacker pixmap = new PixmapPacker(10000, 10000, Format.RGBA8888, 0, false); 
		int space = 0; 
		for(Texture text : textures.getTextures())
		{
			if(!text.getTextureData().isPrepared())
				text.getTextureData().prepare();
			
			Pixmap map = text.getTextureData().consumePixmap() ;
			pixmap.pack(name+space, map) ; 
			space++ ; 
		}
		
		TextureAtlas atlas = pixmap.generateTextureAtlas(TextureFilter.MipMapNearestNearest, TextureFilter.MipMapNearestNearest, true) ; 
		
	}
	
	static public void main (String[] args) throws Exception
    {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(800, 450);

		new Lwjgl3Application(new PixelMapPacking_Testing(), config);
//		new LwjglApplication(new Testing_Basic(), config);
    }
	
}
