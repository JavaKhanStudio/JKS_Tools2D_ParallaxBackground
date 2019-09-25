package jks.tools2d.colorplay;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.mainUi;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
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
	
	private static ExtendedColorPicker topPicker, bottomPicker ; 
	
	private static float width ; 
	private static float height ; 
	
	public static void main (String[] arg) 
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(1800, 800);
		config.setWindowListener(new Lwjgl3WindowAdapter() 
		{
            @Override
            public void filesDropped(String[] files) 
            {
        		reciveFiles(files) ; 
            }
        });
		
		Lwjgl3Application application = new Lwjgl3Application(new ChangeColorOnTextureTest(), config);
	}
	
	@Override
 	public void create () 
 	{
		GVars_Ui.init();
		width = getStandard() ; 
		batch = new SpriteBatch();
		sourceTexture = new Texture(path) ;
		TextureData textureData = sourceTexture.getTextureData();
	    textureData.prepare();
	    sourcePixmap = textureData.consumePixmap() ;
	    grayPixmap = Enum_ColorIsolation.GRAY.rebuildPixmap(sourcePixmap) ;

	    topColor = Utils_ColorMerge.evalColor(sourcePixmap, 1,Utils_ColorMerge.Direction.FromTop) ;
	    bottomColor = Utils_ColorMerge.evalColor(sourcePixmap, 1,Utils_ColorMerge.Direction.FromBottom) ;
	    
	    if(textureData.getWidth() < getStandard())
	    	width = textureData.getWidth() ; 
	    
	    height = width/textureData.getWidth() * textureData.getHeight() ; 
	    grayTexture = new Texture(grayPixmap) ; 
	    newTexture = Utils_ColorMerge.buildTexture(grayPixmap, topColor, bottomColor) ; 
	    
	    topPicker = new ExtendedColorPicker() ; 
		topPicker.setListener(buildListener(topPicker, true));
		topPicker.setColor(topColor);
		
		bottomPicker = new ExtendedColorPicker() ; 
		bottomPicker.setListener(buildListener(bottomPicker, false));
		bottomPicker.setColor(bottomColor);

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
		batch.draw(sourceTexture, 0, 0,width, height);
		
		if(toChange)
		{
			toChange = false ; 
			newTexture = Utils_ColorMerge.buildTexture(grayPixmap, topColor, bottomColor) ; 
		}
		
		if(newTexture != null)
			batch.draw(newTexture, getStandard(), 0,width, height);
		
		batch.end();
		mainUi.draw() ;		
	}
	
	

	@Override
	public void dispose() 
	{
		super.dispose();
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
	

	public static void reciveFiles(String[] files)
	{
		System.out.println("test");
		
		if(files.length == 1)
		{
			FileHandle handle = new FileHandle(files[0]); 
			Texture implementingTexture = new Texture(handle) ;
			TextureData textureData = implementingTexture.getTextureData();
		    textureData.prepare();
		    sourcePixmap = textureData.consumePixmap() ;
		    grayPixmap = Enum_ColorIsolation.GRAY.rebuildPixmap(sourcePixmap) ;
		    height = (getStandard()/textureData.getWidth()) *  textureData.getHeight() ; 
		    
		    if(textureData.getWidth() < getStandard())
		    	width = textureData.getWidth() ; 
		    else 
		    	width = getStandard() ; 
		    
			topColor = Utils_ColorMerge.evalColor(sourcePixmap, 1,Utils_ColorMerge.Direction.FromTop) ;
		    bottomColor = Utils_ColorMerge.evalColor(sourcePixmap, 1,Utils_ColorMerge.Direction.FromBottom) ;
		    topPicker.setColor(topColor); 
		    bottomPicker.setColor(bottomColor);
		    
		    sourceTexture = implementingTexture ; 
		    newTexture = Utils_ColorMerge.buildTexture(grayPixmap, topColor, bottomColor) ;
		}	
	}
	
	public static float getStandard()
	{
		return Gdx.graphics.getWidth() / 3 ; 
	}
}
