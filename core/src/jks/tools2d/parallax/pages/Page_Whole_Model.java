package jks.tools2d.parallax.pages;

import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.DefaultSerializer;

import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.side.SquareBackground;

@DefaultSerializer(Page_Whole_Serializer.class)
public class Page_Whole_Model
{	
	public Color colorSurronding ; 
	
	public Color topHalf_top ; 
	public Color topHalf_bottom ;
	public Color bottomHalf ;
	
	public Page_Model pageModel ; 
	public List<TextureRegionParallaxLayer> preloadValue ;
	
	/*
	public Page_Whole_Model(String atlasPath, Color topHalf_top, Color topHalf_bottom, Color colorSurronding, Color bottomHalf)
	{
		this.atlasPath = atlasPath ;
		this.topHalf_top = topHalf_top ;
		this.topHalf_bottom = topHalf_bottom ; 
		this.colorSurronding = colorSurronding ; 
		this.bottomHalf = bottomHalf ;
	}
	*/
	
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
//		if(preloadValue == null)
//			preloadValue = howToDraw(Parallax_Heart.worldWidth,Parallax_Heart.worldHeight); 
	}

}
