package jks.tools2d.parallax.mains;

import static jks.tools2d.parallax.test.GVars_Heart.*;

import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import jks.tools2d.parallax.ParallaxBackground;
import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.heart.ParralaxPageModel;
import jks.tools2d.parallax.side.SolarAstre;
import jks.tools2d.parallax.side.SquareBackground;
import jks.tools2d.parallax.test.Enum_PageModel_Day;
import jks.tools2d.parallax.test.GVars_Heart;

public class Testing_Day extends ApplicationAdapter
{
	
	private final float cloudSpeed = -0.3f; 
	public static Stage mainInterface ;
	
	
	@Override
	public void create () 
	{
		worldWidth = 40;
		worldHeight = Utils_Parralax.calculateOtherDimension(true, worldWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		useTimeOfDay = true ;
		
		Index_DayLayer.init(worldWidth, worldHeight);
		
		batch = new SpriteBatch();
	    
		worldCamera = new OrthographicCamera();
	    worldCamera.setToOrtho(false,worldWidth,worldHeight);
	    worldCamera.position.add(5000,0,0) ;
	    worldCamera.update();
	    
	    astres = new SolarAstre() ;
	    	    
	    Enum_PageModel_Day nature = Enum_PageModel_Day.NIGHT;
	    parallaxBackground = new ParallaxBackground();
	    parallaxBackground.addLayers(nature.createLayers_day(worldWidth,worldHeight));
	    parallaxBackground.setDrawingHeight(6.78f);
	    square = nature.page.buildSquareBackground(0.4f) ;
	    shapeRender = new ShapeRenderer() ; 

	    parallaxBackground_Road = new ParallaxBackground();
	    parallaxBackground_Road.addLayers(createLayers_greenRoad("day/Green.atlas")) ;
	    parallaxBackground_Road.set_oldLayer_Color(Enum_PageModel_Day.NIGHT.page.colorSurronding);
	    
	    staticCamera = new OrthographicCamera() ;
	    staticCamera.setToOrtho(false, Gdx.graphics.getWidth(),  Gdx.graphics.getHeight());
	    staticCamera.position.add(0,0,0) ;
	    staticCamera.update();

	    mainInterface = new Stage(); ;
		Skin skin = new Skin(Gdx.files.internal("skins/orange/skin/uiskin.json"));
		Gdx.input.setInputProcessor(mainInterface);
		Button button = new Button(skin) ;
		button.setBounds(100, 100, 70, 40);
	}
	 

	
	private List<TextureRegionParallaxLayer> createLayers_greenRoad(String atlasPath) 
	{
		TextureAtlas atlas2 = new TextureAtlas(atlasPath);
		
		TextureRegion roadRegion = atlas2.findRegion("Green_Road");
		TextureRegionParallaxLayer roadLayer = new TextureRegionParallaxLayer(roadRegion, worldWidth, new Vector2(.4f,.4f), true);
		
		TextureRegion roadBackRegionA = atlas2.findRegions("Green_Grass").first();
		TextureRegionParallaxLayer roadBackLayerA = new TextureRegionParallaxLayer(roadBackRegionA, worldWidth, new Vector2(.3f,.3f), true);
		roadBackLayerA.setPadBottom(worldHeight*.165f);
		
		TextureRegion roadBackRegionB = atlas2.findRegion("Green_Back");
		TextureRegionParallaxLayer roadBackLayerB = new TextureRegionParallaxLayer(roadBackRegionB, worldWidth, new Vector2(.35f,.35f), true);
		roadBackLayerB.setPadBottom(worldHeight*.15f);
		
		
		return Arrays.asList(roadBackLayerA,roadBackLayerB,roadLayer) ;	
	}

	@Override
	public void render () 
	{
		GVars_Heart.render();
		mainInterface.draw();
	}
}