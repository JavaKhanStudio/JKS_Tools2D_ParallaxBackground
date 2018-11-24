package jks.tools2d.parallax.heart;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;

import jks.tools2d.parallax.side.DrawingInstruction;
import jks.tools2d.parallax.side.SquareBackground;

public abstract class ParallaxPageModel implements DrawingInstruction
{	
	public String atlasPath ; 
	public final Color colorSurronding ; 
	public final Color top ; 
	public final Color bottom ;
	public float cloudSpeed ; 
	
	public ParallaxPageModel(String atlasPath, Color top, Color bottom, Color colorSurronding)
	{
		this.atlasPath = atlasPath ;
		this.top = top ;
		this.bottom = bottom ; 
		this.colorSurronding = colorSurronding ; 
	}
	
	/*
	 * 1 = nothing
	 * 0 = full screen
	 */
	public SquareBackground buildSquareBackground(float screenPercentage)
	{return new SquareBackground(top.cpy(),bottom.cpy(),Gdx.graphics.getHeight() * screenPercentage) ;}
	
}
