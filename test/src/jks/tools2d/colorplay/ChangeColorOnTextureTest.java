package jks.tools2d.colorplay;

import static jks.tools2d.parallax.editor.gvars.GVars_UI.mainUi;

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
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisCheckBox;

import jks.tools2d.libgdxutils.JksNumberSlider;
import jks.tools2d.libgdxutils.color.ColorPickerListener;
import jks.tools2d.libgdxutils.color.ExtendedColorPicker;
import jks.tools2d.libgdxutils.color.grayscale.Direction;
import jks.tools2d.libgdxutils.color.grayscale.Enum_ColorIsolation;
import jks.tools2d.libgdxutils.color.grayscale.Utils_ColorExtractor;
import jks.tools2d.libgdxutils.color.grayscale.Utils_ColorMerge;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Texture;

public class ChangeColorOnTextureTest extends ApplicationAdapter
{

	public static Texture sourceTexture, grayTexture, newTexture ; 
	public static Pixmap sourcePixmap, grayPixmap ; 
	public SpriteBatch batch;
	
	public static Color topColor = Color.WHITE, bottomColor = Color.WHITE;
	
	private static final String path = "single/chiot.png" ;
	
	private static ExtendedColorPicker topPicker, bottomPicker ; 
	
	private static float width ; 
	private static float height ; 
	
	ButtonGroup<VisCheckBox> grayScalingRadio ; 
	private static Enum_ColorIsolation selectedIsolation = Enum_ColorIsolation.GRAY; 
	VisCheckBox red,gray,dark, darker ; 
	
	VisCheckBox setAsShadow, putFilter; 
	JksNumberSlider shadowPosition ; 
	
	
	
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
		GVars_UI.init();
		
		batch = new SpriteBatch();

		topPicker = new ExtendedColorPicker() ; 
		topPicker.setListener(buildListener(topPicker, true));

		
		bottomPicker = new ExtendedColorPicker() ; 
		bottomPicker.setListener(buildListener(bottomPicker, false));
		
		grayScalingRadio = new ButtonGroup<VisCheckBox>() ;
		
		setAsShadow = new VisCheckBox("Set As Shadow");
		setAsShadow.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{

			}
		}) ; 
		
		shadowPosition = new JksNumberSlider(-100,100,1,GVars_UI.baseSkin)
		{
			@Override
			public void actionOnSliderMovement()
			{}
		};
		
		shadowPosition.setValue(0);
		
		putFilter = new VisCheckBox("Filter");
		putFilter.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{

			}
		}) ; 
		
		putFilter.setChecked(true);
		
		
		Table table = new Table() ; 
		table.setBounds(getStandard() * 2, 0 , Gdx.graphics.getWidth() - getStandard() * 2, Gdx.graphics.getHeight());
		
		table.add(buildColorIsolation(Enum_ColorIsolation.GRAY)) ; 
		table.add(buildColorIsolation(Enum_ColorIsolation.GRAY_DARK)) ; 
		table.add(buildColorIsolation(Enum_ColorIsolation.GREEN)) ; 
		table.add(buildColorIsolation(Enum_ColorIsolation.BLUE)) ;
		table.row() ; 
		table.add(buildColorIsolation(Enum_ColorIsolation.RED)) ; 
		table.add(buildColorIsolation(Enum_ColorIsolation.RED_ADV)) ; 
		
		table.row() ; 
		table.add(setAsShadow) ; 
		table.add(shadowPosition).colspan(3) ; 
		table.row() ; 
		table.add(putFilter) ;
		table.row() ; 
		table.add(topPicker).colspan(4) ;
		table.row() ; 
		table.add(bottomPicker).colspan(4) ;
		GVars_UI.mainUi.addActor(table);
		
		readNewTexture(new Texture(path)) ;
 	}
	
	public VisCheckBox buildColorIsolation(Enum_ColorIsolation iso)
	{
		VisCheckBox building = new VisCheckBox(iso.name());
		building.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				building.setChecked(true);
				selectedIsolation = iso ; 
				createOutupTexture(false) ; 
			}
		}) ; 
		
		grayScalingRadio.add(building);
		return building ; 
	}
	
	@Override
	public void render () 
	{
	    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);	  
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		batch.begin();
		
		float position = setAsShadow.isChecked() ? shadowPosition.getValue():getStandard() ;
		
		if(toChange)
		{
			toChange = false ; 
			newTexture = Utils_ColorMerge.buildTexture(grayPixmap, topColor, bottomColor) ; 
		}
		
		if(putFilter.isChecked())
		{
			if(newTexture != null)
				batch.draw(newTexture, position, 0,width, height);
		}
		else
		{
			if(newTexture != null)
				batch.draw(grayTexture, position, 0,width, height);
		}
		
		batch.draw(sourceTexture, 0, 0,width, height);
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
		if(files.length == 1)
		{
			FileHandle handle = new FileHandle(files[0]); 
			Texture implementingTexture = new Texture(handle) ;
			readNewTexture(implementingTexture) ; 
		}
	}
	
	
	public static void readNewTexture(Texture implementingTexture)
	{
		sourcePixmap = Utils_Texture.extractPixMap(implementingTexture) ;
		sourceTexture = implementingTexture ; 
		createOutupTexture(true) ; 
	}
	
	public static void createOutupTexture(boolean shiftColor)
	{
		grayPixmap = selectedIsolation.rebuildPixmap(sourcePixmap) ;
	    height = (getStandard()/sourcePixmap.getWidth()) *  sourcePixmap.getHeight() ; 
	    
	    if(sourcePixmap.getWidth() < getStandard())
	    	width = sourcePixmap.getWidth() ; 
	    else 
	    	width = getStandard() ; 
	    
	    if(shiftColor)
	    {
	    	topColor = new Utils_ColorExtractor(sourcePixmap, 10,Direction.FromTop).getColor() ;
		    bottomColor = new Utils_ColorExtractor(sourcePixmap, 10,Direction.FromBottom).getColor() ;
		    
		    topPicker.setColor(topColor); 
		    bottomPicker.setColor(bottomColor);
	    }
		grayTexture = new Texture(grayPixmap) ; 
	    newTexture = Utils_ColorMerge.buildTexture(grayPixmap, topColor, bottomColor) ;
	}
	
	public static float getStandard()
	{
		return Gdx.graphics.getWidth() / 3 ; 
	}
}
