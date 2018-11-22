package jks.tools2d.parallax.heart;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;

import jks.tools2d.parallax.side.DrawingInstruction;
import jks.tools2d.parallax.side.SquareBackground;

public abstract class ParralaxPageModel implements DrawingInstruction
{	
	public String atlasPath ; 
	public final Color colorSurronding ; 
	public final Color top ; 
	public final Color bottom ;
	public float timeToTransfertInto ;
	public float cloudSpeed ; 
	
	public ParralaxPageModel(String atlasPath, Color top, Color bottom, float duree,Color colorSurronding)
	{
		this.atlasPath = atlasPath ;
		this.top = top ;
		this.bottom = bottom ; 
		this.timeToTransfertInto = duree ; 
		this.colorSurronding = colorSurronding ; 
	}
	
	/*
	 * 1 = nothing
	 * 0 = full screen
	 */
	public SquareBackground buildSquareBackground(float screenPercentage)
	{return new SquareBackground(top.cpy(),bottom.cpy(),Gdx.graphics.getHeight() * screenPercentage) ;}
	
}
