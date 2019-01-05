package jks.tools2d.parallax.test;

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
		cloud.ratioX = .01f ; 
		cloud.ratioY = .01f ; 
		cloud.pad_Y_Ratio = .35f ;
		
		
		Parallax_Model montains0 = new Parallax_Model() ; 
		montains0.region_Name = "Mountains" ; 
		montains0.region_Position = 0 ; 
		montains0.ratioX = .006f ; 
		montains0.ratioY = .006f ; 
		montains0.pad_Y_Ratio = .12f ; 
		
		Parallax_Model montains1 = new Parallax_Model() ; 
		montains1.region_Name = "Mountains" ; 
		montains1.region_Position = 1 ; 
		montains1.ratioX = .008f ; 
		montains1.ratioY = .008f ; 
		montains1.pad_Y_Ratio = .095f ; 
		
		Parallax_Model montains2 = new Parallax_Model() ; 
		montains2.region_Name = "Mountains" ; 
		montains2.region_Position = 1 ; 
		montains2.ratioX = .012f ; 
		montains2.ratioY = .012f ; 
		montains2.pad_Y_Ratio = .045f ;
		
		Parallax_Model tree0 = new Parallax_Model() ; 
		tree0.region_Name = "Trees" ; 
		tree0.region_Position = 0 ; 
		tree0.ratioX = .03f ; 
		tree0.ratioY = .03f ; 
		tree0.pad_Y_Ratio = .12f ; 

		Parallax_Model tree1 = new Parallax_Model() ; 
		tree1.region_Name = "Trees" ; 
		tree1.region_Position = 0 ; 
		tree1.ratioX = .04f ; 
		tree1.ratioY = .04f ; 
		tree1.pad_Y_Ratio = .09f ; 
		
		Parallax_Model tree2 = new Parallax_Model() ; 
		tree2.region_Name = "Trees" ; 
		tree2.region_Position = 1 ; 
		tree2.ratioX = .06f ; 
		tree2.ratioY = .06f ; 
		tree2.pad_Y_Ratio = .07f ; 
		
		Parallax_Model tree3 = new Parallax_Model() ; 
		tree3.region_Name = "Trees" ; 
		tree3.region_Position = 2 ; 
		tree3.ratioX = .08f ; 
		tree3.ratioY = .08f ; 
		tree3.pad_Y_Ratio = .07f ; 
		
		Parallax_Model tree4 = new Parallax_Model() ; 
		tree4.region_Name = "Trees" ; 
		tree4.region_Position = 1 ; 
		tree4.ratioX = .09f ; 
		tree4.ratioY = .09f ; 
		tree4.pad_Y_Ratio = .01f ; 
		
		Parallax_Model tree5 = new Parallax_Model() ; 
		tree5.region_Name = "Trees" ; 
		tree5.region_Position = 2 ; 
		tree5.ratioX = .1f ; 
		tree5.ratioY = .1f ; 
		tree5.pad_Y_Ratio = .0f ; 
		
        
       
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
