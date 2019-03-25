package jks.tools2d.parallax.pages;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.DefaultSerializer;

import jks.tools2d.parallax.ParallaxLayer;
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
	public List<ParallaxLayer> preloadValue ;
	
	public WholePage_Model()
	{
		colorSurronding = Color.WHITE ; 
		topHalf_top = Color.WHITE ; 
		topHalf_bottom = Color.WHITE ; 
		bottomHalf = Color.WHITE ; 
		pageModel = new Page_Model() ; 
	}
	
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
	
	public WholePage_Model(String atlasPath)
	{
		pageModel = new Page_Model() ; 
		pageModel.atlasPath = atlasPath ; 
		pageModel.outside = false ; 
//		newPage.pageModel.pageList = buildPage() ; 
	}

	public List<ParallaxLayer> getDrawing()
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
	{return new SquareBackground(topHalf_top.cpy(),topHalf_bottom.cpy(),Gdx.graphics.getHeight() * screenPercentage, true) ;}
	
	public SquareBackground buildBottomSquareBackground(float screenPercentage)
	{return new SquareBackground(topHalf_top.cpy(),topHalf_bottom.cpy(),Gdx.graphics.getHeight() * screenPercentage, false) ;}
	
	public void preload()
	{
		if(preloadValue == null)
			preloadValue = load(Parallax_Heart.worldWidth,Parallax_Heart.worldHeight); 
	}
	
	public void forceLoad(TextureAtlas atlas)
	{
		preloadValue = load(Parallax_Heart.worldWidth,Parallax_Heart.worldHeight,atlas); 
	}

	private List<ParallaxLayer> load(float worldWidth, float worldHeight,TextureAtlas atlas)
	{
		List<ParallaxLayer> returningList = new ArrayList<ParallaxLayer>() ; 
		
		for(Parallax_Model parallax : pageModel.pageList)
		{
			ParallaxLayer layer = new ParallaxLayer(
					atlas.findRegions(parallax.region_Name).get(parallax.region_Position), 
					Parallax_Heart.worldWidth, 
					new Vector2(parallax.parallaxScalingSpeedX,parallax.parallaxScalingSpeedY), 
					parallax.sizeRatio,
					true) ; 

			layer.setPadPositionY(parallax.pad_Y_Ratio);
			layer.setPadPositionX(parallax.pad_X_Ratio);
			
			layer.setSpeed(parallax.speed);
			returningList.add(layer) ;
		}
		
		return returningList;
	}
	
	private List<ParallaxLayer> load(float worldWidth, float worldHeight) 
	{
		Parallax_Heart.manager.load(pageModel.atlasPath, TextureAtlas.class);
		Parallax_Heart.manager.finishLoadingAsset(pageModel.atlasPath);
		
		TextureAtlas atlas = new TextureAtlas(pageModel.atlasPath);
		return load(worldWidth, worldHeight, atlas) ; 
	}
		
}