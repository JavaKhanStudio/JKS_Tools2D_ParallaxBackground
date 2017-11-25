package jks.tools2d.parallax.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

import jks.tools2d.parallax.ParallaxBackground;
import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.Utils_Parralax.WH;


public class Testing_Parallax extends ApplicationAdapter
{
	
	private SpriteBatch batch;
	private OrthographicCamera worldCamera;
	
	private TextureAtlas atlas;
	
	private ParallaxBackground parallaxBackground;
	
	private final float worldWidth = 40;
	private float worldHeight;
	
	private Color clearColor = new Color(0Xbeaf7bff);
	
	private final float screenMovementSpeed = 1.0f;
	private final float cloudSpeed = 5.5f;
	private boolean autoMoveScreen = false ; 
	
	
	@Override
	public void create () 
	{
		worldHeight = Utils_Parralax.calculateOtherDimension(WH.width, worldWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		batch = new SpriteBatch();
	    worldCamera = new OrthographicCamera();
	    worldCamera.setToOrtho(false,worldWidth,worldHeight);
	    worldCamera.update();
	    
	    createLayers();  
	    
	    Gdx.input.setInputProcessor(new MyInputProcessor());
	}
	 

	private void createLayers() 
	{
		atlas = new TextureAtlas("data/main_atlas.atlas");

		
		TextureRegion mountainsRegionA = atlas.findRegion("mountains_a");
		TextureRegionParallaxLayer mountainsLayerA = new TextureRegionParallaxLayer(mountainsRegionA, worldWidth, new Vector2(.3f,.3f), WH.width);
	
		TextureRegion mountainsRegionB = atlas.findRegion("mountains_b");
		TextureRegionParallaxLayer mountainsLayerB = new TextureRegionParallaxLayer(mountainsRegionB, worldWidth*.7275f, new Vector2(.6f,.6f), WH.width);
        mountainsLayerB.setPadLeft(.2725f*worldWidth);

      	TextureRegion cloudsRegionA = atlas.findRegion("clouds");
		TextureRegionParallaxLayer cloudsLayerA = new TextureRegionParallaxLayer(cloudsRegionA, worldWidth, new Vector2(.6f,.6f), WH.width);
		cloudsLayerA.setPadBottom(worldHeight*.467f);
		cloudsLayerA.setSpeed(cloudSpeed);
		
        TextureRegion cloudsRegionB = atlas.findRegion("clouds");
		TextureRegionParallaxLayer cloudsLayerB = new TextureRegionParallaxLayer(cloudsRegionB, worldWidth, new Vector2(.6f,.6f), WH.width);
		cloudsLayerB.setPadBottom(worldHeight*.1f);
		cloudsLayerB.setSpeed(-cloudSpeed);
		

		TextureRegion buildingsRegionA = atlas.findRegion("buildings_a");
		TextureRegionParallaxLayer buildingsLayerA = new TextureRegionParallaxLayer(buildingsRegionA, worldWidth, new Vector2(.75f,.75f), WH.width);
       	
		TextureRegion buildingsRegionB = atlas.findRegion("buildings_b");
		TextureRegionParallaxLayer buildingsLayerB = new TextureRegionParallaxLayer(buildingsRegionB, worldWidth*.8575f, new Vector2(1,1), WH.width);
       	buildingsLayerB.setPadLeft(.07125f*worldWidth);
		buildingsLayerB.setPadRight(buildingsLayerB.getPadLeft());
       	
		TextureRegion buildingsRegionC = atlas.findRegion("buildings_c");
		TextureRegionParallaxLayer buildingsLayerC = new TextureRegionParallaxLayer(buildingsRegionC, worldWidth, new Vector2(1.3f,1.3f), WH.width);
       	
		parallaxBackground = new ParallaxBackground();
    	parallaxBackground.addLayers(mountainsLayerA,mountainsLayerB,buildingsLayerA,cloudsLayerA,cloudsLayerB,buildingsLayerB,buildingsLayerC);
	}


	@Override
	public void render () 
	{
		if(inputOn)
			processInput();
		
		if(autoMoveScreen)
			worldCamera.position.add(screenMovementSpeed, 0, 0);
		
		Gdx.gl.glClearColor(clearColor.r,clearColor.g,clearColor.b, clearColor.a);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		batch.begin();
		parallaxBackground.act(delta) ; 
		parallaxBackground.draw(worldCamera, batch);
		batch.end();
	}
	
	
	
	private void processInput() 
	{
		if(Gdx.input.isKeyPressed(Keys.A)&&(worldCamera.position.x-worldCamera.viewportWidth*.5f>0))
			worldCamera.position.sub(screenMovementSpeed, 0, 0);
		if(Gdx.input.isKeyPressed(Keys.D))
			worldCamera.position.add(screenMovementSpeed, 0, 0);
		if(Gdx.input.isKeyPressed(Keys.S)&&(worldCamera.position.y-worldCamera.viewportHeight*.5f>0))
			worldCamera.position.sub(0, screenMovementSpeed, 0);
		if(Gdx.input.isKeyPressed(Keys.W))
			worldCamera.position.add(0, screenMovementSpeed, 0);
		if(Gdx.input.isKeyPressed(Keys.Q))
			worldCamera.zoom *= 1.1f;
		else if(Gdx.input.isKeyPressed(Keys.E))
			worldCamera.zoom /= 1.1f;
		
		worldCamera.update();
		batch.setProjectionMatrix(worldCamera.combined);
	}


	@Override
	public void dispose() 
	{
		super.dispose();
		batch.dispose();
		atlas.dispose();
	}
	
	
	boolean inputOn = false;
	private class MyInputProcessor extends InputAdapter
	{
		@Override
		public boolean keyDown(int keycode) 
		{
			if(keycode==Keys.ESCAPE)
			{Gdx.app.exit(); return true;}
			
			if(keycode==Keys.A||keycode==Keys.D||keycode==Keys.W||keycode==Keys.S||keycode==Keys.E||keycode==Keys.Q)
			{inputOn = true;}
			
			return false;
		}
		@Override
		public boolean keyUp(int keycode) 
		{
		    inputOn = false;
		    return false;
		}
		
		
	}
}
