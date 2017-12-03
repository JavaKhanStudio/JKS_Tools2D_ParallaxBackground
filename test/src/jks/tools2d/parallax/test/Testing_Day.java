package jks.tools2d.parallax.test;

import static jks.tools2d.parallax.test.GVars_Heart.batch;
import static jks.tools2d.parallax.test.GVars_Heart.*;
import static jks.tools2d.parallax.test.GVars_Heart.worldCamera;

import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;

import jks.tools2d.parallax.ParallaxBackground;
import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.test.inputs.InputProcessus;

public class Testing_Day extends ApplicationAdapter
{
	
	private final float worldWidth = 40;
	private float worldHeight;
	
	private final float cloudSpeed = -0.3f; 
	private final float backgroundHeight = 5 ; 
	
	public static Stage mainInterface ;
	
	@Override
	public void create () 
	{
		System.out.println(worldWidth + " Compare " + Gdx.graphics.getWidth());
		worldHeight = Utils_Parralax.calculateOtherDimension(true, worldWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		batch = new SpriteBatch();
	    
		worldCamera = new OrthographicCamera();
	    worldCamera.setToOrtho(false,worldWidth,worldHeight);
	    worldCamera.position.add(5000,0,0) ;
	    worldCamera.update();
	    
		parallaxBackground = new ParallaxBackground();
    	parallaxBackground.addLayers(createLayers_day("day/day.atlas"));
    	parallaxBackground.addLayersTransfert(createLayers_day("day/sunrise.atlas"));
	   

    	parallaxBackground_Road  = new ParallaxBackground();
    	parallaxBackground_Road.addLayers(createLayers_greenRoad("day/Green.atlas"));
    	parallaxBackground_Road.setColor(new Color(0.5f,0.5f,0.5f,1));
    	Gdx.input.setInputProcessor(new InputProcessus());
	
	    
	    mainInterface = new Stage(); ;
		Skin skin = new Skin(Gdx.files.internal("skins/orange/skin/uiskin.json"));
		Gdx.input.setInputProcessor(mainInterface);
		Button button = new Button(skin) ;
		button.setBounds(100, 100, 70, 40);
		mainInterface.addActor(button);
	}
	 

	private List<TextureRegionParallaxLayer> createLayers_day(String atlasPath) 
	{
		TextureAtlas atlas = new TextureAtlas(atlasPath);

		TextureRegion cloudsRegion = atlas.findRegion("Clouds");
		TextureRegionParallaxLayer cloudsLayerA = new TextureRegionParallaxLayer(cloudsRegion, worldWidth, new Vector2(.01f,.01f), true);
		cloudsLayerA.setPadBottom(worldHeight*.7f);
		cloudsLayerA.setSpeed(cloudSpeed);
		
		Array<AtlasRegion> mountainsRegion = atlas.findRegions("Mountains");
		
		TextureRegionParallaxLayer mountainsLayerA = new TextureRegionParallaxLayer(mountainsRegion.get(0), worldWidth, new Vector2(.01f,.01f), true);
		mountainsLayerA.setPadBottom(worldHeight*.47f);
		TextureRegionParallaxLayer mountainsLayerB = new TextureRegionParallaxLayer(mountainsRegion.get(1), worldWidth, new Vector2(.02f,.02f), true);
		mountainsLayerB.setPadBottom(worldHeight*.43f);
		
        Array<AtlasRegion> treesRegion = atlas.findRegions("Trees");
		TextureRegionParallaxLayer treesLayerA = new TextureRegionParallaxLayer(treesRegion.get(0), worldWidth*.7275f, new Vector2(.05f,.05f), true);
		treesLayerA.setPadBottom(worldHeight*.48f);
		TextureRegionParallaxLayer treesLayerB = new TextureRegionParallaxLayer(treesRegion.get(1), worldWidth*.7275f, new Vector2(.08f,.08f), true);
		treesLayerB.setPadBottom(worldHeight*.39f);
		TextureRegionParallaxLayer treesLayerC = new TextureRegionParallaxLayer(treesRegion.get(2), worldWidth*.7275f, new Vector2(.1f,.1f), true);
		treesLayerC.setPadBottom(worldHeight*.34f);
		    
		return Arrays.asList(cloudsLayerA,mountainsLayerA,mountainsLayerB,treesLayerA,treesLayerB,treesLayerC) ;
	}
	
	private List<TextureRegionParallaxLayer> createLayers_greenRoad(String atlasPath) 
	{
		TextureAtlas atlas2 = new TextureAtlas(atlasPath);
		
		TextureRegion roadRegion = atlas2.findRegion("Green_Road");
		TextureRegionParallaxLayer roadLayer = new TextureRegionParallaxLayer(roadRegion, worldWidth, new Vector2(.4f,.4f), true);
		
		TextureRegion roadBackRegionA = atlas2.findRegions("Green_Grass").first();
		TextureRegionParallaxLayer roadBackLayerA = new TextureRegionParallaxLayer(roadBackRegionA, worldWidth, new Vector2(.3f,.3f), true);
		roadBackLayerA.setPadBottom(worldHeight*.21f);
		
		TextureRegion roadBackRegionB = atlas2.findRegion("Green_Back");
		TextureRegionParallaxLayer roadBackLayerB = new TextureRegionParallaxLayer(roadBackRegionB, worldWidth, new Vector2(.35f,.35f), true);
		roadBackLayerB.setPadBottom(worldHeight*.20f);
		
		
		return Arrays.asList(roadBackLayerA,roadBackLayerB,roadLayer) ;
		
	}

	@Override
	public void render () 
	{
		GVars_Heart.render();
		mainInterface.draw();
	}
}