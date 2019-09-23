package jks.tools2d.colorplay;

import java.nio.ByteBuffer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import jks.tools2d.libgdxutils.color.grayscale.Enum_ColorFun;

public class ChangeColorOnTextureTest extends ApplicationAdapter 
{

	public static Texture texture ; 
	public static Texture texture2 ; 
	public SpriteBatch batch;
	
	public static void main (String[] arg) 
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(1300, 800);

		Lwjgl3Application application = new Lwjgl3Application(new ChangeColorOnTextureTest(), config);
	}
	
	@Override
 	public void create () 
 	{
		batch = new SpriteBatch();
		texture = new Texture("single/chiot.png") ;
		TextureData textureData = texture.getTextureData();
	    textureData.prepare();
	    
	    Pixmap pixmap = textureData.consumePixmap() ;
	    for (int x=0; x<pixmap.getWidth(); x++) 
	    {
	        for (int y=0; y<pixmap.getHeight(); y++) 
	        {
	           //  pixmap.drawPixel(x, y, Enum_ColorFun.NONE.buildFromInteger(pixmap.getPixel(x, y)));
	        	//System.out.println(pixmap.getPixel(x, y));
	        	pixmap.drawPixel(x, y, Enum_ColorFun.NONE.buildFromInteger(pixmap.getPixel(x, y)));
	    	     
	        }
	    }
	    texture2 = new Texture(pixmap) ; 
 	}
	
	@Override
	public void render () 
	{
	    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);	  
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		batch.begin();
		batch.draw(texture2, 0, 0);
		batch.draw(texture, texture2.getWidth(), 0);
		batch.end();
	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
//		atlas.dispose();
	}
	
	
	
}
