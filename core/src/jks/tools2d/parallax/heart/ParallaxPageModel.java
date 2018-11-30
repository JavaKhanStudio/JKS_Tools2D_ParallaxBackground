package jks.tools2d.parallax.heart;

import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.side.DrawingInstruction;
import jks.tools2d.parallax.side.SquareBackground;

public abstract class ParallaxPageModel implements DrawingInstruction
{	
	public String atlasPath ; 
	public final Color colorSurronding ; 
	
	public final Color topHalf_top ; 
	public final Color topHalf_bottom ;
	public final Color bottomHalf ;
	
	public List<TextureRegionParallaxLayer> preloadValue ;
	
	public ParallaxPageModel(String atlasPath, Color topHalf_top, Color topHalf_bottom, Color colorSurronding, Color bottomHalf)
	{
		this.atlasPath = atlasPath ;
		this.topHalf_top = topHalf_top ;
		this.topHalf_bottom = topHalf_bottom ; 
		this.colorSurronding = colorSurronding ; 
		this.bottomHalf = bottomHalf ;
	}
	
	
	public List<TextureRegionParallaxLayer> getDrawing()
	{
		if(preloadValue == null)
			preload() ;
		
		return preloadValue ; 
	}
	
	/*
	 * 1 = nothing
	 * 0 = full screen
	 */
	public SquareBackground buildTopSquareBackground(float screenPercentage)
	{return new SquareBackground(topHalf_top.cpy(),topHalf_bottom.cpy(),Gdx.graphics.getHeight() * screenPercentage) ;}
	
	public SquareBackground buildBottomSquareBackground(float screenPercentage)
	{return new SquareBackground(bottomHalf.cpy(),Gdx.graphics.getHeight() * screenPercentage) ;}
	
	
	public void preload()
	{
		if(preloadValue == null)
			preloadValue = howToDraw(Parallax_Heart.worldWidth,Parallax_Heart.worldHeight); 
	}
}
