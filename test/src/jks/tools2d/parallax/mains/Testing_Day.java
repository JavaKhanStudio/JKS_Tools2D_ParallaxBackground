package jks.tools2d.parallax.mains;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.test.Enum_PageModel_Day;

public class Testing_Day extends ApplicationAdapter
{
	
	public static Stage mainInterface ;
	
	
	@Override
	public void create () 
	{
		float worldWidth = 40;
		float worldHeight = Utils_Parralax.calculateOtherDimension(true, worldWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		Index_DayLayer.init(worldWidth, worldHeight);
		
		SpriteBatch batch = new SpriteBatch();
	    
		OrthographicCamera worldCamera = new OrthographicCamera();
	    worldCamera.setToOrtho(false,worldWidth,worldHeight);
	    worldCamera.position.add(5000,0,0) ;
	    worldCamera.update();
	    
//	    astres = new SolarAstre() ;
	    	    
	    Enum_PageModel_Day nature = Enum_PageModel_Day.NIGHT;
	    
//	    parallaxBackground_Road = new ParallaxBackground();
//	    parallaxBackground_Road.addLayers(createLayers_greenRoad("day/Green.atlas")) ;
//	    parallaxBackground_Road.set_oldLayer_Color(Enum_PageModel_Day.NIGHT.page.colorSurronding);
	    
//	    staticCamera = new OrthographicCamera() ;
//	    staticCamera.setToOrtho(false, Gdx.graphics.getWidth(),  Gdx.graphics.getHeight());
//	    staticCamera.position.add(0,0,0) ;
//	    staticCamera.update();

//	    mainInterface = new Stage(); ;
		Skin skin = new Skin(Gdx.files.internal("skins/orange/skin/uiskin.json"));
		Gdx.input.setInputProcessor(mainInterface);
		Button button = new Button(skin) ;
		button.setBounds(100, 100, 70, 40);
	}
	 

	
//	private List<TextureRegionParallaxLayer> createLayers_greenRoad(String atlasPath) 
//	{
//		TextureAtlas atlas2 = new TextureAtlas(atlasPath);
//		
//		TextureRegion roadRegion = atlas2.findRegion("Green_Road");
//		TextureRegionParallaxLayer roadLayer = new TextureRegionParallaxLayer(roadRegion, worldWidth, new Vector2(.4f,.4f), true);
//		
//		TextureRegion roadBackRegionA = atlas2.findRegions("Green_Grass").first();
//		TextureRegionParallaxLayer roadBackLayerA = new TextureRegionParallaxLayer(roadBackRegionA, worldWidth, new Vector2(.3f,.3f), true);
//		roadBackLayerA.setPadBottom(worldHeight*.165f);
//		
//		TextureRegion roadBackRegionB = atlas2.findRegion("Green_Back");
//		TextureRegionParallaxLayer roadBackLayerB = new TextureRegionParallaxLayer(roadBackRegionB, worldWidth, new Vector2(.35f,.35f), true);
//		roadBackLayerB.setPadBottom(worldHeight*.15f);
//		
//		
//		return Arrays.asList(roadBackLayerA,roadBackLayerB,roadLayer) ;	
//	}

	@Override
	public void render () 
	{
//		mainInterface.draw();
	}
}