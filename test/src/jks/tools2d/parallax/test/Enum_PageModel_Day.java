package jks.tools2d.parallax.test;

import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.heart.ParralaxPageModel;

public enum Enum_PageModel_Day
{

	// 026BC0
	DAY("day/day.atlas",Color.valueOf("026BC0"),Color.valueOf("39D5FF"),0.6f, new Color(1,1,1,1)),
	SUNSET("day/sunset.atlas",Color.valueOf("530F1D"),Color.valueOf("FE9946"),0.6f, new Color(0.25f,0.07f,0.0f,1)),
	SUNRISE("day/sunrise.atlas",Color.valueOf("6D6A7D"),Color.valueOf("FDD069"),0.6f, new Color(0.85f,0.5f,0.4f,1)),
	RAIN("day/rain.atlas",Color.valueOf("39393C"),Color.valueOf("A19996"),0.7f, new Color(1,1,1,1)),
	NIGHT("day/night.atlas",Color.valueOf("21354B"),Color.valueOf("BFE5FE"),0.8f, new Color(0.1f,0.1f,0.1f,1)),
	STATU_QUO(null,null,null,1, null)
	;

	public ParralaxPageModel page ;
	
	Enum_PageModel_Day(String atlasPath, Color top, Color bottom, float duree,Color colorSurronding)
	{
		page = new ParralaxPageModel(atlasPath,top,bottom,duree,colorSurronding) ;
	}
	
	public List<TextureRegionParallaxLayer> createLayers_day(float worldWidth, float worldHeight) 
	{
		if(page.atlasPath == null)
			return null ; 
		
		TextureAtlas atlas = new TextureAtlas(page.atlasPath);

		TextureRegion cloudsRegion = atlas.findRegion("Clouds");
		Array<AtlasRegion> mountainsRegion = atlas.findRegions("Mountains");
		Array<AtlasRegion> treesRegion = atlas.findRegions("Trees");
		
		
		TextureRegionParallaxLayer cloudsLayerA = new TextureRegionParallaxLayer(cloudsRegion, worldWidth, new Vector2(.01f,.01f), true);
		cloudsLayerA.setPadBottom(worldHeight*.3f);
		cloudsLayerA.setSpeed(1); // TODO maybie change
		
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
		treesLayerC.setPadBottom(worldHeight*.05f);
		TextureRegionParallaxLayer treesLayerD = new TextureRegionParallaxLayer(treesRegion.get(2), worldWidth*.7275f, new Vector2(.09f,.09f), true);
		treesLayerD.setPadBottom(worldHeight*.0f);
		    
		return Arrays.asList(cloudsLayerA,mountainsLayerA,mountainsLayerB,mountainsLayerC,treesLayerA1,treesLayerA,treesLayerB,treesLayerC,treesLayerD) ;
	}
}
