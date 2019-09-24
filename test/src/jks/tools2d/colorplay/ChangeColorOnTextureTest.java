package jks.tools2d.colorplay;

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

import jks.tools2d.libgdxutils.color.grayscale.Enum_ColorIsolation;
import jks.tools2d.libgdxutils.color.grayscale.Utils_ColorMerge;

public class ChangeColorOnTextureTest extends ApplicationAdapter 
{

	public static Texture texture ; 
	public static Texture textureGrayMod ; 
	public static Texture textureGrayMod2 ; 
	public SpriteBatch batch;
	
	private static final String path = "single/chiot.jpg" ; 
 	
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
		texture = new Texture(path) ;
		TextureData textureData = texture.getTextureData();
	    textureData.prepare();
	    
	    Pixmap pixmap = textureData.consumePixmap() ;
	    textureData.prepare();
	    Pixmap pixmap2 = textureData.consumePixmap() ;
	    for (int x = 0; x < pixmap.getWidth(); x++) 
	    {
	        for (int y = 0; y < pixmap.getHeight(); y++) 
	        {

	        	pixmap.drawPixel(x, y,Enum_ColorIsolation.GRAY.buildFromInteger(pixmap.getPixel(x, y))) ; 
	        	pixmap2.drawPixel(x, y,Enum_ColorIsolation.GRAY.buildFromInteger2(pixmap.getPixel(x, y))) ; 
	        	
	        	pixmap.drawPixel(x, y, 
	        			Utils_ColorMerge.mergeColor(pixmap.getPixel(x, y),
	        					((float)y/pixmap.getHeight() * 100), 
	        					Color.RED.toIntBits(), Color.GREEN.toIntBits()));
	        	pixmap2.drawPixel(x, y, 
	        			Utils_ColorMerge.mergeColor(pixmap2.getPixel(x, y),
	        					((float)y/pixmap.getHeight() * 100), 
	        					Color.RED.toIntBits(), Color.GREEN.toIntBits()));
	        	
//	        	pixmap.drawPixel(x, y, 
//	        			Utils_ColorMerge.mergeColor(Enum_ColorIsolation.GRAY.buildFromInteger(pixmap.getPixel(x, y)),
//	        					((float)y/pixmap.getHeight() * 100), 
//	        					Color.RED.toIntBits(), Color.RED.toIntBits()));
	        }
	    }
	    
	    textureGrayMod = new Texture(pixmap) ; 
	    textureGrayMod2 = new Texture(pixmap2) ; 
 	}
	
	@Override
	public void render () 
	{
	    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);	  
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		batch.begin();
		batch.draw(textureGrayMod, 0, 0);
		batch.draw(textureGrayMod2, textureGrayMod.getWidth(), 0);
		batch.draw(texture, textureGrayMod.getWidth() * 2, 0);
		batch.end();
	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
//		atlas.dispose();
	}
	
	
	
}
