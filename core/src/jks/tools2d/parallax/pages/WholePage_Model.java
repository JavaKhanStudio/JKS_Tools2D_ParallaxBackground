package jks.tools2d.parallax.pages;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.DefaultSerializer;

import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.side.SquareBackground;

@DefaultSerializer(WholePage_Serializer.class)
public class WholePage_Model
{	
	public Color colorSurronding ; 
	
	public Color topHalf_top ; 
	public Color topHalf_bottom ;
	public Color bottomHalf ;
	
	public Page_Model pageModel ; 
	public List<TextureRegionParallaxLayer> preloadValue ;
	
	public WholePage_Model()
	{}
	
	public WholePage_Model(String atlasPath, Color topHalf_top, Color topHalf_bottom, Color colorSurronding, Color bottomHalf)
	{
		this.topHalf_top = topHalf_top ; 
		this.topHalf_bottom = topHalf_bottom ; 
		this.colorSurronding = colorSurronding ;
		this.bottomHalf = bottomHalf ; 
		
		pageModel = new Page_Model() ; 
		pageModel.atlasPath = atlasPath ; 
		pageModel.outside = false ; 
//		newPage.pageModel.pageList = buildPage() ; 
	}

	public List<TextureRegionParallaxLayer> getDrawing()
	{
		if(preloadValue == null)
			preload() ;
		
		return preloadValue ; 
	}
	
	/*
	 * 1   = empty screen
	 * 0.5 = half screen
	 * 0   = full screen
	 */
	public SquareBackground buildTopSquareBackground(float screenPercentage)
	{return new SquareBackground(topHalf_top.cpy(),topHalf_bottom.cpy(),Gdx.graphics.getHeight() * screenPercentage) ;}
	
	public SquareBackground buildBottomSquareBackground(float screenPercentage)
	{return new SquareBackground(bottomHalf.cpy(),Gdx.graphics.getHeight() * screenPercentage) ;}
	
	
	
	public void preload()
	{
		if(preloadValue == null)
			preloadValue = load(Parallax_Heart.worldWidth,Parallax_Heart.worldHeight); 
	}

	private List<TextureRegionParallaxLayer> load(float worldWidth, float worldHeight) 
	{
		Parallax_Heart.manager.load(pageModel.atlasPath, TextureAtlas.class);
		Parallax_Heart.manager.finishLoadingAsset(pageModel.atlasPath);
		
		TextureAtlas atlas = new TextureAtlas(pageModel.atlasPath);
//		HashMap<String, Array<AtlasRegion>> regions = new HashMap<String, Array<AtlasRegion>>(); 	
		List<TextureRegionParallaxLayer> returningList = new ArrayList<TextureRegionParallaxLayer>() ; 
		
		for(Parallax_Model parallax : pageModel.pageList)
		{
			TextureRegionParallaxLayer layer = new TextureRegionParallaxLayer(
					atlas.findRegions(parallax.region_Name).get(parallax.region_Position), 
					worldWidth, 
					new Vector2(parallax.ratioX,parallax.ratioY), 
					true) ; 

			layer.setPadBottom(parallax.pad_Y_Ratio * worldHeight);
			layer.setSpeed(parallax.speed);
			returningList.add(layer) ;
		}
		
		return returningList;
	}
	
}
