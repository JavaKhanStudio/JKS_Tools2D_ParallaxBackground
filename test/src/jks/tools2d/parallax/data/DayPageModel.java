package jks.tools2d.parallax.data;

import java.util.ArrayList;

import jks.tools2d.parallax.pages.Parallax_Model;

public class DayPageModel
{

	public static ArrayList<Parallax_Model> buildPages() 
	{
		/*
		Parallax_Heart.manager.load(atlasPath, TextureAtlas.class);
		Parallax_Heart.manager.finishLoadingAsset(atlasPath);
		TextureAtlas atlas = new TextureAtlas(atlasPath);
		*/ 
	
		ArrayList<Parallax_Model> returningList = new ArrayList<Parallax_Model>() ; 
		Parallax_Model cloud = new Parallax_Model() ; 

		cloud.region_Name = "Clouds" ; 
		cloud.region_Position = 0 ; 
		cloud.speed = 0.5f ;
		cloud.parallaxScalingSpeedX = .01f ; 
		cloud.parallaxScalingSpeedY = .01f ; 
		cloud.decal_Y_Ratio = 35f ;
		
		
		Parallax_Model montains0 = new Parallax_Model() ; 
		montains0.region_Name = "Mountains" ; 
		montains0.region_Position = 0 ; 
		montains0.parallaxScalingSpeedX = .006f ; 
		montains0.parallaxScalingSpeedY = .006f ; 
		montains0.decal_Y_Ratio = 12f ; 
		
		Parallax_Model montains1 = new Parallax_Model() ; 
		montains1.region_Name = "Mountains" ; 
		montains1.region_Position = 1 ; 
		montains1.parallaxScalingSpeedX = .008f ; 
		montains1.parallaxScalingSpeedY = .008f ; 
		montains1.decal_Y_Ratio = 09.5f ; 
		
		Parallax_Model montains2 = new Parallax_Model() ; 
		montains2.region_Name = "Mountains" ; 
		montains2.region_Position = 1 ; 
		montains2.parallaxScalingSpeedX = .012f ; 
		montains2.parallaxScalingSpeedY = .012f ; 
		montains2.decal_Y_Ratio = 04.5f ;
		
		Parallax_Model tree0 = new Parallax_Model() ; 
		tree0.region_Name = "Trees" ; 
		tree0.region_Position = 0 ; 
		tree0.parallaxScalingSpeedX = .03f ; 
		tree0.parallaxScalingSpeedY = .03f ; 
		tree0.decal_Y_Ratio = 12f ; 

		Parallax_Model tree1 = new Parallax_Model() ; 
		tree1.region_Name = "Trees" ; 
		tree1.region_Position = 0 ; 
		tree1.parallaxScalingSpeedX = .04f ; 
		tree1.parallaxScalingSpeedY = .04f ; 
		tree1.decal_Y_Ratio = 09f ; 
		
		Parallax_Model tree2 = new Parallax_Model() ; 
		tree2.region_Name = "Trees" ; 
		tree2.region_Position = 1 ; 
		tree2.parallaxScalingSpeedX = .06f ; 
		tree2.parallaxScalingSpeedY = .06f ; 
		tree2.decal_Y_Ratio = 07f ; 
		
		Parallax_Model tree3 = new Parallax_Model() ; 
		tree3.region_Name = "Trees" ; 
		tree3.region_Position = 2 ; 
		tree3.parallaxScalingSpeedX = .08f ; 
		tree3.parallaxScalingSpeedY = .08f ; 
		tree3.decal_Y_Ratio = 07f ; 
		
		Parallax_Model tree4 = new Parallax_Model() ; 
		tree4.region_Name = "Trees" ; 
		tree4.region_Position = 1 ; 
		tree4.parallaxScalingSpeedX = .09f ; 
		tree4.parallaxScalingSpeedY = .09f ; 
		tree4.decal_Y_Ratio = 01f ; 
		
		Parallax_Model tree5 = new Parallax_Model() ; 
		tree5.region_Name = "Trees" ; 
		tree5.region_Position = 2 ; 
		tree5.parallaxScalingSpeedX = .1f ; 
		tree5.parallaxScalingSpeedY = .1f ; 
		tree5.decal_Y_Ratio = 0f ; 
		
        
       
		returningList.add(cloud) ;
		returningList.add(montains0) ; 
		returningList.add(montains1) ; 
		returningList.add(montains2) ; 
		returningList.add(tree0) ; 
		returningList.add(tree1) ; 
		returningList.add(tree2) ; 
		returningList.add(tree3) ; 
		returningList.add(tree4) ; 
		returningList.add(tree5) ; 
		
		return returningList ; 
	}
	
	/*
	 TextureRegionParallaxLayer cloudsLayerA = new TextureRegionParallaxLayer(cloudsRegion, worldWidth, new Vector2(.01f,.01f), true);
		cloudsLayerA.setPadBottom(worldHeight*.3f);
		cloudsLayerA.setSpeed(1);
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
	 */
//	return Arrays.asList(cloudsLayerA,mountainsLayerA,mountainsLayerB,mountainsLayerC,treesLayerA1,treesLayerA,treesLayerB,treesLayerC,treesLayerD,treesLayerE) ;
	

}
