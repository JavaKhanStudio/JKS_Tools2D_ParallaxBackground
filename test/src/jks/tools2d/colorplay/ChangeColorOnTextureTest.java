package jks.tools2d.colorplay;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.mainUi;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import jks.tools2d.libgdxutils.color.ColorPickerListener;
import jks.tools2d.libgdxutils.color.ExtendedColorPicker;
import jks.tools2d.libgdxutils.color.grayscale.Enum_ColorIsolation;
import jks.tools2d.libgdxutils.color.grayscale.Utils_ColorMerge;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;

public class ChangeColorOnTextureTest extends ApplicationAdapter 
{

	public static Texture sourceTexture, grayTexture, newTexture ; 
	public static Pixmap sourcePixmap, grayPixmap ; 
	public SpriteBatch batch;
	
	public static Color topColor = Color.WHITE, bottomColor = Color.WHITE;
	
	private static final String path = "single/chiot.jpg" ;
	
	public static void main (String[] arg) 
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(1800, 800);

		Lwjgl3Application application = new Lwjgl3Application(new ChangeColorOnTextureTest(), config);
	}
	
	@Override
 	public void create () 
 	{
		GVars_Ui.init();
		batch = new SpriteBatch();
		sourceTexture = new Texture(path) ;
		TextureData textureData = sourceTexture.getTextureData();
	    textureData.prepare();
	    sourcePixmap = textureData.consumePixmap() ;
	    grayPixmap = new Pixmap(sourcePixmap.getWidth(),sourcePixmap.getHeight(), Format.RGB888) ;
	    
	    for (int x = 0; x < sourcePixmap.getWidth(); x++) 
	    {
	        for (int y = 0; y < sourcePixmap.getHeight(); y++) 
	        {
	        	grayPixmap.drawPixel(x, y,Enum_ColorIsolation.GRAY.buildFromInteger(sourcePixmap.getPixel(x, y))) ; 
	        }
	    }
	    
	    grayTexture = new Texture(grayPixmap) ; 

	    final ExtendedColorPicker topPicker = new ExtendedColorPicker() ; 
		topPicker.setListener(buildListener(topPicker, true));
		
		final ExtendedColorPicker bottomPicker = new ExtendedColorPicker() ; 
		bottomPicker.setListener(buildListener(bottomPicker, false));

		Table table = new Table() ; 
		table.setBounds(sourceTexture.getWidth() * 2, 0 , Gdx.graphics.getWidth() - sourceTexture.getWidth() * 2, Gdx.graphics.getHeight());
		table.add(topPicker) ;
		table.row() ; 
		table.add(bottomPicker) ;
		GVars_Ui.mainUi.addActor(table);
		
 	}
	
	@Override
	public void render () 
	{
	    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);	  
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		batch.begin();
		batch.draw(sourceTexture, 0, 0);
		
		if(toChange)
		{
			toChange = false ; 
			newTexture = buildTexture(grayPixmap, topColor, bottomColor) ; 
		}
		
		if(newTexture != null)
			batch.draw(newTexture, sourceTexture.getWidth(), 0);
		
		batch.end();
		mainUi.draw() ;		
	}
	
	private Texture buildTexture(Pixmap pixmapRef, Color top, Color bottom) 
	{
		Pixmap newPixmap = new Pixmap(pixmapRef.getWidth(),sourcePixmap.getHeight(), Format.RGB888) ;
	    
		for (int x = 0; x < pixmapRef.getWidth(); x++) 
	    {
	        for (int y = 0; y < pixmapRef.getHeight(); y++) 
	        {
	        	newPixmap.drawPixel(x, y, 
	        			Utils_ColorMerge.mergeColor2(pixmapRef.getPixel(x, y),
	        					((float)y/pixmapRef.getHeight() * 100), 
	        					top.toIntBits(),bottom.toIntBits()));
	        	
	  
	        }
	    }
		  
		return new Texture(newPixmap);
	}

	@Override
	public void dispose() 
	{
		super.dispose();
//		atlas.dispose();
	}
	
	public static boolean toChange = false ; 
	
	public ColorPickerListener buildListener(ExtendedColorPicker picker, boolean top)
	{
		return new ColorPickerListener()
		{
			
			@Override
			public void changed(Color newColor)
			{
				if(top)
					topColor = newColor ;
				else
					bottomColor = newColor ;
				
				toChange = true ; 
			}
			
			@Override
			public void reset(Color previousColor, Color newColor)
			{}
			@Override
			public void canceled(Color oldColor)
			{}
			@Override
			public void finished(Color newColor)
			{System.out.println("test");}
		};
		
	}
	
}
