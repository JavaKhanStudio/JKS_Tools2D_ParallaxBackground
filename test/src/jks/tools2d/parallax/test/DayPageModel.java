package jks.tools2d.parallax.test;

import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.heart.ParallaxPageModel;

public class DayPageModel extends ParallaxPageModel
{

	public DayPageModel(String atlasPath, Color top, Color bottom, Color colorSurronding,Color halfBottom) 
	{
		super(atlasPath, top, bottom, colorSurronding,halfBottom);
		
	}

	@Override
	public List<TextureRegionParallaxLayer> howToDraw(float worldWidth, float worldHeight) 
	{
		if(atlasPath == null)
			return null ; 
		
		TextureAtlas atlas = new TextureAtlas(atlasPath);

		TextureRegion cloudsRegion = atlas.findRegion("Clouds");
		Array<AtlasRegion> mountainsRegion = atlas.findRegions("Mountains");
		Array<AtlasRegion> treesRegion = atlas.findRegions("Trees");
		
		
		TextureRegionParallaxLayer cloudsLayerA = new TextureRegionParallaxLayer(cloudsRegion, worldWidth, new Vector2(.01f,.01f), true);
		cloudsLayerA.setPadBottom(worldHeight*.3f);
		cloudsLayerA.setSpeed(1);
		
		TextureRegionParallaxLayer mountainsLayerA = new TextureRegionParallaxLayer(mountainsRegion.get(0), worldWidth, new Vector2(.006f,.006f), true);
		mountainsLayerA.setPadBottom(worldHeight*.12f);
		TextureRegionParallaxLayer mountainsLayerB = new TextureRegionParallaxLayer(mountainsRegion.get(1), worldWidth, new Vector2(.008f,.008f), true);
		mountainsLayerB.setPadBottom(worldHeight*.095f);
		TextureRegionParallaxLayer mountainsLayerC = new TextureRegionParallaxLayer(mountainsRegion.get(1), worldWidth, new Vector2(.012f,.012f), true);
		mountainsLayerC.setPadBottom(worldHeight*.045f);
		
        
        TextureRegionParallaxLayer treesLayerA1 = new TextureRegionParallaxLayer(treesRegion.get(0), worldWidth*.7275f, new Vector2(.03f,.03f), true);
		treesLayerA1.setPadBottom(worldHeight*.12f);
        TextureRegionParallaxLayer treesLayerA = new TextureRegionParallaxLayer(treesRegion.get(0), worldWidth*.7275f, new Vector2(.04f,.04f), true);
		treesLayerA.setPadBottom(worldHeight*.09f);
		TextureRegionParallaxLayer treesLayerB = new TextureRegionParallaxLayer(treesRegion.get(1), worldWidth*.7275f, new Vector2(.06f,.06f), true);
		treesLayerB.setPadBottom(worldHeight*.07f);
		TextureRegionParallaxLayer treesLayerC = new TextureRegionParallaxLayer(treesRegion.get(2), worldWidth*.7275f, new Vector2(.08f,.08f), true);
		treesLayerC.setPadBottom(worldHeight*.07f);
		TextureRegionParallaxLayer treesLayerD = new TextureRegionParallaxLayer(treesRegion.get(1), worldWidth*.7275f, new Vector2(.09f,.09f), true);
		treesLayerD.setPadBottom(worldHeight*.01f);
		TextureRegionParallaxLayer treesLayerE = new TextureRegionParallaxLayer(treesRegion.get(2), worldWidth*.7275f, new Vector2(.10f,.10f), true);
		treesLayerE.setPadBottom(worldHeight*.0f);
		    
		return Arrays.asList(cloudsLayerA,mountainsLayerA,mountainsLayerB,mountainsLayerC,treesLayerA1,treesLayerA,treesLayerB,treesLayerC,treesLayerD,treesLayerE) ;
	}

}
