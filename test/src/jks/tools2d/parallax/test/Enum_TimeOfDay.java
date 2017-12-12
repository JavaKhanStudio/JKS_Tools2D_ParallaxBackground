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

public enum Enum_TimeOfDay 
{

	DAY("day/day.atlas",Color.valueOf("026BC0"),Color.valueOf("39D5FF"),1.0f),
	SUNSET("day/sunset.atlas",Color.valueOf("530F1D"),Color.valueOf("FE9946"),0.6f),
	SUNRISE("day/sunrise.atlas",Color.valueOf("6D6A7D"),Color.valueOf("FDD069"),0.6f),
	RAIN("day/rain.atlas",Color.valueOf("39393C"),Color.valueOf("A19996"),0.7f),
	NIGHT("day/night.atlas",Color.valueOf("21354B"),Color.valueOf("BFE5FE"),1),
	STATU_QUO(null,null,null,1)
	;
	
	String atlasPath ; 
	public final Color top ; 
	public final Color bottom ;
	public float timeToTransfertInto ;
	public float cloudSpeed ; 
	
	Enum_TimeOfDay(String atlasPath, Color top, Color bottom, float duree)
	{
		this.atlasPath = atlasPath ;
		this.top = top ;
		this.bottom = bottom ; 
		this.timeToTransfertInto = duree ; 
	}
	
	public List<TextureRegionParallaxLayer> createLayers_day(float worldWidth, float worldHeight) 
	{
		if(atlasPath == null)
			return null ; 
		
		TextureAtlas atlas = new TextureAtlas(atlasPath);

		TextureRegion cloudsRegion = atlas.findRegion("Clouds");
		Array<AtlasRegion> mountainsRegion = atlas.findRegions("Mountains");
		Array<AtlasRegion> treesRegion = atlas.findRegions("Trees");
		
		
		TextureRegionParallaxLayer cloudsLayerA = new TextureRegionParallaxLayer(cloudsRegion, worldWidth, new Vector2(.01f,.01f), true);
		cloudsLayerA.setPadBottom(worldHeight*.5f);
		cloudsLayerA.setSpeed(1); // TODO maybie change
		
		TextureRegionParallaxLayer mountainsLayerA = new TextureRegionParallaxLayer(mountainsRegion.get(0), worldWidth, new Vector2(.006f,.006f), true);
		mountainsLayerA.setPadBottom(worldHeight*.299f);
		TextureRegionParallaxLayer mountainsLayerB = new TextureRegionParallaxLayer(mountainsRegion.get(1), worldWidth, new Vector2(.008f,.008f), true);
		mountainsLayerB.setPadBottom(worldHeight*.27f);
		TextureRegionParallaxLayer mountainsLayerC = new TextureRegionParallaxLayer(mountainsRegion.get(1), worldWidth, new Vector2(.012f,.012f), true);
		mountainsLayerC.setPadBottom(worldHeight*.20f);
		
        
        TextureRegionParallaxLayer treesLayerA1 = new TextureRegionParallaxLayer(treesRegion.get(0), worldWidth*.7275f, new Vector2(.03f,.03f), true);
		treesLayerA1.setPadBottom(worldHeight*.24f);
        TextureRegionParallaxLayer treesLayerA = new TextureRegionParallaxLayer(treesRegion.get(0), worldWidth*.7275f, new Vector2(.04f,.04f), true);
		treesLayerA.setPadBottom(worldHeight*.18f);
		TextureRegionParallaxLayer treesLayerB = new TextureRegionParallaxLayer(treesRegion.get(1), worldWidth*.7275f, new Vector2(.06f,.06f), true);
		treesLayerB.setPadBottom(worldHeight*.10f);
		TextureRegionParallaxLayer treesLayerC = new TextureRegionParallaxLayer(treesRegion.get(2), worldWidth*.7275f, new Vector2(.08f,.08f), true);
		treesLayerC.setPadBottom(worldHeight*.05f);
		TextureRegionParallaxLayer treesLayerD = new TextureRegionParallaxLayer(treesRegion.get(2), worldWidth*.7275f, new Vector2(.09f,.09f), true);
		treesLayerD.setPadBottom(worldHeight*.0f);
		    
		return Arrays.asList(cloudsLayerA,mountainsLayerA,mountainsLayerB,mountainsLayerC,treesLayerA1,treesLayerA,treesLayerB,treesLayerC,treesLayerD) ;
	}
	
}
