package jks.tools.export;

import java.util.Arrays;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.WholePage_Model;

public class DayPageModel extends WholePage_Model
{

	public DayPageModel(String atlasPath, Color top, Color bottom, Color colorSurronding,Color halfBottom) 
	{
		super(atlasPath, top, bottom, colorSurronding,halfBottom);
	}

	@Override
	public void preload() 
	{
		float worldWidth = Gvars_Parallax.getWorldWidth() ;
		float worldHeight = Gvars_Parallax.getWorldHeight() ; 

		TextureAtlas atlas = new TextureAtlas(pageModel.atlasName);

		TextureRegion cloudsRegion = atlas.findRegion("Clouds");
		Array<AtlasRegion> mountainsRegion = atlas.findRegions("Mountains");
		Array<AtlasRegion> treesRegion = atlas.findRegions("Trees");
		
		
		ParallaxLayer cloudsLayerA = new ParallaxLayer(cloudsRegion,true, worldWidth, .01f,.01f, 1);
		cloudsLayerA.setPadY(worldHeight*.35f);
		cloudsLayerA.setSpeedAtRest(0.5f);
		
		ParallaxLayer mountainsLayerA = new ParallaxLayer(mountainsRegion.get(0),true, worldWidth, .006f,.006f, 1);
		mountainsLayerA.setPadY(worldHeight*.12f);
		ParallaxLayer mountainsLayerB = new ParallaxLayer(mountainsRegion.get(1),true, worldWidth, .008f,.008f, 1);
		mountainsLayerB.setPadY(worldHeight*.095f);
		mountainsLayerB.setDecalPercentX(10);
		ParallaxLayer mountainsLayerC = new ParallaxLayer(mountainsRegion.get(1),true, worldWidth, .012f,.012f, 1);
		mountainsLayerC.setPadY(worldHeight*.045f);
		
        
        ParallaxLayer treesLayerA1 = new ParallaxLayer(treesRegion.get(0), true,worldWidth*.7275f, .03f,.03f, 1);
		treesLayerA1.setPadY(worldHeight*.12f);
        ParallaxLayer treesLayerA = new ParallaxLayer(treesRegion.get(0), true,worldWidth*.7275f, .04f,.04f, 1);
		treesLayerA.setPadY(worldHeight*.09f);
		treesLayerA.setDecalPercentX(10);
		
		ParallaxLayer treesLayerB = new ParallaxLayer(treesRegion.get(1), true,worldWidth*.7275f, .06f,.06f, 1);
		treesLayerB.setPadY(worldHeight*.07f);
		
		ParallaxLayer treesLayerC = new ParallaxLayer(treesRegion.get(2), true,worldWidth*.7275f, .08f,.08f, 1);
		treesLayerC.setPadY(worldHeight*.07f);
		
		ParallaxLayer treesLayerD = new ParallaxLayer(treesRegion.get(1), true,worldWidth*.7275f, .09f,.09f, 1);
		treesLayerD.setPadY(worldHeight*.01f);
		treesLayerD.setDecalPercentX(10);
		
		ParallaxLayer treesLayerE = new ParallaxLayer(treesRegion.get(2), true,worldWidth*.7275f, .10f,.10f, 1);
//		treesLayerE.setPadY(worldHeight*.0f);
//		treesLayerE.setDecalPercentX(10);
		    
		preloadValue = Arrays.asList(cloudsLayerA,mountainsLayerA,mountainsLayerB,mountainsLayerC,treesLayerA1,treesLayerA,treesLayerB,treesLayerC,treesLayerD,treesLayerE) ;
	}

}
