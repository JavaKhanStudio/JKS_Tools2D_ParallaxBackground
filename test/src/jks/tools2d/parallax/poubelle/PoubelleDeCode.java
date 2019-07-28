package jks.tools2d.parallax.poubelle;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.ParallaxLayer;

public class PoubelleDeCode {

}

/*
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

*/