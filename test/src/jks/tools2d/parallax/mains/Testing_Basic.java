package jks.tools2d.parallax.mains;

import static jks.tools2d.parallax.test.GVars_Heart.batch;
import static jks.tools2d.parallax.test.GVars_Heart.parallaxBackground;
import static jks.tools2d.parallax.test.GVars_Heart.worldCamera;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

import jks.tools2d.parallax.ParallaxBackground;
import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.test.GVars_Heart;
import jks.tools2d.parallax.test.inputs.InputProcessus; 


public class Testing_Basic extends ApplicationAdapter
{
	
	
	private TextureAtlas atlas;
	private final float worldWidth = 40;
	private float worldHeight;
	
	private Color clearColor = new Color(0Xbeaf7bff);
	
	
	private final float cloudSpeed = 5.5f;
	
	
	
	@Override
	public void create () 
	{
		worldHeight = Utils_Parralax.calculateOtherDimension(true, worldWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		batch = new SpriteBatch();
	    worldCamera = new OrthographicCamera();
	    worldCamera.setToOrtho(false,worldWidth,worldHeight);
	    worldCamera.update();
	    
	    createLayers();  
	    
	    Gdx.input.setInputProcessor(new InputProcessus());
	}
	 

	private void createLayers() 
	{
		atlas = new TextureAtlas("data/main_atlas.atlas");

		TextureRegion mountainsRegionA = atlas.findRegion("mountains_a");
		TextureRegionParallaxLayer mountainsLayerA = new TextureRegionParallaxLayer(mountainsRegionA, worldWidth, new Vector2(.3f,.3f), true);
	
		TextureRegion mountainsRegionB = atlas.findRegion("mountains_b");
		TextureRegionParallaxLayer mountainsLayerB = new TextureRegionParallaxLayer(mountainsRegionB, worldWidth*.7275f, new Vector2(.6f,.6f), true);
        mountainsLayerB.setPadLeft(.2725f*worldWidth);

      	TextureRegion cloudsRegionA = atlas.findRegion("clouds");
		TextureRegionParallaxLayer cloudsLayerA = new TextureRegionParallaxLayer(cloudsRegionA, worldWidth, new Vector2(.6f,.6f), true);
		cloudsLayerA.setPadBottom(worldHeight*.467f);
		cloudsLayerA.setSpeed(cloudSpeed);
		
        TextureRegion cloudsRegionB = atlas.findRegion("clouds");
		TextureRegionParallaxLayer cloudsLayerB = new TextureRegionParallaxLayer(cloudsRegionB, worldWidth, new Vector2(.6f,.6f), true);
		cloudsLayerB.setPadBottom(worldHeight*.1f);
		cloudsLayerB.setSpeed(-cloudSpeed);
		

		TextureRegion buildingsRegionA = atlas.findRegion("buildings_a");
		TextureRegionParallaxLayer buildingsLayerA = new TextureRegionParallaxLayer(buildingsRegionA, worldWidth, new Vector2(.75f,.75f), true);
       	
		TextureRegion buildingsRegionB = atlas.findRegion("buildings_b");
		TextureRegionParallaxLayer buildingsLayerB = new TextureRegionParallaxLayer(buildingsRegionB, worldWidth*.8575f, new Vector2(1,1), true);
       	buildingsLayerB.setPadLeft(.07125f*worldWidth);
		buildingsLayerB.setPadRight(buildingsLayerB.getPadLeft());
       	
		TextureRegion buildingsRegionC = atlas.findRegion("buildings_c");
		TextureRegionParallaxLayer buildingsLayerC = new TextureRegionParallaxLayer(buildingsRegionC, worldWidth, new Vector2(1.3f,1.3f), true);
       	
		parallaxBackground = new ParallaxBackground();
    	parallaxBackground.addLayers(mountainsLayerA,mountainsLayerB,buildingsLayerA,cloudsLayerA,cloudsLayerB,buildingsLayerB,buildingsLayerC);
	}


	@Override
	public void render () 
	{
		GVars_Heart.render();
	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
		batch.dispose();
		atlas.dispose();
	}
	
}
