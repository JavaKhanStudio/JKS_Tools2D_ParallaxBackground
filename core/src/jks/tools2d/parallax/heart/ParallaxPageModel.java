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
	public final Color top ; 
	public final Color bottom ;
	public float cloudSpeed ;
	public final Color bottomHalf ;
	
	public List<TextureRegionParallaxLayer> preloadValue ;
	
	public ParallaxPageModel(String atlasPath, Color top, Color bottom, Color colorSurronding, Color bottomHalf)
	{
		this.atlasPath = atlasPath ;
		this.top = top ;
		this.bottom = bottom ; 
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
	public SquareBackground buildSquareBackground(float screenPercentage)
	{return new SquareBackground(top.cpy(),bottom.cpy(),Gdx.graphics.getHeight() * screenPercentage) ;}
	
	public void preload()
	{
		if(preloadValue == null)
			preloadValue = howToDraw(Parallax_Heart.worldWidth,Parallax_Heart.worldHeight); 
	}
}
